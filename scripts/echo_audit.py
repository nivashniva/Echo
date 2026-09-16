#!/usr/bin/env python3
"""Echo Music repository audit and build-simulation runner.

Scans every tracked source/config file available in the checkout, runs the
most relevant Gradle build/lint/test tasks that exist, and writes JSON+Markdown
reports. Findings are evidence-based static findings; runtime/emulator behavior
cannot be proven without an Android device/emulator.
"""
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
EXCLUDE_DIRS = {".git", ".gradle", "build", ".idea", ".kotlin", ".cxx", "captures"}
SOURCE_EXTS = {".kt", ".kts", ".java", ".xml", ".gradle", ".toml", ".json", ".properties", ".pro"}

ISSUE_RULES = [
    ("CRITICAL", "hardcoded-secret", re.compile(r"(?i)(api[_-]?key|secret|password|token)\\s*=\\s*[\"']([^\"']{8,})[\"']")),
    ("HIGH", "unsafe-null-assertion", re.compile(r"!!")),
    ("HIGH", "webview-js-bridge", re.compile(r"addJavascriptInterface\\s*\\(")),
    ("MEDIUM", "javascript-url-execution", re.compile(r"loadUrl\\s*\\(\\s*[\"']javascript:")),
    ("MEDIUM", "hardcoded-sdk-path", re.compile(r"sdk\\.dir\\s*=\\s*/Users/|sdk\\.dir\\s*=\\s*C:\\\\")),
    ("MEDIUM", "deprecated-new-dsl", re.compile(r"android\\.newDsl\\s*=\\s*false")),
    ("LOW", "todo-fixme", re.compile(r"(?i)\\b(TODO|FIXME|HACK|XXX)\\b")),
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
    "AI": ["/ai/", "AI", "OpenRouter", "Mistral"],
    "Canvas / artwork": ["canvas", "Canvas"],
    "Widgets": ["Widget", "widget"],
    "Database / Room": ["DatabaseDao", "Room", "dao", "entities"],
    "Authentication": ["LoginScreen", "accountInfo", "CookieManager"],
    "Audio effects": ["equalizer", "Equalizer", "audio/", "crossfade"],
}


def files():
    for p in ROOT.rglob("*"):
        if not p.is_file() or any(part in EXCLUDE_DIRS for part in p.parts):
            continue
        if p.suffix in SOURCE_EXTS or p.name in {"AndroidManifest.xml", "gradlew", "gradle.properties"}:
            yield p


def run(cmd: list[str], timeout: int = 1800) -> dict:
    start = time.time()
    try:
        proc = subprocess.run(cmd, cwd=ROOT, text=True, stdout=subprocess.PIPE,
                              stderr=subprocess.STDOUT, timeout=timeout)
        return {"command": " ".join(cmd), "exit_code": proc.returncode,
                "duration_s": round(time.time() - start, 2), "output": proc.stdout[-120000:]}
    except subprocess.TimeoutExpired as e:
        return {"command": " ".join(cmd), "exit_code": 124,
                "duration_s": round(time.time() - start, 2),
                "output": (e.stdout or "")[-120000:] + "\\nTIMEOUT"}
    except Exception as e:
        return {"command": " ".join(cmd), "exit_code": 125,
                "duration_s": round(time.time() - start, 2), "output": repr(e)}


def line_for(text: str, pattern: re.Pattern[str]) -> int:
    m = pattern.search(text)
    return text.count("\\n", 0, m.start()) + 1 if m else 1


