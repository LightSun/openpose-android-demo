//
// Created by Administrator on 2019/7/1.
//

#ifndef COMMONLUAAPP_JAVA_ENV_H
#define COMMONLUAAPP_JAVA_ENV_H

#include <jni.h>

#ifdef __cplusplus
extern "C" {
#endif

jclass getGlobalClass(JNIEnv * env, const char* classname);

JNIEnv *getJNIEnv();

JNIEnv *attachJNIEnv();

JNIEnv *ensureJNIEnv();

void detachJNIEnv();

void dumpReferenceTables(JNIEnv *env);

const char * stringReplace(const char* str, const char* src, const char* dst);

#ifdef __cplusplus
}
#endif

#endif //COMMONLUAAPP_JAVA_ENV_H
