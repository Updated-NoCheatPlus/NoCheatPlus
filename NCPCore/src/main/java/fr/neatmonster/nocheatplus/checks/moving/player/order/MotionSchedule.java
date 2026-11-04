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

import java.util.Collections;
import java.util.List;

import fr.neatmonster.nocheatplus.compat.BridgeMisc;
import fr.neatmonster.nocheatplus.compat.versions.ClientVersion;

/**
 * Ordered client movement for one version group, plus the flags phases need so they do not re-check the protocol.
 * <p>
 * The groups match the clients under {@code final-decompile}:
 * <ul>
 * <li>{@link #THROUGH_1_12} — 1.12.2 and below ({@code remapped_client_1.12.2}). Unknown WASD, one {@code travel()}.
 *     Water and lava subtract a fixed 0.02 after friction. No swimming pitch. Soul sand is inside the client
 *     block-collision loop; the prediction still applies {@code BLOCK_SPEED} after that loop, before friction.</li>
 * <li>{@link #FROM_1_13} — 1.13 through 1.21.1 ({@code remapped_client_1.13.2}, {@code remapped_client_1.21.1}).
 *     Unknown WASD, one {@code travel()}. Fluid falling, dolphin's grace, lava gravity/4, and swimming pitch.
 *     Block speed is its own step after the inside-block loop, and both still run before friction.
 *     Jump replaces Y. Slow falling is sampled before {@code move()}.
 *     Depth Strider stays divided by 3 until 1.21; that constant is not another schedule.</li>
 * <li>{@link #FROM_1_21_2} — 1.21.2 and above ({@code remapped_client_1.21.2} through {@code 1.21.11}).
 *     Sent strafe, forward, jump, shift, and sprint. {@code travel()} is split into air, fluid, and elytra.
 *     Slime step, honey, bubble, and the stuck-speed store run after friction ({@code applyEffectsFromBlocks}).
 *     Jump keeps {@code max(jumpPower, currentY)}. Slow falling is sampled after {@code move()}.</li>
 * </ul>
 * {@link fr.neatmonster.nocheatplus.checks.moving.player.SurvivalFly} and
 * {@link fr.neatmonster.nocheatplus.checks.moving.player.HiddenMotionReconstructor} both walk {@link #forVersion}.
 * Horizontal and vertical stay separate passes. Each pass runs carried phases first, then this tick through {@link MotionPhaseId#COLLIDE}.
 */
public final class MotionSchedule {

    /**
     * Where slow falling reads {@code deltaY <= 0}. Through 1.21.1 that read is before {@code move()}.
     * From 1.21.2 it is {@code getEffectiveGravity()}, after {@code move()}. 1.12.2 has no slow falling.
     */
    public enum GravitySample {
        BEFORE_MOVE,
        AFTER_MOVE
    }

    /**
     * {@code jumpFromGround} vertical write. Through 1.21.1, Y is replaced.
     * From 1.21.2, Y becomes {@code max(jumpPower, currentY)}.
     */
    public enum JumpVertical {
        REPLACE,
        KEEP_IF_HIGHER
    }

    /**
     * Vertical medium after the in-fluid friction. 1.12.2 subtracts 0.02. From 1.13 the fluid-falling function
     * replaces that, and lava adds {@code -gravity / 4} on top of its own friction.
     */
    public enum LiquidModel {
        /** Water and lava: friction, then subtract 0.02. Lava friction is 0.5 on every axis. */
        FIXED_GRAVITY,
        /** Fluid falling. Shallow lava uses 0.8 on Y, then {@code -gravity / 4}. */
        FLUID_FALLING
    }

    /** Last protocol in the pre-aquatic group. */
    public static final ClientVersion THROUGH_1_12_UNTIL = ClientVersion.V_1_12_2;

    /** First protocol that sends player input and splits {@code travel()}. Same cut as {@link BridgeMisc#isWASDImpulseKnown}. */
    public static final ClientVersion FROM_1_21_2_SINCE = ClientVersion.V_1_21_2;

