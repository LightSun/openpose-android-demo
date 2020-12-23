
project(TENSORFLOW_DEMO)
cmake_minimum_required(VERSION 3.4.1)

# openpose
set(LIB_DIR ${CMAKE_SOURCE_DIR}/src/main/jniLibs/${ANDROID_ABI})
include_directories(src/main/cpp/include/applib/ovxinc/include)
include_directories(src/main/cpp/include/service/ovx_inc)
aux_source_directory(src/main/cpp/openpose src_openpose)

add_library(ovxlib SHARED IMPORTED)
set_target_properties(ovxlib PROPERTIES IMPORTED_LOCATION ${LIB_DIR}/libovxlib.so)

add_library(jpeg_t SHARED IMPORTED)
set_target_properties(jpeg_t PROPERTIES IMPORTED_LOCATION ${LIB_DIR}/libjpeg_t.so)

add_library(openpose_npu SHARED ${src_openpose})
find_library( # Sets the name of the path variable.
        log-lib

        # Specifies the name of the NDK library that
        # you want CMake to locate.
        log)

target_link_libraries(openpose_npu
     ovxlib
     jpeg_t
     jnigraphics
     ${log-lib}
)