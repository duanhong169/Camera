package top.defaults.view;

import android.animation.ArgbEvaluator;
import android.animation.ValueAnimator;

public class AnimateTextColorEffect implements TextViewEffect {

    private ValueAnimator pressColorAnimation;
    private TextButton textButton;

    @Override
    public void init(TextButton textButton) {
        this.textButton = textButton;
        pressColorAnimation = ValueAnimator.ofObject(new ArgbEvaluator(),
                textButton.defaultTextColor, textButton.pressedTextColor);
        EffectSettings.apply(pressColorAnimation);

        pressColorAnimation.addUpdateListener(new ValueAnimator.AnimatorUpdateListener() {
            @Override
            public void onAnimationUpdate(ValueAnimator animation) {
                AnimateTextColorEffect.this.textButton
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
        textButton.postDelayed(new Runnable() {
            @Override
            public void run() {
                pressColorAnimation.reverse();
            }
        }, pressColorAnimation.getDuration() - pressColorAnimation.getCurrentPlayTime());
    }
}
