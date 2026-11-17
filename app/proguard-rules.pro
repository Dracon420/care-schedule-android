# Keep JavaScript interface methods so they aren't stripped by ProGuard
-keepclassmembers class com.dracon420.caremanagement.WebAppInterface {
    public *;
}
-keepattributes JavascriptInterface
