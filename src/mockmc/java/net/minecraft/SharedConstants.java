package net.minecraft;

/** Minimal stand-in for Minecraft's version holder, used by the pseudo runtime. */
public final class SharedConstants {

    public static final String VERSION_STRING = "1.20.1";

    private SharedConstants() {}

    public static String getCurrentVersion() {
        return VERSION_STRING;
    }
}
