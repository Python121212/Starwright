package io.github.python121212.starwright;

import io.github.python121212.starwright.ship.ShipBlockSource;
import io.github.python121212.starwright.ship.ShipInteractionRules;
import io.github.python121212.starwright.ship.ShipPhysics;
import io.github.python121212.starwright.ship.ShipStructure;
import io.github.python121212.starwright.ship.ShipStructureRules;
import io.github.python121212.starwright.ship.ShipTuning;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

@DisplayName("Starwright's own lightweight ship engine")
class ShipStructureRulesTest {

    private static final long CORE = ShipStructureRules.pack(0, 0, 0);

    private static ShipBlockSource sourceOf(Set<Long> solid) {
        return new ShipBlockSource() {
            @Override
            public boolean isSolid(long packed) {
                return solid.contains(packed);
            }

            @Override
            public boolean isCore(long packed) {
                return packed == CORE;
            }
        };
    }

    private static Set<Long> floatingHull() {
        Set<Long> solid = new HashSet<>();
        solid.add(CORE);
        solid.add(ShipStructureRules.pack(1, 0, 0));
        solid.add(ShipStructureRules.pack(0, 1, 0));
        solid.add(ShipStructureRules.pack(0, 0, 1));
        solid.add(ShipStructureRules.pack(2, 0, 0));
        solid.add(ShipStructureRules.pack(1, 1, 0));
        return solid;
    }

    @Test
    @DisplayName("packed coordinates round-trip without borrowing between axes")
    void packedCoordinatesRoundTrip() {
        for (int[] c : new int[][] {{0, 0, 0}, {1, -1, 2}, {-32, 7, 31}, {32, -32, 0}}) {
            long packed = ShipStructureRules.pack(c[0], c[1], c[2]);
            assertEquals(c[0], ShipStructureRules.px(packed));
            assertEquals(c[1], ShipStructureRules.py(packed));
            assertEquals(c[2], ShipStructureRules.pz(packed));
        }
        assertTrue(ShipStructureRules.inRange(-32, 32, 0));
        assertFalse(ShipStructureRules.inRange(33, 0, 0));
    }

    @Test
    @DisplayName("flood fill collects exactly the connected hull")
    void collectFindsTheWholeHull() {
        long[] blocks = ShipStructureRules.collect(0, 0, 0, sourceOf(floatingHull()));
        assertEquals(6, blocks.length);
        assertTrue(ShipStructureRules.contains(blocks, CORE));
        assertTrue(ShipStructureRules.contains(blocks,
                ShipStructureRules.pack(2, 0, 0)));
        assertFalse(ShipStructureRules.contains(blocks,
                ShipStructureRules.pack(9, 9, 9)));
    }

    @Test
    @DisplayName("flood fill is bounded so a mountain can never become a ship")
    void collectStopsAtTheHardCap() {
        ShipBlockSource everythingSolid = new ShipBlockSource() {
            @Override public boolean isSolid(long packed) { return true; }
            @Override public boolean isCore(long packed) { return packed == CORE; }
        };
        long[] blocks = ShipStructureRules.collect(0, 0, 0, everythingSolid);
        assertEquals(ShipTuning.MAX_BLOCKS, blocks.length);
        assertTrue(ShipStructureRules.contains(blocks, CORE));
    }

    @Test
    @DisplayName("a hull floating in mid-air assembles")
    void floatingHullPasses() {
        long[] blocks = ShipStructureRules.collect(0, 0, 0, sourceOf(floatingHull()));
        assertTrue(ShipStructureRules.isFloating(blocks, sourceOf(floatingHull())));
    }

    @Test
    @DisplayName("a hull resting on terrain is rejected, not absorbed as 'floating'")
    void groundedHullIsRejected() {
        Set<Long> grounded = floatingHull();
        for (int y = -1; y >= -34; y--) {
            grounded.add(ShipStructureRules.pack(0, y, 0));
        }
        long[] blocks = ShipStructureRules.collect(0, 0, 0, sourceOf(grounded));
        assertFalse(ShipStructureRules.isFloating(blocks, sourceOf(grounded)));
        assertFalse(ShipPhysics.withinLimits(0));
    }

