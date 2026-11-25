#!/usr/bin/env python3
"""Isolated emulator only: exercise an actual APK replacement, never uninstall."""
import argparse
import json
import os
from pathlib import Path
import re
import sqlite3
import subprocess
import tempfile
import time
import xml.etree.ElementTree as ET

PACKAGE = "ir.jibik.app"
PREFS = "shared_prefs/jibik_local_v1.xml"
DB = "databases/jibik_ledger_v1.db"
LOG = Path("upgrade-test.log")
NOW = 1700000000000


def run(*args, data=None, check=True):
    result = subprocess.run(args, input=data, stdout=subprocess.PIPE,
                            stderr=subprocess.PIPE, check=False, timeout=180)
    if check and result.returncode:
        raise RuntimeError(f"{args}: {result.stderr.decode(errors='replace')} {result.stdout.decode(errors='replace')}")
    return result.stdout


def adb(*args, **kwargs):
    return run("adb", *args, **kwargs)


def sandbox_read(path):
    return adb("exec-out", "run-as", PACKAGE, "cat", path)


def sandbox_write(path, data):
    # Fixed fixture paths only, no shell interpolation of user data.
    adb("exec-in", "run-as", PACKAGE, "sh", "-c", f"cat > {path}", data=data)


def single_apk(directory):
    apks = list(Path(directory).glob("*.apk"))
    if len(apks) != 1:
        raise AssertionError(f"Expected one APK in {directory}: {apks}")
    return str(apks[0].resolve())


def identity(apk):
    tools = Path(os.environ["ANDROID_HOME"]) / "build-tools/36.0.0"
    badging = run(str(tools / "aapt"), "dump", "badging", apk).decode()
    cert = run(str(tools / "apksigner"), "verify", "--print-certs", apk).decode()
    package = re.search(r"^package: name='([^']+)' versionCode='(\d+)'", badging, re.M)
    digest = re.search(r"^Signer #1 certificate SHA-256 digest: ([0-9a-fA-F]+)", cert, re.M)
    assert package and digest
    return package.group(1), int(package.group(2)), digest.group(1).lower()


def seed_legacy_preferences():
    entries = [
        dict(id="upgrade-income", kind="INCOME", amount=1500000, to="wallet", category="حقوق", note="آزمون ارتقا", created_at=NOW),
        dict(id="upgrade-expense", kind="EXPENSE", amount=250000, **{"from": "wallet"}, category="سایر هزینه‌ها", note="خرید آزمایشی", created_at=NOW+1),
    ]
    root = ET.Element("map")
    ET.SubElement(root, "boolean", name="welcome_finished", value="true")
    ET.SubElement(root, "boolean", name="home_amounts_visible", value="false")
    ET.SubElement(root, "long", name="budget_toman", value="7000000")
    ET.SubElement(root, "string", name="ledger").text = json.dumps(entries, ensure_ascii=False)
    adb("shell", "run-as", PACKAGE, "mkdir", "-p", "shared_prefs")
    sandbox_write(PREFS, ET.tostring(root, encoding="utf-8", xml_declaration=True))


def wait_for_old_import():
    adb("shell", "am", "start", "-n", PACKAGE + "/.MainActivity")
    for _ in range(45):
        try:
            root = ET.fromstring(sandbox_read(PREFS))
            if any(x.get("name") == "room_ledger_imported_v1" and x.get("value") == "true" for x in root):
                return
        except (RuntimeError, ET.ParseError):
            pass
        time.sleep(1)
    raise AssertionError("Old app did not import financial data")


