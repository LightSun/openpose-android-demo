//
// Created by Administrator on 2020/12/22 0022.
//

#include "jni.h"
#include "JNICC.h"
#include "OpenposeOut.h"
#include "NNHApi.h"
#include "java_env.h"

#define EC_JNIEXPORT extern "C" JNIEXPORT
#define SURFACE_VIEW_JAVA_PREFIX                        com_heaven7_android_vim3_npu
#define CONCAT(prefix, c, func)                         Java_ ## prefix ## _ ## c ## _ ## func
#define CONCAT_SURFACE(prefix, func)                    CONCAT(prefix, NOpenposeOut, func)

#define SURFACE_VIEW_JAVA_API(func)                 CONCAT_SURFACE(SURFACE_VIEW_JAVA_PREFIX, func)(JNIEnv* env, jclass)
#define SURFACE_VIEW_JAVA_API1(func, p1)            CONCAT_SURFACE(SURFACE_VIEW_JAVA_PREFIX, func)(JNIEnv* env, jclass, p1)
#define SURFACE_VIEW_JAVA_API2(func, p1, p2)        CONCAT_SURFACE(SURFACE_VIEW_JAVA_PREFIX, func)(JNIEnv* env, jclass, p1, p2)
//#define SURFACE_VIEW_JAVA_API3(func, p1, p2, p3)    CONCAT_SURFACE(SURFACE_VIEW_JAVA_PREFIX, func)(JNIEnv* env, jclass, p1, p2, p3)

extern "C" JNIEXPORT jlong JNICALL
Java_com_heaven7_android_vim3_npu_NOpenposeOut_nCreate(JNIEnv *env, jclass clazz) {
    auto ptr = new Npu::OpenposeOut();
    return reinterpret_cast<jlong>(ptr);
}

extern "C" JNIEXPORT void JNICALL
Java_com_heaven7_android_vim3_npu_NOpenposeOut_nFree(JNIEnv *env, jclass , jlong ptr) {
    Npu::OpenposeOut* noo = reinterpret_cast<Npu::OpenposeOut *>(ptr);
    delete(noo);
}

extern "C" JNIEXPORT jint
Java_com_heaven7_android_vim3_npu_NOpenposeOut_nGetOutSize(JNIEnv
                                                           *env,jclass,
                                                           jlong ptr
) {
    Npu::OpenposeOut* noo = reinterpret_cast<Npu::OpenposeOut *>(ptr);
    return noo->xCoords->size();
}

extern "C" JNIEXPORT jfloat JNICALL
Java_com_heaven7_android_vim3_npu_NOpenposeOut_nGetOutX(JNIEnv *env, jclass, jlong ptr, jint index) {
    Npu::OpenposeOut* noo = reinterpret_cast<Npu::OpenposeOut *>(ptr);
    return noo->xCoords->get(index);
}

extern "C" JNIEXPORT jfloat JNICALL
Java_com_heaven7_android_vim3_npu_NOpenposeOut_nGetOutY(JNIEnv
                                                        *env,jclass,
                                                        jlong ptr, jint
                                                        index) {
    Npu::OpenposeOut* noo = reinterpret_cast<Npu::OpenposeOut *>(ptr);
    return noo->yCoords->get(index);
}

extern "C" JNIEXPORT jfloat JNICALL
Java_com_heaven7_android_vim3_npu_NOpenposeOut_nGetOutScore(JNIEnv
                                                            *env,jclass,
                                                            jlong ptr, jint
                                                            index) {
    Npu::OpenposeOut* noo = reinterpret_cast<Npu::OpenposeOut *>(ptr);
    return noo->confidenceScores->get(index);
}


extern "C"
JNIEXPORT jlong JNICALL
Java_com_heaven7_android_vim3_npu_NpuOpenpose_nInit(JNIEnv *env, jclass clazz, jstring nb_path,
                                                    jint w, jint h) {
    auto path = env->GetStringUTFChars(nb_path, nullptr);
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
JNIEXPORT void JNICALL
Java_com_heaven7_android_vim3_npu_NpuOpenpose_nDetachJniEnv(JNIEnv *env, jclass clazz) {
    detachJNIEnv();
}
extern "C"
JNIEXPORT jboolean JNICALL
Java_com_heaven7_android_vim3_npu_NpuOpenpose_nInference(JNIEnv *env, jclass clazz, jlong nn_ptr,
                                                         jlong out_ptr, jobject bitmap) {
    Npu::NNHApi* api = reinterpret_cast<Npu::NNHApi *>(nn_ptr);
    Npu::OpenposeOut* out = reinterpret_cast<Npu::OpenposeOut *>(out_ptr);
    return static_cast<jboolean>(api->inference(bitmap, *out));
}extern "C"
JNIEXPORT void JNICALL
Java_com_heaven7_android_vim3_npu_NpuOpenpose_nReleaseGraph(JNIEnv *env, jclass clazz, jlong nn_ptr) {
    Npu::NNHApi* api = reinterpret_cast<Npu::NNHApi *>(nn_ptr);
    api->releaseGraph();
}