package com.heaven7.android.vim3.npu;

import android.content.Context;

public final class ContextHelper {

    private static Context appContext;

    public static Context getAppContext() {
        return appContext;
    }

    public static void setAppContext(Context appContext) {
        ContextHelper.appContext = appContext;
    }
}
