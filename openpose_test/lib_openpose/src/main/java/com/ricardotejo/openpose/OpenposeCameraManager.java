package com.ricardotejo.openpose;

import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.Point;
import android.graphics.Rect;
import android.graphics.Typeface;
import android.os.SystemClock;
import android.util.Size;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.Surface;

import androidx.annotation.IdRes;
import androidx.appcompat.app.AppCompatActivity;

import com.heaven7.android.lib_openpose.R;
import com.heaven7.core.util.Logger;
import com.heaven7.core.util.Toaster;
import com.heaven7.java.base.util.Predicates;
import com.ricardotejo.openpose.bean.Coord;
import com.ricardotejo.openpose.bean.Human;
import com.ricardotejo.openpose.bean.ImageHandleInfo;
import com.ricardotejo.openpose.env.BorderedText;
import com.ricardotejo.openpose.env.ImageUtils;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.Vector;

public class OpenposeCameraManager extends AbsOpenposeCameraManager{

    private static final String TAG = "OpenposeCM";
    /*private*/ static final int MP_INPUT_SIZE = 368;
    /*private*/ static final String MP_INPUT_NAME = "image";
    /*private*/ static final String MP_OUTPUT_L1 = "Openpose/MConv_Stage6_L1_5_pointwise/BatchNorm/FusedBatchNorm";
    /*private*/ static final String MP_OUTPUT_L2 = "Openpose/MConv_Stage6_L2_5_pointwise/BatchNorm/FusedBatchNorm";
    //static final String MP_MODEL_FILE = "file:///android_asset/frozen_person_model.pb";
    static final String MP_MODEL_FILE = "file:///android_asset/graph_opt.pb"; //识别率比 frozen_person_model好

    private static final boolean MAINTAIN_ASPECT = true;

    private static final float TEXT_SIZE_DIP = 10;
    private static final int HUMAN_RADIUS = 3;

    private final Paint mPaint = new Paint();

    private Integer sensorOrientation;

    private Classifier detector;

    private long lastProcessingTimeMs;
    private int lastHumansFound;
    private Bitmap rgbFrameBitmap = null;
    private Bitmap croppedBitmap = null;
    private Bitmap cropCopyBitmap = null;

    private boolean computingDetection = false;

    private long timestamp = 0;

    private Matrix frameToCropTransform;
    private Matrix cropToFrameTransform;

    private BorderedText borderedText;
    private DrawCallback drawCallback = new DrawCallback();
    private Callback callback;

    private Map<Integer, Coord> mMainCoordMap;

    public OpenposeCameraManager(AppCompatActivity ac, @IdRes int mVg_container) {
        super(ac, mVg_container);
    }

    public void setCallback(Callback callback) {
        this.callback = callback;
    }
    public void setDrawCallback(DrawCallback drawCallback) {
        if(drawCallback == null){
            throw new NullPointerException();
        }
        this.drawCallback = drawCallback;
    }
    //set main/stand coord map
    public void setMainCoordMap(Map<Integer, Coord> result){
        this.mMainCoordMap = result instanceof TreeMap ? result: new TreeMap<>(result);
    }

