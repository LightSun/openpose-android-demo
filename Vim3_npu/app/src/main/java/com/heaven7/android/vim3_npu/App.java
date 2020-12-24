package com.heaven7.android.vim3_npu;

import android.app.Application;

import com.heaven7.android.openpose.api.OpenposeApi;
import com.heaven7.java.pc.schedulers.Schedulers;

public class App extends Application {

    @Override
    public void onCreate() {
        super.onCreate();
        OpenposeApi mOpenposeApi = OpenposeApiFactory.newApi(this);

        Schedulers.io().newWorker().schedule(new Runnable() {
            @Override
            public void run() {
                mOpenposeApi.prepare(getApplicationContext());
            }
        });
    }
}
