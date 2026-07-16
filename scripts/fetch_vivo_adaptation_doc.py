#!/usr/bin/env python3
"""Fetch Vivo open-platform adaptation docs and produce a local review package.

Why this exists
---------------
https://dev.vivo.com.cn/documentCenter/doc/<id> is a SPA shell. The actual body is
loaded from:

    GET https://dev.vivo.com.cn/webapi/doc/info?id=<id>

This tool:
1. pulls the JSON body
2. extracts cleaned plain text + headings
3. optionally scans the local Android repo for related keywords
4. writes a reusable review package under docs/vivo-adaptation/

Examples
--------
  python scripts/fetch_vivo_adaptation_doc.py 832
  python scripts/fetch_vivo_adaptation_doc.py 1010 --scan-repo
  python scripts/fetch_vivo_adaptation_doc.py 832 1010 --scan-repo --out-dir docs/vivo-adaptation
"""

from __future__ import annotations

import argparse
import datetime as dt
import html
import json
import re
import sys
import urllib.error
import urllib.request
from dataclasses import dataclass
from pathlib import Path
from typing import Iterable


DOC_API = "https://dev.vivo.com.cn/webapi/doc/info?id={doc_id}"
DOC_PAGE = "https://dev.vivo.com.cn/documentCenter/doc/{doc_id}"
DEFAULT_UA = (
    "Mozilla/5.0 (compatible; ProjectLumenVivoDocFetcher/1.0; "
    "+https://github.com/Chloemlla/Project-Lumen)"
)

# Keywords that commonly appear in Vivo/Android adaptation guidance and matter
# to this repository's Android app surface.
DEFAULT_SCAN_KEYWORDS = [
    "scheduleAtFixedRate",
    "ScheduledExecutorService",
    "elegantTextHeight",
    "enableOnBackInvokedCallback",
    "BackHandler",
    "onBackPressed",
    "OnBackPressed",
    "predictive",
    "screenOrientation",
    "resizeableActivity",
    "setRequestedOrientation",
    "minAspectRatio",
    "maxAspectRatio",
    "PROPERTY_COMPAT_ALLOW_RESTRICTED_RESIZABILITY",
    "ACCESS_LOCAL_NETWORK",
    "NEARBY_WIFI_DEVICES",
    "BODY_SENSORS",
    "READ_HEART_RATE",
    "usesCleartextTraffic",
    "networkSecurityConfig",
    "FLAG_GRANT_READ_URI_PERMISSION",
    "ACTION_SEND",
    "FileProvider",
    "mediaPlayback",
    "FOREGROUND_SERVICE",
    "USAGE_NOTIFICATION",
    "HiddenApiBypass",
    "removeLaunchSecurityProtection",
    "targetSdk",
    "compileSdk",
]


@dataclass
class DocPayload:
    doc_id: str
    title: str
    version_code: int | None
    update_time_ms: int | None
    bread_crumbs: str
    html_content: str
    plain_text: str
    headings: list[str]


def fetch_doc(doc_id: str, timeout: float = 30.0) -> DocPayload:
    url = DOC_API.format(doc_id=doc_id)
    req = urllib.request.Request(
        url,
        headers={
            "User-Agent": DEFAULT_UA,
            "Accept": "application/json,text/plain,*/*",
            "Referer": DOC_PAGE.format(doc_id=doc_id),
        },
    )
    try:
        with urllib.request.urlopen(req, timeout=timeout) as resp:
            raw = resp.read().decode("utf-8", errors="replace")
    except urllib.error.HTTPError as exc:
        raise SystemExit(f"HTTP {exc.code} while fetching {url}: {exc.reason}") from exc
    except urllib.error.URLError as exc:
        raise SystemExit(f"Network error while fetching {url}: {exc.reason}") from exc

    try:
        payload = json.loads(raw)
    except json.JSONDecodeError as exc:
        raise SystemExit(f"Invalid JSON from {url}") from exc

    if payload.get("code") not in (0, "0", None):
        raise SystemExit(f"Unexpected API code for doc {doc_id}: {payload!r}")

    data = payload.get("data")
    if not isinstance(data, dict):
        raise SystemExit(f"Unexpected API data for doc {doc_id}: {payload!r}")

    title = str(data.get("title") or f"Vivo doc {doc_id}")
    html_content = str(data.get("content") or "")
    if not html_content.strip():
        raise SystemExit(f"Empty content for doc {doc_id}")

    plain = html_to_text(html_content)
    headings = extract_headings(plain)
    return DocPayload(
        doc_id=str(doc_id),
        title=title,
        version_code=_as_int(data.get("versionCode")),
        update_time_ms=_as_int(data.get("updateTime")),
        bread_crumbs=str(data.get("breadCrumbs") or ""),
        html_content=html_content,
        plain_text=plain,
        headings=headings,
    )


