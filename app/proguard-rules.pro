# Important instructions:
# - Preserve entry points (MainActivity, FocusRouter, SleepReminderReceiver, AlertsListener) under R8 shrinking.
# - Strip debug logging and Kotlin assertions to reduce APK footprint and runtime overhead.

# Aggressive optimization and repackaging for compact dex output.
-optimizationpasses 9
-allowaccessmodification
-mergeinterfacesaggressively
-repackageclasses ''

# Keep entry components referenced by manifest or reflection.
-keep public class shree.tvhome.MainActivity
-keep public class shree.tvhome.FocusRouter
-keep public class shree.tvhome.core.SleepReminderReceiver
-keep public class shree.tvhome.core.AlertsListener

# Remove runtime Kotlin null/argument check overhead after optimization.
-assumenosideeffects class kotlin.jvm.internal.Intrinsics {
    public static void checkNotNull(...);
    public static void checkNotNullParameter(...);
    public static void checkExpressionValueIsNotNull(...);
    public static void checkNotNullExpressionValue(...);
    public static void checkParameterIsNotNull(...);
    public static void throwUninitializedPropertyAccessException(...);
}

# Strip verbose/debug/info/warn log calls from compiled bytecode.
-assumenosideeffects class android.util.Log {
    public static int d(...);
    public static int v(...);
    public static int i(...);
    public static int w(...);
}

-dontwarn kotlin.**
-dontwarn kotlinx.**
