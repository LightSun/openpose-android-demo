//
// Created by Administrator on 2020/12/22 0022.
//

#ifndef VIM3APP_JNICC_CPP
#define VIM3APP_JNICC_CPP

#include "jni.h"
#include "OpenposeOut.h"

#define EC_JNIEXPORT extern "C" JNIEXPORT
#define SURFACE_VIEW_JAVA_PREFIX                        com_heaven7_android_vim3_npu
#define CONCAT(prefix, class, func)                     Java_ ## prefix ## _ ## class ## _ ## func
#define CONCAT_SURFACE(prefix, func)                    CONCAT(prefix, NOpenposeOut, func)

#define SURFACE_VIEW_JAVA_API(func)                 CONCAT_SURFACE(SURFACE_VIEW_JAVA_PREFIX, func)(JNIEnv* env, jclass)
#define SURFACE_VIEW_JAVA_API1(func, p1)            CONCAT_SURFACE(SURFACE_VIEW_JAVA_PREFIX, func)(JNIEnv* env, jclass, p1)
#define SURFACE_VIEW_JAVA_API2(func, p1, p2)        CONCAT_SURFACE(SURFACE_VIEW_JAVA_PREFIX, func)(JNIEnv* env, jclass, p1, p2)
//#define SURFACE_VIEW_JAVA_API3(func, p1, p2, p3)    CONCAT_SURFACE(SURFACE_VIEW_JAVA_PREFIX, func)(JNIEnv* env, jclass, p1, p2, p3)

EC_JNIEXPORT jlong JNICALL SURFACE_VIEW_JAVA_API(nAlloc) {
    auto ptr = new Npu::OpenposeOut();
    return reinterpret_cast<jlong>(ptr);
}

EC_JNIEXPORT void JNICALL SURFACE_VIEW_JAVA_API1(nFree, jlong ptr) {
    Npu::OpenposeOut* noo = reinterpret_cast<Npu::OpenposeOut *>(ptr);
    delete(noo);
}

EC_JNIEXPORT jint JNICALL SURFACE_VIEW_JAVA_API1(nGetOutSize, jlong ptr) {
    Npu::OpenposeOut* noo = reinterpret_cast<Npu::OpenposeOut *>(ptr);
    return noo->xCoords->size();
}

EC_JNIEXPORT jfloat JNICALL SURFACE_VIEW_JAVA_API2(nGetOutX, jlong ptr, jint index) {
    Npu::OpenposeOut* noo = reinterpret_cast<Npu::OpenposeOut *>(ptr);
    return noo->xCoords->get(index);
}

EC_JNIEXPORT jfloat JNICALL SURFACE_VIEW_JAVA_API2(nGetOutY, jlong ptr, jint index) {
    Npu::OpenposeOut* noo = reinterpret_cast<Npu::OpenposeOut *>(ptr);
    return noo->yCoords->get(index);
}

EC_JNIEXPORT jfloat JNICALL SURFACE_VIEW_JAVA_API2(nGetOutScore, jlong ptr, jint index) {
    Npu::OpenposeOut* noo = reinterpret_cast<Npu::OpenposeOut *>(ptr);
    return noo->confidenceScores->get(index);
}

#endif //VIM3APP_JNICC_CPP
