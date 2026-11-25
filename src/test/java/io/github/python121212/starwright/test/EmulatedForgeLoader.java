package io.github.python121212.starwright.test;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;
import java.util.regex.Pattern;

/**
 * Emulates FML mod loading with <strong>real Forge, fake Minecraft</strong>.
 *
 * <p>Role split:</p>
 * <ul>
 *   <li><b>Forge is real.</b> The bytecode of
 *       {@code net.minecraftforge.fml.common.Mod} is read straight from the Forge
 *       jar on the test classpath and defined by the isolated loader. No Forge stub
 *       exists anywhere in {@code src/mockmc}.</li>
 *   <li><b>Minecraft is a pseudo runtime.</b> {@code src/mockmc/java} provides fake
 *       {@code net.minecraft.*}, {@code com.mojang.logging.*} and {@code org.slf4j.*}
 *       classes, plus {@code MockMinecraft} which records bootstrap, logs and mod
 *       registration.</li>
 *   <li>The built (re-obfuscated) mod jar is loaded for real and its no-arg
 *       constructor is invoked, exactly as FML would.</li>
 * </ul>
 *
 * <p>The class loader's parent is {@code null} (bootstrap only), so the real
 * Minecraft classes on the test classpath cannot leak into the pseudo game.</p>
 */
public final class EmulatedForgeLoader {

    /** Outcome of one emulated discovery + instantiation pass. */
    public record Result(
            String modId,
            String modAnnotationType,
            int forgeAnnotationBytes,
            String forgeCodeSource,
            Object instance,
            String log,
            boolean pseudoMinecraftBootstrapped,
            List<String> pseudoMinecraftEvents,
            List<String> loadedMods,
            List<String> entries) {}

    private static final String MOD_ANNOTATION = "net.minecraftforge.fml.common.Mod";
    private static final String MAIN_CLASS = "io.github.python121212.starwright.Starwright";
    private static final String MOCKMC_CLASS =
            "io.github.python121212.starwright.mockmc.MockMinecraft";

    private EmulatedForgeLoader() {}

    /**
     * Runs the emulation against {@code jarPath} (the production mod jar).
     */
    public static Result load(Path jarPath) throws Exception {
        byte[] realModBytes = readRealForgeAnnotation();
        String forgeSource = describeForgeCodeSource();
        List<Path> mockmcRoots = mockMinecraftRoots();

        ForgeBackedLoader loader = new ForgeBackedLoader(mockmcRoots, jarPath, realModBytes);
        try {
            Class<?> modClass = loader.loadClass(MAIN_CLASS);
            var annotation = Arrays.stream(modClass.getAnnotations())
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
                log = new String(buffer.toByteArray(), StandardCharsets.UTF_8).trim();
            }

            // Emulate FML registering the discovered mod in the pseudo game.
            Class<?> mockmc = loader.loadClass(MOCKMC_CLASS);
            mockmc.getMethod("recordMod", String.class).invoke(null, modId);
            boolean bootstrapped = (boolean) mockmc.getMethod("isBootstrapped").invoke(null);
            List<String> events = copyStrings(mockmc.getMethod("events").invoke(null));
            List<String> loaded = copyStrings(mockmc.getMethod("loadedMods").invoke(null));

            original.println("[emulated FML] real Forge @Mod from: " + forgeSource);
            original.println("[emulated FML] " + MAIN_CLASS + " -> @Mod(\"" + modId + "\")");
            original.println("[emulated FML] " + log);

            return new Result(
                    modId,
                    annotation.annotationType().getName(),
                    realModBytes.length,
                    forgeSource,
                    instance,
                    log,
                    bootstrapped,
                    events,
                    loaded,
                    List.copyOf(loader.jarEntries()));
        } finally {
            loader.closeJar();
        }
    }

    /** Reads the genuine Forge annotation bytes from the test classpath. */
    private static byte[] readRealForgeAnnotation() throws IOException {
        String resource = MOD_ANNOTATION.replace('.', '/') + ".class";
        ClassLoader testLoader = EmulatedForgeLoader.class.getClassLoader();
        try (InputStream in = testLoader.getResourceAsStream(resource)) {
            if (in == null) {
                throw new IllegalStateException(
                        "Real Forge @Mod class is not on the test classpath: " + resource);
            }
            return in.readAllBytes();
        }
    }

    private static String describeForgeCodeSource() throws ClassNotFoundException {
        Class<?> realMod = Class.forName(MOD_ANNOTATION);
        var codeSource = realMod.getProtectionDomain().getCodeSource();
        if (codeSource == null || codeSource.getLocation() == null) {
            return "unknown";
        }
        return codeSource.getLocation().toString();
    }

    private static List<Path> mockMinecraftRoots() {
        String configured = System.getProperty("starwright.mockmc");
        if (configured == null || configured.isBlank()) {
            throw new IllegalStateException(
                    "starwright.mockmc is missing - did mockmcClasses run?");
        }
        return Arrays.stream(configured.split(Pattern.quote(File.pathSeparator)))
                .filter(part -> !part.isBlank())
                .map(Path::of)
                .toList();
    }

    @SuppressWarnings("unchecked")
    private static List<String> copyStrings(Object value) {
        return List.copyOf((List<String>) value);
    }

    /**
     * Bootstrap-only class loader. Forge's {@code @Mod} comes from the real jar,
     * Minecraft-side classes come from the pseudo runtime, and the mod itself is
     * read from the built jar.
     */
    private static final class ForgeBackedLoader extends ClassLoader {

        private final List<Path> mockmcRoots;
        private final JarFile jar;
        private final byte[] realModBytes;

        ForgeBackedLoader(List<Path> mockmcRoots, Path jarPath, byte[] realModBytes)
                throws IOException {
            super(null);
            this.mockmcRoots = mockmcRoots;
            this.jar = new JarFile(jarPath.toFile());
            this.realModBytes = realModBytes;
        }

        @Override
        protected Class<?> findClass(String name) throws ClassNotFoundException {
            if (MOD_ANNOTATION.equals(name)) {
                return defineClass(name, realModBytes, 0, realModBytes.length);
            }
            byte[] bytes = readPseudoMinecraft(name);
            if (bytes == null) {
                bytes = readModJar(name);
            }
            if (bytes == null) {
                throw new ClassNotFoundException(name);
            }
            return defineClass(name, bytes, 0, bytes.length);
        }

        private byte[] readPseudoMinecraft(String name) throws ClassNotFoundException {
            String relative = name.replace('.', '/') + ".class";
            for (Path root : mockmcRoots) {
                Path file = root.resolve(relative);
                if (Files.isRegularFile(file)) {
                    try {
                        return Files.readAllBytes(file);
                    } catch (IOException e) {
                        throw new ClassNotFoundException(name, e);
                    }
                }
            }
            return null;
        }

        private byte[] readModJar(String name) throws ClassNotFoundException {
            JarEntry entry = jar.getJarEntry(name.replace('.', '/') + ".class");
            if (entry == null) {
                return null;
            }
            try (InputStream in = jar.getInputStream(entry)) {
                return in.readAllBytes();
            } catch (IOException e) {
                throw new ClassNotFoundException(name, e);
            }
        }

        List<String> jarEntries() {
            List<String> names = new ArrayList<>();
            jar.stream().forEach(entry -> names.add(entry.getName()));
            return names;
        }

        void closeJar() {
            try {
                jar.close();
            } catch (IOException ignored) {
                // Nothing useful to do while tearing down the emulation.
            }
        }
    }
}
