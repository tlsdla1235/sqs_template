#!/usr/bin/env python3
"""Exercise the sync and async report APIs and summarize response-time behavior."""

from __future__ import annotations

import argparse
import json
import signal
import statistics
import time
from dataclasses import dataclass, field
from datetime import datetime, timezone
from pathlib import Path
from typing import Any
from urllib.error import HTTPError, URLError
from urllib.request import Request, urlopen


@dataclass
class ApiResult:
    mode: str
    title: str
    ok: bool
    status_code: int | None
    response_millis: float | None
    processing_millis: int | None = None
    completion_wait_millis: float | None = None
    report_id: str | None = None
    final_status: str | None = None
    error: str | None = None


@dataclass
class ScenarioResult:
    started_at: str
    finished_at: str
    base_url: str
    duration_seconds: int
    interval_seconds: float
    interrupted: bool = False
    results: list[ApiResult] = field(default_factory=list)


def post_json(base_url: str, path: str, payload: dict[str, Any], timeout: float) -> tuple[int, dict[str, Any], float]:
    body = json.dumps(payload).encode("utf-8")
    request = Request(
        f"{base_url}{path}",
        data=body,
        headers={"Content-Type": "application/json"},
        method="POST",
    )
    started = time.perf_counter()
    with urlopen(request, timeout=timeout) as response:
        elapsed = (time.perf_counter() - started) * 1000
        data = json.loads(response.read().decode("utf-8"))
        return response.status, data, elapsed


def get_json(base_url: str, path: str, timeout: float) -> tuple[int, dict[str, Any], float]:
    request = Request(f"{base_url}{path}", method="GET")
    started = time.perf_counter()
    with urlopen(request, timeout=timeout) as response:
        elapsed = (time.perf_counter() - started) * 1000
        data = json.loads(response.read().decode("utf-8"))
        return response.status, data, elapsed


def run_sync(base_url: str, title: str, timeout: float) -> ApiResult:
    try:
        status_code, data, response_millis = post_json(
            base_url,
            "/sync/reports",
            {"title": title, "type": "SUMMARY"},
            timeout,
        )
        return ApiResult(
            mode="sync",
            title=title,
            ok=200 <= status_code < 300 and data.get("status") == "COMPLETED",
            status_code=status_code,
            response_millis=response_millis,
            processing_millis=data.get("processingMillis"),
            report_id=data.get("reportId"),
            final_status=data.get("status"),
        )
    except (HTTPError, URLError, TimeoutError, json.JSONDecodeError) as error:
        return ApiResult("sync", title, False, getattr(error, "code", None), None, error=str(error))


def run_async(
    base_url: str,
    title: str,
    timeout: float,
    poll_timeout_seconds: float,
    poll_interval_seconds: float,
) -> ApiResult:
    try:
        status_code, data, response_millis = post_json(
            base_url,
            "/reports",
            {"title": title, "type": "SUMMARY"},
            timeout,
        )
        report_id = data.get("reportId")
        if status_code != 202 or not report_id:
            return ApiResult("async", title, False, status_code, response_millis, report_id=report_id)

        poll_started = time.perf_counter()
        deadline = poll_started + poll_timeout_seconds
        last_report: dict[str, Any] = {}
        while time.perf_counter() < deadline:
            get_status, last_report, _ = get_json(base_url, f"/reports/{report_id}", timeout)
            if get_status == 200 and last_report.get("status") in {"COMPLETED", "FAILED"}:
                completion_wait_millis = (time.perf_counter() - poll_started) * 1000
                return ApiResult(
                    mode="async",
                    title=title,
                    ok=last_report.get("status") == "COMPLETED",
                    status_code=status_code,
                    response_millis=response_millis,
                    processing_millis=last_report.get("processingMillis"),
                    completion_wait_millis=completion_wait_millis,
                    report_id=report_id,
                    final_status=last_report.get("status"),
                    error=last_report.get("errorMessage"),
                )
            time.sleep(poll_interval_seconds)

        return ApiResult(
            mode="async",
            title=title,
            ok=False,
            status_code=status_code,
            response_millis=response_millis,
            report_id=report_id,
            final_status=last_report.get("status"),
            error="poll timeout",
        )
    except (HTTPError, URLError, TimeoutError, json.JSONDecodeError) as error:
        return ApiResult("async", title, False, getattr(error, "code", None), None, error=str(error))


def percentile(values: list[float], percentile_rank: float) -> float | None:
    if not values:
        return None
    ordered = sorted(values)
    index = round((len(ordered) - 1) * percentile_rank)
    return ordered[index]


def summarize(results: list[ApiResult]) -> dict[str, Any]:
    summary: dict[str, Any] = {}
    for mode in ("async", "sync"):
        mode_results = [result for result in results if result.mode == mode]
        response_times = [result.response_millis for result in mode_results if result.response_millis is not None]
        processing_times = [result.processing_millis for result in mode_results if result.processing_millis is not None]
        summary[mode] = {
            "count": len(mode_results),
            "success": sum(1 for result in mode_results if result.ok),
            "failed": sum(1 for result in mode_results if not result.ok),
            "response_avg_ms": round(statistics.mean(response_times), 2) if response_times else None,
            "response_p95_ms": round(percentile(response_times, 0.95), 2) if response_times else None,
            "processing_avg_ms": round(statistics.mean(processing_times), 2) if processing_times else None,
        }
    return summary