def html_to_text(content: str) -> str:
    text = re.sub(r"<br\s*/?>", "\n", content, flags=re.I)
    text = re.sub(r"</p>|</h\d>|</li>|</tr>|</div>", "\n", text, flags=re.I)
    text = re.sub(r"<li[^>]*>", "- ", text, flags=re.I)
    text = re.sub(r"<[^>]+>", "", text)
    text = html.unescape(text)
    text = text.replace("\xa0", " ")
    text = re.sub(r"[ \t]+\n", "\n", text)
    text = re.sub(r"\n{3,}", "\n\n", text)
    return text.strip() + "\n"


def extract_headings(plain_text: str) -> list[str]:
    patterns = [
        re.compile(r"^(?:[一二三四五六七八九十]+、.+)$"),
        re.compile(r"^(?:\d+(?:\.\d+)*\s+.+)$"),
        re.compile(r"^(?:#{1,6}\s+.+)$"),
    ]
    headings: list[str] = []
    for line in plain_text.splitlines():
        stripped = line.strip()
        if not stripped:
            continue
        if any(p.match(stripped) for p in patterns):
            headings.append(stripped)
    # de-dup while preserving order
    seen: set[str] = set()
    unique: list[str] = []
    for item in headings:
        if item in seen:
            continue
        seen.add(item)
        unique.append(item)
    return unique


def _as_int(value: object) -> int | None:
    try:
        return int(value) if value is not None else None
    except (TypeError, ValueError):
        return None


def format_update_time(ms: int | None) -> str:
    if not ms:
        return "unknown"
    try:
        return dt.datetime.fromtimestamp(ms / 1000, tz=dt.timezone.utc).isoformat()
    except (OverflowError, OSError, ValueError):
        return str(ms)


def slugify(value: str) -> str:
    """ASCII-first slug for portable directory names on Windows consoles/tools."""
    value = value.strip().lower()
    # Prefer latin/digits; drop CJK from directory names to avoid console garbling.
    value = re.sub(r"[^a-z0-9]+", "-", value)
    value = re.sub(r"-{2,}", "-", value).strip("-")
    return value or "doc"


def write_package(doc: DocPayload, out_dir: Path) -> Path:
    package_dir = out_dir / f"{doc.doc_id}-{slugify(doc.title)[:48]}"
    package_dir.mkdir(parents=True, exist_ok=True)

    (package_dir / "raw.json").write_text(
        json.dumps(
            {
                "id": doc.doc_id,
                "title": doc.title,
                "versionCode": doc.version_code,
                "updateTime": doc.update_time_ms,
                "breadCrumbs": doc.bread_crumbs,
                "sourcePage": DOC_PAGE.format(doc_id=doc.doc_id),
                "sourceApi": DOC_API.format(doc_id=doc.doc_id),
            },
            ensure_ascii=False,
            indent=2,
        )
        + "\n",
        encoding="utf-8",
    )
    (package_dir / "content.html").write_text(doc.html_content, encoding="utf-8")
    (package_dir / "content.txt").write_text(
        f"{doc.title}\n\n{doc.plain_text}",
        encoding="utf-8",
    )
    (package_dir / "headings.txt").write_text(
        "\n".join(doc.headings) + ("\n" if doc.headings else ""),
        encoding="utf-8",
    )
    return package_dir


def iter_repo_files(repo_root: Path) -> Iterable[Path]:
    include_suffixes = {
        ".kt",
        ".java",
        ".xml",
        ".kts",
        ".gradle",
        ".md",
        ".pro",
        ".properties",
    }
    skip_parts = {
        ".git",
        "build",
        ".gradle",
        "node_modules",
        ".idea",
        "dist",
        "target",
        ".tmp",
    }
    for path in repo_root.rglob("*"):
        if not path.is_file():
            continue
        if any(part in skip_parts for part in path.parts):
            continue
        if path.suffix.lower() not in include_suffixes:
            continue
        yield path


def scan_repo(repo_root: Path, keywords: list[str]) -> dict[str, list[str]]:
    hits: dict[str, list[str]] = {k: [] for k in keywords}
    for path in iter_repo_files(repo_root):
        try:
            text = path.read_text(encoding="utf-8")
        except UnicodeDecodeError:
            try:
                text = path.read_text(encoding="utf-8", errors="ignore")
            except OSError:
                continue
        except OSError:
            continue
        rel = path.relative_to(repo_root).as_posix()
        for keyword in keywords:
            if keyword in text:
                # keep a few concrete line refs
                lines = []
                for idx, line in enumerate(text.splitlines(), start=1):
                    if keyword in line:
                        lines.append(f"{rel}:{idx}:{line.strip()[:160]}")
                        if len(lines) >= 5:
                            break
                hits[keyword].extend(lines)
    return hits


