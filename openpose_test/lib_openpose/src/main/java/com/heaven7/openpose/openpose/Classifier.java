// Code migrated from https://github.com/ildoonet/tf-pose-estimation

package com.heaven7.openpose.openpose;

import android.graphics.Bitmap;

import com.heaven7.android.openpose.api.bean.Recognition;

import java.util.List;

/**
 * Generic interface for interacting with different recognition engines.
 */
public interface Classifier {

    List<Recognition> recognizeImage(Bitmap bitmap);

    void enableStatLogging(final boolean debug);

    String getStatString();

    void close();
}
