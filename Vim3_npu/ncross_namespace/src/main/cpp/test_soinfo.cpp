//
// Created by beich on 2019/4/16.
#include "test_soinfo.h"

#include <android/log.h>
#include <cstdio>
#include <dlfcn.h>

#include "soinfo.h"
#include "fake_linker.h"

#define LOGD(...) __android_log_print(ANDROID_LOG_DEBUG, "beichen", __VA_ARGS__)
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, "beichen", __VA_ARGS__)

template<typename dst_type, typename src_type>
dst_type pointer_cast(src_type src) {
    return *static_cast<dst_type *>(static_cast<void *>(&src));
}

void visit(soinfo9 * info) {
    LOGD("current namespace %s, has so: %s", info->primary_namespace_->name_, info->soname_);
}

int test_soinfo_struct(gaddress solist) {
    // 对应方法 find_containing_library, soinfo::soinfo, get_soname, get_primary_namespace,to_handle
    // arm64 base: 16, next: 40, version_: 268, soname_: 408, primary_namespace_: 512, handle_: 536
    // arm base: 140, next: 164, version_: 292, soname_: 376, primary_namespace_: 428, handle_: 440
    // x86 base: 140, next: 164, version_: 284, soname_: 368, primary_namespace_: 420, handle_: 432
    // x64 base: 16, next: 40, version_: 268, soname_: 408, primary_namespace_: 512, handle_: 536
    LOGD("base: %d, next: %d, version_: %d, soname_: %d, primary_namespace_: %d, handle_: %d",
         &soinfo9::base, &soinfo9::next, &soinfo9::version_, &soinfo9::soname_,
         &soinfo9::primary_namespace_, &soinfo9::handle_);

    // 查看源码该地址是 soinfo * 指针值
    soinfo9 * si = *(soinfo9 **) GSIZE_TO_POINTER(solist);

    do {
        if (strcmp("classloader-namespace", si->primary_namespace_->name_) == 0) {
            LOGD("soname: %s, namespace: %s, realpath: %s, isolated: %d, greylist: %d", si->soname_,
                 si->primary_namespace_->name_, si->realpath_.c_str(),
                 si->primary_namespace_->is_isolated_,
                 si->primary_namespace_->is_greylist_enabled_);
            for (const auto&iter : si->primary_namespace_->ld_library_paths_) {
                LOGD("namespace: %s, ld path: %s", si->primary_namespace_->name_, iter.c_str());
            }
            for (const auto&iter : si->primary_namespace_->default_library_paths_) {
                LOGD("namespace: %s, default path: %s", si->primary_namespace_->name_,
                     iter.c_str());
            }
            for (const auto&iter : si->primary_namespace_->permitted_paths_) {
                LOGD("namespace: %s, permitted path: %s", si->primary_namespace_->name_,
                     iter.c_str());
            }
            for (int i = 0; i < si->primary_namespace_->linked_namespaces_.size(); ++i) {
                android_namespace_link_t * link = &si->primary_namespace_->linked_namespaces_[i];
                LOGD("soname: %s, link namespace: %s, link allow_all_shared_libs: %d link isolated: %d, link greylist: %d",
                     si->soname_, link->linked_namespace_->name_, link->allow_all_shared_libs_,
                     link->linked_namespace_->is_isolated_,
                     link->linked_namespace_->is_greylist_enabled_);
                for (const auto&iter : link->linked_namespace_->ld_library_paths_) {
                    LOGD("namespace: %s, ld path: %s", link->linked_namespace_->name_,
                         iter.c_str());
                }
                for (const auto&iter : link->linked_namespace_->default_library_paths_) {
                    LOGD("namespace: %s, default path: %s", link->linked_namespace_->name_,
                         iter.c_str());
                }
                for (const auto&iter : link->linked_namespace_->permitted_paths_) {
                    LOGD("namespace: %s, permitted path: %s", link->linked_namespace_->name_,
                         iter.c_str());
                }
                LOGD("namespace: %p, isolated: %p", si->primary_namespace_, &si->primary_namespace_->is_isolated_);
//                si->primary_namespace_->is_isolated_ = true;
            }
        }
    } while ((si = si->next) != nullptr);
    void * handle = dlopen("/system/lib64/libandroid_runtime.so", RTLD_LAZY);
    void * symbol = nullptr;
    if (handle != nullptr) {
        symbol = dlsym(handle, "_ZN7android14AndroidRuntime7mJavaVME");
    }
    LOGE("find handler: %p, symbol: %p", handle, symbol);
    return 1;
}


//

