package top.defaults.view;

import android.animation.ArgbEvaluator;
import android.animation.ValueAnimator;

public class AnimateTextColorEffect implements TextViewEffect {

    private ValueAnimator pressColorAnimation;
    private ClickableTextView clickableTextView;

    @Override
    public void init(ClickableTextView clickableTextView) {
        this.clickableTextView = clickableTextView;
        pressColorAnimation = ValueAnimator.ofObject(new ArgbEvaluator(),
                clickableTextView.defaultTextColor, clickableTextView.pressedTextColor);
        pressColorAnimation.addUpdateListener(new ValueAnimator.AnimatorUpdateListener() {
            @Override
            public void onAnimationUpdate(ValueAnimator animation) {
                AnimateTextColorEffect.this.clickableTextView
                        .setTextColor((Integer) animation.getAnimatedValue());
            }
        });
    }

    @Override
    public void actionDown() {
        pressColorAnimation.start();
    }

    @Override
    public void actionUp() {
        clickableTextView.postDelayed(new Runnable() {
            @Override
            public void run() {
                pressColorAnimation.reverse();
            }
        }, pressColorAnimation.getDuration() - pressColorAnimation.getCurrentPlayTime());
    }
}
