cmake_minimum_required(VERSION 3.18.1)
project("runner")

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
