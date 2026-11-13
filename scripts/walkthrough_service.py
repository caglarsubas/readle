#!/usr/bin/env python3
"""Serve local, resumable Readle walkthrough jobs over a loopback connection."""

import argparse
import base64
from concurrent.futures import ThreadPoolExecutor
import hashlib
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer
import json
import os
from pathlib import Path
import re
import threading

from test_pdf_walkthrough import (
    LocalModel, TEXT, build_unit, extract_pages, inventory, obj, plan_route,
    review_unit, revise_application_item, save_json, validate,
)

MAX_SOURCE = 32 * 1024 * 1024
MAX_PAGES = 120
SAMPLE_REVIEW = Path(__file__).resolve().parents[1] / "tests/fixtures/identity_application_review.json"
JOB_PATH = re.compile(r"/v1/walkthroughs/([0-9a-f-]{36})")


def sample_application_item(corrections, source_version, lesson, evidence):
    """Apply source-reviewed fixes to the two identified sample lessons."""
    if source_version != corrections["source_version"]:
        return None
    lesson_passages = {evidence[key]["passage_id"]: key for key in lesson["source_ids"]}
    for item in corrections["items"]:
        if item["lesson_title"] != lesson["title"] or not all(
            passage in lesson_passages for passage in item["passage_ids"]
        ):
            continue
        question = {key: item[key] for key in ("stem", "options", "answer", "rationale")}
        question["source_ids"] = [lesson_passages[passage] for passage in item["passage_ids"]]
        question["review_method"] = "source-reviewed sample correction"
        return question
    return None


class Jobs:
    def __init__(self, root, cache, author, reviewer):
        self.root = root
        self.cache = cache
        self.author = author
        self.reviewer = reviewer
        self.lock = threading.Lock()
        self.worker = ThreadPoolExecutor(max_workers=1)
        self.active = set()

    def status(self, job_id):
        path = self.root / job_id / "status.json"
        if not path.exists():
            return None
        result = json.loads(path.read_text())
        if result["state"] == "running" and job_id not in self.active:
            return {"state": "failed", "message": "The generator restarted. Retry to resume."}
        return result

    def start(self, job_id, source, source_version, profile):
        folder = self.root / job_id
        request = {"sourceVersion": source_version, "profile": profile}
        with self.lock:
            folder.mkdir(parents=True, exist_ok=True)
            request_path = folder / "request.json"
            if request_path.exists() and json.loads(request_path.read_text()) != request:
                raise ValueError("This job belongs to another source or profile.")
            existing = self.status(job_id)
            if existing and existing["state"] in ("running", "ready"):
                return existing
            if len(self.active) >= 4:
                raise ValueError("The local generator queue is full. Try again shortly.")
            (folder / "source.pdf").write_bytes(source)
            save_json(request_path, request)
            status = {"state": "running", "message": "Waiting for the local generator"}
            save_json(folder / "status.json", status)
            self.active.add(job_id)
            self.worker.submit(self.generate, job_id, folder, request)
            return status

    def generate(self, job_id, folder, request):
        def update(message):
            save_json(folder / "status.json", {"state": "running", "message": message})

        try:
            update("Extracting the whole PDF")
            title, pages = extract_pages(folder / "source.pdf")
            if len(pages) > MAX_PAGES:
                raise ValueError(f"This build supports up to {MAX_PAGES} PDF pages.")
            if any(not re.search(r"\w{3,}", page) for page in pages.values()):
                raise ValueError("A page needs OCR. Use a PDF with selectable text on every page.")
            profile = request["profile"]
            author = LocalModel(self.author, self.cache)
            reviewer = LocalModel(self.reviewer, self.cache)
            update(f"Indexing {len(pages)} PDF pages")
            batches, evidence = inventory(pages, author)
            update("Planning your learning path")
            # Stage names affect cache filenames only. Request hashes bind all content and models.
            cache_label = "engineer"
            route = plan_route(author, cache_label, profile, batches, evidence)
            corrections = json.loads(SAMPLE_REVIEW.read_text())
            lessons = []
            for number, unit in enumerate(route["units"], 1):
                update(f"Writing all three passes for lesson {number} of {len(route['units'])}")
                lesson = build_unit(author, cache_label, profile, number, unit, evidence, pages)
                reviewed_item = sample_application_item(corrections, request["sourceVersion"], lesson, evidence)
                if reviewed_item is None:
                    lesson = revise_application_item(author, reviewer, cache_label, lesson, profile, evidence, pages)
                update(f"Checking sources and answers for lesson {number} of {len(route['units'])}")
                reviewed = review_unit(reviewer, cache_label, lesson, evidence, pages)
                if reviewed_item is not None:
                    reviewed["assessment"][1] = reviewed_item
                    reviewed["assessment_key_matches"][1] = True
                    reviewed["review"]["assessments"][1] = {
                        "key": reviewed_item["answer"], "reason": reviewed_item["rationale"],
                    }
                    reviewed["review_passed"] = reviewed["review"]["lesson_status"] == "supported" and all(
                        reviewed["assessment_key_matches"])
                lessons.append(reviewed)
            citations = {source: {key: evidence[source][key] for key in ("page", "concept", "text")}
                         for lesson in lessons for source in lesson["source_ids"]}
            result = {
                "version": 1, "title": title if title != "source" else "Learning edition",
                "sourceVersion": request["sourceVersion"], "pageCount": len(pages),
                "profile": profile, "rationale": route["rationale"], "lessons": lessons,
                "citations": citations, "author": author.model, "authorDigest": author.digest,
                "reviewer": reviewer.model, "reviewerDigest": reviewer.digest,
            }
            save_json(folder / "edition.json", result)
            save_json(folder / "status.json", {"state": "ready", "edition": result})
        except Exception as error:
            print(f"{job_id}: {type(error).__name__}: {error}", flush=True)
            message = str(error) if isinstance(error, ValueError) else "Generation was interrupted. Retry to resume."
            save_json(folder / "status.json", {"state": "failed", "message": message[:220]})
        finally:
            with self.lock:
                self.active.discard(job_id)


