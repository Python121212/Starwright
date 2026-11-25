package io.github.python121212.starwright.ship;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.Set;

/**
 * Flood fill, "is it floating?" and detach rules for the ship engine.
 *
 * <p>Pure Java apart from {@code java.util}. Every coordinate is relative to the
 * core block and packed into one {@code long}, which keeps the emulator cheap and
 * avoids allocating a {@code BlockPos} per visited block.</p>
 */
public final class ShipStructureRules {

    private ShipStructureRules() {}

    public static long pack(int x, int y, int z) {
        return ((long) (x + ShipTuning.COORD_BIAS) << 22)
                | ((long) (y + ShipTuning.COORD_BIAS) << 11)
                | (long) (z + ShipTuning.COORD_BIAS);
    }

    public static int px(long packed) {
        return (int) ((packed >> 22) & ShipTuning.COORD_MASK) - ShipTuning.COORD_BIAS;
    }

    public static int py(long packed) {
        return (int) ((packed >> 11) & ShipTuning.COORD_MASK) - ShipTuning.COORD_BIAS;
    }

    public static int pz(long packed) {
        return (int) (packed & ShipTuning.COORD_MASK) - ShipTuning.COORD_BIAS;
    }

    public static boolean inRange(int x, int y, int z) {
        return Math.abs(x) <= ShipTuning.MAX_RADIUS
                && Math.abs(y) <= ShipTuning.MAX_RADIUS
                && Math.abs(z) <= ShipTuning.MAX_RADIUS;
    }

    /** Six face-adjacent packed coordinates. Bias keeps the arithmetic borrow-free. */
    public static long[] neighbours(long packed) {
        return new long[] {
                packed + (1L << 22), packed - (1L << 22),
                packed + (1L << 11), packed - (1L << 11),
                packed + 1L, packed - 1L
        };
    }

    /**
     * Flood-fills the connected non-air structure starting at the core.
     * Stops at {@link ShipTuning#MAX_BLOCKS} and at the radius limit, so a
     * core placed on a mountain can never swallow the whole world.
     */
    public static long[] collect(int originX, int originY, int originZ, ShipBlockSource source) {
        long core = pack(originX, originY, originZ);
        ArrayList<Long> found = new ArrayList<>();
        HashSet<Long> seen = new HashSet<>();
        ArrayDeque<Long> queue = new ArrayDeque<>();
        seen.add(core);
        queue.add(core);

        while (!queue.isEmpty() && found.size() < ShipTuning.MAX_BLOCKS) {
            long current = queue.poll();
            int x = px(current), y = py(current), z = pz(current);
            if (!inRange(x - originX, y - originY, z - originZ)) {
                continue;
            }
            if (!source.isCore(current) && !source.isSolid(current)) {
                continue;
            }
            found.add(current);
            for (long next : neighbours(current)) {
                if (seen.add(next)) {
                    queue.add(next);
                }
            }
        }

        long[] result = new long[found.size()];
        for (int i = 0; i < result.length; i++) {
            result[i] = found.get(i);
        }
        return result;
    }

    /**
     * "A ship the player built, floating in the air".
     *
     * <p>The flood fill deliberately follows every non-air block, including
     * terrain. So "no non-air neighbour outside the structure" would be wrong:
     * a hull resting on grass would simply absorb the ground and look floating.
     * Instead we look at the BOTTOM of the collected structure:</p>
     * <ul>
     *   <li>if the fill reached the downward radius limit, it ran into terrain;</li>
     *   <li>if any bottom cell still has a non-air block directly below it, the
     *       structure is resting on something.</li>
     * </ul>
     * Only a hull with clear air under its whole underside passes.
     */
    public static boolean isFloating(long[] blocks, ShipBlockSource source) {
        if (blocks.length < ShipTuning.MIN_BLOCKS) {
            return false;
        }
        int minY = Integer.MAX_VALUE;
        for (long block : blocks) {
            minY = Math.min(minY, py(block));
        }
        if (minY <= -ShipTuning.MAX_RADIUS) {
            return false;
        }
        for (long block : blocks) {
            if (py(block) == minY && source.isSolid(block - (1L << 11))) {
                return false;
            }
        }
        return true;
    }

    /**
     * Number of block faces actually exposed. Starwright never bakes a mesh -
     * vanilla draws the real blocks - but this is the cheap upper bound used to
     * prove the engine does no per-frame geometry work.
     */
    public static int visibleFaces(long[] blocks) {
        Set<Long> owned = toSet(blocks);
        int faces = 0;
        for (long block : blocks) {
            for (long neighbour : neighbours(block)) {
                if (!owned.contains(neighbour)) {
                    faces++;
                }
            }
        }
        return faces;
    }

    public static boolean contains(long[] blocks, long packed) {
        for (long block : blocks) {
            if (block == packed) {
                return true;
            }
        }
        return false;
    }

    /** An accidentally attached block may be removed; the core never may. */
    public static boolean canDetach(long[] blocks, long target, ShipBlockSource source) {
        return contains(blocks, target)
                && !source.isCore(target)
                && blocks.length - 1 >= ShipTuning.MIN_BLOCKS;
    }

    public static long[] without(long[] blocks, long target) {
        long[] next = new long[blocks.length - 1];
        int i = 0;
        for (long block : blocks) {
            if (block != target) {
                next[i++] = block;
            }
        }
        return next;
    }

    private static Set<Long> toSet(long[] blocks) {
        HashSet<Long> set = new HashSet<>(blocks.length * 2);
        for (long block : blocks) {
            set.add(block);
        }
        return set;
    }
}
