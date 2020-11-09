package com.ricardotejo.openpose;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Matrix;
import android.graphics.Paint;

import com.heaven7.core.util.ImageParser;
import com.heaven7.java.base.util.Disposable;
import com.heaven7.java.base.util.Scheduler;
import com.heaven7.java.visitor.MapFireVisitor;
import com.heaven7.java.visitor.collection.KeyValuePair;
import com.heaven7.java.visitor.collection.VisitServices;
import com.heaven7.java.visitor.util.SparseArray;
import com.ricardotejo.openpose.bean.Human;
import com.ricardotejo.openpose.env.ImageUtils;

import java.io.IOException;
import java.io.InputStream;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import static com.ricardotejo.openpose.OpenposeCameraManager.MP_INPUT_NAME;
import static com.ricardotejo.openpose.OpenposeCameraManager.MP_INPUT_SIZE;
import static com.ricardotejo.openpose.OpenposeCameraManager.MP_MODEL_FILE;
import static com.ricardotejo.openpose.OpenposeCameraManager.MP_OUTPUT_L1;
import static com.ricardotejo.openpose.OpenposeCameraManager.MP_OUTPUT_L2;

public final class OpenposeDetector {

    private static final int RATIO = 2;
    private final AtomicInteger mId = new AtomicInteger();
    private final SparseArray<Disposable> mTaskMap = new SparseArray<Disposable>();

    private final Context mActivity;
    private final Classifier detector;

    public OpenposeDetector(Context activity) {
        this.mActivity = activity.getApplicationContext();
        detector = TensorFlowPoseDetector.create(
                activity.getAssets(),
                MP_MODEL_FILE,
                MP_INPUT_SIZE,
                MP_INPUT_NAME,
                new String[]{MP_OUTPUT_L1, MP_OUTPUT_L2}
        );
    }
    public int recognizeImageFromAssets(Scheduler scheduler, String assetPath, final Callback cb) {
        ImageParser parser = new ImageParser(MP_INPUT_SIZE * RATIO, MP_INPUT_SIZE * RATIO,
                Bitmap.Config.ARGB_8888, true);
        Bitmap bitmap = parser.decodeToBitmap(new AssetPathDecoder(),
                new ImageParser.DecodeParam(assetPath));
        return recognizeImage0(scheduler, bitmap, assetPath, cb);
    }
    public void recognizeImageFromAssets(Scheduler scheduler, final List<String> assetPaths,
                                         final Callback cb, final Runnable end) {
        ImageParser parser = new ImageParser(MP_INPUT_SIZE * RATIO, MP_INPUT_SIZE * RATIO,
                Bitmap.Config.ARGB_8888, true);
        final ComposeCallback ccb = new ComposeCallback(cb, new Callback() {
            final AtomicInteger count = new AtomicInteger(assetPaths.size());
            @Override
            public void onRecognized(Bitmap bitmap, Object key, List<Human> list) {
                if(count.decrementAndGet() == 0 && end != null){
                    end.run();
                }
            }
        });
        AssetPathDecoder pathDecoder = new AssetPathDecoder();
        for (String assetPath : assetPaths){
            Bitmap bitmap = parser.decodeToBitmap(pathDecoder,
                    new ImageParser.DecodeParam(assetPath));
            recognizeImage0(scheduler, bitmap, assetPath, ccb);
        }
    }
    public int recognizeImage(Scheduler scheduler, String filePath,  Object key, final Callback cb) {
        //首次缩放之后先补齐. 然后再缩放和裁剪
        ImageParser parser = new ImageParser(MP_INPUT_SIZE * RATIO, MP_INPUT_SIZE * RATIO,
                Bitmap.Config.ARGB_8888, true);
        Bitmap bitmap = parser.parseToBitmap(filePath);
        return recognizeImage0(scheduler, bitmap, key, cb);
    }

    public int recognizeImage0(Scheduler scheduler, Bitmap bitmap, Object key,final Callback cb) {
        if(cb instanceof DebugCallback){
            ((DebugCallback) cb).debugParserImage(bitmap);
        }
        //对齐宽高. 保证人一定能被完整的保存下来
        int wh = Math.max(bitmap.getWidth(), bitmap.getHeight());
        Bitmap croppedBitmap = ImageUtils.alignWidthHeight(bitmap, wh, wh, MP_INPUT_SIZE, MP_INPUT_SIZE);

        /*int cropSize = MP_INPUT_SIZE;
        Matrix mat = ImageUtils.getTransformationMatrix(bitmap.getWidth(), bitmap.getHeight(),
                cropSize, cropSize, 0, true);
        Bitmap croppedBitmap = Bitmap.createBitmap(cropSize, cropSize, Bitmap.Config.ARGB_8888);
        Canvas canvas = new Canvas(croppedBitmap);
        canvas.drawBitmap(bitmap, mat, new Paint());*/
        return recognizeImage(scheduler, croppedBitmap, key, cb);
    }

    public int recognizeImage(Scheduler scheduler, final Bitmap bitmap, final Object key, final Callback cb) {
        if(cb instanceof DebugCallback){
            ((DebugCallback) cb).debugCropImage(bitmap);
        }
        if(scheduler == null){
            List<Classifier.Recognition> list = detector.recognizeImage(bitmap);
            List<Human> humans = null;
            if (list.size() > 0) {
                Classifier.Recognition reg = list.get(0);
                if (reg.humans.size() > 0) {
                    humans = reg.humans;
                }
            }
            cb.onRecognized(bitmap, key, humans);
            return 0;
        }else {
            final int id = mId.incrementAndGet();
            Disposable task = scheduler.newWorker().schedule(new Runnable() {
                @Override
                public void run() {
                    List<Classifier.Recognition> list = detector.recognizeImage(bitmap);
                    List<Human> humans = null;
                    if (list.size() > 0) {
                        Classifier.Recognition reg = list.get(0);
                        if (reg.humans.size() > 0) {
                            humans = reg.humans;
                        }
                    }
                    cb.onRecognized(bitmap, key, humans);
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
    private class AssetPathDecoder implements ImageParser.IDecoder{
        @Override
        public Bitmap decode(ImageParser.DecodeParam decodeParam, BitmapFactory.Options options) {
            try {
                InputStream in = mActivity.getAssets().open(decodeParam.pathName);
                return BitmapFactory.decodeStream(in, null, options);
            } catch (IOException e) {
                throw new RuntimeException(e);
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
        public void onRecognized(Bitmap bitmap, Object key, List<Human> list) {
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
        void onRecognized(Bitmap bitmap, Object key, List<Human> list);
    }
    public interface DebugCallback{
        void debugParserImage(Bitmap bitmap);
        void debugCropImage(Bitmap bitmap);
    }
}
