//
// Created by Administrator on 2020/7/7 0007.
//

#ifndef FFMPEGOPENGLDEMO_LOG_H
#define FFMPEGOPENGLDEMO_LOG_H

#ifdef __cplusplus
extern "C" {
#endif

#define LOG_TAG "vim_npu"

#ifdef ANDROID
    #include "android/log.h"
    #define LOGV(...) __android_log_print(ANDROID_LOG_VERBOSE, LOG_TAG, __VA_ARGS__)
    #define LOGD(...) __android_log_print(ANDROID_LOG_DEBUG, LOG_TAG, __VA_ARGS__)
    #define LOGI(...) __android_log_print(ANDROID_LOG_INFO, LOG_TAG, __VA_ARGS__)
    #define LOGW(...) __android_log_print(ANDROID_LOG_WARN, LOG_TAG, __VA_ARGS__)
    #define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, LOG_TAG, __VA_ARGS__)
#undef printf
#define printf(...) LOGD(__VA_ARGS__)
#else
    //empty. later will dosomething
    #define LOGV(...) static_cast<void>(0)
    #define LOGD(...) static_cast<void>(0)
    #define LOGI(...) static_cast<void>(0)
    #define LOGW(...) static_cast<void>(0)
    #define LOGE(...) static_cast<void>(0)
#endif


#ifdef __cplusplus
}
#endif

#endif //FFMPEGOPENGLDEMO_LOG_H
