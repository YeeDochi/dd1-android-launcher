cmake_minimum_required(VERSION 3.22.1)
project(dd1android C)

add_library(dd1xserver SHARED
    xserver/xconnector_epoll.c
    xserver/xinput_stream.c
    xserver/xoutput_stream.c
    xserver/drawable.c
    xserver/sysvshared_memory.c
    xserver/arrays.c)
target_include_directories(dd1xserver PRIVATE xserver)
target_link_libraries(dd1xserver log android jnigraphics)

add_library(dd1runtime SHARED dd1_runtime.c)
target_link_libraries(dd1runtime log android EGL GLESv2)

if(ANDROID_ABI STREQUAL "arm64-v8a")
    set(NOGIT ON CACHE BOOL "" FORCE)
    set(ARM_DYNAREC ON CACHE BOOL "" FORCE)
    set(ANDROID ON CACHE BOOL "" FORCE)
    set(NO_LIB_INSTALL ON CACHE BOOL "" FORCE)
    add_subdirectory(${CMAKE_CURRENT_SOURCE_DIR}/../../../../runtime/box64 box64-build)
    set_target_properties(box64 PROPERTIES
        PREFIX "lib"
        SUFFIX ".so"
        OUTPUT_NAME "dd1_runner")
else()
    add_executable(dd1_runner dd1_runner_main.c)
    set_target_properties(dd1_runner PROPERTIES
        PREFIX "lib"
        SUFFIX ".so")
endif()
