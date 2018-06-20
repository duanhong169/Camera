package top.defaults.camera;

import android.Manifest;
import android.annotation.SuppressLint;
import android.app.Activity;
import android.content.Context;
import android.content.pm.PackageManager;
import android.content.res.Configuration;
import android.graphics.ImageFormat;
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
import android.hardware.camera2.CaptureResult;
import android.hardware.camera2.TotalCaptureResult;
import android.hardware.camera2.params.MeteringRectangle;
import android.hardware.camera2.params.StreamConfigurationMap;
import android.media.ImageReader;
import android.media.MediaRecorder;
import android.os.Build;
import android.os.Environment;
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
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;

import top.defaults.logger.Logger;

import static top.defaults.camera.Error.ERROR_CAMERA;

public class Camera2Photographer implements InternalPhotographer {

    private static final int CALLBACK_ON_DEVICE_CONFIGURED = 1;
    private static final int CALLBACK_ON_PREVIEW_STARTED = 2;
    private static final int CALLBACK_ON_PREVIEW_STOPPED = 3;
    private static final int CALLBACK_ON_START_RECORDING = 4;
    private static final int CALLBACK_ON_PAUSE_RECORDING = 5;
    private static final int CALLBACK_ON_RESUME_RECORDING = 6;
    private static final int CALLBACK_ON_FINISH_RECORDING = 7;
    private static final int CALLBACK_ON_SHOT_FINISHED = 8;
    private static final int CALLBACK_ON_ERROR = 9;

    private Activity activityContext;
    private CameraPreview preview;
    private AutoFitTextureView textureView;
    private CallbackHandler callbackHandler;

    private boolean isInitialized;
    private boolean isManualFocusEngaged;

    private Map<String, Object> params;
    private int mode = MODE_IMAGE;
    private int lensFacing;
    private boolean isFlashSupported;

    private HandlerThread backgroundThread;
    private Handler backgroundHandler;

    private Size previewSize;
    private Size[] supportedImageSizes;
    private Size imageSize;
    private Size[] supportedVideoSizes;
    private Size videoSize;

    private Semaphore cameraOpenCloseLock = new Semaphore(1);
    private Integer sensorOrientation;
    private CameraDevice cameraDevice;
    private ImageReader imageReader;
    private MediaRecorder mediaRecorder;
    private CameraCaptureSession previewSession;
    private CaptureRequest.Builder previewBuilder;
    private CameraCharacteristics characteristics;

    private String nextImageAbsolutePath;
    private String nextVideoAbsolutePath;
    private boolean isRecordingVideo;

