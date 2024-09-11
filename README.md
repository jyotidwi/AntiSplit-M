# AntiSplit M
Android app to AntiSplit (merge) split APKs (APKS/XAPK/APKM) to regular .APK file

**This branch supports all Android versions but it doesn't work properly (Removing splits information from manifest not working), don't use it**

This project is a simple GUI implementation of Merge utilities from [REAndroid APKEditor](https://github.com/REAndroid/APKEditor).

There are already some apps that can perform this task like Apktool M, AntiSplit G2, NP Manager, but they are all closed source. 

In addition, Antisplit G2 (com.tilks.arscmerge), the fastest and lightest of the existing apps, has a large problem; it does not remove the information about splits in the APK from the AndroidManifest.xml. If a non-split APK contains this information it will cause an "App not installed" error on some devices. Fortunately the implementation by REAndroid contains a function to remove this automatically and it carries over to this app.

# Usage
Video - https://www.youtube.com/watch?v=Nd3vEzRWY-Q

There are 3 ways to open the split APK to be merged:
* Share the file and select AntiSplit M in the share menu
* Press (open) the file and select AntiSplit M in available options
* Open the app from launcher and press the button then select the split APK file.
   * This option does not work on Android < 4.4, use one of the 2 other options or type the path to the APK (on your device storage) into the box in the app.

Note: The APK will not be signed, you have to sign it before installing with any tool like [apk-signer](https://play.google.com/store/apps/details?id=com.haibison.apksigner). If you are planning to further modify the APK, you only need to sign it after the modifications.

Note: Some apps verify the signature of the APK or take other measures to check if the app was modified, which may cause it to crash on startup.

# Screenshots
<img src="/images/1.4.1 mainscreen.jpg" height="540" width="243" /> <img src="/images/1.4.1 dialog.jpg" height="540" width="243" /> <img src="/images/1.4.1 processed screen.jpg" height="540" width="243" />

# Todo
* Implement signing the exported APK
* support picking from installed apps
