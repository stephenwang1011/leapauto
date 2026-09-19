# WorkManager creates this worker by class name at runtime.
-keep class com.leapauto.app.WidgetSyncWorker {
    public <init>(android.content.Context, androidx.work.WorkerParameters);
}

# These entry points are referenced by the Android framework and home launcher.
-keep class com.leapauto.app.ControlWidget { *; }
-keep class com.leapauto.app.CompactControlWidget { *; }

# 3D 车模 WebGL 引擎交互与资源管理保护
-keep class com.leapauto.app.ui.CarModelWebView { *; }
-keep class com.leapauto.app.ui.LeapNativeBridge { *; }
-keepclassmembers class * {
    @android.webkit.JavascriptInterface <methods>;
}
-keep class com.leapauto.app.CarModel3DManager { *; }
-keepclassmembers class * extends android.webkit.WebChromeClient { *; }
-keepclassmembers class * extends android.webkit.WebViewClient { *; }
