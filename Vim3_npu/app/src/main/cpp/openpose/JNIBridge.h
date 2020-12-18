//
// Created by Administrator on 2020/12/18 0018.
//

#ifndef VIM3APP_JNIBRIDGE_H
#define VIM3APP_JNIBRIDGE_H

#include "jni.h"

#ifdef __cplusplus
extern "C" {
#endif

//float* rgbBufferToFloatArray(JNIEnv* env, jobject buffer);

float *bitmapToRgbArray(JNIEnv *env, jobject jbitmap, float *out);


#ifdef __cplusplus
}
#endif

#endif //VIM3APP_JNIBRIDGE_H
