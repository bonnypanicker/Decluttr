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
