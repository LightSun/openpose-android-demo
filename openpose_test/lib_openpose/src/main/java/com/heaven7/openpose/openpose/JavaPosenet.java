package com.heaven7.openpose.openpose;

import android.content.Context;
import android.graphics.Bitmap;

import com.heaven7.android.openpose.api.OpenposeApi;
import com.heaven7.android.openpose.api.bean.Recognition;

import java.util.List;

public class JavaPosenet implements OpenposeApi {

    private Posenet posenet;

    public JavaPosenet(Context context) {
        if(context != null){
            initialize(context);
        }
    }

    @Override
    public void prepare(Context context) {
    }

    @Override
    public void detachJniEnv() {

    }
    @Override
    public void releaseGraph() {

    }

    @Override
    public void initialize(Context context) {
        if(posenet == null){
            posenet = new Posenet(context, "posenet_model.tflite", Device.CPU);
        }
    }
    @Override
    public void destroy() {
        if(posenet != null){
            posenet.close();
            posenet = null;
        }
    }
    @Override
    public List<Recognition> inference(Bitmap bitmap) {
        return posenet.estimateSinglePose(bitmap);
    }
}
