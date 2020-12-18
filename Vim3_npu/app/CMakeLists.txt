
project(TENSORFLOW_DEMO)
cmake_minimum_required(VERSION 3.4.1)

# openpose
set(LIB_DIR ${CMAKE_SOURCE_DIR}/src/main/jniLibs/${ANDROID_ABI})
include_directories(src/main/cpp/include/applib/ovxinc/include)
include_directories(src/main/cpp/include/service/ovx_inc)
aux_source_directory(src/main/cpp/openpose src_openpose)

add_library(ovxlib SHARED IMPORTED)
set_target_properties(ovxlib PROPERTIES IMPORTED_LOCATION ${LIB_DIR}/libovxlib.so)

add_library(jpeg SHARED IMPORTED)
set_target_properties(jpeg PROPERTIES IMPORTED_LOCATION ${LIB_DIR}/libjpeg.so)

add_library(openpose_npu SHARED ${src_openpose})
target_link_libraries(openpose_npu
     ovxlib
     jpeg
)