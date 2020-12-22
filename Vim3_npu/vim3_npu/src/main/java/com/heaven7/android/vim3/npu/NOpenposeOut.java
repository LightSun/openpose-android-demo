package com.heaven7.android.vim3.npu;

public class NOpenposeOut {

    private long ptr;

    public NOpenposeOut() {
        this.ptr = nAlloc();
    }
    public long getPtr(){
        return ptr;
    }
    public void destroyNative(){
        if(ptr != 0){
            nFree(ptr);
            ptr = 0;
        }
    }
    @Override
    protected void finalize() throws Throwable {
        destroyNative();
        super.finalize();
    }

    private static native long nAlloc();
    private static native void nFree(long ptr);
    private static native int nGetOutSize(long ptr);
    private static native float nGetOutX(long ptr, int index);
    private static native float nGetOutY(long ptr, int index);
    private static native float nGetOutScore(long ptr, int index);

    //-------------------------------------------
    public int getOutSize(){
        return nGetOutSize(getPtr());
    }
    public float getOutX(int index){
        return nGetOutX(getPtr(), index);
    }
    public float getOutY(int index){
        return nGetOutY(getPtr(), index);
    }
    public float getOutScore(int index){
        return nGetOutScore(getPtr(), index);
    }
}
