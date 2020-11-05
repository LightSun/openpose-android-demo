package com.ricardotejo.openpose.bean;

import java.util.HashMap;
import java.util.Map;
import java.util.TreeMap;

public class Human {
    //key是关键点的id, value是坐标
    public Map<Integer, Coord> parts = new TreeMap<>();
}