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

find_package(Boost REQUIRED)

add_subdirectory(${LIBTORRENT_DIR} ${CMAKE_CURRENT_BINARY_DIR}/libtorrent EXCLUDE_FROM_ALL)

add_library(simpletorrent SHARED torrent_jni.cpp)

# Inject Boost include path via target_compile_options, NOT include_directories().
# Rationale: CMake silently filters /usr/include from INCLUDE_DIRECTORIES on Linux
# because it matches the host g++ implicit include list. target_compile_options()
# emits raw -I flags that bypass this filtering. Using cppFlags/CMAKE_CXX_FLAGS is
# also wrong because those affect configure-time feature probes (e.g. std::atomic
# check in libtorrent) and break cross-compilation with host headers in the way.
if(DEFINED BOOST_INCLUDE_DIR)
    target_compile_options(torrent-rasterbar PRIVATE "-I${BOOST_INCLUDE_DIR}")
    target_compile_options(simpletorrent     PRIVATE "-I${BOOST_INCLUDE_DIR}")
endif()

target_link_libraries(simpletorrent
    PRIVATE
        torrent-rasterbar
        android
        log
)
