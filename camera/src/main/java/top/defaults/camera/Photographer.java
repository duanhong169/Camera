package top.defaults.camera;

import android.media.MediaRecorder;
import android.support.annotation.Nullable;

import java.util.Collection;
import java.util.Map;

public interface Photographer {

    Collection<Size> getSupportedImageSizes();

    Collection<Size> getSupportedVideoSizes();

    Map<String, Object> getCurrentParams();

    void startPreview(Map<String, Object> params);

    void restartPreview(Map<String, Object> params);

    void stopPreview();

    void setImageSize(Size size);

    void setVideoSize(Size size);

    void setFacing(int facing);

    int getFacing();

    void setAutoFocus(boolean autoFocus);

    boolean getAutoFocus();

    void setFlash(int flash);

    int getFlash();

    void takePicture();

    void startRecording(@Nullable MediaRecorderConfigurator configurator);

    /**
     * Only works when API level >= 24 (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N).
     */
    void pauseRecording();

    /**
     * Only works when API level >= 24 (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N).
     */
    void resumeRecording();

    void finishRecording();

    interface MediaRecorderConfigurator {

        /**
         * Our Photographer's MediaRecorder use the default configs below:
         * <pre>
         * mediaRecorder.setAudioSource(MediaRecorder.AudioSource.MIC);
         * mediaRecorder.setVideoSource(MediaRecorder.VideoSource.SURFACE);
         * mediaRecorder.setOutputFormat(MediaRecorder.OutputFormat.MPEG_4);
         * if (nextVideoAbsolutePath == null || nextVideoAbsolutePath.isEmpty()) {
         *    nextVideoAbsolutePath = getVideoFilePath(activityContext); // random file
         * }
         * mediaRecorder.setOutputFile(nextVideoAbsolutePath);
         * mediaRecorder.setVideoEncodingBitRate(10000000);
         * mediaRecorder.setVideoFrameRate(30);
         * mediaRecorder.setVideoSize(videoSize.getWidth(), videoSize.getHeight());
         * mediaRecorder.setVideoEncoder(MediaRecorder.VideoEncoder.H264);
         * mediaRecorder.setAudioEncoder(MediaRecorder.AudioEncoder.AAC);
         * </pre>
         *
         * If you need to configure the MediaRecorder by yourself, please be carefully otherwise
         * {@link IllegalStateException} may be thrown. See javadoc for {@link MediaRecorder}
         *
         * @return A boolean value which indicates if we use the default configs for MediaRecorder.
         */
        boolean useDefaultConfigs();

        /**
         * Your configurations here may override the default configs when possible.
         *
         * If you need to configure the MediaRecorder by yourself, please be carefully otherwise
         * {@link IllegalStateException} may be thrown. See javadoc for {@link MediaRecorder}
         *
         * @param recorder The recorder to be configured.
         */
        void configure(MediaRecorder recorder);
    }

    void setOnEventListener(OnEventListener listener);

    interface OnEventListener {

        void onDeviceConfigured();

        void onPreviewStarted();

        void onPreviewStopped();

        void onStartRecording();

        void onFinishRecording(String filePath);

        void onShotFinished(String filePath);

        void onError(Error error);
    }
}
