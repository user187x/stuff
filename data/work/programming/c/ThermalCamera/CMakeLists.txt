cmake_minimum_required(VERSION 3.10)
project(XThermBridge CXX)

set(CMAKE_CXX_STANDARD 11)
set(CMAKE_POSITION_INDEPENDENT_CODE ON)

include_directories(${CMAKE_CURRENT_SOURCE_DIR})

# Create the shared library for JNA using only our consolidated bridge
add_library(ir_bridge SHARED ir_bridge.cpp)

# Link against the provided x86_64 Ubuntu PC binaries
target_link_libraries(ir_bridge
    ${CMAKE_CURRENT_SOURCE_DIR}/lib/libthermometry.so
    ${CMAKE_CURRENT_SOURCE_DIR}/lib/libSimple.so
    m
)
