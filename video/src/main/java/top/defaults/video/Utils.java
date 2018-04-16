package top.defaults.video;

import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static top.defaults.video.Error.ERROR_DEFAULT_CODE;

class Utils {

    private static Pattern pattern = Pattern.compile("^#(\\d+), (.+)");

    static String exceptionMessage(int code, String message) {
        return String.format(Locale.getDefault(), "#%d, %s", code, message);
    }

    private static int codeFromThrowable(Throwable throwable, int fallback) {
        int errorCode = fallback;

        String message = throwable.getMessage();
        if (message != null) {
            Matcher matcher = pattern.matcher(message);
            if (matcher.find()) {
                errorCode = Integer.parseInt(matcher.group(1));
            }
        }

        return errorCode;
    }

    private static String messageFromThrowable(Throwable throwable) {
        String message = throwable.getMessage();

        if (message == null) {
            message = throwable.toString();
        } else {
            Matcher matcher = pattern.matcher(message);
            if (matcher.find()) {
                message = matcher.group(2);
            }
        }
        return message;
    }

    static Error errorFromThrowable(Throwable throwable) {
        return errorFromThrowable(throwable, ERROR_DEFAULT_CODE);
    }

    private static Error errorFromThrowable(Throwable throwable, int fallback) {
        return new Error(codeFromThrowable(throwable, fallback), messageFromThrowable(throwable));
    }

    static boolean napInterrupted() {
        return !sleep(1);
    }

    private static boolean sleep(long millis) {
        try {
            Thread.sleep(millis);
        } catch (Exception e) {
            e.printStackTrace();
            return false;
        }
        return true;
    }

    static boolean getBoolean(Map<String, Object> params, String key, boolean defaultValue) {
        boolean value = defaultValue;
        if (params == null) {
            return value;
        }

        Object valueObject = params.get(key);
        if (valueObject instanceof Boolean) {
            value = (Boolean) valueObject;
        }
        return value;
    }

    static int getInt(Map<String, Object> params, String key, int defaultValue) {
        int value = defaultValue;
        if (params == null) {
            return value;
        }

        Object valueObject = params.get(key);
        if (valueObject instanceof Integer) {
            value = (Integer) valueObject;
        }
        return value;
    }

    static String getString(Map<String, Object> params, String key, String defaultValue) {
        String value = defaultValue;
        if (params == null) {
            return value;
        }

        Object valueObject = params.get(key);
        if (valueObject instanceof String) {
            value = (String) valueObject;
            if (value.length() == 0) {
                value = defaultValue;
            }
        }
        return value;
    }
}
