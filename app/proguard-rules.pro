# Room generated implementations are reached by name.
-keep class * extends androidx.room.RoomDatabase { <init>(); }
-dontwarn androidx.room.paging.**

# Backup archives are (de)serialised by kotlinx.serialization; keep the generated serializers.
-keepclassmembers class com.khmercalendar.data.backup.** {
    *** Companion;
    kotlinx.serialization.KSerializer serializer(...);
}
-keepclasseswithmembers class com.khmercalendar.data.backup.** {
    kotlinx.serialization.KSerializer serializer(...);
}

# Glance widgets are instantiated from the manifest.
-keep class com.khmercalendar.widget.** { *; }

# The MediaPipe backend is optional and looked up at runtime.
-dontwarn com.google.mediapipe.**
-keep class com.google.mediapipe.tasks.genai.** { *; }
