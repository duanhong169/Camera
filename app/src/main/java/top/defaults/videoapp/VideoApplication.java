package top.defaults.videoapp;

import android.app.Application;

import timber.log.Timber;

public class VideoApplication extends Application {

    @Override
    public void onCreate() {
        super.onCreate();
        Timber.plant(new Timber.DebugTree());
    }
}
