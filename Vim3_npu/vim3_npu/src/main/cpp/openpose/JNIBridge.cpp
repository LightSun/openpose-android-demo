//
// Created by Administrator on 2020/12/18 0018.
//

#include <malloc.h>
#include "JNIBridge.h"
#include "and_log.h"
#include "android/bitmap.h"

float *bitmapToRgbArray(JNIEnv *env, jobject jbitmap, float *out) {

    AndroidBitmapInfo bmpInfo;
    if (AndroidBitmap_getInfo(env, jbitmap, &bmpInfo) < 0) {
        return nullptr;
    }
    int* data = nullptr;
    if(AndroidBitmap_lockPixels(env, jbitmap,(void**)&data)){
        return nullptr;
    }
    LOGD("bitmap (w, h = %d, %d)", bmpInfo.width, bmpInfo.height);
    /*  jfloat *pData = (jfloat *) env->GetDirectBufferAddress(buffer);
    jlong dwCapacity = env->GetDirectBufferCapacity(buffer);
    if (!pData) {
        LOGE("GetDirectBufferAddress() return null");
        return NULL;
    }*/
    //rgb . android is argb
    if(out == nullptr){
        out = static_cast<float *>(malloc(sizeof(float) * bmpInfo.width * bmpInfo.height * 3));
    }
    float mean = 128.0f;
    float std = 128.0f;
    for (int i = 0, c = bmpInfo.width * bmpInfo.height; i < c; ++i) {
        out[i * 3] =   ((data[i] >> 16 & 0xff) - mean) / std;
        out[i * 3 + 1] = ((data[i] >> 8 & 0xff) - mean ) / std;
        out[i * 3 + 2] = ((data[i] & 0xff ) - mean) / std;
    }
    AndroidBitmap_unlockPixels(env, jbitmap);
    return out;
}