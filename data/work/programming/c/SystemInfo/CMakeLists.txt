cmake_minimum_required(VERSION 4.0)
project(SystemInfo C)

set(CMAKE_C_STANDARD 23)

add_executable(SystemInfo library.c)
target_link_libraries(SystemInfo dxgi)