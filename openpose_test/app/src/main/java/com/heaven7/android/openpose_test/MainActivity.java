package com.heaven7.android.openpose_test;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;

import android.graphics.Bitmap;
import android.os.Bundle;
import android.view.View;

import com.ricardotejo.openpose.OpenposeCameraManager;
import com.ricardotejo.openpose.OpenposeDetector;
import com.ricardotejo.openpose.bean.Human;

import java.util.List;

public class MainActivity extends AppCompatActivity implements OpenposeDetector.Callback {

    private OpenposeDetector mDetector;
    private OpenposeCameraManager mOCM;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        if(mOCM != null){
            mOCM.onRequestPermissionsResult(requestCode, permissions, grantResults);
        }
    }

    @Override
    protected void onPause() {
        if(mOCM != null){
            mOCM.onPause();
        }
        super.onPause();
    }

    @Override
    protected void onResume() {
        super.onResume();
        if(mOCM != null){
            mOCM.onResume();
        }
    }

    public void onClickCamera(View view) {
        if(mOCM == null){
            mOCM = new OpenposeCameraManager(this, R.id.container);
            mOCM.setDesiredPreviewFrameSize(1920 , 1080);
            mOCM.show();
        }
    }

    public void onClickDirectImage(View view) {
        if(mDetector == null){
            mDetector = new OpenposeDetector(this, this);
        }
    }
    @Override
    public void onRecognized(Bitmap bitmap, List<Human> list) {

    }
}
