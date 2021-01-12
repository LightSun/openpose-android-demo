package com.heaven7.android.openpose_test;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;

import android.Manifest;
import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.Rect;
import android.os.Bundle;
import android.os.Environment;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewTreeObserver;
import android.widget.ImageView;

import com.bumptech.glide.Glide;
import com.heaven7.android.component.gallery.ImagePickComponent;
import com.heaven7.android.component.gallery.PickOption;
import com.heaven7.android.openpose.api.Common;
import com.heaven7.android.openpose.api.OpenposeApi;
import com.heaven7.android.openpose.api.bean.Coord;
import com.heaven7.android.openpose.api.bean.Human;
import com.heaven7.core.util.Logger;
import com.heaven7.core.util.PermissionHelper;
import com.heaven7.java.base.util.FileUtils;
import com.heaven7.java.base.util.IOUtils;
import com.heaven7.java.base.util.Predicates;
import com.heaven7.java.base.util.Scheduler;
import com.heaven7.java.pc.schedulers.Schedulers;
import com.heaven7.java.visitor.MapFireVisitor;
import com.heaven7.java.visitor.collection.KeyValuePair;
import com.heaven7.java.visitor.collection.VisitServices;
import com.heaven7.openpose.openpose.OpenposeCameraManager;
import com.heaven7.openpose.openpose.OpenposeDetector;
import com.heaven7.openpose.openpose.OverlayView;
import com.heaven7.openpose.openpose.bean.ImageHandleInfo;

import java.io.File;
import java.io.FileOutputStream;
import java.io.OutputStream;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;

import butterknife.BindView;
import butterknife.ButterKnife;

