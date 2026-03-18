# Prompt: Fix AAPT2 Inline Namespace / Tint Build Error in Android XML Layouts

## Context

This prompt describes a specific Android build failure, its root cause, the exact
code fix, and a CI workflow improvement to prevent it recurring. Use it to apply
the same fix to any Android project that hits the same error.

---

## The Error

GitHub Actions (or any local Gradle build) fails at the
`:app:processDebugResources` task with:

```
> Android resource linking failed
com.example.myapp.app-main-70:/layout/item_discovery_app.xml:58:
  error: attribute tint (aka com.example.myapp:tint) not found.
  error: failed linking file resources.
```

The key signal is `aka com.example.myapp:tint` — AAPT2 is resolving `tint`
against the **app's own package namespace** instead of the AppCompat namespace.
This always means a namespace declaration problem in XML, not a missing
dependency.

---

## Root Cause

In the failing XML layout file, the `xmlns:app` namespace was declared **inline
on a child element** (an `<ImageView>`) instead of on the **root element**:

```xml
<!-- ❌ BROKEN — xmlns:app on a non-root child element -->
<?xml version="1.0" encoding="utf-8"?>
<LinearLayout xmlns:android="http://schemas.android.com/apk/res/android"
    android:layout_width="match_parent"
    ...>

    ...

    <ImageView
        android:id="@+id/warning_icon"
        android:src="@android:drawable/ic_dialog_alert"
        android:visibility="gone"
        app:tint="?android:attr/colorError"
        xmlns:app="http://schemas.android.com/apk/res-auto" />   ← WRONG POSITION

</LinearLayout>
```

**Why AAPT2 fails:**
AAPT2 (the Android Asset Packaging Tool) does not support `xmlns:` declarations
on non-root elements. When it encounters `app:tint` before reaching a valid
namespace declaration on the root, it resolves the `app` prefix against the
app's own package (`com.example.myapp`), producing the nonsensical attribute
name `com.example.myapp:tint` — which of course does not exist.

Android XML namespaces follow the same rule as standard XML: all namespace
declarations used in a document **must be declared on the root element or an
ancestor** of the element that uses them. Inline declarations on the using
element itself are technically valid XML but AAPT2 does not handle them.

---

## The Fix — Two Changes to the Layout XML

### Change 1 — Move `xmlns:app` to the root element

```xml
<!-- ✅ CORRECT — xmlns:app on the root element -->
<LinearLayout xmlns:android="http://schemas.android.com/apk/res/android"
    xmlns:app="http://schemas.android.com/apk/res-auto"    ← MOVED HERE
    android:layout_width="match_parent"
    ...>
```

### Change 2 — Replace `app:tint` with `android:tint`

Since this project targets `minSdk = 26` (Android 8.0+), `android:tint` is
natively supported on `ImageView` without requiring the AppCompat namespace at
all. This is both simpler and more correct:

```xml
<!-- ✅ CORRECT — native android:tint, no AppCompat namespace needed -->
<ImageView
    android:id="@+id/warning_icon"
    android:layout_width="16dp"
    android:layout_height="16dp"
    android:layout_marginStart="4dp"
    android:src="@android:drawable/ic_dialog_alert"
    android:visibility="gone"
    android:tint="?android:attr/colorError" />    ← no xmlns needed
```

**Rule of thumb for tint on ImageView:**
- `android:tint` — use this. Works from API 21+ natively. No namespace needed.
- `app:tint` — only needed if you are using `AppCompatImageView` explicitly
  AND targeting below API 21. For `minSdk >= 21`, always prefer `android:tint`.

### Full corrected file structure

