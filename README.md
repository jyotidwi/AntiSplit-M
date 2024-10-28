# AntiSplit M
Android app to merge/"AntiSplit" split APKs (APKS/XAPK/APKM) to a regular .APK file

This project is a simple GUI implementation of Merge utilities from [REAndroid APKEditor](https://github.com/REAndroid/APKEditor).

There are already some apps that can perform this task like Apktool M, AntiSplit G2, NP Manager, but they are all closed source. 

In addition, Antisplit G2 (com.tilks.arscmerge), the fastest and lightest of the existing apps, has a large problem; it does not remove the information about splits in the APK from the AndroidManifest.xml. If a non-split APK contains this information it will cause an "App not installed" error on some devices. Fortunately the implementation by REAndroid contains a function to remove this automatically and it carries over to this app.

Version 2.x - Material You design, support Android 4.4+

Version 1.x - Support Android 1.6+

# Usage
Video - https://youtu.be/Vk566iMG6Gs

There are 3 ways to open a split APK to be merged:
* Share the file and select AntiSplit M in the share menu
* Press (open) the file and select AntiSplit M in available options
* Open the app from launcher and press the button then select the split APK file.

There is also a menu in the app that allows selecting an app installed on the device as a split APK.

Note: if you are planning to further modify the APK, you only need to sign it after the modifications.

Note: Some apps verify the signature of the APK or take other measures to check if the app was modified, which may cause it to crash on startup.

# Screenshots
| Main screen                                                                 | Settings                                                                    | Selecting from installed apps                                               |
| ---------------------------------------------------------------------------- | --------------------------------------------------------------------------- | ---------------------------------------------------------------------------- |
| ![Main screen](images/2.0%20mainscreen.jpg) | ![Settings](images/2.0%20settings.jpg) | ![Selecting from installed apps](images/2.0%20app%20list.jpg) |

| Dialog allowing splits selection                                            | Processing                                                                  | Result                                                                      |
| --------------------------------------------------------------------------- | ---------------------------------------------------------------------------- | --------------------------------------------------------------------------- |
| ![Dialog](images/2.0%20dialog.jpg) | ![Processing](images/2.0%20processing.jpg) | ![Result](images/2.0%20result.jpg) |

# Used projects

⭐ [APKEditor](https://github.com/REAndroid/APKEditor) by REAndroid, what makes it all possible
* [simplezip](https://github.com/j506/simplezip) by j506 to help in fixing APK to patch with ReVanced
* [Android port](https://github.com/MuntashirAkon/apksig-android) of apksig library by MuntashirAkon to sign APKs
* [PseudoApkSigner](https://github.com/Aefyr/PseudoApkSigner) by Aefyr for backup signing on older Android versions
* [AmbilWarna Color Picker](https://github.com/yukuku/ambilwarna)
* [android-filepicker](https://github.com/singhangadin/android-filepicker) by Angad Singh for file picker on older Android versions
  
This project is tested with BrowserStack.
