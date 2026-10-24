package io.nayuki.qrcodegen;
/** API18 replacement for the Java 7 Objects helper. */
final class LegacyObjects { static <T> T requireNonNull(T value) { if(value==null) throw new NullPointerException(); return value; } }
