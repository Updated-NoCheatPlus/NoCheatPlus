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

import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;

import fr.neatmonster.nocheatplus.utilities.TickTask;

/**
 * Simple per-axis velocity (positive + negative), only accounting for queuing
 * and invalidation. Since entries just wrap values for one time use, no extra
 * ticking is done, invalidation mechanics and activation count decreasing takes
 * place in removeInvalid.
 * 
 * @author asofold
 *
 */
public class SimpleAxisVelocity {

    private static final long FILTER_SPLIT = VelocityFlags.SPLIT_ABOVE_THIRD | VelocityFlags.SPLIT_ABOVE_0_42;

    /** Flags for fast exclusion check in the end of the use(double, double) method. */
    private static final long FILTER_POST_USE = FILTER_SPLIT;

    /** Margin for accepting a demanded 0.0 amount, regardless sign. */
    private static final double marginAcceptZero = 0.005;

    /** Size of queued for which to force cleanup on add. */
    private static final double thresholdCleanup = 40;

    private final LinkedList<SimpleEntry> queued = new LinkedList<SimpleEntry>();

    /** Activation flag for tracking unused velocity. */
    private boolean unusedActive = true;
    /**
     * Sensitivity for tracking unused velocity (absolute amount, counts for
     * positive and negative).
     */
    // TODO: Ignoring 0-dist velocity allows 'moving on', though.
    private double unusedSensitivity = 0.1;
    // TODO: Visibility of trackers, concept, etc.
    public final UnusedTracker unusedTrackerPos = new UnusedTracker();
    // TODO: Might do without tracking negative velocity.
    public final UnusedTracker unusedTrackerNeg = new UnusedTracker();

    /**
     * Add to the front of the queue.
     * @param entry
     */
    public void addToFront(SimpleEntry entry) {
        queued.addFirst(entry);
    }

    /**
     * Add to the end of the queue.
     * @param entry
     */
    public void add(SimpleEntry entry) { 
        queued.add(entry);
        if (queued.size() > thresholdCleanup) {
            removeInvalid(TickTask.getTick());
        }
    }

    public boolean hasQueued() {
        return !queued.isEmpty();
    }

    /**
     * Use the next matching entry.
     * 
     * @param amount
     * @param tolerance
     *            Allow using entries with less amount (still sign-specific).
     *            Must be equal or greater than 0.0.
     * @return The first matching entry. Returns null if no entry is available.
     *         This will directly invalidate leading entries with the wrong
     *         sign.
     */
    public List<SimpleEntry> use(final double amount, final double tolerance) {
        final Iterator<SimpleEntry> it = queued.iterator();
        SimpleEntry entry = null;
        final List<SimpleEntry> result = new LinkedList<>();
        double totalAmount = 0;
        int lastTick = 0;
        boolean hasVel = false;
        while (it.hasNext()) {
            entry = it.next();
            it.remove();
            if ((entry.flags & VelocityFlags.ADDITIVE) != 0 && lastTick == entry.tick) {
                totalAmount += entry.value;
                result.add(entry);
                hasVel = true;
            } else {
                if (hasVel && Math.abs(totalAmount - amount) < tolerance) {
                    // Success
                    break;
                }
                if (unusedActive) {
                    for (SimpleEntry unused : result) {
                        addUnused(unused);
                    }
                }
                result.clear();
                totalAmount = entry.value;
                lastTick = entry.tick;
                result.add(entry);
                hasVel = true;
            }
            if (!hasVel && Math.abs(totalAmount - amount) >= tolerance) result.clear();
        }
        return result;
//        if (entry == null) {
//            // None found.
//            return null;
//        }
//        else {
//            if ((entry.flags & FILTER_POST_USE) != 0L) {
//                return processFlagsPostUse(entry, amount);
//            }
//            else {
//                return entry;
//            }
//        }
    }
    
