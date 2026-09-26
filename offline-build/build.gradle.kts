/*
 * Offline APK build for environments without access to Google's Maven repository
 * (no Android Gradle Plugin / AndroidX). It compiles exactly the same sources as the
 * Android Studio project in ../app using only artifacts from Maven Central plus the
 * classic Android command-line tools:
 *
 *   aapt2  -> compile + link resources, generate R.java
 *   kotlinc (via the Kotlin Gradle plugin) -> JVM bytecode
 *   ProGuard -> shrink + optimize (same role R8 plays in the AGP build)
 *   dx     -> dex
 *   zipalign + apksigner -> installable APK
 *
 * Usage:  gradle -p offline-build assembleApk      (APK is copied to ../dist)
 *         gradle -p offline-build test             (JVM + Robolectric tests)
 */
import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    kotlin("jvm") version "2.2.21"
}

buildscript {
    repositories { mavenCentral() }
    dependencies { classpath("com.guardsquare:proguard-base:7.8.1") }
}

val appVersionCode = 1
val appVersionName = "1.0.0"
val appPackage = "com.netspeedtest"
val minSdk = 28
val targetSdk = 35

val appDir = rootDir.resolve("../app").normalize()
val sdkDir = rootDir.resolve("sdk")
val compileJar = sdkDir.resolve("android-35.jar")   // classes (compile classpath)
val linkJar = sdkDir.resolve("android-34.jar")      // framework resources for aapt2
val genDir = layout.buildDirectory.dir("generated/r")
val resOut = layout.buildDirectory.dir("res")
val outDir = layout.buildDirectory.dir("outputs")

fun tool(name: String) = listOf("/usr/bin/$name", "/usr/local/bin/$name").firstOrNull { File(it).exists() } ?: name

kotlin {
    jvmToolchain(21)
    compilerOptions {
        jvmTarget.set(JvmTarget.JVM_1_8)
        // dx cannot desugar invokedynamic, so generate classic classes for lambdas.
        freeCompilerArgs.addAll(
            "-Xlambdas=class", "-Xsam-conversions=class", "-Xstring-concat=inline",
            "-Xno-param-assertions", "-Xno-call-assertions", "-Xno-receiver-assertions",
        )
    }
}

java {
    sourceCompatibility = JavaVersion.VERSION_1_8
    targetCompatibility = JavaVersion.VERSION_1_8
}

sourceSets {
    main {
        kotlin.srcDir(appDir.resolve("src/main/java"))
        java.srcDir(genDir)
    }
    test {
        kotlin.srcDir(appDir.resolve("src/test/java"))
    }
}

val coroutines = "1.10.2"
dependencies {
    compileOnly(files(compileJar))
    testCompileOnly(files(compileJar))
    // Real framework classes outside the Robolectric sandbox (AGP uses a "mockable" android.jar).
    testRuntimeOnly("org.robolectric:android-all:14-robolectric-10818077")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:$coroutines")

    testImplementation(kotlin("test-junit"))
    testImplementation("junit:junit:4.13.2")
    testImplementation("org.robolectric:robolectric:4.16.1") {
        // Google-Maven-only artifacts; replaced by a jar built from AOSP sources (tools/fetch-sdk.sh).
        exclude(group = "androidx.test")
        exclude(group = "androidx.test.espresso")
    }
    testImplementation(files(sdkDir.resolve("androidx-test-shim.jar")))
}

tasks.withType<JavaCompile>().configureEach { options.compilerArgs.addAll(listOf("-Xlint:-options")) }
tasks.named<JavaCompile>("compileJava") { classpath += files(compileJar) }

// ---------------------------------------------------------------- resources
val processManifest by tasks.registering {
    val src = appDir.resolve("src/main/AndroidManifest.xml")
    val dst = layout.buildDirectory.file("manifest/AndroidManifest.xml")
    inputs.file(src)
    outputs.file(dst)
    doLast {
        val text = src.readText().replaceFirst(
            "<manifest xmlns:android=\"http://schemas.android.com/apk/res/android\">",
            "<manifest xmlns:android=\"http://schemas.android.com/apk/res/android\"\n" +
                "    package=\"$appPackage\"\n" +
                "    android:versionCode=\"$appVersionCode\"\n" +
                "    android:versionName=\"$appVersionName\">\n" +
                "    <uses-sdk android:minSdkVersion=\"$minSdk\" android:targetSdkVersion=\"$targetSdk\" />",
        )
        dst.get().asFile.apply { parentFile.mkdirs(); writeText(text) }
    }
}

