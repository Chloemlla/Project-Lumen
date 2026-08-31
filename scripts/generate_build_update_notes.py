#!/usr/bin/env python3
"""Generate the per-artifact update notes asset from one repository commit."""

from __future__ import annotations

import argparse
import json
from pathlib import Path
import re
import subprocess


CONVENTIONAL_SUBJECT = re.compile(r"^[A-Za-z]+(?:\([^)]+\))?!?:\s*(.+)$")
COMMIT_TRAILER = re.compile(r"^[A-Za-z][A-Za-z-]*:\s")


def git_message(repository_root: Path, commit_hash: str, field: str) -> str:
    try:
        result = subprocess.run(
            [
                "git",
                "-C",
                str(repository_root),
                "show",
                "-s",
                f"--format={field}",
                "--encoding=UTF-8",
                commit_hash,
            ],
            check=False,
            capture_output=True,
            text=True,
            encoding="utf-8",
            errors="replace",
        )
    except OSError:
        return ""
    if result.returncode != 0:
        return ""
    return result.stdout.strip()


def strip_trailers(body: str) -> str:
    return "\n".join(
        line for line in body.splitlines() if not COMMIT_TRAILER.match(line.strip())
    )


def extract_highlights(subject: str, body: str) -> list[str]:
    highlights: list[str] = []
    for raw_line in body.splitlines():
        line = raw_line.strip()
        if line.startswith(("- ", "* ")):
            highlight = line[2:].strip()
            if highlight and highlight not in highlights:
                highlights.append(highlight)

    if not highlights:
        conventional_match = CONVENTIONAL_SUBJECT.match(subject)
        if conventional_match:
            summary = conventional_match.group(1).strip()
            if summary:
                highlights.append(summary)
    return highlights


def parse_args() -> argparse.Namespace:
    parser = argparse.ArgumentParser()
    parser.add_argument("--output", type=Path, required=True)
    parser.add_argument("--repository-root", type=Path, required=True)
    parser.add_argument("--version-name", required=True)
    parser.add_argument("--version-code", type=int, required=True)
    parser.add_argument("--build-time-utc-millis", type=int, required=True)
    parser.add_argument("--commit-hash", required=True)
    parser.add_argument("--short-hash", required=True)
    return parser.parse_args()


def main() -> None:
    args = parse_args()
    subject = git_message(args.repository_root, args.commit_hash, "%s")
    # The commit body ships inside the APK and is shown to users, so only the curated
    # bullet highlights are published; raw bodies carry internal paths and tool trailers.
    body = strip_trailers(git_message(args.repository_root, args.commit_hash, "%b"))
    highlights = extract_highlights(subject, body)
    payload = {
        "versionName": args.version_name,
        "versionCode": args.version_code,
        "commitHash": args.commit_hash,
        "shortHash": args.short_hash,
        "buildTimeUtcMillis": args.build_time_utc_millis,
        "title": subject or f"Project Lumen {args.version_name}",
        "body": "\n".join(highlights),
        "highlights": highlights,
    }
    args.output.parent.mkdir(parents=True, exist_ok=True)
    args.output.write_text(
        json.dumps(payload, ensure_ascii=False, indent=2) + "\n",
        encoding="utf-8",
        newline="\n",
    )


if __name__ == "__main__":
    main()
