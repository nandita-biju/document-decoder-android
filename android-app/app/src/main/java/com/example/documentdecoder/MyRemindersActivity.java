package com.example.documentdecoder;

import android.app.AlarmManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.database.Cursor;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

public class MyRemindersActivity extends AppCompatActivity {

    private DatabaseHelper databaseHelper;
    private LinearLayout remindersContainer;

    private final SimpleDateFormat dateFormatter =
            new SimpleDateFormat(
                    "dd MMM yyyy, hh:mm a",
                    Locale.getDefault()
            );

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        databaseHelper = new DatabaseHelper(this);

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(Color.rgb(247, 248, 252));
        root.setPadding(dp(20), dp(20), dp(20), dp(12));

        TextView heading = new TextView(this);
        heading.setText("My Reminders");
        heading.setTextSize(26);
        heading.setTextColor(Color.rgb(32, 36, 58));
        heading.setTypeface(null, Typeface.BOLD);

        root.addView(
                heading,
                new LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.MATCH_PARENT,
                        LinearLayout.LayoutParams.WRAP_CONTENT
                )
        );

        TextView subtitle = new TextView(this);
        subtitle.setText(
                "Keep track of upcoming deadlines and manage your alerts."
        );
        subtitle.setTextSize(14);
        subtitle.setTextColor(Color.rgb(104, 109, 128));
        subtitle.setPadding(0, dp(6), 0, dp(16));

        root.addView(subtitle);

        ScrollView scrollView = new ScrollView(this);

        remindersContainer = new LinearLayout(this);
        remindersContainer.setOrientation(LinearLayout.VERTICAL);

        scrollView.addView(remindersContainer);

        root.addView(
                scrollView,
                new LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.MATCH_PARENT,
                        0,
                        1
                )
        );

        Button refreshButton = new Button(this);
        refreshButton.setText("Refresh Reminders");
        refreshButton.setAllCaps(false);
        refreshButton.setOnClickListener(v -> loadReminders());

        root.addView(refreshButton);

        setContentView(root);
        loadReminders();
    }

    @Override
    protected void onResume() {
        super.onResume();

        if (remindersContainer != null) {
            loadReminders();
        }
    }

    private void loadReminders() {
        remindersContainer.removeAllViews();

        Cursor cursor = null;
        int count = 0;

        try {
            cursor = databaseHelper.getAllReminders();

            while (cursor.moveToNext()) {
                count++;

                int id = cursor.getInt(
                        cursor.getColumnIndexOrThrow(
                                DatabaseHelper.COLUMN_REMINDER_ID
                        )
                );

                String event = cursor.getString(
                        cursor.getColumnIndexOrThrow(
                                DatabaseHelper.COLUMN_EVENT
                        )
                );

                long deadlineMillis = cursor.getLong(
                        cursor.getColumnIndexOrThrow(
                                DatabaseHelper.COLUMN_DEADLINE_MILLIS
                        )
                );

                long reminderMillis = cursor.getLong(
                        cursor.getColumnIndexOrThrow(
                                DatabaseHelper.COLUMN_REMINDER_MILLIS
                        )
                );

                String timingLabel = cursor.getString(
                        cursor.getColumnIndexOrThrow(
                                DatabaseHelper.COLUMN_TIMING_LABEL
                        )
                );

                String status = cursor.getString(
                        cursor.getColumnIndexOrThrow(
                                DatabaseHelper.COLUMN_STATUS
                        )
                );

                addReminderCard(
                        id,
                        event,
                        deadlineMillis,
                        reminderMillis,
                        timingLabel,
                        status
                );
            }

        } catch (Exception e) {
            Toast.makeText(
                    this,
                    "Unable to load reminders.",
                    Toast.LENGTH_LONG
            ).show();

            android.util.Log.e(
                    "DocumentDecoder",
                    "Could not load reminders.",
                    e
            );

        } finally {
            if (cursor != null) {
                cursor.close();
            }
        }

        if (count == 0) {
            TextView empty = new TextView(this);
            empty.setText(
                    "No reminders yet.\n\n"
                            + "Analyze a document and tap "
                            + "\"Set Reminder\" on one of its deadlines."
            );
            empty.setTextSize(15);
            empty.setTextColor(Color.rgb(104, 109, 128));
            empty.setPadding(dp(8), dp(28), dp(8), dp(28));

            remindersContainer.addView(empty);
        }
    }

    private void addReminderCard(
            int id,
            String event,
            long deadlineMillis,
            long reminderMillis,
            String timingLabel,
            String status) {

        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setPadding(dp(16), dp(14), dp(16), dp(14));

        GradientDrawable background = new GradientDrawable();
        background.setColor(Color.WHITE);
        background.setCornerRadius(dp(14));
        background.setStroke(dp(1), Color.rgb(230, 232, 240));
        card.setBackground(background);
        card.setElevation(dp(2));

        LinearLayout.LayoutParams cardParams =
                new LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.MATCH_PARENT,
                        LinearLayout.LayoutParams.WRAP_CONTENT
                );

        cardParams.bottomMargin = dp(14);
        remindersContainer.addView(card, cardParams);

        addText(card, event, 18, Color.rgb(32, 36, 58), true);

        addText(
                card,
                "Reminder: " + dateFormatter.format(
                        new Date(reminderMillis)
                ),
                14,
                Color.rgb(82, 101, 216),
                false
        );

        addText(
                card,
                "Deadline: " + dateFormatter.format(
                        new Date(deadlineMillis)
                ),
                14,
                Color.rgb(55, 59, 75),
                false
        );

        addText(
                card,
                "Timing: " + timingLabel,
                13,
                Color.rgb(104, 109, 128),
                false
        );

        boolean notificationSent =
                "Notification sent".equals(status);

        addText(
                card,
                "Status: " + status,
                13,
                notificationSent
                        ? Color.rgb(71, 123, 101)
                        : Color.rgb(183, 121, 40),
                true
        );

        if (!notificationSent) {
            Button cancelButton = new Button(this);
            cancelButton.setText("Cancel Reminder");
            cancelButton.setAllCaps(false);

            card.addView(cancelButton);

            cancelButton.setOnClickListener(v ->
                    confirmCancellation(id, event)
            );
        }
    }

    private void confirmCancellation(int id, String event) {
        new AlertDialog.Builder(this)
                .setTitle("Cancel Reminder")
                .setMessage(
                        "Cancel the reminder for \"" + event + "\"?"
                )
                .setPositiveButton("Cancel Reminder", (dialog, which) ->
                        cancelReminder(id)
                )
                .setNegativeButton("Keep It", null)
                .show();
    }

    private void cancelReminder(int reminderId) {
        AlarmManager alarmManager =
                (AlarmManager) getSystemService(
                        Context.ALARM_SERVICE
                );

        Intent intent = new Intent(
                this,
                DeadlineReminderReceiver.class
        );

        PendingIntent pendingIntent = PendingIntent.getBroadcast(
                this,
                reminderId,
                intent,
                PendingIntent.FLAG_NO_CREATE
                        | PendingIntent.FLAG_IMMUTABLE
        );

        if (pendingIntent != null) {
            if (alarmManager != null) {
                alarmManager.cancel(pendingIntent);
            }

            pendingIntent.cancel();
        }

        int deleted = databaseHelper.deleteReminder(reminderId);

        if (deleted > 0) {
            Toast.makeText(
                    this,
                    "Reminder cancelled.",
                    Toast.LENGTH_SHORT
            ).show();
        } else {
            Toast.makeText(
                    this,
                    "Reminder record could not be found.",
                    Toast.LENGTH_SHORT
            ).show();
        }

        loadReminders();
    }

    private void addText(
            LinearLayout parent,
            String text,
            int size,
            int color,
            boolean bold) {

        TextView view = new TextView(this);
        view.setText(text);
        view.setTextSize(size);
        view.setTextColor(color);

        if (bold) {
            view.setTypeface(null, Typeface.BOLD);
        }

        view.setPadding(0, dp(4), 0, dp(4));

        parent.addView(view);
    }

    private int dp(int value) {
        return (int) (value * getResources()
                .getDisplayMetrics().density + 0.5f);
    }

    @Override
    protected void onDestroy() {
        databaseHelper.close();
        super.onDestroy();
    }
}
