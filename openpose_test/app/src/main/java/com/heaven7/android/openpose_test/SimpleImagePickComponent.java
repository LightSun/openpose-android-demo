package com.heaven7.android.openpose_test;

import android.app.Activity;
import android.content.Intent;
import android.os.Environment;

import androidx.annotation.Nullable;

import com.heaven7.android.component.gallery.ImagePickComponent;
import com.heaven7.android.component.gallery.PickOption;
import com.heaven7.android.imagepick.pub.CameraParameter;
import com.heaven7.android.imagepick.pub.ImageParameter;
import com.heaven7.android.imagepick.pub.ImagePickManager;
import com.heaven7.android.imagepick.pub.ImageSelectParameter;
import com.heaven7.android.imagepick.pub.PickConstants;
import com.heaven7.core.util.Logger;
import com.heaven7.java.base.util.Predicates;

import java.util.ArrayList;

import static com.heaven7.android.imagepick.pub.PickConstants.REQ_CAMERA;
import static com.heaven7.android.imagepick.pub.PickConstants.REQ_GALLERY;

public final class SimpleImagePickComponent implements ImagePickComponent {

    private Callback mCallback;

    @Override
    public void startPickFromCamera(Activity activity, PickOption pickOption, Callback callback) {
        mCallback = callback;
        ImagePickManager.get().getImagePickDelegate().startCamera(activity,
                new CameraParameter.Builder()
                        .setMaxCount(pickOption.getMaxCount())
                        .build());
    }

    @Override
    public void startPickFromGallery(Activity activity, PickOption pickOption, Callback callback) {
        mCallback = callback;
        String cacheDir = Environment.getExternalStorageDirectory() + "/lib_pick";
        ImagePickManager.get().getImagePickDelegate().startBrowseImages(activity, new ImageSelectParameter.Builder()
                .setImageParameter(new ImageParameter.Builder()
                        .setMaxWidth(3000)
                        .setMaxHeight(3000)
                        .build())
                .setCacheDir(cacheDir)
                .setMaxSelect(pickOption.getMaxCount())
                .build());
    }

    @Override
    public void onActivityResult(Activity activity, int requestCode, int resultCode, @Nullable Intent data) {
        if(resultCode == Activity.RESULT_OK && data != null){
            switch (requestCode){
                case REQ_GALLERY:
                case REQ_CAMERA: {
                    try {
                        ArrayList<String> images = data.getStringArrayListExtra(PickConstants.KEY_RESULT);
                        if(!Predicates.isEmpty(images)){
                            mCallback.onPickResult(activity, images);
                            Logger.d("SimpleImagePickComponent", "onActivityResult", "images = \n" + images);
                        }else {
                            Logger.d("SimpleImagePickComponent", "onActivityResult", "no select images.");
                        }
                    }finally {
                        mCallback = null;
                    }
                    break;
                }
            }
        }
    }
}