package com.heaven7.android.openpose_test;

public final class OpenposeUtils {
    private static final String TAG = "OpenposeUtils";

    public static String printTo(float[][] pose1){
        StringBuilder sb = new StringBuilder();
        sb.append("mainPose:\n");
        appendPose(pose1, sb, true, false);

        sb.append("\n");
        appendPose(pose1, sb, false, false);
        appendPose(pose1, sb, false, true);
        return sb.toString();
    }

    public static void print(float[][] pose1, float[][] pose2){
        StringBuilder sb = new StringBuilder();
        sb.append("mainPose:\n");
        appendPose(pose1, sb, true, false);
        System.out.println(sb.toString());

        sb = new StringBuilder();
        sb.append("pose2:\n");
        appendPose(pose2, sb, true, false);
        System.out.println(sb.toString());
    }

    private static void appendPose(float[][] pose1, StringBuilder sb, boolean appendIndex, boolean pyStyle) {
        sb.append(pyStyle ? "[\n" :"{\n");
        for (int i =0, len = pose1.length ; i < len ; i++){
            float[] arr = pose1[i];
            // { 96.43329f   ,  56.096897f  ,   0.86372066f},
            if(appendIndex){
                sb.append(i).append(" ");
            }
            sb.append("{");
            for (int j = 0 ;j < arr.length ; j ++){
                sb.append(arr[j]);
                if(!pyStyle){
                    sb.append("f");
                }
                if(j != arr.length - 1){
                    sb.append(", ");
                }
            }
            sb.append("}");
            if(i != len - 1){
                sb.append(",\n");
            }
        }
        sb.append("\n}\n");
    }
}
