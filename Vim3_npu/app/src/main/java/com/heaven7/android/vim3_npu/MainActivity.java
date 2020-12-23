package com.heaven7.android.vim3_npu;

import androidx.appcompat.app.AppCompatActivity;

import android.os.Bundle;
import android.view.View;

import com.heaven7.android.vim3.npu.NpuOpenpose;

public class MainActivity extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
    }

    public void onTestNpu(View view) {
        NpuOpenpose openpose = new NpuOpenpose();
        System.out.println("load lib NpuOpenpose ok");
    }
}
