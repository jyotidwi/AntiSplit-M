package com.abdurazaaqmohammed.AntiSplit.main;

import static android.provider.Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION;

import android.Manifest;
import android.annotation.TargetApi;
import android.app.Activity;
import android.app.AlertDialog;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.os.Looper;
import android.provider.OpenableColumns;
import android.view.View;
import android.widget.ScrollView;
import android.widget.Switch;
import android.widget.TextView;
import android.widget.Toast;

import com.abdurazaaqmohammed.AntiSplit.R;
import com.reandroid.apk.ApkBundle;
import com.reandroid.apk.ApkModule;
import com.reandroid.apkeditor.merge.LogUtil;
import com.reandroid.apkeditor.merge.Merger;
import com.starry.FileUtils;

import java.io.File;
import java.io.IOException;
import java.io.OutputStream;
import java.lang.ref.WeakReference;
import java.util.List;
import java.util.Objects;

/** @noinspection deprecation*/
public class MainActivity extends Activity implements Merger.LogListener {
    private final static int REQUEST_CODE_OPEN_SPLIT_APK_TO_ANTISPLIT = 1;
    private final static int REQUEST_CODE_SAVE_APK = 2;
    private final static boolean supportsFilePicker = Build.VERSION.SDK_INT>19;
    private final static boolean supportsAsyncTask = Build.VERSION.SDK_INT >= Build.VERSION_CODES.CUPCAKE;
    private static boolean logEnabled;
    private static boolean ask;
    private static boolean showDialog;
    private Uri splitAPKUri;
    private File getTheCacheDir() {
        return (Build.VERSION.SDK_INT >= Build.VERSION_CODES.FROYO) ? this.getExternalCacheDir() : this.getCacheDir();
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.HONEYCOMB) {
            try {
                Objects.requireNonNull(getActionBar()).hide();
            } catch (NullPointerException ignored) {
            }
        }
        setContentView(R.layout.activity_main);
        deleteDir(getTheCacheDir());

        if (!supportsFilePicker) {
            findViewById(R.id.oldAndroidInfo).setVisibility(View.VISIBLE);
            // Android versions below 4.4 are too old to use the file picker for ACTION_OPEN_DOCUMENT/ACTION_CREATE_DOCUMENT. The location of the file must be manually input. The files will be saved to "AntiSplit-M" folder in the internal storage.
        }

        SharedPreferences settings = getSharedPreferences("set", Context.MODE_PRIVATE);

        // Fetch settings from SharedPreferences
        logEnabled = settings.getBoolean("logEnabled", true);
        LogUtil.setLogListener(this);
        LogUtil.setLogEnabled(logEnabled);

        Switch logSwitch = findViewById(R.id.logSwitch);
        logSwitch.setChecked(logEnabled);
        logSwitch.setOnCheckedChangeListener((buttonView, isChecked) ->{
            logEnabled = isChecked;
            LogUtil.setLogEnabled(logEnabled);
        });

        Switch askSwitch = findViewById(R.id.ask);
        if (Build.VERSION.SDK_INT > 22) {
            ask = settings.getBoolean("ask", true);
            askSwitch.setChecked(ask);
            askSwitch.setOnCheckedChangeListener((buttonView, isChecked) -> {
                ask = isChecked;
                if(!isChecked) checkStoragePerm();
            });
        } else askSwitch.setVisibility(View.INVISIBLE);

        showDialog = settings.getBoolean("showDialog", false);
        Switch showDialogSwitch = findViewById(R.id.showDialog);
        showDialogSwitch.setChecked(showDialog);
        showDialogSwitch.setOnCheckedChangeListener((buttonView, isChecked) -> showDialog = isChecked);

        findViewById(R.id.decodeButton).setOnClickListener(v -> openFilePickerOrStartProcessing());

