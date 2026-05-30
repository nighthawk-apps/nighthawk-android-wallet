# Applied only to the darkfimainnet flavor (merged into release shrink configs).
# Removes calls into android.util.Log so chat/wallet diagnostics cannot reach logcat from app or deps.
-assumenosideeffects class android.util.Log {
    public static *** v(...);
    public static *** d(...);
    public static *** i(...);
    public static *** w(...);
    public static *** e(...);
    public static *** wtf(...);
    public static *** println(...);
}
