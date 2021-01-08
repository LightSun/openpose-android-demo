package com.heaven7.android.openpose_test;

import android.content.Context;

import com.bumptech.glide.GlideBuilder;
import com.bumptech.glide.load.engine.cache.DiskLruCacheFactory;
import com.bumptech.glide.module.AppGlideModule;
import com.bumptech.glide.module.GlideModule;

import java.io.File;

public class AppGlideModuleImpl extends AppGlideModule implements GlideModule {

    @Override
    public void applyOptions(Context context, GlideBuilder builder) {
        int diskCacheSizeBytes = 1024 * 1024 * 20;
        builder.setDiskCache(
                new DiskLruCacheFactory(new File(context.getCacheDir(), "glide_cache").getAbsolutePath(), diskCacheSizeBytes)
        );
    }

}
