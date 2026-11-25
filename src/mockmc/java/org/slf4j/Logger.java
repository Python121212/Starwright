package org.slf4j;

/** Minimal stand-in for the slf4j logger that Minecraft's LogUtils returns. */
public interface Logger {

    void info(String message);
}
