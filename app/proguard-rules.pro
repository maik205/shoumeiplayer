# Add project specific ProGuard rules here.
# You can control the set of applied configuration files using the
# proguardFiles setting in build.gradle.
#
# For more details, see
#   http://developer.android.com/guide/developing/tools/proguard.html

# If your project uses WebView with JS, uncomment the following
# and specify the fully qualified class name to the JavaScript interface
# class:
#-keepclassmembers class fqcn.of.javascript.interface.for.webview {
#   public *;
#}

# Uncomment this to preserve the line number information for
# debugging stack traces.
#-keepattributes SourceFile,LineNumberTable

# If you keep the line number information, uncomment this to
# hide the original source file name.
#-renamesourcefileattribute SourceFile

# mpv_jni.cpp resolves its Java-side callbacks (eventProperty*, event, logMessage) by literal
# name+signature via JNI's GetMethodID, and its native method symbols (Java_com_maik205_mpvroid_
# MpvNative_create, etc.) are baked into libmpvroid_jni.so by name -- R8 renaming or stripping any
# member of this class breaks the native bridge silently (GetMethodID returns null and the call is
# a no-op) rather than with a crash. Keep the whole class, names and all.
-keep class com.maik205.mpvroid.MpvNative {
    *;
}