```xml
<?xml version="1.0" encoding="utf-8"?>
<LinearLayout xmlns:android="http://schemas.android.com/apk/res/android"
    xmlns:app="http://schemas.android.com/apk/res-auto"
    android:layout_width="match_parent"
    android:layout_height="wrap_content"
    android:orientation="horizontal"
    android:gravity="center_vertical"
    android:paddingHorizontal="8dp"
    android:paddingVertical="12dp"
    android:layout_marginBottom="8dp"
    android:background="?android:attr/selectableItemBackground"
    android:clickable="true"
    android:focusable="true">

    <CheckBox
        android:id="@+id/app_checkbox"
        android:layout_width="wrap_content"
        android:layout_height="wrap_content"
        android:clickable="false"
        android:focusable="false" />

    <ImageView
        android:id="@+id/app_icon"
        android:layout_width="48dp"
        android:layout_height="48dp"
        android:layout_marginStart="8dp"
        android:contentDescription="App Icon" />

    <LinearLayout
        android:layout_width="0dp"
        android:layout_height="wrap_content"
        android:layout_weight="1"
        android:layout_marginStart="16dp"
        android:orientation="vertical">

        <LinearLayout
            android:layout_width="wrap_content"
            android:layout_height="wrap_content"
            android:orientation="horizontal"
            android:gravity="center_vertical">

            <TextView
                android:id="@+id/app_name"
                android:layout_width="wrap_content"
                android:layout_height="wrap_content"
                android:textSize="16sp"
                android:textStyle="bold"
                android:maxLines="1"
                android:ellipsize="end"
                android:textColor="?android:attr/textColorPrimary" />

            <ImageView
                android:id="@+id/warning_icon"
                android:layout_width="16dp"
                android:layout_height="16dp"
                android:layout_marginStart="4dp"
                android:src="@android:drawable/ic_dialog_alert"
                android:visibility="gone"
                android:tint="?android:attr/colorError" />

        </LinearLayout>

        <TextView
            android:id="@+id/app_details"
            android:layout_width="wrap_content"
            android:layout_height="wrap_content"
            android:layout_marginTop="2dp"
            android:textSize="12sp"
            android:textColor="?android:attr/textColorSecondary" />

        <TextView
            android:id="@+id/app_context_label"
            android:layout_width="wrap_content"
            android:layout_height="wrap_content"
            android:layout_marginTop="2dp"
            android:textSize="11sp"
            android:textColor="?android:attr/textColorTertiary"
            android:visibility="gone" />

    </LinearLayout>
</LinearLayout>
```

---

## How to Find All Instances of This Bug in a Project

Run this from the repo root before building. It scans all layout XML files and
reports any `xmlns:` declaration that appears after the root element's opening
tag, and any `app:tint` usage:

```bash
# Find all inline xmlns declarations (non-root)
grep -rn "xmlns:" app/src/main/res/layout/ \
  | grep -v "^[^:]*:[0-9]*:<[A-Za-z]" \
  | grep -v "xmlns:android\|xmlns:app\|xmlns:tools" \
  || echo "No inline namespace declarations found"

# Find all app:tint usages
grep -rn "app:tint" app/src/main/res/layout/
```

Or use the Python validator script (see below) which handles both checks
automatically with exact line numbers.

---

## CI Workflow Fix — Catch This Before Gradle Starts

The build failure happens late (after ~25 seconds of Gradle startup and
compilation). Add a pre-build validation step that catches it in ~1 second.

### Updated `.github/workflows/android.yml`

