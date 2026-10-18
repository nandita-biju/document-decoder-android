package com.example.documentdecoder;

import android.Manifest;
import android.app.AlarmManager;
import android.app.DatePickerDialog;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.TimePickerDialog;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.SystemClock;
import android.provider.OpenableColumns;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import com.tom_roush.pdfbox.android.PDFBoxResourceLoader;
import com.tom_roush.pdfbox.pdmodel.PDDocument;
import com.tom_roush.pdfbox.text.PDFTextStripper;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.ConnectException;
import java.net.HttpURLConnection;
import java.net.SocketTimeoutException;
import java.net.URL;
import java.net.UnknownHostException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class MainActivity extends AppCompatActivity {

    private static final int MAX_DOCUMENT_LENGTH = 20000;

    private static final String API_URL =
            "http://10.0.2.2:5000/analyze";

    private static final String CHANNEL_ID = "deadline_reminders";

    private static final int NOTIFICATION_PERMISSION_REQUEST = 1001;

    private static final int SCHEDULED_TEST_ID = 3001;

    private EditText documentTitleInput;
    private EditText documentTextInput;

    private TextView characterCount;
    private TextView statusMessage;

    private Button analyzeButton;
    private Button uploadPdfButton;
    private Button historyButton;
    private Button testNotificationButton;
    private Button myRemindersButton;

    private ProgressBar loadingIndicator;
    private LinearLayout resultsContainer;

    private DatabaseHelper databaseHelper;

    private final ExecutorService executor =
            Executors.newSingleThreadExecutor();

    private final ActivityResultLauncher<String> pdfPicker =
            registerForActivityResult(
                    new ActivityResultContracts.GetContent(),
                    this::handleSelectedPdf
            );

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        PDFBoxResourceLoader.init(getApplicationContext());

        databaseHelper = new DatabaseHelper(this);

        documentTitleInput = findViewById(R.id.documentTitleInput);
        documentTextInput = findViewById(R.id.documentTextInput);

        characterCount = findViewById(R.id.characterCount);
        statusMessage = findViewById(R.id.statusMessage);

        analyzeButton = findViewById(R.id.analyzeButton);
        uploadPdfButton = findViewById(R.id.uploadPdfButton);
        historyButton = findViewById(R.id.historyButton);

        testNotificationButton =
                findViewById(R.id.testNotificationButton);

        loadingIndicator = findViewById(R.id.loadingIndicator);
        resultsContainer = findViewById(R.id.resultsContainer);

        setupNotificationChannel();
        requestNotificationPermission();

        setupCharacterCounter();

        setupMyRemindersButton();

        analyzeButton.setOnClickListener(v -> analyzeDocument());

        uploadPdfButton.setOnClickListener(v ->
                pdfPicker.launch("application/pdf")
        );

        historyButton.setOnClickListener(v ->
                showAnalysisHistory()
        );

        testNotificationButton.setOnClickListener(v ->
                handleTestNotificationClick()
        );
    }

    // ---------------------------------------------------------
    // MY REMINDERS BUTTON
    // ---------------------------------------------------------

    private void setupMyRemindersButton() {
        myRemindersButton = new Button(this);
        myRemindersButton.setText("My Reminders");
        myRemindersButton.setAllCaps(false);

        ViewGroup historyParent =
                (ViewGroup) historyButton.getParent();

        if (historyParent != null) {
            int historyIndex =
                historyParent.indexOfChild(historyButton);

            ViewGroup.LayoutParams params =
                    new ViewGroup.LayoutParams(
                            ViewGroup.LayoutParams.MATCH_PARENT,
                            ViewGroup.LayoutParams.WRAP_CONTENT
                    );

            historyParent.addView(
                    myRemindersButton,
                    historyIndex + 1,
                    params
            );
        }

        myRemindersButton.setOnClickListener(v -> {
            Intent intent = new Intent(
                    MainActivity.this,
                    MyRemindersActivity.class
            );

            startActivity(intent);
        });
    }

    // ---------------------------------------------------------
    // UI CONTROL HELPERS
    // ---------------------------------------------------------

    private void setControlsEnabled(boolean enabled) {
        analyzeButton.setEnabled(enabled);
        uploadPdfButton.setEnabled(enabled);
        historyButton.setEnabled(enabled);
        testNotificationButton.setEnabled(enabled);

        if (myRemindersButton != null) {
            myRemindersButton.setEnabled(enabled);
        }
    }

    // ---------------------------------------------------------
    // NOTIFICATION SETUP & PERMISSION
    // ---------------------------------------------------------

    private void setupNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel channel = new NotificationChannel(
                    CHANNEL_ID,
                    "Deadline Reminders",
                    NotificationManager.IMPORTANCE_HIGH
            );

            channel.setDescription(
                    "Notifications for upcoming document deadlines"
            );

            NotificationManager manager =
                    getSystemService(NotificationManager.class);

            if (manager != null) {
                manager.createNotificationChannel(channel);
            }
        }
    }

    private void requestNotificationPermission() {
        if (Build.VERSION.SDK_INT >= 33) {
            if (ContextCompat.checkSelfPermission(
                    this,
                    Manifest.permission.POST_NOTIFICATIONS
            ) != PackageManager.PERMISSION_GRANTED) {

                ActivityCompat.requestPermissions(
                        this,
                        new String[]{
                                Manifest.permission.POST_NOTIFICATIONS
                        },
                        NOTIFICATION_PERMISSION_REQUEST
                );
            }
        }
    }

    private boolean hasNotificationPermission() {
        return Build.VERSION.SDK_INT < 33
                || ContextCompat.checkSelfPermission(
                this,
                Manifest.permission.POST_NOTIFICATIONS
        ) == PackageManager.PERMISSION_GRANTED;
    }

    @Override
    public void onRequestPermissionsResult(
            int requestCode,
            @NonNull String[] permissions,
            @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);

        if (requestCode == NOTIFICATION_PERMISSION_REQUEST) {
            if (grantResults.length > 0
                    && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                Toast.makeText(
                        this,
                        "Notification permission granted.",
                        Toast.LENGTH_SHORT
                ).show();
            } else {
                Toast.makeText(
                        this,
                        "Notifications disabled. You will not receive deadline alerts.",
                        Toast.LENGTH_LONG
                ).show();
            }
        }
    }

    // ---------------------------------------------------------
    // TEST NOTIFICATION
    // ---------------------------------------------------------

    private void handleTestNotificationClick() {
        if (!hasNotificationPermission()) {
            Toast.makeText(
                    this,
                    "Allow notification permission first.",
                    Toast.LENGTH_LONG
            ).show();

            requestNotificationPermission();
            return;
        }

        scheduleTestReminder();
    }

    private void scheduleTestReminder() {
        AlarmManager alarmManager =
                (AlarmManager) getSystemService(ALARM_SERVICE);

        if (alarmManager == null) {
            Toast.makeText(
                    this,
                    "Alarm service unavailable.",
                    Toast.LENGTH_LONG
            ).show();
            return;
        }

        Intent intent = new Intent(
                this,
                DeadlineReminderReceiver.class
        );

        intent.putExtra(
                "title",
                "Document Decoder — Test Reminder"
        );

        intent.putExtra(
                "message",
                "Your scheduled test reminder is working!"
        );

        intent.putExtra(
                "notificationId",
                SCHEDULED_TEST_ID
        );

        PendingIntent pendingIntent = PendingIntent.getBroadcast(
                this,
                SCHEDULED_TEST_ID,
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT
                        | PendingIntent.FLAG_IMMUTABLE
        );

        long triggerTime =
                SystemClock.elapsedRealtime() + 60_000L;

        alarmManager.setAndAllowWhileIdle(
                AlarmManager.ELAPSED_REALTIME_WAKEUP,
                triggerTime,
                pendingIntent
        );

        Toast.makeText(
                this,
                "Test reminder scheduled for about one minute from now.",
                Toast.LENGTH_LONG
        ).show();
    }

    // ---------------------------------------------------------
    // DEADLINE REMINDERS
    // ---------------------------------------------------------

    private void promptToScheduleDeadline(
            String event,
            String extractedDate) {

        if (!hasNotificationPermission()) {
            Toast.makeText(
                    this,
                    "Allow notification permission before setting reminders.",
                    Toast.LENGTH_LONG
            ).show();

            requestNotificationPermission();
            return;
        }

        String dateDescription =
                extractedDate == null || extractedDate.trim().isEmpty()
                        ? "No date was extracted."
                        : "Date extracted from your document: "
                        + extractedDate;

        Calendar selectedDate = Calendar.getInstance();

        // The user must confirm the actual deadline date and time.
        selectedDate.add(Calendar.DAY_OF_MONTH, 1);
        selectedDate.set(Calendar.HOUR_OF_DAY, 9);
        selectedDate.set(Calendar.MINUTE, 0);
        selectedDate.set(Calendar.SECOND, 0);
        selectedDate.set(Calendar.MILLISECOND, 0);

        new AlertDialog.Builder(this)
                .setTitle("Set Deadline Reminder")
                .setMessage(
                        "Deadline: " + event + "\n\n"
                                + dateDescription + "\n\n"
                                + "Confirm the actual deadline date and "
                                + "time on the next screens."
                )
                .setPositiveButton("Choose Date", (dialog, which) ->
                        showDeadlineDatePicker(event, selectedDate)
                )
                .setNegativeButton("Cancel", null)
                .show();
    }

    private void showDeadlineDatePicker(
            String event,
            Calendar selectedDate) {

        DatePickerDialog datePickerDialog = new DatePickerDialog(
                this,
                (view, year, month, dayOfMonth) -> {
                    selectedDate.set(Calendar.YEAR, year);
                    selectedDate.set(Calendar.MONTH, month);
                    selectedDate.set(Calendar.DAY_OF_MONTH, dayOfMonth);

                    showDeadlineTimePicker(event, selectedDate);
                },
                selectedDate.get(Calendar.YEAR),
                selectedDate.get(Calendar.MONTH),
                selectedDate.get(Calendar.DAY_OF_MONTH)
        );

        datePickerDialog.setTitle("Confirm Deadline Date");
        datePickerDialog.show();
    }

    private void showDeadlineTimePicker(
            String event,
            Calendar selectedDate) {

        TimePickerDialog timePickerDialog = new TimePickerDialog(
                this,
                (view, hourOfDay, minute) -> {
                    selectedDate.set(Calendar.HOUR_OF_DAY, hourOfDay);
                    selectedDate.set(Calendar.MINUTE, minute);
                    selectedDate.set(Calendar.SECOND, 0);
                    selectedDate.set(Calendar.MILLISECOND, 0);

                    showReminderTimingOptions(
                            event,
                            selectedDate.getTimeInMillis()
                    );
                },
                selectedDate.get(Calendar.HOUR_OF_DAY),
                selectedDate.get(Calendar.MINUTE),
                false
        );

        timePickerDialog.setTitle("Confirm Deadline Time");
        timePickerDialog.show();
    }

    private void showReminderTimingOptions(
            String event,
            long deadlineMillis) {

        String[] options = {
                "At the deadline",
                "1 hour before",
                "1 day before"
        };

        long[] offsets = {
                0L,
                60L * 60L * 1000L,
                24L * 60L * 60L * 1000L
        };

        new AlertDialog.Builder(this)
                .setTitle("When should we remind you?")
                .setItems(options, (dialog, which) -> {
                    long reminderTime =
                            deadlineMillis - offsets[which];

                    scheduleDeadlineReminder(
                            event,
                            reminderTime,
                            deadlineMillis,
                            options[which]
                    );
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    // Schedule the alarm and save its details in SQLite.
    private void scheduleDeadlineReminder(
            String event,
            long reminderTime,
            long deadlineMillis,
            String timingLabel) {

        if (reminderTime <= System.currentTimeMillis()) {
            Toast.makeText(
                    this,
                    "That reminder time is in the past. "
                            + "Choose a future deadline or an earlier reminder option.",
                    Toast.LENGTH_LONG
            ).show();
            return;
        }

        if (!hasNotificationPermission()) {
            Toast.makeText(
                    this,
                    "Notification permission is required.",
                    Toast.LENGTH_LONG
            ).show();

            requestNotificationPermission();
            return;
        }

        AlarmManager alarmManager =
                (AlarmManager) getSystemService(ALARM_SERVICE);

        if (alarmManager == null) {
            Toast.makeText(
                    this,
                    "Alarm service unavailable.",
                    Toast.LENGTH_LONG
            ).show();
            return;
        }

        // Avoid the test notification ID and reserve a positive ID.
        int reminderId;

        do {
            reminderId = (int) (
                    System.currentTimeMillis() & 0x7fffffff
            );
        } while (reminderId == SCHEDULED_TEST_ID || reminderId == 0);

        Intent intent = new Intent(
                this,
                DeadlineReminderReceiver.class
        );

        intent.putExtra("title", "Upcoming Deadline");

        intent.putExtra(
                "message",
                event + ". Check your document for the requirements."
        );

        intent.putExtra("notificationId", reminderId);

        PendingIntent pendingIntent = PendingIntent.getBroadcast(
                this,
                reminderId,
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT
                        | PendingIntent.FLAG_IMMUTABLE
        );

        try {
            alarmManager.setAndAllowWhileIdle(
                    AlarmManager.RTC_WAKEUP,
                    reminderTime,
                    pendingIntent
            );

            boolean saved = databaseHelper.saveReminder(
                    reminderId,
                    event,
                    deadlineMillis,
                    reminderTime,
                    timingLabel
            );

            if (!saved) {
                alarmManager.cancel(pendingIntent);
                pendingIntent.cancel();

                Toast.makeText(
                        this,
                        "Reminder could not be saved. Please try again.",
                        Toast.LENGTH_LONG
                ).show();

                return;
            }

            java.text.SimpleDateFormat formatter =
                    new java.text.SimpleDateFormat(
                            "dd MMM yyyy, hh:mm a",
                            java.util.Locale.getDefault()
                    );

            String formattedReminderTime =
                    formatter.format(reminderTime);

            Toast.makeText(
                    this,
                    "Reminder scheduled for "
                            + formattedReminderTime,
                    Toast.LENGTH_LONG
            ).show();

        } catch (SecurityException e) {
            Toast.makeText(
                    this,
                    "Unable to schedule the reminder.",
                    Toast.LENGTH_LONG
            ).show();

            android.util.Log.e(
                    "DocumentDecoder",
                    "Could not schedule deadline reminder",
                    e
            );

        } catch (Exception e) {
            android.util.Log.e(
                    "DocumentDecoder",
                    "Could not save the scheduled reminder.",
                    e
            );

            Toast.makeText(
                    this,
                    "Unable to save the reminder.",
                    Toast.LENGTH_LONG
            ).show();
        }
    }

    // ---------------------------------------------------------
    // PDF UPLOAD AND TEXT EXTRACTION
    // ---------------------------------------------------------

    private void handleSelectedPdf(Uri uri) {
        if (uri == null) {
            return;
        }

        setControlsEnabled(false);
        loadingIndicator.setVisibility(View.VISIBLE);
        statusMessage.setVisibility(View.VISIBLE);
        statusMessage.setText("Extracting PDF text...");

        executor.execute(() -> {
            try (InputStream input =
                         getContentResolver().openInputStream(uri)) {

                if (input == null) {
                    throw new Exception("Could not open this PDF file.");
                }

                try (PDDocument document = PDDocument.load(input)) {
                    if (document.isEncrypted()) {
                        throw new Exception(
                                "This PDF is password-protected and cannot be read."
                        );
                    }

                    String extractedText =
                            new PDFTextStripper()
                                    .getText(document)
                                    .trim();

                    if (extractedText.isEmpty()) {
                        throw new Exception(
                                "No selectable text found. "
                                        + "This may be a scanned or image-based PDF."
                        );
                    }

                    if (extractedText.length() > MAX_DOCUMENT_LENGTH) {
                        throw new Exception(
                                "This PDF exceeds the 20,000-character limit. "
                                        + "Please use a shorter document."
                        );
                    }

                    String fileName = getFileName(uri);

                    runOnUiThread(() -> {
                        documentTitleInput.setText(fileName);
                        documentTextInput.setText(extractedText);

                        statusMessage.setText(
                                "PDF loaded. Tap Analyze Document to continue."
                        );

                        setControlsEnabled(true);
                        loadingIndicator.setVisibility(View.GONE);

                        resultsContainer.removeAllViews();
                        resultsContainer.setVisibility(View.GONE);

                        Toast.makeText(
                                MainActivity.this,
                                "PDF loaded successfully.",
                                Toast.LENGTH_SHORT
                        ).show();
                    });
                }

            } catch (Exception e) {
                String message = e.getMessage();

                if (message == null || message.trim().isEmpty()
                        || message.contains("End of data reached")
                        || message.contains("PDF header not found")) {
                    message = "Unable to read the selected PDF. The file may be empty or corrupted.";
                }

                final String errorMessage = message;

                runOnUiThread(() -> {
                    statusMessage.setText(errorMessage);
                    setControlsEnabled(true);
                    loadingIndicator.setVisibility(View.GONE);
                });
            }
        });
    }

    private String getFileName(Uri uri) {
        String fileName = "Selected document.pdf";

        try (Cursor cursor = getContentResolver().query(
                uri, null, null, null, null)) {

            if (cursor != null && cursor.moveToFirst()) {
                int index = cursor.getColumnIndex(
                        OpenableColumns.DISPLAY_NAME
                );

                if (index >= 0) {
                    String name = cursor.getString(index);

                    if (name != null && !name.trim().isEmpty()) {
                        fileName = name;
                    }
                }
            }
        } catch (Exception ignored) {
            // Keep the default name if metadata is unavailable.
        }

        return fileName;
    }

    // ---------------------------------------------------------
    // CHARACTER COUNTER
    // ---------------------------------------------------------

    private void setupCharacterCounter() {
        documentTextInput.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(
                    CharSequence s,
                    int start,
                    int count,
                    int after) {
            }

            @Override
            public void onTextChanged(
                    CharSequence s,
                    int start,
                    int before,
                    int count) {

                characterCount.setText(
                        s.length() + " / " + MAX_DOCUMENT_LENGTH
                                + " characters"
                );

                int color = s.length() > MAX_DOCUMENT_LENGTH
                        ? Color.RED
                        : Color.rgb(133, 137, 154);

                characterCount.setTextColor(color);
            }

            @Override
            public void afterTextChanged(Editable s) {
            }
        });
    }

    // ---------------------------------------------------------
    // DOCUMENT ANALYSIS
    // ---------------------------------------------------------

    private void analyzeDocument() {
        String title = documentTitleInput.getText().toString().trim();
        String text = documentTextInput.getText().toString().trim();

        if (text.isEmpty()) {
            documentTextInput.setError("Enter text or choose a PDF");
            documentTextInput.requestFocus();
            return;
        }

        if (text.length() > MAX_DOCUMENT_LENGTH) {
            Toast.makeText(
                    this,
                    "Document exceeds the 20,000-character limit.",
                    Toast.LENGTH_LONG
            ).show();
            return;
        }

        resultsContainer.removeAllViews();
        resultsContainer.setVisibility(View.GONE);

        loadingIndicator.setVisibility(View.VISIBLE);
        setControlsEnabled(false);

        statusMessage.setVisibility(View.VISIBLE);
        statusMessage.setText("Analyzing your document. Please wait...");

        executor.execute(() -> {
            HttpURLConnection connection = null;

            try {
                connection = (HttpURLConnection)
                        new URL(API_URL).openConnection();

                connection.setRequestMethod("POST");
                connection.setConnectTimeout(15000);
                connection.setReadTimeout(180000);
                connection.setDoOutput(true);

                connection.setRequestProperty(
                        "Content-Type",
                        "application/json; charset=UTF-8"
                );

                connection.setRequestProperty(
                        "Accept",
                        "application/json"
                );

                JSONObject body = new JSONObject();
                body.put("text", text);

                byte[] requestBytes = body.toString()
                        .getBytes(StandardCharsets.UTF_8);

                try (OutputStream output = connection.getOutputStream()) {
                    output.write(requestBytes);
                }

                int code = connection.getResponseCode();

                InputStream responseStream =
                        code >= 200 && code < 300
                                ? connection.getInputStream()
                                : connection.getErrorStream();

                StringBuilder response = new StringBuilder();

                if (responseStream != null) {
                    try (BufferedReader reader = new BufferedReader(
                            new InputStreamReader(
                                    responseStream,
                                    StandardCharsets.UTF_8
                            ))) {

                        String line;

                        while ((line = reader.readLine()) != null) {
                            response.append(line);
                        }
                    }
                }

                String responseBody = response.toString().trim();

                if (code < 200 || code >= 300) {
                    String errorMessage = "Server error: " + code;

                    if (!responseBody.isEmpty()) {
                        try {
                            JSONObject error =
                                    new JSONObject(responseBody);

                            errorMessage = error.optString(
                                    "error",
                                    errorMessage
                            );
                        } catch (Exception ignored) {
                            // Non-JSON error body
                        }
                    }

                    throw new Exception(errorMessage);
                }

                if (responseBody.isEmpty()) {
                    throw new Exception("Received an empty response from server.");
                }

                JSONObject responseJson;
                try {
                    responseJson = new JSONObject(responseBody);
                } catch (Exception parseException) {
                    throw new Exception("Malformed response from server: invalid JSON.");
                }

                if (!responseJson.has("result") || responseJson.isNull("result")) {
                    String err = responseJson.optString(
                            "error",
                            "Invalid response from server: missing analysis result."
                    );
                    throw new Exception(err);
                }

                JSONObject result = responseJson.optJSONObject("result");
                if (result == null) {
                    throw new Exception("Analysis result is not a JSON object.");
                }

                boolean saved = false;

                try {
                    long savedId = databaseHelper.saveAnalysis(
                            title,
                            text,
                            result
                    );

                    saved = savedId != -1;
                } catch (Exception databaseError) {
                    android.util.Log.e(
                            "DocumentDecoder",
                            "Could not save analysis",
                            databaseError
                    );
                }

                final boolean wasSaved = saved;

                runOnUiThread(() -> {
                    displayResults(title, result);

                    statusMessage.setText(
                            wasSaved
                                    ? "Analysis complete and saved to your history."
                                    : "Analysis complete, but it couldn't be saved."
                    );

                    loadingIndicator.setVisibility(View.GONE);
                    setControlsEnabled(true);
                });

            } catch (Exception e) {
                String message;

                if (e instanceof ConnectException) {
                    message = "Unable to connect to the backend server. Please verify the backend is running at " + API_URL;
                } else if (e instanceof SocketTimeoutException) {
                    message = "Connection timed out. The backend server took too long to respond.";
                } else if (e instanceof UnknownHostException) {
                    message = "Cannot resolve server host. Check your network connection.";
                } else {
                    message = e.getMessage();
                    if (message == null || message.trim().isEmpty()) {
                        message = "An unexpected error occurred during document analysis.";
                    }
                }

                final String errorMessage = message;

                runOnUiThread(() -> {
                    statusMessage.setText(
                            "Analysis failed:\n" + errorMessage
                    );

                    loadingIndicator.setVisibility(View.GONE);
                    setControlsEnabled(true);
                });

            } finally {
                if (connection != null) {
                    connection.disconnect();
                }
            }
        });
    }

    // ---------------------------------------------------------
    // ANALYSIS HISTORY
    // ---------------------------------------------------------

    private void showAnalysisHistory() {
        ArrayList<Long> analysisIds = new ArrayList<>();
        ArrayList<String> historyTitles = new ArrayList<>();
        ArrayList<String> historyLabels = new ArrayList<>();

        Cursor cursor = null;

        try {
            cursor = databaseHelper.getAllAnalyses();

            int idColumn = cursor.getColumnIndexOrThrow(
                    DatabaseHelper.COLUMN_ID
            );

            int titleColumn = cursor.getColumnIndexOrThrow(
                    DatabaseHelper.COLUMN_TITLE
            );

            int dateColumn = cursor.getColumnIndexOrThrow(
                    DatabaseHelper.COLUMN_CREATED_AT
            );

            while (cursor.moveToNext()) {
                long id = cursor.getLong(idColumn);
                String title = cursor.getString(titleColumn);
                String date = cursor.getString(dateColumn);

                analysisIds.add(id);
                historyTitles.add(title);
                historyLabels.add(title + "\n" + date);
            }

        } catch (Exception e) {
            Toast.makeText(
                    this,
                    "Unable to load analysis history.",
                    Toast.LENGTH_LONG
            ).show();
            return;

        } finally {
            if (cursor != null) {
                cursor.close();
            }
        }

        if (historyLabels.isEmpty()) {
            new AlertDialog.Builder(this)
                    .setTitle("Analysis History")
                    .setMessage(
                            "No saved analyses yet. Analyze a document "
                                    + "to see it here."
                    )
                    .setPositiveButton("OK", null)
                    .show();

            return;
        }

        ArrayAdapter<String> adapter = new ArrayAdapter<>(
                this,
                android.R.layout.simple_list_item_1,
                historyLabels
        );

        AlertDialog historyDialog = new AlertDialog.Builder(this)
                .setTitle("Your Analysis History")
                .setAdapter(adapter, (dialog, which) -> {
                    long selectedId = analysisIds.get(which);
                    String selectedTitle = historyTitles.get(which);
                    showHistoryItemOptions(selectedId, selectedTitle);
                })
                .setNegativeButton("Close", null)
                .create();

        historyDialog.setOnShowListener(d -> {
            if (historyDialog.getListView() != null) {
                historyDialog.getListView().setOnItemLongClickListener(
                        (parent, view, position, id) -> {
                            historyDialog.dismiss();
                            long selectedId = analysisIds.get(position);
                            String selectedTitle = historyTitles.get(position);
                            confirmDeleteAnalysis(selectedId, selectedTitle);
                            return true;
                        }
                );
            }
        });

        historyDialog.show();
    }

    private void showHistoryItemOptions(long selectedId, String title) {
        String[] options = {
                "Open Analysis",
                "Delete Analysis"
        };

        new AlertDialog.Builder(this)
                .setTitle(title)
                .setItems(options, (dialog, which) -> {
                    if (which == 0) {
                        openSavedAnalysis(selectedId);
                    } else if (which == 1) {
                        confirmDeleteAnalysis(selectedId, title);
                    }
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    private void confirmDeleteAnalysis(long selectedId, String title) {
        new AlertDialog.Builder(this)
                .setTitle("Delete Analysis")
                .setMessage(
                        "Are you sure you want to delete this saved analysis?\n\n\""
                                + title + "\""
                )
                .setPositiveButton("Delete", (dialog, which) -> {
                    int deleted = databaseHelper.deleteAnalysis(selectedId);

                    if (deleted > 0) {
                        Toast.makeText(
                                MainActivity.this,
                                "Analysis deleted.",
                                Toast.LENGTH_SHORT
                        ).show();
                    } else {
                        Toast.makeText(
                                MainActivity.this,
                                "Analysis could not be deleted.",
                                Toast.LENGTH_SHORT
                        ).show();
                    }

                    showAnalysisHistory();
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    private void openSavedAnalysis(long id) {
        Cursor cursor = null;

        try {
            cursor = databaseHelper.getAnalysisById(id);

            if (cursor == null || !cursor.moveToFirst()) {
                Toast.makeText(
                        this,
                        "This saved analysis could not be found.",
                        Toast.LENGTH_SHORT
                ).show();
                return;
            }

            int titleColumn = cursor.getColumnIndexOrThrow(
                    DatabaseHelper.COLUMN_TITLE
            );

            int textColumn = cursor.getColumnIndexOrThrow(
                    DatabaseHelper.COLUMN_DOCUMENT_TEXT
            );

            int resultColumn = cursor.getColumnIndexOrThrow(
                    DatabaseHelper.COLUMN_RESULT_JSON
            );

            String title = cursor.getString(titleColumn);
            String documentText = cursor.getString(textColumn);
            String resultJson = cursor.getString(resultColumn);

            JSONObject result = new JSONObject(resultJson);

            documentTitleInput.setText(title == null ? "" : title);
            documentTextInput.setText(documentText == null ? "" : documentText);

            displayResults(title == null ? "" : title, result);

            statusMessage.setVisibility(View.VISIBLE);
            statusMessage.setText(
                    "Showing saved analysis. No new AI request was made."
            );

            Toast.makeText(
                    this,
                    "Saved analysis opened.",
                    Toast.LENGTH_SHORT
            ).show();

        } catch (Exception e) {
            Toast.makeText(
                    this,
                    "Unable to open saved analysis: " + e.getMessage(),
                    Toast.LENGTH_LONG
            ).show();

        } finally {
            if (cursor != null) {
                cursor.close();
            }
        }
    }

    // ---------------------------------------------------------
    // DISPLAY ANALYSIS RESULTS
    // ---------------------------------------------------------

    private void displayResults(String title, JSONObject result) {
        resultsContainer.removeAllViews();
        resultsContainer.setVisibility(View.VISIBLE);

        addText(
                resultsContainer,
                "YOUR ANALYSIS",
                12,
                Color.rgb(82, 101, 216),
                true
        );

        if (title != null && !title.trim().isEmpty()) {
            addText(
                    resultsContainer,
                    title.trim(),
                    22,
                    Color.rgb(32, 36, 58),
                    true
            );
        }

        String summary = result.optString("summary", "").trim();
        if (summary.isEmpty()) {
            summary = "No summary available.";
        }

        addCard(
                "Summary",
                "A quick overview of the document",
                summary,
                "#5265D8"
        );

        addObjectSection(
                "Action items",
                "Tasks identified in the document",
                result.optJSONArray("tasks"),
                new String[]{
                        "task", "deadline", "priority", "evidence"
                },
                "#477B65"
        );

        addObjectSection(
                "Deadlines",
                "Dates and their associated requirements",
                result.optJSONArray("deadlines"),
                new String[]{
                        "event", "date_or_time", "evidence"
                },
                "#B77928"
        );

        addObjectSection(
                "Required documents",
                "Items you may need to prepare",
                result.optJSONArray("required_documents"),
                new String[]{
                        "document", "purpose", "evidence"
                },
                "#5265D8"
        );

        addObjectSection(
                "Conditions",
                "Rules, restrictions, and eligibility criteria",
                result.optJSONArray("conditions"),
                new String[]{
                        "condition", "evidence"
                },
                "#9A5A8D"
        );

        addSimpleSection(
                "Important details",
                result.optJSONArray("important_details"),
                "#527A91"
        );

        addSimpleSection(
                "Uncertainties",
                result.optJSONArray("uncertainties"),
                "#B77928"
        );

        TextView disclaimer = new TextView(this);

        disclaimer.setText(
                "AI-generated analysis can contain errors. "
                        + "Verify important details against the original document."
        );

        disclaimer.setTextSize(12);
        disclaimer.setTextColor(Color.rgb(104, 109, 128));
        disclaimer.setPadding(dp(4), dp(12), dp(4), dp(20));

        resultsContainer.addView(disclaimer);
    }

    private void addObjectSection(
            String heading,
            String subtitle,
            JSONArray items,
            String[] keys,
            String accentColor) {

        LinearLayout section = createCardContainer(accentColor);

        addText(section, heading, 18,
                Color.rgb(32, 36, 58), true);

        addText(section, subtitle, 12,
                Color.rgb(104, 109, 128), false);

        section.addView(createDivider());

        if (items == null || items.length() == 0) {
            addText(
                    section,
                    "No items identified in this category.",
                    14,
                    Color.rgb(104, 109, 128),
                    false
            );
        } else {
            for (int i = 0; i < items.length(); i++) {
                Object item = items.opt(i);

                if (item instanceof JSONObject) {
                    JSONObject object = (JSONObject) item;

                    addText(
                            section,
                            "Item " + (i + 1),
                            14,
                            Color.rgb(82, 101, 216),
                            true
                    );

                    boolean found = false;

                    for (String key : keys) {
                        if (!object.has(key) || object.isNull(key)) {
                            continue;
                        }

                        String value = object.optString(key, "").trim();

                        if (value.isEmpty()) {
                            continue;
                        }

                        addField(section, getLabel(key), value);
                        found = true;
                    }

                    if (!found) {
                        addText(
                                section,
                                object.toString(),
                                13,
                                Color.DKGRAY,
                                false
                        );
                    }

                    if ("Deadlines".equals(heading)) {
                        String event = object.optString(
                                "event",
                                "Document deadline"
                        ).trim();

                        if (event.isEmpty()) {
                            event = "Document deadline";
                        }

                        String extractedDate = object.optString(
                                "date_or_time",
                                ""
                        ).trim();

                        addDeadlineReminderButton(
                                section,
                                event,
                                extractedDate
                        );
                    }

                } else {
                    addText(
                            section,
                            String.valueOf(item),
                            14,
                            Color.DKGRAY,
                            false
                    );
                }

                if (i < items.length() - 1) {
                    section.addView(createDivider());
                }
            }
        }

        resultsContainer.addView(section);
    }

    private void addDeadlineReminderButton(
            LinearLayout parent,
            String event,
            String extractedDate) {

        Button reminderButton = new Button(this);

        reminderButton.setText("Set Reminder");
        reminderButton.setAllCaps(false);
        reminderButton.setTextColor(Color.WHITE);
        reminderButton.setTextSize(14);

        reminderButton.setBackgroundTintList(
                android.content.res.ColorStateList.valueOf(
                        Color.rgb(183, 121, 40)
                )
        );

        LinearLayout.LayoutParams params =
                new LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.MATCH_PARENT,
                        LinearLayout.LayoutParams.WRAP_CONTENT
                );

        params.topMargin = dp(12);

        parent.addView(reminderButton, params);

        reminderButton.setOnClickListener(v ->
                promptToScheduleDeadline(event, extractedDate)
        );
    }

    private void addSimpleSection(
            String heading,
            JSONArray items,
            String accentColor) {

        LinearLayout section = createCardContainer(accentColor);

        addText(section, heading, 18,
                Color.rgb(32, 36, 58), true);

        int validCount = 0;
        if (items != null) {
            for (int i = 0; i < items.length(); i++) {
                String entry = items.optString(i, "").trim();
                if (!entry.isEmpty()) {
                    validCount++;
                    addText(
                            section,
                            "\u2022  " + entry,
                            14,
                            Color.rgb(55, 59, 75),
                            false
                    );
                }
            }
        }

        if (validCount == 0) {
            addText(
                    section,
                    "None identified.",
                    14,
                    Color.rgb(104, 109, 128),
                    false
            );
        }

        resultsContainer.addView(section);
    }

    private void addCard(
            String heading,
            String subtitle,
            String content,
            String accentColor) {

        LinearLayout card = createCardContainer(accentColor);

        addText(card, heading, 18,
                Color.rgb(32, 36, 58), true);

        addText(card, subtitle, 12,
                Color.rgb(104, 109, 128), false);

        card.addView(createDivider());

        addText(card, content, 14,
                Color.rgb(55, 59, 75), false);

        resultsContainer.addView(card);
    }

    private LinearLayout createCardContainer(String accentColor) {
        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setPadding(dp(16), dp(16), dp(16), dp(16));

        GradientDrawable background = new GradientDrawable();
        background.setColor(Color.WHITE);
        background.setCornerRadius(dp(16));
        background.setStroke(dp(1), Color.rgb(230, 232, 240));

        card.setBackground(background);
        card.setElevation(dp(2));

        LinearLayout.LayoutParams params =
                new LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.MATCH_PARENT,
                        LinearLayout.LayoutParams.WRAP_CONTENT
                );

        params.bottomMargin = dp(14);
        card.setLayoutParams(params);

        try {
            GradientDrawable stripe = new GradientDrawable();
            stripe.setColor(Color.parseColor(accentColor));
            stripe.setCornerRadius(dp(3));
            stripe.setSize(dp(4), dp(24));
        } catch (IllegalArgumentException ignored) {
            // Keep the default card styling if a color is invalid.
        }

        return card;
    }

    private void addField(
            LinearLayout parent,
            String label,
            String value) {

        TextView labelView = new TextView(this);
        labelView.setText(label.toUpperCase());
        labelView.setTextSize(10);
        labelView.setTypeface(null, Typeface.BOLD);
        labelView.setLetterSpacing(0.04f);
        labelView.setTextColor(Color.rgb(104, 109, 128));

        LinearLayout.LayoutParams labelParams =
                new LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.MATCH_PARENT,
                        LinearLayout.LayoutParams.WRAP_CONTENT
                );

        labelParams.topMargin = dp(9);
        parent.addView(labelView, labelParams);

        addText(
                parent,
                value,
                14,
                Color.rgb(55, 59, 75),
                false
        );
    }

    private void addText(
            LinearLayout parent,
            String text,
            int sizeSp,
            int color,
            boolean bold) {

        TextView view = new TextView(this);
        view.setText(text);
        view.setTextSize(sizeSp);
        view.setTextColor(color);
        view.setLineSpacing(dp(3), 1.0f);

        if (bold) {
            view.setTypeface(null, Typeface.BOLD);
        }

        LinearLayout.LayoutParams params =
                new LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.MATCH_PARENT,
                        LinearLayout.LayoutParams.WRAP_CONTENT
                );

        params.topMargin = dp(4);
        params.bottomMargin = dp(4);

        parent.addView(view, params);
    }

    private View createDivider() {
        View divider = new View(this);
        divider.setBackgroundColor(Color.rgb(235, 236, 242));

        LinearLayout.LayoutParams params =
                new LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.MATCH_PARENT,
                        dp(1)
                );

        params.topMargin = dp(10);
        params.bottomMargin = dp(10);
        divider.setLayoutParams(params);

        return divider;
    }

    private String getLabel(String key) {
        switch (key) {
            case "task":
                return "Task";
            case "event":
                return "Event";
            case "document":
                return "Document";
            case "condition":
                return "Condition";
            case "date_or_time":
                return "Date / time";
            case "deadline":
                return "Deadline";
            case "purpose":
                return "Purpose";
            case "priority":
                return "Priority";
            case "evidence":
                return "Evidence from document";
            default:
                return key.replace("_", " ");
        }
    }

    private int dp(int value) {
        return (int) (value * getResources()
                .getDisplayMetrics().density + 0.5f);
    }

    @Override
    protected void onDestroy() {
        if (databaseHelper != null) {
            databaseHelper.close();
        }
        super.onDestroy();
        executor.shutdownNow();
    }
}
