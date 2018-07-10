package top.defaults.camera;

import android.content.Context;
import android.os.Handler;
import android.os.Message;

import top.defaults.logger.Logger;

import static top.defaults.camera.Values.DEBUG;

class CallbackHandler extends Handler {

    private static final int CALLBACK_ON_DEVICE_CONFIGURED = 1;
    private static final int CALLBACK_ON_PREVIEW_STARTED = 2;
    private static final int CALLBACK_ON_ZOOM_CHANGED = 3;
    private static final int CALLBACK_ON_PREVIEW_STOPPED = 4;
    private static final int CALLBACK_ON_START_RECORDING = 5;
    private static final int CALLBACK_ON_FINISH_RECORDING = 6;
    private static final int CALLBACK_ON_SHOT_FINISHED = 7;
    private static final int CALLBACK_ON_ERROR = 8;

    private Photographer.OnEventListener onEventListener;

    CallbackHandler(Context context) {
        super(context.getMainLooper());
    }

    void setOnEventListener(Photographer.OnEventListener listener) {
        onEventListener = listener;
    }

    @Override
    public void handleMessage(Message msg) {
        if (onEventListener == null) {
            return;
        }
        if (DEBUG) {
            Logger.d("handleMessage: " + msg.what);
        }
        switch (msg.what) {
            case CALLBACK_ON_DEVICE_CONFIGURED:
                onEventListener.onDeviceConfigured();
                break;
            case CALLBACK_ON_PREVIEW_STARTED:
                onEventListener.onPreviewStarted();
                break;
            case CALLBACK_ON_ZOOM_CHANGED:
                onEventListener.onZoomChanged((float) msg.obj);
                break;
            case CALLBACK_ON_PREVIEW_STOPPED:
                onEventListener.onPreviewStopped();
                break;
            case CALLBACK_ON_START_RECORDING:
                onEventListener.onStartRecording();
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

    void onZoomChanged(float zoom) {
        Message.obtain(this, CALLBACK_ON_ZOOM_CHANGED, zoom).sendToTarget();
    }

    void onPreviewStopped() {
        Message.obtain(this, CALLBACK_ON_PREVIEW_STOPPED).sendToTarget();
    }

    void onStartRecording() {
        Message.obtain(this, CALLBACK_ON_START_RECORDING).sendToTarget();
    }

    void onFinishRecording(String filePath) {
        Message.obtain(this, CALLBACK_ON_FINISH_RECORDING, filePath).sendToTarget();
    }

    void onShotFinished(String filePath) {
        Message.obtain(this, CALLBACK_ON_SHOT_FINISHED, filePath).sendToTarget();
    }

    void onError(final Error error) {
        Message.obtain(this, CALLBACK_ON_ERROR, error).sendToTarget();
    }
}
