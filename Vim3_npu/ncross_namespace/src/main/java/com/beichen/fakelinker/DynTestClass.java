package com.beichen.fakelinker;

import android.util.Log;

import androidx.annotation.Keep;

import java.io.File;

/**
 * @author beichen
 * @date 2019/04/15
 */
@Keep
public final class DynTestClass {

    public static void loadOfClassLoader(File libFile, ClassLoader loader){
        try {
            System.load(libFile.getAbsolutePath());
        }catch (Throwable e){
            Log.e("beichen", "dyn load lib error ", e);
        }
    }

    public static native int specialLoad(ClassLoader loader);
}
