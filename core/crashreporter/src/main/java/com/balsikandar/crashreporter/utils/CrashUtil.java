package com.balsikandar.crashreporter.utils;

import android.Manifest;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Build;
import android.text.TextUtils;
import android.util.Log;

import com.balsikandar.crashreporter.CrashReporter;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.io.UnsupportedEncodingException;
import java.io.Writer;
import java.net.URLEncoder;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

import androidx.core.app.NotificationCompat;
import androidx.core.content.ContextCompat;
import de.mm20.launcher2.crashreporter.R;

import static android.content.Context.NOTIFICATION_SERVICE;
import static com.balsikandar.crashreporter.utils.Constants.CHANNEL_NOTIFICATION_ID;

public class CrashUtil {

    private static final String TAG = CrashUtil.class.getSimpleName();

    private CrashUtil() {
        //this class is not publicly instantiable
    }

    private static String getCrashLogTime() {
        SimpleDateFormat dateFormat = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault());
        return dateFormat.format(new Date());
    }

    public static void saveCrashReport(final Throwable throwable) {

        String crashReportPath = CrashReporter.getCrashReportPath();
        String filename = getCrashLogTime() + Constants.CRASH_SUFFIX + Constants.FILE_EXTENSION;
        writeToFile(crashReportPath, filename, getStackTrace(throwable));

        //if (crashReportPath.isEmpty()) crashReportPath = getDefaultPath();

        try {
            showNotification(throwable.getLocalizedMessage(), filename);
        } catch (UnsupportedEncodingException e) {
            e.printStackTrace();
        }
    }

    public static void logException(final Exception exception) {

        new Thread(new Runnable() {
            @Override
            public void run() {

                String crashReportPath = CrashReporter.getCrashReportPath();
                final String filename = getCrashLogTime() + Constants.EXCEPTION_SUFFIX + Constants.FILE_EXTENSION;
                writeToFile(crashReportPath, filename, getStackTrace(exception));

                //showNotification(exception.getLocalizedMessage(), false);
            }
        }).start();
    }

    private static void writeToFile(String crashReportPath, String filename, String crashLog) {

        if (TextUtils.isEmpty(crashReportPath)) {
            crashReportPath = getDefaultPath();
        }

        File crashDir = new File(crashReportPath);
        if (!crashDir.exists() || !crashDir.isDirectory()) {
            crashReportPath = getDefaultPath();
            Log.e(TAG, "Path provided doesn't exists : " + crashDir + "\nSaving crash report at : " + getDefaultPath());
        }

        BufferedWriter bufferedWriter;
        try {
            bufferedWriter = new BufferedWriter(new FileWriter(
                    crashReportPath + File.separator + filename));

            bufferedWriter.write(crashLog);
            bufferedWriter.flush();
            bufferedWriter.close();
            Log.d(TAG, "crash report saved in : " + crashReportPath);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private static void showNotification(String localisedMsg, String fileName) throws UnsupportedEncodingException {

        if (CrashReporter.isNotificationEnabled()) {
            Context context = CrashReporter.getContext();
            if (Build.VERSION.SDK_INT >= 33 && context.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
                return;
            }
            NotificationManager notificationManager = (NotificationManager) context.
                    getSystemService(NOTIFICATION_SERVICE);
            createNotificationChannel(notificationManager, context);
            NotificationCompat.Builder builder = new NotificationCompat.Builder(context, CHANNEL_NOTIFICATION_ID);
            builder.setSmallIcon(R.drawable.ic_warning_black_24dp);

            String filePath = new File(getDefaultPath(), fileName).getAbsolutePath();

            Intent intent = new Intent();
            intent.setComponent(new ComponentName(context.getPackageName(), "de.mm20.launcher2.ui.settings.SettingsActivity"));
            intent.putExtra("de.mm20.launcher2.settings.ROUTE", "settings/debug/crashreport");
            intent.putExtra("crash_report_path", filePath);
            intent.setAction(Long.toString(System.currentTimeMillis()));

            PendingIntent pendingIntent = PendingIntent.getActivity(context, 0, intent, PendingIntent.FLAG_IMMUTABLE);
            builder.setContentIntent(pendingIntent);

            builder.setContentTitle(context.getString(R.string.view_crash_report));

            if (TextUtils.isEmpty(localisedMsg)) {
                builder.setContentText(context.getString(R.string.check_your_message_here));
            } else {
                builder.setContentText(localisedMsg);
            }

            builder.setAutoCancel(true);
            builder.setColor(ContextCompat.getColor(context, R.color.colorAccent_CrashReporter));

            notificationManager.notify(Constants.NOTIFICATION_ID, builder.build());
        }
    }

    private static void createNotificationChannel(NotificationManager notificationManager, Context context) {
        CharSequence name = context.getString(R.string.notification_crash_report_title);
        String description = "";
        NotificationChannel channel = new NotificationChannel(CHANNEL_NOTIFICATION_ID, name, NotificationManager.IMPORTANCE_DEFAULT);
        channel.setDescription(description);
        notificationManager.createNotificationChannel(channel);
    }

    private static String getStackTrace(Throwable e) {
        final Writer result = new StringWriter();
        final PrintWriter printWriter = new PrintWriter(result);

        e.printStackTrace(printWriter);
        String crashLog = result.toString();
        printWriter.close();
        return crashLog;
    }

    public static String getDefaultPath() {
        String defaultPath = CrashReporter.getContext().getExternalFilesDir(null).getAbsolutePath()
                + File.separator + Constants.CRASH_REPORT_DIR;

        File file = new File(defaultPath);
        file.mkdirs();
        return defaultPath;
    }
}
