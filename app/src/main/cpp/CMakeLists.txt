cmake_minimum_required(VERSION 3.22.1)
project("simpletorrent")

set(CMAKE_CXX_STANDARD 17)
set(CMAKE_CXX_STANDARD_REQUIRED ON)

# Default to the submodule path (relative to this CMakeLists.txt)
if(NOT DEFINED LIBTORRENT_DIR)
    set(LIBTORRENT_DIR "${CMAKE_CURRENT_SOURCE_DIR}/../../../../libs/libtorrent")
endif()

if(NOT EXISTS "${LIBTORRENT_DIR}/CMakeLists.txt")
    message(FATAL_ERROR
        "libtorrent not found at '${LIBTORRENT_DIR}'.\n"
        "Run: git submodule update --init --recursive")
endif()

set(BUILD_SHARED_LIBS   OFF CACHE BOOL "" FORCE)

# Static OpenSSL built by scripts/build-openssl-android.sh — needed for wss://
# WebSocket trackers (WebTorrent), libdatachannel's DTLS, and encrypted peers.
set(OPENSSL_ROOT_DIR "${CMAKE_CURRENT_SOURCE_DIR}/../../../../libs/openssl-android/${ANDROID_ABI}")
if(NOT EXISTS "${OPENSSL_ROOT_DIR}/lib/libssl.a")
    message(FATAL_ERROR
        "OpenSSL for ${ANDROID_ABI} not found at '${OPENSSL_ROOT_DIR}'.\n"
        "Run: scripts/build-openssl-android.sh")
endif()
set(OPENSSL_USE_STATIC_LIBS TRUE)
# The NDK toolchain re-roots find_library/find_path into the sysroot
# (CMAKE_FIND_ROOT_PATH_MODE_* = ONLY); list the OpenSSL prefix as an extra
# root so FindOpenSSL can see it.
list(APPEND CMAKE_FIND_ROOT_PATH "${OPENSSL_ROOT_DIR}")

set(encryption          ON  CACHE BOOL "" FORCE)
set(webtorrent          ON  CACHE BOOL "" FORCE)
set(dht                 ON  CACHE BOOL "" FORCE)
set(build_tests         OFF CACHE BOOL "" FORCE)
set(build_examples      OFF CACHE BOOL "" FORCE)
set(build_tools         OFF CACHE BOOL "" FORCE)
set(python-bindings     OFF CACHE BOOL "" FORCE)

# Allow find_package to reach host Boost headers during cross-compilation
# (Boost is header-only for libtorrent's needs, so macOS/Linux host headers are fine)
set(CMAKE_FIND_ROOT_PATH_MODE_PACKAGE BOTH)

find_package(Boost REQUIRED)

add_subdirectory(${LIBTORRENT_DIR} ${CMAKE_CURRENT_BINARY_DIR}/libtorrent EXCLUDE_FROM_ALL)

add_library(simpletorrent SHARED torrent_jni.cpp)

# Inject Boost include path via target_compile_options, NOT include_directories().
# Rationale: CMake silently filters /usr/include from INCLUDE_DIRECTORIES on Linux
# because it matches the host g++ implicit include list. target_compile_options()
# emits raw -I flags that bypass this filtering. Using cppFlags/CMAKE_CXX_FLAGS is
# also wrong because those affect configure-time feature probes (e.g. std::atomic
# check in libtorrent) and break cross-compilation with host headers in the way.
#
# IMPORTANT: BOOST_INCLUDE_DIR must point at a directory that contains ONLY Boost
# (i.e. just a `boost/` subdir), never the host's /usr/include. The NDK headers use
# #include_next, which walks past the sysroot into any dir on the search path; if
# host libc headers (limits.h, features.h, ...) are reachable there, the cross
# build breaks. On macOS, Homebrew's prefix already satisfies this. On Debian/Ubuntu
# the system Boost lives in /usr/include alongside glibc, so point BOOST_INCLUDE_DIR
# at an isolated Boost prefix instead (see README "Linux").
if(DEFINED BOOST_INCLUDE_DIR)
    target_compile_options(torrent-rasterbar PRIVATE "-I${BOOST_INCLUDE_DIR}")
    target_compile_options(simpletorrent     PRIVATE "-I${BOOST_INCLUDE_DIR}")
endif()

# Clang 21 (NDK r29) triggers BOOST_ASIO_HAS_STD_ALIGNED_ALLOC auto-detection but the
# Android sysroot doesn't expose std::aligned_alloc reliably; use Boost's own impl.
# Also suppress C++17 deprecated redundant constexpr static member definitions.
target_compile_options(torrent-rasterbar PRIVATE
    "-DBOOST_ASIO_DISABLE_STD_ALIGNED_ALLOC"
    "-Wno-deprecated-redundant-constexpr-static-def"
)
target_compile_options(simpletorrent     PRIVATE
    "-DBOOST_ASIO_DISABLE_STD_ALIGNED_ALLOC"
    "-Wno-deprecated-redundant-constexpr-static-def"
)

target_link_libraries(simpletorrent
    PRIVATE
        torrent-rasterbar
        android
        log
)
