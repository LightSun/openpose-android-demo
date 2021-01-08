package com.heaven7.android.openpose_test;

import com.heaven7.android.openpose.api.Common;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

class Pair {
    public Pair(float[] pt1, float[] pt2) {
        this.pt1 = pt1;
        this.pt2 = pt2;
    }

    public float distance() {
        return (float) Math.sqrt(Math.pow(pt1[0] - pt2[0], 2) + Math.pow(pt1[1] - pt2[1], 2));
    }

    public float[] normalize() {
        float dist = distance();
        return new float[]{
                (pt2[0] - pt1[0]) / (dist + 0.001f/*eps*/),
                (pt2[1] - pt1[1]) / (dist + 0.001f/*eps*/)
        };
    }

    public boolean checkPt1() {
        assert pt1.length == 2;
        return pt1[0] != 0.0f && pt1[1] != 0.0f;
    }

    public boolean checkPt2() {
        assert pt2.length == 2;
        return pt2[0] != 0.0f && pt2[1] != 0.0f;
    }

    public boolean check() {
        return checkPt1() && checkPt2();
    }

    public float[] pt1;
    public float[] pt2;
}

class PoseDiff {
    public PoseDiff() {
        //关节id, 连接的关节。
        KP_TREE = new HashMap<>();
        KP_TREE.put(1, new Integer[]{5, 2, 0});
        KP_TREE.put(2, new Integer[]{3});
        KP_TREE.put(3, new Integer[]{4});
        KP_TREE.put(5, new Integer[]{6});
        KP_TREE.put(6, new Integer[]{7});
        KP_TREE.put(8, new Integer[]{12, 9, 1});
        KP_TREE.put(9, new Integer[]{10});
        KP_TREE.put(10, new Integer[]{11});
        KP_TREE.put(12, new Integer[]{13});
        KP_TREE.put(13, new Integer[]{14});

        // JOINT = ['nose',
        //          'neck',
        //          'right shoulder',
        //          'right elbow',
        //          'right wrist',
        //          'left shoulder',
        //          'left elbow',
        //          'left wrist',
        //          'waist',
        //          'right hip',
        //          'right knee',
        //          'right ankle',
        //          'left hip',
        //          'left knee',
        //          'left ankle']
    }

    float[][] align(float[][] pose1,
                    float[][] pose2) {
        float[] waist_1 = {pose1[8][0], pose1[8][1]};
        float[] neck_1  = {pose1[1][0], pose1[1][1]};
        Pair pose1_waist_neck = new Pair(waist_1, neck_1);
        float[] waist_2 = {pose2[8][0], pose2[8][1]};
        float[] neck_2  = {pose2[1][0], pose2[1][1]};
        Pair pose2_waist_neck = new Pair(waist_2, neck_2);

        assert pose1_waist_neck.distance() > 20;
        assert pose2_waist_neck.distance() > 20;

        float scale_ratio = pose2_waist_neck.distance() / (pose1_waist_neck.distance() + 0.0001f/*eps*/);
        float shift_factor_y = pose2_waist_neck.pt1[0] - pose1_waist_neck.pt1[0] * scale_ratio;
        float shift_factor_x = pose2_waist_neck.pt1[1] - pose1_waist_neck.pt1[1] * scale_ratio;

        float[][] result = pose1;
        for (int i = 0; i < pose1.length; ++i) {
            result[i][0] = result[i][0] * scale_ratio + shift_factor_y;
            result[i][1] = result[i][1] * scale_ratio + shift_factor_x;
        }

        return result;
    }

    float matchLimb(Pair limb1, Pair limb2) {
        if (!limb1.checkPt2()) return 1.0f;
        if (!limb2.checkPt2()) return 0.0f;

        float[] limb1_vec = limb1.normalize();
        float   limb1_len = limb1.distance();
        float[] limb2_vec = limb2.normalize();
        float   limb2_len = limb2.distance();

        float dir_score = Math.max(limb1_vec[0] * limb2_vec[0] + limb1_vec[1] * limb2_vec[1], 0);
        float len_score = Math.min(limb1_len / (limb2_len + 0.0001f/*eps*/), limb2_len / (limb1_len + 0.0001f/*eps*/));

        return dir_score * len_score;
    }

    float[] diff(float[][] pose1, float[][] pose2) {
        float[] result = new float[15];

        pose1 = align(pose1, pose2);

        result[8] = 1.0f;
        // starting from 8 (waist)
        match(pose1, pose2, 8, result);

        return result;
    }

    void match(float[][] pose1, float[][] pose2, int KP_idx, float[] result) {
        if (!KP_TREE.containsKey(KP_idx)) return;

        for (Integer KP_end : KP_TREE.get(KP_idx)) {
            Pair limb1 = new Pair(
                    new float[]{pose1[KP_idx][0], pose1[KP_idx][1]},
                    new float[]{pose1[KP_end][0], pose1[KP_end][1]});
            Pair limb2 = new Pair(
                    new float[]{pose2[KP_idx][0], pose2[KP_idx][1]},
                    new float[]{pose2[KP_end][0], pose2[KP_end][1]});
            float score = matchLimb(limb1, limb2);
            result[KP_end] = score * result[KP_idx];

            // recurse to next level of joint
            match(pose1, pose2, KP_end, result);
        }

    }

