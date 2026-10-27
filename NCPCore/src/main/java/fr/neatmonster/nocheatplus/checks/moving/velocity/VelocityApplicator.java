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

import java.util.Collections;
import java.util.LinkedList;
import java.util.List;

import fr.neatmonster.nocheatplus.checks.moving.MovingConfig;
import fr.neatmonster.nocheatplus.checks.moving.MovingData;

/**
 * Fail-first velocity: peek due queue entries on a retry pass, apply at NCP pipeline
 * insertion points (horizontal after entity push; vertical after travel physics), confirm
 * consume only if retry succeeds.
 */
public final class VelocityApplicator {

    /** Non-destructive peek of queue entries due for this move tick. */
    public static final class PendingApplication {
        /** Effective values for apply (may not be queue references). */
        public final SimpleEntry vertical;
        public final PairEntry horizontal;
        public final boolean additive;
        private final List<SimpleEntry> consumeVertical;
        private final List<PairEntry> consumeHorizontal;

        PendingApplication(final SimpleEntry vertical, final PairEntry horizontal, final boolean additive,
                final List<SimpleEntry> consumeVertical, final List<PairEntry> consumeHorizontal) {
            this.vertical = vertical;
            this.horizontal = horizontal;
            this.additive = additive;
            this.consumeVertical = consumeVertical;
            this.consumeHorizontal = consumeHorizontal;
        }

        public boolean isEmpty() {
            return vertical == null && horizontal == null;
        }

        public List<SimpleEntry> getConsumeVertical() {
            return consumeVertical;
        }

        public List<PairEntry> getConsumeHorizontal() {
            return consumeHorizontal;
        }

        /** Debug: effective folded velocity applied on pass B. */
        public String formatDebugLine() {
            final String mode = additive ? "additive" : "set";
            final int tick = vertical != null ? vertical.tick : (horizontal != null ? horizontal.tick : -1);
            final double vx = horizontal != null ? horizontal.x : 0.0;
            final double vy = vertical != null ? vertical.value : 0.0;
            final double vz = horizontal != null ? horizontal.z : 0.0;
            final int srcCount = (consumeVertical != null ? consumeVertical.size() : 0);
            return "Active velocity (" + mode + " tick=" + tick + " from " + srcCount + " queued): "
                    + vx + ", " + vy + ", " + vz;
        }
    }

    private VelocityApplicator() {
    }

    public static boolean hasDueVelocity(final MovingData data, final int currentTick, final MovingConfig cc) {
        return peekDue(data, currentTick, cc) != null;
    }

    /**
     * Peek all due velocity folded in packet-arrival order (client semantics).
     * Additive entries accumulate; a later set replaces motion; additive after set adds onto that set.
     */
    public static PendingApplication peekDue(final MovingData data, final int currentTick, final MovingConfig cc) {
        final List<PendingApplication> alternatives = peekDueAlternatives(data, currentTick, cc);
        return alternatives.isEmpty() ? null : alternatives.get(alternatives.size() - 1);
    }

    /**
     * Candidate velocity folds for Pass B. When prior-tick additive was deferred and the move tick also
     * has a SET packet, the client may apply additive-only (SET after move) or additive-then-SET (SET
     * before move in the same client tick) — try additive-only first, then the full fold where SET wins.
     */
    public static List<PendingApplication> peekDueAlternatives(final MovingData data, final int currentTick,
            final MovingConfig cc) {
        final int invalidateBeforeTick = currentTick - cc.velocityActivationTicks;
        final LinkedList<SimpleEntry> dueVer = data.getVerticalVelocityTracker()
                .listDueEntriesInOrder(currentTick, invalidateBeforeTick);
        if (dueVer.isEmpty()) {
            return Collections.emptyList();
        }
        final SimpleAxisVelocity verVel = data.getVerticalVelocityTracker();
        final PairAxisVelocity horVel = data.getHorizontalVelocityTracker();
        final PendingApplication fullFold = foldDueEntries(verVel, horVel, dueVer);
        final List<PendingApplication> alternatives = new LinkedList<>();
        if (data.velocityDeferredOnClient) {
            final LinkedList<SimpleEntry> priorAdditive = new LinkedList<>();
            boolean hasSetOnMoveTick = false;
            for (final SimpleEntry entry : dueVer) {
                if (entry.tick < currentTick && (entry.flags & VelocityFlags.ADDITIVE) != 0) {
                    priorAdditive.add(entry);
                }
                else if (entry.tick == currentTick && (entry.flags & VelocityFlags.ADDITIVE) == 0) {
                    hasSetOnMoveTick = true;
                }
            }
            if (!priorAdditive.isEmpty() && hasSetOnMoveTick) {
                final PendingApplication additiveOnly = foldDueEntries(verVel, horVel, priorAdditive);
                if (!sameEffectiveVelocity(additiveOnly, fullFold)) {
                    alternatives.add(additiveOnly);
                }
            }
        }
        alternatives.add(fullFold);
        return alternatives;
    }