    private static final List<MotionPhaseId> ORDER_THROUGH_1_12 = List.of(
            MotionPhaseId.FLUID_CURRENT,
            MotionPhaseId.NEGLIGIBLE_SPEED,
            MotionPhaseId.JUMP,
            MotionPhaseId.INPUT_ACCELERATION,
            MotionPhaseId.CLIMBABLE_CLAMP,
            MotionPhaseId.STUCK_SPEED,
            MotionPhaseId.EDGE_BACKOFF,
            MotionPhaseId.COLLIDE,
            MotionPhaseId.WATER_CURRENT_ON_FALL_CHECK,
            MotionPhaseId.COLLISION_RESET,
            MotionPhaseId.SLIME_BOUNCE,
            MotionPhaseId.SLIME_STEP,
            MotionPhaseId.INSIDE_BLOCKS,
            MotionPhaseId.BLOCK_SPEED,
            MotionPhaseId.CLIMBABLE_POST_MOVE,
            MotionPhaseId.MEDIUM_GRAVITY_FRICTION,
            MotionPhaseId.LIQUID_EXIT,
            MotionPhaseId.ENTITY_PUSH
    );

    private static final List<MotionPhaseId> ORDER_FROM_1_13 = List.of(
            MotionPhaseId.FLUID_CURRENT,
            MotionPhaseId.DESCEND_IN_WATER,
            MotionPhaseId.NEGLIGIBLE_SPEED,
            MotionPhaseId.JUMP,
            MotionPhaseId.SWIM_PITCH,
            MotionPhaseId.INPUT_ACCELERATION,
            MotionPhaseId.CLIMBABLE_CLAMP,
            MotionPhaseId.STUCK_SPEED,
            MotionPhaseId.EDGE_BACKOFF,
            MotionPhaseId.COLLIDE,
            MotionPhaseId.WATER_CURRENT_ON_FALL_CHECK,
            MotionPhaseId.COLLISION_RESET,
            MotionPhaseId.SLIME_BOUNCE,
            MotionPhaseId.SLIME_STEP,
            MotionPhaseId.INSIDE_BLOCKS,
            MotionPhaseId.BLOCK_SPEED,
            MotionPhaseId.CLIMBABLE_POST_MOVE,
            MotionPhaseId.MEDIUM_GRAVITY_FRICTION,
            MotionPhaseId.LIQUID_EXIT,
            MotionPhaseId.ENTITY_PUSH
    );

    private static final List<MotionPhaseId> ORDER_FROM_1_21_2 = List.of(
            MotionPhaseId.FLUID_CURRENT,
            MotionPhaseId.DESCEND_IN_WATER,
            MotionPhaseId.NEGLIGIBLE_SPEED,
            MotionPhaseId.JUMP,
            MotionPhaseId.SWIM_PITCH,
            MotionPhaseId.INPUT_ACCELERATION,
            MotionPhaseId.CLIMBABLE_CLAMP,
            MotionPhaseId.STUCK_SPEED,
            MotionPhaseId.EDGE_BACKOFF,
            MotionPhaseId.COLLIDE,
            MotionPhaseId.COLLISION_RESET,
            MotionPhaseId.SLIME_BOUNCE,
            MotionPhaseId.BLOCK_SPEED,
            MotionPhaseId.CLIMBABLE_POST_MOVE,
            MotionPhaseId.MEDIUM_GRAVITY_FRICTION,
            MotionPhaseId.LIQUID_EXIT,
            MotionPhaseId.SLIME_STEP,
            MotionPhaseId.INSIDE_BLOCKS,
            MotionPhaseId.ENTITY_PUSH
    );

    /** 1.12.2 and below. Fixed liquid gravity, no swimming, effects before friction. */
    public static final MotionSchedule THROUGH_1_12 = new MotionSchedule(
            "1.12.2-and-below",
            false,
            false,
            false,
            false,
            GravitySample.BEFORE_MOVE,
            JumpVertical.REPLACE,
            LiquidModel.FIXED_GRAVITY,
            ORDER_THROUGH_1_12
    );

    /**
     * 1.13 through 1.21.1. Fluid falling and swimming. Effects still before friction.
     * Confirmed on 1.13.2 and 1.21.1.
     */
    public static final MotionSchedule FROM_1_13 = new MotionSchedule(
            "1.13-to-1.21.1",
            false,
            false,
            false,
            true,
            GravitySample.BEFORE_MOVE,
            JumpVertical.REPLACE,
            LiquidModel.FLUID_FALLING,
            ORDER_FROM_1_13
    );

