package com.abdurazaaqmohammed.AntiSplit.main;

import android.os.Build;

public class LegacyUtils {

    public static final boolean supportsFileChannel = Build.VERSION.SDK_INT >= Build.VERSION_CODES.O;
    public static final boolean supportsArraysCopyOfAndDownloadManager = Build.VERSION.SDK_INT >= Build.VERSION_CODES.GINGERBREAD;
    public static final boolean supportsWriteExternalStorage = Build.VERSION.SDK_INT < 30;
    public final static boolean doesNotSupportInbuiltAndroidFilePicker = Build.VERSION.SDK_INT < 19;
    public final static boolean supportsActionBar = Build.VERSION.SDK_INT > 10;
    public final static boolean canSetNotificationBarTransparent = Build.VERSION.SDK_INT > 20;

}