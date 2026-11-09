cmake_minimum_required(VERSION 3.18.1)
project("runner")

set(CMAKE_CXX_STANDARD 17)
set(CMAKE_CXX_STANDARD_REQUIRED ON)

add_library(
        runner
        SHARED
        aimbot.cpp
)

target_include_directories(runner PRIVATE ${CMAKE_CURRENT_SOURCE_DIR})

find_library(log-lib log)
find_library(android-lib android)

target_link_libraries(runner
        ${log-lib}
        ${android-lib}
)
