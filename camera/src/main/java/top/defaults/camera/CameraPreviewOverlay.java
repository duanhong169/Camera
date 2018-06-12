package top.defaults.camera;

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

class CameraPreviewOverlay extends SurfaceView {

    private SurfaceHolder holder;
    private Paint focusPaint;
    private Point focusPoint;

    public CameraPreviewOverlay(Context context) {
        this(context, null);
    }

    public CameraPreviewOverlay(Context context, AttributeSet attrs) {
        this(context, attrs, 0);
    }

    public CameraPreviewOverlay(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        setZOrderOnTop(true);
        holder = getHolder();
        holder.setFormat(PixelFormat.TRANSPARENT);

        focusPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        focusPaint.setStyle(Paint.Style.STROKE);
        focusPaint.setStrokeWidth(2);
        focusPaint.setColor(Color.WHITE);

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

    void focusRequestAt(int x, int y) {
        if (x >= 0 && x <= getMeasuredWidth() && y >= 0 && y <= getMeasuredHeight()) {
            focusPoint = new Point(x, y);
        }

        drawIndicator();
    }

    void focusFinished() {
        focusPoint = null;
        clear();
    }

    private void drawIndicator() {
        invalidate();
        if (holder.getSurface().isValid()) {
            final Canvas canvas = holder.lockCanvas();
            if (canvas != null) {
                canvas.drawColor(Color.TRANSPARENT, PorterDuff.Mode.CLEAR);
                canvas.drawColor(Color.TRANSPARENT);
                canvas.drawCircle(focusPoint.x, focusPoint.y, 150, focusPaint);
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
}
