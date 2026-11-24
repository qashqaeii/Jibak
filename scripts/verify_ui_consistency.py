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

# Financial text tokens must pass WCAG AA normal-text contrast on both
# main paper and warm cream backgrounds. Bright orange is an accent only.
import re


def _linear_channel(value: float) -> float:
    return value / 12.92 if value <= 0.04045 else ((value + 0.055) / 1.055) ** 2.4


def _relative_luminance(hex_color: str) -> float:
    values = [int(hex_color[index:index + 2], 16) / 255 for index in (0, 2, 4)]
    return sum(_linear_channel(v) * w for v, w in zip(values, (0.2126, 0.7152, 0.0722)))


def _contrast_ratio(first: str, second: str) -> float:
    l1, l2 = sorted((_relative_luminance(first), _relative_luminance(second)),
                    reverse=True)
    return (l1 + 0.05) / (l2 + 0.05)


tokens_source = (APP / "JibikPremiumUi.kt").read_text(encoding="utf-8")
token_pattern = r"val\s+{}\s*=\s*Color\(0xFF([A-Fa-f0-9]{{6}})\)"
token_values = {}
for token in ("ink", "forest", "muted", "coral", "warningText", "cream"):
    result = re.search(token_pattern.format(token), tokens_source)
    if not result:
        errors.append(f"Missing color token for accessibility: {token}")
    else:
        token_values[token] = result.group(1)

if "cream" in token_values:
    for token in ("ink", "forest", "muted", "coral", "warningText"):
        if token not in token_values:
            continue
        for background in (token_values["cream"], "FFFFFF"):
            if _contrast_ratio(token_values[token], background) < 4.5:
                errors.append(
                    f"JibikStyle.{token} fails 4.5:1 contrast over {background}"
                )

# Text aliases in the three largest workspaces must use the same accessible
# tokens, not legacy low-contrast gray/red literals.
for filename in ("MainActivity.kt", "ReportsScreen.kt", "AccountsScreen.kt"):
    text_colors = (APP / filename).read_text(encoding="utf-8")
    if "Color(0xFF677A78)" in text_colors or "Color(0xFFE95C50)" in text_colors:
        errors.append(f"{filename}: low-contrast legacy text token has returned")

# Trust-critical UX regression guards. Logic has corresponding JVM unit tests.
main_source = (APP / "MainActivity.kt").read_text(encoding="utf-8")
store_source = (APP / "LocalLedgerStore.kt").read_text(encoding="utf-8")
reports_source = (APP / "ReportsScreen.kt").read_text(encoding="utf-8")
forms_source = (APP / "JibikForms.kt").read_text(encoding="utf-8")
navigation_source = (APP / "JibikBackNavigation.kt").read_text(encoding="utf-8")

home_block = main_source.split("private fun Home(", 1)[-1].split(
    "private fun SpendingByCategory(", 1
)[0]
if not (
    home_block.find('JibikSectionTitle("ثبت سریع"') >= 0
    and home_block.find('JibikSectionTitle("ثبت سریع"') <
        home_block.find('JibikSectionTitle("خلاصه "')
    and home_block.find("LiquidGlassCard(onClick = onAi") >
        home_block.find("onClick = onBudget")
):
    errors.append("Home hierarchy must prioritize fast actions and financial insights over AI")

if 'preferences.getLong("budget_toman", 0L)' not in store_source:
    errors.append("A default budget must never be invented for a new user")
if "require(value >= 0L)" not in store_source:
    errors.append("The user must be able to remove a monthly budget cap")
if "home_amounts_visible" not in store_source:
    errors.append("Dashboard amount visibility preference is not persisted")
if "BackHandler(" not in main_source or "resolveJibikBackRoute(" not in main_source:
    errors.append("System back navigation is not hooked to the route hierarchy")
if "section == Section.ACCOUNTS" not in navigation_source:
    errors.append("Account nested routes are missing in Back navigation")
if "WindowInsets.ime.getBottom" not in main_source:
    errors.append("Navigation must adapt to the software keyboard")
if "jibikPrivateAmount(" not in main_source:
    errors.append("Home amount masking is missing")
if "clearAndSetSemantics" not in reports_source:
    errors.append("Category ring is not accessible to screen readers")
if "KeyboardActions(onDone =" not in forms_source:
    errors.append("Money fields need a keyboard completion action")

manifest = (ROOT / "app/src/main/AndroidManifest.xml").read_text(encoding="utf-8")
if "android.permission.READ_SMS" in manifest or "android.permission.RECEIVE_SMS" in manifest:
    errors.append("Restricted SMS permission leaked into test manifest")

if errors:
    print("Jibik UI consistency failed:")
    for error in errors:
        print(" - " + error)
    sys.exit(1)

print("Jibik UI contract OK: consistent dialog chrome, headings and SMS-free test build")
