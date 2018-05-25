package top.defaults.view;

import android.animation.ValueAnimator;
import android.view.animation.AccelerateInterpolator;
import android.view.animation.Interpolator;

public class EffectSettings {
    static Interpolator INTERPOLATOR = new AccelerateInterpolator();
    static int DURATION = 100;

    static void apply(ValueAnimator animator) {
        animator.setInterpolator(INTERPOLATOR);
        animator.setDuration(DURATION);
    }
}
