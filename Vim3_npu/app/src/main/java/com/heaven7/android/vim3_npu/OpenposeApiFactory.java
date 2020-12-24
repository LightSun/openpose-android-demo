package com.heaven7.android.vim3_npu;

import android.content.Context;

import com.heaven7.android.openpose.api.OpenposeApi;
import com.heaven7.android.vim3.npu.NpuOpenpose;

public final class OpenposeApiFactory {

    public static OpenposeApi newApi(Context context){
        //TODO npu
        //return new JavaPosenet(context);
        return new NpuOpenpose();
    }
}
//adb logcat | ndk-stack -sym armeabi-v7a >1.txt