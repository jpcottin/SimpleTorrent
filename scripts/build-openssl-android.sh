#!/usr/bin/env bash
# Build static OpenSSL for Android (arm64-v8a, x86_64) into libs/openssl-android/<abi>.
# Needed for libtorrent's wss:// WebSocket trackers and libdatachannel's DTLS
# (WebTorrent support), plus encrypted peer connections.
#
# Usage: scripts/build-openssl-android.sh [-f]
#   -f  force rebuild even if outputs exist
#
# NDK is located via $ANDROID_NDK_ROOT, else the highest version under
# $ANDROID_HOME/ndk or ~/Library/Android/sdk/ndk.
set -euo pipefail

OPENSSL_VERSION=3.5.7
MIN_SDK=24
ABIS=(arm64-v8a x86_64)

ROOT="$(cd "$(dirname "$0")/.." && pwd)"
OUT_BASE="$ROOT/libs/openssl-android"
SRC_DIR="$OUT_BASE/.src"
FORCE=0
[[ "${1:-}" == "-f" ]] && FORCE=1

# --- locate NDK ---
if [[ -z "${ANDROID_NDK_ROOT:-}" ]]; then
    for base in "${ANDROID_HOME:-}/ndk" "$HOME/Library/Android/sdk/ndk" "$HOME/Android/Sdk/ndk"; do
        if [[ -d "$base" ]]; then
            ANDROID_NDK_ROOT="$base/$(ls "$base" | sort -V | tail -1)"
            break
        fi
    done
fi
[[ -d "${ANDROID_NDK_ROOT:-}" ]] || { echo "error: NDK not found; set ANDROID_NDK_ROOT" >&2; exit 1; }
echo "Using NDK: $ANDROID_NDK_ROOT"

case "$(uname -s)" in
    Darwin) HOST_TAG=darwin-x86_64 ;;   # NDK ships x86_64 tag on macOS incl. arm64 hosts
    Linux)  HOST_TAG=linux-x86_64 ;;
    *) echo "error: unsupported host $(uname -s)" >&2; exit 1 ;;
esac
TOOLCHAIN="$ANDROID_NDK_ROOT/toolchains/llvm/prebuilt/$HOST_TAG"
[[ -d "$TOOLCHAIN" ]] || { echo "error: toolchain not found at $TOOLCHAIN" >&2; exit 1; }

# --- fetch source ---
TARBALL="$SRC_DIR/openssl-$OPENSSL_VERSION.tar.gz"
if [[ ! -f "$TARBALL" ]]; then
    mkdir -p "$SRC_DIR"
    echo "Downloading OpenSSL $OPENSSL_VERSION..."
    curl -fL --retry 3 -o "$TARBALL" \
        "https://github.com/openssl/openssl/releases/download/openssl-$OPENSSL_VERSION/openssl-$OPENSSL_VERSION.tar.gz"
fi

JOBS=$(getconf _NPROCESSORS_ONLN)

for ABI in "${ABIS[@]}"; do
    PREFIX="$OUT_BASE/$ABI"
    if [[ $FORCE -eq 0 && -f "$PREFIX/lib/libssl.a" ]]; then
        echo "$ABI: already built ($PREFIX), skipping (use -f to rebuild)"
        continue
    fi

    case "$ABI" in
        arm64-v8a) TARGET=android-arm64 ;;
        x86_64)    TARGET=android-x86_64 ;;
        *) echo "error: unmapped ABI $ABI" >&2; exit 1 ;;
    esac

    BUILD_DIR="$SRC_DIR/build-$ABI"
    rm -rf "$BUILD_DIR" "$PREFIX"
    mkdir -p "$BUILD_DIR"
    tar -xzf "$TARBALL" -C "$BUILD_DIR" --strip-components=1

    echo "=== Building $ABI ($TARGET, API $MIN_SDK) ==="
    (
        cd "$BUILD_DIR"
        export ANDROID_NDK_ROOT PATH="$TOOLCHAIN/bin:$PATH"
        ./Configure "$TARGET" -D__ANDROID_API__=$MIN_SDK \
            no-shared no-tests no-apps no-docs \
            --prefix="$PREFIX" --libdir=lib >/dev/null
        make -j"$JOBS" build_libs >/dev/null
        make install_dev >/dev/null
    )
    echo "$ABI: installed to $PREFIX"
done

echo "Done."
