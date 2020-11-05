package com.heaven7.android.openpose_test;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;

import android.graphics.Bitmap;
import android.os.Bundle;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewTreeObserver;
import android.widget.ImageView;

import com.bumptech.glide.Glide;
import com.heaven7.java.pc.schedulers.Schedulers;
import com.ricardotejo.openpose.OpenposeCameraManager;
import com.ricardotejo.openpose.OpenposeDetector;
import com.ricardotejo.openpose.bean.Coord;
import com.ricardotejo.openpose.bean.Human;

import java.util.List;
import java.util.Map;

import butterknife.BindView;
import butterknife.ButterKnife;

public class MainActivity extends AppCompatActivity implements OpenposeDetector.Callback,OpenposeDetector.DebugCallback {

    @BindView(R.id.container)
    ViewGroup mVg_camera;
    @BindView(R.id.vg_imgs)
    ViewGroup mVg_imgs;

    @BindView(R.id.iv1)
    ImageView iv1;
    @BindView(R.id.iv2)
    ImageView iv2;
    @BindView(R.id.iv3)
    ImageView iv3;

    private OpenposeDetector mDetector;
    private OpenposeCameraManager mOCM;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        ButterKnife.bind(this);

        mOCM = new OpenposeCameraManager(this, R.id.container);
        mVg_camera.getViewTreeObserver().addOnPreDrawListener(new ViewTreeObserver.OnPreDrawListener() {
            @Override
            public boolean onPreDraw() {
                mVg_camera.getViewTreeObserver().removeOnPreDrawListener(this);
                mOCM.setDesiredPreviewFrameSize(mVg_camera.getWidth(), mVg_camera.getHeight());
                return true;
            }
        });
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
        mVg_imgs.setVisibility(View.GONE);
        mVg_camera.setVisibility(View.VISIBLE);

        /*if(mOCM == null){
            mOCM = new OpenposeCameraManager(this, R.id.container);
            mOCM.setDesiredPreviewFrameSize(1920 , 1080);
        }*/
        mOCM.show();
    }

    public void onClickDirectImage(View view) {
        if(mDetector == null){
            mDetector = new OpenposeDetector(this);
        }
        mVg_imgs.setVisibility(View.VISIBLE);
        mVg_camera.setVisibility(View.GONE);

        String path = "cover2.jpg";
        Glide.with(getApplicationContext()).load("file:///android_asset/" + path).into(iv1);
        mDetector.recognizeImageFromAssets(Schedulers.io(), path, this);
    }
    @Override
    public void onRecognized(Bitmap bitmap, Object key, List<Human> list) {
        /*
         * 动作是否正确。（比如健身）
         * 1, 计算diff.
         * 2, diff 平均 大于 阈值 则指出哪些动作不正确.
         */
        if(list != null){
            for (Human human : list){
                for (Map.Entry<Integer, Coord> en :human.parts.entrySet()){
                    System.out.println(en.getKey() + ":  " + en.getValue());
                }
            }
        }
    }
    @Override
    public void debugParserImage(Bitmap bitmap) {
        iv2.setImageBitmap(bitmap);
    }
    @Override
    public void debugCropImage(Bitmap bitmap) {
        iv3.setImageBitmap(bitmap);
    }
}
