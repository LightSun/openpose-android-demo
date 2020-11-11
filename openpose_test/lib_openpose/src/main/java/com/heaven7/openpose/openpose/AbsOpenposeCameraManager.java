package com.heaven7.openpose.openpose;

import android.Manifest;
import android.content.Context;
import android.content.pm.PackageManager;
import android.hardware.Camera;
import android.hardware.camera2.CameraAccessException;
import android.hardware.camera2.CameraCharacteristics;
import android.hardware.camera2.CameraManager;
import android.hardware.camera2.params.StreamConfigurationMap;
import android.media.Image;
import android.media.ImageReader;
import android.os.Build;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.Trace;
import android.util.Size;
import android.view.View;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.fragment.app.Fragment;

import com.heaven7.android.lib_openpose.R;
import com.heaven7.openpose.openpose.env.ImageUtils;
import com.heaven7.openpose.openpose.env.Logger;

import java.lang.ref.WeakReference;
import java.nio.ByteBuffer;
import java.util.List;

public abstract class AbsOpenposeCameraManager implements ImageReader.OnImageAvailableListener, Camera.PreviewCallback {

    protected static final Logger LOGGER = new Logger();
    private static final int PERMISSIONS_REQUEST = 1;
    private static final String PERMISSION_CAMERA = Manifest.permission.CAMERA;
    private static final String PERMISSION_STORAGE = Manifest.permission.WRITE_EXTERNAL_STORAGE;
    private static final Size DESIRED_PREVIEW_SIZE = new Size(640, 480);

    protected final AppCompatActivity mActivity;
    private final int mContainerId;
    private boolean debug = true;

    private Handler handler;
    private HandlerThread handlerThread;
    private boolean useCamera2API;
    private boolean isProcessingFrame = false;
    private byte[][] yuvBytes = new byte[3][];
    private int[] rgbBytes = null; //len = w*h
    private int yRowStride;

    protected int previewWidth = 0;
    protected int previewHeight = 0;

    private Runnable postInferenceCallback;
    private Runnable imageConverter;

    private boolean mPermissionRequesting;
    private Size mDesiredPreviewSize;

    private WeakReference<Fragment> mWeakFrag;

    public AbsOpenposeCameraManager(AppCompatActivity ac, int mVg_container) {
        this.mContainerId = mVg_container;
        this.mActivity = ac;
    }

    public void show(){
        if (hasPermission()) {
            showInternal();
        } else {
            requestPermission();
        }
    }
    public void setDesiredPreviewFrameSize(int width, int height){
        mDesiredPreviewSize = new Size(width, height);
    }

    protected Size getDesiredPreviewFrameSize(){
        return mDesiredPreviewSize != null ? mDesiredPreviewSize : DESIRED_PREVIEW_SIZE;
    }

    @Override
    public void onPreviewFrame(final byte[] bytes, final Camera camera) {
        if (isProcessingFrame) {
            LOGGER.w("Dropping frame!");
            return;
        }

        try {
            // Initialize the storage bitmaps once when the resolution is known.
            if (rgbBytes == null) {
                Camera.Size previewSize = camera.getParameters().getPreviewSize();
                previewHeight = previewSize.height;
                previewWidth = previewSize.width;
                rgbBytes = new int[previewWidth * previewHeight];
                onPreviewSizeChosen(new Size(previewSize.width, previewSize.height), 90); // TODO: RT rotation : 90
            }
        } catch (final Exception e) {
            e.printStackTrace();
            return;
        }

        isProcessingFrame = true;
        yuvBytes[0] = bytes;

        yRowStride = previewWidth;

        imageConverter =
                new Runnable() {
                    @Override
                    public void run() {
                        ImageUtils.convertYUV420SPToARGB8888(bytes, previewWidth, previewHeight, rgbBytes);
                    }
                };

        postInferenceCallback =
                new Runnable() {
                    @Override
                    public void run() {
                        camera.addCallbackBuffer(bytes);
                        isProcessingFrame = false;
                    }
                };
        processImage();
    }

