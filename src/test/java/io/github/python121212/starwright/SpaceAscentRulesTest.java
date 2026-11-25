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
    private static final boolean NOT_CREATIVE = false;
    private static final boolean CREATIVE = true;

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
    @DisplayName("below the build limit nothing is changed - creative flight stays vanilla")
    void belowBuildLimitNothingChanges() {
        for (double y : new double[] {-64, 0, 100, 319, 319.999}) {
            assertEquals(0.0, SpaceAscentRules.targetSpeed(y, false, false, NOT_CREATIVE), EPS);
            assertEquals(0.0, SpaceAscentRules.targetSpeed(y, true, false, NOT_CREATIVE), EPS);
            assertEquals(0.0, SpaceAscentRules.targetSpeed(y, false, false, CREATIVE), EPS);
            assertEquals(0.0, SpaceAscentRules.stepTowards(0.0,
                    SpaceAscentRules.targetSpeed(y, false, false, CREATIVE)), EPS);
        }
        assertTrue(SpaceAscentRules.targetSpeed(320, false, false, CREATIVE) > 0.0);
    }

    @Test
    @DisplayName("continuous climb, sneak descent, and automatic stop on arrival in space")
    void climbDescentAndArrivalBehaviour() {
        double max = SpaceTuning.ASCENT_MAX_SPEED;
        assertEquals(max, SpaceAscentRules.targetSpeed(400, false, false, NOT_CREATIVE), EPS);
        assertEquals(-max, SpaceAscentRules.targetSpeed(400, true, false, NOT_CREATIVE), EPS);
        assertEquals(0.0, SpaceAscentRules.targetSpeed(5_000, false, false, NOT_CREATIVE), EPS,
                "reaching space must stop the climb so the player floats");
        assertEquals(-max, SpaceAscentRules.targetSpeed(5_000, true, false, NOT_CREATIVE), EPS,
                "sneak must still allow descending out of space");
        assertEquals(0.0, SpaceAscentRules.targetSpeed(319, false, false, NOT_CREATIVE), EPS);
    }

    @Test
    @DisplayName("creative flight keeps climbing freely from inside space")
    void creativeFlightKeepsWorkingInSpace() {
        double max = SpaceTuning.ASCENT_MAX_SPEED;
        assertEquals(max, SpaceAscentRules.targetSpeed(5_000, false, false, CREATIVE), EPS,
                "creative flight must not be cut off at the space boundary");
        assertEquals(max, SpaceAscentRules.targetSpeed(9_000, false, false, CREATIVE), EPS);
        assertEquals(-max, SpaceAscentRules.targetSpeed(9_000, true, false, CREATIVE), EPS,
                "sneak still descends while creatively flying");
        assertEquals(0.0, SpaceAscentRules.targetSpeed(5_000, false, false, NOT_CREATIVE), EPS,
                "non-creative arrival still settles into zero gravity");
        assertTrue(SpaceAscentRules.isArmed(9_000));
    }

    @Test
    @DisplayName("elytra flight never receives an ascent boost")
    void elytraNeverGetsAnAscentBoost() {
        assertEquals(0.0, SpaceAscentRules.targetSpeed(400, false, true, NOT_CREATIVE), EPS);
        assertEquals(0.0, SpaceAscentRules.targetSpeed(4_000, true, true, NOT_CREATIVE), EPS);
        assertEquals(0.0, SpaceAscentRules.targetSpeed(4_000, false, true, CREATIVE), EPS);
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
            double target = SpaceAscentRules.targetSpeed(y, false, false, NOT_CREATIVE);
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
            double target = SpaceAscentRules.targetSpeed(y, true, false, NOT_CREATIVE);
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

    @Test
    @DisplayName("Survival and Adventure get temporary flight permission above Y=320")
    void survivalAndAdventureNeedTemporaryFlightPermission() {
        assertTrue(SpaceAscentRules.needsTemporaryFlightPermission(320.0, false));
        assertTrue(SpaceAscentRules.needsTemporaryFlightPermission(1_000.0, false));
        assertTrue(SpaceAscentRules.needsTemporaryFlightPermission(5_000.0, false));
        assertFalse(SpaceAscentRules.needsTemporaryFlightPermission(319.0, false));
        assertFalse(SpaceAscentRules.needsTemporaryFlightPermission(0.0, false));
        // Creative / Spectator already have flight and are never touched.
        assertFalse(SpaceAscentRules.needsTemporaryFlightPermission(5_000.0, true));
    }

    @Test
    @DisplayName("flight permission is released as soon as the player is back below Y=320")
    void flightPermissionIsRestoredBelowBuildLimit() {
        boolean granted = false;
        for (double y = 5_000.0; y >= 0.0; y -= 10.0) {
            boolean needs = SpaceAscentRules.needsTemporaryFlightPermission(y, false);
            if (y >= SpaceTuning.ASCENT_ARM_Y) {
                assertTrue(needs, "must keep flight permission at Y=" + y);
                granted = true;
            } else {
                assertFalse(needs, "must release flight permission at Y=" + y);
            }
        }
        assertTrue(granted);
        assertFalse(SpaceAscentRules.needsTemporaryFlightPermission(319.999, false));
    }
}
