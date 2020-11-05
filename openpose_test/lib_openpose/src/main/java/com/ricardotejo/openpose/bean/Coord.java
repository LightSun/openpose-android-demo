package com.ricardotejo.openpose.bean;

public class Coord {
    public float x;
    public float y;
    public float score;
    public int count;

    public Coord(float x, float y) {
        this.x = x;
        this.y = y;
    }

    public Coord(float x, float y, float score, int count) {
        this.x = x;
        this.y = y;
        this.score = score;
        this.count = count;
    }
}