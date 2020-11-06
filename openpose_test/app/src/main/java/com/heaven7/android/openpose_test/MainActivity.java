package com.heaven7.android.openpose_test;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.os.Bundle;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewTreeObserver;
import android.widget.ImageView;

import com.bumptech.glide.Glide;
import com.heaven7.java.base.util.Predicates;
import com.heaven7.java.pc.schedulers.Schedulers;
import com.heaven7.java.visitor.MapFireVisitor;
import com.heaven7.java.visitor.collection.KeyValuePair;
import com.heaven7.java.visitor.collection.VisitServices;
import com.ricardotejo.openpose.Common;
import com.ricardotejo.openpose.OpenposeCameraManager;
import com.ricardotejo.openpose.OpenposeDetector;
import com.ricardotejo.openpose.bean.Coord;
import com.ricardotejo.openpose.bean.Human;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;

import butterknife.BindView;
import butterknife.ButterKnife;

public class MainActivity extends AppCompatActivity implements OpenposeDetector.Callback,
        OpenposeDetector.DebugCallback, OpenposeCameraManager.Callback {

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
    private float[][] mPose1;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        ButterKnife.bind(this);

        mOCM = new OpenposeCameraManager(this, R.id.container);
        mOCM.setCallback(this);
       // mOCM.setDebug(false);
        mOCM.setDrawCallback(new DrawCallback0(this));
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
        if(!Predicates.isEmpty(list)){
            for (Human human : list){
                for (Map.Entry<Integer, Coord> en :human.parts.entrySet()){
                    System.out.println(en.getKey() + ":  " + en.getValue());
                }
            }
            mPose1 = toPose(list.get(0).parts);
        }else {
            System.err.println("no human");
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

    @Override
    public List<Integer> match(Map<Integer, Coord> map) {
        if(mPose1 == null){
            System.err.println("no pose1");
            return Collections.emptyList();
        }
        return OpenposeDiffUtils.match(mPose1, toPose(map));
    }
    private float[][] toPose(Map<Integer, Coord> map){
        final float[][] pose2 = new float[Common.CocoPairs.length][];
        VisitServices.from(map).fire(new MapFireVisitor<Integer, Coord>() {
            @Override
            public Boolean visit(KeyValuePair<Integer, Coord> pair, Object param) {
                Coord c = pair.getValue();
                float[] arr = pose2[pair.getKey()] = new float[3];
                arr[0] = c.x;
                arr[1] = c.y;
                arr[2] = c.score;
                return null;
            }
        });
        populate0(pose2);
        return pose2;
    }
    private void populate0(float[][] pose1){
        //populate 0 if need
        for (int i = 0; i < pose1.length ; i ++){
            float[] arr = pose1[i];
            if(arr == null){
                arr = new float[3];
                Arrays.fill(arr, 0);
                pose1[i] = arr;
            }
        }
    }
    private static class DrawCallback0 extends OpenposeCameraManager.DrawCallback {

        private final Context ctx;

        public DrawCallback0(Context ctx) {
            this.ctx = ctx.getApplicationContext();
        }
        @Override
        public int getPointColor(int id, boolean match, int defaultColor) {
            return match ? Color.BLUE : Color.RED;
        }
        @Override
        public float getPointRadius(boolean match) {
            return super.getPointRadius(match);
        }
        @Override
        public float getConcatStrokeWidth(boolean match) {
            return super.getConcatStrokeWidth(match);
        }
        @Override
        public int getConcatColor(int id1, int id2, boolean match, int defaultColor) {
            /*if(match){
                return super.getConcatColor(id1, id2, match, defaultColor);
            }*/
            return match ? Color.GREEN : Color.BLACK;
        }
    }
}
