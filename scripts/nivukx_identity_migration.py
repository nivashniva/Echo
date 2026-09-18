#!/usr/bin/env python3
from __future__ import annotations
import os
import shutil
import subprocess
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]

SKIP_DIRS = {
    ".git", ".gradle", "build", ".idea", ".kotlin", "node_modules"
}

TEXT_EXTENSIONS = {
    ".kt", ".kts", ".java", ".xml", ".json", ".md", ".txt", ".yml", ".yaml",
    ".properties", ".pro", ".gradle", ".toml", ".py", ".js", ".html", ".css",
    ".sh", ".cmake", ".c", ".cc", ".cpp", ".h", ".hpp", ".proto"
}

EXACT_REPLACEMENTS = [
    ("echo.music.iad1tya", "com.nivukx.music"),
    ("echo/music/iad1tya", "com/nivukx/music"),
    ("com.music.echo", "com.nivukx.music"),
    ("com/music/echo", "com/nivukx/music"),
    ("content://com.echomusic.music", "content://com.nivukx.music"),
    ("echo.music.iad1tya.widget", "com.nivukx.music.widget"),
    ("echo.music.iad1tya.", "com.nivukx.music."),
    ("com.music.echo.", "com.nivukx.music."),
    ("EchoNotificationProvider", "NivukxNotificationProvider"),
    ("EchoMusicCanvasProvider", "NivukxCanvasProvider"),
    ("EchoMusicCanvas", "NivukxCanvas"),
    ("EchoMotion", "NivukxMotion"),
    ("EchoMusic", "Nivukx"),
    ("Echo Music", "Nivukx"),
    ("echomusic_1", "nivukx_1"),
    ("echomusic_canvas", "nivukx_canvas"),
    ('rootProject.name = "echomusic"', 'rootProject.name = "Nivukx"'),
    ('name = "Theme.echomusic"', 'name = "Theme.Nivukx"'),
    ('android:theme="@style/Theme.echomusic"', 'android:theme="@style/Theme.Nivukx"'),
    ('@style/Theme.echomusic', '@style/Theme.Nivukx'),
    ('"echomusic.apk"', '"nivukx.apk"'),
    ('"echomusic.apk.part"', '"nivukx.apk.part"'),
    ('"echo_temp.zip"', '"nivukx_temp.zip"'),
    ('"echo_temp.zip.part"', '"nivukx_temp.zip.part"'),
    ('echomusic/$appVersion', 'nivukx/$appVersion'),
    ('X-Title", "echomusic', 'X-Title", "Nivukx'),
    ('https://github.com/EchoMusicApp/Echo-Music', 'https://github.com/nivash01/Echo'),
    ('https://api.github.com/repos/EchoMusicApp/Echo-Music', 'https://api.github.com/repos/nivash01/Echo'),
    ('https://raw.githubusercontent.com/EchoMusicApp/Echo-Music', 'https://raw.githubusercontent.com/nivash01/Echo'),
    ('https://github.com/iad1tya/Echo-Music', 'https://github.com/nivash01/Echo'),
]

def iter_files():
    for path in ROOT.rglob("*"):
        if not path.is_file():
            continue
        if any(part in SKIP_DIRS for part in path.parts):
            continue
        if path.suffix.lower() in TEXT_EXTENSIONS:
            yield path

def patch_text_files() -> int:
    changed = 0
    for path in iter_files():
        try:
            original = path.read_text(encoding="utf-8")
        except (UnicodeDecodeError, OSError):
            continue
        updated = original
        for old, new in EXACT_REPLACEMENTS:
            updated = updated.replace(old, new)
        if updated != original:
            path.write_text(updated, encoding="utf-8")
            changed += 1
    return changed

