package io.github.python121212.starwright.test;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.PrintStream;
import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;
import java.util.regex.Pattern;

/**
 * Emulates FML mod loading with <strong>real Forge, fake Minecraft</strong>.
 *
 * <p>Role split:</p>
 * <ul>
 *   <li><b>Forge is real.</b> Every {@code net.minecraftforge.*} class (the
 *       {@code @Mod} annotation, {@code IEventBus}, registries, ...) is read from
 *       the real Forge jar on the test classpath. No Forge stub exists anywhere
 *       in {@code src/mockmc}.</li>
 *   <li><b>Minecraft is a pseudo runtime.</b> {@code src/mockmc/java} provides fake
 *       {@code net.minecraft.*}, {@code com.mojang.logging.*} and {@code org.slf4j.*}
 *       classes, plus {@code MockMinecraft} which records bootstrap, logs, mod
 *       registration and every tick of the continuous ascent. If the mod needs a
 *       Minecraft class that is not implemented, the loader fails with an
 *       explicit message instead of silently using the real one.</li>
 *   <li>The built (re-obfuscated) mod jar is loaded for real. FML's constructor
 *       injection is emulated by passing a proxy of the <b>real</b> Forge mod
 *       event bus.</li>
 *   <li>The continuous-ascent rules are loaded from the PRODUCTION jar and
 *       ticked for real inside the pseudo game until Y=5,000 is reached and the
 *       player descends again. Those rules are Minecraft-independent, so they
 *       survive {@code reobfJar} without any name remapping.</li>
 * </ul>
 *
 * <p>The class loader's parent is {@code null} (bootstrap only), so the real
 * Minecraft classes on the test classpath cannot leak into the pseudo game.</p>
 */
public final class EmulatedForgeLoader {

    /** Result of one full tick-by-tick continuous ascent/descent emulation. */
    public record AscentEmulation(
            double startY,
            double spaceY,
            double returnY,
            double maxSpeed,
            double spaceStartY,
            int ascentTicks,
            int descentTicks,
            double maxAscentStep,
            double maxDescentStep,
            boolean monotonicAscent,
            boolean descentReversed,
            List<Double> ascentTrace) {}

