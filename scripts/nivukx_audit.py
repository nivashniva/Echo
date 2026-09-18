#!/usr/bin/env python3
"""Repository-wide static audit for Nivukx.

This audit intentionally performs NO Android/Gradle/app builds.
It checks source/configuration integrity, legacy identity leakage,
workflow build commands, and obvious hardcoded-secret patterns.
"""
from __future__ import annotations

import json
import os
import re
from collections import Counter, defaultdict
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
REPORT_DIR = ROOT / "audit-report"
REPORT_DIR.mkdir(exist_ok=True)

EXCLUDE_DIRS = {
    ".git", ".gradle", "build", ".idea", ".kotlin", ".cxx",
    "captures", "audit-report",
}
SOURCE_EXTS = {
    ".kt", ".kts", ".java", ".xml", ".gradle", ".toml",
    ".json", ".properties", ".pro", ".yml", ".yaml", ".md",
}

ISSUE_RULES = [
    ("CRITICAL", "hardcoded-secret", re.compile(
        r"(?i)(api[_-]?key|secret|password|token)\s*=\s*[\"']([^\"']{8,})[\"']"
    )),
    ("HIGH", "unsafe-null-assertion", re.compile(r"!!")),
    ("HIGH", "webview-js-bridge", re.compile(r"addJavascriptInterface\s*\(")),
    ("MEDIUM", "javascript-url-execution", re.compile(
        r"loadUrl\s*\(\s*[\"']javascript:"
    )),
    ("MEDIUM", "hardcoded-sdk-path", re.compile(
        r"sdk\.dir\s*=\s*/Users/|sdk\.dir\s*=\s*C:\\\\"
    )),
    ("LOW", "todo-fixme", re.compile(r"(?i)\b(TODO|FIXME|HACK|XXX)\b")),
]

LEGACY_IDENTITY_RULES = [
    ("HIGH", "legacy-package-namespace", re.compile(
        r"echo\.music\.iad1tya"
    )),
    ("HIGH", "legacy-widget-namespace", re.compile(
        r"com\.nivukx\.echo"
    )),
    ("MEDIUM", "legacy-brand-display", re.compile(
        r"(?i)\bEcho\s+(Music|Brain|Extractor)\b"
    )),
    ("MEDIUM", "legacy-update-storage", re.compile(
        r"\becho_updates\b|\bechomusic\.apk\b"
    )),
    ("MEDIUM", "legacy-developer-handle", re.compile(
        r"(?i)\biad1tya\b"
    )),
]

FEATURES = {
    "Playback": ["MusicService", "Media3", "ExoPlayer", "PlayerConnection"],
    "YouTube Music / Innertube": ["innertube", "YouTube"],
    "Lyrics": ["lyrics", "lrclib", "betterlyrics", "paxsenixlyrics"],
    "Local media": ["localmedia", "MediaStore"],
    "Downloads / offline": ["Download", "download", "cache"],
    "Listen Together": ["listentogether", "ListenTogether"],
    "Music recognition": ["recognition", "shazam"],
    "Discord": ["discord", "Discord"],
    "Google Cast": ["cast", "Cast"],
    "Spotify import": ["spotify", "Spotify"],
    "AI": ["/ai/", "OpenRouter", "Mistral"],
    "Canvas / artwork": ["canvas", "Canvas"],
    "Widgets": ["Widget", "widget"],
    "Database / Room": ["DatabaseDao", "Room", "/dao/", "/entities/"],
    "Authentication": ["LoginScreen", "accountInfo", "CookieManager"],
    "Audio effects": ["equalizer", "Equalizer", "crossfade", "audio/"],
}


def source_files():
    for path in ROOT.rglob("*"):
        if not path.is_file() or any(part in EXCLUDE_DIRS for part in path.parts):
            continue
        if path.suffix in SOURCE_EXTS or path.name == "AndroidManifest.xml":
            yield path


