package io.github.python121212.starwright.test;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.PrintStream;
import java.net.URI;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.jar.JarFile;
import javax.tools.DiagnosticCollector;
import javax.tools.JavaCompiler;
import javax.tools.JavaFileObject;
import javax.tools.SimpleJavaFileObject;
import javax.tools.StandardJavaFileManager;
import javax.tools.ToolProvider;

/**
 * A tiny stand-in for Forge's FML mod discovery.
 *
 * <p>Instead of launching Minecraft, this class:</p>
 * <ol>
 *   <li>compiles minimal {@code @Mod} / {@code Logger} / {@code LogUtils} stubs at test time,</li>
 *   <li>creates an isolated class loader whose parent is {@code null} (bootstrap only), so the
 *       real Forge/Minecraft classes on the test classpath can never leak in,</li>
 *   <li>loads the built jar, finds the {@code @Mod} annotation, reads its mod id,
 *       and invokes the no-argument constructor exactly like FML would,</li>
 *   <li>captures the log line the mod prints, proving the load-only jar actually loads.</li>
 * </ol>
 */
public final class EmulatedForgeLoader {

    /** Outcome of one emulated discovery + instantiation pass. */
    public record Result(String modId, Object instance, String log, List<String> entries) {}

    private static final String MOD_ANNOTATION = "net.minecraftforge.fml.common.Mod";
    private static final String MAIN_CLASS = "io.github.python121212.starwright.Starwright";

    private static final Map<String, String> STUBS = Map.of(
            "net.minecraftforge.fml.common.Mod", """
                    package net.minecraftforge.fml.common;
                    import java.lang.annotation.*;
                    @Retention(RetentionPolicy.RUNTIME)
                    @Target(ElementType.TYPE)
                    public @interface Mod { String value(); }
                    """,
            "org.slf4j.Logger", """
                    package org.slf4j;
                    public interface Logger { void info(String message); }
                    """,
            "com.mojang.logging.LogUtils", """
                    package com.mojang.logging;
                    import org.slf4j.Logger;
                    public final class LogUtils {
                        private LogUtils() {}
                        public static Logger getLogger() {
                            return message -> System.out.println(message);
                        }
                    }
                    """
    );

    private EmulatedForgeLoader() {}

    /**
     * Emulates FML loading {@code jar} and returns what it discovered.
     *
     * @param jar the production (re-obfuscated) mod jar
     */
    public static Result load(Path jar) throws Exception {
        Path stubs = compileStubs();

        List<String> entries = new ArrayList<>();
        try (JarFile jarFile = new JarFile(jar.toFile())) {
            jarFile.stream().forEach(entry -> entries.add(entry.getName()));
        }

        try (URLClassLoader loader = new URLClassLoader(
                new URL[] { stubs.toUri().toURL(), jar.toUri().toURL() }, null)) {

            Class<?> modClass = loader.loadClass(MAIN_CLASS);
            Object annotation = Arrays.stream(modClass.getAnnotations())
                    .filter(a -> a.annotationType().getName().equals(MOD_ANNOTATION))
                    .findFirst()
                    .orElseThrow(() -> new AssertionError(
                            "@Mod annotation not found on " + MAIN_CLASS));
            String modId = (String) annotation.annotationType()
                    .getMethod("value").invoke(annotation);

            PrintStream original = System.out;
            ByteArrayOutputStream buffer = new ByteArrayOutputStream();
            Object instance;
            String log;
            try (PrintStream capture = new PrintStream(buffer, true, StandardCharsets.UTF_8)) {
                System.setOut(capture);
                try {
                    instance = modClass.getDeclaredConstructor().newInstance();
                } finally {
                    System.setOut(original);
                }
                log = buffer.toString(StandardCharsets.UTF_8).trim();
            }

            // Echo into the Gradle test log so the emulated load is visible in CI.
            original.println("[emulated FML] " + MAIN_CLASS + " -> @Mod(\"" + modId + "\")");
            original.println("[emulated FML] " + log);
            return new Result(modId, instance, log, List.copyOf(entries));
        }
    }

    /** Compiles the runtime stubs into a temporary directory using the JDK compiler. */
    private static Path compileStubs() throws IOException {
        JavaCompiler compiler = ToolProvider.getSystemJavaCompiler();
        if (compiler == null) {
            throw new IllegalStateException(
                    "A full JDK 17 is required for the jar emulation tests (no system Java compiler found)");
        }

        Path out = Files.createTempDirectory("starwright-forge-stubs-");
        DiagnosticCollector<JavaFileObject> diagnostics = new DiagnosticCollector<>();
        List<JavaFileObject> units = new ArrayList<>();
        for (Map.Entry<String, String> entry : STUBS.entrySet()) {
            final String className = entry.getKey();
            final String source = entry.getValue();
            units.add(new SimpleJavaFileObject(
                    URI.create("string:///" + className.replace('.', '/')
                            + JavaFileObject.Kind.SOURCE.extension),
                    JavaFileObject.Kind.SOURCE) {
                @Override
                public CharSequence getCharContent(boolean ignoreEncodingErrors) {
                    return source;
                }
            });
        }

        try (StandardJavaFileManager manager =
                     compiler.getStandardFileManager(diagnostics, null, StandardCharsets.UTF_8)) {
            Boolean ok = compiler.getTask(null, manager, diagnostics,
                    List.of("-d", out.toString(), "-proc:none", "-encoding", "UTF-8"),
                    null, units).call();
            if (!Boolean.TRUE.equals(ok)) {
                throw new IllegalStateException(
                        "Failed to compile Forge stubs: " + diagnostics.getDiagnostics());
            }
        }
        return out;
    }
}
