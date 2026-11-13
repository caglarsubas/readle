#!/usr/bin/env python3
"""Render a private, standalone review page for a generated learning edition."""
import argparse
import hashlib
import html
import json
from pathlib import Path

TEMPLATE = r"""<!doctype html>
<html lang="en">
<meta charset="utf-8">
<meta name="viewport" content="width=device-width, initial-scale=1">
<title>Readle · Walkthrough test</title>
<style>
:root{color-scheme:light dark;--page:#f6f3ed;--paper:#fffdfa;--ink:#232522;--muted:#545952;--line:#d1cfc4;--accent:#235a48;--active:#e1eee6}
@media(prefers-color-scheme:dark){:root{--page:#1a1e1b;--paper:#252a26;--ink:#f0eee8;--muted:#b7beb5;--line:#545d54;--accent:#a4dfc1;--active:#30483a}}
*{box-sizing:border-box}body{margin:0;background:var(--page);color:var(--ink);font:1rem/1.6 system-ui,sans-serif}
header,main{max-width:1100px;margin:auto;padding:1.4rem clamp(1rem,4vw,3rem)}header{padding-bottom:.25rem}
h1,h2{font-family:Georgia,serif;line-height:1.15;margin:.35em 0}h1{font-size:clamp(2rem,5vw,3.7rem);max-width:24ch}h2{font-size:clamp(1.6rem,3vw,2.5rem)}
p{margin:.6rem 0 1rem}p:last-child{margin-bottom:0}h3{margin:1.3rem 0 .3rem;font-size:1.05rem}
.muted{color:var(--muted)}.brand{font-weight:750;color:var(--accent)}a{color:var(--accent)}button,select{font:inherit}
select,button,.option{padding:.52rem .8rem;border:1px solid var(--line);border-radius:.4rem;background:var(--paper);color:var(--ink)}
select{max-width:100%;margin:.25rem 0}.lesson-choice{display:block;text-align:left;width:100%;margin:.35rem 0}
button[aria-pressed=true]{background:var(--active);border-color:var(--accent);font-weight:700}button:focus-visible,select:focus-visible,a:focus-visible{outline:2px solid var(--accent);outline-offset:3px}
.workspace{display:grid;grid-template-columns:minmax(13rem,1fr) minmax(0,3fr);gap:1.25rem;align-items:start;margin-top:1.4rem}
article{background:var(--paper);border:1px solid var(--line);border-radius:.65rem;padding:clamp(1rem,3vw,2rem)}
.passes{display:flex;gap:.4rem;flex-wrap:wrap;margin:1rem 0}fieldset{border:0;padding:0;margin:1rem 0;min-width:0}legend{font-weight:650}
.option{display:flex;align-items:baseline;gap:.6rem;margin:.35rem 0}.option input{flex:none}
.checkpoint{border-inline-start:3px solid var(--accent);padding-inline-start:1rem;margin:1.25rem 0}
details{margin:.6rem 0}summary{cursor:pointer}blockquote{margin:.5rem 0;padding-left:.75rem;border-left:2px solid var(--line)}
.result{padding:.65rem;background:var(--active);border-radius:.35rem}.notice{color:var(--muted);border-bottom:1px solid var(--line);padding-bottom:.65rem}
.mechanism{white-space:pre-wrap;overflow-wrap:anywhere}
@media(max-width:700px){.workspace{grid-template-columns:1fr}}
</style>
<header>
  <div class="brand">Readle · Development test</div>
  <h1>__TITLE__</h1>
  <p class="muted">__SOURCE_INFO__</p>
</header>
<main>
  <label for="profile"><strong>Learner profile</strong></label><br>
  <select id="profile"></select>
  <p id="goal"></p>
  <details><summary>Why this route</summary><p id="rationale" class="muted"></p></details>
  <div class="workspace">
    <nav id="route" aria-label="Recommended lessons"></nav>
    <article id="lesson"></article>
  </div>
</main>
<script type="application/json" id="data">__DATA__</script>
<script>
"use strict";
const data = JSON.parse(document.querySelector("#data").textContent);
const profiles = Object.entries(data.courses);
const select = document.querySelector("#profile");
const route = document.querySelector("#route");
const article = document.querySelector("#lesson");
const passes = ["Concepts", "Technical", "Assessment"];
const states = profiles.map(() => ({lesson: 0, pass: 0, submissions: new Map(), drafts: new Map()}));
const escape = value => String(value).replace(/[&<>"']/g, c => ({"&":"&amp;","<":"&lt;",">":"&gt;","'":"&#39;",'"':"&quot;"})[c]);
for (const [id, course] of profiles) {
  const option = document.createElement("option");
  option.value = id;
  option.textContent = course.profile.background.split(". ")[0].replace(/\.$/, "");
  select.append(option);
}
const prose = value => escape(value)
  .replace(/\[(?:E\d{3}(?:,\s*)?\s*)+\]/g, "")
  .replace(/\*\*([^*]+)\*\*/g, "<strong>$1</strong>")
  .replace(/`([^`]+)`/g, "<code>$1</code>")
  .replace(/[ \t]+([.,;])/g, "$1");
const paragraph = (heading, body) => '<h3>' + heading + '</h3><p class="mechanism">' + prose(body) + '</p>';
const citations = ids => '<h3>Source passages</h3>' + ids.map(id => {
  const entry = data.evidence[id];
  return '<details><summary>PDF page ' + entry.page + ' · ' + escape(entry.concept) +
    '</summary><blockquote>' + escape(entry.text) + '</blockquote><a href="' +
    escape(data.pdf_url + '#page=' + entry.page) + '" target="_blank" rel="noopener">Open original page</a></details>';
}).join("");
function render(focusId) {
  const course = profiles[select.selectedIndex][1];
  const state = states[select.selectedIndex];
  const lesson = course.lessons[state.lesson];
  document.querySelector("#goal").textContent = course.profile.goal;
  document.querySelector("#rationale").textContent = course.rationale;
  route.innerHTML = '<strong>Recommended route</strong>' + course.lessons.map((item, i) =>
    '<button type="button" class="lesson-choice" id="unit-' + i + '" aria-pressed="' +
    (state.lesson === i) + '">' + escape(item.title) + '</button>').join("");
  course.lessons.forEach((_, i) => {
    document.querySelector("#unit-" + i).onclick = () => {state.lesson = i; render("unit-" + i);};
  });
  let body = '<h2>' + escape(lesson.title) + '</h2><p>' + prose(lesson.objective) + '</p>' +
    '<details><summary>Why this lesson</summary><p class="muted">' + prose(lesson.why) +
    '</p></details><div class="passes" role="group" aria-label="Learning pass">' +
    passes.map((name, i) => '<button type="button" id="pass-' + i + '" aria-pressed="' +
      (state.pass === i) + '">Pass ' + (i+1) + ' · ' + name + '</button>').join("") + '</div>';
  if (lesson.review.lesson_status !== "supported") {
    body += '<p class="notice">The lesson explanation needs source review.</p>';
  }
  if (state.pass < 2) {
    const content = state.pass === 0 ? lesson.concepts : lesson.technical;
    body += state.pass === 0
      ? paragraph("Explanation", content.explanation) + paragraph("Worked example", content.example)
      : paragraph("Assumptions", content.assumptions) + paragraph("Mechanism", content.mechanism) +
        paragraph("Failure mode", content.failure_mode) + paragraph("Engineering exercise", content.exercise);
    body += '<div class="checkpoint">' + paragraph("Ungraded checkpoint", content.checkpoint) +
      '</div>' + citations(content.source_ids);
  } else {
    body += lesson.assessment.map((item, i) => {
      const key = state.lesson + "-" + i;
      const selected = state.drafts.get(key);
      const committed = state.submissions.get(key);
      const verified = lesson.assessment_key_matches[i];
      return '<form id="question-' + i + '">' +
        (!verified ? '<p class="notice">This item needs review. Scoring is unavailable.</p>' : '') +
        '<fieldset' + (committed || !verified ? ' disabled' : '') +
        '><legend>' + escape(item.stem) + '</legend>' +
        Object.entries(item.options).map(([id, value]) =>
          '<label class="option"><input type="radio" required name="answer" value="' + id + '"' +
          (selected === id ? ' checked' : '') + '>' + escape(value) + '</label>').join("") +
        '<button type="submit" id="submit-' + i + '">Submit answer</button></fieldset>' +
        (committed ? '<p class="result" tabindex="-1" id="result-' + i + '">' +
          (committed === item.answer ? '<strong>Correct.</strong> ' : '<strong>Incorrect.</strong> ') +
          '<strong>Answer:</strong> ' + escape(item.options[item.answer]) + '<br>' +
          prose(item.rationale) + '</p>' + citations(item.source_ids) : '') + '</form>';
    }).join("");
  }
  article.innerHTML = body;
  passes.forEach((_, i) => {
    document.querySelector("#pass-" + i).onclick = () => {state.pass = i; render("pass-" + i);};
  });
  if (state.pass === 2) {
    lesson.assessment.forEach((_, i) => {
      const form = document.querySelector("#question-" + i);
      const key = state.lesson + "-" + i;
      form.onchange = () => state.drafts.set(key, new FormData(form).get("answer"));
      form.onsubmit = event => {
        event.preventDefault();
        const answer = new FormData(form).get("answer");
        if (answer && lesson.assessment_key_matches[i] && !state.submissions.has(key)) {
          state.drafts.set(key, answer);
          state.submissions.set(key, answer);
          render("result-" + i);
        }
      };
    });
  }
  if (focusId) document.getElementById(focusId)?.focus();
}
select.onchange = () => render();
render();
</script>
</html>
"""


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("result", type=Path)
    parser.add_argument("pdf", type=Path)
    parser.add_argument("--out", type=Path, required=True)
    args = parser.parse_args()
    data = json.loads(args.result.read_text())
    pdf = args.pdf.resolve(strict=True)
    with pdf.open("rb") as source_file:
        if hashlib.file_digest(source_file, "sha256").hexdigest() != data["source"]["sha256"]:
            parser.error("The PDF differs from the edition used to generate this walkthrough.")
    data["pdf_url"] = pdf.as_uri()
    source = data["source"]
    page = (TEMPLATE.replace("__TITLE__", html.escape(source["title"].split(":")[0]))
            .replace("__SOURCE_INFO__", f'{source["pdf_page_count"]} PDF pages inspected · ' +
                     html.escape(data["models"]["author"]) + " · local generation")
            .replace("__DATA__", json.dumps(data, ensure_ascii=False).replace("<", "\\u003c")))
    args.out.write_text(page)


if __name__ == "__main__":
    main()
