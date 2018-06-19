package top.defaults.camera;

import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Point;

public interface CanvasDrawer {

    Paint[] initPaints();

    void draw(Canvas canvas, Point point, Paint[] paints);

    class DefaultCanvasDrawer implements CanvasDrawer {

        @Override
        public Paint[] initPaints() {
            Paint focusPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
            focusPaint.setStyle(Paint.Style.STROKE);
            focusPaint.setStrokeWidth(2);
            focusPaint.setColor(Color.WHITE);
            return new Paint[]{ focusPaint };
        }

        @Override
        public void draw(Canvas canvas, Point point, Paint[] paints) {
            if (paints == null || paints.length == 0) return;
            canvas.drawCircle(point.x, point.y, 150, paints[0]);
        }
    }
}
