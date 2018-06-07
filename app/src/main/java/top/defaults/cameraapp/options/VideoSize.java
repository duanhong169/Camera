package top.defaults.cameraapp.options;

import android.util.Size;

import java.util.ArrayList;
import java.util.List;

import top.defaults.view.PickerView;

public class VideoSize implements PickerView.PickerItem {

    public Size size;

    private VideoSize(Size size) {
        this.size = size;
    }

    @Override
    public String getText() {
        return size.getWidth() + " * " + size.getHeight();
    }

    public static List<VideoSize> supportedSizes(Size[] sizes) {
        List<VideoSize> videoSizes = new ArrayList<>();
        for (Size size: sizes) {
            videoSizes.add(new VideoSize(size));
        }
        return videoSizes;
    }

    public static VideoSize findEqual(List<VideoSize> videoSizes, VideoSize target) {
        if (videoSizes == null || target == null) return null;

        for (VideoSize videoSize: videoSizes) {
            if (videoSize.size.equals(target.size)) {
                return videoSize;
            }
        }

        return null;
    }
}
