package top.defaults.video;

import android.annotation.SuppressLint;
import android.util.Log;

import java.io.*;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

@SuppressWarnings({"WeakerAccess", "unused"})
@SuppressLint("LogNotTimber")
public class Logger {

    private static final String DEFAULT_TAG = "TopDefaultsLogger";
    private static String tag = DEFAULT_TAG;
    private static int level = Log.VERBOSE;
    private static String logFilePath = null;
    private static int logFileSizeInMegabytes = 2;
    private static final String logBrotherSuffix = "_1";
    private static boolean isLoggingToBrother = false;

    /**
     * 设置日志级别
     *
     * @param level 日志级别
     */
    static void setLevel(int level) {
        Logger.level = level;
    }

    public static int getLevel() {
        return level;
    }

    static void setTag(String newTag) {
        tag = newTag;
    }

    /**
     * 设置日志文件路径，为防止日志文件过大，内部采用两个文件来回写，另一个文件加上后缀"_1"
     *
     * @param filePath 文件路径
     */
    static void setLogFile(String filePath) {
        logFilePath = filePath;
    }

    /**
     * 设置日志文件最大尺寸，单位兆字节
     *
     * @param sizeInMegabyte 日志文件大小
     */
    static void setLogFileMaxSizeInMegabyte(int sizeInMegabyte) {
        logFileSizeInMegabytes = sizeInMegabyte;
    }

    static void v(String logMessage) {
        v(tag + "|" + getLineInfo(), logMessage);
    }

    static void d(String logMessage) {
        d(tag + "|" + getLineInfo(), logMessage);
    }

    static void i(String logMessage) {
        i(tag + "|" + getLineInfo(), logMessage);
    }

    static void w(String logMessage) {
        w(tag + "|" + getLineInfo(), logMessage);
    }

    static void e(String logMessage) {
        e(tag + "|" + getLineInfo(), logMessage);
    }

    static void v(String tag, String logMessage) {
        if (level > Log.VERBOSE && !Log.isLoggable(Logger.tag, Log.DEBUG))
            return;
        Log.v(tag, logMessage);
        writeLogFile(tag + "\t" + logMessage);
    }

    static void d(String tag, String logMessage) {
        if (level > Log.DEBUG && !Log.isLoggable(Logger.tag, Log.DEBUG))
            return;
        Log.d(tag, logMessage);
        writeLogFile(tag + "\t" + logMessage);
    }

    static void i(String tag, String logMessage) {
        if (level > Log.INFO && !Log.isLoggable(Logger.tag, Log.DEBUG))
            return;
        Log.i(tag, logMessage);
        writeLogFile(tag + "\t" + logMessage);
    }

    static void w(String tag, String logMessage) {
        if (level > Log.WARN && !Log.isLoggable(Logger.tag, Log.DEBUG))
            return;
        Log.w(tag, logMessage);
        writeLogFile(tag + "\t" + logMessage);
    }

    static void e(String tag, String logMessage) {
        if (level > Log.ERROR && !Log.isLoggable(Logger.tag, Log.DEBUG))
            return;
        Log.e(tag, logMessage);
        writeLogFile(tag + "\t" + logMessage);
    }

    static void logThreadStart() {
        d(tag + "|" + getLineInfo(1), ">>>>>>>> " + Thread.currentThread().getClass() + " start running >>>>>>>>");
    }

    static void logThreadFinish() {
        d(tag + "|" + getLineInfo(1), "<<<<<<<< " + Thread.currentThread().getClass() + " finished running <<<<<<<<");
    }

    private static void writeLogFile(String logMessage) {
        if (logFilePath != null) {
            String fileName = logFilePath + (isLoggingToBrother ? logBrotherSuffix : "");
            File currentLogFile = new File(fileName);
            if (currentLogFile.length() >= logFileSizeInMegabytes * 1024 * 1024) {
                isLoggingToBrother = !isLoggingToBrother;

                // delete file before write
                try {
                    PrintWriter writer = new PrintWriter(logFilePath + (isLoggingToBrother ? logBrotherSuffix : ""));
                    writer.print("");
                    writer.close();
                } catch (FileNotFoundException ignored) {
                }
            }
            try {
                BufferedWriter logOut = new BufferedWriter(new java.io.FileWriter(fileName, true));
                SimpleDateFormat sdfDate = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US);
                String strDate = sdfDate.format(new Date());
                logOut.append(strDate).append("\t").append(logMessage).append("\n");
                logOut.close();
            } catch (IOException ignored) {
            }
        }
    }

    private static String getLineInfo() {
        return getLineInfo(0);
    }

    private static String getLineInfo(int offset) {
        StackTraceElement[] stackTraceElement = Thread.currentThread()
                .getStackTrace();

        String fileName = stackTraceElement[5 - offset].getFileName();
        int lineNumber = stackTraceElement[5 - offset].getLineNumber();
        return ".(" + fileName + ":" + lineNumber + ")";
    }
}
