package io.github.python121212.starwright.test;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.moandjiezana.toml.Toml;
import io.github.python121212.starwright.space.SpaceRenderRules;
import io.github.python121212.starwright.space.SpaceTuning;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

@DisplayName("Built jar / real Forge + pseudo Minecraft")
class StarwrightJarTest {

    private static final String MOD_ID = "starwright";
    private static final String MOD_ANNOTATION = "net.minecraftforge.fml.common.Mod";
    private static final String EVENT_BUS = "net.minecraftforge.eventbus.api.IEventBus";
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

    private static byte[] readClassBytes(String name) throws IOException {
        try (JarFile jarFile = new JarFile(jar.toFile())) {
            JarEntry entry = jarFile.getJarEntry(name);
            assertNotNull(entry, "jar entry missing: " + name);
            try (InputStream in = jarFile.getInputStream(entry)) {
                return in.readAllBytes();
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

    @Test
    @DisplayName("FML injects the real Forge mod event bus")
    void constructorReceivesForgeEventBus() {
        assertEquals(List.of(EVENT_BUS), emulated.constructorParameterTypes(),
                "Starwright should take the Forge mod event bus");
        assertNotNull(emulated.instance(), "construction with the injected bus must succeed");
    }

    @Test
    @DisplayName("the REAL Forge @Mod annotation is used, not a stub")
    void usesRealForgeAnnotation() throws Exception {
        Class<?> real = Class.forName(MOD_ANNOTATION);
        assertTrue(real.isAnnotation());
        assertNotNull(real.getDeclaredMethod("value"));
        assertNotNull(Class.forName(EVENT_BUS), "real Forge event bus must be on the classpath");

        assertEquals(MOD_ANNOTATION, emulated.modAnnotationType());
        assertTrue(emulated.forgeAnnotationBytes() > 0,
                "Forge annotation bytecode must come from the real Forge jar");
        assertNotNull(emulated.forgeCodeSource());

        // The pseudo Minecraft source set must never fake Forge itself.
        String roots = System.getProperty("starwright.mockmc");
        assertNotNull(roots, "pseudo Minecraft classes were not built");
        for (String root : roots.split(Pattern.quote(File.pathSeparator))) {
            assertFalse(Files.exists(Path.of(root)
                            .resolve("net/minecraftforge/fml/common/Mod.class")),
                    "pseudo Minecraft must not ship a Forge @Mod stub");
            assertFalse(Files.exists(Path.of(root)
                            .resolve("net/minecraftforge/eventbus/api/IEventBus.class")),
                    "pseudo Minecraft must not ship a Forge IEventBus stub");
        }
    }

    @Test
    @DisplayName("pseudo Minecraft boots as 1.20.1")
    void pseudoMinecraftBootstraps() {
        assertTrue(emulated.pseudoMinecraftBootstrapped());
        assertTrue(emulated.pseudoMinecraftEvents().stream()
                        .anyMatch(event -> event.equals("bootstrap:1.20.1")),
                "pseudo game should boot as 1.20.1, events: " + emulated.pseudoMinecraftEvents());
        assertTrue(emulated.log().contains("[MockMC] Booting pseudo Minecraft 1.20.1"));
        assertTrue(emulated.log().contains("[MockMC/INFO]"));
    }

    @Test
    @DisplayName("emulated FML registers the mod in pseudo Minecraft")
    void modIsRegisteredInPseudoMinecraft() {
        assertTrue(emulated.loadedMods().contains(MOD_ID),
                "FML should register " + MOD_ID + ", got: " + emulated.loadedMods());
        assertTrue(emulated.pseudoMinecraftEvents().stream()
                        .anyMatch(event -> event.equals("mod:" + MOD_ID)),
                "pseudo game should record the mod registration");
        assertTrue(emulated.pseudoMinecraftEvents().stream()
                        .anyMatch(event -> event.startsWith("log:[Starwright]")),
                "pseudo game should record the mod's log line");
    }

    @Test
    @DisplayName("localisation files exist for en_us and ja_jp")
    void localisationFilesArePackaged() throws Exception {
        for (String lang : List.of("en_us", "ja_jp")) {
            String name = "assets/starwright/lang/" + lang + ".json";
            assertTrue(emulated.entries().contains(name), name + " is missing from the jar");
            JsonObject json = JsonParser.parseString(readEntry(name)).getAsJsonObject();
            assertEquals("Starwright", json.get("itemGroup.starwright").getAsString());
        }
    }

    @Test
    @DisplayName("no dimension_type override: vanilla build limit is untouched")
    void noDimensionTypeOverride() {
        for (String entry : emulated.entries()) {
            assertFalse(entry.startsWith("data/minecraft/dimension_type/"),
                    "vanilla build limit must stay untouched: " + entry);
            assertFalse(entry.startsWith("data/minecraft/dimension/"),
                    "no custom dimension may be added: " + entry);
        }
    }

    @Test
    @DisplayName("production jar climbs to Y=5,000 continuously, without a teleport")
    void productionJarAscentIsContinuous() {
        EmulatedForgeLoader.AscentEmulation ascent = emulated.ascent();
        assertEquals(SpaceTuning.ASCENT_ARM_Y, ascent.startY(), 1e-9);
        assertEquals(5_000.0, ascent.spaceStartY(), 1e-9);
        assertTrue(ascent.spaceY() >= ascent.spaceStartY(),
                "the emulation must actually reach space");
        assertTrue(ascent.monotonicAscent(), "altitude must never jump backwards");
        assertTrue(ascent.descentReversed(), "sneak must reverse the climb");
        assertTrue(ascent.returnY() < ascent.spaceY(), "descent must return from space");
        assertTrue(ascent.maxAscentStep() <= ascent.maxSpeed() + 1e-9,
                "a step larger than the velocity ceiling is a teleport");
        assertTrue(ascent.maxDescentStep() <= ascent.maxSpeed() + 1e-9);
        assertTrue(ascent.ascentTicks()
                        >= (ascent.spaceStartY() - ascent.startY()) / ascent.maxSpeed(),
                "climbing faster than the speed ceiling would not be continuous");
        assertEquals(ascent.ascentTicks() + 1, ascent.ascentTrace().size());

        for (int i = 1; i < ascent.ascentTrace().size(); i++) {
            double step = ascent.ascentTrace().get(i) - ascent.ascentTrace().get(i - 1);
            assertTrue(step >= -1e-9 && step <= ascent.maxSpeed() + 1e-9,
                    "discontinuous altitude change at tick " + i + ": " + step);
        }
    }

    @Test
    @DisplayName("pseudo Minecraft emulated every tick of the continuous ascent")
    void pseudoMinecraftEmulatedEveryAscentTick() {
        long ascentEvents = emulated.pseudoMinecraftEvents().stream()
                .filter(event -> event.startsWith("ascent:"))
                .count();
        assertEquals(emulated.ascent().ascentTicks() + emulated.ascent().descentTicks(),
                ascentEvents, "every server tick must be recorded by the pseudo game");
        assertTrue(ascentEvents > 5_000, "the climb must take thousands of real ticks");
        assertTrue(emulated.pseudoMinecraftEvents().stream()
                        .anyMatch(event -> event.startsWith("ascent:y=")
                                && event.contains("vy=")),
                "pseudo game should record altitude and velocity per tick");
    }

    @Test
    @DisplayName("ascent rules in the jar stay Minecraft-independent and are wired into the hook")
    void ascentRulesArePureJavaAndHookedIn() throws Exception {
        String rulesPool = new String(readClassBytes(
                        "io/github/python121212/starwright/space/SpaceAscentRules.class"),
                StandardCharsets.ISO_8859_1);
        // javac inlines SpaceTuning's static final primitive constants, so the
        // compiled rules class has NO SpaceTuning reference - that is expected.
        // The production constants are verified via AscentEmulation instead.
        assertTrue(rulesPool.contains("targetSpeed"),
                "SpaceAscentRules must expose targetSpeed in the production jar");
        assertTrue(rulesPool.contains("stepTowards"),
                "SpaceAscentRules must expose stepTowards in the production jar");
        assertTrue(rulesPool.contains("needsTemporaryFlightPermission"),
                "SpaceAscentRules must decide Survival / Adventure flight permission");
        assertFalse(rulesPool.contains("net/minecraft"),
                "ascent rules must not reference Minecraft classes (reobf-safe)");
        assertFalse(rulesPool.contains("net/minecraftforge"),
                "ascent rules must stay emulator-safe");

        String hooksPool = new String(readClassBytes(
                        "io/github/python121212/starwright/space/SpaceForgeHooks.class"),
                StandardCharsets.ISO_8859_1);
        assertTrue(hooksPool.contains("SpaceAscentRules"),
                "the server hook must delegate to SpaceAscentRules");
        assertTrue(hooksPool.contains("isArmed"),
                "the hook must guard the vanilla build limit");
        assertTrue(hooksPool.contains("targetSpeed"),
                "the hook must ask the rules for a target velocity");
        assertTrue(hooksPool.contains("stepTowards"),
                "the hook must smooth the velocity every tick");
        assertTrue(hooksPool.contains("START"),
                "the hook must run at Phase.START so creative flight cannot overwrite it");
        assertTrue(hooksPool.contains("needsTemporaryFlightPermission"),
                "the hook must consult the Survival / Adventure permission rule");
        assertTrue(hooksPool.contains("f_20899_"),
                "the hook must read the AT-exposed vanilla jump input");
        assertTrue(hooksPool.contains("ENTITY_GRAVITY"),
                "gravity must be applied as a real Forge attribute modifier");
        assertTrue(hooksPool.contains("gravityScale"),
                "the existing gravityScale curve must actually be used");
        assertFalse(hooksPool.contains("net/minecraft/server/level/ServerLevel"),
                "the hook must never create a ServerLevel or change dimension");

        // No class in the whole production jar may reference a server level.
        for (String entry : emulated.entries()) {
            if (!entry.endsWith(".class")) {
                continue;
            }
            String pool = new String(readClassBytes(entry), StandardCharsets.ISO_8859_1);
            assertFalse(pool.contains("net/minecraft/server/level/ServerLevel"),
                    entry + " must not create/transfer a dimension");
        }
    }

    @Test
    @DisplayName("creative flight keeps climbing from inside space in the production jar")
    void creativeFlightRemainsAvailableInSpace() {
        EmulatedForgeLoader.AscentEmulation ascent = emulated.ascent();
        assertEquals(ascent.maxSpeed(), ascent.creativeSpeedInSpace(), 1e-9,
                "creative flight must still return a climb speed at Y > 5,000");
        assertTrue(ascent.creativeSpeedInSpace() > 0.0);
    }

    @Test
    @DisplayName("production jar grants Survival/Adventure flight permission above the build limit")
    void survivalAndAdventureAreGrantedFlightPermission() {
        assertTrue(emulated.ascent().survivalNeedsFlightPermission(),
                "Survival / Adventure must not be kicked by the vanilla anti-flying check");
    }

    @Test
    @DisplayName("access transformer exposes the vanilla jump input, without an item or UI")
    void accessTransformerExposesVanillaJumpInput() throws Exception {
        assertTrue(emulated.entries().contains("META-INF/accesstransformer.cfg"),
                "the production jar must ship the access transformer");
        String at = readEntry("META-INF/accesstransformer.cfg");
        assertTrue(at.contains("public net.minecraft.world.entity.LivingEntity f_20899_"),
                "AstroDock's proven SRG name for LivingEntity.jumping is required");
        assertTrue(at.contains("# jumping"));
    }

    @Test
    @DisplayName("production jar uses the vanilla jump input as the ascent control")
    void jumpInputDrivesTheAscentInProductionJar() {
        EmulatedForgeLoader.AscentEmulation ascent = emulated.ascent();
        assertEquals(ascent.maxSpeed(), ascent.jumpHeldSpeed(), 1e-9,
                "holding jump must climb");
        assertEquals(0.0, ascent.idleSpeedWithoutJump(), 1e-9,
                "releasing jump must stop - no automatic ascent for Survival / Adventure");
    }

    @Test
    @DisplayName("no AstroDock items, screens or pasted ship code were copied in")
    void noItemsOrUiWereCopiedIn() {
        for (String entry : emulated.entries()) {
            assertFalse(entry.contains("Screen.class"), "no UI: " + entry);
            assertFalse(entry.contains("flight_capsule"), "no capsule item: " + entry);
            assertFalse(entry.contains("FlightCapsule"), "no capsule entity: " + entry);
            assertFalse(entry.contains("ShipGuideScreen"), "no ship guide UI: " + entry);
            assertFalse(entry.contains("CoreScreen"), "no core UI: " + entry);
            assertFalse(entry.startsWith("data/"), "no dimension overrides: " + entry);
        }
    }

    @Test
    @DisplayName("one refmap-free mixin cancels terrain chunk rendering")
    void mixinCancelsTerrainRendering() throws Exception {
        assertTrue(emulated.entries().contains("starwright.mixins.json"),
                "the production jar must ship the mixin config");
        String mixins = readEntry("starwright.mixins.json");
        assertTrue(mixins.contains("SpaceLevelRendererMixin"));
        assertTrue(mixins.contains("\"client\""), "the mixin must be client-only");
        assertFalse(mixins.contains("refmap"),
                "no refmap is needed: remap=false with the literal SRG name");
        // Forge 1.20.1 reads MixinConfigs from the jar manifest, not mods.toml.
        String manifest = readEntry("META-INF/MANIFEST.MF");
        assertTrue(manifest.contains("MixinConfigs: starwright.mixins.json"),
                "the production jar must register its mixin config in the manifest");
        assertFalse(readEntry("META-INF/mods.toml").contains("[[mixins]]"),
                "mods.toml [[mixins]] is a NeoForge-only key on Forge 1.20.1");

        String pool = new String(readClassBytes(
                        "io/github/python121212/starwright/space/client/mixin/"
                                + "SpaceLevelRendererMixin.class"),
                StandardCharsets.ISO_8859_1);
        assertTrue(pool.contains("m_172993_"),
                "m_172993_ is Forge 1.20.1's SRG name for renderChunkLayer");
        assertTrue(pool.contains("skipGroundRendering"),
                "the mixin must consult the client-side cut-off");
        assertTrue(pool.contains("cancel"), "the injector must cancel the call");
    }

    @Test
    @DisplayName("production jar sky rules darken, add stars and cut the ground")
    void productionJarRenderingRulesArePureAndGradual() {
        EmulatedForgeLoader.RenderEmulation render = emulated.render();
        assertEquals(SpaceRenderRules.STAR_COUNT, render.starCount());
        assertEquals(0, render.starsAtGround());
        assertEquals(SpaceRenderRules.STAR_COUNT, render.starsAtSpace());
        assertTrue(render.starsAt1000() > 0 && render.starsAt1000() < render.starCount(),
                "stars must appear gradually, not all at once");
        assertTrue(render.monotonicStars(), "visible stars must never decrease");
        assertEquals(0x659be2, render.skyColorAtGround());
        assertEquals(0x000000, render.skyColorAtSpace());
        assertEquals(1.0, render.fogAtGround(), 1e-9);
        assertEquals(0.0, render.fogAtSpace(), 1e-9);
        assertFalse(render.skipGroundAtGround());
        assertTrue(render.skipGroundAtSpace());
    }

    @Test
    @DisplayName("rendering is lightweight: one renderer, one mixin, no assets")
    void renderingIsLightweight() throws Exception {
        long renderers = emulated.entries().stream()
                .filter(entry -> entry.endsWith("Renderer.class") && entry.contains("/space/"))
                .count();
        assertEquals(1, renderers, "only SpaceSkyRenderer may render");
        long mixins = emulated.entries().stream()
                .filter(entry -> entry.endsWith("Mixin.class"))
                .count();
        assertEquals(1, mixins, "only one mixin");
        for (String entry : emulated.entries()) {
            assertFalse(entry.endsWith(".refmap.json"), "no refmap: " + entry);
            assertFalse(entry.endsWith(".png"), "no textures: " + entry);
            assertFalse(entry.contains("Screen.class"), "no UI: " + entry);
        }
        assertEquals(3, emulated.entries().stream()
                        .filter(entry -> entry.startsWith("assets/starwright/models/"))
                        .count(),
                "only three tiny models, all reusing vanilla textures");
        String pool = new String(readClassBytes(
                        "io/github/python121212/starwright/space/SpaceRenderRules.class"),
                StandardCharsets.ISO_8859_1);
        assertFalse(pool.contains("net/minecraft"),
                "SpaceRenderRules must stay pure Java");
        assertFalse(pool.contains("net/minecraftforge"),
                "SpaceRenderRules must stay emulator-safe");
    }

    @Test
    @DisplayName("core block, wrench and ship entity are shipped in the jar")
    void shipContentIsPackaged() {
        for (String name : List.of(
                "io/github/python121212/starwright/ship/ShipTuning.class",
                "io/github/python121212/starwright/ship/ShipBlockSource.class",
                "io/github/python121212/starwright/ship/ShipStructureRules.class",
                "io/github/python121212/starwright/ship/ShipStructure.class",
                "io/github/python121212/starwright/ship/ShipPhysics.class",
                "io/github/python121212/starwright/ship/ShipInteractionRules.class",
                "io/github/python121212/starwright/ship/forge/CoreBlock.class",
                "io/github/python121212/starwright/ship/forge/WrenchItem.class",
                "io/github/python121212/starwright/ship/forge/ShipEntity.class",
                "io/github/python121212/starwright/ship/forge/ShipForgeBridge.class",
                "io/github/python121212/starwright/ship/forge/ShipRegistry.class",
                "io/github/python121212/starwright/ship/forge/client/ShipClientRegistry.class",
                "io/github/python121212/starwright/ship/forge/client/NoopShipRenderer.class")) {
            assertTrue(emulated.entries().contains(name), "missing from jar: " + name);
        }
    }

    @Test
    @DisplayName("ship engine is pure Java and survives reobf without MC names")
    void shipEngineStaysPureJava() throws Exception {
        for (String name : List.of("ShipTuning", "ShipStructureRules", "ShipStructure",
                "ShipPhysics", "ShipInteractionRules")) {
            String pool = new String(readClassBytes(
                            "io/github/python121212/starwright/ship/" + name + ".class"),
                    StandardCharsets.ISO_8859_1);
            assertFalse(pool.contains("net/minecraft"), name + " must stay pure Java");
            assertFalse(pool.contains("net/minecraftforge"),
                    name + " must stay emulator-safe");
        }
    }

    @Test
    @DisplayName("no Create, Create Aeronautics or flywheel dependency anywhere")
    void noCreateOrAeronauticsDependency() throws Exception {
        String toml = readEntry("META-INF/mods.toml");
        assertFalse(toml.contains("simibubi"), "no Create mod id in the manifest");
        assertFalse(toml.contains("createaeronautics"), "no CA mod id in the manifest");
        for (String entry : emulated.entries()) {
            assertFalse(entry.contains("simibubi"), "no Create package: " + entry);
            assertFalse(entry.contains("createaeronautics"), "no CA package: " + entry);
            assertFalse(entry.contains("flywheel"), "no flywheel renderer: " + entry);
            if (entry.endsWith(".class")) {
                String pool = new String(readClassBytes(entry), StandardCharsets.ISO_8859_1);
                assertFalse(pool.contains("com/simibubi"), "no Create reference: " + entry);
                assertFalse(pool.contains("createaeronautics"), "no CA reference: " + entry);
            }
        }
    }

    @Test
    @DisplayName("ship models are minimal and reuse vanilla textures")
    void shipModelsAreMinimal() throws Exception {
        assertEquals(1, emulated.entries().stream()
                .filter(entry -> entry.startsWith("assets/starwright/blockstates/"))
                .count());
        for (String entry : emulated.entries()) {
            assertFalse(entry.endsWith(".png"), "no new textures: " + entry);
        }
        assertTrue(readEntry("assets/starwright/models/block/core_block.json")
                .contains("minecraft:block/iron_block"));
        assertTrue(readEntry("assets/starwright/models/item/wrench.json")
                .contains("minecraft:item/iron_ingot"));
    }

    @Test
    @DisplayName("localisation names the core block, wrench and ship")
    void shipNamesAreLocalised() throws Exception {
        for (String lang : List.of("en_us", "ja_jp")) {
            JsonObject json = JsonParser.parseString(
                    readEntry("assets/starwright/lang/" + lang + ".json")).getAsJsonObject();
            assertTrue(json.has("block.starwright.core_block"));
            assertTrue(json.has("item.starwright.wrench"));
            assertTrue(json.has("entity.starwright.ship"));
        }
        String japanese = readEntry("assets/starwright/lang/ja_jp.json");
        assertTrue(japanese.contains("コアブロック"));
        assertTrue(japanese.contains("レンチ"));
    }

    @Test
    @DisplayName("production jar assembles only a floating hull")
    void productionJarShipEngineAssemblesOnlyFloatingHulls() {
        EmulatedForgeLoader.ShipEmulation ship = emulated.ship();
        assertTrue(ship.floating(), "an airborne hull must assemble");
        assertTrue(ship.groundedRejected(), "a grounded hull must be refused");
        assertEquals(6, ship.collected());
        assertTrue(ship.coreProtected(), "the core must never be wrenched off");
        assertTrue(ship.spareDetachable(), "extra blocks must be removable");
        assertTrue(ship.wrenchDetach());
        assertTrue(ship.wrenchAdvance());
        assertTrue(ship.chestPassthrough(),
                "chests / walking / placing blocks stay vanilla");
        assertEquals(0, ship.stepX());
        assertEquals(0, ship.stepY());
        assertEquals(1, ship.stepZ(), "one axis, one block per click");
        assertTrue(ship.visibleFaces() > 0);
        assertEquals(4096, ship.maxBlocks());
        assertEquals(2, ship.minBlocks());
    }

    @Test
    @DisplayName("pseudo Minecraft recorded every ship-engine decision")
    void pseudoMinecraftRecordedShipDecisions() {
        assertEquals(3, emulated.pseudoMinecraftEvents().stream()
                .filter(event -> event.startsWith("ship:")).count());
        assertTrue(emulated.pseudoMinecraftEvents().stream()
                .anyMatch(event -> event.contains("groundedRejected=true")));
    }
}
