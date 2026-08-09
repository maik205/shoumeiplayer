#!/usr/bin/env bash
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
REPO_ROOT="$(cd "$SCRIPT_DIR/../.." && pwd)"
# shellcheck source=sources.env
source "$SCRIPT_DIR/sources.env"

BUILD_ROOT="${SHOUMEI_MPV_BUILD_ROOT:-$HOME/.cache/shoumei-mpv-source-build}"
NDK_ROOT="${ANDROID_NDK_ROOT:-/opt/shoumei/toolchains/android-ndk-r29}"
ANDROID_API="${ANDROID_API:-28}"
REQUESTED_ABIS=("${@:-armeabi-v7a arm64-v8a x86 x86_64}")
SOURCE_ROOT="$BUILD_ROOT/sources"
OUTPUT_ROOT="$BUILD_ROOT/output"
TOOLCHAIN="$NDK_ROOT/toolchains/llvm/prebuilt/linux-x86_64"

required_commands=(git cmake meson ninja pkg-config python3 make autoconf automake libtoolize)
for command_name in "${required_commands[@]}"; do
    command -v "$command_name" >/dev/null || {
        echo "Missing build command: $command_name" >&2
        exit 1
    }
done
test -x "$TOOLCHAIN/bin/clang" || {
    echo "Android NDK r29 was not found at $NDK_ROOT" >&2
    exit 1
}
test "$(git -C "$REPO_ROOT/native/mpv/upstream" rev-parse HEAD)" = "$MPV_COMMIT" || {
    echo "native/mpv/upstream is not pinned to $MPV_COMMIT" >&2
    exit 1
}

mkdir -p "$SOURCE_ROOT" "$OUTPUT_ROOT"

checkout_source() {
    local name="$1" url="$2" commit="$3"
    local destination="$SOURCE_ROOT/$name"
    if [[ ! -d "$destination/.git" ]]; then
        git clone --filter=blob:none --no-checkout "$url" "$destination"
    fi
    git -C "$destination" fetch --depth 1 origin "$commit"
    git -C "$destination" checkout --detach --force "$commit"
    test "$(git -C "$destination" rev-parse HEAD)" = "$commit"
}

checkout_source ffmpeg "$FFMPEG_URL" "$FFMPEG_COMMIT"
checkout_source mbedtls "$MBEDTLS_URL" "$MBEDTLS_COMMIT"
git -C "$SOURCE_ROOT/mbedtls" submodule update --init --recursive --depth 1
checkout_source freetype "$FREETYPE_URL" "$FREETYPE_COMMIT"
checkout_source fribidi "$FRIBIDI_URL" "$FRIBIDI_COMMIT"
checkout_source harfbuzz "$HARFBUZZ_URL" "$HARFBUZZ_COMMIT"
checkout_source libunibreak "$LIBUNIBREAK_URL" "$LIBUNIBREAK_COMMIT"
checkout_source libass "$LIBASS_URL" "$LIBASS_COMMIT"
checkout_source libplacebo "$LIBPLACEBO_URL" "$LIBPLACEBO_COMMIT"
git -C "$SOURCE_ROOT/libplacebo" submodule update --init --recursive --depth 1

abi_config() {
    case "$1" in
        armeabi-v7a) TARGET=armv7a-linux-androideabi; SYSROOT_TRIPLE=arm-linux-androideabi; AUTOTOOLS_HOST=arm-linux-androideabi; MESON_CPU_FAMILY=arm; MESON_CPU=armv7; FFMPEG_ARCH=arm; FFMPEG_EXTRA=(--cpu=armv7-a --enable-thumb); LIBASS_EXTRA=() ;;
        arm64-v8a) TARGET=aarch64-linux-android; SYSROOT_TRIPLE=aarch64-linux-android; AUTOTOOLS_HOST=aarch64-linux-android; MESON_CPU_FAMILY=aarch64; MESON_CPU=armv8-a; FFMPEG_ARCH=aarch64; FFMPEG_EXTRA=(); LIBASS_EXTRA=() ;;
        x86) TARGET=i686-linux-android; SYSROOT_TRIPLE=i686-linux-android; AUTOTOOLS_HOST=i686-linux-android; MESON_CPU_FAMILY=x86; MESON_CPU=i686; FFMPEG_ARCH=x86; FFMPEG_EXTRA=(--cpu=i686 --disable-asm); LIBASS_EXTRA=(-Dasm=disabled) ;;
        x86_64) TARGET=x86_64-linux-android; SYSROOT_TRIPLE=x86_64-linux-android; AUTOTOOLS_HOST=x86_64-linux-android; MESON_CPU_FAMILY=x86_64; MESON_CPU=x86_64; FFMPEG_ARCH=x86_64; FFMPEG_EXTRA=(); LIBASS_EXTRA=(-Dasm=disabled) ;;
        *) echo "Unsupported ABI: $1" >&2; exit 1 ;;
    esac
    CC="$TOOLCHAIN/bin/${TARGET}${ANDROID_API}-clang"
    CXX="$TOOLCHAIN/bin/${TARGET}${ANDROID_API}-clang++"
    AR="$TOOLCHAIN/bin/llvm-ar"
    RANLIB="$TOOLCHAIN/bin/llvm-ranlib"
    STRIP="$TOOLCHAIN/bin/llvm-strip"
}

