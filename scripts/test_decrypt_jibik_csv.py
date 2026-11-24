"""Regression tests for the offline Jibik encrypted CSV recovery tool."""
from __future__ import annotations

import hashlib
import os
import tempfile
import unittest
from pathlib import Path

from cryptography.hazmat.primitives.ciphers.aead import AESGCM

from decrypt_jibik_csv import (
    HEADER_LENGTH, ITERATIONS, MAGIC, NONCE_BYTES, SALT_BYTES, decrypt_file,
)


class EncryptedCsvRecoveryTests(unittest.TestCase):
    def setUp(self):
        self.tmp = tempfile.TemporaryDirectory()
        self.addCleanup(self.tmp.cleanup)
        folder = Path(self.tmp.name)
        self.source = folder / "finance.jbc"
        self.output = folder / "recovered.csv"
        self.password = "فارسی-جیبیک-رمز-طولانی-۱۴۰۵"
        self.plain = "\ufefftransaction_id,amount_toman\r\nخرید,1200\r\n".encode("utf-8")
        self.source.write_bytes(self.make_jbc(self.plain, self.password))

    @staticmethod
    def make_jbc(plain: bytes, password: str) -> bytes:
        salt = os.urandom(SALT_BYTES)
        nonce = os.urandom(NONCE_BYTES)
        header = MAGIC + salt + nonce
        assert len(header) == HEADER_LENGTH
        key = hashlib.pbkdf2_hmac(
            "sha256", password.encode("utf-8"), salt, ITERATIONS, dklen=32,
        )
        return header + AESGCM(key).encrypt(nonce, plain, header)

    def test_round_trip_preserves_persian_csv(self):
        decrypt_file(self.source, self.output, self.password)
        self.assertEqual(self.output.read_bytes(), self.plain)

    def test_wrong_password_does_not_write_file(self):
        with self.assertRaises(Exception):
            decrypt_file(self.source, self.output, "incorrect passphrase")
        self.assertFalse(self.output.exists())

    def test_tampered_ciphertext_does_not_write_file(self):
        payload = bytearray(self.source.read_bytes())
        payload[-3] ^= 1
        self.source.write_bytes(payload)
        with self.assertRaises(Exception):
            decrypt_file(self.source, self.output, self.password)
        self.assertFalse(self.output.exists())

    def test_tampered_header_is_rejected(self):
        payload = bytearray(self.source.read_bytes())
        payload[0] ^= 1
        self.source.write_bytes(payload)
        with self.assertRaises(ValueError):
            decrypt_file(self.source, self.output, self.password)
        self.assertFalse(self.output.exists())

    def test_existing_output_is_not_overwritten(self):
        self.output.write_text("preserve", encoding="utf-8")
        with self.assertRaises(FileExistsError):
            decrypt_file(self.source, self.output, self.password)
        self.assertEqual(self.output.read_text(encoding="utf-8"), "preserve")

    def test_rejects_truncated_file(self):
        self.source.write_bytes(MAGIC)
        with self.assertRaises(ValueError):
            decrypt_file(self.source, self.output, self.password)
        self.assertFalse(self.output.exists())


if __name__ == "__main__":
    unittest.main()
