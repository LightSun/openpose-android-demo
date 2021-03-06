// Code migrated from https://github.com/ildoonet/tf-pose-estimation

package com.heaven7.openpose.openpose;

import android.graphics.Color;

public final class Common {
   /* NOSE,
    LEFT_EYE,
    RIGHT_EYE,
    LEFT_EAR,
    RIGHT_EAR,
    LEFT_SHOULDER,
    RIGHT_SHOULDER,
    LEFT_ELBOW,
    RIGHT_ELBOW,
    LEFT_WRIST,
    RIGHT_WRIST,
    LEFT_HIP,
    RIGHT_HIP,
    LEFT_KNEE,
    RIGHT_KNEE,
    LEFT_ANKLE,
    RIGHT_ANKLE*/
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
    public enum CocoPart

    {
        Nose(0), //鼻子
        Neck(1), //颈部

        RShoulder(2), //右肩部
        RElbow(3),    //右手肘
        RWrist(4),    //右手腕

        LShoulder(5), //左肩部
        LElbow(6),    //左手肘
        LWrist(7),    //左手腕

        RHip(8),      //右臀围
        RKnee(9),     //右膝盖
        RAnkle(10),   //右脚裸

        LHip(11),
        LKnee(12),
        LAnkle(13),

        REye(14),     //右眼
        LEye(15),
        REar(16),     //右耳
        LEar(17),
        Background(18);//后背

        public final int index;

        CocoPart(int index) {
            this.index = index;
        }
    }
    public static final boolean OPT_PREVIEW_SIZE = false;

    public static final int[][] CocoPairs = {
            {1, 2}, {1, 5}, {2, 3}, {3, 4}, {5, 6}, {6, 7}, {1, 8}, {8, 9}, {9, 10}, {1, 11},
            {11, 12}, {12, 13}, {1, 0}, {0, 14}, {14, 16}, {0, 15}, {15, 17}, {2, 16}, {5, 17}}; //  # = 19

    public static final int[][] CocoPairsRender = {
            CocoPairs[0], CocoPairs[1], CocoPairs[2], CocoPairs[3], CocoPairs[4], CocoPairs[5], CocoPairs[6],
            CocoPairs[7], CocoPairs[8], CocoPairs[9], CocoPairs[10], CocoPairs[11], CocoPairs[12], CocoPairs[13],
            CocoPairs[14], CocoPairs[15], CocoPairs[16] }; //17

    public static final int[][] CocoPairsNetwork = {
            {12, 13}, {20, 21}, {14, 15}, {16, 17}, {22, 23}, {24, 25}, {0, 1}, {2, 3}, {4, 5},
            {6, 7}, {8, 9}, {10, 11}, {28, 29}, {30, 31}, {34, 35}, {32, 33}, {36, 37}, {18, 19}, {26, 27}}; // # = 19

    public static final int[] CocoColors = {
            Color.rgb(255, 0, 0),
            Color.rgb(255, 85, 0),
            Color.rgb(255, 170, 0),
            Color.rgb(255, 255, 0),
            Color.rgb(170, 255, 0),
            Color.rgb(85, 255, 0),
            Color.rgb(0, 255, 0),

            Color.rgb(0, 255, 85),
            Color.rgb(0, 255, 170),
            Color.rgb(0, 255, 255),
            Color.rgb(0, 170, 255),
            Color.rgb(0, 85, 255),
            Color.rgb(0, 0, 255),
            Color.rgb(85, 0, 255),

            Color.rgb(170, 0, 255),
            Color.rgb(255, 0, 255),
            Color.rgb(255, 0, 170),
            Color.rgb(255, 0, 85),
/*
            {255, 0, 0}, {255, 85, 0}, {255, 170, 0}, {255, 255, 0}, {170, 255, 0}, {85, 255, 0}, {0, 255, 0},
            {0, 255, 85}, {0, 255, 170}, {0, 255, 255}, {0, 170, 255}, {0, 85, 255}, {0, 0, 255}, {85, 0, 255},
            {170, 0, 255}, {255, 0, 255}, {255, 0, 170}, {255, 0, 85}*/
    };

}
