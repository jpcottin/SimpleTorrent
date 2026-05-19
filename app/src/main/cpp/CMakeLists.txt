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
set(encryption          OFF CACHE BOOL "" FORCE)
set(dht                 ON  CACHE BOOL "" FORCE)
set(build_tests         OFF CACHE BOOL "" FORCE)
set(build_examples      OFF CACHE BOOL "" FORCE)
set(build_tools         OFF CACHE BOOL "" FORCE)
set(python-bindings     OFF CACHE BOOL "" FORCE)

# Allow find_package to reach host Boost headers during cross-compilation
# (Boost is header-only for libtorrent's needs, so macOS/Linux host headers are fine)
set(CMAKE_FIND_ROOT_PATH_MODE_PACKAGE BOTH)

# Explicitly resolve Boost include dirs and inject them globally.
# The NDK sysroot isolation can prevent libtorrent's own find_package(Boost) from
# propagating include paths correctly; doing it here ensures every target sees them.
find_package(Boost REQUIRED)
include_directories(SYSTEM ${Boost_INCLUDE_DIRS})

add_subdirectory(${LIBTORRENT_DIR} ${CMAKE_CURRENT_BINARY_DIR}/libtorrent EXCLUDE_FROM_ALL)

add_library(simpletorrent SHARED torrent_jni.cpp)

target_link_libraries(simpletorrent
    PRIVATE
        torrent-rasterbar
        android
        log
)
