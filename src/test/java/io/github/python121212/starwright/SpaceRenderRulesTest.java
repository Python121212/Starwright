package io.github.python121212.starwright;

import io.github.python121212.starwright.space.SpaceRenderRules;
import io.github.python121212.starwright.space.SpaceRules;
import io.github.python121212.starwright.space.SpaceTuning;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

@DisplayName("Client sky rendering rules")
class SpaceRenderRulesTest {

    private static final double EPS = 1e-9;

    @Test
    @DisplayName("visible stars grow gradually with altitude")
    void visibleStarsGrowGradually() {
        assertEquals(SpaceRenderRules.STAR_COUNT, SpaceRenderRules.visibleStars(1.0));
        assertEquals(0, SpaceRenderRules.visibleStars(SpaceRules.starVisibility(0)));

        int previous = -1;
        for (int y = 0; y <= SpaceTuning.SPACE_START_Y; y += 250) {
            int now = SpaceRenderRules.visibleStars(SpaceRules.starVisibility(y));
            assertTrue(now >= previous, "stars must not decrease at Y=" + y);
            assertTrue(now >= 0 && now <= SpaceRenderRules.STAR_COUNT);
            previous = now;
        }
        assertEquals(SpaceRenderRules.STAR_COUNT,
                SpaceRenderRules.visibleStars(SpaceRules.starVisibility(5_000)));
    }

    @Test
    @DisplayName("the sky blends from vanilla blue to black")
    void skyDarkensToBlack() {
        assertEquals(0x659be2, SpaceRenderRules.skyColor(0.0));
        assertEquals(0x000000, SpaceRenderRules.skyColor(1.0));

        int previousR = 256, previousG = 256, previousB = 256;
        for (int y = 0; y <= SpaceTuning.SPACE_START_Y; y += 250) {
            int colour = SpaceRenderRules.skyColor(SpaceRules.darkness(y));
            int r = (colour >> 16) & 255, g = (colour >> 8) & 255, b = colour & 255;
            assertTrue(r <= previousR && g <= previousG && b <= previousB,
                    "sky must not brighten at Y=" + y);
            previousR = r; previousG = g; previousB = b;
        }
    }

    @Test
    @DisplayName("fog fades to zero as the sky darkens")
    void fogFadesToZero() {
        assertEquals(1.0, SpaceRenderRules.fogFactor(0.0), EPS);
        assertEquals(0.0, SpaceRenderRules.fogFactor(1.0), EPS);

        double previous = Double.MAX_VALUE;
        for (int y = 0; y <= SpaceTuning.SPACE_START_Y; y += 250) {
            double now = SpaceRenderRules.fogFactor(SpaceRules.darkness(y));
            assertTrue(now <= previous + EPS, "fog must not brighten at Y=" + y);
            assertTrue(now >= 0.0 && now <= 1.0);
            previous = now;
        }
    }

    @Test
    @DisplayName("the ground-render cut-off matches the client rule exactly")
    void skipGroundMatchesClientCutoff() {
        for (int y : new int[] {0, 800, 2_000, 4_199}) {
            assertFalse(SpaceRenderRules.skipGround(y), "Y=" + y);
        }
        for (int y : new int[] {4_200, 5_000, 9_000}) {
            assertTrue(SpaceRenderRules.skipGround(y), "Y=" + y);
        }
        for (int y = 0; y <= 9_000; y += 500) {
            assertEquals(SpaceRules.shouldSkipGroundRendering(y),
                    SpaceRenderRules.skipGround(y));
        }
    }

    @Test
    @DisplayName("no stars on the ground, some in the stratosphere, all in space")
    void starsAppearLaterThanDarkness() {
        assertEquals(0, SpaceRenderRules.visibleStars(SpaceRules.starVisibility(0)));
        int at1000 = SpaceRenderRules.visibleStars(SpaceRules.starVisibility(1_000));
        assertTrue(at1000 > 0);
        assertTrue(at1000 < SpaceRenderRules.STAR_COUNT);
        assertTrue(SpaceRules.darkness(1_000) > SpaceRules.starVisibility(1_000));
    }

    @Test
    @DisplayName("out-of-range and NaN inputs are clamped safely")
    void inputsAreClamped() {
        assertEquals(0, SpaceRenderRules.visibleStars(-1.0));
        assertEquals(SpaceRenderRules.STAR_COUNT, SpaceRenderRules.visibleStars(2.0));
        assertEquals(SpaceRenderRules.skyColor(0.0),
                SpaceRenderRules.skyColor(-1.0));
        assertEquals(SpaceRenderRules.skyColor(1.0),
                SpaceRenderRules.skyColor(2.0));
        assertEquals(1.0, SpaceRenderRules.fogFactor(Double.NaN), EPS);
        assertEquals(0, SpaceRenderRules.visibleStars(Double.NaN));
    }
}
