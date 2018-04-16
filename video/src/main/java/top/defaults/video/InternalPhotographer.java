package top.defaults.video;

import android.app.Activity;

interface InternalPhotographer extends Photographer {

    void initWithViewfinder(Activity activity, AutoFitTextureView textureView);
}
