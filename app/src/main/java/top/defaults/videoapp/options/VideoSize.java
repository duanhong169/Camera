package top.defaults.videoapp.options;

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
}
