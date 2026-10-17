#!/usr/bin/env bash

# Installs sccache, tolerating a flaky GitHub API/CDN.
#
# The previous version piped `curl | tar` straight into the destination and never checked the
# result. When the release lookup got rate limited, the "archive" was really an error payload,
# tar failed, and the script still `chmod +x`ed the empty file it had produced. The build then
# died much later with a confusing "could not execute process `/usr/local/bin/sccache rustc -vV`
# ... Exec format error". Download to a temp file and validate it before installing anything.

set -euo pipefail

RETRIES=5

# $1=variant  $2=install_dir  $3=exe
install_from_gh() {
  local variant=$1 install_dir=$2 exe=$3
  local dest="$install_dir/$exe"
  local tmp
  tmp=$(mktemp -d)
  trap 'rm -rf "$tmp"' RETURN

  local ver=""
  for attempt in $(seq 1 $RETRIES); do
    # A rate-limited or failed lookup yields an error object, not a name; keep the field check.
    ver=$(curl -sSL --retry 3 --retry-delay 2 \
            'https://api.github.com/repos/mozilla/sccache/releases/latest' \
          | jq -r 'select(.tag_name != null) | .tag_name' 2>/dev/null || true)
    [ -n "$ver" ] && [ "$ver" != "null" ] && break
    echo "sccache: could not resolve the latest release (attempt $attempt/$RETRIES)" >&2
    sleep $((attempt * 3))
  done
  if [ -z "$ver" ] || [ "$ver" = "null" ]; then
    echo "sccache: giving up on the release lookup" >&2
    return 1
  fi

  local url="https://github.com/mozilla/sccache/releases/download/${ver}/sccache-${ver}-${variant}.tar.gz"
  local archive="$tmp/sccache.tar.gz"

  for attempt in $(seq 1 $RETRIES); do
    if curl -sSL --retry 3 --retry-delay 2 -o "$archive" "$url" \
       && gzip -t "$archive" 2>/dev/null; then
      break
    fi
    echo "sccache: download of $url failed (attempt $attempt/$RETRIES)" >&2
    sleep $((attempt * 3))
  done

  if ! gzip -t "$archive" 2>/dev/null; then
    echo "sccache: could not download a valid archive from $url" >&2
    return 1
  fi

  tar xzf "$archive" -C "$tmp" --wildcards "*/$exe"
  local extracted
  extracted=$(find "$tmp" -type f -name "$exe" -not -path "$tmp/sccache.tar.gz" | head -1)
  if [ -z "$extracted" ]; then
    echo "sccache: $exe not found inside the archive" >&2
    return 1
  fi

  mkdir -p "$install_dir"
  install -m 0755 "$extracted" "$dest"

  # Prove the binary actually runs before the build depends on it.
  if ! "$dest" --version >/dev/null 2>&1; then
    echo "sccache: installed binary at $dest does not run" >&2
    return 1
  fi
  echo "sccache: installed $ver to $dest"
}

if [ "${RUNNER_OS:-}" = "macOS" ]; then
  brew install sccache
elif [ "${RUNNER_OS:-}" = "Linux" ]; then
  # A runner image that already ships sccache is fine; do not replace a working copy.
  if ! command -v sccache >/dev/null 2>&1 || ! sccache --version >/dev/null 2>&1; then
    install_from_gh x86_64-unknown-linux-musl /usr/local/bin sccache
  fi
elif [ "${RUNNER_OS:-}" = "Windows" ]; then
  if ! command -v sccache >/dev/null 2>&1 || ! sccache --version >/dev/null 2>&1; then
    install_from_gh x86_64-pc-windows-msvc "$USERPROFILE/.cargo/bin" sccache.exe
  fi
fi
