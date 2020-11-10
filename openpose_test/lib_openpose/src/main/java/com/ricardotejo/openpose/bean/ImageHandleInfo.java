package com.ricardotejo.openpose.bean;

public class ImageHandleInfo {
    /**
     * the first scale often by image parser
     */
    public float scale1 = 1;
    /**
     * the compensate info of with and height. make image not lose some useful info.
     * we compensate with and height .then make image in center.
     */
    public float compensateWidth;
    public float compensateHeight;
    /**
     * the final scale for recognize
     */
    public float scale2 = 1;

    public int finalWidth;
    public int finalHeight;
    /**
     * the key info for callback
     */
    public Object key;

    public void reset(){
        scale1 = 1;
        scale2 = 1;
    }
}
