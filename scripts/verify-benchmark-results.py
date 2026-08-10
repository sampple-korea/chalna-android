#!/usr/bin/env python3
"""Fail CI when stable macrobenchmark medians exceed the accepted noisy-emulator budget."""

from __future__ import annotations

import json
import pathlib
import sys


def main() -> int:
    if len(sys.argv) != 3:
        raise SystemExit("usage: verify-benchmark-results.py BASELINE RESULT")
    baseline_path = pathlib.Path(sys.argv[1])
    result_path = pathlib.Path(sys.argv[2])
    baseline = json.loads(baseline_path.read_text(encoding="utf-8"))
    result = json.loads(result_path.read_text(encoding="utf-8"))
    actual_by_name = {entry["name"]: entry for entry in result.get("benchmarks", [])}
    report: dict[str, object] = {
        "sourceRunId": baseline["sourceRunId"],
        "result": str(result_path),
        "benchmarks": {},
    }
    failed = False
    for name, budget in baseline["benchmarks"].items():
        entry = actual_by_name.get(name)
        if entry is None:
            raise SystemExit(f"missing benchmark result: {name}")
        metric = budget["metric"]
        actual = float(entry["metrics"][metric]["median"])
        accepted = float(budget["median"])
        threshold = accepted * (1.0 + float(budget["maxRegressionPercent"]) / 100.0) + float(
            budget["noiseAllowanceMs"]
        )
        passed = actual <= threshold
        failed = failed or not passed
        report["benchmarks"][name] = {
            "metric": metric,
            "acceptedMedian": accepted,
            "actualMedian": actual,
            "threshold": threshold,
            "passed": passed,
        }
        print(f"{name}: median={actual:.3f}ms threshold={threshold:.3f}ms passed={passed}")
    pathlib.Path("benchmark-gate.json").write_text(
        json.dumps(report, ensure_ascii=False, indent=2) + "\n",
        encoding="utf-8",
    )
    return 1 if failed else 0


if __name__ == "__main__":
    raise SystemExit(main())
