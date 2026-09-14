#!/usr/bin/env python3
"""
Verify that every Java package under a given base package that contains at least
one .java file other than package-info.java also contains a package-info.java
annotated with @NullMarked.

Defaults:
  base_source_root = {CurrentDirectory}/src/main/java
  base_package     = first subdirectory under source root containing a .java file

Accepted annotation forms:
  1) @org.jspecify.annotations.NullMarked
  2) @NullMarked with import org.jspecify.annotations.NullMarked;

Usage:
  python verify_nullmarked.py
  python verify_nullmarked.py /repo/src/main/java
  python verify_nullmarked.py /repo/src/main/java com.example
"""

from __future__ import annotations

import argparse
import re
import sys
from pathlib import Path


BLOCK_COMMENT_RE = re.compile(r"/\*.*?\*/", re.DOTALL)
LINE_COMMENT_RE = re.compile(r"//.*?$", re.MULTILINE)

FQN_ANNOTATION_RE = re.compile(
    r"@org\s*\.\s*jspecify\s*\.\s*annotations\s*\.\s*NullMarked\b"
)
SIMPLE_ANNOTATION_RE = re.compile(r"@NullMarked\b")
IMPORT_RE = re.compile(
    r"import\s+org\s*\.\s*jspecify\s*\.\s*annotations\s*\.\s*NullMarked\s*;"
)
PACKAGE_RE = re.compile(r"\bpackage\s+([a-zA-Z_]\w*(?:\.[a-zA-Z_]\w*)*)\s*;")


def strip_comments(text: str) -> str:
    text = BLOCK_COMMENT_RE.sub("", text)
    text = LINE_COMMENT_RE.sub("", text)
    return text


def expected_package_name(base_package: str, package_dir: Path, base_dir: Path) -> str:
    rel = package_dir.relative_to(base_dir)
    if not rel.parts:
        return base_package
    return base_package + "." + ".".join(rel.parts)


def package_info_is_valid(package_info_path: Path, expected_package: str):
    issues = []

    try:
        raw = package_info_path.read_text(encoding="utf-8")
    except UnicodeDecodeError:
        raw = package_info_path.read_text(encoding="latin-1")

    text = strip_comments(raw)

    package_match = PACKAGE_RE.search(text)
    if not package_match:
        issues.append("missing package declaration")
    else:
        actual_package = package_match.group(1)
        if actual_package != expected_package:
            issues.append(
                f"package declaration mismatch: expected '{expected_package}', found '{actual_package}'"
            )

    has_fqn = bool(FQN_ANNOTATION_RE.search(text))
    has_simple = bool(SIMPLE_ANNOTATION_RE.search(text))
    has_import = bool(IMPORT_RE.search(text))

    if not has_fqn and not (has_simple and has_import):
        issues.append(
            "missing @NullMarked annotation "
            "(expected '@org.jspecify.annotations.NullMarked' or '@NullMarked' with import)"
        )

    return (not issues, issues)


def find_java_package_dirs(root: Path):
    """Return directories containing at least one .java file other than package-info.java."""
    java_dirs = set()

    for java_file in root.rglob("*.java"):
        if java_file.name != "package-info.java":
            java_dirs.add(java_file.parent)

    return sorted(java_dirs)


def detect_base_package(source_root: Path):
    """
    Detect the first top-level directory containing a .java file.
    """
    for child in sorted(source_root.iterdir()):
        if child.is_dir():
            if any(f.name != "package-info.java" for f in child.rglob("*.java")):
                return child.name

    raise RuntimeError("Could not detect base package automatically")


def verify(base_source_root: Path, base_package: str):
    base_package_dir = base_source_root / Path(*base_package.split("."))

    if not base_package_dir.is_dir():
        print(f"ERROR: Base package directory does not exist: {base_package_dir}", file=sys.stderr)
        return 2

    package_dirs = find_java_package_dirs(base_package_dir)

    failures = []

    for package_dir in package_dirs:
        expected_pkg = expected_package_name(base_package, package_dir, base_package_dir)
        package_info = package_dir / "package-info.java"

        if not package_info.is_file():
            failures.append((expected_pkg, "missing package-info.java"))
            continue

        ok, issues = package_info_is_valid(package_info, expected_pkg)
        if not ok:
            failures.append((expected_pkg, "; ".join(issues)))

    if not failures:
        print(
            f"OK: all {len(package_dirs)} package(s) with Java classes under "
            f"'{base_package}' have valid package-info.java with @NullMarked"
        )
        return 0

    print(f"FAILED: {len(failures)} invalid package(s):", file=sys.stderr)
    for pkg, reason in failures:
        print(f"  - {pkg}: {reason}", file=sys.stderr)

    return 1


def parse_args():
    parser = argparse.ArgumentParser(description="Verify @NullMarked package-info.java usage")
    parser.add_argument("base_source_root", nargs="?", default=None)
    parser.add_argument("base_package", nargs="?", default=None)
    return parser.parse_args()


def main():
    args = parse_args()

    # Default source root
    if args.base_source_root:
        source_root = Path(args.base_source_root).resolve()
    else:
        source_root = Path.cwd() / "src" / "main" / "java"

    if not source_root.is_dir():
        print(f"ERROR: Source root does not exist: {source_root}", file=sys.stderr)
        return 2

    # Default base package
    if args.base_package:
        base_package = args.base_package
    else:
        base_package = detect_base_package(source_root)
        print(f"Detected base package: {base_package}")

    return verify(source_root, base_package)


if __name__ == "__main__":
    sys.exit(main())
