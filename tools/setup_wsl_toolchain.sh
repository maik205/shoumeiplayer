#!/usr/bin/env bash
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
REPO_ROOT="$(cd "$SCRIPT_DIR/.." && pwd)"

# shellcheck source=../native/mpv/sources.env
source "$REPO_ROOT/native/mpv/sources.env"

INSTALL_DIR="/opt/shoumei/toolchains"
TARGET_NDK_DIR="$INSTALL_DIR/android-ndk-r29"
NDK_ARCHIVE="$INSTALL_DIR/android-ndk-r29-linux.zip"

echo "=== Shoumei Player: WSL Toolchain Setup ==="

# 1. Check if required commands are already present
required_commands=(git cmake meson ninja pkg-config python3 make autoconf automake libtoolize nasm yasm)
missing_commands=()
for cmd in "${required_commands[@]}"; do
    if ! command -v "$cmd" >/dev/null 2>&1; then
        missing_commands+=("$cmd")
    fi
done

if [ ${#missing_commands[@]} -gt 0 ]; then
    echo "--> Installing missing commands: ${missing_commands[*]}..."
    SUDO=""
    if [ "$(id -u)" -ne 0 ]; then
        SUDO="sudo"
    fi
    $SUDO apt-get update -qq
    $SUDO apt-get install -y -qq \
        build-essential \
        cmake \
        meson \
        ninja-build \
        pkg-config \
        autoconf \
        automake \
        libtool \
        unzip \
        curl \
        wget \
        nasm \
        yasm \
        python3-pip \
        python3-jinja2 \
        python3-mako
else
    echo "--> All required build commands are already installed."
fi

# 2. Setup Android NDK r29
CLANG="$TARGET_NDK_DIR/toolchains/llvm/prebuilt/linux-x86_64/bin/clang"
if [ -x "$CLANG" ]; then
    echo "--> Android NDK r29 is already verified at $TARGET_NDK_DIR"
else
    SUDO=""
    if [ "$(id -u)" -ne 0 ]; then
        SUDO="sudo"
    fi
    $SUDO mkdir -p "$INSTALL_DIR"
    echo "--> Downloading Android NDK r29 from $ANDROID_NDK_LINUX_URL..."
    if [ ! -f "$NDK_ARCHIVE" ] || ! echo "$ANDROID_NDK_LINUX_SHA256  $NDK_ARCHIVE" | sha256sum -c --status 2>/dev/null; then
        $SUDO curl -fL --progress-bar -o "$NDK_ARCHIVE.tmp" "$ANDROID_NDK_LINUX_URL"
        echo "--> Verifying NDK checksum..."
        echo "$ANDROID_NDK_LINUX_SHA256  $NDK_ARCHIVE.tmp" | sha256sum -c -
        $SUDO mv "$NDK_ARCHIVE.tmp" "$NDK_ARCHIVE"
    fi

    echo "--> Extracting Android NDK r29 into $INSTALL_DIR..."
    $SUDO unzip -q -o "$NDK_ARCHIVE" -d "$INSTALL_DIR"
    $SUDO rm -f "$NDK_ARCHIVE"
fi

# 3. Verify Clang in NDK
if [ ! -x "$CLANG" ]; then
    echo "Error: Clang binary not found or not executable at $CLANG" >&2
    exit 1
fi
echo "--> Toolchain clang: $($CLANG --version | head -n 1)"

# 4. Check repo submodule
echo "--> Verifying native/mpv/upstream submodule..."
SUBMODULE_HEAD="$(git -C "$REPO_ROOT/native/mpv/upstream" rev-parse HEAD 2>/dev/null || echo "")"
if [ "$SUBMODULE_HEAD" = "$MPV_COMMIT" ]; then
    echo "--> Upstream mpv submodule matches pinned commit: $MPV_COMMIT"
else
    echo "--> Notice: upstream mpv is at '$SUBMODULE_HEAD', expected '$MPV_COMMIT'"
fi

echo "=== WSL Toolchain Setup Complete! ==="
echo "ANDROID_NDK_ROOT is configured at $TARGET_NDK_DIR"
