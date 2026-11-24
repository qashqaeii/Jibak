#!/usr/bin/env python3
"""Fast, static consistency checks for Jibik's shared Android visual system.

This does not replace on-device screenshot/accessibility QA.
"""
from pathlib import Path
import sys

ROOT = Path(__file__).resolve().parents[1]
APP = ROOT / "app/src/main/java/ir/jibik/app"
errors = []

for source in sorted(APP.glob("*.kt")):
    text = source.read_text(encoding="utf-8")
    dialogs = text.count("AlertDialog(")
    if dialogs:
        chrome = text.count(".jibikDialogSurface()")
        if chrome != dialogs:
            errors.append(
                f"{source.name}: {dialogs} AlertDialog(s) but {chrome} shared chrome calls"
            )

for filename in (
    "MainActivity.kt",
    "AccountsScreen.kt",
    "ReportsScreen.kt",
    "GoalsScreen.kt",
    "CategoryManagementScreen.kt",
    "ObligationsUi.kt",
):
    if "JibikPageHeader(" not in (APP / filename).read_text(encoding="utf-8"):
        errors.append(f"{filename}: missing shared page heading")

if "JibikDialogHeading(" not in (APP / "JibikPremiumUi.kt").read_text(encoding="utf-8"):
    errors.append("Shared dialog title is missing")

# Shared progress language makes financial trends consistent in every workspace.
for filename in (
    "MainActivity.kt",
    "ReportsScreen.kt",
    "GoalsScreen.kt",
    "CategoryBudgetsUi.kt",
    "ObligationsUi.kt",
):
    if "JibikProgressTrack(" not in (APP / filename).read_text(encoding="utf-8"):
        errors.append(f"{filename}: missing shared finance progress track")

# Interactive affordances should never revert to font-dependent chevrons.
for filename in (
    "JibikPremiumUi.kt",
    "AccountsScreen.kt",
    "MainActivity.kt",
    "ReportsScreen.kt",
):
    if "JibikChevronIcon(" not in (APP / filename).read_text(encoding="utf-8"):
        errors.append(f"{filename}: missing frameless RTL navigation chevron")

for filename in (
    "MainActivity.kt",
    "ReportsScreen.kt",
    "GoalsScreen.kt",
    "CategoryBudgetsUi.kt",
    "ObligationsUi.kt",
):
    if "JibikStatePanel(" not in (APP / filename).read_text(encoding="utf-8"):
        errors.append(f"{filename}: empty/recovery state not using shared UI")

manifest = (ROOT / "app/src/main/AndroidManifest.xml").read_text(encoding="utf-8")
if "android.permission.READ_SMS" in manifest or "android.permission.RECEIVE_SMS" in manifest:
    errors.append("Restricted SMS permission leaked into test manifest")

if errors:
    print("Jibik UI consistency failed:")
    for error in errors:
        print(" - " + error)
    sys.exit(1)

print("Jibik UI contract OK: consistent dialog chrome, headings and SMS-free test build")
