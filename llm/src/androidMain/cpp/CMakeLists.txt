cmake_minimum_required(VERSION 3.22.1)
project(kmpai_llama LANGUAGES C CXX)

set(CMAKE_CXX_STANDARD 17)
set(CMAKE_CXX_STANDARD_REQUIRED ON)
set(CMAKE_POSITION_INDEPENDENT_CODE ON)

# llama.cpp build options - keep the build small and Android-friendly
set(LLAMA_BUILD_TESTS OFF CACHE BOOL "" FORCE)
set(LLAMA_BUILD_EXAMPLES OFF CACHE BOOL "" FORCE)
set(LLAMA_BUILD_SERVER OFF CACHE BOOL "" FORCE)
set(LLAMA_BUILD_TOOLS OFF CACHE BOOL "" FORCE)
set(LLAMA_CURL OFF CACHE BOOL "" FORCE)
set(GGML_OPENMP OFF CACHE BOOL "" FORCE)
set(GGML_LLAMAFILE OFF CACHE BOOL "" FORCE)

add_subdirectory(${CMAKE_CURRENT_SOURCE_DIR}/llama.cpp)

add_library(kmpai_llama SHARED llama_jni.cpp)

target_link_libraries(kmpai_llama
    llama
    ggml
    ggml-base
    ggml-cpu
    log
    android
)

target_compile_options(kmpai_llama PRIVATE
    -Wall
    -Wno-unused-function
    -fvisibility=hidden
)
