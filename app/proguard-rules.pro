# Keep line numbers for crash reports.
-keepattributes SourceFile,LineNumberTable
-renamesourcefileattribute SourceFile

# org.json is used via reflection-like dynamic keys in parsers; keep public API.
-keep class org.json.** { *; }