public class MainActivity extends AppCompatActivity implements OpenposeDetector.Callback,
        OpenposeDetector.DebugCallback, OpenposeCameraManager.Callback, ImagePickComponent.Callback {

    private static final String TAG = "MainActivity";
    private static final float EXPECT = 0.8f;
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

    @BindView(R.id.overlap_debug)
    OverlayView overlap_debug;

    private OpenposeDetector mDetector;
    private OpenposeCameraManager mOCM;
    private OpenposeApi mApi;

    private float[][] mPose1;
    private boolean mTestDiff;
    private Bitmap mCopyCropBitmap;
    private Bitmap mRawBitmap;

    private DebugCallback0 debugCB = new DebugCallback0();
    private boolean prepared = false;
    private PermissionHelper mHelper = new PermissionHelper(this);
    private final SimpleImagePickComponent mComponent = new SimpleImagePickComponent();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        ButterKnife.bind(this);

        mApi = OpenposeApiFactory.newApi(this);
        overlap_debug.addCallback(debugCB);

        mOCM = new OpenposeCameraManager(this, R.id.container);
        mOCM.setOpenposeApi(mApi);
        mOCM.setCallback(this);
        mOCM.setDebugCallback(debugCB);
        mOCM.enableCount(true);
        mOCM.setDrawCallback(new DrawCallback0(this));
        mVg_camera.getViewTreeObserver().addOnPreDrawListener(new ViewTreeObserver.OnPreDrawListener() {
            @Override
            public boolean onPreDraw() {
                mVg_camera.getViewTreeObserver().removeOnPreDrawListener(this);
                mOCM.setDesiredPreviewFrameSize(mVg_camera.getWidth(), mVg_camera.getHeight());
                return true;
            }
        });

        mHelper.startRequestPermission(Manifest.permission.WRITE_EXTERNAL_STORAGE, 1, new PermissionHelper.ICallback() {
            @Override
            public void onRequestPermissionResult(String s, int i, boolean b) {
                System.out.println("permission result: " + b);
                if(b){
                    prepareAsync();
                }
            }
            @Override
            public boolean handlePermissionHadRefused(String s, int i, Runnable runnable) {
                return false;
            }
        });
    }
    private void prepareAsync(){
        Schedulers.io().newWorker().schedule(new Runnable() {
            @Override
            public void run() {
                mApi.prepare(getApplicationContext());
                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        mApi.initialize(getApplicationContext());
                        prepared = true;
                        System.out.println("(opt-NPU) nnpai create success.");
                        //for test
                        onClickGenDatas(null);
                    }
                });
            }
        });
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, @Nullable Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        mComponent.onActivityResult(this, requestCode, resultCode, data);
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

    @Override
    protected void onDestroy() {
        if(mOCM != null){
            mOCM.onDestroy();
            mOCM = null;
        }
        if(mDetector != null){
            mDetector.onDestroy();
            mDetector = null;
        }
        super.onDestroy();
    }

    public void onClickCamera(View view) {
        if(!prepared){
            System.err.println("npu not prepared");
            return;
        }
        mVg_imgs.setVisibility(View.GONE);
        mVg_camera.setVisibility(View.VISIBLE);

        /*if(mOCM == null){
            mOCM = new OpenposeCameraManager(this, R.id.container);
            mOCM.setDesiredPreviewFrameSize(1920 , 1080);
        }*/
        mOCM.show();
    }

    public void onClickDirectImage(View view) {
        if(!prepared){
            System.err.println("npu not prepared");
            return;
        }
        mTestDiff = false;
        if(mDetector == null){
            mDetector = new OpenposeDetector(this, mApi);
        }
        mVg_imgs.setVisibility(View.VISIBLE);
        mVg_camera.setVisibility(View.GONE);

        String path = "cover2.jpg";
        Glide.with(getApplicationContext()).load("file:///android_asset/" + path).into(iv1);
        mDetector.recognizeImageFromAssets(Schedulers.io(), path, this);
    }
    public void onClickSelectImage(View view) {
        if(!prepared){
            System.err.println("npu not prepared");
            return;
        }
        mTestDiff = true;
        mPose1 = null;

        if(mDetector == null){
            mDetector = new OpenposeDetector(this, mApi);
        }
        mVg_imgs.setVisibility(View.VISIBLE);
        mVg_camera.setVisibility(View.GONE);

        PickOption option = new PickOption.Builder().setMaxCount(1).build();
        mComponent.startPickFromGallery(this, option, this);
    }
    public void onClickGenDatas(View view){
        if(!prepared){
            System.err.println("npu not prepared");
            return;
        }
        mTestDiff = true;
        mPose1 = null;

        if(mDetector == null){
            mDetector = new OpenposeDetector(this, mApi);
        }
        mVg_imgs.setVisibility(View.VISIBLE);
        mVg_camera.setVisibility(View.GONE);

        Schedulers.io().newWorker().schedule(new Runnable() {
            @Override
            public void run() {
                System.out.println("-------- posenet start ----------");
                String dir = Environment.getExternalStorageDirectory() + "/temp/openpose"; //std posenet
               // String dir = Environment.getExternalStorageDirectory() + "/temp2/openpose"; //npu posenet
                List<String> files = FileUtils.getFiles(new File(dir), "jpg");
                Scheduler scheduler = Schedulers.single();
                for (String file : files){
                    if(FileUtils.getFileName(file).endsWith("__marked")){
                        continue;
                    }
                    runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            mDetector.recognizeImage(scheduler, file, null, new DumpCallback(true));
                        }
                    });
                }
            }
        });
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
            if(mTestDiff && this.mPose1 != null){
                final List<Integer> ids = OpenposeDiffUtils.match(this.mPose1, mPose1, EXPECT);
                System.out.println("onRecognized mismatch ids = " + ids);
                OpenposeUtils.print(this.mPose1, mPose1);
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
        float[][] pose2 = toPose(map);
        OpenposeUtils.print(this.mPose1, pose2);
        return OpenposeDiffUtils.match(mPose1, pose2, EXPECT);
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
    @Override
    public void onPickResult(Activity activity, List<String> files) {
        if(!files.isEmpty()){
            String file = files.get(0);
            Glide.with(getApplicationContext()).load(file).into(iv1);
            Logger.d(TAG,  "onPickResult", file);
            mDetector.recognizeImage(Schedulers.io(), file, null, new DumpCallback(false));
        }else {
            Logger.w(TAG,  "onPickResult", "no file");
        }
    }

    private class DumpCallback implements OpenposeDetector.Callback,OpenposeDetector.DebugCallback{

        private Bitmap mParserImage;
        private Canvas canvas;
        private boolean saveData;

        public DumpCallback(boolean saveData) {
            this.saveData = saveData;
        }

        @Override
        public void onRecognized(Bitmap bitmap, ImageHandleInfo key, List<Human> list) {
            System.out.println("---------- SelectImageCallback ----------- " + list.size());
            System.out.println("scanned: " + key.key);
            String path = (String) key.key;
            String fileDir = FileUtils.getFileDir(path, 1, true);
            String name = FileUtils.getFileName(path);
            //save data
            StringWriter sw = new StringWriter();
            if(!list.isEmpty() && !list.get(0).parts.isEmpty()){
                mPose1 = toPose(list.get(0).parts);
                if(saveData){
                    //print to
                    sw.write("--------------- to diff data -----------------\n");
                    sw.write(OpenposeUtils.printTo(null, mPose1));
                    sw.write("\n");
                    sw.write("\n");
                    sw.write("--------------- raw data ----------------------\n");
                    for (Human human : list){
                        for (Map.Entry<Integer, Coord> en :human.parts.entrySet()){
                            sw.write(en.getKey() + ":  " + en.getValue() + "\n");
                        }
                    }
                    FileUtils.writeTo(new File(fileDir, name + ".txt"), sw.toString());
                }
            }else {
                mPose1 = null;
                for (Human human : list){
                    for (Map.Entry<Integer, Coord> en :human.parts.entrySet()){
                        System.out.println(en.getKey() + ":  " + en.getValue());
                    }
                }
            }
            key.scale1 = 1;
            mOCM.drawMismatch(canvas, list.get(0).parts, key, Collections.emptyList());
            runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    iv4.setImageBitmap(mParserImage);
                }
            });
            //save marked image
            if(saveData){
                File outFile = new File(fileDir, name + "__marked.jpeg");
                OutputStream outSteam = null;
                try {
                    outSteam = new FileOutputStream(outFile);
                    mParserImage.compress(Bitmap.CompressFormat.JPEG, 100, outSteam);
                }catch (Exception e){
                    e.printStackTrace();
                }finally {
                    IOUtils.closeQuietly(outSteam);
                }
                System.out.println("save data ok: " + outFile.getAbsolutePath());
            }
        }
        @Override
        public void debugRawImage(Bitmap bitmap) {

        }
        @Override
        public void debugParserImage(Bitmap bitmap) {
            mParserImage = Bitmap.createBitmap(bitmap.getWidth(), bitmap.getHeight(), bitmap.getConfig());
            canvas = new Canvas(mParserImage);
            canvas.drawBitmap(bitmap, new Matrix(), null);
            iv4.setImageBitmap(mParserImage);
        }
        @Override
        public void debugCropImage(Bitmap bitmap) {

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
            return match ? Color.GREEN : Color.RED;
        }
        @Override
        public float getPointRadius(boolean match) {
            return 15;
        }
        @Override
        public float getConcatStrokeWidth(boolean match) {
            return 6;
        }
        @Override
        public int getConcatColor(int id1, int id2, boolean match, int defaultColor) {
            return Color.YELLOW;
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
    private class DebugCallback0 implements OpenposeCameraManager.DebugCallback, OverlayView.DrawCallback{

        final Rect rect = new Rect();
        final Rect vRect = new Rect();
        volatile Bitmap raw;
        volatile Bitmap mark;

        @Override
        public void debugRawImage(Bitmap bitmap) {
            raw = Bitmap.createBitmap(bitmap);
        }
        @Override
        public void debugMarkImage(Bitmap bitmap) {
            mark = Bitmap.createBitmap(bitmap);
            overlap_debug.postInvalidate();
            vRect.set(0, 0, overlap_debug.getWidth(), overlap_debug.getHeight());
        }
        @Override
        public void drawCallback(Canvas canvas) {
            if(raw != null){
                rect.set(0, 0, raw.getWidth(), raw.getHeight());
                canvas.drawBitmap(raw, rect, vRect, null);
            }
            if(mark != null){
                rect.set(0, 0, mark.getWidth(), mark.getHeight());
                canvas.drawBitmap(mark, rect, vRect, null);
            }
        }
    }
}
