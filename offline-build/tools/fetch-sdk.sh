#!/usr/bin/env bash
# Prepares offline-build/sdk/ for environments where dl.google.com / Google Maven are
# unreachable. Downloads AOSP-built framework jars (classes + framework resources) and
# builds the two tiny androidx.test libraries Robolectric needs from their GitHub sources.
set -euo pipefail
cd "$(dirname "$0")/.."
mkdir -p sdk
RAW=https://raw.githubusercontent.com/Reginer/aosp-android-jar/main
[ -f sdk/android-35.jar ] || curl -fsSL -o sdk/android-35.jar "$RAW/android-35/android.jar"
# aapt2 from Ubuntu (Android 14) cannot parse the API 35 resource table, so resources link against API 34.
[ -f sdk/android-34.jar ] || curl -fsSL -o sdk/android-34.jar "$RAW/android-34/android.jar"

if [ ! -f sdk/androidx-test-shim.jar ]; then
  src=build/android-test-src
  rm -rf "$src"
  git clone -q --depth 1 --filter=blob:none --sparse https://github.com/android/android-test "$src"
  git -C "$src" sparse-checkout set runner/monitor/java espresso/idling_resource/java
  stubs="$src/stubs"
  mkdir -p "$stubs/androidx/annotation" "$stubs/androidx/tracing" "$stubs/com/google/errorprone/annotations"
  for a in NonNull Nullable VisibleForTesting; do
    printf 'package androidx.annotation;\npublic @interface %s { int otherwise() default 0; }\n' "$a" > "$stubs/androidx/annotation/$a.java"
  done
  printf 'package androidx.annotation;\npublic @interface ChecksSdkIntAtLeast { int api() default -1; String codename() default ""; int parameter() default -1; int lambda() default -1; }\n' > "$stubs/androidx/annotation/ChecksSdkIntAtLeast.java"
  printf 'package androidx.annotation;\npublic @interface RestrictTo { Scope[] value(); enum Scope { LIBRARY, LIBRARY_GROUP, LIBRARY_GROUP_PREFIX, GROUP_ID, TESTS, SUBCLASSES } }\n' > "$stubs/androidx/annotation/RestrictTo.java"
  printf 'package androidx.tracing;\npublic final class Trace { public static void beginSection(String s) {} public static void endSection() {} public static boolean isEnabled() { return false; } public static void forceEnableAppTracing() {} }\n' > "$stubs/androidx/tracing/Trace.java"
  for a in MustBeClosed CanIgnoreReturnValue; do
    printf 'package com.google.errorprone.annotations;\npublic @interface %s {}\n' "$a" > "$stubs/com/google/errorprone/annotations/$a.java"
  done
  printf 'package com.google.errorprone.annotations;\npublic @interface InlineMe { String replacement(); String[] imports() default {}; String[] staticImports() default {}; }\n' > "$stubs/com/google/errorprone/annotations/InlineMe.java"
  # The Fragment overload of execStartActivity is a hidden API absent from the API 35 jar.
  python3 - "$src/runner/monitor/java/androidx/test/runner/MonitoringInstrumentation.java" <<'PY'
import re, sys
p = sys.argv[1]
s = open(p).read()
m = re.search(r"\n  @Override\n  public ActivityResult execStartActivity\(\s*Context who,\s*IBinder contextThread,\s*IBinder token,\s*Fragment target,.*?\n  }\n", s, re.S)
if m:
    s = s[:m.start()] + "\n" + s[m.end():]
open(p, "w").write(s)
PY
  gradle -q -p test-shim jar
  cp test-shim/build/libs/androidx-test-shim.jar sdk/
fi
echo "SDK jars ready in $(pwd)/sdk"
