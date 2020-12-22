package com.heaven7.android.openpose.api;

import android.graphics.Bitmap;

import com.heaven7.android.openpose.api.bean.Recognition;

import java.util.List;

public interface OpenposeApi {

    void initialize();
    void destroy();
    List<Recognition> inference(Bitmap bitmap);
}
