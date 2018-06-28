package top.defaults.cameraapp.options;

import top.defaults.camera.AspectRatio;

public class AspectRatioItem implements PickerItemWrapper<AspectRatio> {

    private AspectRatio aspectRatio;

    public AspectRatioItem(AspectRatio ratio) {
        aspectRatio = ratio;
    }

    @Override
    public String getText() {
        return aspectRatio.toString();
    }

    @Override
    public AspectRatio get() {
        return aspectRatio;
    }
}