val compileResources by tasks.registering(Exec::class) {
    val res = appDir.resolve("src/main/res")
    val out = resOut.get().file("compiled.zip").asFile
    inputs.dir(res)
    outputs.file(out)
    doFirst { out.parentFile.mkdirs() }
    commandLine(tool("aapt2"), "compile", "--dir", res.path, "-o", out.path)
}

val linkResources by tasks.registering(Exec::class) {
    dependsOn(processManifest, compileResources)
    val manifest = layout.buildDirectory.file("manifest/AndroidManifest.xml").get().asFile
    val compiled = resOut.get().file("compiled.zip").asFile
    val apk = resOut.get().file("resources.ap_").asFile
    val rules = resOut.get().file("aapt_rules.pro").asFile
    inputs.files(manifest, compiled, linkJar)
    outputs.files(apk, rules)
    outputs.dir(genDir)
    doFirst { genDir.get().asFile.deleteRecursively(); genDir.get().asFile.mkdirs() }
    commandLine(
        tool("aapt2"), "link", "-I", linkJar.path,
        "--manifest", manifest.path,
        "--java", genDir.get().asFile.path,
        "--proguard", rules.path,
        "--min-sdk-version", "$minSdk", "--target-sdk-version", "$targetSdk",
        "--version-code", "$appVersionCode", "--version-name", appVersionName,
        "--auto-add-overlay", "--no-version-vectors",
        "-o", apk.path, compiled.path,
    )
}

tasks.named("compileKotlin") { dependsOn(linkResources) }
tasks.named("compileJava") { dependsOn(linkResources) }

// ---------------------------------------------------------------- shrink + dex
val shrink by tasks.registering(JavaExec::class) {
    dependsOn("classes", linkResources)
    val out = layout.buildDirectory.file("proguard/app.jar").get().asFile
    val mapping = layout.buildDirectory.file("proguard/mapping.txt").get().asFile
    val runtime = configurations.runtimeClasspath.get()
    val classDirs = sourceSets.main.get().output.classesDirs
    inputs.files(classDirs, runtime, appDir.resolve("proguard-rules.pro"), resOut.get().file("aapt_rules.pro"), rootDir.resolve("offline-rules.pro"))
    outputs.files(out, mapping)
    classpath = buildscript.configurations["classpath"]
    mainClass.set("proguard.ProGuard")
    doFirst {
        out.parentFile.mkdirs()
        val injars = (classDirs.files + runtime.files).filter { it.exists() }
            .joinToString(File.pathSeparator) { "${it.path}(!META-INF/**,!**.kotlin_builtins,!**.kotlin_module,!DebugProbesKt.bin)" }
        args(
            "-injars", injars,
            "-outjars", out.path,
            "-libraryjars", compileJar.path,
            // Only for resolving invokedynamic bootstrap references while ProGuard backports them.
            "-libraryjars", "${System.getProperty("java.home")}/jmods/java.base.jmod(java/lang/invoke/LambdaMetafactory.class,java/lang/invoke/StringConcatFactory.class)",
            "-include", appDir.resolve("proguard-rules.pro").path,
            "-include", resOut.get().file("aapt_rules.pro").asFile.path,
            "-include", rootDir.resolve("offline-rules.pro").path,
            "-printmapping", mapping.path,
        )
    }
}

val dex by tasks.registering(Exec::class) {
    dependsOn(shrink)
    val jar = layout.buildDirectory.file("proguard/app.jar").get().asFile
    val out = layout.buildDirectory.file("dex/classes.dex").get().asFile
    inputs.file(jar)
    outputs.file(out)
    doFirst { out.parentFile.mkdirs() }
    commandLine(tool("dalvik-exchange"), "--dex", "--min-sdk-version=26", "--output=${out.path}", jar.path)
}

// ---------------------------------------------------------------- package + sign
val keystore = rootDir.resolve("debug.keystore")
val createKeystore by tasks.registering(Exec::class) {
    onlyIf { !keystore.exists() }
    commandLine(
        "keytool", "-genkeypair", "-keystore", keystore.path, "-storepass", "android", "-keypass", "android",
        "-alias", "androiddebugkey", "-keyalg", "RSA", "-keysize", "2048", "-validity", "10000",
        "-dname", "CN=Android Debug,O=Android,C=US",
    )
}

