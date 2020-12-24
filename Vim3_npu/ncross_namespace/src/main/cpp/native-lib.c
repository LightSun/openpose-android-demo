#include "native-lib.h"

#include <jni.h>
#include <android/log.h>
#include <dlfcn.h>
#include <sys/user.h>
#include <fcntl.h>

#include "fake_linker.h"
#include "test_soinfo.h"


#define LOGD(...) __android_log_print(3, "beichen", __VA_ARGS__)
#define LOGE(...) __android_log_print(6, "beichen", __VA_ARGS__)

typedef void *(*__dlopen_impl)(const char *filename, int flag, void *address);

typedef void *(*__dlsym_impl)(void *__handle, const char *__symbol, void *address);

__dlopen_impl dlopen_impl = NULL;

__dlsym_impl dlsym_impl = NULL;

JNIEXPORT jint JNICALL
Java_com_beichen_fakelinker_MainActivity_hookDlopen(JNIEnv *env, jobject th) {
    char *lib;
    void *p_runtime_handle;
    void *p_vm;
    gaddress ld_debug;

#if defined(__LP64__)
    ld_debug = resolve_library_symbol_address("/system/bin/linker64", "__dl_g_ld_debug_verbosity",
                                              ST_INNER);
    lib = "/system/lib64/libandroid_runtime.so";
#else
    ld_debug = resolve_library_symbol_address("/system/bin/linker", "__dl_g_ld_debug_verbosity", ST_INNER);
    lib = "/system/lib/libandroid_runtime.so";
#endif
    *(int *) ld_debug = 2;

    p_runtime_handle = dlopen(lib, RTLD_LAZY);
    p_vm = NULL;

    if (p_runtime_handle != NULL) {
        p_vm = dlsym(p_runtime_handle, "_ZN7android14AndroidRuntime7mJavaVME");
    }
    LOGD("runtime: %p", p_runtime_handle);
    LOGD("mJavaVM: %p", p_vm);

    // 解析linker函数实现地址
    // ARM 是plt跳转地址,X86是实际地址,详情看方法内部
    dlopen_impl = resolve_inner_dlopen_or_dlsym(dlopen);
    dlsym_impl = resolve_inner_dlopen_or_dlsym(dlsym);


    LOGD("dlopen orig: %p, __dlopen_impl: %p", dlopen, dlopen_impl);
    LOGD("dlsym orig: %p, __dlsym_impl: %p", dlsym, dlsym_impl);

    p_runtime_handle = dlopen_impl(lib, RTLD_LAZY, open);
    if (p_runtime_handle != NULL) {
        p_vm = dlsym_impl(p_runtime_handle, "_ZN7android14AndroidRuntime7mJavaVME", open);
        LOGD("runtime: %p", p_runtime_handle);
        LOGD("mJavaVM: %p", p_vm);
    } else {
        LOGE("__dlopen_impl open android_runtime failed, possible decompilation failed");
    }
    gaddress linker_dlopen = resolve_library_symbol_address("libdl.so", "__loader_dlopen",
                                                            ST_IMPORTED);
    gaddress addr = *(gsize *) linker_dlopen;
    LOGD("find imp address: %llx, value: %llx", linker_dlopen, addr);
    return (int) GPOINTER_TO_SIZE(p_vm);
}

JNIEXPORT void JNICALL
Java_com_beichen_fakelinker_MainActivity_findThirdNamespace(JNIEnv *env, jobject th) {
    if (dlopen_impl != NULL) {
        void *third_so = dlopen_impl("/data/data/com.beichen.fakelinker/libnative-lib.so",
                                     RTLD_LAZY, open);
        void *third_sym = NULL;
        if (third_so != NULL) {
            third_sym = dlsym_impl(third_so, "Java_com_beichen_fakelinker_DynTestClass_specialLoad",
                                   open);
        }
        LOGD("dafault namespace find classloader namespace handle: %p, sym: %p", third_so,
             third_sym);
        third_so = dlopen_impl("libnative-lib.so", RTLD_LAZY, open);
        third_sym = NULL;
        if (third_so != NULL) {
            third_sym = dlsym_impl(third_so, "Java_com_beichen_fakelinker_DynTestClass_specialLoad",
                                   open);
        }
        LOGD("dafault namespace find classloader namespace 2 handle: %p, sym: %p", third_so,
             third_sym);
    }
}


JNIEXPORT void JNICALL
Java_com_beichen_fakelinker_MainActivity_testSolist(JNIEnv *env, jobject th) {
    gaddress solist;

#if defined(__LP64__)
    solist = resolve_library_symbol_address("/system/bin/linker64", "__dl__ZL6solist", ST_INNER);
#else
    solist = resolve_library_symbol_address("/system/bin/linker", "__dl__ZL6solist", ST_INNER);
#endif

    LOGD("solist address: %llx", solist);
    test_soinfo_struct(solist);
}

/*
 * 这个方法是在另一个命名空间内,跨命名空间注册的
 *
 * */
jint dyn_register(JNIEnv *env, jclass type) {
    LOGD("cross native namespace dynamic invoke function success");
    return 1;
}

static JNINativeMethod methods[] = {
        {"dynRegister", "()I", dyn_register}
};

JNIEXPORT jint JNICALL
Java_com_beichen_fakelinker_DynTestClass_specialLoad(JNIEnv *env, jclass type, jobject loader) {
    jclass java_lang_ClassLoader;
    jmethodID java_lang_ClassLoader_loadClass;
    jstring java_str_name;
    jclass java_MainActivity;

    java_lang_ClassLoader = (*env)->FindClass(env, "java/lang/ClassLoader");
    java_lang_ClassLoader_loadClass = (*env)->GetMethodID(env, java_lang_ClassLoader, "loadClass",
                                                          "(Ljava/lang/String;)Ljava/lang/Class;");

    java_str_name = (*env)->NewStringUTF(env, "com.beichen.fakelinker.MainActivity");

    java_MainActivity = (jclass) (*env)->CallObjectMethod(env, loader,
                                                          java_lang_ClassLoader_loadClass,
                                                          java_str_name);

    if (java_MainActivity == NULL) {
        LOGD("not found com.beichen.fakelinker.MainActivity class");
        return -1;
    }

    (*env)->RegisterNatives(env, java_MainActivity, methods, sizeof(methods) / sizeof(methods[0]));

    LOGD("dynamic register function success");
    (*env)->DeleteLocalRef(env, java_lang_ClassLoader);
    (*env)->DeleteLocalRef(env, java_str_name);
    (*env)->DeleteLocalRef(env, java_MainActivity);
    return 0;
}