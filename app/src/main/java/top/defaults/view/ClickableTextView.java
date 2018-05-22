package top.defaults.view;

import android.content.Context;
import android.support.annotation.Nullable;
import android.util.AttributeSet;

public class ClickableTextView extends android.support.v7.widget.AppCompatTextView {

    public ClickableTextView(Context context) {
        this(context, null);
    }

    public ClickableTextView(Context context, @Nullable AttributeSet attrs) {
        this(context, attrs, 0);
    }

    public ClickableTextView(Context context, @Nullable AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
    }

}
