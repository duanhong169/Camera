package top.defaults.camera;

import android.app.Activity;

public class PhotographerFactory {

    public static Photographer createPhotographerWithCamera2(Activity activity, AutoFitTextureView textureView) {
        InternalPhotographer photographer = new Camera2Photographer();
        photographer.initWithViewfinder(activity, textureView);
        return photographer;
    }
}
