#!/usr/bin/env python3
"""Enforce that the coverage manifest stays in lock-step with the pinned OpenAPI spec.

Fails (exit 1) if:
  * the spec defines an operation that the manifest does not classify  -> UNCLASSIFIED
  * the manifest lists an operation the spec no longer defines         -> STALE
  * a manifest entry has an unknown/missing status                     -> BAD STATUS

Run locally:  python3 contracts/check_coverage.py
Run in CI:    invoked by .github/workflows/spec-drift.yml

Pure stdlib + PyYAML (PyYAML is the only non-stdlib dep; CI installs it).
"""
from __future__ import annotations

import pathlib
import sys

import yaml

HERE = pathlib.Path(__file__).resolve().parent
SPEC = HERE / "auth.openapi.yaml"
MANIFEST = HERE / "coverage-manifest.yaml"

HTTP_METHODS = {"get", "post", "put", "patch", "delete", "head", "options", "trace"}
VALID_STATUS = {"covered", "out-of-scope", "on-demand"}


def spec_operations(path: pathlib.Path) -> set[tuple[str, str]]:
    spec = yaml.safe_load(path.read_text())
    ops: set[tuple[str, str]] = set()
    for route, item in (spec.get("paths") or {}).items():
        if not isinstance(item, dict):
            continue
        for method in item:
            if method.lower() in HTTP_METHODS:
                ops.add((method.upper(), route))
    return ops


def manifest_operations(path: pathlib.Path) -> tuple[set[tuple[str, str]], list[str]]:
    data = yaml.safe_load(path.read_text())
    ops: set[tuple[str, str]] = set()
    errors: list[str] = []
    for entry in data.get("endpoints") or []:
        method = str(entry.get("method", "")).upper()
        route = str(entry.get("path", ""))
        status = entry.get("status")
        key = (method, route)
        if not method or not route:
            errors.append(f"  manifest entry missing method/path: {entry!r}")
            continue
        if status not in VALID_STATUS:
            errors.append(f"  {method} {route}: bad status {status!r} (want one of {sorted(VALID_STATUS)})")
        if key in ops:
            errors.append(f"  duplicate manifest entry: {method} {route}")
        ops.add(key)
    return ops, errors


def fmt(ops: set[tuple[str, str]]) -> list[str]:
    return [f"  {m} {p}" for m, p in sorted(ops, key=lambda o: (o[1], o[0]))]


def main() -> int:
    if not SPEC.exists():
        print(f"ERROR: pinned spec not found at {SPEC}", file=sys.stderr)
        return 1
    if not MANIFEST.exists():
        print(f"ERROR: coverage manifest not found at {MANIFEST}", file=sys.stderr)
        return 1

    spec_ops = spec_operations(SPEC)
    man_ops, errors = manifest_operations(MANIFEST)

    unclassified = spec_ops - man_ops
    stale = man_ops - spec_ops

    if unclassified:
        errors.append("UNCLASSIFIED — in the spec but not in coverage-manifest.yaml:")
        errors += fmt(unclassified)
        errors.append("  -> audit each, then add it to coverage-manifest.yaml with a status.")
    if stale:
        errors.append("STALE — in coverage-manifest.yaml but not in the pinned spec:")
        errors += fmt(stale)
        errors.append("  -> the pinned spec dropped these; remove them from the manifest.")

    if errors:
        print("Coverage manifest is out of sync with the pinned OpenAPI spec:\n")
        print("\n".join(errors))
        return 1

    print(f"OK: all {len(spec_ops)} spec operations are classified in coverage-manifest.yaml.")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
