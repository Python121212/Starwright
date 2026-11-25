package io.github.python121212.starwright;

import io.github.python121212.starwright.space.SpaceRules;
import io.github.python121212.starwright.space.SpaceStage;
import io.github.python121212.starwright.space.SpaceState;
import io.github.python121212.starwright.space.SpaceTuning;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

@DisplayName("Seamless space layer rules")
class SpaceRulesTest {

    private static final double EPS = 1e-9;

    @Test
    @DisplayName("space starts exactly at Y = 5,000")
    void spaceStartsExactlyAt5000() {
        assertEquals(5_000, SpaceTuning.SPACE_START_Y);
        assertEquals(SpaceStage.EXOSPHERE, SpaceRules.stageForY(4_999.0));
        assertEquals(SpaceStage.SPACE, SpaceRules.stageForY(5_000.0));
        assertFalse(SpaceRules.isSpace(4_999.0));
        assertTrue(SpaceRules.isSpace(5_000.0));
        assertTrue(SpaceRules.stateAt(5_000.0, true).isSpace());
    }

    @Test
    @DisplayName("atmospheric layers progress in order")
    void stagesProgressThroughAtmosphericLayers() {
        assertEquals(SpaceStage.GROUND, SpaceRules.stageForY(0));
        assertEquals(SpaceStage.TROPOSPHERE, SpaceRules.stageForY(400));
        assertEquals(SpaceStage.STRATOSPHERE, SpaceRules.stageForY(800));
        assertEquals(SpaceStage.MESOSPHERE, SpaceRules.stageForY(2_000));
        assertEquals(SpaceStage.THERMOSPHERE, SpaceRules.stageForY(3_200));
        assertEquals(SpaceStage.EXOSPHERE, SpaceRules.stageForY(4_200));
        assertEquals(SpaceStage.SPACE, SpaceRules.stageForY(5_000));
    }

    @Test
    @DisplayName("the sky darkens gradually and monotonically")
    void darknessIncreasesSmoothly() {
        assertEquals(0.0, SpaceRules.darkness(0), EPS);
        assertEquals(1.0, SpaceRules.darkness(5_000), EPS);
        assertTrue(SpaceRules.darkness(1_000) > 0.0);

        double previous = -1.0;
        for (int y = 0; y <= 5_000; y += 250) {
            double now = SpaceRules.darkness(y);
            assertTrue(now >= previous, "darkness must not decrease at Y=" + y);
            assertTrue(now >= 0.0 && now <= 1.0);
            previous = now;
        }
    }

    @Test
    @DisplayName("visible stars increase gradually, later than the darkness")
    void starsIncreaseGradually() {
        assertEquals(0.0, SpaceRules.starVisibility(0), EPS);
        assertEquals(1.0, SpaceRules.starVisibility(5_000), EPS);

        double previous = -1.0;
        for (int y = 0; y <= 5_000; y += 250) {
            double now = SpaceRules.starVisibility(y);
            assertTrue(now >= previous, "stars must not decrease at Y=" + y);
            assertTrue(now >= 0.0 && now <= 1.0);
            previous = now;
        }
        // Low down the sky is already dim while few stars are visible yet.
        assertTrue(SpaceRules.darkness(1_000) > SpaceRules.starVisibility(1_000));
    }

    @Test
    @DisplayName("gravity weakens towards space but never reaches zero")
    void gravityFallsTowardZero() {
        assertEquals(1.0, SpaceRules.gravityScale(0), EPS);
        assertEquals(0.05, SpaceRules.gravityScale(5_000), EPS);

        double previous = Double.MAX_VALUE;
        for (int y = 0; y <= 5_000; y += 250) {
            double now = SpaceRules.gravityScale(y);
            assertTrue(now <= previous, "gravity must not increase at Y=" + y);
            assertTrue(now > 0.0);
            previous = now;
        }
    }

    @Test
    @DisplayName("elytra cannot be used to reach space")
    void elytraIsBlockedAboveCutoff() {
        assertTrue(SpaceRules.isElytraAllowed(999));
        assertFalse(SpaceRules.isElytraAllowed(1_000));
        assertFalse(SpaceRules.isElytraAllowed(5_000));
        assertFalse(SpaceRules.stateAt(1_000, true).elytraAllowed());
    }

    @Test
    @DisplayName("ground rendering is skipped in space and when the ground is invisible")
    void groundRenderingIsSkippedOnlyAtHighAltitude() {
        assertFalse(SpaceRules.shouldSkipGroundRendering(0));
        assertFalse(SpaceRules.shouldSkipGroundRendering(4_199));
        assertTrue(SpaceRules.shouldSkipGroundRendering(4_200));
        assertTrue(SpaceRules.shouldSkipGroundRendering(5_000));
        assertFalse(SpaceRules.stateAt(0, false).skipGroundRendering());
        assertTrue(SpaceRules.stateAt(5_000, false).skipGroundRendering());
    }

    @Test
    @DisplayName("no dimension transfer: every altitude stays in the Overworld")
    void stateAlwaysStaysInTheOverworld() {
        for (int y : new int[] {0, 1_000, 4_200, 5_000, 9_000}) {
            assertEquals(SpaceTuning.OVERWORLD,
                    SpaceRules.stateAt(y, true).dimensionId());
            assertEquals(SpaceTuning.OVERWORLD,
                    SpaceRules.stateAt(y, false).dimensionId());
        }
    }

    @Test
    @DisplayName("server and client states are explicitly separated")
    void serverAndClientStatesAreDistinct() {
        SpaceState server = SpaceRules.stateAt(5_000, true);
        SpaceState client = SpaceRules.stateAt(5_000, false);

        assertTrue(server.serverSide());
        assertFalse(client.serverSide());
        assertEquals(server.stage(), client.stage());
        assertEquals(server.darkness(), client.darkness(), EPS);
        assertTrue(server.isSpace());
    }
}
