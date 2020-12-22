package com.heaven7.android.openpose.api.bean;

import java.util.List;

public class Recognition {

    public List<Human> humans;

    private final String id;

    private final Float confidence;

    public Recognition(final String id, final Float confidence) {
        this.id = id;
        this.confidence = confidence;
    }

    public String getId() {
        return id;
    }

    public Float getConfidence() {
        return confidence;
    }

    @Override
    public String toString() {
        String resultString = "";
        if (id != null) {
            resultString += "[" + id + "] ";
        }
        if (confidence != null) {
            resultString += String.format("(%.1f%%) ", confidence * 100.0f);
        }
        return resultString.trim();
    }
}