    /**
     * 1.21.2 and above. Known input, split {@code travel()}, block effects after friction.
     * Confirmed on 1.21.2, 1.21.3, and 1.21.5, and it matches 1.21.4.
     */
    public static final MotionSchedule FROM_1_21_2 = new MotionSchedule(
            "1.21.2-and-above",
            true,
            true,
            true,
            true,
            GravitySample.AFTER_MOVE,
            JumpVertical.KEEP_IF_HIGHER,
            LiquidModel.FLUID_FALLING,
            ORDER_FROM_1_21_2
    );

    private final String name;
    private final boolean inputKnown;
    private final boolean travelSplit;
    private final boolean blockEffectsAfterFriction;
    private final boolean swimming;
    private final GravitySample gravitySample;
    private final JumpVertical jumpVertical;
    private final LiquidModel liquidModel;
    private final List<MotionPhaseId> phases;

    private MotionSchedule(final String name, final boolean inputKnown, final boolean travelSplit,
            final boolean blockEffectsAfterFriction, final boolean swimming,
            final GravitySample gravitySample, final JumpVertical jumpVertical, final LiquidModel liquidModel,
            final List<MotionPhaseId> phases) {
        this.name = name;
        this.inputKnown = inputKnown;
        this.travelSplit = travelSplit;
        this.blockEffectsAfterFriction = blockEffectsAfterFriction;
        this.swimming = swimming;
        this.gravitySample = gravitySample;
        this.jumpVertical = jumpVertical;
        this.liquidModel = liquidModel;
        this.phases = Collections.unmodifiableList(phases);
    }

    /**
     * Schedule for this client protocol.
     * Unknown and older-than-known versions use {@link #THROUGH_1_12}. Newer-than-known versions use {@link #FROM_1_21_2}.
     * <p>
     * {@link #isInputKnown()} is the client half of {@link BridgeMisc#isWASDImpulseKnown(org.bukkit.entity.Player)}.
     * A server without the input getter still cannot read WASD, so the call site keeps that check.
     */
    public static MotionSchedule forVersion(final ClientVersion version) {
        if (version == null || version.isAtMost(THROUGH_1_12_UNTIL)) {
            return THROUGH_1_12;
        }
        if (version.isAtLeast(FROM_1_21_2_SINCE)) {
            return FROM_1_21_2;
        }
        return FROM_1_13;
    }

    public String name() {
        return name;
    }

    /**
     * True when this client sends strafe, forward, jump, shift, and sprint (1.21.2+).
     * {@link MotionPhaseId#INPUT_ACCELERATION} and {@link MotionPhaseId#JUMP} either read that input or loop candidates.
     */
    public boolean isInputKnown() {
        return inputKnown;
    }

    /** True when {@code travel()} is split into air, fluid, and elytra helpers (1.21.2+). */
    public boolean isTravelSplit() {
        return travelSplit;
    }

    /**
     * True when slime step, honey, bubble, and the stuck-speed store run after friction (1.21.2+).
     */
    public boolean areBlockEffectsAfterFriction() {
        return blockEffectsAfterFriction;
    }

    /** True when {@code Player.travel} applies the swimming pitch before the medium branch (1.13+). */
    public boolean hasSwimming() {
        return swimming;
    }

    public GravitySample gravitySample() {
        return gravitySample;
    }

    public JumpVertical jumpVertical() {
        return jumpVertical;
    }

    public LiquidModel liquidModel() {
        return liquidModel;
    }

    public List<MotionPhaseId> phases() {
        return phases;
    }

    /** Phases after this index edit next-tick motion. {@link MotionPhaseId#COLLIDE} itself is the sample. */
    public boolean carriesIntoNextTick(final MotionPhaseId id) {
        final int collide = phases.indexOf(MotionPhaseId.COLLIDE);
        final int index = phases.indexOf(id);
        return collide >= 0 && index > collide;
    }

    public int indexOf(final MotionPhaseId id) {
        return phases.indexOf(id);
    }
}
