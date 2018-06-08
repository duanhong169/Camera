package top.defaults.camera;

import android.Manifest;
import android.annotation.SuppressLint;
import android.app.Activity;
import android.content.Context;
import android.content.pm.PackageManager;
import android.content.res.Configuration;
import android.graphics.Matrix;
import android.graphics.Rect;
import android.graphics.RectF;
import android.graphics.SurfaceTexture;
import android.hardware.camera2.CameraAccessException;
import android.hardware.camera2.CameraCaptureSession;
import android.hardware.camera2.CameraCharacteristics;
import android.hardware.camera2.CameraDevice;
import android.hardware.camera2.CameraManager;
import android.hardware.camera2.CameraMetadata;
import android.hardware.camera2.CaptureFailure;
import android.hardware.camera2.CaptureRequest;
import android.hardware.camera2.TotalCaptureResult;
import android.hardware.camera2.params.MeteringRectangle;
import android.hardware.camera2.params.StreamConfigurationMap;
import android.media.MediaRecorder;
import android.os.Build;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.Message;
import android.support.annotation.NonNull;
import android.support.v4.content.ContextCompat;
import android.util.Size;
import android.util.SparseIntArray;
import android.view.MotionEvent;
import android.view.Surface;
import android.view.TextureView;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;

import top.defaults.logger.Logger;

public class Camera2Photographer implements InternalPhotographer {

    private static final int CALLBACK_ON_DEVICE_CONFIGURED = 1;
    private static final int CALLBACK_ON_PREVIEW_STARTED = 2;
    private static final int CALLBACK_ON_PREVIEW_STOPPED = 3;
    private static final int CALLBACK_ON_START_RECORDING = 4;
    private static final int CALLBACK_ON_PAUSE_RECORDING = 5;
    private static final int CALLBACK_ON_RESUME_RECORDING = 6;
    private static final int CALLBACK_ON_FINISH_RECORDING = 7;
    private static final int CALLBACK_ON_ERROR = 8;

    private Activity activityContext;
    private AutoFitTextureView textureView;
    private CallbackHandler callbackHandler;
    private boolean isInitialized;
    private boolean isManualFocusEngaged;

    private HandlerThread backgroundThread;
    private Handler backgroundHandler;
    private int lensFacing;
    private Size previewSize;
    private Size[] supportedVideoSizes;
    private Size videoSize;
    private Semaphore cameraOpenCloseLock = new Semaphore(1);
    private Integer sensorOrientation;
    private CameraDevice cameraDevice;
    private MediaRecorder mediaRecorder;
    private CameraCaptureSession previewSession;
    private CaptureRequest.Builder previewBuilder;
    private CameraCharacteristics characteristics;
    private String nextVideoAbsolutePath;
    private boolean isRecordingVideo;

    private static final int FOCUS_AREA_SIZE = 150;
    private static final int SENSOR_ORIENTATION_DEFAULT_DEGREES = 90;
    private static final int SENSOR_ORIENTATION_INVERSE_DEGREES = 270;
    private static final SparseIntArray DEFAULT_ORIENTATIONS = new SparseIntArray();
    private static final SparseIntArray INVERSE_ORIENTATIONS = new SparseIntArray();

    static {
        DEFAULT_ORIENTATIONS.append(Surface.ROTATION_0, 90);
        DEFAULT_ORIENTATIONS.append(Surface.ROTATION_90, 0);
        DEFAULT_ORIENTATIONS.append(Surface.ROTATION_180, 270);
        DEFAULT_ORIENTATIONS.append(Surface.ROTATION_270, 180);
    }

    static {
        INVERSE_ORIENTATIONS.append(Surface.ROTATION_0, 270);
        INVERSE_ORIENTATIONS.append(Surface.ROTATION_90, 180);
        INVERSE_ORIENTATIONS.append(Surface.ROTATION_180, 90);
        INVERSE_ORIENTATIONS.append(Surface.ROTATION_270, 0);
    }

    private static final ArrayList<String> RECORD_VIDEO_PERMISSIONS = new ArrayList<>(3);

