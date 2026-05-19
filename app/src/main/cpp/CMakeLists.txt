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

# Modern Boost CMake (>=1.70) uses imported targets and leaves Boost_INCLUDE_DIRS empty.
# When cross-compiling with the NDK, the sysroot boundary also strips -isystem paths.
# BOOST_INCLUDE_DIR is passed explicitly from build.gradle.kts so we use plain -I here.
if(DEFINED BOOST_INCLUDE_DIR)
    include_directories(${BOOST_INCLUDE_DIR})
elseif(TARGET Boost::headers)
    get_target_property(_boost_inc Boost::headers INTERFACE_INCLUDE_DIRECTORIES)
    if(_boost_inc)
        include_directories(${_boost_inc})
    endif()
endif()

add_subdirectory(${LIBTORRENT_DIR} ${CMAKE_CURRENT_BINARY_DIR}/libtorrent EXCLUDE_FROM_ALL)

add_library(simpletorrent SHARED torrent_jni.cpp)

target_link_libraries(simpletorrent
    PRIVATE
        torrent-rasterbar
        android
        log
)
