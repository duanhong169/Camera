package top.defaults.camera;

import android.content.Context;
import android.content.res.TypedArray;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.support.v4.view.ViewCompat;
import android.util.AttributeSet;
import android.view.ViewGroup;
import android.widget.RelativeLayout;

public class CameraPreview extends RelativeLayout {

    private Context context;
    private AutoFitTextureView textureView;
    private CameraPreviewOverlay overlay;
    private final DisplayOrientationDetector displayOrientationDetector;
    String aspectRatio;
    boolean autoFocus;
    int facing;
    int flash;
    int mode;

    public CameraPreview(@NonNull Context context) {
        this(context, null);
    }

    public CameraPreview(@NonNull Context context, @Nullable AttributeSet attrs) {
        this(context, attrs, 0);
    }

    public CameraPreview(@NonNull Context context, @Nullable AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);

        this.context = context;
        textureView = new AutoFitTextureView(context);
        textureView.setId(R.id.textureView);
        LayoutParams textureViewParams = new LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        textureViewParams.addRule(CENTER_IN_PARENT);
        addView(textureView, textureViewParams);

        TypedArray typedArray = context.obtainStyledAttributes(attrs, R.styleable.CameraPreview);
        aspectRatio = typedArray.getString(R.styleable.CameraPreview_aspectRatio);
        autoFocus = typedArray.getBoolean(R.styleable.CameraPreview_autoFocus, true);
        facing = typedArray.getInt(R.styleable.CameraPreview_facing, Values.FACING_BACK);
        flash = typedArray.getInt(R.styleable.CameraPreview_flash, Values.FLASH_OFF);
        mode = typedArray.getInt(R.styleable.CameraPreview_mode, Values.MODE_IMAGE);
        boolean fillSpace = typedArray.getBoolean(R.styleable.CameraPreview_fillSpace, false);
        textureView.setFillSpace(fillSpace);
        boolean showFocusIndicator = typedArray.getBoolean(R.styleable.CameraPreview_showFocusIndicator, true);
        typedArray.recycle();

        addOverlay();

        if (showFocusIndicator) {
            setFocusIndicatorDrawer(new CanvasDrawer.DefaultCanvasDrawer());
        }

        displayOrientationDetector = new DisplayOrientationDetector(context) {
            @Override
            public void onDisplayOrientationChanged(int displayOrientation) {
                textureView.setDisplayOrientation(displayOrientation);
            }
        };
    }

    void assign(InternalPhotographer photographer) {
        photographer.setMode(mode);
        photographer.setAspectRatio(AspectRatio.parse(aspectRatio));
        photographer.setAutoFocus(autoFocus);
        photographer.setFacing(facing);
        photographer.setFlash(flash);
    }

    @Override
    protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        if (!isInEditMode()) {
            displayOrientationDetector.enable(ViewCompat.getDisplay(this));
        }
    }

    @Override
    protected void onDetachedFromWindow() {
        if (!isInEditMode()) {
            displayOrientationDetector.disable();
        }
        super.onDetachedFromWindow();
    }

    AutoFitTextureView getTextureView() {
        return textureView;
    }

    public boolean isFillSpace() {
        return textureView.isFillSpace();
    }

    public void setFillSpace(boolean fillSpace) {
        textureView.setFillSpace(fillSpace);
    }

    private void addOverlay() {
        overlay = new CameraPreviewOverlay(context);
        LayoutParams overlayParams = new LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        overlayParams.addRule(ALIGN_LEFT, R.id.textureView);
        overlayParams.addRule(ALIGN_TOP, R.id.textureView);
        overlayParams.addRule(ALIGN_RIGHT, R.id.textureView);
        overlayParams.addRule(ALIGN_BOTTOM, R.id.textureView);
        addView(overlay, overlayParams);
    }

    public void setFocusIndicatorDrawer(CanvasDrawer drawer) {
        overlay.setCanvasDrawer(drawer);
    }

    void focusRequestAt(int x, int y) {
        overlay.focusRequestAt(x, y);
    }

    void focusFinished() {
        overlay.focusFinished();
    }

    void shot() {
        overlay.shot();
    }

    public interface Callback extends AutoFitTextureView.Callback { }

    public void addCallback(Callback callback) {
        textureView.addCallback(callback);
    }
}