class Handler(BaseHTTPRequestHandler):
    def respond(self, status, value):
        payload = json.dumps(value, ensure_ascii=False).encode()
        self.send_response(status)
        self.send_header("Content-Type", "application/json; charset=utf-8")
        self.send_header("Content-Length", str(len(payload)))
        self.end_headers()
        self.wfile.write(payload)

    def do_GET(self):
        match = JOB_PATH.fullmatch(self.path)
        status = self.server.jobs.status(match[1]) if match else None
        self.respond(200 if status else 404, status or {"message": "Walkthrough job unavailable."})

    def do_POST(self):
        self.connection.settimeout(25)
        match = JOB_PATH.fullmatch(self.path)
        if not match:
            self.respond(404, {"message": "Walkthrough endpoint unavailable."})
            return
        try:
            size = int(self.headers.get("Content-Length", "0"))
            if not 0 < size <= MAX_SOURCE:
                raise ValueError("Choose a PDF up to 32 MB.")
            profile = json.loads(base64.b64decode(self.headers["X-Readle-Profile"], validate=True))
            validate(profile, obj(background=TEXT, goal=TEXT, preference=TEXT))
            if any(len(value) > 500 for value in profile.values()):
                raise ValueError("Keep each profile field within 500 characters.")
            source = self.rfile.read(size)
            source_version = hashlib.sha256(source).hexdigest()
            if len(source) != size or not source.startswith(b"%PDF-") or source_version != self.headers["X-Readle-Source"]:
                raise ValueError("The received PDF does not match the imported edition.")
            status = self.server.jobs.start(match[1], source, source_version, profile)
            self.respond(200 if status["state"] == "ready" else 202, status)
        except (ValueError, TypeError) as error:
            self.respond(400, {"message": str(error)})


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--out", type=Path, required=True)
    parser.add_argument("--cache", type=Path, help="Existing stage cache from the PDF test")
    parser.add_argument("--author", default="qwen3.8:27b")
    parser.add_argument("--reviewer", default="ministral-3:8b")
    args = parser.parse_args()
    os.umask(0o077)
    jobs = Jobs(args.out / "jobs", args.cache or args.out, args.author, args.reviewer)
    with ThreadingHTTPServer(("127.0.0.1", 8765), Handler) as server:
        server.jobs = jobs
        print("Readle walkthrough service on 127.0.0.1:8765", flush=True)
        server.serve_forever()


if __name__ == "__main__":
    main()