    @Override
    protected void processImage() {
        ++timestamp;
        final long currTimestamp = timestamp;

        // No mutex needed as this method is not reentrant.
        if (computingDetection) {
            readyForNextImage();
            return;
        }
        computingDetection = true;
        LOGGER.i("Preparing image " + currTimestamp + " for detection in bg thread.");

        rgbFrameBitmap.setPixels(getRgbBytes(), 0, previewWidth, 0, 0, previewWidth, previewHeight);

        readyForNextImage();

        final Canvas canvas = new Canvas(croppedBitmap);
        canvas.drawBitmap(rgbFrameBitmap, frameToCropTransform, null); // paint the cropped image

        runInBackground(
                new Runnable() {
                    @Override
                    public void run() {
                        //LOGGER.i("Running detection on image " + currTimestamp);
                        final long startTime = SystemClock.uptimeMillis();
                        final List<Classifier.Recognition> results = detector.recognizeImage(croppedBitmap);
                        lastProcessingTimeMs = SystemClock.uptimeMillis() - startTime;
                        Logger.d(TAG, "recognizeImage", "cost time = " + (lastProcessingTimeMs));

                        if(isDebug()){
                            //below just for debug
                            lastHumansFound = results.get(0).humans.size();

                            cropCopyBitmap = Bitmap.createBitmap(croppedBitmap);
                            final Canvas canvas = new Canvas(cropCopyBitmap);

                            draw_humans(canvas, results.get(0).humans);
                            requestRender();
                        }else {
                            List<Human> humans = results.get(0).humans;
                            if(humans.size() > 0){
                                //for openpose no-debug. must set callback
                                long s = System.currentTimeMillis();
                                List<Integer> ids = callback.match(new TreeMap<Integer, Coord>(humans.get(0).parts));
                                Logger.d(TAG , "match", "mismatch ids = " + ids + ", cost time(ms) = " + (System.currentTimeMillis() - s));
                                if(!Predicates.isEmpty(ids)){
                                    //permit
                                    cropCopyBitmap = Bitmap.createBitmap(croppedBitmap);
                                    final Canvas canvas = new Canvas(cropCopyBitmap);
                                    drawMismatch(canvas, humans.get(0).parts, ids);
                                    //TODO 放大到识别之前的图像？
                                    requestRender();
                                }else {
                                    Logger.d(TAG, "match ok");
                                }
                            }else {
                                Toaster.show(mActivity,  R.string.lib_openpose_recognize_failed, Gravity.CENTER);
                            }
                        }
                        computingDetection = false;
                    }
                });
    }

    @Override
    protected void onPreviewSizeChosen(Size size, int rotation) {
        final float textSizePx =
                TypedValue.applyDimension(
                        TypedValue.COMPLEX_UNIT_DIP, TEXT_SIZE_DIP, mActivity.getResources().getDisplayMetrics());
        borderedText = new BorderedText(textSizePx);
        borderedText.setTypeface(Typeface.MONOSPACE);

        int cropSize = MP_INPUT_SIZE;

        // Configure the detector
        detector = TensorFlowPoseDetector.create(
                mActivity.getAssets(),
                MP_MODEL_FILE,
                MP_INPUT_SIZE,
                MP_INPUT_NAME,
                new String[]{MP_OUTPUT_L1, MP_OUTPUT_L2}
        );

        previewWidth = size.getWidth();
        previewHeight = size.getHeight();

        sensorOrientation = rotation - getScreenOrientation();
        LOGGER.i("Camera orientation relative to screen canvas: %d", sensorOrientation);

        LOGGER.i("Initializing at size %dx%d", previewWidth, previewHeight);
        rgbFrameBitmap = Bitmap.createBitmap(previewWidth, previewHeight, Bitmap.Config.ARGB_8888);
        croppedBitmap = Bitmap.createBitmap(cropSize, cropSize, Bitmap.Config.ARGB_8888);

        frameToCropTransform =
                ImageUtils.getTransformationMatrix(
                        previewWidth, previewHeight,
                        cropSize, cropSize,
                        sensorOrientation, MAINTAIN_ASPECT);
        //mapVectors等函数是映射成需要的坐标
        cropToFrameTransform = new Matrix();
        frameToCropTransform.invert(cropToFrameTransform);//逆矩阵

        addCallback(new Draw0());
    }

