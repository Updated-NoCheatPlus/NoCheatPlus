/*
 * This program is free software: you can redistribute it and/or modify
 *   it under the terms of the GNU General Public License as published by
 *   the Free Software Foundation, either version 3 of the License, or
 *   (at your option) any later version.
 *
 *   This program is distributed in the hope that it will be useful,
 *   but WITHOUT ANY WARRANTY; without even the implied warranty of
 *   MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *   GNU General Public License for more details.
 *
 *   You should have received a copy of the GNU General Public License
 *   along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */
package fr.neatmonster.nocheatplus.checks.moving.velocity;

/**
 * Merge multiple velocity packets on the same server tick into one queued entry.
 * <ul>
 * <li>All additive: sum components, keep {@link VelocityFlags#ADDITIVE}.</li>
 * <li>Set first, then additive: sum onto the set vector, apply as set (replace).</li>
 * <li>Set after anything on the same tick: discard prior same-tick velocity, use the new set.</li>
 * <li>Cross-tick: prior-tick additive is not removed when a set arrives on a later tick (client may still carry additive).</li>
 * </ul>
 */
public final class VelocityTickCoalescer {

    private VelocityTickCoalescer() {
    }

    public static void coalesceAndQueue(final SimpleAxisVelocity verVel, final PairAxisVelocity horVel,
            final int tick, final double vx, final double vy, final double vz, final long flags, final int actCount) {
        final boolean incomingAdditive = (flags & VelocityFlags.ADDITIVE) != 0;
        final SimpleEntry existingVer = verVel.findEntryByTick(tick);
        final PairEntry existingHor = horVel.findEntryByTick(tick);

        double mergedX = vx;
        double mergedY = vy;
        double mergedZ = vz;
        long mergedFlags = flags;
        boolean mergedSetBased = !incomingAdditive;

        if (existingVer != null || existingHor != null) {
            final double prevX = existingHor != null ? existingHor.x : 0.0;
            final double prevY = existingVer != null ? existingVer.value : 0.0;
            final double prevZ = existingHor != null ? existingHor.z : 0.0;
            final long prevFlags = existingVer != null ? existingVer.flags
                    : (existingHor != null ? existingHor.flags : 0L);
            final boolean prevSetBased = (prevFlags & VelocityFlags.ADDITIVE) == 0;

            if (!incomingAdditive) {
                mergedX = vx;
                mergedY = vy;
                mergedZ = vz;
                mergedFlags = flags;
                mergedSetBased = true;
            }
            else if (prevSetBased) {
                mergedX = prevX + vx;
                mergedY = prevY + vy;
                mergedZ = prevZ + vz;
                mergedFlags = mergeOriginFlags(prevFlags, flags);
                mergedSetBased = true;
            }
            else {
                mergedX = prevX + vx;
                mergedY = prevY + vy;
                mergedZ = prevZ + vz;
                mergedFlags = prevFlags | flags;
                mergedSetBased = false;
            }
        }

        if (mergedSetBased) {
            mergedFlags &= ~VelocityFlags.ADDITIVE;
        }
        else {
            mergedFlags |= VelocityFlags.ADDITIVE;
        }

        verVel.removeEntriesByTick(tick);
        horVel.removeEntriesByTick(tick);

        verVel.add(new SimpleEntry(tick, mergedY, mergedFlags, actCount));
        if (mergedX != 0.0 || mergedZ != 0.0) {
            horVel.add(new PairEntry(tick, mergedX, mergedZ, mergedFlags, actCount));
        }
    }

    private static long mergeOriginFlags(final long prevFlags, final long incomingFlags) {
        return prevFlags | (incomingFlags & ~VelocityFlags.ADDITIVE);
    }
}
