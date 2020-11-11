package com.heaven7.openpose.openpose;

import com.heaven7.java.base.util.Objects;

/*public*/ class OpenposeDebug {

    public static final byte TYPE_PREPARE   = 1;
    public static final byte TYPE_RECOGNIZE = 2;
    public static final byte TYPE_ALGORITHM = 3;
    public static final byte TYPE_DRAW      = 4;

    private long start;
    private byte type;

    private long prepareCost;
    private long regCost;
    private long algCost;
    private long drawCost;

    private boolean enable = true;

    public boolean isEnable() {
        return enable;
    }

    public void setEnable(boolean enable) {
        this.enable = enable;
    }

    public void start(byte type){
        if(enable){
            start = System.currentTimeMillis();
            this.type = type;
        }
    }
    public void end(){
        if(enable) {
            switch (this.type) {
                case TYPE_PREPARE:
                    prepareCost = System.currentTimeMillis() - start;
                    break;
                case TYPE_RECOGNIZE:
                    regCost = System.currentTimeMillis() - start;
                    break;
                case TYPE_ALGORITHM:
                    algCost = System.currentTimeMillis() - start;
                    break;
                case TYPE_DRAW:
                    drawCost = System.currentTimeMillis() - start;
                    break;

                default:
                    System.err.println("wrong type = " + type);
            }
        }
    }
    public String toCostString(){
        return Objects.toStringHelper("OpenposeDebug")
                .add("prepare_Cost", prepareCost)
                .add("recognize_cost", regCost)
                .add("algorithm_Cost", algCost)
                .add("draw_cost", drawCost)
                .toString();
    }
    public void printCostInfo(){
        if(enable){
            System.out.println(toCostString());
            prepareCost = 0;
            regCost = 0;
            algCost = 0;
            drawCost = 0;
        }
    }
}
