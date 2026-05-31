cmake_minimum_required(VERSION 4.0)
project(C C)

set(CMAKE_C_STANDARD 11)

include_directories(.)

add_executable(C App.c
        if (WIN32)
        # This tells CMake to link the dxgi library on Windows
        target_link_libraries (${PROJECT_NAME} dxgi)
        endif ()
)
