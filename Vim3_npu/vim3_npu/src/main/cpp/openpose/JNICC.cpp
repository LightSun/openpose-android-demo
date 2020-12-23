//
// Created by Administrator on 2020/12/22 0022.
//

#include "jni.h"
#include "OpenposeOut.h"
#include "NNHApi.h"

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


extern "C"
JNIEXPORT jlong JNICALL
Java_com_heaven7_android_vim3_npu_NpuOpenpose_nInit(JNIEnv *env, jclass clazz, jstring nb_path,
                                                    jint w, jint h) {
    auto path = env->GetStringUTFChars(nb_path, false);
    auto pApi = new Npu::NNHApi(path, w, h);
    env->ReleaseStringUTFChars(nb_path, path);
    return reinterpret_cast<jlong>(pApi);
}
extern "C"
JNIEXPORT void JNICALL
Java_com_heaven7_android_vim3_npu_NpuOpenpose_nDestroy(JNIEnv *env, jclass clazz, jlong nn_ptr) {

    Npu::NNHApi* api = reinterpret_cast<Npu::NNHApi *>(nn_ptr);
    delete api;
}
extern "C"
JNIEXPORT jboolean JNICALL
Java_com_heaven7_android_vim3_npu_NpuOpenpose_nInference(JNIEnv *env, jclass clazz, jlong nn_ptr,
                                                         jlong out_ptr, jobject bitmap) {
    Npu::NNHApi* api = reinterpret_cast<Npu::NNHApi *>(nn_ptr);
    Npu::OpenposeOut* out = reinterpret_cast<Npu::OpenposeOut *>(out_ptr);
    return static_cast<jboolean>(api->inference(bitmap, *out));
}