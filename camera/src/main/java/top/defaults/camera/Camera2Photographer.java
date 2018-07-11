package top.defaults.camera;

import android.Manifest;
import android.annotation.SuppressLint;
import android.app.Activity;
import android.content.Context;
import android.content.pm.PackageManager;
import android.content.res.Configuration;
import android.graphics.ImageFormat;
import android.graphics.Rect;
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
import android.hardware.camera2.params.StreamConfigurationMap;
import android.media.ImageReader;
import android.media.MediaRecorder;
import android.os.Build;
import android.os.Handler;
import android.os.HandlerThread;
import android.support.annotation.NonNull;
import android.support.v4.content.ContextCompat;
import android.util.SparseIntArray;
import android.view.MotionEvent;
import android.view.OrientationEventListener;
import android.view.Surface;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.SortedSet;
import java.util.TreeSet;

public class Camera2Photographer implements InternalPhotographer {
    // we don't use sizes larger than 2160p, since MediaRecorder
    // cannot handle such a high-resolution video.
    private static final int MAX_VIDEO_SIZE = 3840 * 2160;

    private static final SparseIntArray INTERNAL_FACINGS = new SparseIntArray();

    static {
        INTERNAL_FACINGS.put(Values.FACING_BACK, CameraCharacteristics.LENS_FACING_BACK);
        INTERNAL_FACINGS.put(Values.FACING_FRONT, CameraCharacteristics.LENS_FACING_FRONT);
    }

    private Activity activityContext;
    private CameraView preview;
    private AutoFitTextureView textureView;
    private CallbackHandler callbackHandler;
    private OrientationEventListener orientationEventListener;

    private boolean isInitialized;
    private boolean isPreviewStarted;

    private int mode = Values.MODE_IMAGE;
    private AspectRatio aspectRatio = Values.DEFAULT_ASPECT_RATIO;
    private boolean autoFocus = true;
    private int facing = Values.FACING_BACK;
    private int flash = Values.FLASH_OFF;
    private FocusHandler focusHandler = new FocusHandler();

    private HandlerThread backgroundThread;
    private Handler backgroundHandler;

    private SizeMap previewSizeMap = new SizeMap();
    private SortedSet<Size> supportedPreviewSizes = new TreeSet<>();
    private Size previewSize;

    private SizeMap imageSizeMap = new SizeMap();
    private SortedSet<Size> supportedImageSizes = new TreeSet<>();
    private Size imageSize;

    private SizeMap videoSizeMap = new SizeMap();
    private SortedSet<Size> supportedVideoSizes = new TreeSet<>();
    private Size videoSize;

    private CameraManager cameraManager;
    private CameraDevice camera;
    private CameraCaptureSession captureSession;
    private CaptureRequest.Builder previewRequestBuilder;

    private String cameraId;
    private CameraCharacteristics characteristics;
    private int sensorOrientation = 90;
    // last determined degree, it is either Surface.Rotation_0, _90, _180, _270, or -1 (undetermined)
    private int currentDeviceRotation = -1;
    private float zoom = 1.f;
    private float maxZoom = 2.f;

    private ImageReader imageReader;
    private MediaRecorder mediaRecorder;

    private String nextImageAbsolutePath;
    private String nextVideoAbsolutePath;
    private boolean isRecordingVideo;

    private static final ArrayList<String> RECORD_VIDEO_PERMISSIONS = new ArrayList<>(3);

    static {
        RECORD_VIDEO_PERMISSIONS.add(Manifest.permission.CAMERA);
        RECORD_VIDEO_PERMISSIONS.add(Manifest.permission.RECORD_AUDIO);
        RECORD_VIDEO_PERMISSIONS.add(Manifest.permission.WRITE_EXTERNAL_STORAGE);
    }

    private final CameraDevice.StateCallback cameraDeviceCallback
            = new CameraDevice.StateCallback() {

        @Override
        public void onOpened(@NonNull CameraDevice camera) {
            Camera2Photographer.this.camera = camera;
            startCaptureSession();
        }

        @Override
        public void onDisconnected(@NonNull CameraDevice camera) {
            camera.close();
            Camera2Photographer.this.camera = null;
            callbackHandler.onPreviewStopped();
        }

        @Override
        public void onError(@NonNull CameraDevice camera, int error) {
            stopPreview();
            callbackHandler.onError(new Error(Error.ERROR_CAMERA));
        }
    };