    /** Outcome of one emulated discovery + instantiation + ascent pass. */
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
            List<String> entries,
            List<String> constructorParameterTypes,
            AscentEmulation ascent) {}

    private static final String MOD_ANNOTATION = "net.minecraftforge.fml.common.Mod";
    private static final String MAIN_CLASS = "io.github.python121212.starwright.Starwright";
    private static final String MOCKMC_CLASS =
            "io.github.python121212.starwright.mockmc.MockMinecraft";
    private static final String TUNING_CLASS =
            "io.github.python121212.starwright.space.SpaceTuning";
    private static final String ASCENT_RULES_CLASS =
            "io.github.python121212.starwright.space.SpaceAscentRules";
    private static final int MAX_EMULATED_TICKS = 50_000;

    private EmulatedForgeLoader() {}

    /** Runs the emulation against {@code jarPath} (the production mod jar). */
    public static Result load(Path jarPath) throws Exception {
        byte[] realModBytes = readRealForgeAnnotation();
        String forgeSource = describeForgeCodeSource();
        List<Path> mockmcRoots = mockMinecraftRoots();
        ClassLoader testLoader = EmulatedForgeLoader.class.getClassLoader();

        ForgeBackedLoader loader = new ForgeBackedLoader(mockmcRoots, jarPath, testLoader);
        try {
            Class<?> mockmc = loader.loadClass(MOCKMC_CLASS);
            Method recordEventBus = mockmc.getMethod("recordEventBusCall", String.class);

            Class<?> modClass = loader.loadClass(MAIN_CLASS);
            var annotation = Arrays.stream(modClass.getAnnotations())
                    .filter(a -> a.annotationType().getName().equals(MOD_ANNOTATION))
                    .findFirst()
                    .orElseThrow(() -> new AssertionError(
                            "@Mod annotation not found on " + MAIN_CLASS));
            String modId = (String) annotation.annotationType()
                    .getMethod("value").invoke(annotation);

            // FML 1.20.1 injects IEventBus into the mod constructor. Accept both
            // the old no-arg form and the injected form so adding registration
            // code later never breaks the emulator.
            Constructor<?> ctor = Arrays.stream(modClass.getDeclaredConstructors())
                    .filter(c -> c.getParameterCount() <= 1)
                    .min(Comparator.comparingInt(Constructor::getParameterCount))
                    .orElseThrow(() -> new AssertionError(
                            "mod needs a no-arg or single-argument constructor"));
            List<String> paramTypes = Arrays.stream(ctor.getParameterTypes())
                    .map(Class::getName)
                    .toList();

            Object[] args;
            if (ctor.getParameterCount() == 0) {
                args = new Object[0];
            } else {
                Class<?> busType = ctor.getParameterTypes()[0];
                Object bus = Proxy.newProxyInstance(
                        loader, new Class<?>[] { busType },
                        (proxy, method, methodArgs) -> {
                            recordEventBus.invoke(null, method.getName());
                            return defaultValue(method.getReturnType());
                        });
                args = new Object[] { bus };
            }

            PrintStream original = System.out;
            ByteArrayOutputStream buffer = new ByteArrayOutputStream();
            Object instance;
            String log;
            try (PrintStream capture = new PrintStream(buffer, true, StandardCharsets.UTF_8)) {
                System.setOut(capture);
                try {
                    instance = ctor.newInstance(args);
                } finally {
                    System.setOut(original);
                }
                log = new String(buffer.toByteArray(), StandardCharsets.UTF_8).trim();
            }

            // Emulate FML registering the discovered mod in the pseudo game.
            mockmc.getMethod("recordMod", String.class).invoke(null, modId);

            // Emulate the real server ticks that carry the player to space.
            AscentEmulation ascent = runAscentEmulation(loader, mockmc);

            boolean bootstrapped = (boolean) mockmc.getMethod("isBootstrapped").invoke(null);
            List<String> events = copyStrings(mockmc.getMethod("events").invoke(null));
            List<String> loaded = copyStrings(mockmc.getMethod("loadedMods").invoke(null));

            original.println("[emulated FML] real Forge @Mod from: " + forgeSource);
            original.println("[emulated FML] " + MAIN_CLASS + " -> @Mod(\"" + modId + "\")");
            original.println("[emulated FML] constructor args: " + paramTypes);
            original.println("[emulated FML] " + log);
            original.println("[emulated FML] continuous ascent: " + ascent.ascentTicks()
                    + " ticks to Y=" + String.format("%.1f", ascent.spaceY())
                    + ", max step " + String.format("%.2f", ascent.maxAscentStep())
                    + " m/tick, descended back to Y=" + String.format("%.1f", ascent.returnY())
                    + " in " + ascent.descentTicks() + " ticks");

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
                    List.copyOf(loader.jarEntries()),
                    paramTypes,
                    ascent);
        } finally {
            loader.closeJar();
        }
    }

    /**
     * Loads {@link #ASCENT_RULES_CLASS} and {@link #TUNING_CLASS} straight out
     * of the PRODUCTION jar and ticks them inside the pseudo Minecraft runtime.
     * Every tick records the resulting altitude, so a teleport (a step larger
     * than ASCENT_MAX_SPEED) or a non-monotonic climb is impossible to hide.
     */
    private static AscentEmulation runAscentEmulation(
            ForgeBackedLoader loader, Class<?> mockmc) throws Exception {

        Class<?> tuning = loader.loadClass(TUNING_CLASS);
        double armY = tuning.getField("ASCENT_ARM_Y").getInt(null);
        double maxSpeed = tuning.getField("ASCENT_MAX_SPEED").getDouble(null);
        double spaceStartY = tuning.getField("SPACE_START_Y").getInt(null);

        Class<?> rules = loader.loadClass(ASCENT_RULES_CLASS);
        Method isArmed = rules.getMethod("isArmed", double.class);
        Method targetSpeed = rules.getMethod("targetSpeed",
                double.class, boolean.class, boolean.class, boolean.class);
        Method stepTowards = rules.getMethod("stepTowards", double.class, double.class);
        Method recordAscent = mockmc.getMethod("recordAscent", double.class, double.class);

        assertTrue(maxSpeed > 0.0, "ASCENT_MAX_SPEED must be positive");
        assertTrue(spaceStartY > armY, "space must be above the vanilla build limit");

        double y = armY;
        double velocity = 0.0;
        int ascentTicks = 0;
        double maxAscentStep = 0.0;
        boolean monotonicAscent = true;
        List<Double> trace = new ArrayList<>();
        trace.add(y);

        // Hold jump: rise continuously until the real Y=5,000 boundary.
        while (y < spaceStartY && ascentTicks < MAX_EMULATED_TICKS) {
            boolean armed = (boolean) isArmed.invoke(null, y);
            double target = armed
                    ? (double) targetSpeed.invoke(null, y, true, false, false)
                    : 0.0;
            velocity = (double) stepTowards.invoke(null, velocity, target);
            y += velocity; // the pseudo game applies the vanilla entity move
            double step = Math.abs(velocity);
            maxAscentStep = Math.max(maxAscentStep, step);
            if (velocity < -1e-9) monotonicAscent = false;
            trace.add(y);
            ascentTicks++;
            recordAscent.invoke(null, y, velocity);
        }
        double spaceY = y;

        // Hold sneak: descend continuously back towards the build limit.
        int descentTicks = 0;
        double maxDescentStep = 0.0;
        boolean descentReversed = false;
        while (y > armY + 1.0 && descentTicks < MAX_EMULATED_TICKS) {
            boolean armed = (boolean) isArmed.invoke(null, y);
            double target = armed
                    ? (double) targetSpeed.invoke(null, y, false, true, false)
                    : 0.0;
            velocity = (double) stepTowards.invoke(null, velocity, target);
            y += velocity;
            maxDescentStep = Math.max(maxDescentStep, Math.abs(velocity));
            if (velocity < 0.0) descentReversed = true;
            descentTicks++;
            recordAscent.invoke(null, y, velocity);
        }

        return new AscentEmulation(
                armY, spaceY, y,
                maxSpeed, spaceStartY,
                ascentTicks, descentTicks,
                maxAscentStep, maxDescentStep,
                monotonicAscent, descentReversed,
                List.copyOf(trace));
    }

    private static void assertTrue(boolean condition, String message) {
        if (!condition) {
            throw new AssertionError(message);
        }
    }

    private static Object defaultValue(Class<?> type) {
        if (!type.isPrimitive()) {
            return null;
        }
        if (type == boolean.class) return false;
        if (type == char.class) return '\0';
        if (type == byte.class) return (byte) 0;
        if (type == short.class) return (short) 0;
        if (type == int.class) return 0;
        if (type == long.class) return 0L;
        if (type == float.class) return 0f;
        if (type == double.class) return 0d;
        return null; // void
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
            throw new IllegalStateException("starwright.mockmc is missing - did mockmcClasses run?");
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

    private static boolean isMinecraftSide(String name) {
        return name.startsWith("net.minecraft.")
                || name.equals("net.minecraft")
                || name.startsWith("com.mojang.")
                || name.startsWith("org.slf4j.");
    }

    /**
     * Bootstrap-only class loader. Resolution order:
     * <ol>
     *   <li>pseudo Minecraft classes ({@code src/mockmc}) win for Minecraft packages,</li>
     *   <li>then the built mod jar,</li>
     *   <li>then real classes from the test classpath (all of Forge + its deps).</li>
     * </ol>
     * A missing Minecraft-side class is a hard error, never a silent fallback to
     * the real Minecraft jar.
     */
    private static final class ForgeBackedLoader extends ClassLoader {

        private final List<Path> mockmcRoots;
        private final JarFile jar;
        private final ClassLoader testLoader;

        ForgeBackedLoader(List<Path> mockmcRoots, Path jarPath, ClassLoader testLoader)
                throws IOException {
            super(null);
            this.mockmcRoots = mockmcRoots;
            this.jar = new JarFile(jarPath.toFile());
            this.testLoader = testLoader;
        }

        @Override
        protected Class<?> findClass(String name) throws ClassNotFoundException {
            boolean minecraftSide = isMinecraftSide(name);
            byte[] bytes = readPseudoMinecraft(name);
            if (bytes == null && !minecraftSide) {
                bytes = readModJar(name);
            }
            if (bytes == null && !minecraftSide) {
                bytes = readRealClass(name);
            }
            if (bytes == null) {
                if (minecraftSide) {
                    throw new ClassNotFoundException(
                            "pseudo Minecraft does not implement " + name
                                    + " - add it to src/mockmc/java before adding this feature");
                }
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

        private byte[] readRealClass(String name) throws ClassNotFoundException {
            String resource = name.replace('.', '/') + ".class";
            try (InputStream in = testLoader.getResourceAsStream(resource)) {
                return in == null ? null : in.readAllBytes();
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