    public void draw_humans(Canvas canvas, List<Human> human_list) {
        //def draw_humans(img, human_list):
        // image_h, image_w = img_copied.shape[:2]

        //    for human in human_list:
        for (Human human : human_list) {
            drawMismatch(canvas, human.parts, Collections.<Integer>emptyList());
        }
        //    return img_copied
    }
    public void drawMismatch(Map<Integer, Coord> parts, ImageHandleInfo info, List<Integer> mismatches){
        int cp = Common.CocoPart.values().length;
        Point[] centers = new Point[cp];

        Coord part_coord;
        float x, y;
        boolean match;
        int[] pair;
        Point p;

        drawCallback.beginDraw();
        for (Map.Entry<Integer, Coord> en : parts.entrySet()){
            int idx = en.getKey();
            part_coord = en.getValue();
            float w0 = info.finalWidth * info.scale2;
            float h0 = info.finalHeight * info.scale2;
            x = part_coord.x * w0;
            y = part_coord.y * h0;
            x = (x - info.compensateWidth / 2) * info.scale1;
            y = (y - info.compensateHeight / 2) * info.scale1;
            p = centers[idx] = new Point((int)(x + 0.5f), (int)(y + 0.5f));

            match = mismatches.isEmpty() || !mismatches.contains(idx);
            //mPaint.setColor(Common.CocoColors[i.index]);
            mPaint.setColor(drawCallback.getPointColor(idx, match, Common.CocoColors[idx]));
            mPaint.setStyle(Paint.Style.FILL);
            drawCallback.drawPoint(p.x, p.y, drawCallback.getPointRadius(match), mPaint);
           // canvas.drawCircle(center.x, center.y, drawCallback.getPointRadius(match), mPaint);
        }
        Set<Integer> part_idxs = parts.keySet();
        for (int pair_order = 0; pair_order < Common.CocoPairsRender.length; pair_order++) {
            pair = Common.CocoPairsRender[pair_order];
            //if pair[0] not in part_idxs or pair[1] not in part_idxs:
            if (!part_idxs.contains(pair[0]) || !part_idxs.contains(pair[1])) {
                continue;
            }
            match = mismatches.isEmpty() || (!mismatches.contains(pair[0]) && !mismatches.contains(pair[1]));

            //img_copied = cv2.line(img_copied, centers[pair[0]], centers[pair[1]], CocoColors[pair_order], 3)
            //mPaint.setColor(Common.CocoColors[pair_order]);
            mPaint.setColor(drawCallback.getConcatColor(pair[0], pair[1], match, Common.CocoColors[pair_order]));
            mPaint.setStrokeWidth(drawCallback.getConcatStrokeWidth(match));
            mPaint.setStyle(Paint.Style.STROKE);
            drawCallback.drawConcatLine(centers[pair[0]].x, centers[pair[0]].y, centers[pair[1]].x, centers[pair[1]].y, mPaint);
           // canvas.drawLine(centers[pair[0]].x, centers[pair[0]].y, centers[pair[1]].x, centers[pair[1]].y, mPaint);
        }

        drawCallback.endDraw();
    }
    public void drawMismatch(Canvas canvas, Map<Integer, Coord> parts, List<Integer> mismatches){
        int cp = Common.CocoPart.values().length;
        int image_w = canvas.getWidth();
        int image_h = canvas.getHeight();

        Point[] centers = new Point[cp]; //识别到的人体关键点坐标Point. 数组index代表关键点的id.
        //part_idxs = human.keys()
        Set<Integer> part_idxs = parts.keySet();

        LOGGER.i("COORD =====================================");
        //# draw point
        //for i in range(CocoPart.Background.value):
        Coord part_coord;
        Point center;
        boolean match;
        int[] pair;
        for (Common.CocoPart i : Common.CocoPart.values()) {
            //if i not in part_idxs:
            if (!part_idxs.contains(i.index)) {
                LOGGER.w("COORD %s, NULL, NULL", i.toString());
                continue;
            }
            //part_coord = human[i][1]
            part_coord = parts.get(i.index);
            //center = (int(part_coord[0] * image_w + 0.5), int(part_coord[1] * image_h + 0.5))
            //映射到canvas的坐标
            center = new Point((int) (part_coord.x * image_w + 0.5f), (int) (part_coord.y * image_h + 0.5f));
            //centers[i] = center
            centers[i.index] = center;

            //cv2.circle(img_copied, center, 3, CocoColors[i], thickness=3, lineType=8, shift=0)
            match = mismatches.isEmpty() || !mismatches.contains(i.index);
            //mPaint.setColor(Common.CocoColors[i.index]);
            mPaint.setColor(drawCallback.getPointColor(i.index, match, Common.CocoColors[i.index]));
            mPaint.setStyle(Paint.Style.FILL);
            canvas.drawCircle(center.x, center.y, drawCallback.getPointRadius(match), mPaint);

            LOGGER.i("COORD %s, %f, %f", i.toString(), part_coord.x, part_coord.y);
        }

        //# draw line 连接的关键点.
        //for pair_order, pair in enumerate(CocoPairsRender):
        for (int pair_order = 0; pair_order < Common.CocoPairsRender.length; pair_order++) {
            pair = Common.CocoPairsRender[pair_order];
            //if pair[0] not in part_idxs or pair[1] not in part_idxs:
            if (!part_idxs.contains(pair[0]) || !part_idxs.contains(pair[1])) {
                continue;
            }
            match = mismatches.isEmpty() || (!mismatches.contains(pair[0]) && !mismatches.contains(pair[1]));

            //img_copied = cv2.line(img_copied, centers[pair[0]], centers[pair[1]], CocoColors[pair_order], 3)
            //mPaint.setColor(Common.CocoColors[pair_order]);
            mPaint.setColor(drawCallback.getConcatColor(pair[0], pair[1], match, Common.CocoColors[pair_order]));
            mPaint.setStrokeWidth(drawCallback.getConcatStrokeWidth(match));
            mPaint.setStyle(Paint.Style.STROKE);

            canvas.drawLine(centers[pair[0]].x, centers[pair[0]].y, centers[pair[1]].x, centers[pair[1]].y, mPaint);
        }
    }