def main() -> int:
    all_files = list(files())
    findings = []
    ext_counts = Counter(p.suffix or "[no extension]" for p in all_files)
    module_counts = Counter()
    feature_hits = defaultdict(list)
    total_lines = 0

    for p in all_files:
        rel = p.relative_to(ROOT).as_posix()
        parts = rel.split("/")
        module = parts[0] if len(parts) > 1 else "root"
        module_counts[module] += 1
        try:
            text = p.read_text(encoding="utf-8", errors="replace")
        except Exception as exc:
            findings.append({"severity": "HIGH", "type": "unreadable-file", "file": rel, "detail": str(exc)})
            continue
        total_lines += text.count("\\n") + (1 if text else 0)
        for severity, kind, rule in ISSUE_RULES:
            for m in rule.finditer(text):
                # Secret rule is deliberately reported without the secret value.
                findings.append({"severity": severity, "type": kind, "file": rel,
                                 "line": text.count("\\n", 0, m.start()) + 1,
                                 "detail": "Potential hardcoded credential" if kind == "hardcoded-secret" else m.group(0)[:120]})
                if len([f for f in findings if f["file"] == rel and f["type"] == kind]) >= 20:
                    break
        lower = rel.lower() + "\\n" + text.lower()
        for feature, needles in FEATURES.items():
            if any(n.lower() in lower for n in needles):
                feature_hits[feature].append(rel)

    # Documentation/config consistency checks.
    agent = ROOT / "AGENT.md"
    if agent.exists():
        t = agent.read_text(encoding="utf-8", errors="replace")
        if "previous FOSS" in t and (ROOT / "app/src/foss").exists():
            findings.append({"severity": "MEDIUM", "type": "documentation-drift", "file": "AGENT.md",
                             "detail": "AGENT.md says the FOSS flavor was removed, but app/src/foss exists."})
        if "upcomingupdate.json" in t and not (ROOT / "upcomingupdate.json").exists():
            findings.append({"severity": "LOW", "type": "missing-documentation-file", "file": "AGENT.md",
                             "detail": "AGENT.md requires upcomingupdate.json, but it is absent."})

    gp = ROOT / "gradle.properties"
    if gp.exists():
        t = gp.read_text(encoding="utf-8", errors="replace")
        if re.search(r"(?m)^sdk\\.dir\\s*=\\s*/Users/", t):
            findings.append({"severity": "HIGH", "type": "nonportable-sdk-path", "file": "gradle.properties",
                             "detail": "sdk.dir points to a developer-local macOS path."})

    # Build simulation. Prefer exact known debug target, then fall back to assembleDebug.
    gradlew = ROOT / "gradlew"
    build_results = []
    if gradlew.exists():
        os.chmod(gradlew, os.stat(gradlew).st_mode | 0o111)
        build_results.append(run(["./gradlew", "assembleUniversalFossDebug", "--stacktrace", "--no-daemon"], 2400))
        build_results.append(run(["./gradlew", "test", "--continue", "--stacktrace", "--no-daemon"], 2400))
        build_results.append(run(["./gradlew", "lintUniversalFossDebug", "--stacktrace", "--no-daemon"], 2400))
    else:
        build_results.append({"command": "gradlew missing", "exit_code": 126, "duration_s": 0, "output": ""})

    compiler_errors = []
    for result in build_results:
        out = result["output"]
        for line in out.splitlines():
            if re.search(r"(^|\\s)e: .*:(?:\\d+):(?:\\d+):|Unresolved reference|Compilation error|FAILURE: Build failed", line):
                compiler_errors.append({"command": result["command"], "message": line[-500:]})

    counts = Counter(f["severity"] for f in findings)
    summary = {
        "repository": "nivash01/Echo",
        "commit": os.getenv("GITHUB_SHA", "unknown"),
        "files_scanned": len(all_files),
        "source_lines_scanned": total_lines,
        "extensions": dict(ext_counts),
        "modules": dict(module_counts),
        "features": {k: {"detected": bool(v), "files": len(v), "sample": sorted(v)[:12]} for k, v in feature_hits.items()},
        "findings": findings,
        "finding_counts": dict(counts),
        "builds": [{k: v for k, v in r.items() if k != "output"} for r in build_results],
        "compiler_error_candidates": compiler_errors,
    }
    (REPORT_DIR / "echo_audit.json").write_text(json.dumps(summary, indent=2), encoding="utf-8")

    md = ["# Echo Music — Python Repository Audit", "", f"Commit: `{summary['commit']}`", "",
          f"Files scanned: **{len(all_files)}**", f"Source/config lines scanned: **{total_lines:,}**", "",
          "## Build simulation", ""]
    for r in build_results:
        status = "PASS" if r["exit_code"] == 0 else f"FAIL ({r['exit_code']})"
        md.append(f"- **{status}** `{r['command']}` — {r['duration_s']}s")
    md += ["", "## Finding counts", ""]
    for sev in ("CRITICAL", "HIGH", "MEDIUM", "LOW"):
        md.append(f"- {sev}: **{counts.get(sev, 0)}**")
    md += ["", "## Feature detection", ""]
    for name, info in summary["features"].items():
        md.append(f"- **{name}:** {'detected' if info['detected'] else 'not detected'} ({info['files']} files)")
    md += ["", "## Concrete findings", ""]
    for f in findings:
        loc = f.get("file", "unknown") + (f":{f['line']}" if f.get("line") else "")
        md.append(f"- **{f['severity']} — {f['type']}** `{loc}` — {f['detail']}")
    if compiler_errors:
        md += ["", "## Compiler/build error candidates", ""]
        for e in compiler_errors[:100]:
            md.append(f"- `{e['command']}`: `{e['message']}`")
    md += ["", "## Method limitations", "",
           "This is a repository-wide static + Gradle build simulation. It scans every source/config file in the checkout and executes available Gradle build/test/lint tasks. It does not replace Android emulator/device tests, network integration tests, media DRM tests, or real user interaction tests."]
    (REPORT_DIR / "echo_audit.md").write_text("\\n".join(md) + "\\n", encoding="utf-8")
    print("\\n".join(md))
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