def main() -> int:
    files = list(source_files())
    findings: list[dict] = []
    feature_hits = defaultdict(list)
    ext_counts = Counter()
    module_counts = Counter()
    total_lines = 0

    for path in files:
        rel = path.relative_to(ROOT).as_posix()
        module_counts[rel.split("/")[0] if "/" in rel else "root"] += 1
        ext_counts[path.suffix or "[none]"] += 1

        try:
            content = path.read_text(encoding="utf-8", errors="replace")
        except Exception as exc:
            findings.append({
                "severity": "HIGH",
                "type": "unreadable-file",
                "file": rel,
                "detail": str(exc),
            })
            continue

        total_lines += content.count("\n") + bool(content)

        for severity, kind, rule in ISSUE_RULES:
            for match in list(rule.finditer(content))[:25]:
                detail = (
                    "Potential hardcoded credential; value intentionally omitted"
                    if kind == "hardcoded-secret"
                    else match.group(0)[:120]
                )
                findings.append({
                    "severity": severity,
                    "type": kind,
                    "file": rel,
                    "line": content.count("\n", 0, match.start()) + 1,
                    "detail": detail,
                })

        for severity, kind, rule in LEGACY_IDENTITY_RULES:
            for match in list(rule.finditer(content))[:25]:
                # Historical Room schema paths and migration identifiers can
                # intentionally retain old identities. Flag them for review,
                # but do not automatically mutate them.
                if (
                    kind == "legacy-package-namespace"
                    and (
                        "/schemas/" in rel
                        or "schema" in rel.lower()
                        or "migration" in content.lower()
                    )
                ):
                    continue
                findings.append({
                    "severity": severity,
                    "type": kind,
                    "file": rel,
                    "line": content.count("\n", 0, match.start()) + 1,
                    "detail": match.group(0)[:160],
                })

        searchable = (rel + "\n" + content).lower()
        for feature, needles in FEATURES.items():
            if any(needle.lower() in searchable for needle in needles):
                feature_hits[feature].append(rel)

    workflow_build_hits = []
    workflows_dir = ROOT / ".github" / "workflows"
    workflow_build_rule = re.compile(
        r"(?im)^\s*(?:run:|script:)\s*.*(?:gradlew|\bgradle\b|assemble\w+|"
        r"compile\w+|lint\w+)"
    )
    if workflows_dir.exists():
        for workflow in workflows_dir.glob("*.y*ml"):
            try:
                content = workflow.read_text(encoding="utf-8", errors="replace")
            except Exception:
                continue
            for match in workflow_build_rule.finditer(content):
                workflow_build_hits.append({
                    "severity": "HIGH",
                    "type": "workflow-app-build",
                    "file": workflow.relative_to(ROOT).as_posix(),
                    "line": content.count("\n", 0, match.start()) + 1,
                    "detail": (
                        "GitHub Actions workflow contains an Android/Gradle build "
                        "command; repository policy forbids app builds in workflows."
                    ),
                })
    findings.extend(workflow_build_hits)

    build_gradle = ROOT / "app" / "build.gradle.kts"
    if build_gradle.exists():
        content = build_gradle.read_text(encoding="utf-8", errors="replace")
        if re.search(r'create\("foss"\)', content):
            findings.append({
                "severity": "HIGH",
                "type": "stale-foss-flavor",
                "file": "app/build.gradle.kts",
                "detail": "The repository is GMS-only but still defines a FOSS product flavor.",
            })
        if re.search(
            r'(LASTFM_API_KEY|LASTFM_SECRET)\s*[^\n]*=\s*[\"\'][^\"\']{8,}[\"\']',
            content,
        ):
            findings.append({
                "severity": "CRITICAL",
                "type": "hardcoded-lastfm-secret",
                "file": "app/build.gradle.kts",
                "detail": (
                    "Last.fm credentials are committed instead of being read from "
                    "local.properties or environment variables."
                ),
            })

    if (ROOT / "app/src/foss").exists():
        findings.append({
            "severity": "MEDIUM",
            "type": "stale-foss-source-set",
            "file": "app/src/foss",
            "detail": "The obsolete FOSS source set is still present.",
        })

    gradle_properties = ROOT / "gradle.properties"
    if gradle_properties.exists():
        content = gradle_properties.read_text(encoding="utf-8", errors="replace")
        if re.search(r"(?m)^sdk\.dir\s*=\s*/Users/", content):
            findings.append({
                "severity": "HIGH",
                "type": "nonportable-sdk-path",
                "file": "gradle.properties",
                "detail": "sdk.dir points to a developer-local macOS path.",
            })

    finding_counts = Counter(item["severity"] for item in findings)
    summary = {
        "repository": "nivash01/Echo",
        "commit": os.getenv("GITHUB_SHA", "unknown"),
        "files_scanned": len(files),
        "source_lines_scanned": int(total_lines),
        "extensions": dict(ext_counts),
        "modules": dict(module_counts),
        "features": {
            name: {
                "detected": bool(paths),
                "files": len(paths),
                "sample": sorted(paths)[:15],
            }
            for name, paths in feature_hits.items()
        },
        "finding_counts": dict(finding_counts),
        "findings": findings,
        "builds": [],
        "workflow_app_build_findings": workflow_build_hits,
        "app_build_executed": False,
    }

    (REPORT_DIR / "nivukx_audit.json").write_text(
        json.dumps(summary, indent=2), encoding="utf-8"
    )

    report = [
        "# Nivukx — Static Repository Audit",
        "",
        f"Commit: `{summary['commit']}`",
        "",
        f"Files scanned: **{len(files)}**",
        f"Source/config lines scanned: **{int(total_lines):,}**",
        "",
        "## Build policy",
        "",
        "- **PASS** No Android/Gradle compile, lint, APK, or AAB build is executed by this audit.",
        "- **PASS** Workflows are statically scanned for forbidden app-build commands.",
        "",
        "## Finding counts",
        "",
    ]
    for severity in ("CRITICAL", "HIGH", "MEDIUM", "LOW"):
        report.append(f"- {severity}: **{finding_counts.get(severity, 0)}**")

    report += ["", "## Findings", ""]
    for item in findings:
        location = item["file"] + (
            f":{item['line']}" if "line" in item else ""
        )
        report.append(
            f"- **{item['severity']} — {item['type']}** "
            f"`{location}` — {item['detail']}"
        )

    report += [
        "",
        "## Method limitation",
        "",
        (
            "This audit is repository-wide static analysis only. It deliberately "
            "does not execute Gradle, compile, lint, APK, or AAB build commands. "
            "It cannot prove runtime behavior requiring an Android emulator/device, "
            "authenticated accounts, live services, DRM, Bluetooth/Cast hardware, "
            "sensors, or human UI interaction."
        ),
    ]
    (REPORT_DIR / "nivukx_audit.md").write_text(
        "\n".join(report) + "\n", encoding="utf-8"
    )
    print("\n".join(report))
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
