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
 *   along with this program. If not, see <http://www.gnu.org/licenses/>.
 */

package fr.neatmonster.nocheatplus.checks.moving.envelope;

import fr.neatmonster.nocheatplus.checks.moving.model.PlayerMoveData;
import fr.neatmonster.nocheatplus.utilities.location.PlayerLocation;

/**
 * Centralizes which ground-state signal is used by movement-physics
 * calculations and prediction envelopes. The collision-derived state on a
 * {@link PlayerLocation} describes what NCP detects at a location; packet (or
 * Bukkit-observed) ground metadata on a {@link PlayerMoveData} describes the
 * state associated with a particular movement segment. They can differ, so
 * using collision state alone can make the physics prediction disagree with
 * the state used for that segment by the client.
 * <p>
 * {@link fr.neatmonster.nocheatplus.checks.moving.MovingData} carries the
 * segment metadata into {@code SurvivalFly}. There it informs horizontal
 * speed estimation and the movement prediction envelope, while collision
 * results are still computed and used by other parts of the check and as a
 * fallback when segment metadata is unavailable.
 * <p>
 * A reported ground flag is not proof that the player is physically grounded:
 * it can be spoofed. This class is a resolver, not a validator or anti-spoof
 * check; it selects the state used to model movement. The reported state can
 * therefore affect the prediction, but the resulting movement is still
 * evaluated by {@code SurvivalFly}'s movement checks. Callers must not treat a
 * resolved on-ground value as authorization to accept a move.
 */
public final class PhysicsGroundResolver {
    /** {@link net.minecraft.world.level.block.SlimeBlock#stepOn} only runs while {@code |y| < 0.1}. */
    public static final double SLIME_STEP_ON_MAX_Y = 0.1;

    private PhysicsGroundResolver() {}

    /**
     * Clear the availability flags for client-reported ground and collision
     * state on a move. The stored values are left untouched and ignored while
     * their corresponding availability flags are false.
     *
     * @param move movement data to clear
     */
    public static void clearClientPacketGround(final PlayerMoveData move) {
        move.hasClientFromOnGround = false;
        move.hasClientToOnGround = false;
        move.hasClientToHorizontalCollision = false;
    }

    /**
     * Store supplied ground and horizontal collision states on a move, keeping
     * each value separate from whether it is available.
     *
     * @param move movement data to update
     * @param fromOnGround reported ground state at the move's start
     * @param hasFrom whether the start ground state is available
     * @param toOnGround reported ground state at the move's end
     * @param hasTo whether the end ground state is available
     * @param horizontalCollision reported horizontal collision at the end
     * @param hasHorizontalCollision whether the horizontal collision state is available
     */
    public static void bindClientGround(final PlayerMoveData move, final boolean fromOnGround, final boolean hasFrom,
                                        final boolean toOnGround, final boolean hasTo,
                                        final boolean horizontalCollision, final boolean hasHorizontalCollision) {
        move.hasClientFromOnGround = hasFrom;
        move.clientFromOnGround = fromOnGround;
        move.hasClientToOnGround = hasTo;
        move.clientToOnGround = toOnGround;
        move.hasClientToHorizontalCollision = hasHorizontalCollision;
        move.clientToHorizontalCollision = horizontalCollision;
    }


    /**
     * Resolve the ground state used for movement-physics speed calculations.
     * Forced off-ground takes precedence over forced on-ground, followed by
     * the segment's reported start state when available, and finally a
     * collision-derived estimate. {@code SurvivalFly} uses this when preparing
     * the horizontal speed estimate. The reported state is an input to that
     * prediction, not a verified fact about the player's position.
     *
     * @param from start location of the move
     * @param thisMove current move, including any client-reported state
     * @param lastMove preceding move used for the collision-derived fallback
     * @param forceSetOnGround whether the caller requires an on-ground result
     * @param forceSetOffGround whether the caller requires an off-ground result
     * @return the resolved ground state
     */
    public static boolean resolvePhysicsOnGround(final PlayerLocation from,
            final PlayerMoveData thisMove, final PlayerMoveData lastMove,
            final boolean forceSetOnGround, final boolean forceSetOffGround) {
        if (forceSetOffGround) {
            return false;
        }

        if (forceSetOnGround) {
            return true;
        }

        if (thisMove.hasClientFromOnGround) {
            return thisMove.clientFromOnGround;
        }

        return collisionDerivedOnGround(from, thisMove, lastMove);
    }

    /**
     * Resolve the start ground state used by {@code SurvivalFly}'s prediction
     * envelope, preferring the segment's reported state when available. This
     * is a prediction input, not a check that the report agrees with collisions.
     *
     * @param thisMove current move
     * @param collisionFromOnGround ground state derived from collision checks
     * @return supplied state when available, otherwise the collision result
     */
    public static boolean resolveEnvelopeFromOnGround(final PlayerMoveData thisMove,
            final boolean collisionFromOnGround) {
        if (thisMove.hasClientFromOnGround) {
            return thisMove.clientFromOnGround;
        }

        return collisionFromOnGround;
    }

    /**
     * Resolve the end ground state used by {@code SurvivalFly}'s prediction
     * envelope, preferring the segment's reported state when available. This
     * is a prediction input, not a check that the report agrees with collisions.
     *
     * @param thisMove current move
     * @param collisionToOnGround ground state derived from collision checks
     * @return supplied state when available, otherwise the collision result
     */
    public static boolean resolveEnvelopeToOnGround(final PlayerMoveData thisMove,
            final boolean collisionToOnGround) {
        if (thisMove.hasClientToOnGround) {
            return thisMove.clientToOnGround;
        }

        return collisionToOnGround;
    }

    /**
     * Estimate whether the player is on the ground using the current collision
     * state and recent downward movement when no client start flag is available.
     */
    private static boolean collisionDerivedOnGround(final PlayerLocation from,
            final PlayerMoveData thisMove, final PlayerMoveData lastMove) {
        return from.isOnGround()
                || lastMove.toIsValid && lastMove.yDistance <= 0.0 && lastMove.from.onGround
                || lastMove.yDistance < 0.0 && thisMove.fromLostGround;
    }
}