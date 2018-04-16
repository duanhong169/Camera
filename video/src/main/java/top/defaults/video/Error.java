package top.defaults.video;

import java.util.Locale;

public class Error extends java.lang.Error {
    public static final int ERROR_DEFAULT_CODE = -1;
    public static final int ERROR_CAMERA = 1;
    public static final int ERROR_UNSUPPORTED_OPERATION = 2;
    public static final int ERROR_PERMISSION = 3;

    private int code;

    public Error(int code) {
        super(messageFor(code));
        this.code = code;
    }

    Error(int code, String message) {
        super(message);
        this.code = code;
    }

    private static String messageFor(int code) {
        String message;
        switch (code) {
            case ERROR_CAMERA:
                message = "Camera error";
                break;
            case ERROR_UNSUPPORTED_OPERATION:
                message = "Unsupported operation";
                break;
            case ERROR_PERMISSION:
                message = "No enough permissions";
                break;
            default:
                message = "Undefined error";
                break;
        }
        return message;
    }

    @Override
    public String toString() {
        return String.format(Locale.getDefault(), "%s(%d)", getMessage(), code);
    }
}
