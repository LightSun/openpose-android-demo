package com.heaven7.android.openpose_test;

import android.content.Context;

import com.heaven7.android.openpose.api.OpenposeApi;
import com.heaven7.openpose.openpose.JavaPosenet;

public final class OpenposeApiFactory {

    public static OpenposeApi newApi(Context context){
        //TODO npu
        return new JavaPosenet(context);
    }
}
