package com.example.documentdecoder;

import android.app.NotificationManager;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

import androidx.core.app.NotificationCompat;

public class DeadlineReminderReceiver extends BroadcastReceiver {

    public static final String CHANNEL_ID = "deadline_reminders";

    @Override
    public void onReceive(Context context, Intent intent) {
        String title = intent.getStringExtra("title");
        String message = intent.getStringExtra("message");

        if (title == null || title.trim().isEmpty()) {
            title = "Document Decoder";
        }

        if (message == null || message.trim().isEmpty()) {
            message = "You have an upcoming deadline.";
        }

        int notificationId = intent.getIntExtra(
                "notificationId",
                3001
        );

        NotificationCompat.Builder builder =
                new NotificationCompat.Builder(context, CHANNEL_ID)
                        .setSmallIcon(android.R.drawable.ic_dialog_info)
                        .setContentTitle(title)
                        .setContentText(message)
                        .setStyle(
                                new NotificationCompat.BigTextStyle()
                                        .bigText(message)
                        )
                        .setPriority(NotificationCompat.PRIORITY_HIGH)
                        .setAutoCancel(true);

        NotificationManager manager =
                (NotificationManager) context.getSystemService(
                        Context.NOTIFICATION_SERVICE
                );

        if (manager != null) {
            try {
                manager.notify(notificationId, builder.build());

                // The test notification has no corresponding database row.
                if (notificationId != 3001) {
                    DatabaseHelper databaseHelper =
                            new DatabaseHelper(context);

                    try {
                        databaseHelper.markReminderNotified(
                                notificationId
                        );
                    } finally {
                        databaseHelper.close();
                    }
                }

            } catch (SecurityException e) {
                android.util.Log.e(
                        "DocumentDecoder",
                        "Notification permission was not granted.",
                        e
                );
            } catch (Exception e) {
                android.util.Log.e(
                        "DocumentDecoder",
                        "Could not update reminder status.",
                        e
                );
            }
        }
    }
}
