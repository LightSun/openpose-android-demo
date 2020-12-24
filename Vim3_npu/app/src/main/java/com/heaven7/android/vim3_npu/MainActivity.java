package com.heaven7.android.vim3_npu;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;

import android.Manifest;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.os.Bundle;
import android.view.View;

import com.heaven7.android.openpose.api.OpenposeApi;
import com.heaven7.android.openpose.api.bean.Coord;
import com.heaven7.android.openpose.api.bean.Human;
import com.heaven7.android.vim3.npu.NpuOpenpose;
import com.heaven7.core.util.MainWorker;
import com.heaven7.core.util.PermissionHelper;
import com.heaven7.java.base.util.Predicates;
import com.heaven7.java.pc.schedulers.Schedulers;
import com.heaven7.openpose.openpose.OpenposeDetector;
import com.heaven7.openpose.openpose.bean.ImageHandleInfo;

import java.sql.SQLOutput;
import java.util.List;
import java.util.Map;

public class MainActivity extends AppCompatActivity implements OpenposeDetector.Callback {

    private final PermissionHelper mHelper = new PermissionHelper(this);
    private OpenposeDetector mDetector;

    private OpenposeApi mApi;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

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

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        mHelper.onRequestPermissionsResult(requestCode, permissions, grantResults);
    }
    private void prepareAsync(){
        mApi = OpenposeApiFactory.newApi(this);
        Schedulers.io().newWorker().schedule(new Runnable() {
            @Override
            public void run() {
                mApi.prepare(getApplicationContext());
                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        mApi.initialize(getApplicationContext());
                        System.out.println("nnpai create success.");
                        testNpuImage();
                    }
                });
            }
        });
    }

    private void testNpuImage() {
        MainWorker.postDelay(3000, new Runnable() {
            @Override
            public void run() {
                onTestNpuImage(null);
            }
        });
    }

    public void onTestNpu(View view) {
        NpuOpenpose openpose = new NpuOpenpose();
        System.out.println("load lib NpuOpenpose ok");
    }

    public void onTestNpuImage(View view) {
        if(mDetector == null){
            mDetector = new OpenposeDetector(this, mApi);
        }

        String path = "cover2.jpg";
        mDetector.recognizeImageFromAssets(Schedulers.io(), path, this);
    }

    @Override
    protected void onDestroy() {
        mDetector.onDestroy();
        super.onDestroy();
    }

    @Override
    public void onRecognized(Bitmap bitmap, ImageHandleInfo imageHandleInfo, List<Human> list) {
        /*
         * 动作是否正确。（比如健身）
         * 1, 计算diff.
         * 2, diff 平均 大于 阈值 则指出哪些动作不正确.
         */
        if(!Predicates.isEmpty(list)){
            System.out.println("onRecognized: get stand info ok.");
            for (Human human : list){
                for (Map.Entry<Integer, Coord> en :human.parts.entrySet()){
                    System.out.println(en.getKey() + ":  " + en.getValue());
                }
            }
        }else {
            System.err.println("onRecognized: no human");
        }
    }
}