    @Override
    public void onImageAvailable(ImageReader reader) {
        //We need wait until we have some size from onPreviewSizeChosen
        if (previewWidth == 0 || previewHeight == 0) {
            return;
        }
        if (rgbBytes == null) {
            rgbBytes = new int[previewWidth * previewHeight];
        }
        try {
            final Image image = reader.acquireLatestImage();

            if (image == null) {
                return;
            }

            if (isProcessingFrame) {
                image.close();
                return;
            }
            isProcessingFrame = true;
            Trace.beginSection("imageAvailable");
            final Image.Plane[] planes = image.getPlanes();
            fillBytes(planes, yuvBytes);
            yRowStride = planes[0].getRowStride();
            final int uvRowStride = planes[1].getRowStride();
            final int uvPixelStride = planes[1].getPixelStride();

            imageConverter =
                    new Runnable() {
                        @Override
                        public void run() {
                            ImageUtils.convertYUV420ToARGB8888(
                                    yuvBytes[0],
                                    yuvBytes[1],
                                    yuvBytes[2],
                                    previewWidth,
                                    previewHeight,
                                    yRowStride,
                                    uvRowStride,
                                    uvPixelStride,
                                    rgbBytes);
                        }
                    };

            postInferenceCallback =
                    new Runnable() {
                        @Override
                        public void run() {
                            image.close();
                            isProcessingFrame = false;
                        }
                    };

            processImage();
        } catch (final Exception e) {
            LOGGER.e(e, "Exception!");
            Trace.endSection();
            return;
        }
        Trace.endSection();
    }
    //--------------------------- called by caller --------------------------------------------
    public void pause(){
        List<Fragment> fragments = mActivity.getSupportFragmentManager().getFragments();
        for (Fragment f : fragments){
           if(f instanceof ICameraDelegate){
               ((ICameraDelegate) f).pause();
           }
        }
    }
    public void resume(){
        List<Fragment> fragments = mActivity.getSupportFragmentManager().getFragments();
        for (Fragment f : fragments){
            if(f instanceof ICameraDelegate){
                ((ICameraDelegate) f).resume();
            }
        }
    }
    public synchronized void onResume() {
        LOGGER.d("onResume " + this);
        if(handlerThread == null){
            handlerThread = new HandlerThread("inference");
            handlerThread.start();
            handler = new Handler(handlerThread.getLooper());
        }
    }

    public synchronized void onPause() {
        LOGGER.d( "onPause " + this);

        if(mPermissionRequesting){
            return;
        }
        if(handlerThread != null){
            handlerThread.quitSafely();
            try {
                handlerThread.join();
                handlerThread = null;
                handler = null;
            } catch (final InterruptedException e) {
                e.printStackTrace();
            }
        }
    }
    public void onRequestPermissionsResult(
            final int requestCode, final String[] permissions, final int[] grantResults) {
        if (requestCode == PERMISSIONS_REQUEST) {
            if (grantResults.length > 0
                    && grantResults[0] == PackageManager.PERMISSION_GRANTED
                    && grantResults[1] == PackageManager.PERMISSION_GRANTED) {
                showInternal();
            } else {
                requestPermission();
            }
        }
    }
    //-------------------------- protected ----------------------
    protected abstract void processImage();

    protected abstract void onPreviewSizeChosen(final Size size, final int rotation);

    //---------------------------- privates ---------------------

    protected void fillBytes(final Image.Plane[] planes, final byte[][] yuvBytes) {
        // Because of the variable row stride it's not possible to know in
        // advance the actual necessary dimensions of the yuv planes.
        for (int i = 0; i < planes.length; ++i) {
            final ByteBuffer buffer = planes[i].getBuffer();
            if (yuvBytes[i] == null) {
                LOGGER.d("Initializing buffer %d at size %d", i, buffer.capacity());
                yuvBytes[i] = new byte[buffer.capacity()];
            }
            buffer.get(yuvBytes[i]);
        }
    }

    public boolean isDebug() {
        return debug;
    }

    public void requestRender() {
        Fragment fragment = mWeakFrag.get();
        View parent = fragment != null ? fragment.getView() : null;
        if(parent != null){
            final OverlayView overlay = parent.findViewById(R.id.debug_overlay);
            if (overlay != null) {
                overlay.postInvalidate();
            }
        }
    }