    static {
        RECORD_VIDEO_PERMISSIONS.add(Manifest.permission.CAMERA);
        RECORD_VIDEO_PERMISSIONS.add(Manifest.permission.RECORD_AUDIO);
        RECORD_VIDEO_PERMISSIONS.add(Manifest.permission.WRITE_EXTERNAL_STORAGE);
    }

    @Override
    public void initWithViewfinder(Activity activity, AutoFitTextureView textureView) {
        this.activityContext = activity;
        this.textureView = textureView;
        callbackHandler = new CallbackHandler(activityContext);
        isInitialized = true;
    }

    private void throwIfNotInitialized() {
        if (!isInitialized) {
            throw new RuntimeException("Camera2Photographer is not initialized");
        }
    }

    private void throwIfNoMediaRecorder() {
        if (mediaRecorder == null) {
            throw new RuntimeException("MediaRecorder is not initialized");
        }
    }

    @Override
    public Size[] getSupportedRecordSize() {
        return supportedVideoSizes;
    }

    @Override
    public void startPreview(Map<String, Object> params) {
        throwIfNotInitialized();
        for (String permission: RECORD_VIDEO_PERMISSIONS) {
            int permissionCheck = ContextCompat.checkSelfPermission(activityContext, permission);
            if (permissionCheck != PackageManager.PERMISSION_GRANTED) {
                callbackHandler.onError(new Error(Error.ERROR_PERMISSION, "Unsatisfied permission: " + permission));
                return;
            }
        }

        if (params != null) {
            int newLensFacing = Utils.getInt(params, Keys.LENS_FACING, CameraCharacteristics.LENS_FACING_BACK);
            if (newLensFacing != lensFacing) {
                lensFacing = newLensFacing;
                // clear the video size
                videoSize = null;
            }
        }
        startBackgroundThread();
        if (textureView.isAvailable()) {
            openCamera(textureView.getWidth(), textureView.getHeight());
        } else {
            textureView.setSurfaceTextureListener(surfaceTextureListener);
        }
    }

    @Override
    public void stopPreview() {
        throwIfNotInitialized();
        closeCamera();
        stopBackgroundThread();
    }

    @Override
    public void setVideoSize(Size size) {
        videoSize = size;
        stopPreview();
        startPreview(null);
    }

    @Override
    public void startRecording(MediaRecorderConfigurator configurator) {
        throwIfNoMediaRecorder();
        if (cameraDevice == null || !textureView.isAvailable() || previewSize == null) {
            callbackHandler.onError(new Error(Error.ERROR_CAMERA));
            return;
        }
        try {
            closePreviewSession();
            setUpMediaRecorder(configurator);
            SurfaceTexture texture = textureView.getSurfaceTexture();
            assert texture != null;
            texture.setDefaultBufferSize(previewSize.getWidth(), previewSize.getHeight());
            previewBuilder = cameraDevice.createCaptureRequest(CameraDevice.TEMPLATE_RECORD);
            List<Surface> surfaces = new ArrayList<>();

            // Set up Surface for the camera preview
            Surface previewSurface = new Surface(texture);
            surfaces.add(previewSurface);
            previewBuilder.addTarget(previewSurface);

            // Set up Surface for the MediaRecorder
            Surface recorderSurface = mediaRecorder.getSurface();
            surfaces.add(recorderSurface);
            previewBuilder.addTarget(recorderSurface);

            // Start a capture session
            // Once the session starts, we can update the UI and start recording
            cameraDevice.createCaptureSession(surfaces, new CameraCaptureSession.StateCallback() {

                @Override
                public void onConfigured(@NonNull CameraCaptureSession cameraCaptureSession) {
                    previewSession = cameraCaptureSession;
                    updatePreview();
                    activityContext.runOnUiThread(() -> {
                        isRecordingVideo = true;
                        mediaRecorder.start();
                        callbackHandler.onStartRecording();
                    });
                }

                @Override
                public void onConfigureFailed(@NonNull CameraCaptureSession cameraCaptureSession) {
                    callbackHandler.onError(new Error(Error.ERROR_CAMERA));
                }
            }, backgroundHandler);
        } catch (CameraAccessException | IOException e) {
            e.printStackTrace();
        }
    }

