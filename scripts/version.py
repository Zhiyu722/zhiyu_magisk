#!/usr/bin/env python3
"""Print the user-facing version for a Magisk version code.

Upstream derives a release name from the version code alone, so 31000 is "31.0"
and 31100 is "31.1". The build needs the same string in several places (the build
config that ends up in versionName, MAGISK_VER and the native MAGISK_VERSION; the
release tag; the artifact names), and computing it inline in each place is how it
drifted: `f"{code / 1000:g}"` looks right but formats 31000 as "31", dropping the
trailing zero, which then showed up as a release called v31 and an app reporting
versionName 31.

    $ python3 scripts/version.py 31000
    31.0
"""

import sys


def version_name(version_code: int) -> str:
    major, minor = divmod(version_code, 1000)
    if minor == 0:
        return f"{major}.0"
    # 31100 -> "31.1", 31230 -> "31.23"
    return f"{major}.{minor:03d}".rstrip("0").rstrip(".")


def main(argv: list[str]) -> int:
    if len(argv) != 2:
        print(f"usage: {argv[0]} <versionCode>", file=sys.stderr)
        return 2
    try:
        code = int(argv[1])
    except ValueError:
        print(f"version code must be an integer, got {argv[1]!r}", file=sys.stderr)
        return 2
    if code <= 0:
        print(f"version code must be positive, got {code}", file=sys.stderr)
        return 2
    print(version_name(code))
    return 0


if __name__ == "__main__":
    sys.exit(main(sys.argv))
