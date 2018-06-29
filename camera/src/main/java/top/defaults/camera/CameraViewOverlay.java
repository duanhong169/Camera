package top.defaults.camera;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.ArgbEvaluator;
import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.PixelFormat;
import android.graphics.Point;
import android.graphics.PorterDuff;
import android.util.AttributeSet;
import android.view.SurfaceHolder;
import android.view.SurfaceView;
import android.view.animation.DecelerateInterpolator;

class CameraViewOverlay extends SurfaceView {

    private SurfaceHolder holder;
    private Point focusPoint;
    private CanvasDrawer canvasDrawer;
    private Paint[] paints;

    public CameraViewOverlay(Context context) {
        this(context, null);
    }

    public CameraViewOverlay(Context context, AttributeSet attrs) {
        this(context, attrs, 0);
    }

    public CameraViewOverlay(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        setZOrderOnTop(true);
        holder = getHolder();
        holder.setFormat(PixelFormat.TRANSPARENT);
        holder.addCallback(new SurfaceHolder.Callback() {
            @Override
            public void surfaceCreated(SurfaceHolder holder) {
                clear();
            }

            @Override
            public void surfaceChanged(SurfaceHolder holder, int format, int width, int height) {
                clear();
            }

            @Override
            public void surfaceDestroyed(SurfaceHolder holder) {

            }
        });
    }

    public void setCanvasDrawer(CanvasDrawer drawer) {
        canvasDrawer = drawer;
        paints = drawer.initPaints();
    }

    void focusRequestAt(int x, int y) {
        if (x >= 0 && x <= getMeasuredWidth() && y >= 0 && y <= getMeasuredHeight()) {
            focusPoint = new Point(x, y);
        }

        drawIndicator();
    }

    void focusFinished() {
        focusPoint = null;
        postDelayed(this::clear, 300);
    }

    private void drawIndicator() {
        invalidate();
        if (holder.getSurface().isValid()) {
            final Canvas canvas = holder.lockCanvas();
            if (canvas != null) {
                canvas.drawColor(Color.TRANSPARENT, PorterDuff.Mode.CLEAR);
                canvas.drawColor(Color.TRANSPARENT);
                if (canvasDrawer != null) {
                    canvasDrawer.draw(canvas, focusPoint, paints);
                }
                holder.unlockCanvasAndPost(canvas);
            }
        }
    }

    private void clear() {
        Canvas canvas = holder.lockCanvas();
        if(canvas != null) {
            canvas.drawColor(Color.TRANSPARENT, PorterDuff.Mode.CLEAR);
            holder.unlockCanvasAndPost(canvas);
        }
    }

    private static final int SHUTTER_ONE_WAY_TIME = 150;

    public void shot() {
        int colorFrom = Color.TRANSPARENT;
        int colorTo = 0xaf000000;
        ValueAnimator colorAnimation = ValueAnimator.ofObject(new ArgbEvaluator(), colorFrom, colorTo);
        colorAnimation.setInterpolator(new DecelerateInterpolator());
        colorAnimation.setDuration(SHUTTER_ONE_WAY_TIME);
        colorAnimation.addUpdateListener(animator -> setBackgroundColor((int) animator.getAnimatedValue()));
        colorAnimation.addListener(new AnimatorListenerAdapter() {
            @Override
            public void onAnimationCancel(Animator animation) {
                setBackgroundColor(colorFrom);
            }
        });
        colorAnimation.start();
        postDelayed(colorAnimation::reverse, SHUTTER_ONE_WAY_TIME);
    }
}
