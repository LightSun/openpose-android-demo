// Code migrated from https://github.com/ildoonet/tf-pose-estimation

package com.heaven7.openpose.openpose;

import android.graphics.Bitmap;
import android.graphics.RectF;

import com.heaven7.openpose.openpose.bean.Human;

import java.util.List;

/**
 * Generic interface for interacting with different recognition engines.
 */
public interface Classifier {
    /**
     * An immutable result returned by a Classifier describing what was recognized.
     */
    class Recognition {
        // TODO: part, fromXY, toXY
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

    List<Recognition> recognizeImage(Bitmap bitmap);

    void enableStatLogging(final boolean debug);

    String getStatString();

    void close();
}