def write_report(
    docs: list[DocPayload],
    package_dirs: list[Path],
    scan_hits: dict[str, list[str]] | None,
    out_dir: Path,
    repo_root: Path,
) -> Path:
    now = dt.datetime.now(tz=dt.timezone.utc).strftime("%Y-%m-%d %H:%M:%SZ")
    lines: list[str] = []
    lines.append("# Vivo Adaptation Doc Review Package")
    lines.append("")
    lines.append(f"- Generated at: `{now}`")
    lines.append(f"- Repo root: `{repo_root}`")
    lines.append(f"- Output dir: `{out_dir}`")
    lines.append("")
    lines.append("## Documents")
    lines.append("")
    for doc, package_dir in zip(docs, package_dirs):
        lines.append(f"### {doc.doc_id} — {doc.title}")
        lines.append("")
        lines.append(f"- Page: {DOC_PAGE.format(doc_id=doc.doc_id)}")
        lines.append(f"- API: `{DOC_API.format(doc_id=doc.doc_id)}`")
        lines.append(f"- Breadcrumbs: {doc.bread_crumbs or 'n/a'}")
        lines.append(f"- Version code: {doc.version_code if doc.version_code is not None else 'n/a'}")
        lines.append(f"- Update time (UTC): {format_update_time(doc.update_time_ms)}")
        lines.append(f"- Package: `{package_dir.relative_to(repo_root).as_posix()}`")
        lines.append("")
        lines.append("Headings:")
        lines.append("")
        if doc.headings:
            for heading in doc.headings[:80]:
                lines.append(f"- {heading}")
            if len(doc.headings) > 80:
                lines.append(f"- ... ({len(doc.headings) - 80} more)")
        else:
            lines.append("- (no headings detected)")
        lines.append("")

    if scan_hits is not None:
        lines.append("## Repo keyword scan")
        lines.append("")
        lines.append(
            "This is a helper scan, not a full compliance verdict. "
            "Use it to jump into likely code paths when reviewing the doc."
        )
        lines.append("")
        any_hit = False
        for keyword, refs in scan_hits.items():
            if not refs:
                continue
            any_hit = True
            lines.append(f"### `{keyword}` ({len(refs)} hit lines, showing up to 8)")
            lines.append("")
            for ref in refs[:8]:
                lines.append(f"- `{ref}`")
            lines.append("")
        if not any_hit:
            lines.append("No default keywords were found in the scanned source set.")
            lines.append("")

    lines.append("## Suggested review flow")
    lines.append("")
    lines.append("1. Read `content.txt` / `headings.txt` for each package.")
    lines.append("2. Map high/medium priority sections to app surfaces:")
    lines.append("   - Manifest / permissions / FGS")
    lines.append("   - Activity orientation / large-screen behavior")
    lines.append("   - Back navigation")
    lines.append("   - Background timers / alarms")
    lines.append("   - Share / FileProvider / intent grants")
    lines.append("   - Network security / cleartext")
    lines.append("3. Update adaptation notes under `docs/ANDROID_*_VIVO_ADAPTATION.md`.")
    lines.append("4. Implement only the gaps that apply to this product.")
    lines.append("")

    report_path = out_dir / "REVIEW.md"
    report_path.write_text("\n".join(lines) + "\n", encoding="utf-8")
    return report_path


def parse_args(argv: list[str]) -> argparse.Namespace:
    parser = argparse.ArgumentParser(
        description="Fetch Vivo adaptation docs and build a local review package.",
    )
    parser.add_argument(
        "doc_ids",
        nargs="+",
        help="Vivo document id(s), e.g. 832 1010",
    )
    parser.add_argument(
        "--out-dir",
        default="docs/vivo-adaptation",
        help="Output directory relative to repo root (default: docs/vivo-adaptation)",
    )
    parser.add_argument(
        "--repo-root",
        default=".",
        help="Repository root (default: current directory)",
    )
    parser.add_argument(
        "--scan-repo",
        action="store_true",
        help="Scan local Android/source files for common adaptation keywords",
    )
    parser.add_argument(
        "--keyword",
        action="append",
        default=[],
        help="Extra keyword for repo scan (repeatable)",
    )
    parser.add_argument(
        "--timeout",
        type=float,
        default=30.0,
        help="HTTP timeout seconds (default: 30)",
    )
    return parser.parse_args(argv)


def main(argv: list[str] | None = None) -> int:
    args = parse_args(argv or sys.argv[1:])
    repo_root = Path(args.repo_root).resolve()
    out_dir = (repo_root / args.out_dir).resolve()
    out_dir.mkdir(parents=True, exist_ok=True)

    docs: list[DocPayload] = []
    package_dirs: list[Path] = []
    for doc_id in args.doc_ids:
        print(f"Fetching Vivo doc {doc_id} ...")
        doc = fetch_doc(doc_id, timeout=args.timeout)
        package_dir = write_package(doc, out_dir)
        docs.append(doc)
        package_dirs.append(package_dir)
        print(f"  title: {doc.title}")
        print(f"  package: {package_dir}")

    scan_hits = None
    if args.scan_repo:
        keywords = list(dict.fromkeys(DEFAULT_SCAN_KEYWORDS + list(args.keyword or [])))
        print(f"Scanning repo for {len(keywords)} keywords ...")
        scan_hits = scan_repo(repo_root, keywords)

    report_path = write_report(docs, package_dirs, scan_hits, out_dir, repo_root)
    print(f"Review report: {report_path}")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
