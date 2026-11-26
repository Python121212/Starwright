package com.mojang.logging;

import io.github.python121212.starwright.mockmc.MockMinecraft;
import org.slf4j.Logger;

/** Minimal stand-in for Mojang's LogUtils, wired into the pseudo Minecraft runtime. */
public final class LogUtils {

    private LogUtils() {}

    public static Logger getLogger() {
        // Creating a logger is what "starts" the pseudo game in this environment.
        MockMinecraft.bootstrap();
        return new Logger() {
            @Override
            public void info(String message) {
                MockMinecraft.recordLog(message);
                System.out.println("[MockMC/INFO] " + message);
            }
        };
    }
}
