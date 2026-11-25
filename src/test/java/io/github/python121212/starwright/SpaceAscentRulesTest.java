package io.github.python121212.starwright;

import io.github.python121212.starwright.space.SpaceAscentRules;
import io.github.python121212.starwright.space.SpaceRules;
import io.github.python121212.starwright.space.SpaceStage;
import io.github.python121212.starwright.space.SpaceTuning;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.EnumSet;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

@DisplayName("Continuous ascent / descent to Y=5,000")
class SpaceAscentRulesTest {

    private static final double EPS = 1e-9;

    @Test
    @DisplayName("ascent arms exactly one block above the vanilla build limit")
    void ascentArmsExactlyAboveVanillaBuildLimit() {
        assertEquals(320, SpaceTuning.ASCENT_ARM_Y);
        assertEquals(SpaceTuning.VANILLA_MAX_BUILD_Y + 1, SpaceTuning.ASCENT_ARM_Y);
        assertFalse(SpaceAscentRules.isArmed(319.0));
        assertFalse(SpaceAscentRules.isArmed(-64.0));
        assertTrue(SpaceAscentRules.isArmed(320.0));
        assertTrue(SpaceAscentRules.isArmed(1_000.0));
        assertTrue(SpaceAscentRules.isArmed(5_000.0));
    }

    @Test
    @DisplayName("below the build limit nothing is changed - vanilla stays vanilla")
    void belowBuildLimitNothingChanges() {
        for (double y : new double[] {-64, 0, 100, 319, 319.999}) {
            assertEquals(0.0, SpaceAscentRules.targetSpeed(y, true, false, false), EPS);
            assertEquals(0.0, SpaceAscentRules.targetSpeed(y, false, true, false), EPS);
        }
        assertTrue(SpaceAscentRules.targetSpeed(320, true, false, false) > 0.0);
    }

    @Test
    @DisplayName("jump rises, sneak descends, both/neither holds altitude")
    void vanillaInputsSelectDirection() {
        double max = SpaceTuning.ASCENT_MAX_SPEED;
        assertEquals(max, SpaceAscentRules.targetSpeed(400, true, false, false), EPS);
        assertEquals(-max, SpaceAscentRules.targetSpeed(400, false, true, false), EPS);
        assertEquals(0.0, SpaceAscentRules.targetSpeed(400, true, true, false), EPS);
        assertEquals(0.0, SpaceAscentRules.targetSpeed(400, false, false, false), EPS);
    }

    @Test
    @DisplayName("elytra flight never receives an ascent boost")
    void elytraNeverGetsAnAscentBoost() {
        assertEquals(0.0, SpaceAscentRules.targetSpeed(400, true, false, true), EPS);
        assertEquals(0.0, SpaceAscentRules.targetSpeed(4_000, false, true, true), EPS);
        assertFalse(SpaceRules.isElytraAllowed(5_000));
    }

    @Test
    @DisplayName("clampSpeed bounds every velocity, including NaN")
    void velocityIsAlwaysBounded() {
        double max = SpaceTuning.ASCENT_MAX_SPEED;
        assertEquals(max, SpaceAscentRules.clampSpeed(100.0), EPS);
        assertEquals(-max, SpaceAscentRules.clampSpeed(-100.0), EPS);
        assertEquals(max, SpaceAscentRules.clampSpeed(max), EPS);
        assertEquals(0.0, SpaceAscentRules.clampSpeed(Double.NaN), EPS);
        assertEquals(-0.5, SpaceAscentRules.clampSpeed(-0.5), EPS);
    }

    @Test
    @DisplayName("one tick never changes velocity by more than the acceleration")
    void accelerationIsBounded() {
        double accel = SpaceTuning.ASCENT_ACCELERATION;
        assertEquals(accel, SpaceAscentRules.stepTowards(0.0, 1.0), EPS);
        assertEquals(-accel, SpaceAscentRules.stepTowards(0.0, -1.0), EPS);
        assertEquals(0.0, SpaceAscentRules.stepTowards(0.0, 0.0), EPS);

        double v = 0.0;
        for (int tick = 0; tick < 1_000; tick++) {
            double next = SpaceAscentRules.stepTowards(v, 1.0);
            assertTrue(Math.abs(next - v) <= accel + EPS,
                    "velocity jumped at tick " + tick);
            assertTrue(Math.abs(next) <= SpaceTuning.ASCENT_MAX_SPEED + EPS);
            v = next;
        }
        assertEquals(SpaceTuning.ASCENT_MAX_SPEED, v, EPS);
    }

    @Test
    @DisplayName("the full climb to space is continuous and passes every stage")
    void trajectoryToSpaceIsContinuous() {
        double y = SpaceTuning.ASCENT_ARM_Y;
        double velocity = 0.0;
        double previous = y;
        int ticks = 0;
        Set<SpaceStage> seen = EnumSet.noneOf(SpaceStage.class);
        seen.add(SpaceRules.stageForY(y));

        while (y < SpaceTuning.SPACE_START_Y && ticks < 50_000) {
            double target = SpaceAscentRules.targetSpeed(y, true, false, false);
            velocity = SpaceAscentRules.stepTowards(velocity, target);
            y += velocity;

            double step = y - previous;
            assertTrue(step >= -EPS, "altitude must not decrease while rising, tick " + ticks);
            assertTrue(step <= SpaceTuning.ASCENT_MAX_SPEED + EPS,
                    "a single tick moved more than one metre - that would be a teleport, tick " + ticks);
            assertTrue(Math.abs(velocity) <= SpaceTuning.ASCENT_MAX_SPEED + EPS);
            seen.add(SpaceRules.stageForY(y));
            previous = y;
            ticks++;
        }

        assertTrue(y >= SpaceTuning.SPACE_START_Y, "climb never reached space");
        assertTrue(SpaceRules.isSpace(y));
        assertTrue(seen.containsAll(EnumSet.of(
                SpaceStage.TROPOSPHERE, SpaceStage.STRATOSPHERE, SpaceStage.MESOSPHERE,
                SpaceStage.THERMOSPHERE, SpaceStage.EXOSPHERE, SpaceStage.SPACE)),
                "the climb must pass through every atmospheric layer: " + seen);
        assertTrue(ticks >= (SpaceTuning.SPACE_START_Y - SpaceTuning.ASCENT_ARM_Y)
                        / SpaceTuning.ASCENT_MAX_SPEED,
                "reaching Y=5,000 too fast would not be continuous: " + ticks + " ticks");
        assertEquals(SpaceTuning.OVERWORLD, SpaceRules.stateAt(y, true).dimensionId());
    }

    @Test
    @DisplayName("descent brings the player back towards the build limit")
    void descentReturnsTowardsTheBuildLimit() {
        double y = 5_000.0;
        double velocity = SpaceTuning.ASCENT_MAX_SPEED;
        boolean reversed = false;
        int ticks = 0;

        while (y > SpaceTuning.ASCENT_ARM_Y + 1.0 && ticks < 50_000) {
            double target = SpaceAscentRules.targetSpeed(y, false, true, false);
            velocity = SpaceAscentRules.stepTowards(velocity, target);
            y += velocity;
            assertTrue(Math.abs(velocity) <= SpaceTuning.ASCENT_MAX_SPEED + EPS);
            if (velocity < 0.0) reversed = true;
            ticks++;
        }

        assertTrue(reversed, "sneak must actually reverse the vertical velocity");
        assertTrue(y < 5_000.0, "descent must return from space");
        assertTrue(y <= SpaceTuning.ASCENT_ARM_Y + 1.0 + EPS);
        assertFalse(SpaceRules.isSpace(y));
    }
}
