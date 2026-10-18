package com.example.documentdecoder;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;

import org.json.JSONObject;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

public class DatabaseHelper extends SQLiteOpenHelper {

    private static final String DATABASE_NAME = "DocumentDecoder.db";
    private static final int DATABASE_VERSION = 2;

    public static final String TABLE_ANALYSES = "analyses";
    public static final String TABLE_REMINDERS = "reminders";

    public static final String COLUMN_ID = "id";
    public static final String COLUMN_TITLE = "title";
    public static final String COLUMN_DOCUMENT_TEXT = "document_text";
    public static final String COLUMN_RESULT_JSON = "result_json";
    public static final String COLUMN_CREATED_AT = "created_at";

    public static final String COLUMN_REMINDER_ID = "reminder_id";
    public static final String COLUMN_EVENT = "event";
    public static final String COLUMN_DEADLINE_MILLIS = "deadline_millis";
    public static final String COLUMN_REMINDER_MILLIS = "reminder_millis";
    public static final String COLUMN_TIMING_LABEL = "timing_label";
    public static final String COLUMN_STATUS = "status";

    public DatabaseHelper(Context context) {
        super(context, DATABASE_NAME, null, DATABASE_VERSION);
    }

    @Override
    public void onCreate(SQLiteDatabase db) {
        createAnalysesTable(db);
        createRemindersTable(db);
    }

    private void createAnalysesTable(SQLiteDatabase db) {
        String sql = "CREATE TABLE " + TABLE_ANALYSES + " ("
                + COLUMN_ID + " INTEGER PRIMARY KEY AUTOINCREMENT, "
                + COLUMN_TITLE + " TEXT NOT NULL, "
                + COLUMN_DOCUMENT_TEXT + " TEXT NOT NULL, "
                + COLUMN_RESULT_JSON + " TEXT NOT NULL, "
                + COLUMN_CREATED_AT + " TEXT NOT NULL"
                + ")";

        db.execSQL(sql);
    }

    private void createRemindersTable(SQLiteDatabase db) {
        String sql = "CREATE TABLE IF NOT EXISTS " + TABLE_REMINDERS + " ("
                + COLUMN_REMINDER_ID + " INTEGER PRIMARY KEY, "
                + COLUMN_EVENT + " TEXT NOT NULL, "
                + COLUMN_DEADLINE_MILLIS + " INTEGER NOT NULL, "
                + COLUMN_REMINDER_MILLIS + " INTEGER NOT NULL, "
                + COLUMN_TIMING_LABEL + " TEXT NOT NULL, "
                + COLUMN_STATUS + " TEXT NOT NULL"
                + ")";

        db.execSQL(sql);
    }

    @Override
    public void onUpgrade(
            SQLiteDatabase db,
            int oldVersion,
            int newVersion) {

        if (oldVersion < 2) {
            createRemindersTable(db);
        }
    }

    // ---------------------------------------------------------
    // ANALYSIS HISTORY
    // ---------------------------------------------------------

    public long saveAnalysis(
            String title,
            String documentText,
            JSONObject result) {

        SQLiteDatabase db = getWritableDatabase();

        String createdAt = new SimpleDateFormat(
                "dd MMM yyyy, hh:mm a",
                Locale.getDefault()
        ).format(new Date());

        ContentValues values = new ContentValues();

        values.put(
                COLUMN_TITLE,
                title == null || title.trim().isEmpty()
                        ? "Untitled document"
                        : title.trim()
        );

        values.put(
                COLUMN_DOCUMENT_TEXT,
                documentText == null ? "" : documentText
        );
        values.put(
                COLUMN_RESULT_JSON,
                result == null ? "{}" : result.toString()
        );
        values.put(COLUMN_CREATED_AT, createdAt);

        return db.insert(TABLE_ANALYSES, null, values);
    }

    public Cursor getAllAnalyses() {
        SQLiteDatabase db = getReadableDatabase();

        return db.query(
                TABLE_ANALYSES,
                new String[]{
                        COLUMN_ID,
                        COLUMN_TITLE,
                        COLUMN_CREATED_AT
                },
                null,
                null,
                null,
                null,
                COLUMN_ID + " DESC"
        );
    }

    public Cursor getAnalysisById(long id) {
        SQLiteDatabase db = getReadableDatabase();

        return db.query(
                TABLE_ANALYSES,
                null,
                COLUMN_ID + " = ?",
                new String[]{String.valueOf(id)},
                null,
                null,
                null
        );
    }

    public int deleteAnalysis(long id) {
        SQLiteDatabase db = getWritableDatabase();

        return db.delete(
                TABLE_ANALYSES,
                COLUMN_ID + " = ?",
                new String[]{String.valueOf(id)}
        );
    }

    // ---------------------------------------------------------
    // DEADLINE REMINDERS
    // ---------------------------------------------------------

    public boolean saveReminder(
            int reminderId,
            String event,
            long deadlineMillis,
            long reminderMillis,
            String timingLabel) {

        ContentValues values = new ContentValues();

        values.put(COLUMN_REMINDER_ID, reminderId);
        values.put(
                COLUMN_EVENT,
                event == null || event.trim().isEmpty()
                        ? "Document deadline"
                        : event.trim()
        );
        values.put(COLUMN_DEADLINE_MILLIS, deadlineMillis);
        values.put(COLUMN_REMINDER_MILLIS, reminderMillis);
        values.put(
                COLUMN_TIMING_LABEL,
                timingLabel == null ? "At deadline" : timingLabel
        );
        values.put(COLUMN_STATUS, "Scheduled");

        long result = getWritableDatabase().insertWithOnConflict(
                TABLE_REMINDERS,
                null,
                values,
                SQLiteDatabase.CONFLICT_REPLACE
        );

        return result != -1;
    }

    public Cursor getAllReminders() {
        return getReadableDatabase().query(
                TABLE_REMINDERS,
                null,
                null,
                null,
                null,
                null,
                COLUMN_REMINDER_MILLIS + " ASC"
        );
    }

    public int deleteReminder(int reminderId) {
        return getWritableDatabase().delete(
                TABLE_REMINDERS,
                COLUMN_REMINDER_ID + " = ?",
                new String[]{String.valueOf(reminderId)}
        );
    }

    public int markReminderNotified(int reminderId) {
        ContentValues values = new ContentValues();
        values.put(COLUMN_STATUS, "Notification sent");

        return getWritableDatabase().update(
                TABLE_REMINDERS,
                values,
                COLUMN_REMINDER_ID + " = ?",
                new String[]{String.valueOf(reminderId)}
        );
    }
}