    /**
     * Peek due velocity for this move: current tick, then next tick (packet labeled ahead of move check),
     * then previous tick (lagged application).
     */
    public static PendingApplication peekDueWithLag(final MovingData data, final int currentTick, final MovingConfig cc) {
        final List<PendingApplication> alternatives = peekDueAlternativesWithLag(data, currentTick, cc);
        return alternatives.isEmpty() ? null : alternatives.get(0);
    }

    /**
     * Lag-aware alternative folds for Pass B (additive-only before full fold when both apply).
     */
    public static List<PendingApplication> peekDueAlternativesWithLag(final MovingData data, final int currentTick,
            final MovingConfig cc) {
        List<PendingApplication> alternatives = peekDueAlternatives(data, currentTick, cc);
        if (!alternatives.isEmpty()) {
            return alternatives;
        }
        alternatives = peekDueAlternatives(data, currentTick + 1, cc);
        if (!alternatives.isEmpty()) {
            return alternatives;
        }
        if (currentTick > 0) {
            return peekDueAlternatives(data, currentTick - 1, cc);
        }
        return Collections.emptyList();
    }

    private static boolean sameEffectiveVelocity(final PendingApplication a, final PendingApplication b) {
        if (a == null || b == null) {
            return a == b;
        }
        final double avy = a.vertical != null ? a.vertical.value : 0.0;
        final double bvy = b.vertical != null ? b.vertical.value : 0.0;
        final double avx = a.horizontal != null ? a.horizontal.x : 0.0;
        final double bvx = b.horizontal != null ? b.horizontal.x : 0.0;
        final double avz = a.horizontal != null ? a.horizontal.z : 0.0;
        final double bvz = b.horizontal != null ? b.horizontal.z : 0.0;
        return a.additive == b.additive && avy == bvy && avx == bvx && avz == bvz;
    }

    public static void confirmConsume(final MovingData data, final PendingApplication pending) {
        if (pending == null || pending.isEmpty()) {
            return;
        }
        if (pending.consumeVertical != null) {
            for (final SimpleEntry entry : pending.consumeVertical) {
                data.getVerticalVelocityTracker().removeEntry(entry);
            }
        }
        if (pending.consumeHorizontal != null) {
            for (final PairEntry entry : pending.consumeHorizontal) {
                data.getHorizontalVelocityTracker().removeEntry(entry);
            }
        }
        data.velocityDeferredOnClient = false;
    }

    /**
     * Track client-side additive carry when velocity is queued but the move passed without a retry.
     */
    public static void updateClientDeferredState(final MovingData data, final boolean passBAttempted,
            final boolean consumedOnThisMove) {
        if (consumedOnThisMove || passBAttempted) {
            data.velocityDeferredOnClient = false;
        }
        else if (data.getVerticalVelocityTracker().hasQueued() || data.getHorizontalVelocityTracker().hasQueued()) {
            data.velocityDeferredOnClient = true;
        }
    }

    private static double resolveAdditiveBaseY(final double lastMoveY, final boolean fromOnGround,
            final double moveY, final double lastGravity) {
        double baseY = lastMoveY;
        if (fromOnGround && baseY == 0.0 && moveY > 0.0 && lastGravity != 0.0) {
            baseY = -lastGravity;
        }
        else if (!fromOnGround && lastGravity != 0.0) {
            // Packet yDistance is displacement; client motion before this tick's gravity is one step earlier.
            baseY -= lastGravity;
        }
        return baseY;
    }

    /**
     * Y component for horizontal {@code collide()} when vertical velocity is applied later in {@link #applyVerticalAfterTravel}.
     */
    public static double resolveVerticalForCollideSweep(final PendingApplication pending,
            final double postPhysicsY, final double lastMoveY, final boolean fromOnGround,
            final double moveY, final double lastGravity) {
        if (pending == null || pending.vertical == null) {
            return postPhysicsY;
        }
        if (!pending.additive) {
            return pending.vertical.value;
        }
        return resolveAdditiveBaseY(lastMoveY, fromOnGround, moveY, lastGravity) + pending.vertical.value;
    }

