package com.beichen.fakelinker;

import android.content.Context;
import android.os.Build;
import android.os.Bundle;
import android.os.Process;
import android.util.Log;
import android.view.View;
import android.widget.TextView;

import androidx.annotation.RequiresApi;
import androidx.appcompat.app.AppCompatActivity;

import com.heaven7.android.jni.cross_namespace.R;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.lang.reflect.Method;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

import dalvik.system.DexClassLoader;

public class MainActivity extends AppCompatActivity implements View.OnClickListener {
    private static final String TAG = "beichen";

    static {
        System.loadLibrary("native-lib");
    }

    @RequiresApi(api = Build.VERSION_CODES.N)
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity1);
        findViewById(R.id.btn_call).setOnClickListener(this);
        findViewById(R.id.btn_call_test).setOnClickListener(this);
        findViewById(R.id.btn_register).setOnClickListener(this);
        findViewById(R.id.btn_test_dlopen).setOnClickListener(this);
        findViewById(R.id.btn_find_classloader).setOnClickListener(this);
        TextView tv = findViewById(R.id.sample_text);

        // 开始跨命名空间注册函数
        new Thread() {
            @Override
            public void run() {
                Log.d(TAG, "release result: " + releaseDexAndLoad(MainActivity.this));
            }
        }.run();
    }


    public native int hookDlopen();

    public static native int dynRegister();

    public native void findThirdNamespace();

    public native void testSolist();

    @RequiresApi(api = Build.VERSION_CODES.N)
    public static boolean releaseDexAndLoad(Context context) {
        //DynTestClass is in classes5.dex
        if (!releaseFile(context, "classes5.dex", context.getDataDir().getAbsolutePath() + File.separator + "dyn.dex")) {
            return false;
        }
        String[] abis = Build.SUPPORTED_32_BIT_ABIS;
        boolean isX86 = false;
        for (String abi : abis) {
            if (abi.contains("x86")) {
                isX86 = true;
                break;
            }
        }
        String libEntryName;
        if (Process.is64Bit()) {
            libEntryName = isX86 ? "lib/x86_64/libnative-lib.so" : "lib/arm64-v8a/libnative-lib.so";
        } else {
            libEntryName = isX86 ? "lib/x86/libnative-lib.so" : "lib/armeabi-v7a/libnative-lib.so";
        }
        File lib = new File(context.getDataDir().getAbsolutePath() + File.separator + "libnative-lib.so");
        if (!releaseFile(context, libEntryName, lib.getAbsolutePath())) {
            return false;
        }
        return dynLoad(context, lib);

    }

    @RequiresApi(api = Build.VERSION_CODES.N)
    public static boolean dynLoad(Context context, File lib) {
        try {
            DexClassLoader loader = new DexClassLoader(context.getDataDir().getAbsolutePath() + File.separator + "dyn.dex", null, null, null);
            Class testClass = loader.loadClass(DynTestClass.class.getCanonicalName());

            Method med = testClass.getDeclaredMethod("loadOfClassLoader", File.class, ClassLoader.class);
            med.invoke(null, lib, MainActivity.class.getClassLoader());

            specialMethod = testClass.getDeclaredMethod("specialLoad", ClassLoader.class);
        } catch (Throwable e) {
            Log.e(TAG, "dyn load dex error", e);
            return false;
        }
        return true;
    }

    private static boolean releaseFile(Context context, String entryName, String out) {
        try {
            ZipFile zip = new ZipFile(context.getPackageResourcePath());
            ZipEntry entry = zip.getEntry(entryName);
            InputStream is = zip.getInputStream(entry);
            byte[] bytes = new byte[4096];
            OutputStream os = new FileOutputStream(out);
            int len;
            while ((len = is.read(bytes)) != -1) {
                os.write(bytes, 0, len);
            }
            os.flush();
            os.close();
            is.close();
            return true;
        } catch (IOException ignore) {
        }
        return false;
    }

    static Method specialMethod;


    @Override
    public void onClick(View v) {
        int id = v.getId();
        if (id == R.id.btn_test_dlopen) {
            Log.d(TAG, "dlopen output: " + hookDlopen());
        } else if (id == R.id.btn_register) {
            try {
                specialMethod.invoke(null, this.getClass().getClassLoader());
            } catch (Exception e) {
                e.printStackTrace();
            }
        } else if (id == R.id.btn_call) {
            Log.d(TAG, "dyn register output: " + dynRegister());
        } else if (id == R.id.btn_call_test) {
            testSolist();
        } else if (id == R.id.btn_find_classloader) {
            findThirdNamespace();
        }
    }
}
