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
package fr.neatmonster.nocheatplus.checks.net.model;

import java.util.Iterator;
import java.util.LinkedList;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

import fr.neatmonster.nocheatplus.compat.AlmostBoolean;

/**
 * Queue outgoing teleport locations (DataPacketFlying), in order to cancel
 * other moving until the client acknowledges previous teleport locations. In
 * fact this might only queue a single location.
 * 
 * @author asofold
 *
 */
public class TeleportQueue {

    public static enum AckResolution {
        /** Waiting for ACK. */
        WAITING,
        /** Packet is ACK. */
        ACK,
        /** Not waiting for an ACK. */
        IDLE
    }

    public static class AckReference {
        public int lastOutgoingId = Integer.MIN_VALUE;
        /**
         * The maximum of confirmed ids. Once lastOutgoingId is reached, we
         * assume all teleports are done. Until then this id may get adjusted if
         * lower than lastOutgoingId. Note that the id will be circling within 0
         * to Integer.MAX_VALUE - 1, systematically increasing until back to 0,
         * thus this value can be greater than lastOutGoingId, which should be
         * treated as 'bad luck', resetting this to Integer.MIN_VALUE.
         */
        public int maxConfirmedId = Integer.MIN_VALUE;
    }

    // TODO: Consider passing a reentrant lock in the constructor (e.g. one lock per NetData).
    private final Lock lock = new ReentrantLock();

    /** Validated outgoing teleport locations, expected to be confirmed by the client. */
    private final LinkedList<CountableLocation> expectIncoming = new LinkedList<CountableLocation>();
    /** Location from a Bukkit event, which we expect an outgoing teleport for. */
    private DataLocation expectOutgoing = null;

    private long maxAge = 4000; // TODO: configurable
    private int maxQueueSize = 60; // TODO: configurable

    /**
     * Queried from the primary thread (read only), reset with outgoing
     * teleport.
     */
    private CountableLocation lastAck = null;

    private AckReference lastAckReference = new AckReference();

    /**
     * Maximum age in milliseconds, older entries expire.
     * @return
     */
    public long getMaxAge() {
        return maxAge;
    }

    /**
     * The last confirmed teleport location (read-only by primary thread),
     * resets with outgoing teleport.
     * 
     * @return
     */
    public CountableLocation getLastAck() {
        return lastAck;
    }

    /**
     * Get the reference of the last ack, not resetting, but updating with
     * incoming acks. This always is the same object, it might only by updated
     * on a successful ACK, depending on what's needed (current use is to cancel
     * packets on processAck returning WAITING).
     * 
     * @return
     */
    public AckReference getLastAckReference() {
        return lastAckReference;
    }

    /**
     * Call for Bukkit events (expect this packet to be sent).<br>
     * TODO: The method name is misleading, as this also should be called with
     * expected outgoing packet.
     * 
     * @param packetData
     */
    public void onTeleportEvent(final double x, final double y, final double z, final float yaw, final float pitch) {
        lock.lock();
        lastAck = null;
        expectOutgoing = new DataLocation(x, y, z, yaw, pitch);
        lock.unlock();
    }

    /**
     * Call for outgoing teleport packets. Lazily checks for expiration and max
     * queue size. Update teleportId (Integer.MIN_VALUE means that it isn't
     * provided).
     * 
     * @param x
     * @param y
     * @param z
     * @param yaw
     * @param pitch
     * @param teleportId
     * @return
     */
    public CountableLocation onOutgoingTeleport(final double x, final double y, final double z, 
            final float yaw, final float pitch, final int teleportId) {
        CountableLocation res = null;
        final long time = System.currentTimeMillis();
        lock.lock();
        lastAckReference.lastOutgoingId = teleportId;
        if (lastAckReference.maxConfirmedId > lastAckReference.lastOutgoingId) {
            lastAckReference.maxConfirmedId = Integer.MIN_VALUE; // Some data loss accepted here.
        }
        // Only register this location, if it matches the location from a Bukkit event.
        if (expectOutgoing != null) {
            if (expectOutgoing.isSamePosAndLook(x, y, z, yaw, pitch)) {
                // Add to queue.
                if (!expectIncoming.isEmpty()) {
                    // Lazy expiration check.
                    final Iterator<CountableLocation> it = expectIncoming.iterator();
                    while (it.hasNext()) {
                        final CountableLocation ref = it.next();
                        if (time < ref.time) {
                            // Time ran backwards. Force keep entries.
                            ref.time = time;
                        }
                        else if (time - maxAge > ref.time) {
                            it.remove();
                        } else {
                            break;
                        }
                    }
                    if (!expectIncoming.isEmpty()) {
                        final CountableLocation last = expectIncoming.getLast();
                        if (last.isSamePosAndLook(x, y, z, yaw, pitch)) {
                            last.time = time;
                            last.count ++;
                            last.teleportId = teleportId;
                            res = last;
                        }
                    }
                }
                // Add a new entry, if not merged with last.
                if (res == null) {
                    res = new CountableLocation(x, y, z, yaw, pitch, 1, time, teleportId);
                    expectIncoming.addLast(res);
                    // Don't exceed maxQueueSize.
                    if (expectIncoming.size() > maxQueueSize) {
                        expectIncoming.removeFirst();
                    }
                }
            }
            // Reset in any case.
            expectOutgoing = null;
        }
        lock.unlock();
        return res;
    }

