#!/usr/bin/env python3
"""Offline decryptor for Jibik's password-protected CSV (.jbc) format.

Requires: python -m pip install cryptography
Usage: python scripts/decrypt_jibik_csv.py /path/to/file.jbc /path/to/output.csv

The passphrase is collected privately with getpass (never as a CLI argument).
Only use this tool on files you own. Decrypted CSV contains sensitive financial
information and is *not* a complete Jibik backup or an app restore format.
"""
from __future__ import annotations

import argparse
from getpass import getpass
from pathlib import Path
import hashlib
import sys


MAGIC = b"JIBIKCSV1"
SALT_BYTES = 16
NONCE_BYTES = 12
HEADER_LENGTH = len(MAGIC) + SALT_BYTES + NONCE_BYTES
MIN_LENGTH = HEADER_LENGTH + 16
MAX_FILE_SIZE = 128 * 1024 * 1024
ITERATIONS = 210_000


def decrypt_file(source: Path, destination: Path, password: str) -> None:
    if not source.is_file():
        raise ValueError("Input file does not exist")
    if source.stat().st_size < MIN_LENGTH:
        raise ValueError("File is too small to be a valid encrypted Jibik CSV")
    if source.stat().st_size > MAX_FILE_SIZE:
        raise ValueError("File exceeds the 128 MiB safety limit")
    data = source.read_bytes()
    if not data.startswith(MAGIC):
        raise ValueError("Unknown encrypted Jibik CSV format")
    salt = data[len(MAGIC): len(MAGIC) + SALT_BYTES]
    nonce = data[len(MAGIC) + SALT_BYTES: HEADER_LENGTH]
    ciphertext_and_tag = data[HEADER_LENGTH:]
    key = hashlib.pbkdf2_hmac(
        "sha256", password.encode("utf-8"), salt, ITERATIONS, dklen=32,
    )
    # Import only after validation so dependency failures do not leak secrets.
    from cryptography.hazmat.primitives.ciphers.aead import AESGCM
    # Authentication is checked BEFORE any plaintext is written to disk.
    plain = AESGCM(key).decrypt(nonce, ciphertext_and_tag, data[:HEADER_LENGTH])
    if not plain.startswith(b"\xef\xbb\xbf"):
        raise ValueError("Decrypted payload does not look like Jibik UTF-8 CSV")
    if destination.resolve() == source.resolve():
        raise ValueError("Input and output files must be different")
    with destination.open("xb") as output:
        output.write(plain)


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("source", type=Path, help="Encrypted .jbc file")
    parser.add_argument("output", type=Path, help="New destination CSV path")
    args = parser.parse_args()
    password = getpass("Jibik export passphrase: ")
    if not password:
        print("Passphrase required", file=sys.stderr)
        return 2
    try:
        decrypt_file(args.source, args.output, password)
    except Exception as exc:
        print("Decryption failed; nothing restored: " + str(exc), file=sys.stderr)
        return 1
    print("CSV decrypted successfully. Keep the unencrypted file private.")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
