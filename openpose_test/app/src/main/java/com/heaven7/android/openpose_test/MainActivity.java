package com.heaven7.android.openpose_test;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;

import android.os.Bundle;

import com.ricardotejo.openpose.OpenposeCameraManager;

public class MainActivity extends AppCompatActivity {

    private OpenposeCameraManager mOCM;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        mOCM = new OpenposeCameraManager(this, R.id.container);
        mOCM.show();
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        mOCM.onRequestPermissionsResult(requestCode, permissions, grantResults);
    }

    @Override
    protected void onPause() {
        mOCM.onPause();
        super.onPause();
    }

    @Override
    protected void onResume() {
        super.onResume();
        mOCM.onResume();
    }
}
