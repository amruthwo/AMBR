# youtubedl-android maps yt-dlp JSON with Jackson via reflection.
-keep class com.yausername.** { *; }
-keep class com.fasterxml.jackson.** { *; }
-keepattributes *Annotation*, Signature, EnclosingMethod, InnerClasses
-dontwarn com.fasterxml.jackson.**
-dontwarn java.beans.**
-dontwarn org.apache.commons.compress.**
-dontwarn org.slf4j.**
# yt-dlp/Python are unpacked with commons-compress, which registers zip extra-field classes by reflection.
-keep class org.apache.commons.compress.** { *; }
-keep class org.apache.commons.io.** { *; }
