package top.defaults.camera;

import java.util.Locale;

public class Error extends java.lang.Error {
    public static final int ERROR_DEFAULT_CODE = -1;
    public static final int ERROR_CAMERA = 1;
    public static final int ERROR_UNSUPPORTED_OPERATION = 2;
    public static final int ERROR_PERMISSION = 3;
    public static final int ERROR_STORAGE = 4;
    public static final int ERROR_INVALID_PARAM = 5;

    private int code;
    private Throwable cause;

    Error(int code) {
        super(messageFor(code));
        this.code = code;
    }

    Error(int code, Throwable cause) {
        super(cause.getMessage());
        this.code = code;
        this.cause = cause;
        cause.printStackTrace();
    }

    Error(int code, String message) {
        super(message);
        this.code = code;
    }

    Error(int code, String message, Throwable cause) {
        super(message);
        this.code = code;
        this.cause = cause;
        cause.printStackTrace();
    }

    @Override
    public Throwable getCause() {
        return cause;
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
            case ERROR_STORAGE:
                message = "No enough storage";
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
