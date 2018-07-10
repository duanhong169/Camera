package top.defaults.camera;

import android.graphics.Rect;
import android.hardware.camera2.CameraAccessException;
import android.hardware.camera2.CameraCaptureSession;
import android.hardware.camera2.CameraMetadata;
import android.hardware.camera2.CaptureFailure;
import android.hardware.camera2.CaptureRequest;
import android.hardware.camera2.TotalCaptureResult;
import android.hardware.camera2.params.MeteringRectangle;
import android.support.annotation.NonNull;

class FocusHandler {

    private boolean isFocusProcessing;

    void focus(CameraCaptureSession captureSession, CaptureRequest.Builder captureRequestBuilder,
               Rect focusArea, Callback callback) {
        if (isFocusProcessing) {
            return;
        }

        try {
            captureSession.stopRepeating();
        } catch (CameraAccessException e) {
            callback.onFinish(new Error(Error.ERROR_CAMERA, e));
            return;
        }

        // cancel any existing AF trigger (repeated touches, etc.)
        captureRequestBuilder.set(CaptureRequest.CONTROL_AF_TRIGGER, CameraMetadata.CONTROL_AF_TRIGGER_CANCEL);
        captureRequestBuilder.set(CaptureRequest.CONTROL_AF_MODE, CaptureRequest.CONTROL_AF_MODE_OFF);

        // add a new AF trigger with focus region
        if (focusArea != null) {
            MeteringRectangle rectangle = new MeteringRectangle(focusArea, MeteringRectangle.METERING_WEIGHT_MAX - 1);
            captureRequestBuilder.set(CaptureRequest.CONTROL_AF_REGIONS, new MeteringRectangle[]{rectangle});
        }
        captureRequestBuilder.set(CaptureRequest.CONTROL_MODE, CameraMetadata.CONTROL_MODE_AUTO);
        captureRequestBuilder.set(CaptureRequest.CONTROL_AF_MODE, CaptureRequest.CONTROL_AF_MODE_AUTO);
        captureRequestBuilder.set(CaptureRequest.CONTROL_AF_TRIGGER, CameraMetadata.CONTROL_AF_TRIGGER_START);
        captureRequestBuilder.setTag("FOCUS_TAG"); // we'll capture this later for resuming the preview

        try {
            CameraCaptureSession.CaptureCallback focusCallbackHandler = new CameraCaptureSession.CaptureCallback() {

                @Override
                public void onCaptureCompleted(@NonNull CameraCaptureSession session,
                                               @NonNull CaptureRequest request,
                                               @NonNull TotalCaptureResult result) {
                    isFocusProcessing = false;

                    if (request.getTag() == "FOCUS_TAG") {
                        // the focus trigger is complete, clear AF trigger
                        captureRequestBuilder.set(CaptureRequest.CONTROL_AF_TRIGGER, null);
                        callback.onFinish(null);
                    }
                }

                @Override
                public void onCaptureFailed(@NonNull CameraCaptureSession session,
                                            @NonNull CaptureRequest request,
                                            @NonNull CaptureFailure failure) {
                    isFocusProcessing = false;
                    callback.onFinish(new Error(Error.ERROR_CAMERA, "focus failed"));
                }
            };
            captureSession.capture(captureRequestBuilder.build(), focusCallbackHandler, null);
        } catch (CameraAccessException e) {
            e.printStackTrace();
            callback.onFinish(new Error(Error.ERROR_CAMERA, e));
            return;
        }
        isFocusProcessing = true;
    }

    interface Callback {
        void onFinish(Error error);
    }
}
