# Rules R8 would pick up from library META-INF/proguard files; ProGuard needs them spelled out.
-keep class kotlinx.coroutines.android.AndroidDispatcherFactory { *; }
-keep class kotlinx.coroutines.android.AndroidExceptionPreHandler { *; }
-keepnames class kotlinx.coroutines.internal.MainDispatcherFactory
-keepnames class kotlinx.coroutines.CoroutineExceptionHandler
-keepclassmembers class kotlinx.coroutines.** { volatile <fields>; }
-dontwarn java.lang.ClassValue
-dontwarn java.lang.instrument.**
-dontwarn sun.misc.**
-dontwarn kotlinx.coroutines.debug.**
-dontwarn org.codehaus.mojo.animal_sniffer.**
-dontwarn java.lang.management.**
-dontwarn javax.annotation.**
-dontnote **
-target 1.7
-dontwarn androidx.annotation.**
# ProGuard's optimizer produced unverifiable bytecode (VerifyError at startup) on
# Kotlin coroutines code; shrinking + obfuscation alone are safe. (R8 in the AGP build is unaffected.)
-dontoptimize
