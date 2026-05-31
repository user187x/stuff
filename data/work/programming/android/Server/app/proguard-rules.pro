# ProGuard rules for Vert.x, Netty, and Jackson

# Keep everything in Vert.x and Netty from being stripped or renamed,
# as they use reflection and native code extensively.
-keep class io.vertx.** { *; }
-keep interface io.vertx.** { *; }
-dontwarn io.vertx.**

-keep class io.netty.** { *; }
-keep interface io.netty.** { *; }
-dontwarn io.netty.**

-keep class org.bouncycastle.** { *; }
-dontwarn org.bouncycastle.**

# Keep Jackson classes, which are used for JSON processing by Vert.x.
-keep class com.fasterxml.jackson.** { *; }
-keep interface com.fasterxml.jackson.** { *; }
-dontwarn com.fasterxml.jackson.**
-keep @com.fasterxml.jackson.annotation.JsonIgnoreProperties class *
-keep class * { @com.fasterxml.jackson.annotation.JsonIgnoreProperties *; }

# Vert.x rules
-keep class io.vertx.core.json.jackson.JacksonCodec {
  <init>();
}