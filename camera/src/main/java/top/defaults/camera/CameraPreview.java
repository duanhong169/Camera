package top.defaults.camera;

import android.content.Context;
import android.content.res.TypedArray;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.util.AttributeSet;
import android.view.ViewGroup;
import android.widget.RelativeLayout;

public class CameraPreview extends RelativeLayout {

    private AutoFitTextureView textureView;
    private CameraPreviewOverlay overlay;

    public CameraPreview(@NonNull Context context) {
        this(context, null);
    }

    public CameraPreview(@NonNull Context context, @Nullable AttributeSet attrs) {
        this(context, attrs, 0);
    }

    public CameraPreview(@NonNull Context context, @Nullable AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);

        textureView = new AutoFitTextureView(context);
        textureView.setId(R.id.textureView);
        LayoutParams textureViewParams = new LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        textureViewParams.addRule(CENTER_IN_PARENT);
        addView(textureView, textureViewParams);

        TypedArray typedArray = context.obtainStyledAttributes(attrs, R.styleable.CameraPreview);
        boolean fillSpace = typedArray.getBoolean(R.styleable.CameraPreview_fillSpace, false);
        textureView.setFillSpace(fillSpace);
        boolean showFocusIndicator = typedArray.getBoolean(R.styleable.CameraPreview_showFocusIndicator, true);
        typedArray.recycle();

        if (showFocusIndicator) {
            overlay = new CameraPreviewOverlay(context);
            LayoutParams overlayParams = new LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            overlayParams.addRule(ALIGN_LEFT, R.id.textureView);
            overlayParams.addRule(ALIGN_TOP, R.id.textureView);
            overlayParams.addRule(ALIGN_RIGHT, R.id.textureView);
            overlayParams.addRule(ALIGN_BOTTOM, R.id.textureView);
            addView(overlay, overlayParams);
        }
    }

    AutoFitTextureView getTextureView() {
        return textureView;
    }

    public void setFillSpace(boolean fillSpace) {
        textureView.setFillSpace(fillSpace);
    }

    void focusRequestAt(int x, int y) {
        if (overlay != null) {
            overlay.focusRequestAt(x, y);
        }
    }

    void focusFinished() {
        if (overlay != null) {
            overlay.focusFinished();
        }
    }
}
