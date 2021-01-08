package com.heaven7.android.openpose_test;

import com.heaven7.core.util.Logger;

public final class OpenposeUtils {
    private static final String TAG = "OpenposeUtils";

    public static void print(float[][] pose1, float[][] pose2){
        StringBuilder sb = new StringBuilder();
        sb.append("mainPose:\n").append("{");
        appendPose(pose1, sb);
        sb.append("}");
        System.out.println(sb.toString());

        sb = new StringBuilder();
        sb.append("pose2:\n").append("{");
        appendPose(pose2, sb);
        sb.append("}");
        System.out.println(sb.toString());
    }

    private static void appendPose(float[][] pose1, StringBuilder sb) {
        for (int i =0, len = pose1.length ; i < len ; i++){
            float[] arr = pose1[i];
            // { 96.43329f   ,  56.096897f  ,   0.86372066f},
            sb.append(i).append(" ").append("{");
            for (int j = 0 ;j < arr.length ; j ++){
                sb.append(arr[j]);
                if(j != arr.length - 1){
                    sb.append(", ");
                }
            }
            if(i != len - 1){
                sb.append(",\n");
            }
            sb.append("}");
        }
    }
}