    private void setUpMediaRecorder(MediaRecorderConfigurator configurator) throws IOException {
        if (configurator == null || configurator.useDefaultConfigs()) {
            mediaRecorder.setAudioSource(MediaRecorder.AudioSource.MIC);
            mediaRecorder.setVideoSource(MediaRecorder.VideoSource.SURFACE);
            mediaRecorder.setOutputFormat(MediaRecorder.OutputFormat.MPEG_4);
            if (nextVideoAbsolutePath == null || nextVideoAbsolutePath.isEmpty()) {
                nextVideoAbsolutePath = getVideoFilePath(activityContext);
            }
            mediaRecorder.setOutputFile(nextVideoAbsolutePath);
            mediaRecorder.setVideoEncodingBitRate(10000000);
            mediaRecorder.setVideoFrameRate(30);
            mediaRecorder.setVideoSize(videoSize.getWidth(), videoSize.getHeight());
            mediaRecorder.setVideoEncoder(MediaRecorder.VideoEncoder.H264);
            mediaRecorder.setAudioEncoder(MediaRecorder.AudioEncoder.AAC);
        }

        if (configurator != null) {
            configurator.configure(mediaRecorder);
        }

        int rotation = activityContext.getWindowManager().getDefaultDisplay().getRotation();
        switch (sensorOrientation) {
            case SENSOR_ORIENTATION_DEFAULT_DEGREES:
                mediaRecorder.setOrientationHint(DEFAULT_ORIENTATIONS.get(rotation));
                break;
            case SENSOR_ORIENTATION_INVERSE_DEGREES:
                mediaRecorder.setOrientationHint(INVERSE_ORIENTATIONS.get(rotation));
                break;
        }
        mediaRecorder.prepare();
    }

    private String getVideoFilePath(Context context) {
        final File dir = context.getExternalFilesDir(null);
        return (dir == null ? "" : (dir.getAbsolutePath() + "/"))
                + System.currentTimeMillis() + ".mp4";
    }

