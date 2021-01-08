package com.heaven7.android.openpose_test;

import android.app.Activity;
import android.content.Context;
import android.widget.ImageView;

import androidx.lifecycle.LifecycleOwner;

import com.bumptech.glide.Glide;
import com.bumptech.glide.RequestBuilder;
import com.bumptech.glide.load.Transformation;
import com.bumptech.glide.load.engine.DiskCacheStrategy;
import com.bumptech.glide.load.resource.bitmap.CenterCrop;
import com.heaven7.android.imagepick.pub.IImageItem;
import com.heaven7.android.imagepick.pub.ImageLoadDelegate;
import com.heaven7.android.imagepick.pub.ImageOptions;
import com.heaven7.core.util.Logger;

import java.io.File;

public class SimpleImageLoadDelegate implements ImageLoadDelegate {

    private static final String TAG = "SimpleImageLoadDelegate";

    @Override
    public void loadImage(LifecycleOwner owner, ImageView iv, IImageItem item, ImageOptions options) {
        Context context = iv.getContext();
        if(item.isGif()){
            Logger.e(TAG, "loadImage", "gif not support now.");
            return;
        }
        if(options != null){
            RequestBuilder rb;
            if(options.getBorder() > 0 && options.getRound() > 0){
                BorderRoundTransformation borderTrans = new BorderRoundTransformation(context, options.getRound(),
                        0, options.getBorder(), options.getBorderColor());
                rb = (RequestBuilder) getRequestBuilder(context, item)
                        .transform(new Transformation[]{
                                new CenterCrop(),
                                borderTrans})
                        .dontAnimate();
            }else {
                rb = (RequestBuilder) getRequestBuilder(context, item)
                        .transform(new CenterCrop())
                        .dontAnimate();
            }
            if(options.getTargetWidth() > 0 && options.getTargetHeight() > 0){
                rb = (RequestBuilder) rb.override(options.getTargetWidth(), options.getTargetHeight());
            }
            if(options.getCacheFlags() == ImageOptions.CACHE_FLAG_DATA){
                rb = (RequestBuilder) rb.diskCacheStrategy(DiskCacheStrategy.DATA);
            }else if(options.getCacheFlags() == ImageOptions.CACHE_FLAG_RESOURCE){
                rb = (RequestBuilder) rb.diskCacheStrategy(DiskCacheStrategy.RESOURCE);
            }else if(options.getCacheFlags() == ImageOptions.CACHE_FLAG_DATA + ImageOptions.CACHE_FLAG_RESOURCE){
                rb = (RequestBuilder) rb.diskCacheStrategy(DiskCacheStrategy.ALL);
            }
            rb.into(iv);
        }else {
            RequestBuilder rb = (RequestBuilder) getRequestBuilder(context, item)
                    .centerCrop()
                    .diskCacheStrategy(DiskCacheStrategy.AUTOMATIC);
            rb.into(iv);
        }
    }

    private RequestBuilder getRequestBuilder(Context context, IImageItem item) {
        if(item.getFilePath() != null){
            return Glide.with(context).load(new File(item.getFilePath()));
        }
        return Glide.with(context).load(item.getUrl());
    }

    @Override
    public void pauseRequests(Activity activity) {
        Glide.with(activity).pauseRequests();
    }

    @Override
    public void resumeRequests(Activity activity) {
        if(activity.isFinishing() || activity.isDestroyed()){
            return;
        }
        Glide.with(activity).resumeRequests();
    }
}
