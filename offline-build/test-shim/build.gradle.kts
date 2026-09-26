// Compiles androidx.test:monitor and espresso-idling-resource from their AOSP sources
// (cloned by ../tools/fetch-sdk.sh). Only needed because Google Maven is unreachable
// in the offline build environment; the Android Studio build uses the real artifacts.
plugins { kotlin("jvm") version "2.2.21" }

val src = rootDir.resolve("../build/android-test-src")
kotlin { jvmToolchain(21) }
java {
    sourceCompatibility = JavaVersion.VERSION_11
    targetCompatibility = JavaVersion.VERSION_11
}
tasks.withType<org.jetbrains.kotlin.gradle.tasks.KotlinCompile>().configureEach {
    compilerOptions.jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_11)
}
sourceSets.main {
    java.srcDirs(src.resolve("runner/monitor/java"), src.resolve("espresso/idling_resource/java"), src.resolve("stubs"))
    kotlin.srcDirs(src.resolve("runner/monitor/java"))
    // On-device-only pieces that override hidden Instrumentation APIs; Robolectric doesn't use them.
    val onDeviceOnly = listOf(
        "**/runner/MonitoringInstrumentation.java",
        "**/internal/runner/InstrumentationConnection.java",
        "**/internal/runner/intercepting/DefaultInterceptingActivityFactory.java",
        "**/internal/runner/hidden/**",
    )
    java.exclude(onDeviceOnly)
    kotlin.exclude(onDeviceOnly)
}
dependencies {
    compileOnly(files(rootDir.resolve("../sdk/android-35.jar")))
}
tasks.withType<JavaCompile>().configureEach { options.compilerArgs.addAll(listOf("-nowarn", "-proc:none")) }
tasks.jar { archiveFileName.set("androidx-test-shim.jar") }
