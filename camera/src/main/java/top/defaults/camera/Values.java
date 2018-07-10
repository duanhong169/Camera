package top.defaults.camera;

public interface Values {
    boolean DEBUG = false;

    AspectRatio DEFAULT_ASPECT_RATIO = AspectRatio.of(4, 3);

    int MODE_IMAGE = 0;
    int MODE_VIDEO = 1;

    int FLASH_OFF = 0;
    int FLASH_ON = 1;
    int FLASH_TORCH = 2;
    int FLASH_AUTO = 3;
    int FLASH_RED_EYE = 4;

    int FACING_BACK = 0;
    int FACING_FRONT = 1;
}
