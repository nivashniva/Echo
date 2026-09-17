#!/usr/bin/env python3
"""Repository-wide static audit plus Universal GMS Gradle verification."""
from __future__ import annotations

import json
import os
import re
import subprocess
import time
from collections import Counter, defaultdict
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
REPORT_DIR = ROOT / "audit-report"
REPORT_DIR.mkdir(exist_ok=True)
EXCLUDE_DIRS = {".git", ".gradle", "build", ".idea", ".kotlin", ".cxx", "captures", "audit-report"}
SOURCE_EXTS = {".kt", ".kts", ".java", ".xml", ".gradle", ".toml", ".json", ".properties", ".pro"}

ISSUE_RULES = [
    ("CRITICAL", "hardcoded-secret", re.compile(r"(?i)(api[_-]?key|secret|password|token)\s*=\s*[\"']([^\"']{8,})[\"']")),
    ("HIGH", "unsafe-null-assertion", re.compile(r"!!")),
    ("HIGH", "webview-js-bridge", re.compile(r"addJavascriptInterface\s*\(")),
    ("MEDIUM", "javascript-url-execution", re.compile(r"loadUrl\s*\(\s*[\"']javascript:")),
    ("MEDIUM", "hardcoded-sdk-path", re.compile(r"sdk\.dir\s*=\s*/Users/|sdk\.dir\s*=\s*C:\\")),
    ("LOW", "todo-fixme", re.compile(r"(?i)\b(TODO|FIXME|HACK|XXX)\b")),
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
        if path.suffix in SOURCE_EXTS or path.name in {"AndroidManifest.xml", "gradlew"}:
            yield path


def run(command: list[str], timeout: int = 2400) -> dict:
    started = time.time()
    try:
        proc = subprocess.run(command, cwd=ROOT, text=True, stdout=subprocess.PIPE,
                              stderr=subprocess.STDOUT, timeout=timeout)
        return {"command": " ".join(command), "exit_code": proc.returncode,
                "duration_s": round(time.time() - started, 2), "output": proc.stdout[-150000:]}
    except subprocess.TimeoutExpired as exc:
        output = exc.stdout or ""
        if isinstance(output, bytes):
            output = output.decode(errors="replace")
        return {"command": " ".join(command), "exit_code": 124,
                "duration_s": round(time.time() - started, 2),
                "output": output[-150000:] + "\nTIMEOUT"}


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
            text = path.read_text(encoding="utf-8", errors="replace")
        except Exception as exc:
            findings.append({"severity": "HIGH", "type": "unreadable-file", "file": rel, "detail": str(exc)})
            continue
        total_lines += text.count("\n") + bool(text)
        for severity, kind, rule in ISSUE_RULES:
            for match in list(rule.finditer(text))[:25]:
                detail = "Potential hardcoded credential; value intentionally omitted" if kind == "hardcoded-secret" else match.group(0)[:120]
                findings.append({"severity": severity, "type": kind, "file": rel,
                                 "line": text.count("\n", 0, match.start()) + 1, "detail": detail})
        searchable = (rel + "\n" + text).lower()
        for feature, needles in FEATURES.items():
            if any(needle.lower() in searchable for needle in needles):
                feature_hits[feature].append(rel)

    build_gradle = ROOT / "app" / "build.gradle.kts"
    if build_gradle.exists():
        text = build_gradle.read_text(encoding="utf-8", errors="replace")
        if re.search(r'create\("foss"\)', text):
            findings.append({"severity": "HIGH", "type": "stale-foss-flavor", "file": "app/build.gradle.kts",
                             "detail": "The repository is GMS-only but still defines a FOSS product flavor."})
        if re.search(r'(LASTFM_API_KEY|LASTFM_SECRET)\s*[^\n]*=\s*[\"\'][^\"\']{8,}[\"\']', text):
            findings.append({"severity": "CRITICAL", "type": "hardcoded-lastfm-secret", "file": "app/build.gradle.kts",
                             "detail": "Last.fm credentials are committed instead of being read from local.properties or environment variables."})

    if (ROOT / "app/src/foss").exists():
        findings.append({"severity": "MEDIUM", "type": "stale-foss-source-set", "file": "app/src/foss",
                         "detail": "The obsolete FOSS source set is still present."})

    gradle_properties = ROOT / "gradle.properties"
    if gradle_properties.exists():
        text = gradle_properties.read_text(encoding="utf-8", errors="replace")
        if re.search(r"(?m)^sdk\.dir\s*=\s*/Users/", text):
            findings.append({"severity": "HIGH", "type": "nonportable-sdk-path", "file": "gradle.properties",
                             "detail": "sdk.dir points to a developer-local macOS path."})

    build_results = []
    gradlew = ROOT / "gradlew"
    if gradlew.exists():
        os.chmod(gradlew, os.stat(gradlew).st_mode | 0o111)
        build_results.append(run(["./gradlew", ":app:compileUniversalGmsDebugKotlin", "--stacktrace", "--no-daemon"]))
        build_results.append(run(["./gradlew", "assembleUniversalGmsDebug", "--stacktrace", "--no-daemon"]))
        build_results.append(run(["./gradlew", "lintUniversalGmsDebug", "--stacktrace", "--no-daemon"]))
    else:
        build_results.append({"command": "gradlew missing", "exit_code": 126, "duration_s": 0, "output": ""})

    compiler_errors = []
    for result in build_results:
        for line in result["output"].splitlines():
            if re.search(r"(^|\s)e: .*:\d+:\d+:", line) or "Unresolved reference" in line or "Compilation error" in line or "FAILURE: Build failed" in line:
                compiler_errors.append({"command": result["command"], "message": line[-700:]})

    finding_counts = Counter(item["severity"] for item in findings)
    summary = {
        "repository": "nivash01/Echo",
        "commit": os.getenv("GITHUB_SHA", "unknown"),
        "files_scanned": len(files),
        "source_lines_scanned": int(total_lines),
        "extensions": dict(ext_counts),
        "modules": dict(module_counts),
        "features": {name: {"detected": bool(paths), "files": len(paths), "sample": sorted(paths)[:15]} for name, paths in feature_hits.items()},
        "finding_counts": dict(finding_counts),
        "findings": findings,
        "builds": [{k: v for k, v in result.items() if k != "output"} for result in build_results],
        "compiler_error_candidates": compiler_errors,
    }
    (REPORT_DIR / "echo_audit.json").write_text(json.dumps(summary, indent=2), encoding="utf-8")

    report = ["# Nivukx — Python Repository Audit", "", f"Commit: `{summary['commit']}`", "",
              f"Files scanned: **{len(files)}**", f"Source/config lines scanned: **{int(total_lines):,}**", "",
              "## Universal GMS verification", ""]
    for result in build_results:
        status = "PASS" if result["exit_code"] == 0 else f"FAIL ({result['exit_code']})"
        report.append(f"- **{status}** `{result['command']}` — {result['duration_s']}s")
    report += ["", "## Finding counts", ""]
    for severity in ("CRITICAL", "HIGH", "MEDIUM", "LOW"):
        report.append(f"- {severity}: **{finding_counts.get(severity, 0)}**")
    report += ["", "## Findings", ""]
    for item in findings:
        location = item["file"] + (f":{item['line']}" if "line" in item else "")
        report.append(f"- **{item['severity']} — {item['type']}** `{location}` — {item['detail']}")
    report += ["", "## Method limitation", "",
               "This audit is repository-wide static analysis plus real Universal GMS Gradle compile/build/lint execution. It cannot prove runtime behavior requiring an Android emulator/device, authenticated accounts, live services, DRM, Bluetooth/Cast hardware, sensors, or human UI interaction."]
    (REPORT_DIR / "echo_audit.md").write_text("\n".join(report) + "\n", encoding="utf-8")
    print("\n".join(report))
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