val assembleApk by tasks.registering {
    dependsOn(dex, linkResources, createKeystore)
    val resApk = resOut.get().file("resources.ap_").asFile
    val dexFile = layout.buildDirectory.file("dex/classes.dex").get().asFile
    val stage = layout.buildDirectory.dir("apk").get().asFile
    val finalApk = outDir.get().file("NetSpeedTest-$appVersionName.apk").asFile
    inputs.files(resApk, dexFile)
    outputs.file(finalApk)
    doLast {
        stage.deleteRecursively(); stage.mkdirs()
        val unsigned = File(stage, "unsigned.apk")
        resApk.copyTo(unsigned)
        dexFile.copyTo(File(stage, "classes.dex"))
        // kotlinx.coroutines discovers Dispatchers.Main through ServiceLoader.
        val services = File(stage, "META-INF/services").apply { mkdirs() }
        File(services, "kotlinx.coroutines.internal.MainDispatcherFactory")
            .writeText("kotlinx.coroutines.android.AndroidDispatcherFactory\n")
        File(services, "kotlinx.coroutines.CoroutineExceptionHandler")
            .writeText("kotlinx.coroutines.android.AndroidExceptionPreHandler\n")
        project.exec { workingDir = stage; commandLine("zip", "-q", "-X", "unsigned.apk", "classes.dex") }
        project.exec { workingDir = stage; commandLine("zip", "-q", "-X", "-r", "unsigned.apk", "META-INF") }
        val aligned = File(stage, "aligned.apk")
        project.exec { commandLine(tool("zipalign"), "-p", "-f", "4", unsigned.path, aligned.path) }
        finalApk.parentFile.mkdirs()
        project.exec {
            commandLine(
                tool("apksigner"), "sign", "--ks", keystore.path, "--ks-pass", "pass:android",
                "--key-pass", "pass:android", "--ks-key-alias", "androiddebugkey",
                "--min-sdk-version", "$minSdk", "--out", finalApk.path, aligned.path,
            )
        }
        project.exec { commandLine(tool("apksigner"), "verify", "--min-sdk-version", "$minSdk", finalApk.path) }
        finalApk.copyTo(rootDir.resolve("../dist/${finalApk.name}"), overwrite = true)
        println("APK: ${finalApk.path} (${finalApk.length() / 1024} KB)")
    }
}

// ---------------------------------------------------------------- tests
// Robolectric reads the linked resources exactly like AGP's includeAndroidResources.
val robolectricConfig by tasks.registering {
    dependsOn(linkResources)
    val out = layout.buildDirectory.dir("robolectric-config").get().asFile
    outputs.dir(out)
    doLast {
        val f = File(out, "com/android/tools/test_config.properties")
        f.parentFile.mkdirs()
        f.writeText(
            "android_resource_apk=${resOut.get().file("resources.ap_").asFile.path}\n" +
                "android_merged_manifest=${layout.buildDirectory.file("manifest/AndroidManifest.xml").get().asFile.path}\n" +
                "android_custom_package=$appPackage\n",
        )
    }
}

tasks.test {
    dependsOn(robolectricConfig)
    classpath += files(layout.buildDirectory.dir("robolectric-config"))
    systemProperty("screenshots.dir", rootDir.resolve("../docs/screenshots").path)
    maxHeapSize = "3g"
    testLogging { events("passed", "failed", "skipped"); showStandardStreams = true; exceptionFormat = org.gradle.api.tasks.testing.logging.TestExceptionFormat.FULL }
}

// ---------------------------------------------------------------- smoke test of the shrunk code
val smoke by sourceSets.creating { java.srcDir("src/smoke/java") }
val smokeImplementation by configurations.getting
dependencies {
    smokeImplementation(files(layout.buildDirectory.file("proguard/app.jar")))
    smokeImplementation("junit:junit:4.13.2")
    smokeImplementation("org.robolectric:robolectric:4.16.1") {
        exclude(group = "androidx.test")
        exclude(group = "androidx.test.espresso")
    }
    smokeImplementation(files(sdkDir.resolve("androidx-test-shim.jar")))
    "smokeCompileOnly"(files(compileJar))
    "smokeRuntimeOnly"("org.robolectric:android-all:14-robolectric-10818077")
}
tasks.named("compileSmokeJava") { dependsOn(shrink) }
val smokeTest by tasks.registering(Test::class) {
    dependsOn(shrink, robolectricConfig)
    testClassesDirs = smoke.output.classesDirs
    classpath = smoke.runtimeClasspath + files(layout.buildDirectory.dir("robolectric-config"))
    maxHeapSize = "3g"
    testLogging { events("passed", "failed"); showStandardStreams = true; exceptionFormat = org.gradle.api.tasks.testing.logging.TestExceptionFormat.FULL }
}