    private final CameraCaptureSession.StateCallback sessionCallback
            = new CameraCaptureSession.StateCallback() {
        @Override
        public void onConfigured(@NonNull CameraCaptureSession session) {
            captureSession = session;
            updateAutoFocus();
            updateFlash();
            applyZoom();
            updatePreview(null);
            callbackHandler.onPreviewStarted();
            callbackHandler.onZoomChanged(zoom);
        }

        @Override
        public void onConfigureFailed(@NonNull CameraCaptureSession session) {
            stopPreview();
            callbackHandler.onError(new Error(Error.ERROR_CAMERA));
        }

        @Override
        public void onClosed(@NonNull CameraCaptureSession session) {
            if (captureSession != null && captureSession.equals(session)) {
                captureSession = null;
            }
        }
    };

    private ImageCaptureCallback imageCaptureCallback = new ImageCaptureCallback() {

        @Override
        public void onPrecaptureRequired() {
            previewRequestBuilder.set(CaptureRequest.CONTROL_AE_PRECAPTURE_TRIGGER,
                    CaptureRequest.CONTROL_AE_PRECAPTURE_TRIGGER_START);
            setState(STATE_PRECAPTURE);
            try {
                captureSession.capture(previewRequestBuilder.build(), this, null);
                previewRequestBuilder.set(CaptureRequest.CONTROL_AE_PRECAPTURE_TRIGGER,
                        CaptureRequest.CONTROL_AE_PRECAPTURE_TRIGGER_IDLE);
            } catch (CameraAccessException e) {
                callbackHandler.onError(new Error(Error.ERROR_CAMERA, e));
            }
        }

        @Override
        public void onReady() {
            captureStillPicture();
        }

    };

    private final ImageReader.OnImageAvailableListener onImageAvailableListener
            = new ImageReader.OnImageAvailableListener() {

        @Override
        public void onImageAvailable(ImageReader reader) {
            backgroundHandler.post(new ImageSaver(reader.acquireLatestImage(), nextImageAbsolutePath));
        }

    };