    private static final int STATE_PREVIEW = 0;
    private static final int STATE_WAITING_LOCK = 1;
    private static final int STATE_WAITING_PRECAPTURE = 2;
    private static final int STATE_WAITING_NON_PRECAPTURE = 3;
    private static final int STATE_PICTURE_TAKEN = 4;
    private int shotState = STATE_PREVIEW;

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
    public void initWithViewfinder(Activity activity, CameraPreview preview) {
        this.activityContext = activity;
        this.preview = preview;
        this.textureView = preview.getTextureView();
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
    public Size[] getSupportedImageSizes() {
        return supportedImageSizes;
    }

    @Override
    public Size[] getSupportedVideoSizes() {
        return supportedVideoSizes;
    }

    @Override
    public Map<String, Object> getCurrentParams() {
        return params;
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

        this.params = params;
        int newLensFacing = Utils.getInt(params, Keys.LENS_FACING, CameraCharacteristics.LENS_FACING_BACK);
        if (newLensFacing != lensFacing) {
            lensFacing = newLensFacing;
            // clear the image/video size
            imageSize = null;
            videoSize = null;
        }

        mode = Utils.getInt(params, Keys.MODE, MODE_IMAGE);

        startBackgroundThread();
        if (textureView.isAvailable()) {
            openCamera(textureView.getWidth(), textureView.getHeight());
        } else {
            textureView.setSurfaceTextureListener(surfaceTextureListener);
        }
    }

    @Override
    public void restartPreview(Map<String, Object> params) {
        stopPreview();
        Map<String, Object> mergedParams = new HashMap<>();
        if (this.params != null) {
            mergedParams.putAll(this.params);
        }
        if (params != null) {
            mergedParams.putAll(params);
        }
        startPreview(mergedParams);
    }

    @Override
    public void stopPreview() {
        throwIfNotInitialized();
        closeCamera();
        stopBackgroundThread();
    }

    private CameraCaptureSession.CaptureCallback shotCallback = new CameraCaptureSession.CaptureCallback() {

        private void process(CaptureResult result) {
            switch (shotState) {
                case STATE_PREVIEW: {
                    // We have nothing to do when the camera preview is working normally.
                    break;
                }
                case STATE_WAITING_LOCK: {
                    Integer afState = result.get(CaptureResult.CONTROL_AF_STATE);
                    if (afState == null) {
                        captureStillPicture();
                    } else if (CaptureResult.CONTROL_AF_STATE_FOCUSED_LOCKED == afState ||
                            CaptureResult.CONTROL_AF_STATE_NOT_FOCUSED_LOCKED == afState) {
                        Integer aeState = result.get(CaptureResult.CONTROL_AE_STATE);
                        if (aeState == null || aeState == CaptureResult.CONTROL_AE_STATE_CONVERGED) {
                            shotState = STATE_PICTURE_TAKEN;
                            captureStillPicture();
                        } else {
                            runPrecaptureSequence();
                        }
                    }
                    break;
                }
                case STATE_WAITING_PRECAPTURE: {
                    Integer aeState = result.get(CaptureResult.CONTROL_AE_STATE);
                    if (aeState == null ||
                            aeState == CaptureResult.CONTROL_AE_STATE_PRECAPTURE ||
                            aeState == CaptureRequest.CONTROL_AE_STATE_FLASH_REQUIRED) {
                        shotState = STATE_WAITING_NON_PRECAPTURE;
                    }
                    break;
                }
                case STATE_WAITING_NON_PRECAPTURE: {
                    Integer aeState = result.get(CaptureResult.CONTROL_AE_STATE);
                    if (aeState == null || aeState != CaptureResult.CONTROL_AE_STATE_PRECAPTURE) {
                        shotState = STATE_PICTURE_TAKEN;
                        captureStillPicture();
                    }
                    break;
                }
            }
        }

        @Override
        public void onCaptureProgressed(@NonNull CameraCaptureSession session,
                                        @NonNull CaptureRequest request,
                                        @NonNull CaptureResult partialResult) {
            process(partialResult);
        }

        @Override
        public void onCaptureCompleted(@NonNull CameraCaptureSession session,
                                       @NonNull CaptureRequest request,
                                       @NonNull TotalCaptureResult result) {
            process(result);
        }

    };

    private void runPrecaptureSequence() {
        try {
            previewBuilder.set(CaptureRequest.CONTROL_AE_PRECAPTURE_TRIGGER,
                    CaptureRequest.CONTROL_AE_PRECAPTURE_TRIGGER_START);
            // Tell #shotCallback to wait for the precapture sequence to be set.
            shotState = STATE_WAITING_PRECAPTURE;
            previewSession.capture(previewBuilder.build(), shotCallback, backgroundHandler);
        } catch (CameraAccessException e) {
            e.printStackTrace();
            callbackHandler.onError(Utils.errorFromThrowable(e));
        }
    }

    private void captureStillPicture() {
        try {
            if (cameraDevice == null) {
                return;
            }

            final CaptureRequest.Builder captureBuilder =
                    cameraDevice.createCaptureRequest(CameraDevice.TEMPLATE_STILL_CAPTURE);
            captureBuilder.addTarget(imageReader.getSurface());

            captureBuilder.set(CaptureRequest.CONTROL_AF_MODE,
                    CaptureRequest.CONTROL_AF_MODE_CONTINUOUS_PICTURE);
            setAutoFlash(captureBuilder);
            captureBuilder.set(CaptureRequest.JPEG_ORIENTATION, getOrientation());

            CameraCaptureSession.CaptureCallback shotCallback
                    = new CameraCaptureSession.CaptureCallback() {
                @Override
                public void onCaptureCompleted(@NonNull CameraCaptureSession session,
                                               @NonNull CaptureRequest request,
                                               @NonNull TotalCaptureResult result) {
                    callbackHandler.onShotFinished(nextImageAbsolutePath);
                    updatePreview();
                }

                @Override
                public void onCaptureFailed(@NonNull CameraCaptureSession session,
                                            @NonNull CaptureRequest request,
                                            @NonNull CaptureFailure failure) {
                    callbackHandler.onError(new Error(ERROR_CAMERA));
                    updatePreview();
                }
            };

            previewSession.stopRepeating();
            previewSession.capture(captureBuilder.build(), shotCallback, null);
        } catch (CameraAccessException e) {
            e.printStackTrace();
            callbackHandler.onError(Utils.errorFromThrowable(e));
        }
    }

    private void setAutoFlash(CaptureRequest.Builder requestBuilder) {
        if (isFlashSupported) {
            requestBuilder.set(CaptureRequest.CONTROL_AE_MODE,
                    CaptureRequest.CONTROL_AE_MODE_ON_AUTO_FLASH);
        }
    }

    private int getOrientation() {
        int rotation = activityContext.getWindowManager().getDefaultDisplay().getRotation();
        int degree = DEFAULT_ORIENTATIONS.get(rotation);
        switch (sensorOrientation) {
            case SENSOR_ORIENTATION_DEFAULT_DEGREES:
                degree = DEFAULT_ORIENTATIONS.get(rotation);
                break;
            case SENSOR_ORIENTATION_INVERSE_DEGREES:
                degree = INVERSE_ORIENTATIONS.get(rotation);
                break;
        }
        return degree;
    }

    @Override
    public void shot() {
        try {
            nextImageAbsolutePath = getImageFilePath();
            previewBuilder.set(CaptureRequest.CONTROL_AF_TRIGGER, CameraMetadata.CONTROL_AF_TRIGGER_START);

            shotState = STATE_WAITING_LOCK;
            previewSession.capture(previewBuilder.build(), shotCallback, backgroundHandler);
        } catch (CameraAccessException e) {
            e.printStackTrace();
            callbackHandler.onError(Utils.errorFromThrowable(e));
        }
    }

    @Override
    public void setImageSize(Size size) {
        imageSize = size;
        restartPreview(null);
    }

    @Override
    public void setVideoSize(Size size) {
        videoSize = size;
        restartPreview(null);
    }

    @Override
    public void startRecording(MediaRecorderConfigurator configurator) {
        throwIfNoMediaRecorder();
        if (cameraDevice == null || !textureView.isAvailable() || previewSize == null) {
            callbackHandler.onError(new Error(ERROR_CAMERA));
            return;
        }
        try {
            closePreviewSession();
            setUpMediaRecorder(configurator);
            SurfaceTexture texture = textureView.getSurfaceTexture();
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
                    callbackHandler.onError(new Error(ERROR_CAMERA));
                }
            }, backgroundHandler);
        } catch (CameraAccessException | IOException e) {
            e.printStackTrace();
            callbackHandler.onError(Utils.errorFromThrowable(e));
        }
    }

    private void setUpMediaRecorder(MediaRecorderConfigurator configurator) throws IOException {
        if (configurator == null || configurator.useDefaultConfigs()) {
            mediaRecorder.setAudioSource(MediaRecorder.AudioSource.MIC);
            mediaRecorder.setVideoSource(MediaRecorder.VideoSource.SURFACE);
            mediaRecorder.setOutputFormat(MediaRecorder.OutputFormat.MPEG_4);
            nextVideoAbsolutePath = getVideoFilePath();
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

        mediaRecorder.setOrientationHint(getOrientation());
        mediaRecorder.prepare();
    }

    private String getImageFilePath() {
        return getFilePath(".jpg");
    }

    private String getVideoFilePath() {
        return getFilePath(".mp4");
    }

    private String getFilePath(String fileSuffix) {
        final File dir = new File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DCIM) + "/Camera/");
        return dir.getAbsolutePath() + "/" + System.currentTimeMillis() + fileSuffix;
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
            callbackHandler.onError(Utils.errorFromThrowable(e));
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
            callbackHandler.onError(new Error(ERROR_CAMERA));
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

    private void realStartPreview() {
        if (cameraDevice == null || !textureView.isAvailable() || previewSize == null) {
            return;
        }
        try {
            closePreviewSession();
            SurfaceTexture texture = textureView.getSurfaceTexture();
            texture.setDefaultBufferSize(previewSize.getWidth(), previewSize.getHeight());
            previewBuilder = cameraDevice.createCaptureRequest(CameraDevice.TEMPLATE_PREVIEW);

            Surface previewSurface = new Surface(texture);
            previewBuilder.addTarget(previewSurface);

            List<Surface> surfaces = new ArrayList<>();
            surfaces.add(previewSurface);
            if (mode == Photographer.MODE_IMAGE) {
                surfaces.add(imageReader.getSurface());
            }
            cameraDevice.createCaptureSession(surfaces,
                    new CameraCaptureSession.StateCallback() {

                        @Override
                        public void onConfigured(@NonNull CameraCaptureSession session) {
                            previewSession = session;
                            updatePreview();
                            callbackHandler.onPreviewStarted();
                        }

                        @Override
                        public void onConfigureFailed(@NonNull CameraCaptureSession session) {
                            callbackHandler.onError(new Error(ERROR_CAMERA));
                        }
                    }, backgroundHandler);

            setupTapToFocus();
        } catch (CameraAccessException e) {
            e.printStackTrace();
            callbackHandler.onError(Utils.errorFromThrowable(e));
        }
    }

    @SuppressLint("ClickableViewAccessibility")
    private void setupTapToFocus() {
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

            final int eventX = (int) event.getX();
            final int eventY = (int) event.getY();

            final int focusX;
            final int focusY;

            int degree = getOrientation();

            switch (degree) {
                case 0:
                    focusX = (int)((eventX / (float)v.getWidth())  * (float)sensorArraySize.width());
                    focusY = (int)((eventY / (float)v.getHeight()) * (float)sensorArraySize.height());
                    break;
                case 180:
                    focusX = (int)((1 - (eventX / (float)v.getWidth()))  * (float)sensorArraySize.width());
                    focusY = (int)((1 - (eventY / (float)v.getHeight())) * (float)sensorArraySize.height());
                    break;
                case 270:
                    focusX = (int)((1- (eventY / (float)v.getHeight())) * (float)sensorArraySize.width());
                    focusY = (int)((eventX / (float)v.getWidth())  * (float)sensorArraySize.height());
                    break;
                case 90:
                default:
                    focusX = (int)((eventY / (float)v.getHeight()) * (float)sensorArraySize.width());
                    focusY = (int)((1 - (eventX / (float)v.getWidth()))  * (float)sensorArraySize.height());
                    break;
            }

            MeteringRectangle focusAreaTouch = new MeteringRectangle(Math.max(focusX - FOCUS_AREA_SIZE,  0),
                    Math.max(focusY - FOCUS_AREA_SIZE, 0),
                    FOCUS_AREA_SIZE  * 2,
                    FOCUS_AREA_SIZE * 2,
                    MeteringRectangle.METERING_WEIGHT_MAX - 1);

            CameraCaptureSession.CaptureCallback focusCallbackHandler = new CameraCaptureSession.CaptureCallback() {
                @Override
                public void onCaptureCompleted(@NonNull CameraCaptureSession session, @NonNull CaptureRequest request, @NonNull TotalCaptureResult result) {
                    super.onCaptureCompleted(session, request, result);
                    isManualFocusEngaged = false;

                    if (request.getTag() == "FOCUS_TAG") {
                        // the focus trigger is complete, clear AF trigger
                        previewBuilder.set(CaptureRequest.CONTROL_AF_TRIGGER, null);
                        // resume repeating (preview surface will get frames)
                        updatePreview();
                        preview.focusFinished();
                    }
                }

                @Override
                public void onCaptureFailed(@NonNull CameraCaptureSession session, @NonNull CaptureRequest request, @NonNull CaptureFailure failure) {
                    super.onCaptureFailed(session, request, failure);
                    Logger.e("Manual AF failure: " + failure);
                    isManualFocusEngaged = false;
                    preview.focusFinished();
                }
            };

            try {
                previewSession.stopRepeating();
            } catch (CameraAccessException e) {
                e.printStackTrace();
                callbackHandler.onError(Utils.errorFromThrowable(e));
            }

            // cancel any existing AF trigger (repeated touches, etc.)
            previewBuilder.set(CaptureRequest.CONTROL_AF_TRIGGER, CameraMetadata.CONTROL_AF_TRIGGER_CANCEL);
            previewBuilder.set(CaptureRequest.CONTROL_AF_MODE, CaptureRequest.CONTROL_AF_MODE_OFF);

            // add a new AF trigger with focus region
            Integer maxRegionsAf = characteristics.get(CameraCharacteristics.CONTROL_MAX_REGIONS_AF);
            if (maxRegionsAf != null && maxRegionsAf >= 1) {
                previewBuilder.set(CaptureRequest.CONTROL_AF_REGIONS, new MeteringRectangle[]{focusAreaTouch});
            }
            previewBuilder.set(CaptureRequest.CONTROL_MODE, CameraMetadata.CONTROL_MODE_AUTO);
            previewBuilder.set(CaptureRequest.CONTROL_AF_MODE, CaptureRequest.CONTROL_AF_MODE_AUTO);
            previewBuilder.set(CaptureRequest.CONTROL_AF_TRIGGER, CameraMetadata.CONTROL_AF_TRIGGER_START);
            previewBuilder.setTag("FOCUS_TAG"); // we'll capture this later for resuming the preview

            try {
                previewSession.capture(previewBuilder.build(), focusCallbackHandler, backgroundHandler);
                preview.focusRequestAt(eventX, eventY);
            } catch (CameraAccessException e) {
                e.printStackTrace();
                callbackHandler.onError(Utils.errorFromThrowable(e));
            }
            isManualFocusEngaged = true;

            return true;
        });
    }

    private void updatePreview() {
        if (cameraDevice == null) {
            return;
        }
        try {
            if (mode == Photographer.MODE_IMAGE) {
                shotState = STATE_PREVIEW;
                previewBuilder.set(CaptureRequest.CONTROL_AF_MODE, CaptureRequest.CONTROL_AF_MODE_CONTINUOUS_PICTURE);
                previewSession.setRepeatingRequest(previewBuilder.build(), shotCallback, backgroundHandler);
            } else {
                previewBuilder.set(CaptureRequest.CONTROL_MODE, CameraMetadata.CONTROL_MODE_AUTO);
                previewSession.setRepeatingRequest(previewBuilder.build(), null, backgroundHandler);
            }
        } catch (CameraAccessException e) {
            e.printStackTrace();
            callbackHandler.onError(Utils.errorFromThrowable(e));
        }
    }

    private final ImageReader.OnImageAvailableListener onImageAvailableListener
            = new ImageReader.OnImageAvailableListener() {

        @Override
        public void onImageAvailable(ImageReader reader) {
            backgroundHandler.post(new ImageSaver(reader.acquireLatestImage(), nextImageAbsolutePath));
        }

    };

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
                callbackHandler.onError(new Error(ERROR_CAMERA, "Time out waiting to lock camera opening."));
                return;
            }

            for (String cameraId: manager.getCameraIdList()) {
                CameraCharacteristics characteristics = manager.getCameraCharacteristics(cameraId);

                Integer facing = characteristics.get(CameraCharacteristics.LENS_FACING);
                if (facing != null && facing != lensFacing) {
                    continue;
                }

                this.characteristics = characteristics;
                // Choose the sizes for camera preview and video recording
                StreamConfigurationMap map = characteristics.get(CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP);

                sensorOrientation = characteristics.get(CameraCharacteristics.SENSOR_ORIENTATION);
                if (map == null) {
                    callbackHandler.onError(new Error(ERROR_CAMERA, "Cannot get available preview/video sizes"));
                    return;
                }

                Boolean available = characteristics.get(CameraCharacteristics.FLASH_INFO_AVAILABLE);
                isFlashSupported = available == null ? false : available;

                supportedImageSizes = map.getOutputSizes(ImageFormat.JPEG);
                if (imageSize == null) {
                    imageSize = chooseSize(supportedImageSizes);
                }

                supportedVideoSizes = map.getOutputSizes(MediaRecorder.class);
                if (videoSize == null) {
                    videoSize = chooseSize(supportedVideoSizes);
                }

                Size size = mode == MODE_IMAGE ? imageSize : videoSize;

                previewSize = chooseOptimalSize(map.getOutputSizes(SurfaceTexture.class),
                        width, height, size);

                int orientation = activityContext.getResources().getConfiguration().orientation;
                if (orientation == Configuration.ORIENTATION_LANDSCAPE) {
                    textureView.setAspectRatio(previewSize.getWidth(), previewSize.getHeight());
                } else {
                    textureView.setAspectRatio(previewSize.getHeight(), previewSize.getWidth());
                }
                callbackHandler.onDeviceConfigured();

                if (mode == MODE_IMAGE) {
                    imageReader = ImageReader.newInstance(imageSize.getWidth(), imageSize.getHeight(),
                            ImageFormat.JPEG,2);
                    imageReader.setOnImageAvailableListener(onImageAvailableListener, backgroundHandler);
                } else if (mode == MODE_VIDEO) {
                    mediaRecorder = new MediaRecorder();
                }
                manager.openCamera(cameraId, stateCallback, null);
            }
        } catch (CameraAccessException e) {
            callbackHandler.onError(new Error(ERROR_CAMERA, "Cannot access the camera."));
        } catch (NullPointerException e) {
            // Currently an NPE is thrown when the Camera2API is used but not supported on the
            // device this code runs.
            callbackHandler.onError(new Error(ERROR_CAMERA, "This device doesn\'t support Camera2 API."));
        } catch (InterruptedException e) {
            callbackHandler.onError(new Error(ERROR_CAMERA, "Interrupted while trying to lock camera opening."));
        }
    }

    private static Size chooseSize(Size[] choices) {
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
            callbackHandler.onError(new Error(ERROR_CAMERA, "Interrupted while trying to lock camera closing."));
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
                case CALLBACK_ON_SHOT_FINISHED:
                    onEventListener.onShotFinished((String) msg.obj);
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

        void onShotFinished(String filePath) {
            Message.obtain(this, CALLBACK_ON_SHOT_FINISHED, filePath).sendToTarget();
        }

        void onError(final Error error) {
            Message.obtain(this, CALLBACK_ON_ERROR, error).sendToTarget();
            error.printStackTrace();
        }
    }
}
