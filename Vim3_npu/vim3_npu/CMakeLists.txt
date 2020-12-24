
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

add_library(cutils SHARED IMPORTED)
set_target_properties(cutils PROPERTIES IMPORTED_LOCATION ${LIB_DIR}/libcutils.so)

add_library(GAL SHARED IMPORTED)
set_target_properties(GAL PROPERTIES IMPORTED_LOCATION ${LIB_DIR}/libGAL.so)

add_library(NNArchPerf SHARED IMPORTED)
set_target_properties(NNArchPerf PROPERTIES IMPORTED_LOCATION ${LIB_DIR}/libNNArchPerf.so)

add_library(archmodelSw SHARED IMPORTED)
set_target_properties(archmodelSw PROPERTIES IMPORTED_LOCATION ${LIB_DIR}/libarchmodelSw.so)

add_library(OpenVX SHARED IMPORTED)
set_target_properties(OpenVX PROPERTIES IMPORTED_LOCATION ${LIB_DIR}/libOpenVX.so)

add_library(VSC SHARED IMPORTED)
set_target_properties(VSC PROPERTIES IMPORTED_LOCATION ${LIB_DIR}/libVSC.so)

add_library(openpose_npu SHARED ${src_openpose})
find_library( # Sets the name of the path variable.
        log-lib

        # Specifies the name of the NDK library that
        # you want CMake to locate.
        log)

target_link_libraries(openpose_npu
     jpeg_t
     cutils
     GAL
     NNArchPerf
     archmodelSw
     OpenVX
     VSC
     ovxlib
     jnigraphics
     ${log-lib}
)