    @Override
    public void initWithViewfinder(Activity activity, CameraView preview) {
        this.activityContext = activity;
        this.preview = preview;
        this.textureView = preview.getTextureView();
        cameraManager = (CameraManager) activity.getSystemService(Context.CAMERA_SERVICE);
        callbackHandler = new CallbackHandler(activityContext);
        preview.addCallback(new CameraView.Callback() {
            @Override
            public void onSingleTap(MotionEvent e) {
                focusAt(e);
            }

            @Override
            public void onScale(float scaleFactor) {
                updateZoom(zoom * scaleFactor);
                updatePreview(null);
            }

            @Override
            public void onSurfaceChanged() {
                startCaptureSession();
            }
        });
        orientationEventListener = new OrientationEventListener(activityContext) {

            // a slop before change the device orientation
            private int changeSlop = 10;

            @Override
            public void onOrientationChanged(int orientation) {
                if (shouldChange(orientation)) {
                    int rotation = Surface.ROTATION_0;
                    if ((orientation >= 0 && orientation < 45) || (orientation >= 315 && orientation < 360)) {
                        rotation = Surface.ROTATION_0;
                    } else if (orientation >= 45 && orientation < 135) {
                        rotation = Surface.ROTATION_270;
                    } else if (orientation >= 135 && orientation < 225) {
                        rotation = Surface.ROTATION_180;
                    } else if (orientation >= 225 && orientation < 315) {
                        rotation = Surface.ROTATION_90;
                    }
                    currentDeviceRotation = rotation;
                }
            }

            private boolean shouldChange(int orientation) {
                if (currentDeviceRotation == -1) return true;
                if (currentDeviceRotation == 0) return orientation >= 45 + changeSlop && orientation < 315 - changeSlop;
                int upLimit = currentDeviceRotation + 45 + changeSlop;
                int downLimit = currentDeviceRotation - 45 - changeSlop;
                return !(orientation >= downLimit && orientation < upLimit);
            }
        };
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
    public Set<Size> getSupportedImageSizes() {
        return supportedImageSizes;
    }

    @Override
    public Set<Size> getSupportedVideoSizes() {
        return supportedVideoSizes;
    }

    @Override
    public void startPreview() {
        throwIfNotInitialized();
        for (String permission: RECORD_VIDEO_PERMISSIONS) {
            int permissionCheck = ContextCompat.checkSelfPermission(activityContext, permission);
            if (permissionCheck != PackageManager.PERMISSION_GRANTED) {
                callbackHandler.onError(new Error(Error.ERROR_PERMISSION, "Unsatisfied permission: " + permission));
                return;
            }
        }
        startBackgroundThread();

        if (!chooseCameraIdByFacing()) {
            callbackHandler.onError(new Error(Error.ERROR_CAMERA));
            return;
        }
        if (!collectCameraInfo()) {
            return;
        }
        prepareWorkers();

        callbackHandler.onDeviceConfigured();
        startOpeningCamera();
        if (orientationEventListener != null) {
            orientationEventListener.enable();
        }
        isPreviewStarted = true;
    }

    private boolean chooseCameraIdByFacing() {
        try {
            int internalFacing = INTERNAL_FACINGS.get(facing);
            final String[] ids = cameraManager.getCameraIdList();
            if (ids.length == 0) { // No camera
                callbackHandler.onError(new Error(Error.ERROR_CAMERA, "No camera available."));
                return false;
            }
            for (String id : ids) {
                CameraCharacteristics characteristics = cameraManager.getCameraCharacteristics(id);

                Integer level = characteristics.get(CameraCharacteristics.INFO_SUPPORTED_HARDWARE_LEVEL);
                if (level == null || level == CameraCharacteristics.INFO_SUPPORTED_HARDWARE_LEVEL_LEGACY) {
                    continue;
                }

                Integer internal = characteristics.get(CameraCharacteristics.LENS_FACING);
                if (internal == null) {
                    callbackHandler.onError(new Error(Error.ERROR_CAMERA, "Unexpected state: LENS_FACING null."));
                    return false;
                }
                if (internal == internalFacing) {
                    updateCameraInfo(id, characteristics);
                    return true;
                }
            }

            // Not found
            updateCameraInfo(ids[0], cameraManager.getCameraCharacteristics(ids[0]));
            Integer internal = characteristics.get(CameraCharacteristics.LENS_FACING);
            if (internal == null) {
                callbackHandler.onError(new Error(Error.ERROR_CAMERA, "Unexpected state: LENS_FACING null."));
                return false;
            }
            for (int i = 0, count = INTERNAL_FACINGS.size(); i < count; i++) {
                if (INTERNAL_FACINGS.valueAt(i) == internal) {
                    facing = INTERNAL_FACINGS.keyAt(i);
                    return true;
                }
            }
            // The operation can reach here when the only camera device is an external one.
            // We treat it as facing back.
            facing = Values.FACING_BACK;
            return true;
        } catch (CameraAccessException e) {
            callbackHandler.onError(new Error(Error.ERROR_CAMERA, e));
            return false;
        }
    }

    private void updateCameraInfo(String cameraId, CameraCharacteristics characteristics) {
        this.cameraId = cameraId;
        this.characteristics = characteristics;
        Integer orientation = characteristics.get(CameraCharacteristics.SENSOR_ORIENTATION);
        if (orientation != null) {
            sensorOrientation = orientation;
        }
        Float maxZoomObject = characteristics.get(CameraCharacteristics.SCALER_AVAILABLE_MAX_DIGITAL_ZOOM);
        if (maxZoomObject != null) {
            maxZoom = maxZoomObject;
        }
    }

    private void resetSizes() {
        // clear the image/video size & aspect ratio
        aspectRatio = null;
        imageSize = null;
        videoSize = null;
    }

    private boolean collectCameraInfo() {
        StreamConfigurationMap map = characteristics.get(CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP);
        if (map == null) {
            callbackHandler.onError(new Error(Error.ERROR_CAMERA, "Cannot get available preview/video sizes"));
            return false;
        }

        collectPreviewSizes(map);
        collectImageSizes(map);
        collectVideoSizes(map);
        refineSizes();
        return true;
    }

    private void prepareWorkers() {
        Size size;
        if (mode == Values.MODE_IMAGE) {
            if (imageSize == null) {
                // determine image size
                SortedSet<Size> sizesWithAspectRatio = imageSizeMap.sizes(aspectRatio);
                if (sizesWithAspectRatio != null && sizesWithAspectRatio.size() > 0) {
                    imageSize = sizesWithAspectRatio.last();
                } else {
                    imageSize = imageSizeMap.defaultSize();
                }
            }
            size = imageSize;
            imageReader = ImageReader.newInstance(imageSize.getWidth(), imageSize.getHeight(),
                    ImageFormat.JPEG,2);
            imageReader.setOnImageAvailableListener(onImageAvailableListener, null);
        } else if (mode == Values.MODE_VIDEO) {
            if (videoSize == null) {
                // determine video size
                SortedSet<Size> sizesWithAspectRatio = videoSizeMap.sizes(aspectRatio);
                if (sizesWithAspectRatio != null && sizesWithAspectRatio.size() > 0) {
                    videoSize = sizesWithAspectRatio.last();
                } else {
                    videoSize = chooseVideoSize(supportedVideoSizes);
                }
            }
            size = videoSize;
            mediaRecorder = new MediaRecorder();
        } else {
            throw new RuntimeException("Wrong mode value: " + mode);
        }
        previewSize = chooseOptimalPreviewSize(size);

        int orientation = activityContext.getResources().getConfiguration().orientation;
        if (orientation == Configuration.ORIENTATION_LANDSCAPE) {
            textureView.setAspectRatio(previewSize.getWidth(), previewSize.getHeight());
        } else {
            textureView.setAspectRatio(previewSize.getHeight(), previewSize.getWidth());
        }
    }

    @SuppressLint("MissingPermission")
    private void startOpeningCamera() {
        try {
            cameraManager.openCamera(cameraId, cameraDeviceCallback, null);
        } catch (CameraAccessException e) {
            callbackHandler.onError(new Error(Error.ERROR_CAMERA, "Failed to open camera: " + cameraId, e));
        }
    }

    @Override
    public void restartPreview() {
        if (isPreviewStarted) {
            stopPreview();
            startPreview();
        }
    }

    @Override
    public void stopPreview() {
        isPreviewStarted = false;
        if (orientationEventListener != null) {
            orientationEventListener.disable();
        }
        throwIfNotInitialized();
        closeCamera();
        stopBackgroundThread();
    }

    @Override
    public Size getPreviewSize() {
        return previewSize;
    }

    @Override
    public Size getImageSize() {
        return imageSize;
    }

    @Override
    public void setImageSize(Size size) {
        if (size == null || !supportedImageSizes.contains(size)) {
            callbackHandler.onError(new Error(Error.ERROR_INVALID_PARAM, size + " not supported."));
            return;
        }

        if (imageSize.equals(size)) {
            return;
        }

        resetSizes();
        imageSize = size;
        restartPreview();
    }

    @Override
    public Size getVideoSize() {
        return videoSize;
    }

    @Override
    public void setVideoSize(Size size) {
        if (size == null || !supportedVideoSizes.contains(size)) {
            callbackHandler.onError(new Error(Error.ERROR_INVALID_PARAM, size + " not supported."));
            return;
        }

        if (videoSize.equals(size)) {
            return;
        }

        resetSizes();
        videoSize = size;
        restartPreview();
    }

    @Override
    public Set<AspectRatio> getSupportedAspectRatios() {
        return previewSizeMap.ratios();
    }

    @Override
    public void setAspectRatio(AspectRatio ratio) {
        if (!isPreviewStarted) {
            aspectRatio = ratio;
            return;
        }

        if (ratio == null || !previewSizeMap.ratios().contains(ratio)) {
            callbackHandler.onError(new Error(Error.ERROR_INVALID_PARAM, ratio + " not supported."));
            return;
        }
        if (ratio.equals(aspectRatio)) {
            return;
        }
        resetSizes();
        aspectRatio = ratio;
        restartPreview();
    }

    @Override
    public AspectRatio getAspectRatio() {
        return aspectRatio;
    }

    @Override
    public void setAutoFocus(boolean autoFocus) {
        if (this.autoFocus == autoFocus) {
            return;
        }
        this.autoFocus = autoFocus;
        if (previewRequestBuilder != null) {
            updateAutoFocus();
            updatePreview(() -> this.autoFocus = !this.autoFocus);
        }
    }

    @Override
    public boolean getAutoFocus() {
        return autoFocus;
    }

    @Override
    public void setFacing(int facing) {
        this.facing = facing;
        restartPreview();
    }

    @Override
    public int getFacing() {
        return facing;
    }

    @Override
    public void setFlash(int flash) {
        if (this.flash == flash) {
            return;
        }
        int saved = this.flash;
        this.flash = flash;
        if (previewRequestBuilder != null) {
            updateFlash();
            updatePreview(() -> this.flash = saved);
        }
    }

    @Override
    public int getFlash() {
        return flash;
    }

    @Override
    public void setZoom(float zoom) {
        updateZoom(zoom);
    }

    @Override
    public float getZoom() {
        return zoom;
    }

    @Override
    public void setMode(int mode) {
        this.mode = mode;
        restartPreview();
    }

    @Override
    public int getMode() {
        return mode;
    }

    private void collectPreviewSizes(StreamConfigurationMap map) {
        supportedPreviewSizes.clear();
        for (android.util.Size size : map.getOutputSizes(SurfaceTexture.class)) {
            Size s = new Size(size.getWidth(), size.getHeight());
            supportedPreviewSizes.add(s);
            previewSizeMap.add(s);
        }
    }

    private void collectImageSizes(StreamConfigurationMap map) {
        supportedImageSizes.clear();
        for (android.util.Size size : map.getOutputSizes(ImageFormat.JPEG)) {
            Size s = new Size(size.getWidth(), size.getHeight());
            supportedImageSizes.add(s);
            imageSizeMap.add(s);
        }
    }

    private void collectVideoSizes(StreamConfigurationMap map) {
        supportedVideoSizes.clear();
        for (android.util.Size size : map.getOutputSizes(MediaRecorder.class)) {
            Size s = new Size(size.getWidth(), size.getHeight());
            if (s.getAreaSize() > MAX_VIDEO_SIZE) continue;
            supportedVideoSizes.add(s);
            videoSizeMap.add(s);
        }
    }

    private void refineSizes() {
        for (AspectRatio ratio : previewSizeMap.ratios()) {
            if ((mode == Values.MODE_VIDEO && !videoSizeMap.ratios().contains(ratio))
                    || (mode == Values.MODE_IMAGE && !imageSizeMap.ratios().contains(ratio))) {
                if (previewSizeMap.sizes(ratio) != null) {
                    supportedPreviewSizes.removeAll(previewSizeMap.sizes(ratio));
                }
                previewSizeMap.remove(ratio);
            }
        }

        // fix the aspectRatio if set
        if (aspectRatio != null && !previewSizeMap.ratios().contains(aspectRatio)) {
            aspectRatio = previewSizeMap.ratios().iterator().next();
        }
    }

    private static Size chooseVideoSize(SortedSet<Size> choices) {
        Size chosen = null;
        for (Size size : choices) {
            if (size.getWidth() == size.getHeight() * 4 / 3 && size.getHeight() <= MAX_VIDEO_SIZE) {
                chosen = size;
            }
        }
        if (chosen!= null) return chosen;
        return choices.last();
    }

    private Size chooseOptimalPreviewSize(Size preferred) {
        int surfaceLonger, surfaceShorter;
        final int surfaceWidth = preferred.getWidth();
        final int surfaceHeight = preferred.getHeight();
        if (surfaceWidth < surfaceHeight) {
            surfaceLonger = surfaceHeight;
            surfaceShorter = surfaceWidth;
        } else {
            surfaceLonger = surfaceWidth;
            surfaceShorter = surfaceHeight;
        }

        AspectRatio preferredAspectRatio = AspectRatio.of(surfaceLonger, surfaceShorter);
        // Pick the smallest of those big enough
        for (Size size : supportedPreviewSizes) {
            if (preferredAspectRatio.matches(size)
                    && size.getWidth() >= surfaceLonger && size.getHeight() >= surfaceShorter) {
                return size;
            }
        }

        // If no size is big enough, pick the largest one which matches the ratio.
        SortedSet<Size> matchedSizes = previewSizeMap.sizes(preferredAspectRatio);
        if (matchedSizes != null && matchedSizes.size() > 0) {
            return matchedSizes.last();
        }

        // If no size is big enough or ratio cannot be matched, pick the largest one.
        return supportedPreviewSizes.last();
    }

    private void closeCamera() {
        closePreviewSession();
        if (camera != null) {
            camera.close();
            camera = null;
        }
        if (mediaRecorder != null) {
            mediaRecorder.release();
            mediaRecorder = null;
        }
    }

    private void closePreviewSession() {
        if (captureSession != null) {
            captureSession.close();
            captureSession = null;
        }
    }

    private void startCaptureSession() {
        if (camera == null || textureView.getSurfaceTexture() == null
                || (mode == Values.MODE_IMAGE && imageReader == null)) {
            return;
        }
        try {
            textureView.setBufferSize(previewSize.getWidth(), previewSize.getHeight());
            previewRequestBuilder = camera.createCaptureRequest(CameraDevice.TEMPLATE_PREVIEW);
            Surface previewSurface = textureView.getSurface();
            previewRequestBuilder.addTarget(previewSurface);

            List<Surface> surfaces = new ArrayList<>();
            surfaces.add(previewSurface);
            if (mode == Values.MODE_IMAGE) {
                surfaces.add(imageReader.getSurface());
            }
            camera.createCaptureSession(surfaces, sessionCallback, null);
        } catch (CameraAccessException e) {
            callbackHandler.onError(new Error(Error.ERROR_CAMERA, e));
        }
    }

    private void updateAutoFocus() {
        if (!autoFocus) {
            previewRequestBuilder.set(CaptureRequest.CONTROL_AF_MODE, CaptureRequest.CONTROL_AF_MODE_OFF);
            return;
        }

        int[] modes = characteristics.get(CameraCharacteristics.CONTROL_AF_AVAILABLE_MODES);
        // Auto focus is not supported
        if (modes == null || modes.length == 0 ||
                (modes.length == 1 && modes[0] == CameraCharacteristics.CONTROL_AF_MODE_OFF)) {
            autoFocus = false;
            previewRequestBuilder.set(CaptureRequest.CONTROL_AF_MODE, CaptureRequest.CONTROL_AF_MODE_OFF);
        } else {
            if (mode == Values.MODE_IMAGE) {
                previewRequestBuilder.set(CaptureRequest.CONTROL_AF_MODE,
                        CaptureRequest.CONTROL_AF_MODE_CONTINUOUS_PICTURE);
            } else {
                previewRequestBuilder.set(CaptureRequest.CONTROL_AF_MODE,
                        CaptureRequest.CONTROL_AF_MODE_CONTINUOUS_VIDEO);
            }
        }
    }

    private void updateFlash() {
        switch (flash) {
            case Values.FLASH_OFF:
                previewRequestBuilder.set(CaptureRequest.CONTROL_AE_MODE, CaptureRequest.CONTROL_AE_MODE_ON);
                previewRequestBuilder.set(CaptureRequest.FLASH_MODE, CaptureRequest.FLASH_MODE_OFF);
                break;
            case Values.FLASH_ON:
                previewRequestBuilder.set(CaptureRequest.CONTROL_AE_MODE, CaptureRequest.CONTROL_AE_MODE_ON_ALWAYS_FLASH);
                previewRequestBuilder.set(CaptureRequest.FLASH_MODE, CaptureRequest.FLASH_MODE_OFF);
                break;
            case Values.FLASH_TORCH:
                previewRequestBuilder.set(CaptureRequest.CONTROL_AE_MODE, CaptureRequest.CONTROL_AE_MODE_ON);
                previewRequestBuilder.set(CaptureRequest.FLASH_MODE, CaptureRequest.FLASH_MODE_TORCH);
                break;
            case Values.FLASH_AUTO:
                previewRequestBuilder.set(CaptureRequest.CONTROL_AE_MODE, CaptureRequest.CONTROL_AE_MODE_ON_AUTO_FLASH);
                previewRequestBuilder.set(CaptureRequest.FLASH_MODE, CaptureRequest.FLASH_MODE_OFF);
                break;
            case Values.FLASH_RED_EYE:
                previewRequestBuilder.set(CaptureRequest.CONTROL_AE_MODE, CaptureRequest.CONTROL_AE_MODE_ON_AUTO_FLASH_REDEYE);
                previewRequestBuilder.set(CaptureRequest.FLASH_MODE, CaptureRequest.FLASH_MODE_OFF);
                break;
        }
    }

    @Override
    public void takePicture() {
        if (mode != Values.MODE_IMAGE) {
            callbackHandler.onError(new Error(Error.ERROR_INVALID_PARAM, "Cannot takePicture() in non-IMAGE mode"));
            return;
        }

        try {
            nextImageAbsolutePath = Utils.getImageFilePath();
        } catch (IOException e) {
            callbackHandler.onError(Utils.errorFromThrowable(e));
            return;
        }
        if (autoFocus) {
            lockFocus();
        } else {
            captureStillPicture();
        }
        preview.shot();
    }

    @Override
    public void startRecording(MediaRecorderConfigurator configurator) {
        throwIfNoMediaRecorder();
        if (camera == null || !textureView.isAvailable() || previewSize == null) {
            callbackHandler.onError(new Error(Error.ERROR_CAMERA));
            return;
        }

        try {
            nextVideoAbsolutePath = Utils.getVideoFilePath();
        } catch (IOException e) {
            callbackHandler.onError(Utils.errorFromThrowable(e));
            return;
        }

        try {
            closePreviewSession();
            setUpMediaRecorder(configurator);
            previewRequestBuilder = camera.createCaptureRequest(CameraDevice.TEMPLATE_RECORD);
            List<Surface> surfaces = new ArrayList<>();

            Surface previewSurface = textureView.getSurface();
            surfaces.add(previewSurface);
            previewRequestBuilder.addTarget(previewSurface);

            // Set up Surface for the MediaRecorder
            Surface recorderSurface = mediaRecorder.getSurface();
            surfaces.add(recorderSurface);
            previewRequestBuilder.addTarget(recorderSurface);
            // Start a capture session
            // Once the session starts, we can update the UI and start recording
            camera.createCaptureSession(surfaces, new CameraCaptureSession.StateCallback() {

                @Override
                public void onConfigured(@NonNull CameraCaptureSession cameraCaptureSession) {
                    captureSession = cameraCaptureSession;
                    applyZoom();
                    updatePreview(null);
                    isRecordingVideo = true;
                    mediaRecorder.start();
                    callbackHandler.onStartRecording();
                }

                @Override
                public void onConfigureFailed(@NonNull CameraCaptureSession cameraCaptureSession) {
                    callbackHandler.onError(new Error(Error.ERROR_CAMERA));
                }
            }, null);
        } catch (CameraAccessException e) {
            callbackHandler.onError(new Error(Error.ERROR_CAMERA, e));
        } catch (IOException e) {
            callbackHandler.onError(Utils.errorFromThrowable(e));
        }
    }

    private void setUpMediaRecorder(MediaRecorderConfigurator configurator) throws IOException {
        if (configurator == null || configurator.useDefaultConfigs()) {
            mediaRecorder.setAudioSource(MediaRecorder.AudioSource.MIC);
            mediaRecorder.setVideoSource(MediaRecorder.VideoSource.SURFACE);
            mediaRecorder.setOutputFormat(MediaRecorder.OutputFormat.MPEG_4);
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

        mediaRecorder.setOrientationHint(Utils.getOrientation(sensorOrientation, currentDeviceRotation));
        mediaRecorder.prepare();
    }

    @Override
    public void pauseRecording() {
        throwIfNoMediaRecorder();
        if (!isRecordingVideo) return;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            mediaRecorder.pause();
        } else {
            callbackHandler.onError(new Error(Error.ERROR_UNSUPPORTED_OPERATION));
        }
    }

    @Override
    public void resumeRecording() {
        throwIfNoMediaRecorder();
        if (!isRecordingVideo) return;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            mediaRecorder.resume();
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
        startCaptureSession();
    }

    @Override
    public void setOnEventListener(OnEventListener listener) {
        throwIfNotInitialized();
        callbackHandler.setOnEventListener(listener);
    }

    private void lockFocus() {
        previewRequestBuilder.set(CaptureRequest.CONTROL_AF_TRIGGER,
                CaptureRequest.CONTROL_AF_TRIGGER_START);
        try {
            imageCaptureCallback.setState(ImageCaptureCallback.STATE_LOCKING);
            captureSession.capture(previewRequestBuilder.build(), imageCaptureCallback, null);
        } catch (CameraAccessException e) {
            callbackHandler.onError(new Error(Error.ERROR_CAMERA, "Failed to lock focus.", e));
        }
    }

    private void captureStillPicture() {
        try {
            CaptureRequest.Builder captureRequestBuilder = camera.createCaptureRequest(
                    CameraDevice.TEMPLATE_STILL_CAPTURE);
            captureRequestBuilder.addTarget(imageReader.getSurface());
            captureRequestBuilder.set(CaptureRequest.CONTROL_AF_MODE,
                    previewRequestBuilder.get(CaptureRequest.CONTROL_AF_MODE));
            switch (flash) {
                case Values.FLASH_OFF:
                    captureRequestBuilder.set(CaptureRequest.CONTROL_AE_MODE,
                            CaptureRequest.CONTROL_AE_MODE_ON);
                    captureRequestBuilder.set(CaptureRequest.FLASH_MODE,
                            CaptureRequest.FLASH_MODE_OFF);
                    break;
                case Values.FLASH_ON:
                    captureRequestBuilder.set(CaptureRequest.CONTROL_AE_MODE,
                            CaptureRequest.CONTROL_AE_MODE_ON_ALWAYS_FLASH);
                    break;
                case Values.FLASH_TORCH:
                    captureRequestBuilder.set(CaptureRequest.CONTROL_AE_MODE,
                            CaptureRequest.CONTROL_AE_MODE_ON);
                    captureRequestBuilder.set(CaptureRequest.FLASH_MODE,
                            CaptureRequest.FLASH_MODE_TORCH);
                    break;
                case Values.FLASH_AUTO:
                    captureRequestBuilder.set(CaptureRequest.CONTROL_AE_MODE,
                            CaptureRequest.CONTROL_AE_MODE_ON_AUTO_FLASH);
                    break;
                case Values.FLASH_RED_EYE:
                    captureRequestBuilder.set(CaptureRequest.CONTROL_AE_MODE,
                            CaptureRequest.CONTROL_AE_MODE_ON_AUTO_FLASH);
                    break;
            }
            captureRequestBuilder.set(CaptureRequest.JPEG_ORIENTATION,
                    Utils.getOrientation(sensorOrientation, currentDeviceRotation));
            captureRequestBuilder.set(CaptureRequest.SCALER_CROP_REGION, calculateZoomRect());
            captureSession.stopRepeating();
            captureSession.capture(captureRequestBuilder.build(),
                    new CameraCaptureSession.CaptureCallback() {
                        @Override
                        public void onCaptureCompleted(@NonNull CameraCaptureSession session,
                                                       @NonNull CaptureRequest request,
                                                       @NonNull TotalCaptureResult result) {
                            unlockFocus();
                            callbackHandler.onShotFinished(nextImageAbsolutePath);
                        }

                        @Override
                        public void onCaptureFailed(@NonNull CameraCaptureSession session,
                                                    @NonNull CaptureRequest request,
                                                    @NonNull CaptureFailure failure) {
                            unlockFocus();
                            callbackHandler.onError(new Error(Error.ERROR_CAMERA));
                        }
                    }, null);
        } catch (CameraAccessException e) {
            callbackHandler.onError(new Error(Error.ERROR_CAMERA, "Cannot capture a still picture.", e));
        }
    }

    private void unlockFocus() {
        previewRequestBuilder.set(CaptureRequest.CONTROL_AF_TRIGGER, CaptureRequest.CONTROL_AF_TRIGGER_CANCEL);
        try {
            captureSession.capture(previewRequestBuilder.build(), imageCaptureCallback, null);
            updateAutoFocus();
            updateFlash();
            previewRequestBuilder.set(CaptureRequest.CONTROL_AF_TRIGGER, CaptureRequest.CONTROL_AF_TRIGGER_IDLE);
            updatePreview(null);
            imageCaptureCallback.setState(ImageCaptureCallback.STATE_PREVIEW);
        } catch (CameraAccessException e) {
            callbackHandler.onError(new Error(Error.ERROR_CAMERA, e));
        }
    }

    private int getDisplayOrientation() {
        int rotation = activityContext.getWindowManager().getDefaultDisplay().getRotation();
        return Utils.getOrientation(sensorOrientation, rotation);
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
            callbackHandler.onError(new Error(Error.ERROR_DEFAULT_CODE, e));
        }
    }

