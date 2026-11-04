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
package fr.neatmonster.nocheatplus.checks.moving.player.order;

/**
 * One vanilla movement operation. The math lives in a {@link MotionPhase}.
 * {@link MotionSchedule} decides when the phase runs.
 * <p>
 * {@link #COLLIDE} is the sample point. The packet distance is the motion at the end of that phase.
 * Every later phase edits the delta that the next tick starts from.
 */
public enum MotionPhaseId {
    /** {@code Entity.baseTick}: {@code updateInWaterStateAndDoFluidPushing}. */
    FLUID_CURRENT,
    /** {@code LocalPlayer.aiStep}: {@code goDownInWater} when in water and sneaking. */
    DESCEND_IN_WATER,
    /** {@code LivingEntity.aiStep}: components under 0.003 are cleared. */
    NEGLIGIBLE_SPEED,
    /**
     * {@code LivingEntity.aiStep} jump section, then {@code jumpFromGround} or {@code jumpInLiquid}.
     * Known input reads the sent jump key. Older clients loop the space bar.
     */
    JUMP,
    /**
     * {@code Player.travel}: swimming pitch acceleration, before the medium branch.
     * Omitted through 1.12.2. Swimming starts in 1.13.
     */
    SWIM_PITCH,
    /**
     * Input scaled by 0.98, sneak, and item use, then {@code moveRelative}.
     * Known input uses the sent strafe and forward. Older clients loop the nine WASD states.
     */
    INPUT_ACCELERATION,
    /** {@code handleOnClimbable}, before {@code move}. */
    CLIMBABLE_CLAMP,
    /** Start of {@code Entity.move}: stuck-speed multiplier stored by an earlier inside-block phase. */
    STUCK_SPEED,
    /** {@code Player.maybeBackOffFromEdge}. */
    EDGE_BACKOFF,
    /**
     * {@code Entity.collide}, including the versioned step-up. This is the distance the server observes.
     * Step-up itself is not a schedule entry. 1.21.4 tries sorted collider heights inside this phase.
     */
    COLLIDE,
    /**
     * {@code LivingEntity.checkFallDamage} calls the water current again when the entity is not in water.
     * Present on the 1.20.6 path, inside {@code move}, before the horizontal reset.
     * 1.21.4 players skip {@code checkFallDamage} inside {@code move} because {@code Player.isControlledByClient} is true.
     */
    WATER_CURRENT_ON_FALL_CHECK,
    /** Horizontal delta on a colliding axis is set to 0. This runs after {@code checkFallDamage} in 1.20.6. */
    COLLISION_RESET,
    /** {@code Block.updateEntityAfterFallOn} / {@code updateEntityMovementAfterFallOn}. Slime bounce, mostly vertical. */
    SLIME_BOUNCE,
    /** {@code Block.stepOn}. Slime slows the horizontal delta when grounded. */
    SLIME_STEP,
    /**
     * Inside-block loop: honey slide, bubble column, and the stuck-speed value consumed by the next {@link #STUCK_SPEED}.
     * Vanilla runs this as one loop, so honey and bubble are not separately ordered.
     */
    INSIDE_BLOCKS,
    /**
     * {@code Entity.getBlockSpeedFactor}: soul sand and honey, after the inside-block loop.
     * Omitted through 1.12.2, where soul sand multiplies motion inside the block-collision loop.
     */
    BLOCK_SPEED,
    /** Climbable {@code y = 0.2} after {@code move}, before gravity and friction. Air and fluid both do this. */
    CLIMBABLE_POST_MOVE,
    /**
     * Medium integration after {@code move}. Air applies gravity then friction. Fluid applies friction, then fluid-falling.
     * Elytra replaces this phase while gliding. From 1.21.2 the three cases are separate helpers; the slot in the list stays one phase.
     */
    MEDIUM_GRAVITY_FRICTION,
    /** {@code isFree} jump out of water or lava, after that medium's friction. */
    LIQUID_EXIT,
    /** {@code LivingEntity.aiStep} entity push, after travel and after any post-travel block effects. */
    ENTITY_PUSH
}
