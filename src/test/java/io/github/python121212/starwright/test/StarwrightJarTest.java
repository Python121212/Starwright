package io.github.python121212.starwright.test;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.moandjiezana.toml.Toml;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

@DisplayName("Built jar / emulated Forge load")
class StarwrightJarTest {

    private static final String MOD_ID = "starwright";
    private static Path jar;
    private static EmulatedForgeLoader.Result emulated;

    @BeforeAll
    static void locateJarAndEmulate() throws Exception {
        String configured = System.getProperty("starwright.jar");
        if (configured != null && !configured.isBlank() && Files.exists(Path.of(configured))) {
            jar = Path.of(configured);
        } else {
            try (Stream<Path> candidates = Files.list(Path.of("build/libs"))) {
                jar = candidates
                        .filter(p -> p.getFileName().toString().endsWith(".jar"))
                        .filter(p -> !p.getFileName().toString().contains("sources"))
                        .findFirst()
                        .orElseThrow(() -> new AssertionError("No jar found in build/libs"));
            }
        }
        assertTrue(Files.isRegularFile(jar), "Built jar is missing: " + jar);
        emulated = EmulatedForgeLoader.load(jar);
    }

    private static String readEntry(String name) throws IOException {
        try (JarFile jarFile = new JarFile(jar.toFile())) {
            JarEntry entry = jarFile.getJarEntry(name);
            assertNotNull(entry, "jar entry missing: " + name);
            try (InputStream in = jarFile.getInputStream(entry)) {
                return new String(in.readAllBytes(), StandardCharsets.UTF_8);
            }
        }
    }

    private static int countOccurrences(String haystack, String needle) {
        int count = 0;
        int index = 0;
        while ((index = haystack.indexOf(needle, index)) >= 0) {
            count++;
            index += needle.length();
        }
        return count;
    }

    @Test
    @DisplayName("jar contains the mod class, metadata and resource pack")
    void jarContainsExpectedEntries() {
        List<String> entries = emulated.entries();
        assertTrue(entries.contains("io/github/python121212/starwright/Starwright.class"));
        assertTrue(entries.contains("META-INF/mods.toml"));
        assertTrue(entries.contains("META-INF/MANIFEST.MF"));
        assertTrue(entries.contains("pack.mcmeta"));
    }

    @Test
    @DisplayName("processResources expanded every placeholder")
    void noUnresolvedPlaceholders() throws Exception {
        for (String name : List.of("META-INF/mods.toml", "pack.mcmeta")) {
            assertFalse(readEntry(name).contains("${"),
                    name + " still contains an unresolved placeholder");
        }
    }

    @Test
    @DisplayName("mods.toml declares exactly one javafml mod")
    void modsTomlIsValidAndExpanded() throws Exception {
        Toml toml = new Toml().read(readEntry("META-INF/mods.toml"));
        assertEquals("javafml", toml.getString("modLoader"));
        assertEquals("[47,)", toml.getString("loaderVersion"));
        assertEquals("MIT", toml.getString("license"));

        List<Toml> mods = toml.getTables("mods");
        assertNotNull(mods);
        assertEquals(1, mods.size());
        Toml mod = mods.get(0);
        assertEquals(MOD_ID, mod.getString("modId"));
        assertEquals("1.0.0", mod.getString("version"));
        assertEquals("Starwright", mod.getString("displayName"));
        assertEquals("Python121212", mod.getString("authors"));
    }

    @Test
    @DisplayName("mods.toml requires Forge 47.x and Minecraft 1.20.1")
    void modsTomlDeclaresDependencies() throws Exception {
        String text = readEntry("META-INF/mods.toml");
        assertEquals(2, countOccurrences(text, "[[dependencies.starwright]]"));
        assertTrue(text.contains("modId=\"forge\""));
        assertTrue(text.contains("modId=\"minecraft\""));
        assertTrue(text.contains("versionRange=\"[47,)\""));
        assertTrue(text.contains("versionRange=\"[1.20.1,1.20.2)\""));
        assertTrue(text.contains("mandatory=true"));
    }

    @Test
    @DisplayName("pack.mcmeta targets Minecraft 1.20.1")
    void packMcmetaTargets1201() throws Exception {
        JsonObject pack = JsonParser.parseString(readEntry("pack.mcmeta"))
                .getAsJsonObject()
                .getAsJsonObject("pack");
        assertNotNull(pack);
        assertEquals(15, pack.get("pack_format").getAsInt());
        assertEquals("Starwright resources", pack.get("description").getAsString());
    }

    @Test
    @DisplayName("manifest exposes production metadata")
    void manifestHasProductionMetadata() throws Exception {
        try (JarFile jarFile = new JarFile(jar.toFile())) {
            assertNotNull(jarFile.getManifest());
            var attributes = jarFile.getManifest().getMainAttributes();
            assertEquals("Starwright", attributes.getValue("Implementation-Title"));
            assertEquals("1.0.0", attributes.getValue("Implementation-Version"));
            assertNotNull(attributes.getValue("Implementation-Timestamp"));
        }
    }

    @Test
    @DisplayName("class files are Java 17 bytecode (major 61)")
    void classFilesAreJava17() throws Exception {
        try (JarFile jarFile = new JarFile(jar.toFile())) {
            List<JarEntry> classes = jarFile.stream()
                    .filter(entry -> entry.getName().endsWith(".class"))
                    .toList();
            assertFalse(classes.isEmpty(), "jar has no class files");
            for (JarEntry entry : classes) {
                try (InputStream in = jarFile.getInputStream(entry)) {
                    byte[] header = in.readNBytes(8);
                    assertEquals(8, header.length);
                    assertEquals(0xCA, header[0] & 0xFF);
                    assertEquals(0xFE, header[1] & 0xFF);
                    assertEquals(0xBA, header[2] & 0xFF);
                    assertEquals(0xBE, header[3] & 0xFF);
                    int major = ((header[6] & 0xFF) << 8) | (header[7] & 0xFF);
                    assertEquals(61, major, entry.getName() + " is not Java 17 bytecode");
                }
            }
        }
    }

    @Test
    @DisplayName("emulated FML discovers @Mod and instantiates the mod")
    void emulatedForgeLoad() {
        assertEquals(MOD_ID, emulated.modId());
        assertNotNull(emulated.instance(), "the mod constructor must run");
        assertTrue(emulated.log().contains("[Starwright] Loaded successfully"),
                "the mod should log its load message, got: " + emulated.log());
    }
}