    private void focusAt(MotionEvent event) {
        Rect focusRect = null;
        Integer maxRegionsAf = characteristics.get(CameraCharacteristics.CONTROL_MAX_REGIONS_AF);
        if (maxRegionsAf != null && maxRegionsAf >= 1) {
            final Rect sensorArraySize = characteristics.get(CameraCharacteristics.SENSOR_INFO_ACTIVE_ARRAY_SIZE);
            focusRect = Utils.calculateFocusArea(sensorArraySize, getDisplayOrientation(), textureView, event);
        }
        focusHandler.focus(captureSession, previewRequestBuilder,
                focusRect,
                error -> {
                    // resume repeating (preview surface will get frames)
                    updatePreview(null);
                    preview.focusFinished();
                    if (error != null) {
                        callbackHandler.onError(error);
                    }
                });
        preview.focusRequestAt((int) event.getX(), (int) event.getY());
    }

    private void updateZoom(float newZoom) {
        newZoom = clampZoom(newZoom);
        if (Utils.checkFloatEqual(zoom, newZoom)) return;
        zoom = newZoom;
        callbackHandler.onZoomChanged(zoom);
        applyZoom();
    }

    private float clampZoom(float zoom) {
        return Utils.clamp(zoom, 1.f, maxZoom);
    }

