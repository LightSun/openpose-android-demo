package com.heaven7.android.openpose_test;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapShader;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;
import android.graphics.Shader;

import com.bumptech.glide.Glide;
import com.bumptech.glide.load.Transformation;
import com.bumptech.glide.load.engine.Resource;
import com.bumptech.glide.load.engine.bitmap_recycle.BitmapPool;
import com.bumptech.glide.load.resource.bitmap.BitmapResource;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;

/**
 * from internet
 */
public class BorderRoundTransformation implements Transformation<Bitmap> {

    /**
     * 用一个整形表示哪些边角需要加圆角边框
     * 例如：0b1000,表示左上角需要加圆角边框
     * 0b1110 表示左上右上右下需要加圆角边框
     * 0b0000表示不加圆形边框
     */
    private BitmapPool mBitmapPool;
    private float mRadius;
    private int mMargin;

    private float mBorderWidth;
    private int mBorderColor;
    private int mCornerPos;

    public BorderRoundTransformation(Context context, float radius, int margin, float mBorderWidth, int mBorderColor){
        this(context, radius, margin, mBorderWidth, mBorderColor, 0b1111);
    }
    public BorderRoundTransformation(Context context, float radius, int margin, float mBorderWidth, int mBorderColor, int position) {
        mBitmapPool = Glide.get(context).getBitmapPool();
        mRadius = radius;
        mMargin = margin;
        this.mBorderColor = mBorderColor;
        this.mBorderWidth = mBorderWidth;
        this.mCornerPos = position;
    }

    @Override
    public Resource<Bitmap> transform(Context context, Resource<Bitmap> resource, int outWidth, int outHeight) {
        Bitmap source = resource.get();

        int width = source.getWidth();
        int height = source.getHeight();

        Bitmap bitmap = mBitmapPool.get(width, height, Bitmap.Config.ARGB_8888);
        /*if (bitmap == null) {
            bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888);
        }*/

        Canvas canvas = new Canvas(bitmap);
        Paint paint = new Paint();
        paint.setAntiAlias(true);
        paint.setShader(new BitmapShader(source, Shader.TileMode.CLAMP, Shader.TileMode.CLAMP));//设置要绘制的图形

        Paint borderPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        borderPaint.setColor(mBorderColor);
        borderPaint.setStyle(Paint.Style.STROKE);
        borderPaint.setStrokeWidth(mBorderWidth);

        drawRoundRect(canvas, paint, width, height, borderPaint);
        return BitmapResource.obtain(bitmap, mBitmapPool);
    }

    private void drawRoundRect(Canvas canvas, Paint paint, float width, float height, Paint borderPaint) {
        float right = width - mMargin;
        float bottom = height - mMargin;
        float halfBorder = mBorderWidth / 2;
        Path path = new Path();

        float[] pos = new float[8];
        int shift = mCornerPos;

        int index = 3;

        while (index >= 0) {//设置四个边角的弧度半径
            pos[2 * index + 1] = ((shift & 1) > 0) ? mRadius : 0;
            pos[2 * index] = ((shift & 1) > 0) ? mRadius : 0;
            shift = shift >> 1;
            index--;
        }

        path.addRoundRect(new RectF(mMargin + halfBorder, mMargin + halfBorder, right - halfBorder, bottom - halfBorder),
                pos, Path.Direction.CW);

        canvas.drawPath(path, paint);

        canvas.drawPath(path, borderPaint);//

    }

    @Override
    public void updateDiskCacheKey(MessageDigest md) {
        //must
        md.update(getId().getBytes(StandardCharsets.UTF_8));
    }

    //@Override
    public String getId() {
        return "RoundedTransformation(radius=" + mRadius + ", margin=" + mMargin + ", mBorderWidth"
                + mBorderWidth + ", mBorderColor" + mBorderColor + "mCornerPos" + mCornerPos + ")";
    }
}