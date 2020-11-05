package com.ricardotejo.openpose;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Matrix;

import androidx.appcompat.app.AppCompatActivity;

import com.heaven7.core.util.ImageParser;
import com.heaven7.java.base.util.Scheduler;
import com.ricardotejo.openpose.bean.Human;
import com.ricardotejo.openpose.env.ImageUtils;

import java.io.IOException;
import java.io.InputStream;
import java.util.List;

import static com.ricardotejo.openpose.OpenposeCameraManager.MP_INPUT_NAME;
import static com.ricardotejo.openpose.OpenposeCameraManager.MP_INPUT_SIZE;
import static com.ricardotejo.openpose.OpenposeCameraManager.MP_MODEL_FILE;
import static com.ricardotejo.openpose.OpenposeCameraManager.MP_OUTPUT_L1;
import static com.ricardotejo.openpose.OpenposeCameraManager.MP_OUTPUT_L2;

public final class OpenposeDetector {

    private final AppCompatActivity mActivity;
    private final Classifier detector;
    private final Callback callback;
   //private List<Disposable>

    public OpenposeDetector(AppCompatActivity activity, Callback callback) {
        this.mActivity = activity;
        detector = TensorFlowPoseDetector.create(
                activity.getAssets(),
                MP_MODEL_FILE,
                MP_INPUT_SIZE,
                MP_INPUT_NAME,
                new String[]{MP_OUTPUT_L1, MP_OUTPUT_L2}
        );
        this.callback = callback;
    }
    public void recognizeImageFromAssets(String assetPath, Scheduler scheduler) {
        ImageParser parser = new ImageParser(MP_INPUT_SIZE * 3, MP_INPUT_SIZE * 3,
                Bitmap.Config.ARGB_8888, true);
        Bitmap bitmap = parser.decodeToBitmap(new AssetPathDecoder(),
                new ImageParser.DecodeParam(assetPath));
        recognizeImage0(bitmap, scheduler);
    }
    public void recognizeImage(String filePath, Scheduler scheduler) {
        ImageParser parser = new ImageParser(MP_INPUT_SIZE * 3, MP_INPUT_SIZE * 3,
                Bitmap.Config.ARGB_8888, true);
        Bitmap bitmap = parser.parseToBitmap(filePath);
        recognizeImage0(bitmap, scheduler);
    }

    public void recognizeImage0(Bitmap bitmap,Scheduler scheduler) {
        int cropSize = MP_INPUT_SIZE;

        Matrix mat = ImageUtils.getTransformationMatrix(bitmap.getWidth(), bitmap.getHeight(),
                cropSize, cropSize, 0, true);
        Bitmap croppedBitmap = Bitmap.createBitmap(cropSize, cropSize, Bitmap.Config.ARGB_8888);
        Canvas canvas = new Canvas(croppedBitmap);
        canvas.drawBitmap(bitmap, mat, null);
        recognizeImage(croppedBitmap, scheduler);
    }

    public void recognizeImage(final Bitmap bitmap, Scheduler scheduler) {
        scheduler.newWorker().schedule(new Runnable() {
            @Override
            public void run() {
                List<Classifier.Recognition> list = detector.recognizeImage(bitmap);
                List<Human> humans = null;
                if(list.size() > 0){
                    Classifier.Recognition reg = list.get(0);
                    if(reg.humans.size() > 0){
                        humans = reg.humans;
                    }
                }
                callback.onRecognized(bitmap, humans);
            }
        });
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
    public interface Callback{
        void onRecognized(Bitmap bitmap, List<Human> list);
    }

}