    /**
     * Use the matching tick entry.
     * 
     * @param tick
     * @return The first matching entry. Returns null if no entry is available.
     *         This will directly invalidate leading entries with the wrong
     *         sign.
     */
    public List<SimpleEntry> use(final int tick) {
        final Iterator<SimpleEntry> it = queued.iterator();
        SimpleEntry entry = null;
        final List<SimpleEntry> result = new LinkedList<>();
        int lastTick = 0;
        boolean hasVel = false;
        while (it.hasNext()) {
            entry = it.next();
            it.remove();
            if ((entry.flags & VelocityFlags.ADDITIVE) != 0 && lastTick == entry.tick) {
                result.add(entry);
                hasVel = true;
            } else {
                if (hasVel && tick == result.get(0).tick) {
                    // Success
                    break;
                }
                if (unusedActive) {
                    for (SimpleEntry unused : result) {
                        addUnused(unused);
                    }
                }
                result.clear();
                lastTick = entry.tick;
                result.add(entry);
                hasVel = true;
            }
        }
        return result;
    }

    private SimpleEntry processFlagsPostUse(SimpleEntry entry, double amount) {
        // Check flags for splitting entries.
        if (allowsSplit(entry, amount)) {
            addToFront(new SimpleEntry(entry.tick, entry.value - amount, entry.flags, 
                    (entry.flags & VelocityFlags.SPLIT_RETAIN_ACTCOUNT) == 0 
                    ? entry.actCount : Math.max(entry.actCount, 2))
                    );
            // TODO: For performance reasons we don't return the used amount.
        }
        return entry;
    }

    private final boolean allowsSplit(final SimpleEntry entry, final double amount) {
        if ((entry.flags & FILTER_SPLIT) == 0L) {
            return false;
        }
        final double remain = entry.value - amount;
        return (entry.flags & VelocityFlags.SPLIT_ABOVE_THIRD) != 0L && remain > entry.value / 3.0
                || (entry.flags & VelocityFlags.SPLIT_ABOVE_0_42) != 0L && remain > 0.42;
    }

    /**
     * Without checking for invalidation, test if there is a matching entry with
     * same or less the activation count.
     * 
     * @param amount
     * @param maxActCount
     * @param tolerance
     * @return
     */
    public List<SimpleEntry> peek(final double amount, final int minActCount, final int maxActCount, 
            final double tolerance) {
        final List<SimpleEntry> result = new LinkedList<>();
        double totalAmount = 0;
        int lastTick = 0;
        boolean hasVel = false;
        final Iterator<SimpleEntry> it = queued.iterator();
        while (it.hasNext()) {
            final SimpleEntry entry = it.next();
            if (entry.actCount >= minActCount && entry.actCount <= maxActCount) {
                if ((entry.flags & VelocityFlags.ADDITIVE) != 0 && lastTick == entry.tick) {
                    totalAmount += entry.value;
                    result.add(entry);
                    hasVel = true;
                } else {
                    if (hasVel && Math.abs(totalAmount - amount) < tolerance) return result;
                    result.clear();
                    totalAmount = entry.value;
                    lastTick = entry.tick;
                    result.add(entry);
                    hasVel = true;
                }
            }
        }
        if (Math.abs(totalAmount - amount) >= tolerance) result.clear();
        return result;
    }
    
    /**
     * Without checking for invalidation, test if there is a matching entry with
     * same or less the activation count.
     * 
     * @param amount
     * @param maxActCount
     * @param tolerance
     * @return
     */
    public List<SimpleEntry> peek(final int tick, final int minActCount, final int maxActCount, 
            final double tolerance) {
        final List<SimpleEntry> result = new LinkedList<>();
        int lastTick = 0;
        boolean hasVel = false;
        final Iterator<SimpleEntry> it = queued.iterator();
        while (it.hasNext()) {
            final SimpleEntry entry = it.next();
            if (entry.actCount >= minActCount && entry.actCount <= maxActCount) {
                if ((entry.flags & VelocityFlags.ADDITIVE) != 0 && lastTick == entry.tick) {
                    result.add(entry);
                    hasVel = true;
                } else {
                    if (hasVel && tick == result.get(0).tick) return result;
                    result.clear();
                    lastTick = entry.tick;
                    result.add(entry);
                    hasVel = true;
                }
            }
        }
        return result;
    }

