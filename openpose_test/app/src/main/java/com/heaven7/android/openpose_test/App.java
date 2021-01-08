package com.heaven7.android.openpose_test;

import android.app.Application;

import com.heaven7.android.imagepick.pub.ImagePickManager;
import com.heaven7.android.openpose.api.OpenposeApi;
import com.heaven7.java.pc.schedulers.Schedulers;

public class App extends Application {

    @Override
    public void onCreate() {
        super.onCreate();

        ImagePickManager.get().getImagePickDelegate().setImageLoadDelegate(new SimpleImageLoadDelegate());
    }
}
