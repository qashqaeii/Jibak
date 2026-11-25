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
    if source.name == "JibikModalDialog.kt":
        continue
    content = source.read_text(encoding="utf-8")
    dialogs = content.count("JibikModalDialog(")
    legacy = content.count("AlertDialog(")
    if legacy:
        errors.append(
            f"{source.name}: {legacy} legacy AlertDialog(s) bypass the fixed "
            "footer and responsive financial modal"
        )
    if dialogs:
        chrome = content.count(".jibikDialogSurface()")
        wide = content.count("properties = JibikWideDialogProperties")
        if chrome != dialogs or wide != dialogs:
            errors.append(
                f"{source.name}: {dialogs} modal(s), but {chrome} shared surfaces "
                f"and {wide} wide-window policies"
            )

modal_shell = (APP / "JibikModalDialog.kt").read_text(encoding="utf-8")
if "Dialog(" not in modal_shell or ".weight(1f, fill = false)" not in modal_shell:
    errors.append("Financial dialog needs a scroll-safe body and persistent action footer")
if "LocalJibikModalPrimaryAction provides true" not in modal_shell:
    errors.append("Finance dialog primary actions must use a full-width target")

dialog_style = (APP / "JibikLiquidGlass.kt").read_text(encoding="utf-8")
if "usePlatformDefaultWidth = false" not in dialog_style:
    errors.append("Shared modal window must opt out of platform default width")
if ".fillMaxWidth(0.94f)" not in dialog_style:
    errors.append("Shared modal must fill a documented fraction of phone width")
if "widthIn(max = 600.dp)" not in dialog_style:
    errors.append("Tablet dialog width needs a sensible maximum")
if "LocalDensity.current.fontScale >= 1.3f" not in (
    APP / "ItemizedPurchaseDialog.kt"
).read_text(encoding="utf-8"):
    errors.append("Itemized form must stack inputs at large font scale")
if ".weight(1f, fill = false)" not in (
    APP / "CategoryPickerDialog.kt"
).read_text(encoding="utf-8"):
    errors.append("Long category picker list needs flexible dialog height")

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

# Editorial UI contracts: single-choice fields behave as grouped controls,
# and the modal footer is a distinct persistent hierarchy.
segmented = (APP / "JibikSegmentedControl.kt").read_text(encoding="utf-8")
if "selectableGroup()" not in segmented or "Role.RadioButton" not in segmented:
    errors.append("Segmented controls require grouped single-choice semantics")
for filename in (
    "MainActivity.kt",
    "ReportsScreen.kt",
    "CategoryManagementScreen.kt",
    "CategoryBudgetsUi.kt",
):
    if "JibikSegmentedControl(" not in (APP / filename).read_text(encoding="utf-8"):
        errors.append(f"{filename}: finance modes must use the shared segmented selector")
if "drawWithContent" in (
    APP / "JibikLiquidGlass.kt"
).read_text(encoding="utf-8"):
    errors.append("Ordinary cards should not regress to layered specular glass rims")
if "HorizontalDivider(" not in modal_shell:
    errors.append("Finance modal header, body and actions need visible separation")

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
# Direct-install distribution must not request SMS or other sensitive message access.
import xml.etree.ElementTree as ET
android = "{http://schemas.android.com/apk/res/android}"
root = ET.fromstring(manifest)
restricted = {"READ_SMS", "RECEIVE_SMS", "SEND_SMS", "RECEIVE_MMS", "RECEIVE_WAP_PUSH",
              "BIND_NOTIFICATION_LISTENER_SERVICE", "BIND_ACCESSIBILITY_SERVICE"}
for node in root.iter():
    if any(node.get(android + attr, "") == "android.permission." + name
           for attr in ("name", "permission") for name in restricted):
        errors.append("Direct APK must not request restricted SMS or sensitive service access")
receivers = {node.get(android + "name") for node in root.findall("./application/receiver")}
if receivers.intersection({".BankSmsReceiver", ".BankAutoReplyReceiver"}):
    errors.append("Background bank receivers must not be registered in direct distribution")
if not any(
    any(a.get(android + "name") == "android.intent.action.SEND" for a in f.findall("action")) and
    any(d.get(android + "mimeType") == "text/plain" for d in f.findall("data"))
    for f in root.findall("./application/activity/intent-filter")
):
    errors.append("User-initiated text sharing entry is missing")
gradle = (ROOT / "app/build.gradle.kts").read_text(encoding="utf-8")
if 'buildConfigField("boolean", "BANK_SMS_AVAILABLE", "true")' in gradle or 'create("smsDebug")' in gradle:
    errors.append("Direct APK cannot enable background bank SMS")
workflow = (ROOT / ".github/workflows/android.yml").read_text(encoding="utf-8")
if workflow.count("name: Jibik-APK") != 1 or "assembleSmsDebug" in workflow or "assembleRecoveryDebug" in workflow:
    errors.append("Android CI must publish exactly one installable APK channel")

# Prevent the original recurring-manager regression: the same scrolling
# viewport must not retain its position across long/short dialog modes.
calendar_source = (APP / "FinancialCalendar.kt").read_text(encoding="utf-8")
if "bodyKey = mode" not in calendar_source or "jibikScrollableBody" not in calendar_source:
    errors.append("Recurring manager needs mode-keyed scroll viewport")
if 'dismissButton = if (mode in listOf(' not in calendar_source:
    errors.append("Recurring manager must not reserve space for an absent cancel action")
for filename in ("ReportAnalyticsUi.kt", "CashForecastChart.kt", "WealthHistoryUi.kt"):
    source = (APP / filename).read_text(encoding="utf-8")
    if "FinanceChartFrame(" not in source or "rememberChartReveal(" not in source:
        errors.append(f"{filename}: missing shared financial chart axes and motion")

if errors:
    print("Jibik UI consistency failed:")
    for error in errors:
        print(" - " + error)
    sys.exit(1)

print("Jibik UI contract OK: consistent dialog chrome, headings and user-shared offline banking")