    /**
     * Check if the demanded amount can be covered by this velocity entry. Might
     * return an entry with a small value with a different sign, if amount is
     * set to 0.0. Needed also for testing stored entries.
     * 
     * @param entry
     * @param amount
     * @param tolerance
     *            Allow using entries with less amount (still sign-specific).
     *            Must be equal or greater than 0.0.
     * @return
     */
    public boolean matchesEntry(final SimpleEntry entry, final double amount, double tolerance) {
        return Math.abs(amount) <= Math.abs(entry.value) + tolerance && 
                (amount > 0.0 && entry.value > 0.0 && amount <= entry.value + tolerance 
                || amount < 0.0 && entry.value < 0.0 && entry.value - tolerance <= amount 
                || amount == 0.0 && Math.abs(entry.value) <= marginAcceptZero);
    }

    /**
     * Remove all entries that have been added before the given tick, or for
     * which the activation count has reached 0.
     * 
     * @param invalidateBeforeTick
     *            Entries with a smaller tick value get removed.
     */
    public void removeInvalid(final int invalidateBeforeTick) {
        // Note: clear invalidated here, append unused to invalidated.
        final Iterator<SimpleEntry> it = queued.iterator();
        while (it.hasNext()) {
            final SimpleEntry entry = it.next();
            entry.actCount --; // Let others optimize this.
            if (entry.actCount <= 0 || entry.tick < invalidateBeforeTick) {
                it.remove();
                // Track unused velocity.
                if (unusedActive) {
                    addUnused(entry);
                }
            }
        }
    }

    public void clear() {
        if (unusedActive && !queued.isEmpty()) {
            removeInvalid(TickTask.getTick());
        }
        queued.clear();
    }

    /**
     * Debugging.
     * 
     * @param builder
     */
    public void addQueued(final StringBuilder builder) {
        for (final SimpleEntry vel: queued) {
            builder.append(" ");
            builder.append(vel);
        }
    }

    // TODO: Might add the yDistance for a move here (and if to use it for external calls), but that needs a more complex modeling anyway!?
    public void updateBlockedState(final int tick, final boolean posBlocked, final boolean negBlocked) {
        // Store state, ignoring the activation flag.
        unusedTrackerPos.updateState(tick, posBlocked);
        unusedTrackerNeg.updateState(tick, negBlocked);
    }

    /**
     * Having checked the activation flag, call this with velocity entries, that
     * have just been invalidated.
     * 
     * @param entry
     */
    private void addUnused(final SimpleEntry entry) {
        // Global pre-conditions (sensitivity, skip entries that are supposed to be even more fake than default).
        if (Math.abs(entry.value) < unusedSensitivity || entry.initialActCount < 5) {
            return;
        }
        // Add to the tracker for the given direction.
        // TODO: Consider evaluating activation count + have a flag to ignore an entry.
        if (entry.value < 0.0) {
            // Negative value.
            unusedTrackerNeg.addValue(entry.tick, -entry.value); // Add absolute amount.
        }
        else {
            // Positive value.
            unusedTrackerPos.addValue(entry.tick, entry.value);
        }
    }

    public boolean isUnusedActive() {
        return unusedActive;
    }

    public void setUnusedActive(final boolean unusedActive) {
        this.unusedActive = unusedActive;
    }

    /**
     * Remove from start while the flag is present.
     * @param flag
     */
    public void removeLeadingQueuedVerticalVelocityByFlag(final long flag) {
        if (queued.isEmpty()) {
            return;
        }
        final Iterator<SimpleEntry> it = queued.iterator();
        while (it.hasNext()) {
            final SimpleEntry entry = it.next();
            if (entry.hasFlag(flag)) {
                it.remove();
            }
            else {
                break;
            }
        }
    }

}
