package net.minecraft.server;

import io.github.python121212.starwright.mockmc.MockMinecraft;

/** Minimal stand-in for Minecraft's bootstrap hook. */
public final class Bootstrap {

    private Bootstrap() {}

    public static void bootStrap() {
        MockMinecraft.bootstrap();
    }

    public static boolean isBootstrapped() {
        return MockMinecraft.isBootstrapped();
    }
}
