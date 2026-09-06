# WorkManager creates this worker by class name at runtime.
-keep class com.leapauto.app.WidgetSyncWorker {
    public <init>(android.content.Context, androidx.work.WorkerParameters);
}

# These entry points are referenced by the Android framework and home launcher.
-keep class com.leapauto.app.ControlWidget { *; }
-keep class com.leapauto.app.CompactControlWidget { *; }