    /**
     * Process ack on receiving a 'confirm teleport' packet.
     * 
     * @param teleportId
     * @return YES, if this teleport id is explicitly matched (not necessarily
     *         the latest one), MAYBE if it's a unique id smaller than than
     *         maxConfirmedId. NO, if it's not a valid ack - it could be some
     *         other special condition, like Integer overflow, or timeout.
     */
    public AlmostBoolean processAck(final int teleportId) {
        /*
         * Return values are subject to change, e.g.: (ACK_LATEST_POS),
         * ACK_LATEST_ID, ACK_OUTDATED_POS, ACK_OUTDATED_ID, UNKNOWN
         */
        if (teleportId == Integer.MIN_VALUE) {
            // Could consider to return MAYBE for not knowing, needs elaborating on context and use cases.
            return AlmostBoolean.NO;
        }
        lock.lock();
        if (teleportId == lastAckReference.lastOutgoingId) {
            lastAckReference.maxConfirmedId = teleportId;
            // Abort here for efficiency.
            expectIncoming.clear();
            lock.unlock();
            return AlmostBoolean.YES;
        }
        AlmostBoolean ackState = AlmostBoolean.NO;
        final Iterator<CountableLocation> it = expectIncoming.iterator();
        while (it.hasNext()) {
            final CountableLocation ref = it.next();
            // No expiration checks here.
            if (ref.teleportId == teleportId) {
                // Match an outdated id.
                // Remove all preceding older entries and this one.
                while (ref != expectIncoming.getFirst()) {
                    expectIncoming.removeFirst();
                }
                expectIncoming.removeFirst();
                // The count doesn't count anymore.
                ref.count = 0;
                ackState = AlmostBoolean.YES;
                break;
            }
        }
        // Update lastAckReference only if within the safe area.
        if (teleportId < lastAckReference.lastOutgoingId 
                && teleportId > lastAckReference.maxConfirmedId) {
            // Allow update.
            lastAckReference.maxConfirmedId = teleportId;
            if (ackState == AlmostBoolean.NO) {
                // Adjust to maybe, as long as the id is increasing within unique range.
                ackState = AlmostBoolean.MAYBE;
            }
        }
        else {
            lastAckReference.maxConfirmedId = Integer.MIN_VALUE;
        }
        lock.unlock();
        return ackState;
    }

    /**
     * Test if the move is an ACK move (or no ACK is expected), adjust internals
     * if necessary.
     * 
     * @param packetData
     * @return ACK, if the packet matches an expected ACK position. IDLE, if no
     *         ACK is expected or on timeout. CANCEL, if still waiting for an
     *         ACK.
     */
    public AckResolution processAck(final DataPacketFlying packetData) {
        // Check packet.
        if (!packetData.hasPos || !packetData.hasLook) {
            // Applies, if we don't have a teleport-confirm event.
            return AckResolution.IDLE;
        }
        // Check queue.
        final AckResolution res;

        lock.lock();
        if (expectIncoming.isEmpty()) {
            res = AckResolution.IDLE;
        } else {
            res = getAckResolution(packetData);
        }
        lock.unlock();

        return res;
    }

    /**
     * Check queue (lock is handled outside of this method). Does check for
     * expiration of entries. Must only be called under lock.
     * 
     * @param packetData
     * @return
     */
    private AckResolution getAckResolution(final DataPacketFlying packetData) {
        // Iterate from oldest to newest.
        final Iterator<CountableLocation> it = expectIncoming.iterator();
        while (it.hasNext()) {
            final CountableLocation ref = it.next();
            if (packetData.time - maxAge >= ref.time) {
                it.remove();
            }
            else if (packetData.isSamePosAndLook(ref)) {
                // Match.
                // Remove all preceding older entries.
                while (ref != expectIncoming.getFirst()) {
                    expectIncoming.removeFirst();
                }
                // Decrease count and remove the matching entry if count is down to 0.
                if (--ref.count <= 0) { // (Lots of safety margin.)
                    expectIncoming.removeFirst(); // Do not use the iterator here.
                }
                lastAck = ref;
                return AckResolution.ACK;
            }
            else {
                // Skip until match or none found.
                // TODO: Consider settings like maxSkipCount or strictly return WAITING.
                if (packetData.time < ref.time) {
                    // Time ran backwards, update to now.
                    ref.time = packetData.time;
                }
            }
        }
        // No match.
        return expectIncoming.isEmpty() ? AckResolution.IDLE : AckResolution.WAITING;
    }

    public void clear() {
        lock.lock();
        expectIncoming.clear();
        expectOutgoing = null;
        lock.unlock();
    }

}