```yaml
name: Android CI

on:
  push:
    branches: [ "main" ]
  pull_request:
    branches: [ "main" ]

jobs:
  build:
    runs-on: ubuntu-latest

    steps:

    - name: Checkout repository
      uses: actions/checkout@v4

    - name: Set up JDK 17
      uses: actions/setup-java@v4
      with:
        java-version: '17'
        distribution: 'temurin'
        cache: 'gradle'

    - name: Set up Gradle
      uses: gradle/actions/setup-gradle@v4
      with:
        gradle-version: '8.7'

    # ── PRE-BUILD: Layout XML validator ──────────────────────────────────
    # Runs BEFORE Gradle. Catches inline namespace declarations and wrong
    # tint attributes that cause AAPT2 resource linking failures.
    # Fails immediately with exact file name and line number.
    - name: Validate layout XML files
      run: python3 scripts/validate_layouts.py "$GITHUB_WORKSPACE"

    - name: Ensure Gradle wrapper is executable
      run: |
        if [ ! -f gradlew ]; then
          gradle wrapper
        fi
        chmod +x gradlew

    # Suppress AGP 8.5 / compileSdk 35 version mismatch warning
    - name: Suppress compileSdk version warning
      run: |
        if ! grep -q "android.suppressUnsupportedCompileSdk" gradle.properties; then
          echo "android.suppressUnsupportedCompileSdk=35" >> gradle.properties
        fi

    - name: Build debug APK
      run: ./gradlew :app:assembleDebug --stacktrace

    # Verify APK was actually produced — Gradle can exit 0 without it
    - name: Verify APK exists
      run: |
        APK_PATH="app/build/outputs/apk/debug/app-debug.apk"
        if [ ! -f "$APK_PATH" ]; then
          echo "❌ APK not found at: $APK_PATH"
          find app/build/outputs/ -type f 2>/dev/null || echo "(outputs directory empty)"
          exit 1
        fi
        APK_SIZE=$(stat -c%s "$APK_PATH")
        echo "✅ APK found — size: $(( APK_SIZE / 1024 )) KB"
        if [ "$APK_SIZE" -lt 51200 ]; then
          echo "❌ APK is suspiciously small (${APK_SIZE} bytes)"
          exit 1
        fi

    - name: Upload debug APK
      uses: actions/upload-artifact@v4
      with:
        name: decluttr-debug-apk
        path: app/build/outputs/apk/debug/app-debug.apk
        if-no-files-found: error
```

---

## Pre-Build Validator Script — `scripts/validate_layouts.py`

Create this file at `scripts/validate_layouts.py` in your repo root.
It is called by the CI step above and can also be run locally:

```bash
python3 scripts/validate_layouts.py .
```