    public void addCallback(final OverlayView.DrawCallback callback) {
        Fragment fragment = mWeakFrag.get();
        View parent = fragment != null ? fragment.getView() : null;
        if(parent != null) {
            final OverlayView overlay = parent.findViewById(R.id.debug_overlay);
            overlay.addCallback(callback);
        }
    }

    public void setDebug(final boolean debug) {
        this.debug = debug;
    }
    protected void readyForNextImage() {
        if (postInferenceCallback != null) {
            postInferenceCallback.run();
        }
    }
    protected int[] getRgbBytes() {
        imageConverter.run();
        return rgbBytes;
    }

    protected byte[] getLuminance() {
        return yuvBytes[0];
    }

    protected synchronized void runInBackground(final Runnable r) {
        if (handler != null) {
            handler.post(r);
        }
    }
    //----------------------------------------------------------------------------------------------
    private boolean hasPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            return mActivity.checkSelfPermission(PERMISSION_CAMERA) == PackageManager.PERMISSION_GRANTED &&
                    mActivity.checkSelfPermission(PERMISSION_STORAGE) == PackageManager.PERMISSION_GRANTED;
        } else {
            return true;
        }
    }
    private void requestPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            if (mActivity.shouldShowRequestPermissionRationale(PERMISSION_CAMERA) ||
                    mActivity.shouldShowRequestPermissionRationale(PERMISSION_STORAGE)) {
                Toast.makeText(mActivity,
                        R.string.request_permission, Toast.LENGTH_LONG).show();
            }
            mPermissionRequesting = true;
            mActivity.requestPermissions(new String[]{PERMISSION_CAMERA, PERMISSION_STORAGE}, PERMISSIONS_REQUEST);
        }else {
            mPermissionRequesting = false;
        }
    }

    private String chooseCamera() {
        final CameraManager manager = (CameraManager) mActivity.getSystemService(Context.CAMERA_SERVICE);
        try {
            for (final String cameraId : manager.getCameraIdList()) {
                final CameraCharacteristics characteristics = manager.getCameraCharacteristics(cameraId);

                // We don't use a front facing camera in this sample.
                final Integer facing = characteristics.get(CameraCharacteristics.LENS_FACING);
                if (facing != null && facing == CameraCharacteristics.LENS_FACING_FRONT) {
                    continue;
                }

                final StreamConfigurationMap map =
                        characteristics.get(CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP);

                if (map == null) {
                    continue;
                }

                // Fallback to camera1 API for internal cameras that don't have full support.
                // This should help with legacy situations where using the camera2 API causes
                // distorted or otherwise broken previews.
               /* useCamera2API = (facing == CameraCharacteristics.LENS_FACING_EXTERNAL)
                        ||
                        isHardwareLevelSupported(characteristics, CameraCharacteristics.INFO_SUPPORTED_HARDWARE_LEVEL_FULL);*/
                useCamera2API = true;
                LOGGER.i("Camera API lv2?: %s", useCamera2API);
                return cameraId;
            }
        } catch (CameraAccessException e) {
            LOGGER.e(e, "Not allowed to access camera");
        }
        return null;
    }
    protected void showInternal(){
        mPermissionRequesting = false;
        LOGGER.d("start show camera fragment");
        Fragment fragment = setFragment();
        mWeakFrag = new WeakReference<>(fragment);
        mActivity.getSupportFragmentManager().beginTransaction()
                .replace(mContainerId, fragment, fragment.getClass().getSimpleName())
                .commit();
    }

    private Fragment setFragment() {
        String cameraId = chooseCamera();
      //  String cameraId = null;

        Fragment fragment;
        if (useCamera2API) {
            CameraConnectionFragment camera2Fragment =
                    CameraConnectionFragment.newInstance(
                            new CameraConnectionFragment.ConnectionCallback() {
                                @Override
                                public void onPreviewSizeChosen(final Size size, final int rotation) {
                                    previewHeight = size.getHeight();
                                    previewWidth = size.getWidth();
                                    AbsOpenposeCameraManager.this.onPreviewSizeChosen(size, rotation);
                                }
                            },
                            this,
                            getDesiredPreviewFrameSize());

            camera2Fragment.setCamera(cameraId);
            fragment = camera2Fragment;
        } else {
            fragment = new LegacyCameraConnectionFragment(this, getDesiredPreviewFrameSize());
        }
        return fragment;
    }
}
