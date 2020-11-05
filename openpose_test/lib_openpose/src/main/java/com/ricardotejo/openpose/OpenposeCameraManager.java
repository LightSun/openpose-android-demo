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
import android.view.Surface;

import androidx.annotation.IdRes;
import androidx.appcompat.app.AppCompatActivity;

import com.ricardotejo.openpose.bean.Coord;
import com.ricardotejo.openpose.bean.Human;
import com.ricardotejo.openpose.env.BorderedText;
import com.ricardotejo.openpose.env.ImageUtils;

import java.util.List;
import java.util.Set;
import java.util.Vector;

public class OpenposeCameraManager extends AbsOpenposeCameraManager{

    /*private*/ static final int MP_INPUT_SIZE = 368;
    /*private*/ static final String MP_INPUT_NAME = "image";
    /*private*/ static final String MP_OUTPUT_L1 = "Openpose/MConv_Stage6_L1_5_pointwise/BatchNorm/FusedBatchNorm";
    /*private*/ static final String MP_OUTPUT_L2 = "Openpose/MConv_Stage6_L2_5_pointwise/BatchNorm/FusedBatchNorm";
    /*private*/ static final String MP_MODEL_FILE = "file:///android_asset/frozen_person_model.pb";

    private static final boolean MAINTAIN_ASPECT = true;

    private static final float TEXT_SIZE_DIP = 10;
    private static final int HUMAN_RADIUS = 3;

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

    public OpenposeCameraManager(AppCompatActivity ac, @IdRes int mVg_container) {
        super(ac, mVg_container);
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
                        LOGGER.i("Running detection on image " + currTimestamp);
                        final long startTime = SystemClock.uptimeMillis();

                        final List<Classifier.Recognition> results = detector.recognizeImage(croppedBitmap);

                        lastProcessingTimeMs = SystemClock.uptimeMillis() - startTime;
                        lastHumansFound = results.get(0).humans.size();
                        LOGGER.i("Running detection on image (DONE) in " + lastProcessingTimeMs);

                        cropCopyBitmap = Bitmap.createBitmap(croppedBitmap);
                        final Canvas canvas = new Canvas(cropCopyBitmap);
                        draw_humans(canvas, results.get(0).humans);

                        requestRender();
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

        if(isDebug()){
            addCallback(
                    new OverlayView.DrawCallback() {
                        @Override
                        public void drawCallback(final Canvas canvas) {
                            if (!isDebug()) {
                                return;
                            }
                            final Bitmap copy = cropCopyBitmap;
                            if (copy == null) {
                                return;
                            }

                            final int backgroundColor = Color.rgb(0, 255, 0);
                            //canvas.drawColor(backgroundColor);
                            Paint pp = new Paint();
                            pp.setColor(backgroundColor);
                            final float scaleFactor = 2;
                            canvas.drawRect(new Rect(5, 5,
                                    15 + copy.getWidth() * (int)scaleFactor,
                                    15 + copy.getHeight() * (int)scaleFactor), pp);

                            final Matrix matrix = new Matrix();

                            matrix.postScale(scaleFactor, scaleFactor);

                            // RT: Position of the preview canvas
                            matrix.postTranslate(10, 10);
                            //matrix.postTranslate(
                            //        canvas.getWidth() - copy.getWidth() * scaleFactor,
                            //        canvas.getHeight() - copy.getHeight() * scaleFactor);
                            canvas.drawBitmap(copy, matrix, null);

                            final Vector<String> lines = new Vector<String>();
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

                            //borderedText.drawLines(canvas, 10, canvas.getHeight() - 10, lines); // bottom
                            borderedText.drawLinesTop(canvas, copy.getWidth() * scaleFactor + 30, 10, lines); // top-right
                        }
                    });
        }
    }

    public void draw_humans(Canvas canvas, List<Human> human_list) {
        //def draw_humans(img, human_list):
        // image_h, image_w = img_copied.shape[:2]
        int cp = Common.CocoPart.values().length;
        int image_w = canvas.getWidth();
        int image_h = canvas.getHeight();

        Paint paint = new Paint();
        //    for human in human_list:
        for (Human human : human_list) {
            Point[] centers = new Point[cp]; //识别到的人体关键点坐标Point. 数组index代表关键点的id.
            //part_idxs = human.keys()
            Set<Integer> part_idxs = human.parts.keySet();

            LOGGER.i("COORD =====================================");
            //# draw point
            //for i in range(CocoPart.Background.value):
            for (Common.CocoPart i : Common.CocoPart.values()) {
                //if i not in part_idxs:
                if (!part_idxs.contains(i.index)) {
                    LOGGER.w("COORD %s, NULL, NULL", i.toString());
                    continue;
                }
                //part_coord = human[i][1]
                Coord part_coord = human.parts.get(i.index);
                //center = (int(part_coord[0] * image_w + 0.5), int(part_coord[1] * image_h + 0.5))
                //映射到canvas的坐标
                Point center = new Point((int) (part_coord.x * image_w + 0.5f), (int) (part_coord.y * image_h + 0.5f));
                //centers[i] = center
                centers[i.index] = center;

                //cv2.circle(img_copied, center, 3, CocoColors[i], thickness=3, lineType=8, shift=0)
                paint.setColor(Color.rgb(Common.CocoColors[i.index][0], Common.CocoColors[i.index][1], Common.CocoColors[i.index][2]));
                paint.setStyle(Paint.Style.FILL);
                canvas.drawCircle(center.x, center.y, HUMAN_RADIUS, paint);

                LOGGER.i("COORD %s, %f, %f", i.toString(), part_coord.x, part_coord.y);
            }

            //# draw line 连接的关键点.
            //for pair_order, pair in enumerate(CocoPairsRender):
            for (int pair_order = 0; pair_order < Common.CocoPairsRender.length; pair_order++) {
                int[] pair = Common.CocoPairsRender[pair_order];
                //if pair[0] not in part_idxs or pair[1] not in part_idxs:
                if (!part_idxs.contains(pair[0]) || !part_idxs.contains(pair[1])) {
                    continue;
                }

                //img_copied = cv2.line(img_copied, centers[pair[0]], centers[pair[1]], CocoColors[pair_order], 3)
                paint.setColor(Color.rgb(Common.CocoColors[pair_order][0], Common.CocoColors[pair_order][1], Common.CocoColors[pair_order][2]));
                paint.setStrokeWidth(HUMAN_RADIUS);
                paint.setStyle(Paint.Style.STROKE);

                canvas.drawLine(centers[pair[0]].x, centers[pair[0]].y, centers[pair[1]].x, centers[pair[1]].y, paint);
            }
        }
        //    return img_copied
    }

    @Override
    public void setDebug(final boolean debug) {
        super.setDebug(debug);
        detector.enableStatLogging(debug);
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

    public interface DrawCallback{
        int getPointColor(int id);
        int getConcatColor(int id1, int id2);
    }
}
