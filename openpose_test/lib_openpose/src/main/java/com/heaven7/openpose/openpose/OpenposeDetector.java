package com.heaven7.openpose.openpose;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;

import com.heaven7.android.openpose.api.OpenposeApi;
import com.heaven7.android.openpose.api.bean.Human;
import com.heaven7.android.openpose.api.bean.Recognition;
import com.heaven7.core.util.ImageParser;
import com.heaven7.java.base.util.Disposable;
import com.heaven7.java.base.util.IOUtils;
import com.heaven7.java.base.util.Scheduler;
import com.heaven7.java.visitor.MapFireVisitor;
import com.heaven7.java.visitor.collection.KeyValuePair;
import com.heaven7.java.visitor.collection.VisitServices;
import com.heaven7.java.visitor.util.SparseArray;
import com.heaven7.openpose.openpose.bean.ImageHandleInfo;
import com.heaven7.openpose.openpose.env.ImageUtils;
import com.heaven7.openpose.openpose.env.SimpleResizeCallback;

import java.io.IOException;
import java.io.InputStream;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import static com.heaven7.openpose.openpose.OpenposeCameraManager.MP_INPUT_SIZE;

public final class OpenposeDetector {

    private static final int RATIO = 2;
    private final AtomicInteger mId = new AtomicInteger();
    private final SparseArray<Disposable> mTaskMap = new SparseArray<Disposable>();

    private final Context mActivity;
    private final OpenposeApi detector;

    public OpenposeDetector(Context activity) {
        this(activity, new JavaPosenet(activity));
    }
    public OpenposeDetector(Context activity, OpenposeApi api) {
        this.mActivity = activity.getApplicationContext();
        detector = api;
    }
    public int recognizeImageFromAssets(Scheduler scheduler, String assetPath, final Callback cb) {
        int expectSize = MP_INPUT_SIZE * RATIO;

        ImageParser parser = new ImageParser(MP_INPUT_SIZE * RATIO, MP_INPUT_SIZE * RATIO,
                Bitmap.Config.ARGB_8888, false);
        parser.setCallback(new SimpleResizeCallback());
        float[] outInfo = new float[2];
        Bitmap bitmap = parser.decodeToBitmap(new AssetPathDecoder(),
                new ImageParser.DecodeParam(assetPath), outInfo);
        ImageHandleInfo info = new ImageHandleInfo();
        info.scale1 = outInfo[0];
        info.key = assetPath;
        return recognizeImage0(scheduler, bitmap, info, cb);
    }
    public void recognizeImageFromAssets(Scheduler scheduler, final List<String> assetPaths,
                                         final Callback cb, final Runnable end) {
        ImageParser parser = new ImageParser(MP_INPUT_SIZE * RATIO, MP_INPUT_SIZE * RATIO,
                Bitmap.Config.ARGB_8888, false);
        parser.setCallback(new SimpleResizeCallback());
        final ComposeCallback ccb = new ComposeCallback(cb, new Callback() {
            final AtomicInteger count = new AtomicInteger(assetPaths.size());
            @Override
            public void onRecognized(Bitmap bitmap, ImageHandleInfo key, List<Human> list) {
                if(count.decrementAndGet() == 0 && end != null){
                    end.run();
                }
            }
        });
        AssetPathDecoder pathDecoder = new AssetPathDecoder();
        float[] outInfo = new float[2];
        for (String assetPath : assetPaths){
            Bitmap bitmap = parser.decodeToBitmap(pathDecoder,
                    new ImageParser.DecodeParam(assetPath), outInfo);
            ImageHandleInfo info = new ImageHandleInfo();
            info.scale1 = outInfo[0];
            info.key = assetPath;
            recognizeImage0(scheduler, bitmap, info, ccb);
        }
    }
    public int recognizeImage(Scheduler scheduler, String filePath, Object key, final Callback cb) {
        //首次缩放之后先补齐. 然后再缩放和裁剪
        ImageParser parser = new ImageParser(MP_INPUT_SIZE * RATIO, MP_INPUT_SIZE * RATIO,
                Bitmap.Config.ARGB_8888, false);
        parser.setCallback(new SimpleResizeCallback());
        float[] outInfo = new float[2];
        Bitmap bitmap = parser.parseToBitmap(filePath, outInfo);
        ImageHandleInfo info = new ImageHandleInfo();
        info.scale1 = outInfo[0];
        info.key = key;
        return recognizeImage0(scheduler, bitmap, info, cb);
    }