    private void applyZoom() {
        Rect zoomRect = calculateZoomRect();
        previewRequestBuilder.set(CaptureRequest.SCALER_CROP_REGION, zoomRect);
    }

    private Rect calculateZoomRect() {
        final Rect origin = characteristics.get(CameraCharacteristics.SENSOR_INFO_ACTIVE_ARRAY_SIZE);
        if (origin == null) return null;
        if (Utils.checkFloatEqual(zoom, 1.f) || zoom < 1.f) return origin;

        int xOffset = (int) (((1 - 1 / zoom) / 2) * (origin.right - origin.left));
        int yOffset = (int) (((1 - 1 / zoom ) / 2) * (origin.bottom - origin.top));

        return new Rect(xOffset, yOffset, origin.right - xOffset, origin.bottom - yOffset);
    }

    private void updatePreview(Runnable exceptionCallback) {
        if (camera == null) {
            return;
        }
        try {
            if (mode == Values.MODE_IMAGE) {
                captureSession.setRepeatingRequest(previewRequestBuilder.build(), imageCaptureCallback, null);
            } else {
                previewRequestBuilder.set(CaptureRequest.CONTROL_MODE, CameraMetadata.CONTROL_MODE_AUTO);
                captureSession.setRepeatingRequest(previewRequestBuilder.build(), null, null);
            }
        } catch (CameraAccessException e) {
            if (exceptionCallback != null) {
                exceptionCallback.run();
            }
            callbackHandler.onError(new Error(Error.ERROR_CAMERA, e));
        }
    }
}