    @Override
    public void pauseRecording() {
        throwIfNoMediaRecorder();
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            mediaRecorder.pause();
            callbackHandler.onPauseRecording();
        } else {
            callbackHandler.onError(new Error(Error.ERROR_UNSUPPORTED_OPERATION));
        }
    }

    @Override
    public void resumeRecording() {
        throwIfNoMediaRecorder();
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            mediaRecorder.resume();
            callbackHandler.onResumeRecording();
        } else {
            callbackHandler.onError(new Error(Error.ERROR_UNSUPPORTED_OPERATION));
        }
    }

    @Override
    public void finishRecording() {
        if (!isRecordingVideo) return;
        throwIfNoMediaRecorder();
        isRecordingVideo = false;
        mediaRecorder.stop();
        mediaRecorder.reset();
        callbackHandler.onFinishRecording(nextVideoAbsolutePath);
        nextVideoAbsolutePath = null;
        realStartPreview();
    }

    @Override
    public void setOnEventListener(OnEventListener listener) {
        throwIfNotInitialized();
        callbackHandler.setOnEventListener(listener);
    }

    private void startBackgroundThread() {
        backgroundThread = new HandlerThread("CameraBackground");
        backgroundThread.start();
        backgroundHandler = new Handler(backgroundThread.getLooper());
    }

    private void stopBackgroundThread() {
        backgroundThread.quitSafely();
        try {
            backgroundThread.join();
            backgroundThread = null;
            backgroundHandler = null;
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
    }

    private CameraDevice.StateCallback stateCallback = new CameraDevice.StateCallback() {

        @Override
        public void onOpened(@NonNull CameraDevice cameraDevice) {
            Camera2Photographer.this.cameraDevice = cameraDevice;
            realStartPreview();
            cameraOpenCloseLock.release();
            if (textureView != null) {
                configureTransform(textureView.getWidth(), textureView.getHeight());
            }
        }

        @Override
        public void onDisconnected(@NonNull CameraDevice cameraDevice) {
            cameraOpenCloseLock.release();
            cameraDevice.close();
            Camera2Photographer.this.cameraDevice = null;
            callbackHandler.onPreviewStopped();
        }

        @Override
        public void onError(@NonNull CameraDevice cameraDevice, int error) {
            cameraOpenCloseLock.release();
            cameraDevice.close();
            Camera2Photographer.this.cameraDevice = null;
            callbackHandler.onError(new Error(Error.ERROR_CAMERA));
        }

    };

    private TextureView.SurfaceTextureListener surfaceTextureListener
            = new TextureView.SurfaceTextureListener() {

        @Override
        public void onSurfaceTextureAvailable(SurfaceTexture surfaceTexture,
                                              int width, int height) {
            openCamera(width, height);
        }

        @Override
        public void onSurfaceTextureSizeChanged(SurfaceTexture surfaceTexture,
                                                int width, int height) {
            configureTransform(width, height);
        }

        @Override
        public boolean onSurfaceTextureDestroyed(SurfaceTexture surfaceTexture) {
            return true;
        }

        @Override
        public void onSurfaceTextureUpdated(SurfaceTexture surfaceTexture) {
        }

    };

    @SuppressLint("ClickableViewAccessibility")
    private void realStartPreview() {
        if (cameraDevice == null || !textureView.isAvailable() || previewSize == null) {
            return;
        }
        try {
            closePreviewSession();
            SurfaceTexture texture = textureView.getSurfaceTexture();
            assert texture != null;
            texture.setDefaultBufferSize(previewSize.getWidth(), previewSize.getHeight());
            previewBuilder = cameraDevice.createCaptureRequest(CameraDevice.TEMPLATE_PREVIEW);

            Surface previewSurface = new Surface(texture);
            previewBuilder.addTarget(previewSurface);

            textureView.setOnTouchListener((v, event) -> {
                final int actionMasked = event.getActionMasked();
                if (actionMasked != MotionEvent.ACTION_DOWN) {
                    return false;
                }
                if (isManualFocusEngaged) {
                    Logger.d("Manual focus already engaged");
                    return true;
                }

                final Rect sensorArraySize = characteristics.get(CameraCharacteristics.SENSOR_INFO_ACTIVE_ARRAY_SIZE);
                if (sensorArraySize == null) return false;
                int rotation = activityContext.getWindowManager().getDefaultDisplay().getRotation();

                final int x;
                final int y;

                int degree = DEFAULT_ORIENTATIONS.get(rotation);
                switch (sensorOrientation) {
                    case SENSOR_ORIENTATION_DEFAULT_DEGREES:
                        degree = DEFAULT_ORIENTATIONS.get(rotation);
                        break;
                    case SENSOR_ORIENTATION_INVERSE_DEGREES:
                        degree = INVERSE_ORIENTATIONS.get(rotation);
                        break;
                }

                switch (degree) {
                    case 0:
                        x = (int)((event.getX() / (float)v.getWidth())  * (float)sensorArraySize.width());
                        y = (int)((event.getY() / (float)v.getHeight()) * (float)sensorArraySize.height());
                        break;
                    case 180:
                        x = (int)((1 - (event.getX() / (float)v.getWidth()))  * (float)sensorArraySize.width());
                        y = (int)((1 - (event.getY() / (float)v.getHeight())) * (float)sensorArraySize.height());
                        break;
                    case 270:
                        x = (int)((1- (event.getY() / (float)v.getHeight())) * (float)sensorArraySize.width());
                        y = (int)((event.getX() / (float)v.getWidth())  * (float)sensorArraySize.height());
                        break;
                    case 90:
                    default:
                        x = (int)((event.getY() / (float)v.getHeight()) * (float)sensorArraySize.width());
                        y = (int)((1 - (event.getX() / (float)v.getWidth()))  * (float)sensorArraySize.height());
                        break;
                }

                MeteringRectangle focusAreaTouch = new MeteringRectangle(Math.max(x - FOCUS_AREA_SIZE,  0),
                        Math.max(y - FOCUS_AREA_SIZE, 0),
                        FOCUS_AREA_SIZE  * 2,
                        FOCUS_AREA_SIZE * 2,
                        MeteringRectangle.METERING_WEIGHT_MAX - 1);

                CameraCaptureSession.CaptureCallback captureCallbackHandler = new CameraCaptureSession.CaptureCallback() {
                    @Override
                    public void onCaptureCompleted(@NonNull CameraCaptureSession session, @NonNull CaptureRequest request, @NonNull TotalCaptureResult result) {
                        super.onCaptureCompleted(session, request, result);
                        isManualFocusEngaged = false;

                        if (request.getTag() == "FOCUS_TAG") {
                            //the focus trigger is complete -
                            //resume repeating (preview surface will get frames), clear AF trigger
                            previewBuilder.set(CaptureRequest.CONTROL_AF_TRIGGER, null);
                            try {
                                previewSession.setRepeatingRequest(previewBuilder.build(), null, null);
                            } catch (CameraAccessException e) {
                                e.printStackTrace();
                            }
                        }
                    }

                    @Override
                    public void onCaptureFailed(@NonNull CameraCaptureSession session, @NonNull CaptureRequest request, @NonNull CaptureFailure failure) {
                        super.onCaptureFailed(session, request, failure);
                        Logger.e("Manual AF failure: " + failure);
                        isManualFocusEngaged = false;
                    }
                };

                try {
                    previewSession.stopRepeating();
                } catch (CameraAccessException e) {
                    e.printStackTrace();
                }

                // cancel any existing AF trigger (repeated touches, etc.)
                previewBuilder.set(CaptureRequest.CONTROL_AF_TRIGGER, CameraMetadata.CONTROL_AF_TRIGGER_CANCEL);
                previewBuilder.set(CaptureRequest.CONTROL_AF_MODE, CaptureRequest.CONTROL_AF_MODE_OFF);
                try {
                    previewSession.capture(previewBuilder.build(), captureCallbackHandler, backgroundHandler);
                } catch (CameraAccessException e) {
                    e.printStackTrace();
                }

                // add a new AF trigger with focus region
                Integer maxRegionsAf = characteristics.get(CameraCharacteristics.CONTROL_MAX_REGIONS_AF);
                if (maxRegionsAf != null && maxRegionsAf >= 1) {
                    previewBuilder.set(CaptureRequest.CONTROL_AF_REGIONS, new MeteringRectangle[]{focusAreaTouch});
                }
                previewBuilder.set(CaptureRequest.CONTROL_MODE, CameraMetadata.CONTROL_MODE_AUTO);
                previewBuilder.set(CaptureRequest.CONTROL_AF_MODE, CaptureRequest.CONTROL_AF_MODE_AUTO);
                previewBuilder.set(CaptureRequest.CONTROL_AF_TRIGGER, CameraMetadata.CONTROL_AF_TRIGGER_START);
                previewBuilder.setTag("FOCUS_TAG"); //we'll capture this later for resuming the preview

                // then we ask for a single request (not repeating!)
                try {
                    previewSession.capture(previewBuilder.build(), captureCallbackHandler, backgroundHandler);
                } catch (CameraAccessException e) {
                    e.printStackTrace();
                }
                isManualFocusEngaged = true;

                return true;
            });

            cameraDevice.createCaptureSession(Collections.singletonList(previewSurface),
                    new CameraCaptureSession.StateCallback() {

                        @Override
                        public void onConfigured(@NonNull CameraCaptureSession session) {
                            previewSession = session;
                            updatePreview();
                            callbackHandler.onPreviewStarted();
                        }

                        @Override
                        public void onConfigureFailed(@NonNull CameraCaptureSession session) {
                            callbackHandler.onError(new Error(Error.ERROR_CAMERA));
                        }
                    }, backgroundHandler);
        } catch (CameraAccessException e) {
            e.printStackTrace();
        }
    }

    private void updatePreview() {
        if (cameraDevice == null) {
            return;
        }
        try {
            previewBuilder.set(CaptureRequest.CONTROL_MODE, CameraMetadata.CONTROL_MODE_AUTO);
            previewSession.setRepeatingRequest(previewBuilder.build(), null, backgroundHandler);
        } catch (CameraAccessException e) {
            e.printStackTrace();
        }
    }

    @SuppressLint("MissingPermission")
    private void openCamera(int width, int height) {
        if (activityContext.isFinishing()) {
            return;
        }
        CameraManager manager = (CameraManager) activityContext.getSystemService(Context.CAMERA_SERVICE);
        if (manager == null) {
            return;
        }
        try {
            if (!cameraOpenCloseLock.tryAcquire(2500, TimeUnit.MILLISECONDS)) {
                throw new RuntimeException("Time out waiting to lock camera opening.");
            }

            for (String cameraId: manager.getCameraIdList()) {
                CameraCharacteristics characteristics = manager.getCameraCharacteristics(cameraId);

                Integer facing = characteristics.get(CameraCharacteristics.LENS_FACING);
                if (facing != null && facing != lensFacing) {
                    continue;
                }

                this.characteristics = characteristics;
                // Choose the sizes for camera preview and video recording
                StreamConfigurationMap map = characteristics
                        .get(CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP);
                sensorOrientation = characteristics.get(CameraCharacteristics.SENSOR_ORIENTATION);
                if (map == null) {
                    throw new RuntimeException("Cannot get available preview/video sizes");
                }
                supportedVideoSizes = map.getOutputSizes(MediaRecorder.class);
                if (videoSize == null) {
                    videoSize = chooseVideoSize(supportedVideoSizes);
                }
                previewSize = chooseOptimalSize(map.getOutputSizes(SurfaceTexture.class),
                        width, height, videoSize);

                int orientation = activityContext.getResources().getConfiguration().orientation;
                if (orientation == Configuration.ORIENTATION_LANDSCAPE) {
                    textureView.setAspectRatio(previewSize.getWidth(), previewSize.getHeight());
                } else {
                    textureView.setAspectRatio(previewSize.getHeight(), previewSize.getWidth());
                }
                callbackHandler.onDeviceConfigured();

                mediaRecorder = new MediaRecorder();
                manager.openCamera(cameraId, stateCallback, null);
            }
        } catch (CameraAccessException e) {
            callbackHandler.onError(new Error(Error.ERROR_CAMERA, "Cannot access the camera."));
        } catch (NullPointerException e) {
            // Currently an NPE is thrown when the Camera2API is used but not supported on the
            // device this code runs.
            callbackHandler.onError(new Error(Error.ERROR_CAMERA, "This device doesn\'t support Camera2 API."));
        } catch (InterruptedException e) {
            throw new RuntimeException("Interrupted while trying to lock camera opening.");
        }
    }

    private static Size chooseVideoSize(Size[] choices) {
        for (Size size : choices) {
            if (size.getWidth() == size.getHeight() * 4 / 3 && size.getHeight() <= 1080) {
                return size;
            }
        }
        Logger.e("Couldn't find any suitable video size");
        return choices[choices.length - 1];
    }

    private static Size chooseOptimalSize(Size[] choices, int width, int height, Size aspectRatio) {
        // Collect the supported resolutions that are at least as big as the preview Surface
        List<Size> bigEnough = new ArrayList<>();
        int w = aspectRatio.getWidth();
        int h = aspectRatio.getHeight();
        for (Size option : choices) {
            if (option.getHeight() == option.getWidth() * h / w &&
                    option.getWidth() >= width && option.getHeight() >= height) {
                bigEnough.add(option);
            }
        }

        // Pick the smallest of those, assuming we found any
        if (bigEnough.size() > 0) {
            return Collections.min(bigEnough, new CompareSizesByArea());
        } else {
            Logger.e("Couldn't find any suitable preview size");
            return choices[0];
        }
    }

    private static class CompareSizesByArea implements Comparator<Size> {

        @Override
        public int compare(Size lhs, Size rhs) {
            // We cast here to ensure the multiplications won't overflow
            return Long.signum((long) lhs.getWidth() * lhs.getHeight() -
                    (long) rhs.getWidth() * rhs.getHeight());
        }
    }

    private void configureTransform(int viewWidth, int viewHeight) {
        if (textureView == null || previewSize == null) {
            return;
        }
        int rotation = activityContext.getWindowManager().getDefaultDisplay().getRotation();
        Matrix matrix = new Matrix();
        RectF viewRect = new RectF(0, 0, viewWidth, viewHeight);
        RectF bufferRect = new RectF(0, 0, previewSize.getHeight(), previewSize.getWidth());
        float centerX = viewRect.centerX();
        float centerY = viewRect.centerY();
        if (rotation == Surface.ROTATION_90 || rotation == Surface.ROTATION_270) {
            bufferRect.offset(centerX - bufferRect.centerX(), centerY - bufferRect.centerY());
            matrix.setRectToRect(viewRect, bufferRect, Matrix.ScaleToFit.FILL);
            float scale = Math.max(
                    (float) viewHeight / previewSize.getHeight(),
                    (float) viewWidth / previewSize.getWidth());
            matrix.postScale(scale, scale, centerX, centerY);
            matrix.postRotate(90 * (rotation - 2), centerX, centerY);
        }
        textureView.setTransform(matrix);
    }

    private void closeCamera() {
        try {
            cameraOpenCloseLock.acquire();
            closePreviewSession();
            if (cameraDevice != null) {
                cameraDevice.close();
                cameraDevice = null;
            }
            if (mediaRecorder != null) {
                mediaRecorder.release();
                mediaRecorder = null;
            }
        } catch (InterruptedException e) {
            throw new RuntimeException("Interrupted while trying to lock camera closing.");
        } finally {
            cameraOpenCloseLock.release();
        }
    }

    private void closePreviewSession() {
        if (previewSession != null) {
            previewSession.close();
            previewSession = null;
        }
    }

    private static class CallbackHandler extends Handler {

        private OnEventListener onEventListener;

        CallbackHandler(Context context) {
            super(context.getMainLooper());
        }

        void setOnEventListener(OnEventListener listener) {
            onEventListener = listener;
        }

        @Override
        public void handleMessage(Message msg) {
            if (onEventListener == null) {
                return;
            }
            Logger.d("handleMessage: " + msg.what);
            switch (msg.what) {
                case CALLBACK_ON_DEVICE_CONFIGURED:
                    onEventListener.onDeviceConfigured();
                    break;
                case CALLBACK_ON_PREVIEW_STARTED:
                    onEventListener.onPreviewStarted();
                    break;
                case CALLBACK_ON_PREVIEW_STOPPED:
                    onEventListener.onPreviewStopped();
                    break;
                case CALLBACK_ON_START_RECORDING:
                    onEventListener.onStartRecording();
                    break;
                case CALLBACK_ON_PAUSE_RECORDING:
                    onEventListener.onPauseRecording();
                    break;
                case CALLBACK_ON_RESUME_RECORDING:
                    onEventListener.onResumeRecording();
                    break;
                case CALLBACK_ON_FINISH_RECORDING:
                    onEventListener.onFinishRecording((String) msg.obj);
                    break;
                case CALLBACK_ON_ERROR:
                    onEventListener.onError((Error) msg.obj);
                    break;
                default:
                    break;
            }
        }

        void onDeviceConfigured() {
            Message.obtain(this, CALLBACK_ON_DEVICE_CONFIGURED).sendToTarget();
        }

        void onPreviewStarted() {
            Message.obtain(this, CALLBACK_ON_PREVIEW_STARTED).sendToTarget();
        }

        void onPreviewStopped() {
            Message.obtain(this, CALLBACK_ON_PREVIEW_STOPPED).sendToTarget();
        }

        void onStartRecording() {
            Message.obtain(this, CALLBACK_ON_START_RECORDING).sendToTarget();
        }

        void onPauseRecording() {
            Message.obtain(this, CALLBACK_ON_PAUSE_RECORDING).sendToTarget();
        }

        void onResumeRecording() {
            Message.obtain(this, CALLBACK_ON_RESUME_RECORDING).sendToTarget();
        }

        void onFinishRecording(String filePath) {
            Message.obtain(this, CALLBACK_ON_FINISH_RECORDING, filePath).sendToTarget();
        }

        void onError(final Error error) {
            Message.obtain(this, CALLBACK_ON_ERROR, error).sendToTarget();
            error.printStackTrace();
        }
    }
}
