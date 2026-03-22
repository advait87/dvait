# Add project specific ProGuard rules here.
-keep class com.dvait.base.data.model.** { *; }
-keep class io.objectbox.** { *; }
-keepclassmembers class com.dvait.base.engine.LlamaEngine {
    native <methods>;
}
