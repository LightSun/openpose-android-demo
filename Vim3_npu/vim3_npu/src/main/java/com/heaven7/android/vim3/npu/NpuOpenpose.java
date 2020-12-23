package com.heaven7.android.vim3.npu;

import android.content.Context;
import android.graphics.Bitmap;

import com.heaven7.android.openpose.api.Common;
import com.heaven7.android.openpose.api.OpenposeApi;
import com.heaven7.android.openpose.api.bean.Coord;
import com.heaven7.android.openpose.api.bean.Human;
import com.heaven7.android.openpose.api.bean.Recognition;
import com.heaven7.android.openpose.api.utils.AssetsUtils;
import com.heaven7.core.util.Logger;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
//TODO test
public class NpuOpenpose implements OpenposeApi {

    private static final String TAG = "NpuOpenpose";
    private static final String SAVE_DIR = "heaven7/vim3_npu";
    private static final String NB_NAME = "network_binary.nb";
    private final NOpenposeOut mOut = new NOpenposeOut();
    private long mNNApi;

    static {
        System.loadLibrary("openpose_npu");
        System.loadLibrary("jpeg");
        System.loadLibrary("ovxlib");
    }

    @Override
    public void prepare(Context context) {
        AssetsUtils.copy(context, NB_NAME, SAVE_DIR + "/" + NB_NAME, true);
    }
    @Override
    public void initialize(Context context) {
        if(mNNApi == 0){
            mNNApi = nInit(SAVE_DIR + "/" + NB_NAME, 257 , 257);
        }
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
        boolean result = nInference(mNNApi, mOut.getPtr(), bitmap);
        if(!result){
            Logger.e(TAG, "inference failed");
            return Collections.emptyList();
        }
        Human hu = new Human();
        Map<Integer, Coord> map = hu.parts;
        float totalScore = 0.0f;
        for (int idx = 0; idx < BodyPart.VALUES.length ; idx ++){
            byte it = BodyPart.VALUES[idx];
            map.put(castIdx(it), new Coord(mOut.getOutX(idx), mOut.getOutY(idx), mOut.getOutScore(idx), 1));
            totalScore += mOut.getOutScore(idx);
        }

        //add neck coord
        Coord left_shoulder = map.get(Common.CocoPart.LShoulder.index);
        Coord right_shoulder = map.get(Common.CocoPart.RShoulder.index);
        if (left_shoulder == null) {
            left_shoulder = new Coord(0f, 0f, 0f, 0);
        }
        if (right_shoulder == null) {
            right_shoulder = new Coord(0f, 0f, 0f, 0);
        }
        map.put(Common.CocoPart.Neck.index, new Coord(
                (left_shoulder.x + right_shoulder.x) / 2,
                (left_shoulder.y + right_shoulder.y) / 2,
                (left_shoulder.score + right_shoulder.score) / 2,
                (left_shoulder.count + right_shoulder.count) / 2
        ));
        //build result
        Recognition r0 = new Recognition("a", totalScore / mOut.getOutSize());
        r0.humans = new ArrayList<Human>();
        r0.humans.add(hu);
        ArrayList list = new ArrayList<Recognition>();
        list.add(r0);
        return list;
    }

    private static int castIdx(byte part){
        switch (part) {
            case BodyPart.NOSE : return Common.CocoPart.Nose.index;
            case BodyPart.LEFT_EYE : return Common.CocoPart.LEye.index;
            case BodyPart.RIGHT_EYE : return Common.CocoPart.REye.index;
            case BodyPart.LEFT_EAR : return Common.CocoPart.LEar.index;
            case BodyPart.RIGHT_EAR : return Common.CocoPart.REar.index;
            case BodyPart.LEFT_SHOULDER : return Common.CocoPart.LShoulder.index;
            case BodyPart.RIGHT_SHOULDER : return Common.CocoPart.RShoulder.index;
            case BodyPart.LEFT_ELBOW : return Common.CocoPart.LElbow.index;
            case BodyPart.RIGHT_ELBOW : return Common.CocoPart.RElbow.index;
            case BodyPart.LEFT_WRIST : return Common.CocoPart.LWrist.index;
            case BodyPart.RIGHT_WRIST : return Common.CocoPart.RWrist.index;
            case BodyPart.LEFT_HIP : return Common.CocoPart.LHip.index;
            case BodyPart.RIGHT_HIP : return Common.CocoPart.RHip.index;
            case BodyPart.LEFT_KNEE : return Common.CocoPart.LKnee.index;
            case BodyPart.RIGHT_KNEE : return Common.CocoPart.RKnee.index;
            case BodyPart.LEFT_ANKLE : return Common.CocoPart.LAnkle.index;
            case BodyPart.RIGHT_ANKLE : return Common.CocoPart.RAnkle.index;

            default:
                Logger.w(TAG, "castIdx", "wrong body id = " + part);
        }
        return Common.CocoPart.Nose.index;
    }

    private static native long nInit(String nbPath, int w, int h);
    private static native void nDestroy(long nnPtr);
    private static native boolean nInference(long nnPtr, long outPtr, Bitmap bitmap);
}
