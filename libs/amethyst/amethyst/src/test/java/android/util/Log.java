package android.util;

public class Log {
    // Primitive signature on purpose: OkHttp's Android platform probe (AndroidLog.enableLogging)
    // links against `boolean isLoggable(String, int)`, and a boxed variant is a different method.
    // Answering false keeps OkHttp from installing its Android log handler, which would route
    // every internal task-runner trace through println() below on the dispatcher threads.
    public static boolean isLoggable(String tag, int level) {
        return false;
    }

    public static int println(int priority, String tag, String msg) {
        System.out.println(tag + ": " + msg);
        return 0;
    }

    public static int d(String tag, String msg) {
        System.out.println("DEBUG: " + tag + ": " + msg);
        return 0;
    }

    public static int i(String tag, String msg) {
        System.out.println("INFO: " + tag + ": " + msg);
        return 0;
    }

    public static int w(String tag, String msg) {
        System.out.println("WARN: " + tag + ": " + msg);
        return 0;
    }

    public static int w(String tag, String msg, Throwable e) {
        System.out.println("WARN: " + tag + ": " + msg);
        e.printStackTrace();
        return 0;
    }

    public static int e(String tag, String msg) {
        System.out.println("ERROR: " + tag + ": " + msg);
        return 0;
    }

    public static int e(String tag, String msg, Throwable e) {
        System.out.println("ERROR: " + tag + ": " + msg);
        e.printStackTrace();
        return 0;
    }
}