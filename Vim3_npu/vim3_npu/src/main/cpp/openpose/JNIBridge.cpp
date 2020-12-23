//
// Created by Administrator on 2020/12/18 0018.
//

#include <malloc.h>
#include "JNIBridge.h"
#include "and_log.h"
#include "android/bitmap.h"

float *bitmapToRgbArray(JNIEnv *env, jobject jbitmap, float *out) {

    AndroidBitmapInfo bmpInfo = {0};
    if (AndroidBitmap_getInfo(env, jbitmap, &bmpInfo) < 0) {
        return nullptr;
    }
    int* data = nullptr;
    if(AndroidBitmap_lockPixels(env, jbitmap,(void**)&data)){
        return nullptr;
    }
  /*  jfloat *pData = (jfloat *) env->GetDirectBufferAddress(buffer);
    jlong dwCapacity = env->GetDirectBufferCapacity(buffer);
    if (!pData) {
        LOGE("GetDirectBufferAddress() return null");
        return NULL;
    }*/
    //rgb . android is argb
    if(out == nullptr){
        out = static_cast<float *>(malloc(bmpInfo.width * bmpInfo.height * 3));
    }
    for (int i = 0, c = bmpInfo.width * bmpInfo.height; i < c; ++i) {
        out[i * 3] = data[i] << 16 & 0xff;
        out[i * 3 + 1] = data[i] << 8 & 0xff;
        out[i * 3 + 2] = data[i] & 0xff;
    }
    AndroidBitmap_unlockPixels(env, jbitmap);
    return out;
}