package top.defaults.view;

import android.content.Context;
import android.content.res.ColorStateList;
import android.content.res.TypedArray;
import android.graphics.Color;
import android.graphics.Paint;
import android.support.annotation.ColorInt;
import android.support.annotation.Nullable;
import android.util.AttributeSet;

import top.defaults.view.clickabletextview.R;

public class ClickableTextView extends android.support.v7.widget.AppCompatTextView {

    private @ColorInt int defaultTextColor;
    private @ColorInt int pressedTextColor;
    private @ColorInt int disabledTextColor;
    private boolean isUnderlined;

    public ClickableTextView(Context context) {
        this(context, null);
    }

    public ClickableTextView(Context context, @Nullable AttributeSet attrs) {
        this(context, attrs, 0);
    }

    public ClickableTextView(Context context, @Nullable AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);

        TypedArray typedArray = context.obtainStyledAttributes(attrs, R.styleable.ClickableTextView);
        defaultTextColor = typedArray.getColor(R.styleable.ClickableTextView_defaultTextColor, getCurrentTextColor());
        pressedTextColor = typedArray.getColor(R.styleable.ClickableTextView_defaultTextColor, calculatePressedColor(defaultTextColor));
        disabledTextColor = typedArray.getColor(R.styleable.ClickableTextView_defaultTextColor, calculateDisabledColor(getCurrentTextColor()));
        isUnderlined = typedArray.getBoolean(R.styleable.ClickableTextView_underline, false);
        typedArray.recycle();

        apply();
    }

    private void apply() {
        ColorStateList colorStateList = new ColorStateList(
                new int[][]{
                        new int[]{ android.R.attr.state_pressed },
                        new int[]{ -android.R.attr.state_enabled },
                        new int[]{} // this should be empty to make default color as we want
                },
                new int[]{
                        pressedTextColor,
                        disabledTextColor,
                        defaultTextColor
                }
        );
        setTextColor(colorStateList);
        setPaintFlags(getPaintFlags() | Paint.UNDERLINE_TEXT_FLAG);
    }

    private int calculatePressedColor(@ColorInt int defaultColor) {
        int alpha = Color.alpha(defaultColor);
        alpha = Math.max(16, alpha - 96);
        return Color.argb(alpha, Color.red(defaultColor), Color.green(defaultColor), Color.blue(defaultColor));
    }

    private int calculateDisabledColor(@ColorInt int defaultColor) {
        int alpha = Color.alpha(defaultColor);
        float[] hsv = new float[3];
        Color.colorToHSV(defaultColor, hsv);
        hsv[1] = Math.max(0, hsv[1] - 0.4f);
        return Color.HSVToColor(alpha, hsv);
    }
}
