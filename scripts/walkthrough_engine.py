"""Authenticated inference-engine client for the Mac walkthrough service."""

import json
import os
from urllib.error import HTTPError, URLError
from urllib.parse import urlsplit
from urllib.request import ProxyHandler, Request, build_opener

from test_pdf_walkthrough import NoRedirect, StageModel

LOOPBACK_HOSTS = ("127.0.0.1", "localhost", "::1")


def provider_settings(path):
    settings = {}
    if path.exists():
        for line in path.read_text().splitlines():
            name, _, value = line.strip().removeprefix("export ").partition("=")
            name = name.strip()
            if name.startswith("LLM_PROVIDER_"):
                value = value.strip()
                if len(value) >= 2 and value[0] in "\"'" and value[-1] == value[0]:
                    value = value[1:-1]
                settings[name] = value
    settings.update({name: value for name, value in os.environ.items()
                     if name.startswith("LLM_PROVIDER_")})
    return settings


class InferenceEngine:
    def __init__(self, settings):
        required = ("BASE_URL", "API_KEY", "TENANT_ID", "ORG_ID", "KEY_ID")
        for name in required:
            if not settings.get(f"LLM_PROVIDER_{name}"):
                raise ValueError(f"Set LLM_PROVIDER_{name} in the Mac service environment or .env.")
        self.base_url = settings["LLM_PROVIDER_BASE_URL"].rstrip("/")
        target = urlsplit(self.base_url)
        if (target.scheme not in ("http", "https")
                or target.hostname not in LOOPBACK_HOSTS
                or target.username is not None or target.query or target.fragment):
            raise ValueError("Set LLM_PROVIDER_BASE_URL to the local inference engine's API URL.")
        key = settings["LLM_PROVIDER_API_KEY"]
        if not key.isascii() or not key.isprintable() or any(char.isspace() for char in key):
            raise ValueError("LLM_PROVIDER_API_KEY is malformed.")
        self.headers = {"Authorization": f"Bearer {key}", "Content-Type": "application/json"}
        self.cache_scope = ["inference-engine", self.base_url, settings["LLM_PROVIDER_TENANT_ID"],
                            settings["LLM_PROVIDER_ORG_ID"], settings["LLM_PROVIDER_KEY_ID"]]
        self.opener = build_opener(ProxyHandler({}), NoRedirect())

    def call(self, path, payload=None):
        body = json.dumps(payload).encode() if payload is not None else None
        request = Request(self.base_url + path, body, self.headers)
        try:
            with self.opener.open(request, timeout=360 if payload is not None else 5) as response:
                return json.load(response), response.headers.get("X-Request-ID")
        except HTTPError as error:
            if error.code in (401, 403):
                raise ValueError("The inference engine rejected the Readle key or its route permissions.") from None
            request_id = error.headers.get("X-Request-ID")
            suffix = f" (request {request_id})" if request_id else ""
            raise ValueError(f"Inference engine returned HTTP {error.code}{suffix}.") from None
        except (URLError, TimeoutError):
            raise ValueError("The local inference engine is unavailable or timed out. Retry to resume.") from None
        except ValueError:
            raise ValueError("The inference engine returned an invalid JSON response.") from None

    def models(self, output, *names):
        response, _ = self.call("/models")
        catalog = {item["id"]: item for item in response["data"]}
        models = []
        for name in names:
            item = catalog.get(name)
            if not item:
                raise ValueError(f"The inference engine catalog does not list {name}.")
            # The UAT flow sends source passages only to models hosted on the Mac.
            backend = item.get("backend")
            if (backend not in ("ollama_http", "llama_cpp", "mlx")
                    or ":cloud" in name or name.endswith("-cloud")):
                raise ValueError(f"The configured model {name} is not a local inference route.")
            if backend == "ollama_http":
                # The catalog advertises the Ollama server used by the engine.
                # pathlib normalizes the catalog's synthetic URL to single slashes.
                path = (item.get("model_path") or "").removeprefix("ollama_http:").lstrip("/")
                scheme, _, address = path.partition(":/")
                model_host = urlsplit(f"{scheme}://{address.lstrip('/')}").hostname
                if scheme not in ("http", "https") or model_host not in LOOPBACK_HOSTS:
                    raise ValueError(f"The configured model {name} is not hosted on the Mac.")
            models.append(EngineModel(self, name, item, output))
        return models


class EngineModel(StageModel):
    def __init__(self, engine, model, catalog_entry, output):
        self.engine = engine
        self.model = model
        self.output = output
        # The catalog does not always publish a weight digest. Separate these
        # stages from direct Ollama results and bind them to the engine route.
        route = {key: catalog_entry.get(key) for key in (
            "backend", "model_path", "size_bytes", "context_length", "revision", "digest",
        )}
        self.cache_scope = [*engine.cache_scope, route]

    def request(self, messages, schema):
        return {
            "model": self.model, "stream": False, "messages": messages,
            "temperature": 0.15, "max_tokens": 4096, "seed": 29,
            "response_format": {
                "type": "json_schema",
                "json_schema": {"name": "walkthrough_stage", "strict": True, "schema": schema},
            },
        }

    def complete(self, request):
        response, request_id = self.engine.call("/chat/completions", request)
        try:
            if response["model"] != self.model or response.get("fallback_from_model"):
                raise ValueError(f"The inference engine substituted a different route for {self.model}.")
            choice = response["choices"][0]
            content = choice["message"]["content"]
            finish_reason = choice["finish_reason"]
            usage = response.get("usage") or {}
            if content is not None and not isinstance(content, str):
                raise TypeError
            return content or "", finish_reason, {
                "provider": "inference-engine",
                "model": response["model"],
                "engine_request_id": request_id,
                "input_tokens": usage.get("prompt_tokens"),
                "output_tokens": usage.get("completion_tokens"),
            }
        except (KeyError, IndexError, TypeError, AttributeError):
            raise ValueError("The inference engine returned an invalid chat completion.") from None

    @staticmethod
    def advance_seed(request):
        request["seed"] += 1
