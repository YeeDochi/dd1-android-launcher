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

if(ANDROID_ABI STREQUAL "x86_64")
    add_executable(dd1_runner dd1_runner_main.c)
    set_target_properties(dd1_runner PROPERTIES
        PREFIX "lib"
        SUFFIX ".so")
endif()