def extend_old_database(schema):
    # The real historical app creates its own Room schema. Stop all writers
    # before taking the fixture's SQLite main file and optional WAL.
    adb("shell", "am", "force-stop", PACKAGE)
    with tempfile.TemporaryDirectory() as temp:
        path = Path(temp) / "old.db"
        path.write_bytes(sandbox_read(DB))
        try:
            wal = sandbox_read(DB + "-wal")
            if wal:
                Path(str(path) + "-wal").write_bytes(wal)
        except RuntimeError:
            pass
        with sqlite3.connect(path) as db:
            assert db.execute("PRAGMA user_version").fetchone()[0] == schema
            assert db.execute("SELECT COUNT(*) FROM ledger_records").fetchone()[0] == 2
            db.execute("PRAGMA foreign_keys=ON")
            db.execute("INSERT INTO financial_accounts(id,title,openingBalanceToman) VALUES(?,?,?)",
                       ("upgrade-savings", "پس‌انداز آزمون", 300000))
            db.execute("INSERT INTO savings_goals(id,title,accountId,targetToman,createdAtMillis) VALUES(?,?,?,?,?)",
                       ("upgrade-goal", "هدف آزمون", "upgrade-savings", 1000000, NOW))
            columns = "transactionId,lineIndex,title,category,quantity,unitPriceToman"
            db.execute(f"INSERT INTO purchase_lines({columns}) VALUES(?,?,?,?,?,?)",
                       ("upgrade-expense", 0, "کالای آزمون", "سایر هزینه‌ها", 2, 125000))
            if schema >= 20:
                db.execute("INSERT INTO monthly_budget(id,amount) VALUES(1,7000000)")
                db.execute("INSERT INTO custom_categories(id,kind,parentPath,title,archived,createdAtMillis) VALUES(?,?,?,?,?,?)",
                           ("upgrade-category", "EXPENSE", "", "دسته آزمون", 0, NOW))
                db.execute("INSERT INTO asset_holdings(id,title,kind,costBasisToman,createdAtMillis) VALUES(?,?,?,?,?)",
                           ("upgrade-asset", "دارایی آزمون", "GOLD", 200000, NOW))
                db.execute("INSERT INTO asset_events(assetId,kind,valueToman,atMillis,note) VALUES(?,?,?,?,?)",
                           ("upgrade-asset", "VALUE", 350000, NOW, "آزمون"))
            assert db.execute("PRAGMA integrity_check").fetchone()[0] == "ok"
            assert not db.execute("PRAGMA foreign_key_check").fetchall()
        # sqlite3 connection context commits but does not close on exit; use
        # a separate explicit close/checkpoint before transferring the fixture.
        db.close()
        with sqlite3.connect(path) as checkpoint:
            checkpoint.execute("PRAGMA wal_checkpoint(TRUNCATE)").fetchall()
        checkpoint.close()
        sandbox_write(DB, path.read_bytes())
        # Only test-emulator WAL sidecars, never a real user's files.
        adb("shell", "run-as", PACKAGE, "rm", "-f", DB + "-wal", DB + "-shm")


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("--schema", type=int, choices=(4, 20), required=True)
    schema = parser.parse_args().schema
    # Refuse to run against a physical phone or multiple devices.
    devices = [line.split()[0] for line in adb("devices").decode().splitlines()[1:] if "\tdevice" in line]
    assert len(devices) == 1 and devices[0].startswith("emulator-")
    old = single_apk("historical/app/build/outputs/apk/debug")
    new = single_apk("app/build/outputs/apk/debug")
    test = single_apk("app/build/outputs/apk/androidTest/debug")
    a, b = identity(old), identity(new)
    assert a[0] == b[0] == PACKAGE and a[2] == b[2] and b[1] > a[1], (a,b)
    LOG.write_text(f"Historical Room {schema}, version {a[1]} -> {b[1]}; common signer verified.\n")
    assert b"Success" in adb("install", old)
    seed_legacy_preferences()
    wait_for_old_import()
    extend_old_database(schema)
    assert b"Success" in adb("install", "-r", new)
    assert b"Success" in adb("install", test)
    output = adb("shell", "am", "instrument", "-w", "-e", "class",
                 PACKAGE + ".InPlaceUpgradeTest", "-e", "upgradeFixture", str(schema),
                 PACKAGE + ".test/androidx.test.runner.AndroidJUnitRunner").decode(errors="replace")
    with LOG.open("a") as log:
        log.write(output)
    print(output)
    assert re.search(r"OK \(1 test\)", output) and "FAILURES" not in output
    print("In-place update preserved existing data; no uninstall or pm clear.")


if __name__ == "__main__":
    main()
