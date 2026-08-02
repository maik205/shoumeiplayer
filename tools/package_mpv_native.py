#!/usr/bin/env python3
"""Create a deterministic Android native bundle for Shoumei Player."""

from __future__ import annotations

import argparse
import hashlib
import json
import os
from pathlib import Path
import zipfile


REQUIRED_LIBRARIES = (
    "libavcodec.so",
    "libavdevice.so",
    "libavfilter.so",
    "libavformat.so",
    "libavutil.so",
    "libc++_shared.so",
    "libmpv.so",
    "libswresample.so",
    "libswscale.so",
)
ZIP_TIMESTAMP = (1980, 1, 1, 0, 0, 0)


def parse_abi_source(value: str) -> tuple[str, Path]:
    try:
        abi, source = value.split("=", 1)
    except ValueError as error:
        raise argparse.ArgumentTypeError("ABI sources must use ABI=PATH") from error
    if not abi or not source:
        raise argparse.ArgumentTypeError("ABI sources must use ABI=PATH")
    return abi, Path(source).resolve()


def sha256(path: Path) -> str:
    digest = hashlib.sha256()
    with path.open("rb") as source:
        for chunk in iter(lambda: source.read(1024 * 1024), b""):
            digest.update(chunk)
    return digest.hexdigest()


def write_bytes(archive: zipfile.ZipFile, name: str, data: bytes, mode: int) -> None:
    info = zipfile.ZipInfo(name, ZIP_TIMESTAMP)
    info.compress_type = zipfile.ZIP_DEFLATED
    info.external_attr = mode << 16
    archive.writestr(info, data, compresslevel=9)


def main() -> int:
    parser = argparse.ArgumentParser()
    parser.add_argument("--output", required=True, type=Path)
    parser.add_argument("--mpv-version", required=True)
    parser.add_argument("--mpv-commit", required=True)
    parser.add_argument("--ffmpeg-commit", required=True)
    parser.add_argument("--ndk-version", required=True)
    parser.add_argument(
        "--abi-source",
        action="append",
        required=True,
        type=parse_abi_source,
        metavar="ABI=PATH",
    )
    args = parser.parse_args()

    sources = dict(args.abi_source)
    if len(sources) != len(args.abi_source):
        parser.error("Each ABI may be specified only once")

    files: list[tuple[str, Path]] = []
    manifest_files: dict[str, dict[str, str | int]] = {}
    for abi, source in sorted(sources.items()):
        if not source.is_dir():
            parser.error(f"Native source directory does not exist: {source}")
        manifest_files[abi] = {}
        for library in REQUIRED_LIBRARIES:
            path = source / library
            if not path.is_file():
                parser.error(f"Missing {library} for {abi}: {path}")
            archive_name = f"jniLibs/{abi}/{library}"
            files.append((archive_name, path))
            manifest_files[abi][library] = {
                "sha256": sha256(path),
                "size": path.stat().st_size,
            }

    manifest = {
        "schemaVersion": 1,
        "mpvVersion": args.mpv_version,
        "mpvCommit": args.mpv_commit,
        "ffmpegCommit": args.ffmpeg_commit,
        "ndkVersion": args.ndk_version,
        "abis": manifest_files,
    }
    manifest_data = (json.dumps(manifest, indent=2, sort_keys=True) + "\n").encode()

    output = args.output.resolve()
    output.parent.mkdir(parents=True, exist_ok=True)
    temporary = output.with_suffix(output.suffix + ".tmp")
    temporary.unlink(missing_ok=True)
    with zipfile.ZipFile(temporary, "w", allowZip64=True) as archive:
        write_bytes(archive, "manifest.json", manifest_data, 0o100644)
        for archive_name, path in sorted(files):
            write_bytes(archive, archive_name, path.read_bytes(), 0o100755)
    os.replace(temporary, output)

    print(f"{sha256(output)}  {output.name}")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