def move_package_directories() -> int:
    moves = [
        (ROOT / "app/src/main/kotlin/com/music/echo", ROOT / "app/src/main/kotlin/com/nivukx/music"),
        (ROOT / "app/src/gms/kotlin/com/music/echo", ROOT / "app/src/gms/kotlin/com/nivukx/music"),
        (ROOT / "core/src/main/kotlin/echo/music/iad1tya", ROOT / "core/src/main/kotlin/com/nivukx/music"),
        (ROOT / "lyrics/src/main/kotlin/echo/music/iad1tya", ROOT / "lyrics/src/main/kotlin/com/nivukx/music"),
        (ROOT / "playback/src/main/kotlin/echo/music/iad1tya", ROOT / "playback/src/main/kotlin/com/nivukx/music"),
        (ROOT / "canvas/src/main/kotlin/com/music/echo", ROOT / "canvas/src/main/kotlin/com/nivukx/music"),
        (ROOT / "applecanvas/src/main/kotlin/com/music/echo", ROOT / "applecanvas/src/main/kotlin/com/nivukx/music"),
        (ROOT / "echomusiccanvas/src/main/kotlin/com/music/echo", ROOT / "echomusiccanvas/src/main/kotlin/com/nivukx/music"),
        (ROOT / "unison/src/main/kotlin/com/music/echo", ROOT / "unison/src/main/kotlin/com/nivukx/music"),
    ]
    moved = 0
    for src, dst in moves:
        if src.exists():
            dst.parent.mkdir(parents=True, exist_ok=True)
            if dst.exists():
                for child in src.iterdir():
                    target = dst / child.name
                    if target.exists():
                        if target.is_dir() and child.is_dir():
                            shutil.copytree(child, target, dirs_exist_ok=True)
                            shutil.rmtree(child)
                        else:
                            target.unlink()
                            shutil.move(str(child), str(target))
                    else:
                        shutil.move(str(child), str(target))
                src.rmdir()
            else:
                shutil.move(str(src), str(dst))
            moved += 1
    return moved

def targeted_gradle_normalization() -> int:
    changed = 0
    for path in ROOT.rglob("build.gradle.kts"):
        if any(part in SKIP_DIRS for part in path.parts):
            continue
        text = path.read_text(encoding="utf-8")
        new = text
        namespace_map = {
            'com.music.echo.core': 'com.nivukx.music.core',
            'com.music.echo.lyrics': 'com.nivukx.music.lyrics',
            'com.music.echo.playback': 'com.nivukx.music.playback',
            'echo.music.iad1tya': 'com.nivukx.music',
        }
        for old, replacement in namespace_map.items():
            new = new.replace(old, replacement)
        if new != text:
            path.write_text(new, encoding="utf-8")
            changed += 1
    return changed

def assert_clean_identity() -> None:
    forbidden = [
        "echo.music.iad1tya",
        "com.music.echo",
        "com/music/echo",
        "EchoMusicApp/Echo-Music",
        "github.com/iad1tya/Echo-Music",
        "https://echomusic.fun",
        "share.echomusic.fun",
        "Echo Music",
        "EchoNotificationProvider",
        "EchoMotion",
        "content://com.echomusic.music",
    ]
    bad = []
    for path in iter_files():
        try:
            text = path.read_text(encoding="utf-8")
        except (UnicodeDecodeError, OSError):
            continue
        for token in forbidden:
            if token in text:
                bad.append(f"{path}: {token}")
    if bad:
        print("LEGACY_IDENTITY_RESIDUES")
        print("\n".join(sorted(set(bad))[:200]))
        raise SystemExit(2)

def git_changed_files() -> list[str]:
    result = subprocess.run(
        ["git", "status", "--short"],
        cwd=ROOT,
        check=True,
        capture_output=True,
        text=True,
    )
    return result.stdout.splitlines()

if __name__ == "__main__":
    moved = move_package_directories()
    patched = patch_text_files()
    gradle_patched = targeted_gradle_normalization()
    assert_clean_identity()
    print(f"NIVUKX_MIGRATION moved={moved} patched={patched} gradle={gradle_patched}")
    for item in git_changed_files()[:250]:
        print(item)