write_cross_file() {
    local path="$1"
    cat >"$path" <<EOF
[binaries]
c = '$CC'
cpp = '$CXX'
ar = '$AR'
strip = '$STRIP'
pkg-config = 'pkg-config'

[host_machine]
system = 'android'
cpu_family = '$MESON_CPU_FAMILY'
cpu = '$MESON_CPU'
endian = 'little'

[properties]
needs_exe_wrapper = true

[built-in options]
c_args = ['-fPIC']
cpp_args = ['-fPIC']
EOF
}

for abi in ${REQUESTED_ABIS[*]}; do
    abi_config "$abi"
    BUILD="$BUILD_ROOT/build/$abi"
    PREFIX="$BUILD_ROOT/prefix/$abi"
    CROSS="$BUILD/meson-android.ini"
    rm -rf "$BUILD" "$PREFIX"
    mkdir -p "$BUILD" "$PREFIX"
    write_cross_file "$CROSS"
    export CC CXX AR RANLIB STRIP
    export PKG_CONFIG_LIBDIR="$PREFIX/lib/pkgconfig:$PREFIX/share/pkgconfig"
    export PKG_CONFIG_PATH=
    export PKG_CONFIG_SYSROOT_DIR=/

    cmake -S "$SOURCE_ROOT/mbedtls" -B "$BUILD/mbedtls" -G Ninja \
        -DCMAKE_TOOLCHAIN_FILE="$NDK_ROOT/build/cmake/android.toolchain.cmake" \
        -DANDROID_ABI="$abi" -DANDROID_PLATFORM="$ANDROID_API" \
        -DCMAKE_INSTALL_PREFIX="$PREFIX" -DENABLE_PROGRAMS=OFF -DENABLE_TESTING=OFF \
        -DUSE_SHARED_MBEDTLS_LIBRARY=OFF -DUSE_STATIC_MBEDTLS_LIBRARY=ON
    cmake --build "$BUILD/mbedtls" --target install

    cmake -S "$SOURCE_ROOT/freetype" -B "$BUILD/freetype" -G Ninja \
        -DCMAKE_TOOLCHAIN_FILE="$NDK_ROOT/build/cmake/android.toolchain.cmake" \
        -DANDROID_ABI="$abi" -DANDROID_PLATFORM="$ANDROID_API" \
        -DCMAKE_INSTALL_PREFIX="$PREFIX" -DBUILD_SHARED_LIBS=OFF \
        -DFT_DISABLE_BZIP2=TRUE -DFT_DISABLE_BROTLI=TRUE -DFT_DISABLE_HARFBUZZ=TRUE \
        -DFT_DISABLE_PNG=TRUE -DFT_DISABLE_ZLIB=TRUE
    cmake --build "$BUILD/freetype" --target install

    meson setup "$BUILD/fribidi" "$SOURCE_ROOT/fribidi" --cross-file "$CROSS" \
        --prefix "$PREFIX" --default-library static --buildtype release \
        -Ddocs=false -Dbin=false -Dtests=false
    meson compile -C "$BUILD/fribidi"
    meson install -C "$BUILD/fribidi"

    if [[ ! -x "$SOURCE_ROOT/libunibreak/configure" ]]; then
        (cd "$SOURCE_ROOT/libunibreak" && autoreconf -fiv)
    fi
    mkdir -p "$BUILD/libunibreak"
    (cd "$BUILD/libunibreak" && "$SOURCE_ROOT/libunibreak/configure" \
        --host="$AUTOTOOLS_HOST" --prefix="$PREFIX" --disable-shared --enable-static)
    make -C "$BUILD/libunibreak" -j"$(nproc)"
    make -C "$BUILD/libunibreak" install

    meson setup "$BUILD/harfbuzz" "$SOURCE_ROOT/harfbuzz" --cross-file "$CROSS" \
        --prefix "$PREFIX" --default-library static --buildtype release \
        -Dfreetype=enabled -Dglib=disabled -Dgobject=disabled -Dcairo=disabled \
        -Dicu=disabled -Dtests=disabled -Ddocs=disabled -Dutilities=disabled
    meson compile -C "$BUILD/harfbuzz"
    meson install -C "$BUILD/harfbuzz"

    meson setup "$BUILD/libass" "$SOURCE_ROOT/libass" --cross-file "$CROSS" \
        --prefix "$PREFIX" --default-library static --buildtype release \
        -Dfontconfig=disabled -Ddirectwrite=disabled -Dcoretext=disabled \
        -Drequire-system-font-provider=false -Dtest=disabled -Db_pie=false \
        "${LIBASS_EXTRA[@]}"
    meson compile -C "$BUILD/libass"
    meson install -C "$BUILD/libass"

    meson setup "$BUILD/libplacebo" "$SOURCE_ROOT/libplacebo" --cross-file "$CROSS" \
        --prefix "$PREFIX" --default-library static --buildtype release \
        -Dvulkan=disabled -Dopengl=enabled -Dgl-proc-addr=enabled -Dd3d11=disabled \
        -Dglslang=disabled -Dshaderc=disabled -Dlcms=disabled -Ddovi=disabled \
        -Dlibdovi=disabled -Ddemos=false -Dtests=false -Dbench=false -Dfuzz=false \
        -Dunwind=disabled -Dxxhash=disabled
    meson compile -C "$BUILD/libplacebo"
    meson install -C "$BUILD/libplacebo"

    mkdir -p "$BUILD/ffmpeg"
    (cd "$BUILD/ffmpeg" && "$SOURCE_ROOT/ffmpeg/configure" \
        --prefix="$PREFIX" --target-os=android --arch="$FFMPEG_ARCH" \
        --enable-cross-compile --cc="$CC" --cxx="$CXX" --ar="$AR" \
        --ranlib="$RANLIB" --strip="$STRIP" --sysroot="$TOOLCHAIN/sysroot" \
        --enable-shared --disable-static --disable-programs --disable-doc --disable-debug \
        --enable-version3 --enable-jni --enable-mediacodec --enable-mbedtls \
        --extra-cflags="-fPIC -I$PREFIX/include" \
        --extra-ldflags="-L$PREFIX/lib" "${FFMPEG_EXTRA[@]}")
    make -C "$BUILD/ffmpeg" -j"$(nproc)"
    make -C "$BUILD/ffmpeg" install

    rm -rf "$BUILD/mpv-source"
    mkdir -p "$BUILD/mpv-source"
    git -C "$REPO_ROOT/native/mpv/upstream" archive "$MPV_COMMIT" | tar -x -C "$BUILD/mpv-source"
    meson setup "$BUILD/mpv" "$BUILD/mpv-source" --cross-file "$CROSS" \
        --prefix "$PREFIX" --buildtype release --default-library shared \
        -Dcplayer=false -Dlibmpv=true -Dbuild-date=false -Dtests=false \
        -Dlua=disabled -Djavascript=disabled -Dlibarchive=disabled -Dlibbluray=disabled \
        -Ddvdnav=disabled -Dcdda=disabled -Drubberband=disabled -Duchardet=disabled \
        -Dzimg=disabled -Djpeg=disabled -Dlcms2=disabled -Dvulkan=disabled \
        -Degl-android=enabled -Dandroid-media-ndk=enabled
    meson compile -C "$BUILD/mpv"
    meson install -C "$BUILD/mpv"

    DEST="$OUTPUT_ROOT/$abi"
    rm -rf "$DEST"
    mkdir -p "$DEST"
    for library in libavcodec.so libavdevice.so libavfilter.so libavformat.so \
        libavutil.so libmpv.so libswresample.so libswscale.so; do
        source_path="$(find "$PREFIX/lib" -maxdepth 1 -name "$library*" -type f | head -1)"
        test -n "$source_path" || { echo "Missing $library for $abi" >&2; exit 1; }
        cp "$source_path" "$DEST/$library"
    done
    cp "$TOOLCHAIN/sysroot/usr/lib/$SYSROOT_TRIPLE/libc++_shared.so" "$DEST/libc++_shared.so"
    "$TOOLCHAIN/bin/llvm-strip" --strip-unneeded "$DEST"/*.so
    "$TOOLCHAIN/bin/llvm-readelf" -h "$DEST/libmpv.so" | grep -E 'Class:|Machine:'
done

echo "Native outputs: $OUTPUT_ROOT"
