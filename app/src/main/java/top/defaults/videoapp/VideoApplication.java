package top.defaults.videoapp;

import android.app.Application;

import timber.log.Timber;
import top.defaults.view.TextButton;
import top.defaults.view.TextButtonEffect;

public class VideoApplication extends Application {

    @Override
    public void onCreate() {
        super.onCreate();
        Timber.plant(new Timber.DebugTree());
        TextButton.Defaults defaults = TextButton.Defaults.get();
        defaults.set(top.defaults.view.textbutton.R.styleable.TextButton_backgroundEffect, TextButtonEffect.BACKGROUND_EFFECT_RIPPLE);
    }
}
