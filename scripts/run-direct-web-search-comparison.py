#!/usr/bin/env python3
"""Run a bounded, redacted Tavily/Exa focused-evidence comparison."""

from __future__ import annotations

import json
import os
import sys
import time
import urllib.error
import urllib.request
from dataclasses import dataclass
from pathlib import Path
from urllib.parse import urlparse


MAX_REQUESTS = 100
MINIMUM_INTERVAL_SECONDS = 0.25
MAX_SOURCES = 3
MAX_EXCERPTS_PER_SOURCE = 2
MAX_EXCERPT_CHARACTERS = 500
MAX_EVIDENCE_CHARACTERS = 1_800
ALLOWED_ENDPOINTS = {
    "https://api.tavily.com/search",
    "https://api.tavily.com/extract",
    "https://api.exa.ai/search",
}


@dataclass
class RequestBudget:
    count: int = 0
    last_started: float = 0.0

    def post(self, url: str, headers: dict[str, str], body: dict) -> tuple[int, dict]:
        if url not in ALLOWED_ENDPOINTS:
            raise ValueError(f"Endpoint not allowed: {url}")
        if self.count >= MAX_REQUESTS:
            raise RuntimeError("HTTP request budget exhausted")
        wait = MINIMUM_INTERVAL_SECONDS - (time.monotonic() - self.last_started)
        if wait > 0:
            time.sleep(wait)
        payload = json.dumps(body, ensure_ascii=False).encode("utf-8")
        request = urllib.request.Request(
            url,
            data=payload,
            headers={
                "Accept": "application/json",
                "Content-Type": "application/json",
                "User-Agent": "ArarAI/1.0 direct comparison",
                **headers,
            },
            method="POST",
        )
        self.count += 1
        self.last_started = time.monotonic()
        try:
            with urllib.request.urlopen(request, timeout=20) as response:
                return response.status, json.load(response)
        except urllib.error.HTTPError as error:
            try:
                response_body = json.load(error)
            except (json.JSONDecodeError, UnicodeDecodeError):
                response_body = {}
            return error.code, response_body


def valid_https_url(value: str) -> bool:
    parsed = urlparse(value)
    return parsed.scheme == "https" and bool(parsed.hostname) and not parsed.username and not parsed.fragment


def normalize_sources(candidates: list[dict]) -> list[dict]:
    accepted: list[dict] = []
    seen_urls: set[str] = set()
    seen_excerpts: set[str] = set()
    used_characters = 0
    for candidate in candidates:
        if len(accepted) >= MAX_SOURCES:
            break
        title = " ".join(str(candidate.get("title", "")).split())[:200]
        url = str(candidate.get("url", "")).strip()
        if not title or not valid_https_url(url) or url in seen_urls:
            continue
        excerpts = []
        for raw_excerpt in candidate.get("excerpts", []):
            excerpt = " ".join(str(raw_excerpt).split())[:MAX_EXCERPT_CHARACTERS]
            if not excerpt or excerpt in seen_excerpts:
                continue
            available = MAX_EVIDENCE_CHARACTERS - used_characters
            if available <= 0:
                break
            excerpt = excerpt[:available]
            excerpts.append(excerpt)
            seen_excerpts.add(excerpt)
            used_characters += len(excerpt)
            if len(excerpts) >= MAX_EXCERPTS_PER_SOURCE:
                break
        if excerpts:
            accepted.append({"title": title, "url": url, "excerpts": excerpts})
            seen_urls.add(url)
    return accepted


