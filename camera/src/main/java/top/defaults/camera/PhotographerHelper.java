package top.defaults.camera;

import android.hardware.camera2.CameraCharacteristics;

import java.util.Collections;

public class PhotographerHelper {

    private Photographer photographer;

    public PhotographerHelper(Photographer photographer) {
        this.photographer = photographer;
    }

    public int getMode() {
        Integer mode = Values.MODE_IMAGE;
        if (photographer.getCurrentParams() != null && photographer.getCurrentParams().get(Keys.MODE) != null) {
            mode = (Integer) photographer.getCurrentParams().get(Keys.MODE);
        }
        return mode;
    }

    public int switchMode() {
        int newMode = (getMode() == Values.MODE_IMAGE ? Values.MODE_VIDEO : Values.MODE_IMAGE);
        photographer.restartPreview(Collections.singletonMap(Keys.MODE, newMode));
        return newMode;
    }

    public int flip() {
        Integer lensFacing = CameraCharacteristics.LENS_FACING_BACK;
        if (photographer.getCurrentParams() != null && photographer.getCurrentParams().get(Keys.FACING) != null) {
            lensFacing = (Integer) photographer.getCurrentParams().get(Keys.FACING);
        }
        int newLensFacing = (lensFacing == CameraCharacteristics.LENS_FACING_BACK
                ? CameraCharacteristics.LENS_FACING_FRONT : CameraCharacteristics.LENS_FACING_BACK);
        photographer.restartPreview(Collections.singletonMap(Keys.FACING, newLensFacing));
        return newLensFacing;
    }
}
