package com.heaven7.android.openpose_test;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
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
import com.heaven7.openpose.openpose.Common;
import com.heaven7.openpose.openpose.OpenposeCameraManager;
import com.heaven7.openpose.openpose.OpenposeDetector;
import com.heaven7.openpose.openpose.bean.Coord;
import com.heaven7.openpose.openpose.bean.Human;
import com.heaven7.openpose.openpose.bean.ImageHandleInfo;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;

import butterknife.BindView;
import butterknife.ButterKnife;

public class MainActivity extends AppCompatActivity implements OpenposeDetector.Callback,
        OpenposeDetector.DebugCallback, OpenposeCameraManager.Callback {

    private static final String TAG = "MainActivity";
    private static final float EXPECT = 0.01f;
    @BindView(R.id.container)
    ViewGroup mVg_camera;
    @BindView(R.id.vg_imgs)
    ViewGroup mVg_imgs;

    @BindView(R.id.iv1)
    ImageView iv1;
    @BindView(R.id.iv1_mask)
    ImageView iv1_mask;
    @BindView(R.id.iv2)
    ImageView iv2;
    @BindView(R.id.iv3)
    ImageView iv3;
    @BindView(R.id.iv4)
    ImageView iv4;

    private OpenposeDetector mDetector;
    private OpenposeCameraManager mOCM;
    private float[][] mPose1;
    private boolean mTestDiff;
    private Bitmap mCopyCropBitmap;

    private Bitmap mRawBitmap;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        ButterKnife.bind(this);

        mOCM = new OpenposeCameraManager(this, R.id.container);
        mOCM.setCallback(this);
        mOCM.setDebug(false);
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
        mTestDiff = false;
        if(mDetector == null){
            mDetector = new OpenposeDetector(this);
        }
        mVg_imgs.setVisibility(View.VISIBLE);
        mVg_camera.setVisibility(View.GONE);

        String path = "cover2.jpg";
        Glide.with(getApplicationContext()).load("file:///android_asset/" + path).into(iv1);
        mDetector.recognizeImageFromAssets(Schedulers.io(), path, this);
    }
    public void onClickDirectImage2(View view) {
        mTestDiff = true;
        if(mDetector == null){
            mDetector = new OpenposeDetector(this);
        }
        mVg_imgs.setVisibility(View.VISIBLE);
        mVg_camera.setVisibility(View.GONE);

        String path = "test.jpg";
        Glide.with(getApplicationContext()).load("file:///android_asset/" + path).into(iv1);
        mDetector.recognizeImageFromAssets(Schedulers.io(), path, this);
    }
    @Override
    public void onRecognized(Bitmap bitmap, ImageHandleInfo key, final List<Human> list) {
        /*
         * 动作是否正确。（比如健身）
         * 1, 计算diff.
         * 2, diff 平均 大于 阈值 则指出哪些动作不正确.
         */
        if(!Predicates.isEmpty(list)){
            System.out.println("get stand info ok.");
            for (Human human : list){
                for (Map.Entry<Integer, Coord> en :human.parts.entrySet()){
                    System.out.println(en.getKey() + ":  " + en.getValue());
                }
            }
            //mOCM.setMainCoordMap(list.get(0).parts);
            float[][] mPose1 = toPose(list.get(0).parts);
            if(mTestDiff){
                final List<Integer> ids = OpenposeDiffUtils.match(this.mPose1, mPose1, EXPECT);
                System.out.println("onRecognized mismatch ids = " + ids);
                //cropped image. just for debug
                Canvas canvas = new Canvas(mCopyCropBitmap);
                mOCM.drawMismatch(canvas, list.get(0).parts, ids);
                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        iv4.setImageBitmap(mCopyCropBitmap);
                    }
                });
                mOCM.drawMismatch(list.get(0).parts, key, ids);
            }else {
                this.mPose1 = mPose1;
            }
        }else {
            System.err.println("no human");
        }
    }

    @Override
    public void debugRawImage(Bitmap bitmap) {
        mRawBitmap = bitmap;
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                iv1_mask.setImageBitmap(mRawBitmap);
            }
        });
    }
    @Override
    public void debugParserImage(Bitmap bitmap) {
        iv2.setImageBitmap(bitmap);
    }
    @Override
    public void debugCropImage(Bitmap bitmap) {
        iv3.setImageBitmap(bitmap);
        mCopyCropBitmap = Bitmap.createBitmap(bitmap);
    }

    @Override
    public List<Integer> match(Map<Integer, Coord> map) {
        if(mPose1 == null){
            System.err.println("no pose1");
            return Collections.emptyList();
        }
        return OpenposeDiffUtils.match(mPose1, toPose(map), EXPECT);
    }
    private float[][] toPose(Map<Integer, Coord> map){
        final float[][] pose2 = new float[Common.CocoPairs.length][];
        //为了算法，插入腰.  腰 = (左臀 + 右臀)/2. index = 8
        Coord left = map.get(Common.CocoPart.LHip.index);
        Coord right = map.get(Common.CocoPart.RHip.index);
        float[] waist = new float[3];
        if(left != null){
            float x, y , score;
            if(right != null){
                x = (left.x + right.x) / 2;
                y = (left.y + right.y) / 2;
                score = (left.score + right.score) / 2;
            }else {
                x = left.x;
                y = left.y;
                score = left.score;
            }
            waist[0] = x;
            waist[1] = y;
            waist[2] = score;
        }else if(right != null){
            waist[0] = right.x;
            waist[1] = right.y;
            waist[2] = right.score;
        }else {
            Arrays.fill(waist, 0);
        }
        VisitServices.from(map).fire(new MapFireVisitor<Integer, Coord>() {
            @Override
            public Boolean visit(KeyValuePair<Integer, Coord> pair, Object param) {
                Coord c = pair.getValue();
                //for insert waist to index 8.
                int index = pair.getKey() >= 8 ? pair.getKey() + 1 : pair.getKey();
                float[] arr = pose2[index] = new float[3];
                arr[0] = c.x;
                arr[1] = c.y;
                arr[2] = c.score;
                return null;
            }
        });
        pose2[8] = waist;
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
    private class DrawCallback0 extends OpenposeCameraManager.DrawCallback {

        private final Context ctx;
        private Canvas canvas;
        private Bitmap mMaskImage;

        public DrawCallback0(Context ctx) {
            this.ctx = ctx.getApplicationContext();
        }
        @Override
        public int getPointColor(int id, boolean match, int defaultColor) {
            return match ? Color.BLUE : Color.RED;
        }
        @Override
        public float getPointRadius(boolean match) {
            return 8;
        }
        @Override
        public float getConcatStrokeWidth(boolean match) {
            return 6;
        }
        @Override
        public int getConcatColor(int id1, int id2, boolean match, int defaultColor) {
            return match ? Color.GREEN : Color.BLACK;
        }
        @Override
        public void drawPoint(float x, float y, float radius, Paint mPaint) {
            canvas.drawCircle(x, y, radius, mPaint);
        }
        @Override
        public void drawConcatLine(int x, int y, int x1, int y1, Paint mPaint) {
            canvas.drawLine(x, y, x1, y1, mPaint);
        }
        @Override
        public void beginDraw() {
            mMaskImage = Bitmap.createBitmap(mRawBitmap);
            canvas = new Canvas(mMaskImage);
        }
        @Override
        public void endDraw() {
            runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    iv1_mask.setImageBitmap(mMaskImage);
                }
            });
        }
    }
}