    public Map<Integer, Integer[]> KP_TREE;
}

public final class OpenposeDiffUtils {

    public static void main(String[] args) {
        //body 25 ?
        //横纵坐标 + 置信度
        float [][] pose1 = {
                { 61.454258f  ,  53.41982f   ,   0.93215847f},
                {104.49912f   ,  56.079205f  ,   0.78159636f},
                { 83.86863f   ,  61.44554f   ,   0.7020095f },
                {103.60957f   ,  89.23664f   ,   0.35986856f},
                { 87.43795f   , 104.478355f  ,   0.61353266f},
                {125.99088f   ,  51.606964f  ,   0.6937203f },
                {177.08769f   ,  80.26664f   ,   0.78802425f},
                {136.75899f   , 105.37853f   ,   0.77055633f},
                {144.8303f    , 137.65736f   ,   0.6575804f },
                {126.00983f   , 140.32758f   ,   0.6885707f },
                { 88.363335f  , 194.12444f   ,   0.7789235f },
                { 80.27588f   , 276.59695f   ,   0.7344706f },
                {161.8926f    , 134.9846f    ,   0.62454927f},
                {126.0445f    , 197.75565f   ,   0.84280133f},
                {126.01337f   , 291.85532f   ,   0.7197512f },
                { 56.0834f    ,  47.136307f  ,   0.34755847f},
                { 62.385002f  ,  45.28699f   ,   0.853442f  },
                {  0.f        ,   0.f        ,   0.f        },
                { 82.03646f   ,  31.859026f  ,   0.9252022f },
                { 94.63671f   , 307.99786f   ,   0.63879377f},
                {102.67512f   , 310.6538f    ,   0.62901235f},
                {133.18594f   , 299.93033f   ,   0.6322739f },
                { 59.655987f  , 287.37305f   ,   0.4449718f },
                { 59.667976f  , 282.88013f   ,   0.5353729f },
                { 83.88847f   , 282.91095f   ,   0.7038615f }}
                ;
        float [][] pose2 = {
                { 96.43329f   ,  56.096897f  ,   0.86372066f},
                {132.28876f   ,  68.64918f   ,   0.810936f  },
                {110.73215f   ,  74.90611f   ,   0.71522355f},
                { 90.18612f   , 117.92614f   ,   0.8497982f },
                { 57.873466f  , 161.85849f   ,   0.7987103f },
                {154.67798f   ,  65.92649f   ,   0.66297865f},
                {137.66705f   , 125.10346f   ,   0.7842502f },
                {104.49397f   , 169.0325f    ,   0.8494513f },
                {180.67688f   , 139.43913f   ,   0.655208f  },
                {161.8904f    , 141.23114f   ,   0.62387294f},
                {121.536644f  , 191.44049f   ,   0.7887028f },
                {109.87479f   , 276.585f     ,   0.7558689f },
                {197.70332f   , 138.53854f   ,   0.62932074f},
                {161.84354f   , 198.6076f    ,   0.84282714f},
                {155.60756f   , 292.74673f   ,   0.6917627f },
                { 91.0291f    ,  51.590065f  ,   0.33497256f},
                { 98.20706f   ,  47.12905f   ,   0.9287781f },
                {  0.f        ,   0.f        ,   0.f        },
                {117.8905f    ,  35.470234f  ,   0.86060053f},
                {125.09658f   , 309.77042f   ,   0.6263589f },
                {132.28064f   , 311.53595f   ,   0.6232552f },
                {162.77498f   , 300.80774f   ,   0.5858406f },
                { 78.502975f  , 291.8707f    ,   0.41080147f},
                { 79.40123f   , 290.0634f    ,   0.37501f   },
                {114.36681f   , 282.905f     ,   0.66658765f}
        };

        PoseDiff pd = new PoseDiff();
        float[] result = pd.diff(pose1, pose2);

        //匹配度 数组
        for (int i = 0; i < result.length; ++i) {
            System.out.println(result[i]);
        }
    }
    //返回不匹配的数组
    public static List<Integer> match(float[][] mainPose, float[][] pose2, float expect) {
        PoseDiff pd = new PoseDiff();
        float[] result = pd.diff(mainPose, pose2);
        System.out.println("diff result: " + Arrays.toString(result));

        List<Integer> mismatch = new ArrayList<>();
        boolean waist_miss = false;
        for (int i = 0 ; i < result.length ; i ++){
            float val = result[i];
            //去掉非stand的 diff. 超过阈值就认定为miss
            if(mainPose[i][0] != 0 && val > expect){
                if(i == 1){//posenet doesn't have neck
                    continue;
                }
                if(i == 8){
                    waist_miss = true;
                }else {
                    mismatch.add(i >= 8 ? i - 1 : i);
                }
            }
        }
        //根据未识别到的腰index. 添加左右臀
       /* if(waist_miss){
            int idx = Common.CocoPart.LHip.index;
            if(mainPose[idx][0] != 0 && !mismatch.contains(idx)){
                mismatch.add(idx);
            }
            idx = Common.CocoPart.RHip.index;
            if(mainPose[idx][0] != 0 && !mismatch.contains(idx)){
                mismatch.add(idx);
            }
        }*/
        return mismatch;
    }
}
