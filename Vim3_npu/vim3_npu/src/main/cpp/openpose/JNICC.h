//
// Created by Administrator on 2020/12/24 0024.
//

#ifndef VIM3APP_JNICC_H
#define VIM3APP_JNICC_H

#include "jni.h"

extern "C" JNIEXPORT jlong JNICALL
Java_com_heaven7_android_vim3_npu_NOpenposeOut_nCreate(JNIEnv *env, jclass clazz);

extern "C" JNIEXPORT void JNICALL
Java_com_heaven7_android_vim3_npu_NOpenposeOut_nFree(JNIEnv *env, jclass, jlong ptr);

extern "C" JNIEXPORT jint
Java_com_heaven7_android_vim3_npu_NOpenposeOut_nGetOutSize(JNIEnv
                                                           *env, jclass,
                                                           jlong ptr
);

extern "C" JNIEXPORT jfloat JNICALL
Java_com_heaven7_android_vim3_npu_NOpenposeOut_nGetOutX(JNIEnv *env, jclass, jlong ptr, jint index);

extern "C" JNIEXPORT jfloat JNICALL
Java_com_heaven7_android_vim3_npu_NOpenposeOut_nGetOutY(JNIEnv
                                                        *env, jclass,
                                                        jlong ptr, jint
                                                        index);

extern "C" JNIEXPORT jfloat JNICALL
Java_com_heaven7_android_vim3_npu_NOpenposeOut_nGetOutScore(JNIEnv
                                                            *env, jclass,
                                                            jlong ptr, jint
                                                            index);


extern "C"
JNIEXPORT jlong JNICALL
Java_com_heaven7_android_vim3_npu_NpuOpenpose_nInit(JNIEnv *env, jclass clazz, jstring nb_path,
                                                    jint w, jint h);
extern "C"
JNIEXPORT void JNICALL
Java_com_heaven7_android_vim3_npu_NpuOpenpose_nDestroy(JNIEnv *env, jclass clazz, jlong nn_ptr);
extern "C"
JNIEXPORT jboolean JNICALL
Java_com_heaven7_android_vim3_npu_NpuOpenpose_nInference(JNIEnv *env, jclass clazz, jlong nn_ptr,
                                                         jlong out_ptr, jobject bitmap);

#endif //VIM3APP_JNICC_H