        // Check if user shared or opened file with the app.
        final Intent fromShareOrView = getIntent();
        final String fromShareOrViewAction = fromShareOrView.getAction();
        if (Intent.ACTION_SEND.equals(fromShareOrViewAction)) {
            splitAPKUri = fromShareOrView.getParcelableExtra(Intent.EXTRA_STREAM);
        } else if (Intent.ACTION_VIEW.equals(fromShareOrViewAction)) {
            splitAPKUri = fromShareOrView.getData();
        }
        if (splitAPKUri != null) {
            selectDirToSaveAPKOrSaveNow();
        }
    }

    final File getAntisplitMFolder() {
        final File bruh = new File(Environment.getExternalStorageDirectory() + File.separator + "AntiSplit-M");
        if(!bruh.exists()) bruh.mkdir();
        return bruh;
    }

    @TargetApi(Build.VERSION_CODES.M)
    private void checkStoragePerm() {
        final boolean write = Build.VERSION.SDK_INT < 30;
        final boolean noPermission = write ? checkSelfPermission(Manifest.permission.WRITE_EXTERNAL_STORAGE) == PackageManager.PERMISSION_DENIED : !Environment.isExternalStorageManager();
        if (noPermission) {
            Toast.makeText(getApplicationContext(), getString(R.string.grant_storage), Toast.LENGTH_LONG).show();
            if(write) requestPermissions(new String[]{Manifest.permission.WRITE_EXTERNAL_STORAGE}, 0);
            else {
                Intent intent = new Intent(ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION, Uri.parse("package:" + getPackageName()));
                startActivityForResult(intent, 0);
            }
        }
    }

    @Override
    protected void onPause() {
        final SharedPreferences.Editor e = getSharedPreferences("set", Context.MODE_PRIVATE).edit();
        e.putBoolean("logEnabled", logEnabled).putBoolean("ask", ask).putBoolean("showDialog", showDialog);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.GINGERBREAD) {
            e.apply();
        } else e.commit();
        super.onPause();
    }

    @Override
    public void onLog(String log) {
        runOnUiThread(() -> {
            TextView logTextView = findViewById(R.id.logField);
            logTextView.append(log + "\n");
            ScrollView scrollView = findViewById(R.id.scrollView);
            scrollView.post(() -> scrollView.fullScroll(View.FOCUS_DOWN));
        });
    }
    private void openFilePickerOrStartProcessing() {
        if (supportsFilePicker) {
            Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
            intent.addCategory(Intent.CATEGORY_OPENABLE);
            intent.setType("*/*");
            intent.putExtra(Intent.EXTRA_MIME_TYPES, new String[] {"application/zip", "application/octet-stream"}); // XAPK is octet-stream
            startActivityForResult(intent, REQUEST_CODE_OPEN_SPLIT_APK_TO_ANTISPLIT);
        } else {
            TextView workingFileField = findViewById(R.id.workingFileField);
            final String workingFilePath = workingFileField.getText().toString();
            splitAPKUri = Uri.fromFile(new File(workingFilePath));
            final Uri output = Uri.fromFile(new File(getAntisplitMFolder() + File.separator + workingFilePath.substring(workingFilePath.lastIndexOf("/") + 1).replaceFirst("\\.(?:xapk|aspk|apk[sm])", "_antisplit.apk")));
            if (supportsAsyncTask) {
                new ProcessTask(this).execute(output);
            } else {
                process(output, new WeakReference<>(this));
            }
        }
    }

    private void process(Uri outputUri, WeakReference<MainActivity> activityReference) {
        runOnUiThread(() -> ((TextView)findViewById(R.id.logField)).setText(""));
        if (getTheCacheDir() != null) {
            deleteDir(getTheCacheDir());
        }

        Uri xapkUri;
        try {
            xapkUri = Objects.requireNonNull(splitAPKUri.getPath()).endsWith("xapk") ? splitAPKUri : null;
        } catch (NullPointerException ignored) {
            xapkUri = null;
        }

        try {
            Merger.run(getContentResolver().openInputStream(splitAPKUri), getTheCacheDir(), getContentResolver().openOutputStream(outputUri), xapkUri, this, supportsAsyncTask && showDialog, activityReference);
        } catch (IOException e) {
            showError(e);
        }
    }

    public static void deleteDir(File dir){
        String[] children = dir.list();
        if (children != null) {
            for (String child : children) {
                // There should never be folders in here.
                new File(dir, child).delete();
            }
        }
    }

    @Override
    protected void onDestroy() {
        deleteDir(getTheCacheDir());
        super.onDestroy();
    }


    @TargetApi(Build.VERSION_CODES.CUPCAKE)
    private static class ProcessTask extends AsyncTask<Uri, Void, String> {
        private final WeakReference<MainActivity> activityReference;

        // only retain a weak reference to the activity
        ProcessTask(MainActivity context) {
            activityReference = new WeakReference<>(context);
        }
        private void createHandler(Uri uri) {
            Thread thread = new Thread() {
                public void run() {
                    Looper.prepare();

                    final Handler handler = new Handler();
                    handler.postDelayed(new Runnable() {
                        @Override
                        public void run() {
                            activityReference.get().process(uri, activityReference);
                            handler.removeCallbacks(this);
                            Looper.myLooper().quit();
                        }
                    }, 2000);

                    Looper.loop();
                }
            };
            thread.start();
        }
        @Override
        protected String doInBackground(Uri... uris) {
            MainActivity activity = activityReference.get();
            if(showDialog) createHandler(uris[0]);
            else {
                activity.process(uris[0], activityReference);
                activity.showSuccess();
            }
            return null;
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (resultCode == RESULT_OK && data != null) {
            Uri uri = data.getData();
            if (uri != null) {
                switch(requestCode) {
                    case 0:
                        checkStoragePerm();
                    break;
                    case REQUEST_CODE_OPEN_SPLIT_APK_TO_ANTISPLIT:
                        splitAPKUri = uri;
                        selectDirToSaveAPKOrSaveNow();
                    break;
                    case REQUEST_CODE_SAVE_APK:
                        if (supportsAsyncTask) {
                            new ProcessTask(this).execute(uri);
                        } else {
                            process(uri, new WeakReference<>(this));
                        }
                        break;
                }
            }
        }
    }
    @TargetApi(Build.VERSION_CODES.CUPCAKE)
    public void showApkSelectionDialog(List<File> apkFiles, Context c, OutputStream out) {
        CharSequence[] apkNames = new CharSequence[apkFiles.size() + 1];
        boolean[] checkedItems = new boolean[apkFiles.size() + 1];

        apkNames[0] = "Select All";
        for (int i = 0; i < apkFiles.size(); i++) {
            apkNames[i + 1] = apkFiles.get(i).getName();
            checkedItems[i + 1] = false;
        }

        AlertDialog.Builder builder = (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.HONEYCOMB) ? new AlertDialog.Builder(c, R.style.CustomDialogTheme) :  new AlertDialog.Builder(c);
        builder.setTitle("Select APK files");
        builder.setMultiChoiceItems(apkNames, checkedItems, (dialog, which, isChecked) -> {
            if (which == 0) {
                // "Select All" option
                for (int i = 1; i < checkedItems.length; i++) {
                    checkedItems[i] = isChecked;
                    ((AlertDialog) dialog).getListView().setItemChecked(i, isChecked);
                }
            } else {
                // Uncheck "Select All" if any individual item is unchecked
                if (!isChecked) {
                    checkedItems[0] = false;
                    ((AlertDialog) dialog).getListView().setItemChecked(0, false);
                }
            }
        });
        builder.setPositiveButton("OK", (dialog, which) -> {
            // Perform actions on OK button click
            boolean anyFileSelected = false;
            for (int i = 1; i < checkedItems.length; i++) {
                if (checkedItems[i]) {
                    anyFileSelected = true;
                    break;
                }
            }

            if (anyFileSelected) {
                for (int i = 1; i < checkedItems.length; i++) {
                    if (!checkedItems[i]) {
                        apkFiles.get(i - 1).delete();
                    }
                }
                new ProcessFromDialogTask(this, getTheCacheDir(), out).execute();
            }
        });
        builder.setNegativeButton("Cancel", null);

        runOnUiThread(() -> {
            AlertDialog dialog = builder.create();
            dialog.show();
        });
    }

    @TargetApi(Build.VERSION_CODES.CUPCAKE)
    private static class ProcessFromDialogTask extends AsyncTask<Uri, Void, String> {
        private final WeakReference<MainActivity> activityReference;

        File cacheDir;
        OutputStream outputStream;

        // only retain a weak reference to the activity
        ProcessFromDialogTask(MainActivity context, File cacheDirectory, OutputStream out) {
            activityReference = new WeakReference<>(context);
            cacheDir = cacheDirectory;
            outputStream = out;
        }

        @Override
        protected String doInBackground(Uri... uris) {
            MainActivity activity = activityReference.get();
            try {
                ApkBundle bundle = new ApkBundle();
                bundle.loadApkDirectory(cacheDir, false);
                LogUtil.logMessage("Found modules: " + bundle.getApkModuleList().size());

                ApkModule mergedModule = bundle.mergeModules();

                LogUtil.logMessage("Saving...");
                mergedModule.writeApk(outputStream);
                mergedModule.close();
                outputStream.close();
                bundle.close();
            } catch (IOException e) {
                activity.showError(e);
            }
            activity.showSuccess();
            return null;
        }
    }

    private void showSuccess() {
        final String successMessage = getString(R.string.success_saved);
        LogUtil.logMessage(successMessage);
        runOnUiThread(() -> Toast.makeText(this, successMessage, Toast.LENGTH_SHORT).show());
    }

    private void showError(Exception e) {
        final String mainErr = e.toString();
        StringBuilder stackTrace = new StringBuilder().append(mainErr);
        for(StackTraceElement line : e.getStackTrace()) {
            stackTrace.append(line);
        }
        runOnUiThread(() -> {
            TextView errorBox = findViewById(R.id.errorField);
            errorBox.setVisibility(View.VISIBLE);
            errorBox.setText(stackTrace);
            Toast.makeText(this, mainErr, Toast.LENGTH_SHORT).show();
        });
    }

    private void showError(String err) {
        runOnUiThread(() -> {
            TextView errorBox = findViewById(R.id.errorField);
            errorBox.setVisibility(View.VISIBLE);
            errorBox.setText(err);
            Toast.makeText(this, err, Toast.LENGTH_SHORT).show();
        });
    }

    private static String getOriginalFileName(Context context, Uri uri) {
        String result = null;
        try {
            if (Objects.equals(uri.getScheme(), "content")) {
                try (Cursor cursor = context.getContentResolver().query(uri, null, null, null, null)) {
                    if (cursor != null && cursor.moveToFirst()) {
                        result = cursor.getString(cursor.getColumnIndexOrThrow(OpenableColumns.DISPLAY_NAME));
                    }
                }
            }
            if (result == null) {
                result = uri.getPath();
                int cut = Objects.requireNonNull(result).lastIndexOf('/'); // Ensure it throw the NullPointerException here to be caught
                if (cut != -1) {
                    result = result.substring(cut + 1);
                }
            }
        } catch (NullPointerException | IllegalArgumentException ignored) {
            result = "filename_not_found";
        }
        return result;
    }

    private void selectDirToSaveAPKOrSaveNow() {
        if (supportsFilePicker){
         if(ask) {
             Intent intent = new Intent(Intent.ACTION_CREATE_DOCUMENT);
             intent.addCategory(Intent.CATEGORY_OPENABLE);
             intent.setType("application/vnd.android.package-archive");
             intent.putExtra(Intent.EXTRA_TITLE, getOriginalFileName(this, splitAPKUri));
             startActivityForResult(intent, REQUEST_CODE_SAVE_APK);
         } else saveNow();
        } else saveNow();
    }

    private void saveNow() {
        checkStoragePerm();

        final String originalFilePath = new FileUtils(this).getPath(splitAPKUri);
        runOnUiThread(() -> ((TextView)findViewById(R.id.workingFileField)).setText(originalFilePath));

        String newFilePath = originalFilePath.replaceFirst("\\.(?:xapk|aspk|apk[sm])", "_antisplit.apk");
        if(newFilePath.length() == 0 || newFilePath.startsWith("/data/")) { // when shared it in /data/ bruh
            newFilePath = getAntisplitMFolder() + File.separator + newFilePath.substring(newFilePath.lastIndexOf(File.separator) + 1);
            showError(getString(R.string.no_filepath));
        }
        LogUtil.logMessage(getString(R.string.output) + " " + newFilePath);
        final Uri output = Uri.fromFile(new File(newFilePath));
        if (supportsAsyncTask) {
            new ProcessTask(this).execute(output);
        } else process(output, new WeakReference<>(this));
    }
}