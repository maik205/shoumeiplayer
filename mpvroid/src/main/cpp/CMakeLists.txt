cmake_minimum_required(VERSION 3.22.1)
project(mpvroid_jni LANGUAGES CXX)

set(MPV_LIB "${CMAKE_CURRENT_SOURCE_DIR}/../jniLibs/${ANDROID_ABI}/libmpv.so")
if(NOT EXISTS "${MPV_LIB}")
    message(FATAL_ERROR
        "Missing ${MPV_LIB}. Build libmpv from the pinned official mpv-player/mpv "
        "source; see native/mpv/README.md.")
endif()

add_library(mpv SHARED IMPORTED)
set_target_properties(mpv PROPERTIES IMPORTED_LOCATION "${MPV_LIB}")

add_library(mpvroid_jni SHARED mpv_jni.cpp)
target_include_directories(mpvroid_jni PRIVATE
    "${CMAKE_CURRENT_SOURCE_DIR}/../../../../native/mpv/upstream/include")
target_link_libraries(mpvroid_jni PRIVATE mpv log dl)