def as_dict(result: ScenarioResult) -> dict[str, Any]:
    return {
        "started_at": result.started_at,
        "finished_at": result.finished_at,
        "base_url": result.base_url,
        "duration_seconds": result.duration_seconds,
        "interval_seconds": result.interval_seconds,
        "interrupted": result.interrupted,
        "summary": summarize(result.results),
        "results": [item.__dict__ for item in result.results],
    }


def write_outputs(output_dir: Path, result: ScenarioResult) -> tuple[Path, Path]:
    output_dir.mkdir(parents=True, exist_ok=True)
    timestamp = result.started_at.replace(":", "").replace("-", "").replace("+", "Z")
    json_path = output_dir / f"sqs-scenario-{timestamp}.json"
    md_path = output_dir / f"sqs-scenario-{timestamp}.md"
    payload = as_dict(result)
    json_path.write_text(json.dumps(payload, indent=2), encoding="utf-8")

    summary = payload["summary"]
    md_path.write_text(
        "\n".join(
            [
                "# SQS Scenario Test Run",
                "",
                f"- Started: `{result.started_at}`",
                f"- Finished: `{result.finished_at}`",
                f"- Base URL: `{result.base_url}`",
                f"- Duration: `{result.duration_seconds}` seconds",
                f"- Interval: `{result.interval_seconds}` seconds",
                f"- Interrupted: `{result.interrupted}`",
                "",
                "| mode | count | success | failed | avg response ms | p95 response ms | avg processing ms |",
                "| --- | ---: | ---: | ---: | ---: | ---: | ---: |",
                table_row("async", summary["async"]),
                table_row("sync", summary["sync"]),
                "",
                "Interpretation:",
                "",
                "- `async` response time measures only the API request that saves a row and sends an SQS message.",
                "- `async` processing time measures the background worker's actual report generation time.",
                "- `sync` response time includes report generation, so it should be close to the processing time.",
                "",
            ]
        ),
        encoding="utf-8",
    )
    return json_path, md_path


def table_row(mode: str, summary: dict[str, Any]) -> str:
    return (
        f"| {mode} | {summary['count']} | {summary['success']} | {summary['failed']} | "
        f"{display(summary['response_avg_ms'])} | {display(summary['response_p95_ms'])} | "
        f"{display(summary['processing_avg_ms'])} |"
    )


def display(value: Any) -> str:
    return "-" if value is None else str(value)


def main() -> int:
    parser = argparse.ArgumentParser(description="Run a sync-vs-async report API scenario.")
    parser.add_argument("--base-url", default="http://localhost:8081")
    parser.add_argument("--duration-seconds", type=int, default=1800)
    parser.add_argument("--interval-seconds", type=float, default=10)
    parser.add_argument("--timeout-seconds", type=float, default=20)
    parser.add_argument("--poll-timeout-seconds", type=float, default=30)
    parser.add_argument("--poll-interval-seconds", type=float, default=1)
    parser.add_argument("--output-dir", default="docs/test-runs")
    args = parser.parse_args()

    started_at = datetime.now(timezone.utc).isoformat()
    deadline = time.monotonic() + args.duration_seconds
    results: list[ApiResult] = []
    iteration = 1
    interrupted = False
    stop_requested = False

    def request_stop(signum: int, _frame: Any) -> None:
        nonlocal stop_requested
        stop_requested = True
        print(f"stop requested by signal {signum}; writing partial report after current request", flush=True)

    signal.signal(signal.SIGTERM, request_stop)

    try:
        while time.monotonic() < deadline and not stop_requested:
            run_id = datetime.now(timezone.utc).strftime("%Y%m%d%H%M%S")
            async_result = run_async(
                args.base_url,
                f"scenario-async-{run_id}-{iteration}",
                args.timeout_seconds,
                args.poll_timeout_seconds,
                args.poll_interval_seconds,
            )
            print_result(async_result)
            results.append(async_result)

            if stop_requested:
                break

            sync_result = run_sync(
                args.base_url,
                f"scenario-sync-{run_id}-{iteration}",
                args.timeout_seconds,
            )
            print_result(sync_result)
            results.append(sync_result)

            iteration += 1
            if time.monotonic() < deadline and not stop_requested:
                time.sleep(args.interval_seconds)
    except KeyboardInterrupt:
        interrupted = True
        print("interrupted by keyboard; writing partial report", flush=True)

    if stop_requested:
        interrupted = True

    scenario = ScenarioResult(
        started_at=started_at,
        finished_at=datetime.now(timezone.utc).isoformat(),
        base_url=args.base_url,
        duration_seconds=args.duration_seconds,
        interval_seconds=args.interval_seconds,
        interrupted=interrupted,
        results=results,
    )
    json_path, md_path = write_outputs(Path(args.output_dir), scenario)
    print(json.dumps({"summary": summarize(results), "json": str(json_path), "markdown": str(md_path)}, indent=2))
    if interrupted:
        return 130
    return 0 if all(result.ok for result in results) else 1


def print_result(result: ApiResult) -> None:
    print(
        json.dumps(
            {
                "mode": result.mode,
                "ok": result.ok,
                "status_code": result.status_code,
                "response_millis": round(result.response_millis, 2) if result.response_millis is not None else None,
                "processing_millis": result.processing_millis,
                "final_status": result.final_status,
                "error": result.error,
            },
            ensure_ascii=False,
        ),
        flush=True,
    )


if __name__ == "__main__":
    raise SystemExit(main())