    @Test
    @DisplayName("any solid block directly under the underside rejects the ship")
    void undersideContactIsRejected() {
        ShipBlockSource source = sourceOf(Set.of(
                ShipStructureRules.pack(0, -1, 0)));
        long[] blocks = new long[] {CORE, ShipStructureRules.pack(1, 0, 0)};
        assertFalse(ShipStructureRules.isFloating(blocks, source));
    }

    @Test
    @DisplayName("the core is protected; an accidentally attached block is removable")
    void coreIsProtectedButSpareBlocksDetach() {
        Set<Long> solid = floatingHull();
        long[] blocks = ShipStructureRules.collect(0, 0, 0, sourceOf(solid));
        long spare = ShipStructureRules.pack(2, 0, 0);
        assertFalse(ShipStructureRules.canDetach(blocks, CORE, sourceOf(solid)));
        assertTrue(ShipStructureRules.canDetach(blocks, spare, sourceOf(solid)));
        assertEquals(blocks.length - 1, ShipStructureRules.without(blocks, spare).length);
        assertFalse(ShipStructureRules.contains(
                ShipStructureRules.without(blocks, spare), spare));
    }

    @Test
    @DisplayName("exposed faces are counted once, with no mesh generated")
    void visibleFacesAreCountedCheaply() {
        assertEquals(6, ShipStructureRules.visibleFaces(new long[] {CORE}));
        long[] two = new long[] {CORE, ShipStructureRules.pack(1, 0, 0)};
        assertEquals(10, ShipStructureRules.visibleFaces(two));
        ShipStructure structure = new ShipStructure(two, 100, 80, -20);
        assertEquals(10, structure.visibleFaces());
        assertTrue(structure.contains(101, 80, -20));
        assertFalse(structure.contains(99, 80, -20));
        ShipStructure shifted = structure.shifted(new int[] {0, 0, 1});
        assertArrayEquals(new int[] {100, 80, -19},
                new int[] {shifted.originX(), shifted.originY(), shifted.originZ()});
    }

    @Test
    @DisplayName("movement is one axis, one block, one click - never a physics tick")
    void movementIsOneBlockPerClick() {
        assertArrayEquals(new int[] {1, 0, 0}, ShipPhysics.clampStep(5, 3, 1));
        assertArrayEquals(new int[] {0, -1, 0}, ShipPhysics.clampStep(0, -9, 2));
        assertArrayEquals(new int[] {0, 0, 0}, ShipPhysics.clampStep(0, 0, 0));
        assertTrue(ShipPhysics.isIdle(ShipPhysics.clampStep(0, 0, 0)));
        assertEquals(1, ShipTuning.STEP_BLOCKS_PER_CLICK);
        assertTrue(ShipPhysics.withinLimits(6));
        assertFalse(ShipPhysics.withinLimits(ShipTuning.MAX_BLOCKS + 1));
    }

    @Test
    @DisplayName("wrench actions are explicit; everything else stays vanilla")
    void interactionsFallThroughToVanilla() {
        assertTrue(ShipInteractionRules.isWrenchDetach(true, true, true, false));
        assertFalse(ShipInteractionRules.isWrenchDetach(true, true, true, true));
        assertFalse(ShipInteractionRules.isWrenchDetach(false, true, true, false));
        assertTrue(ShipInteractionRules.isWrenchAdvance(false, true, true));
        assertFalse(ShipInteractionRules.isWrenchAdvance(true, true, true));
        assertTrue(ShipInteractionRules.isVanillaPassthrough(false, true),
                "opening a chest must be handed straight back to Minecraft");
        assertTrue(ShipInteractionRules.coreIsProtected(true));
    }
}
