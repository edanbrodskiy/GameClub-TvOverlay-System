#!/usr/bin/env python3
"""Package the built debug APK and a portable source archive using only the Python standard library."""
import hashlib
from pathlib import Path
import re
import shutil
import zipfile


def main():
    root = Path(__file__).resolve().parent.parent
    build_file = (root / "app/build.gradle.kts").read_text()
    version = re.search(r'versionName\s*=\s*"([A-Za-z0-9._-]+)"', build_file).group(1)
    apk = root / "app/build/outputs/apk/debug/app-debug.apk"
    if not apk.is_file():
        raise SystemExit("Build the APK first: ./scripts/build.sh")

    dist = root / "dist"
    dist.mkdir(exist_ok=True)
    base = f"mvp-session-overlay-{version}"
    output_apk = dist / f"{base}-debug.apk"
    archive = dist / f"{base}-source.zip"
    shutil.copy2(apk, output_apk)

    paths = [root / name for name in (
        ".editorconfig", ".gitattributes", ".gitignore", "README.md", "CHANGELOG.md",
        "build.gradle.kts", "settings.gradle.kts", "gradle.properties", "gradlew", "gradlew.bat",
        "app/build.gradle.kts",
    )]
    for directory in ("app/src", "gradle/wrapper", "scripts", "docs"):
        paths.extend(path for path in (root / directory).rglob("*") if path.is_file())
    excluded = {"__pycache__", ".DS_Store"}
    paths = sorted(set(path for path in paths if not excluded.intersection(path.parts)
                       and path.suffix != ".pyc"))
    with zipfile.ZipFile(archive, "w", compression=zipfile.ZIP_DEFLATED, compresslevel=9) as bundle:
        for path in paths:
            bundle.write(path, Path(base) / path.relative_to(root))

    checksums = []
    for path in (output_apk, archive):
        digest = hashlib.sha256(path.read_bytes()).hexdigest()
        checksums.append(f"{digest}  {path.name}\n")
        print(f"{path.relative_to(root)} ({path.stat().st_size:,} bytes)")
    (dist / "SHA256SUMS").write_text("".join(checksums))


if __name__ == "__main__":
    main()
