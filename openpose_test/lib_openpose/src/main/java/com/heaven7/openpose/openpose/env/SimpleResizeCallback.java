package com.heaven7.openpose.openpose.env;

import com.heaven7.core.util.ImageParser;

public class SimpleResizeCallback extends ImageParser.SimpleCallback {

    @Override
    public int[] getResizedDimensions(int maxWidth, int maxHeight, int actualWidth, int actualHeight) {
        float sx = actualWidth * 1f / maxWidth;
        float sy = actualHeight * 1f / maxHeight;
        float s = Math.max(sx, sy);
        return new int[]{(int) (actualWidth / s), (int) (actualHeight /s)};
    }

}
