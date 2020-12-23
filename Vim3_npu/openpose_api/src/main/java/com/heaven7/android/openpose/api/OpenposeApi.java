package com.heaven7.android.openpose.api;

import android.content.Context;
import android.graphics.Bitmap;

import com.heaven7.android.openpose.api.bean.Recognition;

import java.util.List;

public interface OpenposeApi {

    //often called async
    void prepare(Context context);

    void initialize();
    void destroy();
    List<Recognition> inference(Bitmap bitmap);
}
