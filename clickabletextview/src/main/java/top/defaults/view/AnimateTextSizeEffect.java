package top.defaults.view;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.FloatEvaluator;
import android.animation.ValueAnimator;
import android.util.TypedValue;
import android.view.ViewGroup;

public class AnimateTextSizeEffect implements TextViewEffect {

    private ValueAnimator pressSizeAnimation;
    private TextButton textButton;
    private ViewGroup.LayoutParams layoutParams;
    private int originWidth;
    private int originHeight;

    @Override
    public void init(final TextButton textButton) {
        this.textButton = textButton;
        pressSizeAnimation = ValueAnimator.ofObject(new FloatEvaluator(),
                textButton.getTextSize(), textButton.getTextSize() * 0.9);
        EffectSettings.apply(pressSizeAnimation);

        pressSizeAnimation.addUpdateListener(new ValueAnimator.AnimatorUpdateListener() {
            @Override
            public void onAnimationUpdate(ValueAnimator animation) {
                AnimateTextSizeEffect.this.textButton
                        .setTextSize(TypedValue.COMPLEX_UNIT_PX, (Float) animation.getAnimatedValue());
            }
        });
        pressSizeAnimation.addListener(new AnimatorListenerAdapter() {

            @Override
            public void onAnimationStart(Animator animation, boolean isReverse) {
                if (!isReverse) {
                    layoutParams = textButton.getLayoutParams();
                    originWidth = layoutParams.width;
                    originHeight = layoutParams.height;

                    layoutParams.width = textButton.getWidth();
                    layoutParams.height = textButton.getHeight();
                    textButton.setLayoutParams(layoutParams);
                }
            }

            @Override
            public void onAnimationEnd(Animator animation, boolean isReverse) {
                if (isReverse) {
                    if (layoutParams != null) {
                        // restore
                        layoutParams.width = originWidth;
                        layoutParams.height = originHeight;
                        textButton.setLayoutParams(layoutParams);
                    }
                }
            }
        });
    }

    @Override
    public void actionDown() {
        pressSizeAnimation.start();
    }

    @Override
    public void actionUp() {
        textButton.postDelayed(new Runnable() {
            @Override
            public void run() {
                pressSizeAnimation.reverse();
            }
        }, pressSizeAnimation.getDuration() - pressSizeAnimation.getCurrentPlayTime());
    }
}