def tavily(question: dict, token: str, budget: RequestBudget) -> tuple[str, list[dict]]:
    status, search = budget.post(
        "https://api.tavily.com/search",
        {"Authorization": f"Bearer {token}"},
        {
            "query": question["prompt"],
            "search_depth": "basic",
            "topic": "general",
            "max_results": MAX_SOURCES,
            "include_answer": False,
            "include_raw_content": False,
        },
    )
    if status < 200 or status >= 300:
        return f"http_{status}", []
    pages = [item for item in search.get("results", []) if item.get("title") and item.get("url")][:MAX_SOURCES]
    if not pages:
        return "no_results", []
    status, extract = budget.post(
        "https://api.tavily.com/extract",
        {"Authorization": f"Bearer {token}"},
        {
            "urls": [page["url"] for page in pages],
            "query": question["focus"],
            "chunks_per_source": MAX_EXCERPTS_PER_SOURCE,
            "extract_depth": "basic",
            "format": "text",
        },
    )
    if status < 200 or status >= 300:
        return f"http_{status}", []
    extracted = {item.get("url"): item.get("raw_content", "") for item in extract.get("results", [])}
    candidates = []
    for page in pages:
        raw = extracted.get(page["url"], "") or page.get("content", "")
        chunks = [chunk.strip() for chunk in raw.split("[...]") if chunk.strip()]
        candidates.append({"title": page["title"], "url": page["url"], "excerpts": chunks})
    return "success", normalize_sources(candidates)


def exa(question: dict, token: str, budget: RequestBudget) -> tuple[str, list[dict]]:
    status, response = budget.post(
        "https://api.exa.ai/search",
        {"x-api-key": token},
        {
            "query": question["prompt"],
            "type": "auto",
            "numResults": MAX_SOURCES,
            "contents": {
                "highlights": {
                    "query": question["focus"],
                    "maxCharacters": MAX_EXCERPT_CHARACTERS * MAX_EXCERPTS_PER_SOURCE,
                }
            },
        },
    )
    if status < 200 or status >= 300:
        return f"http_{status}", []
    candidates = [
        {"title": item.get("title", ""), "url": item.get("url", ""), "excerpts": item.get("highlights", [])}
        for item in response.get("results", [])
    ]
    return "success", normalize_sources(candidates)


def main() -> int:
    project = Path(__file__).resolve().parent.parent
    corpus_path = project / "docs" / "web-search-comparison-corpus.json"
    output_path = project.parent.parent / "artifacts" / "ararai" / "web-search-direct-comparison.json"
    tavily_token = os.environ.get("TAVILY_API_KEY", "").strip()
    exa_token = os.environ.get("EXA_API_KEY", "").strip()
    if not tavily_token or not exa_token:
        print("TAVILY_API_KEY and EXA_API_KEY are required", file=sys.stderr)
        return 2
    corpus = json.loads(corpus_path.read_text(encoding="utf-8"))
    questions = [question for question in corpus["questions"] if question["shouldUseWebSearch"]]
    if len(questions) > 20:
        raise RuntimeError("Comparison corpus exceeds the 20-question request budget")

    budget = RequestBudget()
    records = []
    for question in questions:
        for provider, runner, token in (
            ("Tavily", tavily, tavily_token),
            ("Exa", exa, exa_token),
        ):
            started = time.monotonic()
            try:
                outcome, sources = runner(question, token, budget)
            except Exception as error:  # bounded runner records failures and continues
                outcome, sources = f"error_{type(error).__name__}", []
            records.append(
                {
                    "questionId": question["id"],
                    "language": question["language"],
                    "category": question["category"],
                    "provider": provider,
                    "outcome": outcome,
                    "latencyMillis": round((time.monotonic() - started) * 1000),
                    "sourceCount": len(sources),
                    "evidenceCharacters": sum(len(excerpt) for source in sources for excerpt in source["excerpts"]),
                    "sources": sources,
                }
            )
            print(f"{len(records):02d}/{len(questions) * 2} {question['id']} {provider}: {outcome}", flush=True)
    report = {
        "generatedAtEpochMillis": round(time.time() * 1000),
        "requestLimit": MAX_REQUESTS,
        "rateLimitPerSecond": 1 / MINIMUM_INTERVAL_SECONDS,
        "requestCount": budget.count,
        "questionCount": len(questions),
        "records": records,
    }
    output_path.parent.mkdir(parents=True, exist_ok=True)
    output_path.write_text(json.dumps(report, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")
    print(f"report={output_path} requests={budget.count}")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