    public int recognizeImage0(Scheduler scheduler, Bitmap bitmap, ImageHandleInfo info, final Callback cb) {
        if(cb instanceof DebugCallback){
            Bitmap rawImage = Bitmap.createScaledBitmap(bitmap, (int) (info.scale1 * bitmap.getWidth()),
                    (int) (info.scale1 * bitmap.getHeight()), false);
            ((DebugCallback) cb).debugRawImage(rawImage);
        }
        if(cb instanceof DebugCallback){
            ((DebugCallback) cb).debugParserImage(bitmap);
        }
        //对齐宽高. 保证人一定能被完整的保存下来
        int wh = Math.max(bitmap.getWidth(), bitmap.getHeight());
        Bitmap croppedBitmap = ImageUtils.alignWidthHeight(bitmap, wh, wh, MP_INPUT_SIZE, MP_INPUT_SIZE);
        info.scale2 = wh * 1f / MP_INPUT_SIZE;
        //为了渲染出最后不正确的动作。需要将最后识别出的动作对齐到原图。
        info.compensateWidth = wh - bitmap.getWidth();
        info.compensateHeight = wh - bitmap.getHeight();
        info.finalWidth = MP_INPUT_SIZE;
        info.finalHeight = MP_INPUT_SIZE;
        return recognizeImage(scheduler, croppedBitmap, info, cb);
    }

    public int recognizeImage(Scheduler scheduler, final Bitmap bitmap, final ImageHandleInfo info, final Callback cb) {
        if(cb instanceof DebugCallback){
            ((DebugCallback) cb).debugCropImage(bitmap);
        }
        if(scheduler == null){
            List<Recognition> list = detector.inference(bitmap);
            List<Human> humans = null;
            if (list.size() > 0) {
                Recognition reg = list.get(0);
                if (reg.humans.size() > 0) {
                    humans = reg.humans;
                }
            }
            cb.onRecognized(bitmap, info, humans);
            return 0;
        }else {
            final int id = mId.incrementAndGet();
            Disposable task = scheduler.newWorker().schedule(new Runnable() {
                @Override
                public void run() {
                    List<Recognition> list = detector.inference(bitmap);
                    List<Human> humans = null;
                    if (list.size() > 0) {
                        Recognition reg = list.get(0);
                        if (reg.humans.size() > 0) {
                            humans = reg.humans;
                        }
                    }
                    cb.onRecognized(bitmap, info, humans);
                    synchronized (mTaskMap){
                        mTaskMap.remove(id);
                    }
                }
            });
            synchronized (mTaskMap){
                mTaskMap.put(id, task);
            }
            return id;
        }
    }
    public void cancel(int key){
        Disposable task = mTaskMap.get(key);
        if(task != null){
            synchronized (mTaskMap){
                mTaskMap.remove(key);
            }
            task.dispose();
        }
    }
    public void cancelAll(){
        synchronized (mTaskMap){
            VisitServices.from(mTaskMap).fire(new MapFireVisitor<Integer, Disposable>() {
                @Override
                public Boolean visit(KeyValuePair<Integer, Disposable> pair, Object param) {
                    pair.getValue().dispose();
                    return null;
                }
            });
        }
    }
    public void onDestroy(){
        detector.destroy();
    }
    private class AssetPathDecoder implements ImageParser.IDecoder{
        @Override
        public Bitmap decode(ImageParser.DecodeParam decodeParam, BitmapFactory.Options options) {
            InputStream in = null;
            try {
                in = mActivity.getAssets().open(decodeParam.pathName);
                return BitmapFactory.decodeStream(in, null, options);
            } catch (IOException e) {
                throw new RuntimeException(e);
            }finally {
                IOUtils.closeQuietly(in);
            }
        }
        @Override
        public int getOrientation(ImageParser.DecodeParam decodeParam) throws IOException {
            return 0;
        }
    }
    public static class ComposeCallback implements Callback, DebugCallback{
        private final Callback c1;
        private final Callback c2;
        public ComposeCallback(Callback c1, Callback c2) {
            this.c1 = c1;
            this.c2 = c2;
        }
        @Override
        public void onRecognized(Bitmap bitmap, ImageHandleInfo key, List<Human> list) {
            if(c1 != null){
                c1.onRecognized(bitmap, key, list);
            }
            if(c2 != null){
                c2.onRecognized(bitmap, key, list);
            }
        }
        @Override
        public void debugParserImage(Bitmap bitmap) {
            if(c1 instanceof DebugCallback){
                ((DebugCallback)c1).debugParserImage(bitmap);
            }
            if(c2 instanceof DebugCallback){
                ((DebugCallback)c2).debugParserImage(bitmap);
            }
        }
        @Override
        public void debugRawImage(Bitmap bitmap) {
            if(c1 instanceof DebugCallback){
                ((DebugCallback)c1).debugRawImage(bitmap);
            }
            if(c2 instanceof DebugCallback){
                ((DebugCallback)c2).debugRawImage(bitmap);
            }
        }
        @Override
        public void debugCropImage(Bitmap bitmap) {
            if(c1 instanceof DebugCallback){
                ((DebugCallback)c1).debugCropImage(bitmap);
            }
            if(c2 instanceof DebugCallback){
                ((DebugCallback)c2).debugCropImage(bitmap);
            }
        }
    }
    public interface Callback{
        void onRecognized(Bitmap bitmap, ImageHandleInfo key, List<Human> list);
    }
    public interface DebugCallback{
        void debugRawImage(Bitmap bitmap);
        void debugParserImage(Bitmap bitmap);
        void debugCropImage(Bitmap bitmap);
    }
}
