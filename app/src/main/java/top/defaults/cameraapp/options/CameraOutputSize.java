package top.defaults.cameraapp.options;

import android.util.Size;

import java.util.ArrayList;
import java.util.List;

import top.defaults.view.PickerView;

public class CameraOutputSize implements PickerView.PickerItem {

    public Size size;

    private CameraOutputSize(Size size) {
        this.size = size;
    }

    @Override
    public String getText() {
        return size.getWidth() + " * " + size.getHeight();
    }

    public static List<CameraOutputSize> supportedSizes(Size[] sizes) {
        List<CameraOutputSize> cameraOutputSizes = new ArrayList<>();
        for (Size size: sizes) {
            cameraOutputSizes.add(new CameraOutputSize(size));
        }
        return cameraOutputSizes;
    }

    public static CameraOutputSize findEqual(List<CameraOutputSize> cameraOutputSizes, CameraOutputSize target) {
        if (cameraOutputSizes == null || target == null) return null;

        for (CameraOutputSize cameraOutputSize : cameraOutputSizes) {
            if (cameraOutputSize.size.equals(target.size)) {
                return cameraOutputSize;
            }
        }

        return null;
    }
}