```python
#!/usr/bin/env python3
"""
validate_layouts.py
Pre-build validator for Android XML layout files.

Checks:
  1. INLINE_NAMESPACE  — xmlns: declared on non-root elements
  2. WRONG_TINT_ATTR   — app:tint used without xmlns:app on root
  3. WELL_FORMED_XML   — every file must be parseable XML

Exit 0 = all passed. Exit 1 = violations found.
Usage: python3 scripts/validate_layouts.py <repo-root>
"""

import re
import sys
import xml.etree.ElementTree as ET
from pathlib import Path

LAYOUT_DIRS = [
    "app/src/main/res/layout",
    "app/src/main/res/layout-land",
    "app/src/main/res/layout-sw600dp",
]

APPCOMPAT_NS_URL = "http://schemas.android.com/apk/res-auto"


def iter_xml_files(root: Path) -> list:
    files = []
    for layout_dir in LAYOUT_DIRS:
        d = root / layout_dir
        if d.exists():
            files.extend(sorted(d.glob("*.xml")))
    return files


def extract_root_opening_tag(raw: str) -> str:
    no_pi = re.sub(r'<\?xml[^?]*\?>', '', raw, count=1)
    m = re.search(r'<\w[\w:.]*(?:[^>]|\n)*?>', no_pi, re.DOTALL)
    return m.group(0) if m else ""


def check_file(path: Path) -> list:
    violations = []
    raw = path.read_text(encoding="utf-8")
    lines = raw.splitlines()

    try:
        ET.fromstring(raw)
    except ET.ParseError as e:
        violations.append(f"  [MALFORMED_XML] {e}")
        return violations

    root_tag = extract_root_opening_tag(raw)
    root_tag_end_pos = raw.find(root_tag) + len(root_tag) if root_tag else 0
    xmlns_on_root = APPCOMPAT_NS_URL in root_tag

    char_pos = 0
    for line_num, line in enumerate(lines, start=1):
        stripped = line.strip()
        line_start = char_pos
        char_pos += len(line) + 1

        if line_start < root_tag_end_pos:
            continue

        if "xmlns:" in stripped:
            idx = stripped.find("xmlns:")
            end = stripped.find("=", idx)
            prefix = stripped[idx:end].strip() if end != -1 else stripped[idx:idx+20]
            violations.append(
                f"  [INLINE_NAMESPACE] Line {line_num}: `{prefix}` declared on "
                f"non-root element. Move it to the root.\n    → {stripped}"
            )

        if "app:tint" in stripped:
            if not xmlns_on_root:
                violations.append(
                    f"  [WRONG_TINT_ATTR] Line {line_num}: `app:tint` used but "
                    f"`xmlns:app` not on root. Use `android:tint` for minSdk>=21.\n"
                    f"    → {stripped}"
                )
            else:
                violations.append(
                    f"  [PREFER_ANDROID_TINT] Line {line_num}: prefer `android:tint` "
                    f"over `app:tint` for minSdk>=21.\n    → {stripped}"
                )

    return violations


def main() -> int:
    if len(sys.argv) < 2:
        print("Usage: validate_layouts.py <repo-root>")
        return 1

    repo_root = Path(sys.argv[1]).resolve()
    xml_files = iter_xml_files(repo_root)

    if not xml_files:
        print("No layout XML files found.")
        return 0

    print(f"Scanning {len(xml_files)} layout file(s)...\n")
    total_violations = 0
    files_with_errors = 0

    for path in xml_files:
        rel = path.relative_to(repo_root)
        issues = check_file(path)
        if issues:
            files_with_errors += 1
            total_violations += len(issues)
            print(f"❌  {rel}")
            for issue in issues:
                print(issue)
            print()
        else:
            print(f"✅  {rel}")

    print()
    if total_violations == 0:
        print(f"All {len(xml_files)} file(s) passed. ✅")
        return 0
    else:
        print(
            f"Found {total_violations} violation(s) across {files_with_errors} "
            f"file(s). ❌\nFix the issues above before building."
        )
        return 1


if __name__ == "__main__":
    sys.exit(main())
```

---

## Summary of All Changes

| File | Change | Reason |
|---|---|---|
| `app/src/main/res/layout/item_discovery_app.xml` | Add `xmlns:app` to root `<LinearLayout>` | Namespace must be on root, not child |
| `app/src/main/res/layout/item_discovery_app.xml` | Change `app:tint` → `android:tint` on `<ImageView>` | Native attr for minSdk≥21, no AppCompat needed |
| `.github/workflows/android.yml` | Add `Validate layout XML files` step before Gradle | Catch this error in 1s, not 25s |
| `.github/workflows/android.yml` | Add `Verify APK exists` step after build | Explicit failure if Gradle exits 0 without APK |
| `.github/workflows/android.yml` | Add `suppressUnsupportedCompileSdk=35` step | Silence AGP 8.5 / compileSdk 35 warning |
| `scripts/validate_layouts.py` | New file | Reusable pre-build validator for layout XML |

---

## How to Apply This to a Different Project

1. Search all `res/layout/*.xml` files for any `xmlns:` declaration that is
   not on the first (root) element.
2. Move all such declarations to the root element's attribute list.
3. Search for all `app:tint` usages. If `minSdk >= 21`, replace with
   `android:tint` and remove the `xmlns:app` declaration if it is now unused.
4. Copy `scripts/validate_layouts.py` into the repo and add the validation
   step to CI before the Gradle build step.

---

## Key Diagnostic Signal

When you see this in a build error:

```
error: attribute ATTR_NAME (aka com.example.YOURPACKAGE:ATTR_NAME) not found.
```

The `aka com.example.YOURPACKAGE:` prefix is the giveaway. It means AAPT2
resolved your `app:ATTR_NAME` against the app's own package namespace instead
of AppCompat. The fix is always one of:
- Missing or misplaced `xmlns:app` declaration
- Wrong namespace prefix used for a natively supported Android attribute
