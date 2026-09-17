from pathlib import Path

REPO_ROOT = Path(__file__).resolve().parents[1]

REQUIRED_PRESENT = {
    "app/src/main/res/values/app_name.xml": "<string name=\"app_name\">Nivukx</string>",
    "fastlane/metadata/android/en-US/title.txt": "Nivukx\n",
    "app/src/main/res/values/updater_strings.xml": "<string name=\"echo_music_title\">Nivukx</string>",
}

REQUIRED_ABSENT = {
    "app/src/main/res/values/app_name.xml": "Echo Music",
    "fastlane/metadata/android/en-US/title.txt": "Echo Music",
}


def verify() -> None:
    failures: list[str] = []

    for relative_path, expected in REQUIRED_PRESENT.items():
        path = REPO_ROOT / relative_path
        if not path.exists():
            failures.append(f"Missing required file: {relative_path}")
            continue
        content = path.read_text(encoding="utf-8")
        if expected not in content:
            failures.append(f"Expected Nivukx branding not found in {relative_path}")

    for relative_path, forbidden in REQUIRED_ABSENT.items():
        path = REPO_ROOT / relative_path
        if path.exists() and forbidden in path.read_text(encoding="utf-8"):
            failures.append(f"Legacy public branding remains in {relative_path}")

    if failures:
        raise SystemExit("Nivukx branding verification failed:\n- " + "\n- ".join(failures))

    print("Nivukx branding verification passed.")


if __name__ == "__main__":
    verify()
