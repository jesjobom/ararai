# whisper.cpp exports name-based JNI entry points for this exact Kotlin object.
-keep class com.jesjobom.ararai.whisper.WhisperRuntime { *; }

# LiteRT-LM 0.14.0 ships name-based JNI methods and native-to-Java callbacks,
# but its AAR does not publish consumer rules for those boundaries.
# LiteRT-LM's native layer looks up methods and constructs multiple Kotlin DTOs
# by their original JNI names (for example SamplerConfig and BenchmarkInfo).
# The dependency does not currently publish complete consumer rules for this
# boundary, so preserve its Java/Kotlin API while still shrinking the app.
-keep class com.google.ai.edge.litertlm.** { *; }
-keepclassmembers,allowoptimization class * implements com.google.ai.edge.litertlm.LiteRtLmJni$JniInferenceCallback {
    public void onNext(java.lang.String);
    public void onDone();
    public void onError(int, java.lang.String);
}
-keepclassmembers,allowoptimization class * implements com.google.ai.edge.litertlm.LiteRtLmJni$JniMessageCallback {
    public void onMessage(java.lang.String);
    public void onDone();
    public void onError(int, java.lang.String);
}

# EvalEx bytecode retains its compile-only Lombok marker after Lombok itself is absent.
-dontwarn lombok.Generated

# EvalEx inspects runtime annotations on its built-in operator and function
# implementations while constructing the standard expression configuration.
-keep class com.ezylang.evalex.operators.** { *; }
-keep class com.ezylang.evalex.functions.** { *; }

# Room resolves WorkManager's generated database implementation and constructor
# reflectively. Optimized R8 otherwise removes the no-arg constructor and the
# application crashes in WorkManagerInitializer before MainActivity starts.
-keep class androidx.work.impl.WorkDatabase_Impl {
    <init>();
}