    /**
     * Apply horizontal-stage velocity (X/Z only) after entity push. Vertical Y is applied in {@link #applyVerticalAfterTravel}.
     */
    public static void applyHorizontalVelocity(final PendingApplication pending, final double[] xAllowed,
            final double[] zAllowed, final double[] yDistanceBeforeCollide) {
        if (pending == null || pending.isEmpty()) {
            return;
        }
        if (pending.horizontal == null) {
            return;
        }
        if (pending.additive) {
            xAllowed[0] += pending.horizontal.x;
            zAllowed[0] += pending.horizontal.z;
        }
        else {
            xAllowed[0] = pending.horizontal.x;
            zAllowed[0] = pending.horizontal.z;
        }
    }

    /**
     * Apply vertical velocity after gravity/friction/exiting-liquid in vDistRel.
     * SET replaces post-physics Y; additive uses last-tick motion (+ ground air carry) plus packet delta
     * (displacement tick — gravity already modeled above must not be applied again).
     */
    public static void applyVerticalAfterTravel(final PendingApplication pending, final double lastMoveY,
            final boolean fromOnGround, final double moveY, final double lastGravity,
            final double[] yAllowed, final double[] yTheoreticalDistance) {
        if (pending == null || pending.vertical == null) {
            return;
        }
        final double vy;
        if (pending.additive) {
            vy = resolveAdditiveBaseY(lastMoveY, fromOnGround, moveY, lastGravity) + pending.vertical.value;
        }
        else {
            vy = pending.vertical.value;
        }
        if (yAllowed != null) {
            yAllowed[0] = vy;
        }
        if (yTheoreticalDistance != null) {
            for (int i = 0; i < yTheoreticalDistance.length; i++) {
                yTheoreticalDistance[i] = vy;
            }
        }
    }

    private static PendingApplication foldDueInOrder(final SimpleAxisVelocity verVel, final PairAxisVelocity horVel,
            final int currentTick, final int invalidateBeforeTick) {
        final LinkedList<SimpleEntry> dueVer = verVel.listDueEntriesInOrder(currentTick, invalidateBeforeTick);
        if (dueVer.isEmpty()) {
            return null;
        }
        return foldDueEntries(verVel, horVel, dueVer);
    }

    private static PendingApplication foldDueEntries(final SimpleAxisVelocity verVel, final PairAxisVelocity horVel,
            final LinkedList<SimpleEntry> dueVer) {
        double effX = 0.0;
        double effY = 0.0;
        double effZ = 0.0;
        long mergedFlags = 0L;
        boolean setBased = false;
        int lastTick = dueVer.getLast().tick;
        final List<SimpleEntry> consumeVertical = new LinkedList<>();
        final List<PairEntry> consumeHorizontal = new LinkedList<>();

        for (final SimpleEntry ver : dueVer) {
            final PairEntry hor = horVel.findEntryByTick(ver.tick);
            final boolean incomingAdditive = (ver.flags & VelocityFlags.ADDITIVE) != 0;
            final double vx = hor != null ? hor.x : 0.0;
            final double vz = hor != null ? hor.z : 0.0;

            if (!incomingAdditive) {
                effX = vx;
                effY = ver.value;
                effZ = vz;
                mergedFlags = ver.flags;
                setBased = true;
            }
            else if (setBased) {
                effX += vx;
                effY += ver.value;
                effZ += vz;
                mergedFlags = mergeOriginFlags(mergedFlags, ver.flags);
            }
            else {
                effX += vx;
                effY += ver.value;
                effZ += vz;
                mergedFlags |= ver.flags;
            }

            consumeVertical.add(ver);
            if (hor != null && !consumeHorizontal.contains(hor)) {
                consumeHorizontal.add(hor);
            }
            lastTick = ver.tick;
        }

        if (setBased) {
            mergedFlags &= ~VelocityFlags.ADDITIVE;
        }
        else {
            mergedFlags |= VelocityFlags.ADDITIVE;
        }

        final SimpleEntry vertical = new SimpleEntry(lastTick, effY, mergedFlags, 0);
        final PairEntry horizontal = (effX != 0.0 || effZ != 0.0)
                ? new PairEntry(lastTick, effX, effZ, mergedFlags, 0) : null;
        return new PendingApplication(vertical, horizontal, !setBased, consumeVertical, consumeHorizontal);
    }

    private static long mergeOriginFlags(final long prevFlags, final long incomingFlags) {
        return prevFlags | (incomingFlags & ~VelocityFlags.ADDITIVE);
    }
}