    @Override
    public void setDebug(final boolean debug) {
        super.setDebug(debug);
        if(detector != null){
            detector.enableStatLogging(debug);
        }
    }
    protected int getScreenOrientation() {
        switch (mActivity.getWindowManager().getDefaultDisplay().getRotation()) {
            case Surface.ROTATION_270:
                return 270;
            case Surface.ROTATION_180:
                return 180;
            case Surface.ROTATION_90:
                return 90;
            default:
                return 0;
        }
    }
    public interface Callback{
        /**
         * match the movement and return mismatched coords.
         * @param result the recognize result
         * @return the mismatch ids
         */
        List<Integer> match(Map<Integer, Coord> result);
    }

    public static class DrawCallback{

        public int getPointColor(int id, boolean match, int defaultColor){
            return defaultColor;
        }
        public int getConcatColor(int id1, int id2, boolean match, int defaultColor){
            return defaultColor;
        }
        public float getPointRadius(boolean match){
            return HUMAN_RADIUS;
        }
        public float getConcatStrokeWidth(boolean match){
            return HUMAN_RADIUS;
        }
        //used for draw to raw image
        public void drawPoint(float x, float y, float radius, Paint mPaint) {

        }
        public void beginDraw() {

        }
        public void endDraw() {

        }
        public void drawConcatLine(int x, int y, int x1, int y1, Paint mPaint) {

        }
    }
    private class Draw0 implements OverlayView.DrawCallback{
        final Paint pp = new Paint();
        final Matrix matrix = new Matrix();
        final Vector<String> lines = new Vector<String>();
        @Override
        public void drawCallback(final Canvas canvas) {
            final Bitmap copy = cropCopyBitmap;
            if (copy == null) {
                return;
            }
            matrix.reset();
            if (!isDebug()) {
                matrix.postScale(2, 2);
                canvas.drawBitmap(copy, matrix, null);
                return;
            }
            final int backgroundColor = Color.rgb(0, 255, 0);
            //canvas.drawColor(backgroundColor);
            pp.setColor(backgroundColor);
            final float scaleFactor = 2;
            canvas.drawRect(new Rect(5, 5,
                    15 + copy.getWidth() * (int)scaleFactor,
                    15 + copy.getHeight() * (int)scaleFactor), pp);

            matrix.postScale(scaleFactor, scaleFactor);

            // RT: Position of the preview canvas
            matrix.postTranslate(10, 10);
            //matrix.postTranslate(
            //        canvas.getWidth() - copy.getWidth() * scaleFactor,
            //        canvas.getHeight() - copy.getHeight() * scaleFactor);
            canvas.drawBitmap(copy, matrix, null);

            if (detector != null) {
                final String statString = detector.getStatString();
                final String[] statLines = statString.split("\n");
                for (final String line : statLines) {
                    lines.add(line);
                }
            }
            lines.add("");

            lines.add("Frame: " + previewWidth + "x" + previewHeight);
            lines.add("Crop: " + copy.getWidth() + "x" + copy.getHeight());
            lines.add("View: " + canvas.getWidth() + "x" + canvas.getHeight());
            lines.add("Rotation: " + sensorOrientation);
            lines.add("Inference time: " + lastProcessingTimeMs + "ms");
            lines.add("Humans found: " + lastHumansFound);
            lines.clear();

            //borderedText.drawLines(canvas, 10, canvas.getHeight() - 10, lines); // bottom
            borderedText.drawLinesTop(canvas, copy.getWidth() * scaleFactor + 30, 10, lines); // top-right
        }
    }
}
