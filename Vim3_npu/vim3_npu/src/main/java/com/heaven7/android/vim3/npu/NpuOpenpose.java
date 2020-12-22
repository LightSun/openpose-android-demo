package com.heaven7.android.vim3.npu;

import android.graphics.Bitmap;

import com.heaven7.android.openpose.api.OpenposeApi;
import com.heaven7.android.openpose.api.bean.Recognition;

import java.util.List;

public class NpuOpenpose implements OpenposeApi {

    private final NOpenposeOut mOut = new NOpenposeOut();
    private long mNNApi;

    static {
        System.loadLibrary("openpose_npu");
        System.loadLibrary("jpeg");
        System.loadLibrary("ovxlib");
    }

    @Override
    public void initialize() {
        String nbPath;
        mNNApi = nInit(nbPath, 257 , 257);
    }
    @Override
    public void destroy() {
        if(mNNApi != 0){
            nDestroy(mNNApi);
            mNNApi = 0;
        }
    }
    @Override
    public List<Recognition> inference(Bitmap bitmap) {

        return null;
    }

    private static native long nInit(String nbPath, int w, int h);
    private static native void nDestroy(long nnPtr);
}
