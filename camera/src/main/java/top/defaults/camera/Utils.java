package top.defaults.camera;

import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.Environment;

import java.io.File;
import java.io.IOException;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@SuppressWarnings("unused")
public class Utils {

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
        return errorFromThrowable(throwable, Error.ERROR_DEFAULT_CODE);
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

    public static void addMediaToGallery(Context context, String photoPath) {
        Intent mediaScanIntent = new Intent(Intent.ACTION_MEDIA_SCANNER_SCAN_FILE);
        File photoFile = new File(photoPath);
        Uri contentUri = Uri.fromFile(photoFile);
        mediaScanIntent.setData(contentUri);
        context.sendBroadcast(mediaScanIntent);
    }

    static String getImageFilePath() throws IOException {
        return getFilePath(".jpg");
    }

    static String getVideoFilePath() throws IOException {
        return getFilePath(".mp4");
    }

    private static String fileDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DCIM) + "/TopDefaultsCamera/";

    static void setFileDir(String fileDir) {
        Utils.fileDir = fileDir;
    }

    private static String getFilePath(String fileSuffix) throws IOException {
        final File dir = new File(fileDir);
        if (!dir.exists()) {
            boolean result = dir.mkdirs();
            if (!result) {
                throw new IOException(Utils.exceptionMessage(Error.ERROR_STORAGE, "Unable to create folder"));
            }
        }
        return dir.getAbsolutePath() + "/" + System.currentTimeMillis() + fileSuffix;
    }

    static boolean checkFloatEqual(float a, float b) {
        return Math.abs(a - b) < 0.001;
    }
}
