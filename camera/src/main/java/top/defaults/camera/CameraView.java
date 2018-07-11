package top.defaults.camera;

import android.annotation.SuppressLint;
import android.content.Context;
import android.content.res.TypedArray;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.support.v4.view.ViewCompat;
import android.util.AttributeSet;
import android.view.GestureDetector;
import android.view.MotionEvent;
import android.view.ScaleGestureDetector;
import android.view.ViewGroup;
import android.widget.RelativeLayout;

import java.util.LinkedList;
import java.util.List;

public class CameraView extends RelativeLayout {

    private Context context;
    private AutoFitTextureView textureView;
    private CameraViewOverlay overlay;
    private final DisplayOrientationDetector displayOrientationDetector;
    private String aspectRatio;
    private boolean autoFocus;
    private int facing;
    private int flash;
    private int mode;
    private boolean pinchToZoom;
    private GestureDetector gestureDetector;
    private ScaleGestureDetector scaleGestureDetector;

    public CameraView(@NonNull Context context) {
        this(context, null);
    }

    public CameraView(@NonNull Context context, @Nullable AttributeSet attrs) {
        this(context, attrs, 0);
    }

    @SuppressLint("ClickableViewAccessibility")
    public CameraView(@NonNull Context context, @Nullable AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);

        this.context = context;
        textureView = new AutoFitTextureView(context);
        textureView.setId(R.id.textureView);
        LayoutParams textureViewParams = new LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        textureViewParams.addRule(CENTER_IN_PARENT);
        addView(textureView, textureViewParams);

        TypedArray typedArray = context.obtainStyledAttributes(attrs, R.styleable.CameraView);
        aspectRatio = typedArray.getString(R.styleable.CameraView_aspectRatio);
        autoFocus = typedArray.getBoolean(R.styleable.CameraView_autoFocus, true);
        facing = typedArray.getInt(R.styleable.CameraView_facing, Values.FACING_BACK);
        flash = typedArray.getInt(R.styleable.CameraView_flash, Values.FLASH_OFF);
        mode = typedArray.getInt(R.styleable.CameraView_mode, Values.MODE_IMAGE);
        boolean fillSpace = typedArray.getBoolean(R.styleable.CameraView_fillSpace, false);
        textureView.setFillSpace(fillSpace);
        pinchToZoom = typedArray.getBoolean(R.styleable.CameraView_pinchToZoom, true);
        boolean showFocusIndicator = typedArray.getBoolean(R.styleable.CameraView_showFocusIndicator, true);
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

        gestureDetector = new GestureDetector(context, new GestureDetector.SimpleOnGestureListener() {
            @Override
            public boolean onSingleTapConfirmed(MotionEvent e) {
                dispatchOnSingleTap(e);
                return true;
            }
        });
        scaleGestureDetector = new ScaleGestureDetector(context, new ScaleGestureDetector.SimpleOnScaleGestureListener() {
            @Override
            public boolean onScale(ScaleGestureDetector detector) {
                if (pinchToZoom) {
                    dispatchOnScale(detector.getScaleFactor());
                }
                return true;
            }
        });

        textureView.setOnTouchListener((v, event) -> {
            if (gestureDetector.onTouchEvent(event)) {
                return true;
            }
            if (scaleGestureDetector.onTouchEvent(event)) {
                return true;
            }
            return true;
        });
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

    public void setPinchToZoom(boolean pinchToZoom) {
        this.pinchToZoom = pinchToZoom;
    }

    private void addOverlay() {
        overlay = new CameraViewOverlay(context);
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

    public interface Callback extends AutoFitTextureView.Callback {
        void onSingleTap(MotionEvent e);

        void onScale(float scaleFactor);
    }

    private List<Callback> callbacks = new LinkedList<>();

    public void addCallback(Callback callback) {
        if (callback != null) {
            callbacks.add(callback);
            textureView.addCallback(callback);
        }
    }

    private void dispatchOnSingleTap(MotionEvent e) {
        for (Callback callback : callbacks) {
            callback.onSingleTap(e);
        }
    }

    private void dispatchOnScale(float scaleFactor) {
        for (Callback callback : callbacks) {
            callback.onScale(scaleFactor);
        }
    }
}
