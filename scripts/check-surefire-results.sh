#!/usr/bin/env bash
set -euo pipefail

boot_root="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"

python3 - "$boot_root" <<'PY'
from pathlib import Path
import sys
import xml.etree.ElementTree as ET

root = Path(sys.argv[1])
reports = sorted(root.glob("**/target/surefire-reports/TEST-*.xml"))
if not reports:
    raise SystemExit("[ainer-test-results] ERROR: no Surefire XML reports found")

totals = {"tests": 0, "failures": 0, "errors": 0, "skipped": 0}
for report in reports:
    suite = ET.parse(report).getroot()
    for name in totals:
        totals[name] += int(suite.attrib.get(name, "0"))

summary = ", ".join(f"{name}={value}" for name, value in totals.items())
print(f"[ainer-test-results] {summary}")

if totals["tests"] == 0:
    raise SystemExit("[ainer-test-results] ERROR: no tests were executed")
if totals["failures"] or totals["errors"] or totals["skipped"]:
    raise SystemExit(
        "[ainer-test-results] ERROR: release-quality CI requires "
        "failures=0, errors=0 and skipped=0"
    )
PY
