cmake_minimum_required(VERSION 3.22.1)

project("piper_jni" LANGUAGES CXX C)

add_subdirectory(piper)

add_library(piper_jni SHARED jni_bridge.cpp)

target_link_libraries(piper_jni
    piper
    onnxruntime::onnxruntime
    log # Android logging library
)
