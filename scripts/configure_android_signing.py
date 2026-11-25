#!/usr/bin/env python3
"""Owner-side signing setup. Never deletes an app or exposes a private key."""
import argparse
import base64
import hashlib
import pathlib
import re
import shutil
import subprocess
import tempfile

REPO = "qashqaeii/Jibak"
SECRET = "JIBIK_UPDATE_KEYSTORE_B64"
VARIABLE = "JIBIK_UPDATE_CERT_SHA256"


def run(args, *, data=None):
    result = subprocess.run(args, input=data, stdout=subprocess.PIPE, stderr=subprocess.PIPE)
    if result.returncode:
        # Do not echo a command or its input, which may contain sensitive material.
        raise SystemExit(f"Command failed: {args[0]} (exit {result.returncode}). Check authentication or local key settings.")
    return result.stdout


def fingerprint(value):
    clean = re.sub(r"[:\s]", "", value).lower()
    if not re.fullmatch(r"[0-9a-f]{64}", clean):
        raise SystemExit("Expected a SHA-256 certificate fingerprint (64 hexadecimal characters).")
    return clean


def certificate(key):
    return hashlib.sha256(run(["keytool", "-exportcert", "-alias", "androiddebugkey",
                               "-keystore", str(key), "-storepass", "android"])).hexdigest()


def main():
    p = argparse.ArgumentParser(description=__doc__)
    p.add_argument("--keystore", required=True, type=pathlib.Path, help="Private key file outside the repository")
    mode = p.add_mutually_exclusive_group(required=True)
    mode.add_argument("--expected-certificate", help="SHA-256 certificate of the existing installed APK")
    mode.add_argument("--create-new", action="store_true", help="Only after encrypted backup recovery has been verified")
    args = p.parse_args()
    key = args.keystore.expanduser().resolve()
    if not shutil.which("gh") or not shutil.which("keytool"):
        raise SystemExit("Install GitHub CLI and JDK (keytool), then run gh auth login.")
    # Refuse to place a key anywhere under a Git working tree.
    if any((folder / ".git").exists() for folder in [key.parent, *key.parent.parents]):
        raise SystemExit("Choose a keystore path outside any Git repository.")
    run(["gh", "auth", "status"])
    # Listing, rather than interpreting any command failure as a missing variable,
    # distinguishes unavailable authorization from a truly unconfigured repository.
    import json
    variables = json.loads(run(["gh", "variable", "list", "--repo", REPO, "--limit", "1000", "--json", "name,value"]))
    pinned = next((fingerprint(row["value"]) for row in variables if row["name"] == VARIABLE), None)
    if args.create_new:
        if pinned or key.exists():
            raise SystemExit("A pinned certificate or key already exists. Never rotate it automatically.")
        if input("Type BACKUP VERIFIED to confirm you tested recovery without deleting the old app: ") != "BACKUP VERIFIED":
            raise SystemExit("Aborted; no key created.")
        key.parent.mkdir(parents=True, exist_ok=True)
        run(["keytool", "-genkeypair", "-storetype", "JKS", "-keystore", str(key),
             "-storepass", "android", "-keypass", "android", "-alias", "androiddebugkey",
             "-keyalg", "RSA", "-keysize", "3072", "-validity", "10000",
             "-dname", "CN=Jibik Persistent Test Channel"])
        # Standard debug credentials are used only for the private test channel.
        # The key file itself must be protected by the owner's OS and offline backup.
        if __import__("os").name != "nt":
            key.chmod(0o600)
    elif not key.is_file():
        raise SystemExit("The original private keystore is required; the APK alone is insufficient.")
    actual = certificate(key)
    if args.expected_certificate and actual != fingerprint(args.expected_certificate):
        raise SystemExit("Certificate mismatch. No GitHub settings changed. Do not uninstall your app.")
    if pinned and pinned != actual:
        raise SystemExit("Existing pinned certificate differs. Refusing to replace it.")
    # Verify the private key can actually sign, not merely export a certificate.
    with tempfile.TemporaryDirectory() as directory:
        import zipfile
        fixture = pathlib.Path(directory) / "probe.jar"
        with zipfile.ZipFile(fixture, "w") as jar:
            jar.writestr("probe.txt", "Jibik signing probe")
        run(["jarsigner", "-keystore", str(key), "-storepass", "android", "-keypass", "android",
             str(fixture), "androiddebugkey"])
    run(["gh", "secret", "set", SECRET, "--repo", REPO], data=base64.b64encode(key.read_bytes()))
    if not pinned:
        run(["gh", "variable", "set", VARIABLE, "--repo", REPO, "--body", actual])
    print("Persistent test-channel signing configured. Certificate SHA-256:", actual)
    print("Keep a secure offline copy of the private keystore. Do not commit it.")
    if args.create_new:
        print("New signer CANNOT update the old app directly. Preserve the verified backup for migration.")
    print("Run Android CI — Jibik from GitHub Actions. Only stable-signed primary APKs will be published.")


if __name__ == "__main__":
    main()
