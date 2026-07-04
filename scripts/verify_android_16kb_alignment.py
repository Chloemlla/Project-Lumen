#!/usr/bin/env python3
"""Validate Android APK native libraries for 16 KB page-size support."""

from __future__ import annotations

import argparse
import struct
import sys
import zipfile
from pathlib import Path


PAGE_SIZE = 16 * 1024
PT_LOAD = 1
ZIP_LOCAL_HEADER_SIZE = 30


def zip_data_offset(apk: zipfile.ZipFile, info: zipfile.ZipInfo) -> int:
    apk.fp.seek(info.header_offset)
    header = apk.fp.read(ZIP_LOCAL_HEADER_SIZE)
    if len(header) != ZIP_LOCAL_HEADER_SIZE or header[:4] != b"PK\x03\x04":
        raise ValueError(f"{info.filename}: invalid ZIP local header")
    file_name_length, extra_length = struct.unpack_from("<HH", header, 26)
    return info.header_offset + ZIP_LOCAL_HEADER_SIZE + file_name_length + extra_length


def elf_load_alignments(data: bytes, name: str) -> list[int]:
    if len(data) < 64 or data[:4] != b"\x7fELF":
        raise ValueError(f"{name}: invalid ELF header")

    elf_class = data[4]
    endian_token = data[5]
    if endian_token == 1:
        endian = "<"
    elif endian_token == 2:
        endian = ">"
    else:
        raise ValueError(f"{name}: unsupported ELF endianness {endian_token}")

    if elf_class == 1:
        header_format = f"{endian}HHIIIIIHHHHHH"
        header_size = struct.calcsize(header_format)
        if len(data) < 16 + header_size:
            raise ValueError(f"{name}: truncated ELF32 header")
        header = struct.unpack_from(header_format, data, 16)
        program_header_offset = header[4]
        program_header_entry_size = header[8]
        program_header_count = header[9]
        align_offset = 28
        type_offset = 0
        align_format = f"{endian}I"
    elif elf_class == 2:
        header_format = f"{endian}HHIQQQIHHHHHH"
        header_size = struct.calcsize(header_format)
        if len(data) < 16 + header_size:
            raise ValueError(f"{name}: truncated ELF64 header")
        header = struct.unpack_from(header_format, data, 16)
        program_header_offset = header[4]
        program_header_entry_size = header[8]
        program_header_count = header[9]
        align_offset = 48
        type_offset = 0
        align_format = f"{endian}Q"
    else:
        raise ValueError(f"{name}: unsupported ELF class {elf_class}")

    alignments: list[int] = []
    for index in range(program_header_count):
        entry_offset = program_header_offset + (index * program_header_entry_size)
        entry_end = entry_offset + program_header_entry_size
        if entry_end > len(data):
            raise ValueError(f"{name}: truncated program header {index}")
        program_type = struct.unpack_from(f"{endian}I", data, entry_offset + type_offset)[0]
        if program_type == PT_LOAD:
            alignments.append(struct.unpack_from(align_format, data, entry_offset + align_offset)[0])
    return alignments


def verify_apk(path: Path) -> list[str]:
    failures: list[str] = []
    with zipfile.ZipFile(path) as apk:
        native_libs = [info for info in apk.infolist() if info.filename.startswith("lib/") and info.filename.endswith(".so")]
        if not native_libs:
            return [f"{path}: no native libraries found"]

        for info in native_libs:
            if info.compress_type != zipfile.ZIP_STORED:
                failures.append(f"{path}:{info.filename}: native library is compressed")
            offset = zip_data_offset(apk, info)
            if offset % PAGE_SIZE != 0:
                failures.append(f"{path}:{info.filename}: ZIP data offset {offset} is not 16 KB aligned")

            alignments = elf_load_alignments(apk.read(info), f"{path}:{info.filename}")
            if not alignments:
                failures.append(f"{path}:{info.filename}: no PT_LOAD segments found")
            for alignment in alignments:
                if alignment < PAGE_SIZE or alignment % PAGE_SIZE != 0:
                    failures.append(
                        f"{path}:{info.filename}: PT_LOAD alignment {alignment} is not 16 KB compatible"
                    )
    return failures


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("apk", nargs="+", type=Path, help="APK file to inspect")
    args = parser.parse_args()

    failures: list[str] = []
    for apk_path in args.apk:
        if apk_path.exists():
            failures.extend(verify_apk(apk_path))
        else:
            failures.append(f"{apk_path}: APK file does not exist")

    if failures:
        print("Android 16 KB native library alignment check failed:", file=sys.stderr)
        for failure in failures:
            print(f"- {failure}", file=sys.stderr)
        return 1

    print("Android 16 KB native library alignment check passed.")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
