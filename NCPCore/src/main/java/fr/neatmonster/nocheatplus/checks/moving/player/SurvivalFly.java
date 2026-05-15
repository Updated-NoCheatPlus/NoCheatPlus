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
package fr.neatmonster.nocheatplus.checks.moving.player;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Locale;

import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.util.Vector;

import fr.neatmonster.nocheatplus.NCPAPIProvider;
import fr.neatmonster.nocheatplus.actions.ParameterName;
import fr.neatmonster.nocheatplus.checks.Check;
import fr.neatmonster.nocheatplus.checks.CheckType;
import fr.neatmonster.nocheatplus.checks.ViolationData;
import fr.neatmonster.nocheatplus.checks.combined.CombinedData;
import fr.neatmonster.nocheatplus.checks.combined.Improbable;
import fr.neatmonster.nocheatplus.checks.moving.MovingConfig;
import fr.neatmonster.nocheatplus.checks.moving.MovingData;
import fr.neatmonster.nocheatplus.checks.moving.envelope.PhysicsEnvelope;
import fr.neatmonster.nocheatplus.checks.moving.envelope.workaround.LostGround;
import fr.neatmonster.nocheatplus.checks.moving.envelope.workaround.MagicWorkarounds;
import fr.neatmonster.nocheatplus.checks.moving.model.PlayerKeyboardInput;
import fr.neatmonster.nocheatplus.checks.moving.model.PlayerKeyboardInput.ForwardDirection;
import fr.neatmonster.nocheatplus.checks.moving.model.PlayerKeyboardInput.StrafeDirection;
import fr.neatmonster.nocheatplus.checks.moving.model.LiftOffEnvelope;
import fr.neatmonster.nocheatplus.checks.moving.model.PlayerMoveData;
import fr.neatmonster.nocheatplus.checks.moving.velocity.PairEntry;
import fr.neatmonster.nocheatplus.checks.net.NetData;
import fr.neatmonster.nocheatplus.checks.workaround.WRPT;
import fr.neatmonster.nocheatplus.compat.AlmostBoolean;
import fr.neatmonster.nocheatplus.compat.Bridge1_13;
import fr.neatmonster.nocheatplus.compat.Bridge1_9;
import fr.neatmonster.nocheatplus.compat.BridgeMisc;
import fr.neatmonster.nocheatplus.compat.SchedulerHelper;
import fr.neatmonster.nocheatplus.compat.bukkit.BridgeMaterial;
import fr.neatmonster.nocheatplus.compat.blocks.changetracker.BlockChangeTracker;
import fr.neatmonster.nocheatplus.compat.blocks.changetracker.BlockChangeTracker.Direction;
import fr.neatmonster.nocheatplus.compat.versions.ClientVersion;
import fr.neatmonster.nocheatplus.components.modifier.IAttributeAccess;
import fr.neatmonster.nocheatplus.components.registry.event.IGenericInstanceHandle;
import fr.neatmonster.nocheatplus.logging.Streams;
import fr.neatmonster.nocheatplus.players.DataManager;
import fr.neatmonster.nocheatplus.players.IPlayerData;
import fr.neatmonster.nocheatplus.utilities.CheckUtils;
import fr.neatmonster.nocheatplus.utilities.StringUtil;
import fr.neatmonster.nocheatplus.utilities.collision.CollisionUtil;
import fr.neatmonster.nocheatplus.utilities.collision.supportingblock.SupportingBlockUtils;
import fr.neatmonster.nocheatplus.utilities.location.PlayerLocation;
import fr.neatmonster.nocheatplus.utilities.map.BlockFlags;
import fr.neatmonster.nocheatplus.utilities.map.BlockProperties;
import fr.neatmonster.nocheatplus.utilities.map.MaterialUtil;
import fr.neatmonster.nocheatplus.utilities.math.MathUtil;
import fr.neatmonster.nocheatplus.utilities.math.TrigUtil;
import fr.neatmonster.nocheatplus.utilities.moving.Magic;
import fr.neatmonster.nocheatplus.utilities.moving.MovingUtil;

/**
 * The counterpart to the CreativeFly check, designed for ordinary gameplay (Survival/Adventure)
 */
@SuppressWarnings({"UnstableApiUsage", "StatementWithEmptyBody"})
public class SurvivalFly extends Check {

    // TODO: Unification of vertical and horizontal motion. Also to reduce the number of RichEntityLocation#collide() calls.

    /** To join some tags with moving check violations. */
    private final ArrayList<String> tags = new ArrayList<>(15);

    /*
     * Bedrock/Geyser clients can disagree with the Java movement model around
     * combat knockback and half-block geometry. These values are only used by
     * Bedrock-gated helper methods so they do not widen Java client movement.
     */
    private static final double BEDROCK_HORIZONTAL_PREDICTION_EPSILON = 0.08D;
    private static final double BEDROCK_GROUNDED_COMBAT_HORIZONTAL_OVER_GRACE = 0.75D;
    private static final double BEDROCK_GROUNDED_COMBAT_MOVE_GRACE = 0.75D;
    private static final double BEDROCK_GROUNDED_COMBAT_VERTICAL_MOVE_GRACE = 0.45D;
    private static final double BEDROCK_GROUNDED_COMBAT_VERTICAL_OVER_GRACE = 0.45D;
    private static final double BEDROCK_GROUNDED_COMBAT_VERTICAL_VELOCITY_GRACE = 0.45D;
    private static final double BEDROCK_GROUNDED_COMBAT_VERTICAL_LAUNCH_HORIZONTAL_RESIDUAL = 0.05D;
    private static final double BEDROCK_GROUND_VERTICAL_QUANTUM = 0.0625D;
    private static final double BEDROCK_GROUND_VERTICAL_QUANTUM_EPSILON = 0.006D;
    private static final double BEDROCK_PACKET_VERTICAL_PRECISION = 0.01D;
    private static final double BEDROCK_AIR_GRAVITY_RESET_EPSILON = 0.015D;
    private static final double BEDROCK_HALF_STEP_VERTICAL_MOVE = 0.50D;
    private static final double BEDROCK_HALF_STEP_VERTICAL_EPSILON = 0.015D;
    private static final double BEDROCK_STEP_VERTICAL_UNDERSHOOT_MOVE_GRACE = 0.05D;
    private static final double BEDROCK_STEP_VERTICAL_MODEL_GRACE = 0.02D;
    private static final double BEDROCK_STEP_VERTICAL_UNDERSHOOT_MIN_MODEL = 0.30D;
    /*
     * Server-applied velocity can arrive one movement packet apart from the
     * player's position update. These graces let legitimate boosts/knockback
     * explain a small horizontal miss before SurvivalFly adds VL.
     */
    private static final double GROUNDED_VERTICAL_VELOCITY_HORIZONTAL_OVER_GRACE = 0.35D;
    private static final double GROUNDED_VERTICAL_VELOCITY_MOVE_GRACE = 0.80D;
    private static final double GROUNDED_VERTICAL_VELOCITY_MOVE_Y_GRACE = 0.45D;
    private static final double SERVER_VERTICAL_VELOCITY_HORIZONTAL_OVER_GRACE = 0.50D;
    private static final double SERVER_VERTICAL_VELOCITY_HORIZONTAL_MOVE_GRACE = 0.65D;
    private static final double SERVER_VERTICAL_VELOCITY_ASCEND_GRACE = 1.20D;
    private static final double GROUNDED_ITEM_RESYNC_HORIZONTAL_OVER_GRACE = 0.12D;
    private static final double GROUNDED_ITEM_RESYNC_MOVE_GRACE = 0.25D;
    /*
     * Lanterns, trapdoors, carpets, and layered snow have thin support/collision
     * shapes. The old full-block model can see these as air and repeatedly set
     * players back.
     */
    private static final double THIN_SUPPORT_HORIZONTAL_OVER_GRACE = 0.38D;
    private static final double THIN_SUPPORT_HORIZONTAL_MOVE_GRACE = 0.80D;
    private static final double THIN_SUPPORT_VERTICAL_OVER_GRACE = 0.62D;
    private static final double THIN_SUPPORT_VERTICAL_MOVE_GRACE = 0.60D;
    private static final double PARTIAL_SUPPORT_STEP_HEIGHT_MODEL = 0.60D;
    private static final double PARTIAL_SUPPORT_HORIZONTAL_MODEL_EPSILON = 0.08D;
    private static final double PARTIAL_SUPPORT_VERTICAL_MODEL_EPSILON = 0.10D;
    private static final double PARTIAL_SUPPORT_VERTICAL_CLAMP_UNIT = 0.0625D;
    private static final double PARTIAL_SUPPORT_VERTICAL_CLAMP_EPSILON = 0.025D;
    private static final double PARTIAL_SUPPORT_VERTICAL_CLAMP_MAX_DESCEND = 0.3125D;
    private static final double SNOW_SUPPORT_LAYER_HEIGHT = 0.125D;
    private static final double SNOW_SUPPORT_MAX_COLLISION_HEIGHT = 0.875D;
    private static final double NEWER_CLIENT_HORIZONTAL_OVER_GRACE = 0.04D;
    private static final double NEWER_CLIENT_HORIZONTAL_MOVE_GRACE = 0.34D;
    private static final double GROUNDED_MICRO_OVER_GRACE = 0.12D;
    private static final double GROUNDED_MICRO_MOVE_GRACE = 0.70D;
    private static final double GROUNDED_JUMP_HORIZONTAL_OVER_GRACE = 0.05D;
    private static final double GROUNDED_JUMP_HORIZONTAL_MOVE_GRACE = 0.75D;
    private static final double GROUNDED_JUMP_VERTICAL_MOVE_GRACE = 0.46D;
    private static final double GROUNDED_STEP_HORIZONTAL_OVER_GRACE = 0.05D;
    private static final double GROUNDED_STEP_HORIZONTAL_MOVE_GRACE = 0.75D;
    private static final double GROUNDED_STEP_VERTICAL_MOVE_GRACE = 0.52D;
    private static final double GROUNDED_SETBACK_HORIZONTAL_GRACE = 0.20D;
    private static final double GROUNDED_SETBACK_MOVE_GRACE = 0.32D;
    private static final double GROUNDED_MICRO_HORIZONTAL_GRACE = 0.01D;
    private static final double WATER_HORIZONTAL_MODEL_EPSILON = 0.06D;
    private static final double WATER_QUEUED_VELOCITY_HORIZONTAL_RESIDUAL = 0.11D;
    private static final double WATER_CURRENT_VELOCITY_HORIZONTAL_RESIDUAL = 0.12D;
    private static final double WATER_CURRENT_VELOCITY_HORIZONTAL_CAP = 0.45D;
    private static final double WATER_IMPLICIT_SWIM_HORIZONTAL_CAP = 0.16D;
    private static final double WATER_IMPLICIT_SWIM_HORIZONTAL_RESIDUAL = 0.10D;
    private static final double WATER_VERTICAL_MODEL_EPSILON = 0.06D;
    private static final double WATER_SURFACE_ASCEND_MODEL = 0.34D;
    private static final double WATER_SURFACE_EXIT_ASCEND_MODEL = 0.30D;
    private static final double WATER_EXIT_DESCEND_MODEL = Magic.DEFAULT_GRAVITY * Magic.FRICTION_MEDIUM_AIR;
    private static final double WATER_DOLPHIN_HORIZONTAL_OVER_GRACE = 0.02D;
    private static final double WATER_DOLPHIN_HORIZONTAL_MOVE_GRACE = 0.45D;
    private static final double LAVA_VERTICAL_OVER_GRACE = 0.90D;
    private static final double LAVA_ASCEND_MOVE_GRACE = 0.95D;
    private static final double LAVA_TAG_VERTICAL_OVER_GRACE = 0.10D;
    private static final double LAVA_HORIZONTAL_OVER_GRACE = 0.08D;
    private static final double LAVA_HORIZONTAL_MOVE_GRACE = 0.35D;
    private static final double LAVA_VELOCITY_HORIZONTAL_RESIDUAL = 0.14D;
    private static final double LAVA_VELOCITY_HORIZONTAL_PERPENDICULAR_RESIDUAL = 0.08D;
    private static final double LAVA_CURRENT_VERTICAL_RESIDUAL = 0.10D;
    private static final double CLIMBABLE_HORIZONTAL_OVER_GRACE = 0.03D;
    private static final double CLIMBABLE_HORIZONTAL_MOVE_GRACE = 0.13D;
    private static final double CLIMBABLE_ASCEND_GRACE = 0.22D;
    private static final double CLIMBABLE_DESCEND_GRACE = 0.30D;
    private static final double CLIMBABLE_DESCEND_OVER_GRACE = 0.30D;
    private static final double CLIMBABLE_VERTICAL_PRECISION_GRACE = 0.005D;
    private static final double CLIMBABLE_JUMP_CARRY_HORIZONTAL_EPSILON = 0.04D;
    private static final double CLIMBABLE_ENTRY_HORIZONTAL_CARRY = 0.24D;
    private static final double CLIMBABLE_DIAGONAL_AXIS_CAP = Magic.CLIMBABLE_MAX_SPEED * Math.sqrt(2.0D);
    private static final double LEVITATION_STALL_VERTICAL_GRACE = 0.12D;
    private static final double LEVITATION_STALL_OVER_GRACE = 0.12D;
    private static final double LEVITATION_HORIZONTAL_OVER_GRACE = 0.08D;
    private static final double GLIDING_VERTICAL_PRECISION_GRACE = 0.005D;
    private static final double GLIDING_HORIZONTAL_PRECISION_GRACE = 0.06D;
    private static final double GLIDING_VELOCITY_VERTICAL_OVER_GRACE = 0.70D;
    private static final double GLIDING_VELOCITY_HORIZONTAL_OVER_GRACE = 0.04D;
    private static final double GLIDING_VELOCITY_MIN_HORIZONTAL_MOVE = 0.75D;
    private static final double GLIDING_VELOCITY_MAX_VERTICAL_MOVE = 0.75D;
    private static final double GLIDING_STALL_VERTICAL_OVER_GRACE = 0.18D;
    private static final double GLIDING_STALL_HORIZONTAL_OVER_GRACE = 0.04D;
    private static final double GLIDING_STALL_VERTICAL_MOVE_GRACE = 0.18D;
    private static final double GLIDING_STALL_HORIZONTAL_MOVE_GRACE = 0.20D;
    private static final double GLIDING_CURRENT_VELOCITY_VERTICAL_OVER_GRACE = 1.00D;
    private static final double GLIDING_CURRENT_VELOCITY_VERTICAL_MATCH_GRACE = 0.20D;
    private static final double GLIDING_CURRENT_VELOCITY_BETTER_MODEL_GRACE = 0.08D;
    private static final double GLIDING_CURRENT_VELOCITY_VERTICAL_MODEL_DIFF_GRACE = 0.55D;
    private static final double GLIDING_CURRENT_VELOCITY_HORIZONTAL_MODEL_DIFF_GRACE = 0.55D;
    private static final double GLIDING_CURRENT_VELOCITY_HORIZONTAL_AMOUNT_GRACE = 0.35D;
    private static final double GLIDING_CURRENT_VELOCITY_HORIZONTAL_PERPENDICULAR_GRACE = 0.12D;
    private static final double GLIDING_CURRENT_VELOCITY_TURN_YAW_EXTRA = 6.0D;
    private static final double GLIDING_CURRENT_VELOCITY_HORIZONTAL_MOVE_GRACE = 0.45D;
    private static final double GLIDING_NO_FIREWORK_ASCENT_RESIDUAL = 0.06D;
    private static final double GLIDING_NO_FIREWORK_ASCENT_SPEED_START = 0.45D;
    private static final double GLIDING_NO_FIREWORK_ASCENT_SPEED_FACTOR = 0.18D;
    private static final double GLIDING_NO_FIREWORK_ASCENT_SPEED_CAP = 0.42D;
    private static final double GLIDING_NO_FIREWORK_ASCENT_DIVE_FACTOR = 0.90D;
    private static final double GLIDING_NO_FIREWORK_ASCENT_TRADE_FACTOR = 0.55D;
    private static final double GLIDING_NO_FIREWORK_CURRENT_VELOCITY_MIN_HORIZONTAL = 0.45D;
    private static final double GLIDING_NO_FIREWORK_DESCENT_CREDIT_FACTOR = 0.70D;
    private static final double GLIDING_NO_FIREWORK_DESCENT_CREDIT_CAP = 8.0D;
    private static final double GLIDING_NO_FIREWORK_DESCENT_CREDIT_DECAY = 0.006D;
    private static final double GLIDING_NO_FIREWORK_DESCENT_CREDIT_MIN_H = 0.45D;
    private static final double GLIDING_NO_FIREWORK_DESCENT_CREDIT_FULL_H = 1.40D;
    private static final double GLIDING_NO_FIREWORK_DESCENT_CREDIT_TICK_CAP = 0.34D;
    private static final double GLIDING_NO_FIREWORK_SETBACK_MIN_DROP = 1.25D;
    private static final double GLIDING_NO_FIREWORK_SETBACK_MAX_DROP = 5.0D;
    private static final double GLIDING_NO_FIREWORK_SETBACK_DEFICIT_FACTOR = 2.0D;
    private static final double GLIDING_NO_FIREWORK_SETBACK_DEBT_FACTOR = 0.20D;
    private static final double GLIDING_NO_FIREWORK_SETBACK_EXCESS_FACTOR = 4.0D;
    private static final double GLIDING_NO_FIREWORK_START_SETBACK_MIN_GAIN = 0.40D;
    private static final double GLIDING_NO_FIREWORK_START_SETBACK_MAX_HORIZONTAL = 80.0D;
    private static final double GLIDING_NO_FIREWORK_DOWNWARD_VELOCITY_Y = -0.075D;
    private static final double GLIDING_NO_FIREWORK_DOWNWARD_VELOCITY_MAX_DEBT = 0.20D;
    private static final double GLIDING_NO_FIREWORK_ASCENT_DEBT_LIMIT = 0.32D;
    private static final int GLIDING_NO_FIREWORK_ASCENT_TICK_LIMIT = 5;
    private static final double GLIDING_NO_FIREWORK_DESCENT_DROP_DEFICIT = 0.52D;
    private static final double GLIDING_NO_FIREWORK_FLAT_DROP_DEFICIT = 0.25D;
    private static final double GLIDING_NO_FIREWORK_FLAT_MAX_ACTUAL_DROP = 0.02D;
    private static final int GLIDING_NO_FIREWORK_DESCENT_BUDGET_MIN_TICKS = 45;
    private static final double GLIDING_STEEP_DIVE_ENERGY_EPSILON = 0.04D;
    private static final double GLIDING_VERTICAL_BELOW_MODEL_GRACE = 1.10D;
    private static final double GLIDING_VERTICAL_BELOW_MODEL_FIREWORK_GRACE = 1.50D;
    private static final double GLIDING_VERTICAL_SMALL_MISS_GRACE = 0.25D;
    private static final double GLIDING_VERTICAL_SMALL_MISS_MOVE_GRACE = 1.85D;
    // Model cleanup: firework residuals are empirical boundaries around vanilla boost vectors, not post-failure grace windows.
    private static final double GLIDING_FIREWORK_PACKET_ORDER_HORIZONTAL_RESIDUAL = 0.20D;
    private static final double GLIDING_FIREWORK_PACKET_ORDER_VERTICAL_RESIDUAL = 0.24D;
    private static final double GLIDING_FIREWORK_SKIPPED_BOOST_HORIZONTAL_RESIDUAL = 0.08D;
    private static final double GLIDING_FIREWORK_SKIPPED_BOOST_VERTICAL_RESIDUAL = 0.08D;
    private static final double GLIDING_FIREWORK_TURN_YAW_EXTRA = 10.0D;
    private static final double GLIDING_FIREWORK_PERPENDICULAR_RESIDUAL = 0.16D;
    private static final double GLIDING_FIREWORK_GROUND_PROXIMITY_VERTICAL_RESIDUAL = 0.08D;
    private static final double GLIDING_FIREWORK_GRAVITY_VERTICAL_MATCH = 0.015D;
    private static final double GLIDING_FIREWORK_VERTICAL_PRECISION = 0.03D;
    private static final double GLIDING_FIREWORK_PARTIAL_VERTICAL_RESIDUAL = 0.18D;
    /*
     * False-positive tuning: wearing an elytra is not the same as actively
     * gliding. These handle launch and velocity transition packets before Bukkit
     * reports normal gliding state.
     */
    private static final double ELYTRA_EQUIPPED_VERTICAL_VELOCITY_MOVE_GRACE = 1.10D;
    private static final double ELYTRA_EQUIPPED_VERTICAL_VELOCITY_Y_GRACE = 1.05D;
    private static final double ELYTRA_EQUIPPED_VELOCITY_HORIZONTAL_RESIDUAL = 0.20D;
    private static final double ELYTRA_EQUIPPED_VELOCITY_VERTICAL_RESIDUAL = 0.28D;
    private static final double ELYTRA_EQUIPPED_VELOCITY_FOLLOWUP_RESIDUAL = 0.30D;
    private static final double ELYTRA_EQUIPPED_FIREWORK_HORIZONTAL_RESIDUAL = 0.22D;
    private static final double ELYTRA_EQUIPPED_FIREWORK_VERTICAL_RESIDUAL = 0.26D;
    private static final double ELYTRA_EQUIPPED_QUEUED_VELOCITY_MOVE_GRACE = 1.80D;
    private static final double ELYTRA_EQUIPPED_QUEUED_VELOCITY_Y_GRACE = 1.05D;
    private static final double ELYTRA_EQUIPPED_GROUND_HORIZONTAL_OVER_GRACE = 0.25D;
    private static final double ELYTRA_EQUIPPED_GROUND_MOVE_GRACE = 0.80D;
    private static final double ELYTRA_EQUIPPED_DESCEND_VERTICAL_OVER_GRACE = 0.10D;
    private static final double ELYTRA_EQUIPPED_DESCEND_HORIZONTAL_OVER_GRACE = 0.08D;
    private static final double ELYTRA_EQUIPPED_DESCEND_MOVE_GRACE = 0.60D;
    private static final double ELYTRA_EQUIPPED_STALE_ASCEND_VERTICAL_OVER_GRACE = 0.45D;
    private static final double ELYTRA_EQUIPPED_STALE_ASCEND_HORIZONTAL_OVER_GRACE = 0.10D;
    private static final double ELYTRA_EQUIPPED_STALE_ASCEND_MOVE_GRACE = 0.65D;
    private static final double ELYTRA_EQUIPPED_GROUND_STEP_VERTICAL_GRACE = 0.52D;
    private static final double ELYTRA_EQUIPPED_GLIDE_EXIT_VERTICAL_MATCH = 0.02D;
    private static final double ELYTRA_LIFTOFF_VERTICAL_OVER_GRACE = 0.095D;
    private static final double ELYTRA_LIFTOFF_MAX_ASCEND = 0.44D;
    private static final double ELYTRA_LIFTOFF_LAST_Y_GRACE = 0.02D;
    private static final double ELYTRA_LIFTOFF_HORIZONTAL_OVER_GRACE = 0.04D;
    private static final double ELYTRA_LIFTOFF_HORIZONTAL_MOVE_GRACE = 0.60D;
    private static final double ELYTRA_GEOMETRY_STALL_VERTICAL_OVER_GRACE = LiftOffEnvelope.NORMAL.getJumpGain(0.0) + Magic.PREDICTION_EPSILON;
    private static final double ELYTRA_GEOMETRY_STALL_MAX_VERTICAL_MOVE = 0.05D;
    private static final double ELYTRA_GEOMETRY_STALL_LAST_ASCEND = 0.40D;
    private static final double ELYTRA_GEOMETRY_STALL_HORIZONTAL_OVER_GRACE = 0.15D;
    private static final double ELYTRA_GEOMETRY_STALL_HORIZONTAL_MOVE_GRACE = 0.30D;
    private static final double ELYTRA_LANDING_INERTIA_HORIZONTAL_RESIDUAL = 0.18D;
    private static final double ELYTRA_LANDING_INERTIA_PERPENDICULAR_RESIDUAL = 0.16D;
    private static final double ELYTRA_LANDING_INERTIA_GENERAL_SUPPORT_CARRY = 0.42D;
    private static final double ELYTRA_LANDING_INERTIA_MIN_LAST_HORIZONTAL = 0.75D;
    private static final double ELYTRA_LANDING_INERTIA_MAX_VERTICAL_MOVE = 0.65D;
    private static final double ELYTRA_LANDING_INERTIA_MAX_HORIZONTAL_MOVE = 1.95D;
    private static final double SETBACK_GRAVITY_VERTICAL_GRACE = 0.18D;
    private static final double SETBACK_GRAVITY_OVER_GRACE = 0.18D;
    private static final double SETBACK_GRAVITY_SETBACK_Y_GRACE = 0.04D;
    private static final double CURRENT_SERVER_VELOCITY_VERTICAL_OVER_GRACE = 2.00D;
    private static final double CURRENT_SERVER_VELOCITY_VERTICAL_MATCH_GRACE = 0.25D;
    private static final double CURRENT_SERVER_VELOCITY_HORIZONTAL_OVER_GRACE = 0.05D;
    private static final double WATER_TAG_VERTICAL_OVER_GRACE = 0.16D;
    private static final double WATER_TAG_HORIZONTAL_OVER_GRACE = 0.06D;
    private static final double QUEUED_VELOCITY_HORIZONTAL_OVER_GRACE = 0.35D;
    private static final double QUEUED_VELOCITY_HORIZONTAL_MOVE_GRACE = 0.45D;
    private static final double QUEUED_VELOCITY_VERTICAL_MOVE_GRACE = 0.50D;
    private static final double QUEUED_VELOCITY_HORIZONTAL_RESIDUAL = 0.05D;
    private static final double GROUND_JUMP_TINY_HORIZONTAL_OVER_GRACE = 0.03D;
    private static final double GROUND_JUMP_TINY_HORIZONTAL_MOVE_GRACE = 0.50D;
    private static final double MODERN_VERTICAL_IMPULSE_Y_GRACE = 1.45D;
    private static final double MODERN_VERTICAL_IMPULSE_MOVE_GRACE = 1.25D;
    private static final double MODERN_VERTICAL_IMPULSE_MIN_SERVER_VELOCITY = 0.15D;
    private static final double MODERN_VERTICAL_IMPULSE_HORIZONTAL_VELOCITY_GRACE = 0.45D;
    private static final double MODERN_VERTICAL_IMPULSE_VERTICAL_RESIDUAL = 0.30D;
    private static final double ELYTRA_EQUIPPED_NEUTRAL_VERTICAL_OVER_GRACE = 0.14D;
    private static final double ELYTRA_EQUIPPED_NEUTRAL_HORIZONTAL_OVER_GRACE = 0.35D;
    private static final double ELYTRA_EQUIPPED_NEUTRAL_MOVE_GRACE = 0.45D;
    private static final double ELYTRA_EQUIPPED_SMALL_VERTICAL_OVER_GRACE = 0.18D;
    private static final double ELYTRA_EQUIPPED_SMALL_VERTICAL_MOVE_GRACE = 0.10D;
    private static final double ELYTRA_EQUIPPED_SMALL_VERTICAL_HORIZONTAL_OVER_GRACE = 0.35D;
    private static final double ELYTRA_EQUIPPED_SMALL_VERTICAL_HORIZONTAL_MOVE_GRACE = 0.45D;
    private static final double ELYTRA_EQUIPPED_LAST_INVALID_ASCEND_VERTICAL_RESIDUAL = 0.08D;
    private static final double ELYTRA_EQUIPPED_LAST_INVALID_ASCEND_MAX_MOVE = 0.24D;
    private static final double ELYTRA_EQUIPPED_GLIDE_COAST_HORIZONTAL_RESIDUAL = 0.12D;
    private static final double ELYTRA_EQUIPPED_GLIDE_COAST_PERPENDICULAR_RESIDUAL = 0.12D;
    private static final double ELYTRA_EQUIPPED_GLIDE_COAST_VERTICAL_RESIDUAL = 0.09D;
    private static final double ELYTRA_EQUIPPED_GLIDE_COAST_MIN_LAST_HORIZONTAL = 0.30D;
    private static final double COLLISION_VERTICAL_CORRECTION_OVER_GRACE = 0.18D;
    private static final double COLLISION_VERTICAL_CORRECTION_HORIZONTAL_OVER_GRACE = 0.35D;
    private static final double COLLISION_VERTICAL_CORRECTION_MOVE_GRACE = 0.35D;
    private static final double COLLISION_VERTICAL_CORRECTION_Y_MOVE_GRACE = 0.20D;
    private static final double COLLISION_HORIZONTAL_SLIDE_INPUT_CARRY = 0.08D;
    private static final double COLLISION_HORIZONTAL_SLIDE_RESIDUAL = 0.02D;
    private static final double COLLISION_HORIZONTAL_SLIDE_MOVE_CAP = 0.40D;
    private static final double COLLISION_VERTICAL_TRUNCATION_MAX = 0.12D;
    private static final double COLLISION_VERTICAL_TRUNCATION_HORIZONTAL_OVER = 0.08D;
    private static final double LAST_INVALID_RESYNC_VERTICAL_MATCH = 0.035D;
    private static final double LAST_INVALID_RESYNC_HORIZONTAL_INPUT_CARRY = 0.12D;
    private static final double LAST_INVALID_RESYNC_HORIZONTAL_RESIDUAL = 0.08D;
    private static final double LAST_INVALID_RESYNC_MAX_HORIZONTAL_MOVE = 0.45D;
    private static final double LAST_INVALID_RESYNC_MAX_VERTICAL_MOVE = 0.35D;
    private static final double LAST_INVALID_STANDSTILL_HORIZONTAL_MOVE = 0.04D;
    private static final double LAST_INVALID_STANDSTILL_VERTICAL_OVER = 0.09D;
    private static final double LAST_INVALID_GROUND_INPUT_HORIZONTAL_RESIDUAL = 0.04D;
    private static final double LAST_INVALID_GROUND_INPUT_HORIZONTAL_CAP = 0.35D;
    private static final double LAST_INVALID_VELOCITY_HANDOFF_VERTICAL_MATCH = 0.04D;
    private static final double LAST_INVALID_VELOCITY_HANDOFF_MAX_VERTICAL_MOVE = 0.65D;
    private static final double LAST_INVALID_AIR_STALL_VERTICAL_MOVE = 0.01D;
    private static final double LAST_INVALID_AIR_STALL_SERVER_Y = 0.10D;
    private static final double LAST_INVALID_AIR_STALL_VERTICAL_OVER = 0.09D;
    private static final double MODERN_HALF_STEP_HORIZONTAL_INPUT_CARRY = 0.16D;
    private static final double MODERN_HALF_STEP_HORIZONTAL_RESIDUAL = 0.035D;
    private static final double MODERN_HALF_STEP_HORIZONTAL_CAP = 0.55D;
    private static final double MODERN_HALF_STEP_PLATEAU_EPSILON = 0.015D;
    private static final double LAST_INVALID_JUMP_CONTINUATION_HORIZONTAL_INPUT_CARRY = 0.30D;
    private static final double LAST_INVALID_JUMP_CONTINUATION_HORIZONTAL_RESIDUAL = 0.04D;
    private static final double LAST_INVALID_JUMP_CONTINUATION_HORIZONTAL_CAP = 0.38D;
    private static final double LAST_INVALID_JUMP_CONTINUATION_VERTICAL_EPSILON = 0.015D;
    private static final double LAST_INVALID_FIRST_JUMP_VERTICAL_EPSILON = 0.02D;
    private static final double LAST_INVALID_LOW_JUMP_CONTINUATION_VERTICAL_MIN = 0.18D;
    private static final double LAST_INVALID_LOW_JUMP_CONTINUATION_VERTICAL_MAX = 0.28D;
    private static final double LAST_INVALID_LOW_JUMP_CONTINUATION_VERTICAL_EPSILON = 0.025D;
    private static final double ELYTRA_EQUIPPED_VELOCITY_HANDOFF_HORIZONTAL_INPUT_CARRY = 0.30D;
    private static final double ELYTRA_EQUIPPED_VELOCITY_HANDOFF_HORIZONTAL_RESIDUAL = 0.04D;
    private static final double ELYTRA_EQUIPPED_VELOCITY_HANDOFF_VERTICAL_MATCH = 0.04D;
    private static final double AIR_INERTIA_HORIZONTAL_EPSILON = 0.02D;
    private static final double AIR_INERTIA_VERTICAL_EPSILON = 0.015D;
    private static final double AIR_INERTIA_MAX_HORIZONTAL_MOVE = 0.36D;
    private static final double GROUND_VELOCITY_CARRY_HORIZONTAL_RESIDUAL = 0.025D;
    private static final double GROUND_VELOCITY_CARRY_MAX_MOVE = 0.55D;
    private static final double GROUND_LANDING_CARRY_INPUT_MULTIPLIER = 1.45D;
    private static final double GROUND_LANDING_CARRY_HORIZONTAL_RESIDUAL = 0.025D;
    private static final double GROUND_LANDING_CARRY_MAX_MOVE = 0.62D;
    private static final double GROUND_PASSABLE_PLANT_INPUT_CARRY = 0.45D;
    private static final double GROUND_PASSABLE_PLANT_HORIZONTAL_RESIDUAL = 0.025D;
    private static final double GROUND_PASSABLE_PLANT_MAX_MOVE = 0.48D;
    private static final double JUMP_CARRY_HORIZONTAL_RESIDUAL = 0.04D;
    private static final double JUMP_CARRY_MAX_MOVE = 0.55D;
    private static final double LOW_JUMP_CARRY_VERTICAL_MIN = 0.18D;
    private static final double LOW_JUMP_CARRY_VERTICAL_MAX = 0.28D;
    private static final double LOW_JUMP_CARRY_VERTICAL_EPSILON = 0.025D;
    private static final double AIR_CURRENT_VELOCITY_HORIZONTAL_RESIDUAL = 0.025D;
    private static final double AIR_CURRENT_VELOCITY_VERTICAL_MATCH = 0.004D;
    private static final double AIR_CURRENT_VELOCITY_MAX_HORIZONTAL_MOVE = 0.34D;
    private static final double QUEUED_VELOCITY_VERTICAL_PACKET_ORDER_OVER = 0.12D;
    private static final double QUEUED_VELOCITY_VERTICAL_PACKET_ORDER_MOVE = 0.05D;
    private static final double QUEUED_VELOCITY_VERTICAL_INERTIA_RESIDUAL = 0.025D;
    private static final double QUEUED_VELOCITY_VERTICAL_INERTIA_MIN_H = 0.75D;
    private static final long SERVER_POSITION_JUMP_SURVIVALFLY_GRACE_MS = 2500L;
    private static final double SERVER_POSITION_JUMP_HORIZONTAL_OVER_GRACE = 0.55D;
    private static final double SERVER_POSITION_JUMP_HORIZONTAL_MOVE_GRACE = 0.65D;
    private static final double SERVER_POSITION_JUMP_AIR_HORIZONTAL_RESIDUAL = 0.035D;
    private static final double SERVER_POSITION_JUMP_AIR_VERTICAL_RESIDUAL = 0.04D;
    private static final double SERVER_POSITION_JUMP_AIR_VERTICAL_MOVE_MODEL = 1.20D;
    private static final double PORTAL_TRANSITION_HORIZONTAL_OVER_GRACE = 0.12D;
    private static final double PORTAL_TRANSITION_HORIZONTAL_MOVE_GRACE = 0.16D;
    private static final double PORTAL_TRANSITION_VERTICAL_OVER_GRACE = 1.10D;
    private static final double PORTAL_TRANSITION_VERTICAL_MOVE_GRACE = 1.25D;
    private static final double CURRENT_VELOCITY_PERPENDICULAR_GRACE = 0.05D;
    private static final double CURRENT_VELOCITY_AMOUNT_GRACE = 0.04D;

    /*
     * Documentation note: several constants still end in GRACE because they
     * started as false-positive tolerances. In the model paths below, use them
     * as bounded residuals inside a selected movement state, not as a separate
     * "normal model failed, forgive it anyway" branch.
     *
     * This commit moves the most common false-positive fixes away from loose
     * one-axis exemptions and into named movement model branches. Each branch
     * still has tight bounds, but horizontal and vertical decisions now share
     * the same state label in logs and diagnostics.
     */
    private enum MovementModelBranch {
        NONE("none"),
        ELYTRA_GLIDING("elytra_gliding"),
        ELYTRA_FIREWORK("elytra_firework"),
        ELYTRA_EQUIPPED_FIREWORK("elytra_equipped_firework"),
        ELYTRA_EQUIPPED_TRANSITION("elytra_equipped_transition"),
        PORTAL_TRANSITION("portal_transition"),
        SERVER_POSITION_JUMP_RESYNC("server_position_jump_resync"),
        WATER("water"),
        WATER_DOLPHIN("water_dolphin"),
        LAVA("lava"),
        CLIMBABLE("climbable"),
        PARTIAL_SUPPORT("partial_support"),
        BEDROCK_GROUNDED_COMBAT("bedrock_grounded_combat"),
        BEDROCK_STEP("bedrock_step"),
        BEDROCK_PACKET_PRECISION("bedrock_packet_precision"),
        MODERN_CLIENT_GROUND("modern_client_ground"),
        MODERN_VERTICAL_IMPULSE("modern_vertical_impulse"),
        QUEUED_VELOCITY("queued_velocity"),
        GROUNDED_VERTICAL_VELOCITY("grounded_vertical_velocity"),
        SERVER_VERTICAL_VELOCITY("server_vertical_velocity"),
        COLLISION("collision"),
        LAST_INVALID_RESYNC("last_invalid_resync"),
        MODERN_HALF_STEP("modern_half_step"),
        GROUNDED_RECOVERY("grounded_recovery"),
        SETBACK_GRAVITY("setback_gravity"),
        GROUND_JUMP_TINY("ground_jump_tiny"),
        GROUNDED_ITEM_RESYNC("grounded_item_resync"),
        CURRENT_SERVER_VELOCITY("current_server_velocity"),
        LEVITATION("levitation"),
        ITEM_RESYNC("item_resync"),
        AIR_INERTIA("air_inertia"),
        GROUND_VELOCITY_CARRY("ground_velocity_carry"),
        GROUND_LANDING_CARRY("ground_landing_carry"),
        GROUND_PASSABLE_PLANT("ground_passable_plant"),
        JUMP_CARRY("jump_carry"),
        AIR_CURRENT_VELOCITY("air_current_velocity");

        private final String tag;

        private MovementModelBranch(final String tag) {
            this.tag = tag;
        }
    }

    /*
     * Climbable model: keep separate tags for vines and scaffolding even though
     * their current limits match. Logs stay readable if one surface needs
     * different Bedrock or modern-client tuning later.
     */
    private enum ClimbableSurfaceModel {
        VINES("vines", CLIMBABLE_HORIZONTAL_MOVE_GRACE, CLIMBABLE_HORIZONTAL_OVER_GRACE,
                CLIMBABLE_ASCEND_GRACE, CLIMBABLE_DESCEND_GRACE, CLIMBABLE_DESCEND_OVER_GRACE,
                CLIMBABLE_VERTICAL_PRECISION_GRACE),
        SCAFFOLDING("scaffolding", CLIMBABLE_HORIZONTAL_MOVE_GRACE, CLIMBABLE_HORIZONTAL_OVER_GRACE,
                CLIMBABLE_ASCEND_GRACE, CLIMBABLE_DESCEND_GRACE, CLIMBABLE_DESCEND_OVER_GRACE,
                CLIMBABLE_VERTICAL_PRECISION_GRACE),
        GENERIC("climbable", CLIMBABLE_HORIZONTAL_MOVE_GRACE, CLIMBABLE_HORIZONTAL_OVER_GRACE,
                CLIMBABLE_ASCEND_GRACE, CLIMBABLE_DESCEND_GRACE, CLIMBABLE_DESCEND_OVER_GRACE,
                CLIMBABLE_VERTICAL_PRECISION_GRACE);

        private final String tag;
        private final double horizontalMoveCap;
        private final double horizontalResidual;
        private final double ascendLimit;
        private final double descendLimit;
        private final double descendOverLimit;
        private final double verticalPrecision;

        private ClimbableSurfaceModel(final String tag, final double horizontalMoveCap,
                                      final double horizontalResidual, final double ascendLimit,
                                      final double descendLimit, final double descendOverLimit,
                                      final double verticalPrecision) {
            this.tag = tag;
            this.horizontalMoveCap = horizontalMoveCap;
            this.horizontalResidual = horizontalResidual;
            this.ascendLimit = ascendLimit;
            this.descendLimit = descendLimit;
            this.descendOverLimit = descendOverLimit;
            this.verticalPrecision = verticalPrecision;
        }
    }
    
    private final ArrayList<String> justUsedWorkarounds = new ArrayList<>();
    
    private final BlockChangeTracker blockChangeTracker;
    
    private final IGenericInstanceHandle<IAttributeAccess> attributeAccess = NCPAPIProvider.getNoCheatPlusAPI().getGenericInstanceHandle(IAttributeAccess.class);
    
    /**
     * The vertical component of liquid push (water/lava).
     * This is computed in hdistrel and then used by vidstrel to avoid having to call the getLiquidPush() method twice.
     * When computing the horizontal liquid push, the way Mojang coded the function to get the liquid's flowing force
     * to calculate horizontal speed with, also affects the vertical speed (See comment in {@link fr.neatmonster.nocheatplus.utilities.location.RichEntityLocation#getFlowForceVector(int, int, int, long)}.
     */
    private double verticalLiquidPushComponent = 0.0;
    
    
    public SurvivalFly() {
        super(CheckType.MOVING_SURVIVALFLY);
        blockChangeTracker = NCPAPIProvider.getNoCheatPlusAPI().getBlockChangeTracker();
    }
    
    
    /**
     * Checks a player
     *
     * @param multiMoveCount
     *            0: Ordinary, 1/2/(...): first/second/(...) part of a split move.
     * @param isNormalOrPacketSplitMove
     *           Flag to indicate if the packet-based split move mechanic is used instead of the Bukkit-based one (or the move was not split)
     *
     * @return The Location where to set back the player to. Null in case of no violation.
     */
    public Location check(final Player player, final PlayerLocation from, final PlayerLocation to,
                          final int multiMoveCount, final MovingData data, final MovingConfig cc,
                          final IPlayerData pData, final int tick, final long now,
                          final boolean useBlockChangeTracker, final boolean isNormalOrPacketSplitMove) {
        /*
          TODO: Ideally, all this data should really be set outside SurvivalFly (in the MovingListener), since they can be useful
          for other checks / stuff.
         */
        tags.clear();
        justUsedWorkarounds.clear();
        // Shortcuts:
        final boolean debug = pData.isDebugActive(type);
        final PlayerMoveData thisMove = data.playerMoves.getCurrentMove();
        final PlayerMoveData lastMove = data.playerMoves.getFirstPastMove();
        final CombinedData cData = pData.getGenericInstance(CombinedData.class);
        /*
         * Actually forgot what this does. But quick guess might be idle move (0,0,0) on count 1 and riptide on count 2.
         * TODO: Is this supposed to be here? (Move in MovingListener at the beginning of checkPayerMove?)
         */
        if (lastMove.tridentRelease.decideOptimistically() && multiMoveCount == 2 && lastMove.yDistance == 0.0) {
            thisMove.tridentRelease = AlmostBoolean.YES;
            lastMove.tridentRelease = AlmostBoolean.NO;
        }
        /* Regular and past fromOnGround */
        final boolean fromOnGround = from.isOnGround() || useBlockChangeTracker && from.isOnGroundOpportune(cc.yOnGround, 0L, blockChangeTracker, data.blockChangeRef, tick);
        /* Regular and past toOnGround */
        final boolean toOnGround = to.isOnGround() || useBlockChangeTracker && to.isOnGroundOpportune(cc.yOnGround, 0L, blockChangeTracker, data.blockChangeRef, tick);  // TODO: Work in the past ground stuff differently (thisMove, touchedGround?, from/to ...)
        /* Moving onto/into everything that isn't in air (liquids, stuck-speed, ground, ALL) */
        final boolean resetTo = toOnGround || to.isResetCond();
        /* Moving off from anything that is not air (liquids, stuck-speed, ground, ALL). */
        final boolean resetFrom = fromOnGround || from.isResetCond();
        // Run lostground checks.
        LostGround.runLostGroundChecks(player, from, to, thisMove.hDistance, thisMove.yDistance, lastMove, data, cc, useBlockChangeTracker ? blockChangeTracker : null, tags);

        // Set workarounds for the registry
        data.ws.setJustUsedIds(justUsedWorkarounds);

        // Recover from data removal (somewhat random insertion point).
        if (data.liftOffEnvelope == LiftOffEnvelope.UNKNOWN) {
            data.adjustLiftOffEnvelope(from);
        }

        // Adjust block properties (friction, block speed etc...)
        data.adjustMediumProperties(player, from);
        
        // Ground somehow appeared out of thin air (block place).
        // This move is registered as "coming from ground" despite the player not having moved onto ground with the previous move, which was fully in air.
        if (thisMove.touchedGround) {
            if (multiMoveCount == 0 && thisMove.from.onGround && PhysicsEnvelope.inAir(lastMove) 
                && TrigUtil.isSamePosAndLook(thisMove.from, lastMove.to)) {
                data.setSetBack(from);
                if (debug) {
                    debug(player, "Ground appeared due to a block-place: adjust set-back location.");
                }
            }
        }

        // Decrease bunnyhop delay counter
        if (data.jumpDelay > 0) {
            data.jumpDelay--;
        }
      


        /////////////////////////////////////
        // Horizontal move                ///
        /////////////////////////////////////
        double hAllowedDistance, hDistanceAboveLimit, hFreedom;
        double[] resGlide = processGliding(from, to, pData, data, player, isNormalOrPacketSplitMove, fromOnGround, toOnGround, debug);
        // Set the allowed distance and determine the distance above limit
        double[] hRes = Bridge1_9.isGliding(player) ? resGlide : prepareSpeedEstimation(from, to, pData, player, data, thisMove, lastMove, fromOnGround, toOnGround, debug, isNormalOrPacketSplitMove, false, false);
        hAllowedDistance = hRes[0];
        hDistanceAboveLimit = hRes[1];
        // Beyond limit? Check if there may have been a reason for this (and try to re-estimate if needed)
        if (hDistanceAboveLimit > 0.0) {
            double[] res = hDistAfterFailure(player, from, to, hAllowedDistance, hDistanceAboveLimit, thisMove, lastMove, debug, data, cc, pData, tick, useBlockChangeTracker, fromOnGround, toOnGround, isNormalOrPacketSplitMove);
            hAllowedDistance = res[0];
            hDistanceAboveLimit = res[1];
            hFreedom = res[2];
        }
        else {
            // Clear active velocity if the distance is within limit (clearly not needed. :))
            //data.clearActiveHorVel();
            hFreedom = 0.0;
        }
        /////////////////////////////////////
        // Vertical move                  ///
        /////////////////////////////////////
        // Order of checking in EntityLiving.java water -> lava -> gliding -> air
        // TODO: Clean-up this left-over bit of the old implementation (respect MC's order)
        double yAllowedDistance, yDistanceAboveLimit;
        if (Bridge1_9.isGliding(player)) {
            yAllowedDistance = resGlide[2];
            yDistanceAboveLimit = resGlide[3];
        }
        else {
            final double[] res = vDistRel(player, from, fromOnGround, resetFrom, to, toOnGround, resetTo, thisMove.yDistance, isNormalOrPacketSplitMove, lastMove, data, pData, false, debug, useBlockChangeTracker );
            yAllowedDistance = res[0];
            yDistanceAboveLimit = res[1];
        }
        // Model pass: select the movement state first, then compare H/Y against that state-derived envelope.
        final double[] modelRes = applyExplicitMovementModel(player, pData, data, from, to, thisMove, lastMove,
                hDistanceAboveLimit, yDistanceAboveLimit, resetFrom, resetTo);
        hDistanceAboveLimit = modelRes[0];
        yDistanceAboveLimit = modelRes[1];
        hAllowedDistance = thisMove.hAllowedDistance;
        yAllowedDistance = thisMove.yAllowedDistance;
        // Legacy fallback pass: keep after the model pass so diagnostics show which model did or did not cover it.
        hDistanceAboveLimit = applyEnvironmentalHorizontalLeniency(player, pData, data, from, to, thisMove, lastMove, hDistanceAboveLimit);
        hAllowedDistance = thisMove.hAllowedDistance;
        yDistanceAboveLimit = applyEnvironmentalVerticalLeniency(player, pData, data, from, to, thisMove, lastMove,
                yDistanceAboveLimit, hDistanceAboveLimit, resetFrom, resetTo);
        yAllowedDistance = thisMove.yAllowedDistance;
        final double dataOnlyResult = (Math.max(hDistanceAboveLimit, 0.0) + Math.max(yDistanceAboveLimit, 0.0)) * 100D;
        if (dataOnlyResult > 0.0 && isElytraModelDataOnly(player, cc)) {
            /*
             * Elytra model collection is data-only when enforcement is disabled.
             * Keep the model passive without dumping every glide miss to console;
             * violation details below still include elytra diagnostics when the
             * check is actively enforced.
             */
            addTag(SurvivalFlyTags.ELYTRA_MODEL_DATA_ONLY);
            hDistanceAboveLimit = 0.0D;
            yDistanceAboveLimit = 0.0D;
        }


        ////////////////////////////
        // Debug output.          //
        ////////////////////////////
        final int tagsLength;
        if (debug) {
            outputDebug(player, to, from, data, thisMove.hDistance, hAllowedDistance, hFreedom, thisMove.yDistance, yAllowedDistance, fromOnGround, resetFrom, toOnGround, resetTo, thisMove);
            tagsLength = tags.size();
            data.ws.setJustUsedIds(null);
        }
        else tagsLength = 0; // JIT vs. IDE.


        //////////////////////////////////////
        // Handle violations               ///
        //////////////////////////////////////
        final boolean inAir = PhysicsEnvelope.inAir(thisMove);
        final double result = (Math.max(hDistanceAboveLimit, 0.0) + Math.max(yDistanceAboveLimit, 0.0)) * 100D;
        if (result > 0.0) {
            final Location vLoc = handleViolation(result, player, from, to, data, cc);
            if (CheckUtils.shouldLogDebugToConsole()) {
                try {
                    logConsoleDetails(result, player, from, to, vLoc, data, pData, thisMove, lastMove, hAllowedDistance,
                            hDistanceAboveLimit, yAllowedDistance, yDistanceAboveLimit, fromOnGround, resetFrom,
                            toOnGround, resetTo, tick, now, multiMoveCount, isNormalOrPacketSplitMove);
                }
                catch (Throwable t) {
                    player.getServer().getLogger().info("[NCP][SurvivalFly][detail] diagnostic logging failed for player="
                            + player.getName() + " reason=" + t.getClass().getSimpleName());
                }
            }
            if (inAir) {
                data.sfVLInAir = true;
            }
            // Request a new to-location
            if (vLoc != null) {
                return vLoc;
            }
        }
        else {
            if (canRelaxVL(data, cc, inAir, lastMove, thisMove)) {
                // Relax VL.
                data.survivalFlyVL *= 0.95;
            }
        }


        //////////////////////////////////////////////////////////////////////////////////////////////
        //  Set data for normal move or violation without cancel (cancel would have returned above) //
        //////////////////////////////////////////////////////////////////////////////////////////////
        // Adjust lift off envelope to medium
        if (thisMove.to.inPowderSnow) {
            data.liftOffEnvelope = LiftOffEnvelope.LIMIT_POWDER_SNOW;
        }
        else if (thisMove.to.inWeb) {
            data.liftOffEnvelope = LiftOffEnvelope.LIMIT_WEBS;
        }
        else if (thisMove.to.inBerryBush) {
            data.liftOffEnvelope = LiftOffEnvelope.LIMIT_SWEET_BERRY;
        }
        else if (thisMove.to.onHoneyBlock) {
            data.liftOffEnvelope = LiftOffEnvelope.LIMIT_HONEY_BLOCK;
        }
        else if (resetTo) {
            data.liftOffEnvelope = LiftOffEnvelope.NORMAL;
        }
        else if (thisMove.from.inPowderSnow) {
            data.liftOffEnvelope = LiftOffEnvelope.LIMIT_POWDER_SNOW;
        }
        else if (thisMove.from.inWeb) {
            data.liftOffEnvelope = LiftOffEnvelope.LIMIT_WEBS;
        }
        else if (thisMove.from.inBerryBush) {
            data.liftOffEnvelope = LiftOffEnvelope.LIMIT_SWEET_BERRY;
        }
        else if (thisMove.from.onHoneyBlock) {
            data.liftOffEnvelope = LiftOffEnvelope.LIMIT_HONEY_BLOCK;
        }
        else if (resetFrom || thisMove.touchedGround) {
            data.liftOffEnvelope = LiftOffEnvelope.NORMAL;
        }
        else {
            // Air, Keep medium.
        }

        // Apply reset conditions.
        if (resetTo || isBedrockLanternCollisionMove(player, pData, from, to, thisMove)) {
            // Reset data.
            data.setSetBack(to);
            data.sfJumpPhase = 0;
        }
        // The player moved from ground.
        else if (resetFrom) {
            data.setSetBack(from);
            data.sfJumpPhase = 1; // This event is already in air.
        }
        else {
            data.sfJumpPhase ++;
            if (!Double.isInfinite(Bridge1_9.getLevitationAmplifier(player))
                || Bridge1_13.isRiptiding(player)
                || Bridge1_9.isGliding(player)) {
                data.setSetBack(to);
            }
        }

        // Adjust not in-air stuff.
        if (!inAir) {
            data.ws.resetConditions(WRPT.G_RESET_NOTINAIR);
            data.sfVLInAir = false;
        }

        // Update unused velocity tracking.
        // TODO: Hide and seek with API.
        // TODO: Pull down tick / timing data (perhaps add an API object for millis + source + tick + sequence count (+ source of sequence count).
        if (debug) {
            // TODO: Only update, if velocity is queued at all.
            data.getVerticalVelocityTracker().updateBlockedState(tick,
                    // Assume blocked with being in web/water, despite not entirely correct.
                    thisMove.headObstructed || thisMove.from.resetCond,
                    // (Similar here.)
                    thisMove.touchedGround || thisMove.to.resetCond);
            // TODO: TEST: Check unused velocity here too. (Should have more efficient process, pre-conditions for checking.)
            UnusedVelocity.checkUnusedVelocity(player, type, data, cc);
        }

        // Adjust various speed/friction factors (both h/v).
        data.lastFrictionVertical = data.nextFrictionVertical;
        data.lastFrictionHorizontal = data.nextFrictionHorizontal;
        data.lastStuckInBlockVertical = data.nextStuckInBlockVertical;
        data.lastStuckInBlockHorizontal = data.nextStuckInBlockHorizontal;
        data.lastBlockSpeedMultiplier = data.nextBlockSpeedMultiplier;
        data.lastInertia = data.nextInertia;
        data.lastLevitationLevel = !Double.isInfinite(Bridge1_9.getLevitationAmplifier(player)) ? Bridge1_9.getLevitationAmplifier(player) + 1 : 0.0;
        data.lastGravity = data.nextGravity;
        data.lastCollidingEntitiesLocations = CollisionUtil.getCollidingEntitiesLocations(player);
        cData.wasSprinting = pData.isSprinting();
        cData.wasPressingShift = pData.isShiftKeyPressed();
        cData.wasSlowFalling = !Double.isInfinite(Bridge1_13.getSlowfallingAmplifier(player));
        cData.wasLevitating = !Double.isInfinite(Bridge1_9.getLevitationAmplifier(player));
        // Log tags added after violation handling.
        if (debug && tags.size() > tagsLength) {
            logPostViolationTags(player);
        }
        // Nothing to do, newTo (MovingListener) stays null
        return null;
    }
    
    
    /**
     * Check if the violation level may decrease.
     * 
     * @param data
     * @param cc
     * @param inAir
     * @param lastMove
     * @param thisMove
     * @return
     */
    private boolean canRelaxVL(MovingData data, MovingConfig cc, boolean inAir, PlayerMoveData lastMove, PlayerMoveData thisMove) {
        // Slowly reduce the level with each event, if violations have not recently happened.
        return data.getPlayerMoveCount() - data.sfVLMoveCount > cc.survivalFlyVLFreezeCount
                && (!cc.survivalFlyVLFreezeInAir || !inAir
                // Favor bunny-hopping slightly: clean descend.
                || !data.sfVLInAir
                && data.liftOffEnvelope == LiftOffEnvelope.NORMAL
                && lastMove.toIsValid
                && lastMove.yDistance < -Magic.GRAVITY_MIN
                && thisMove.yDistance - lastMove.yDistance < -Magic.GRAVITY_MIN);
    }
    
    
    /**
     * A check to prevent players from bed-flying.
     * To be called on PlayerBedLeaveEvent(s)
     * (This increases VL and sets tag only. Setback is done in MovingListener).
     *
     * @return If to prevent action (use the setback location of survivalfly).
     */
    public boolean checkBed(final Player player, final MovingConfig cc, final MovingData data) {
        boolean cancel = false;
        // Check if the player had been in bed at all.
        if (!data.wasInBed) {
            // Violation ...
            tags.add("bedfly");
            data.survivalFlyVL += 100D;
            Improbable.check(player, (float) 5.0, System.currentTimeMillis(), "moving.survivalfly.bedfly", DataManager.getPlayerData(player));
            final ViolationData vd = new ViolationData(this, player, data.survivalFlyVL, 100D, cc.survivalFlyActions);
            if (vd.needsParameters()) vd.setParameter(ParameterName.TAGS, StringUtil.join(tags, "+"));
            cancel = executeActions(vd).willCancel();
        }
        // Nothing detected.
        else data.wasInBed = false;
        return cancel;
    }


    /**
     * Check for push/pull by pistons, alter data appropriately (blockChangeId).
     */
    private double[] getVerticalBlockMoveResult(final double yDistance, final PlayerLocation from, final PlayerLocation to, final MovingData data) {
        /*
         * TODO: Pistons pushing horizontally allow similar/same upwards
         * (downwards?) moves (possibly all except downwards, which is hard to
         * test :p).
         */
        // TODO: Allow push up to 1.0 (or 0.65 something) even beyond block borders, IF COVERED [adapt PlayerLocation].
        // TODO: Other conditions/filters ... ?
        // Push (/pull) up.
        if (yDistance > 0.0) {
            if (yDistance <= 1.015) {
                /*
                 * (Full blocks: slightly more possible, ending up just above
                 * the block. Bounce allows other end positions.)
                 */
                // TODO: Is the air block wich the slime block is pushed onto really in? 
                if (from.matchBlockChange(blockChangeTracker, data.blockChangeRef, Direction.Y_POS, Math.min(yDistance, 1.0))) {
                    if (yDistance > 1.0) {
                        if (to.getY() - to.getBlockY() >= 0.015) {
                            // Exclude ordinary cases for this condition.
                            return null;
                        }
                    }
                    tags.add("blkmv_y_pos");
                    final double maxDistYPos = yDistance; //1.0 - (from.getY() - from.getBlockY()); // TODO: Margin ?
                    return new double[]{maxDistYPos, 0.0};
                }
            }
        }
        // Push (/pull) down.
        else if (yDistance < 0.0 && yDistance >= -1.0) {
            if (from.matchBlockChange(blockChangeTracker, data.blockChangeRef, Direction.Y_NEG, -yDistance)) {
                tags.add("blkmv_y_neg");
                final double maxDistYNeg = yDistance; // from.getY() - from.getBlockY(); // TODO: Margin ?
                return new double[]{maxDistYNeg, 0.0};
            }
        }
        // Nothing found.
        return null;
    }


    /**
     * Determine the allowed h / v distance for gliding.
     * Handled in its own method because of vertical and horizontal motion being too intertwined to separate (y-distance changes relate to h-distance changes). <br>
     * Consistency checks are done within the {@link fr.neatmonster.nocheatplus.checks.combined.CombinedListener}.<br>
     * <li> NOTE: this should be called with {@link Bridge1_9#isGliding(LivingEntity)} not {@link Bridge1_9#isGlidingWithElytra(Player)}, because the client does not check for elytra to apply the corresponding motion (EntityLiving, travel())</li>
     *
     * @return the allowed xyz distances + distances above limit.
     */
    private double[] processGliding(final PlayerLocation from, final PlayerLocation to, final IPlayerData pData, final MovingData data,
                                    final Player player, boolean isNormalOrPacketSplitMove, final boolean fromOnGround, final boolean toOnGround, final boolean debug) {
        final PlayerMoveData lastMove = data.playerMoves.getFirstPastMove();
        final PlayerMoveData thisMove = data.playerMoves.getCurrentMove();
        final CombinedData cData = pData.getGenericInstance(CombinedData.class);
        double yDistanceAboveLimit = 0.0, hDistanceAboveLimit = 0.0;
        // TODO: What with gliding + rocket boosting + riptiding + bouncing on slimes/beds while riptiding? LMAO
        // Beats me why Mojang keeps letting players perform such ridiculous moves.
        if (!Bridge1_9.isGliding(player)) {
            // No Gliding, no deal
            return new double[] {thisMove.hDistance, 0.0, thisMove.yDistance, 0.0};
        }
        addTag(data.fireworksBoostDuration > 0
                ? SurvivalFlyTags.MODE_ELYTRA_FIREWORK : SurvivalFlyTags.MODE_ELYTRA_GLIDING);
        if (data.fireworksBoostDuration > 0) {
            addTag(SurvivalFlyTags.GLIDE_FIREWORK_ACTIVE);
        }
        // Note: WASD key presses, as well as sneaking and item-use are irrelevant when gliding.
        // Initialize speed.
        thisMove.xAllowedDistance = lastMove.toIsValid ? lastMove.xDistance : 0.0;
        thisMove.yAllowedDistance = lastMove.toIsValid ? lastMove.yDistance : 0.0;
        thisMove.zAllowedDistance = lastMove.toIsValid ? lastMove.zDistance : 0.0;
        // Reset momentum if collided with something on the previous tick.
        doWallCollision(lastMove, thisMove);
        // Throttle speed if stuck in a block.
        if (TrigUtil.lengthSquared(data.lastStuckInBlockHorizontal, data.lastStuckInBlockVertical, data.lastStuckInBlockHorizontal) > 1.0E-7) {
            if (data.lastStuckInBlockVertical != 1.0) {
                thisMove.yAllowedDistance = 0.0;
            }
            if (data.lastStuckInBlockHorizontal != 1.0) {
                thisMove.xAllowedDistance = thisMove.zAllowedDistance = 0.0;
            }
        }
        // Reset speed if judged to be negligible.
        checkNegligibleMomentum(pData, thisMove);
        checkNegligibleMomentumVertical(pData, thisMove);
        // Yes, players can glide and riptide at the same time, increasing speed at a faster rate than chunks can load...
        // Surely a questionable decision on Mojang's part.
        // NOTE: For the elytra, this has to be done before applying gravity and other motion changes.
        if (thisMove.tridentRelease.decideOptimistically()) {
            thisMove.tridentRelease = AlmostBoolean.YES;
            Vector riptideVelocity = to.getRiptideVelocity(false); // Cannot glide while on ground, so no need to check for it.
            thisMove.xAllowedDistance += riptideVelocity.getX();
            thisMove.yAllowedDistance += riptideVelocity.getY();
            thisMove.zAllowedDistance += riptideVelocity.getZ();
        }
        // TODO: Reduce verbosity (at least, make it easier to look at)
        Vector viewVector = TrigUtil.getLookingDirection(to, player);
        float radianPitch = to.getPitch() * TrigUtil.toRadians;
        final ClientVersion clientVersion = getMovementClientVersion(pData);
        // Horizontal length of the look direction
        double viewVecHorizontalLength = MathUtil.dist(viewVector.getX(), viewVector.getZ());
        // Horizontal length of the movement
        double thisMoveHDistance = MathUtil.dist(thisMove.xAllowedDistance, thisMove.zAllowedDistance); // NOTE: MUST BE the ALLOWED distances.
        // Overall length of the look direction.
        double viewVectorLength = viewVector.length();
        // Mojang switched from their own cosine function to the standard Math.cos() one in 1.18.2
        double cosPitch = clientVersion.isAtMost(ClientVersion.V_1_18_2) ? TrigUtil.cos((double)radianPitch) : Math.cos((double)radianPitch);
        cosPitch = cosPitch * cosPitch * Math.min(1.0, viewVectorLength / 0.4);
        // Base gravity when gliding.
        thisMove.yAllowedDistance += (cData.wasSlowFalling && lastMove.yDistance <= 0.0 ? Magic.SLOW_FALL_GRAVITY : Magic.DEFAULT_GRAVITY) * (-1.0 + cosPitch * 0.75);
        double baseSpeed;
        if (thisMove.yAllowedDistance < 0.0 && viewVecHorizontalLength > 0.0) {
            // Slow down.
            baseSpeed = thisMove.yAllowedDistance * -0.1 * cosPitch;
            thisMove.xAllowedDistance += viewVector.getX() * baseSpeed / viewVecHorizontalLength;
            thisMove.yAllowedDistance += baseSpeed;
            thisMove.zAllowedDistance += viewVector.getZ() * baseSpeed / viewVecHorizontalLength;
        }
        if (radianPitch < 0.0 && viewVecHorizontalLength > 0.0) {
            // Looking down speeds up the player.
            baseSpeed = thisMoveHDistance * (double) (-TrigUtil.sin(radianPitch)) * 0.04;
            thisMove.xAllowedDistance += -viewVector.getX() * baseSpeed / viewVecHorizontalLength;
            thisMove.yAllowedDistance += baseSpeed * 3.2;
            thisMove.zAllowedDistance += -viewVector.getZ() * baseSpeed / viewVecHorizontalLength;
        }
        if (viewVecHorizontalLength > 0.0) {
            // Accelerate
            thisMove.xAllowedDistance += (viewVector.getX() / viewVecHorizontalLength * thisMoveHDistance - thisMove.xAllowedDistance) * 0.1;
            thisMove.zAllowedDistance += (viewVector.getZ() / viewVecHorizontalLength * thisMoveHDistance - thisMove.zAllowedDistance) * 0.1;
        }
        // Boosted with a firework: propel the player.
        if (data.fireworksBoostDuration > 0) {
            // TODO: Firework netcode is horrible (a single firework can tick twice on the same tick, skipping the subsequent one), so simply applying the increase of speed won't cut it.
            // Not even sure if we can predict this at all without some kind of hacks / workarounds.
            thisMove.xAllowedDistance += viewVector.getX() * 0.1 + (viewVector.getX() * 1.5 - thisMove.xAllowedDistance) * 0.5;
            thisMove.yAllowedDistance += viewVector.getY() * 0.1 + (viewVector.getY() * 1.5 - thisMove.yAllowedDistance) * 0.5;
            thisMove.zAllowedDistance += viewVector.getZ() * 0.1 + (viewVector.getZ() * 1.5 - thisMove.zAllowedDistance) * 0.5;
        }
        // Friction here. (TEST)
        // Note about inertia: the game assigns the radian pitch to the "f" variable, which is the variable used to apply friction at the end of the tick, _normally_.
        // However, with gliding, the game does not use the f variable at the end of the tick, but instead applies the magic value of 0.99.
        thisMove.xAllowedDistance *= 0.99;
        thisMove.yAllowedDistance *= data.lastFrictionVertical;
        thisMove.zAllowedDistance *= 0.99;
        // Stuck-speed with the updated multiplier (both at the end)
        if (TrigUtil.lengthSquared(data.nextStuckInBlockHorizontal, data.nextStuckInBlockVertical, data.nextStuckInBlockHorizontal) > 1.0E-7) {
            thisMove.xAllowedDistance *= (double) data.nextStuckInBlockHorizontal;
            thisMove.yAllowedDistance *= (double) data.nextStuckInBlockVertical;
            thisMove.zAllowedDistance *= (double) data.nextStuckInBlockHorizontal;
        }
        // Collisions last.
        Vector collisionVector = from.collide(new Vector(thisMove.xAllowedDistance, thisMove.yAllowedDistance, thisMove.zAllowedDistance), fromOnGround || thisMove.fromLostGround, from.getBoundingBox());
        thisMove.collideX = collisionVector.getX() != thisMove.xAllowedDistance;
        thisMove.collideY = collisionVector.getY() != thisMove.yAllowedDistance;
        thisMove.collideZ = collisionVector.getZ() != thisMove.zAllowedDistance;
        thisMove.collidesHorizontally = thisMove.collideX || thisMove.collideZ;
        thisMove.xAllowedDistance = collisionVector.getX();
        thisMove.yAllowedDistance = collisionVector.getY();
        thisMove.zAllowedDistance = collisionVector.getZ();

        // Can a vertical workaround apply? If so, override the prediction.
        if (MagicWorkarounds.checkPostPredictWorkaround(data, fromOnGround, toOnGround, from, to, thisMove.yAllowedDistance, player, isNormalOrPacketSplitMove)) {
            thisMove.yAllowedDistance = thisMove.yDistance;
        }
        final boolean noFireworkAscentEnergyViolation = updateNoFireworkGlidingAscentEnergy(player, data, from, thisMove, lastMove);
        
        ////////////////////////////
        /// Calculate offests     //
        ////////////////////////////
        /* Expected difference from current to allowed */
        final double offsetV = thisMove.yDistance - thisMove.yAllowedDistance;
        if (Math.abs(offsetV) < Magic.PREDICTION_EPSILON) {
            // Accuracy margin.
        }
        else if (currentGlidingVerticalVelocityMatches(player, thisMove, thisMove.yAllowedDistance, Math.abs(offsetV))) {
            thisMove.yAllowedDistance = thisMove.yDistance;
            addTag("glide_current_velocity_vertical_model");
        }
        else if (currentGlidingVerticalVelocityEnvelopeCovers(player, thisMove,
                thisMove.yAllowedDistance, Math.abs(offsetV))) {
            /*
             * Elytra current velocity model: after firework handoff/reload/packet
             * ordering, Bukkit can still expose upward glide velocity while the
             * vanilla tick prediction has lost that energy source. Accept only
             * packets inside the server velocity envelope.
             */
            thisMove.yAllowedDistance = thisMove.yDistance;
            addTag("glide_current_velocity_vertical_envelope_model");
        }
        else {
            // If velocity can be used for compensation, use it.
            if (data.getOrUseVerticalVelocity(thisMove.yDistance).isEmpty()) {
                yDistanceAboveLimit = Math.max(yDistanceAboveLimit, Math.abs(offsetV));
                addGlidingVerticalPredictionTags(offsetV);
                addTag("vdistrel");
            }
        }
        if (noFireworkAscentEnergyViolation) {
            /*
             * Elytra no-firework energy model: the model above tracks sustained
             * climb debt separately from the one-packet vanilla prediction. If
             * there is no firework, no queued vertical velocity, no riptide, and
             * no descent/speed budget to pay for the climb, this must become an
             * active setback immediately. Do not let the generic current-velocity
             * vertical branch hide a no-energy ascent; legitimate handoff velocity
             * is accepted inside updateNoFireworkGlidingAscentEnergy instead.
             */
            yDistanceAboveLimit = Math.max(yDistanceAboveLimit,
                    Math.max(GLIDING_VERTICAL_PRECISION_GRACE, data.elytraNoFireworkAscentExcess));
            addTag("glide_no_firework_ascent_energy_enforced");
            addTag("vdistrel");
        }
        final double noFireworkDescentBudgetOver = getNoFireworkGlidingDescentBudgetOver(player, data, thisMove);
        if (noFireworkDescentBudgetOver > 0.0D) {
            /*
             * Elytra no-firework descent budget model: packet-shaped hover can
             * stay under the one-packet ascent limit by repeatedly sending tiny
             * positive/flat Y. Once the accumulated glide budget says descent is
             * missing and the helper has ruled out firework/current upward velocity,
             * surface that as an active SurvivalFly model miss instead of waiting
             * only for the legacy hover tick action path.
             */
            yDistanceAboveLimit = Math.max(yDistanceAboveLimit,
                    Math.max(GLIDING_VERTICAL_PRECISION_GRACE, noFireworkDescentBudgetOver));
            addTag("glide_no_firework_descent_budget_enforced");
            addTag("vdistrel");
        }
        thisMove.hAllowedDistance = MathUtil.dist(thisMove.xAllowedDistance, thisMove.zAllowedDistance);
        final double offsetH = thisMove.hDistance - thisMove.hAllowedDistance;
        if (offsetH < Magic.PREDICTION_EPSILON) {
            // Accuracy margin.
        }
        else if (currentGlidingHorizontalVelocityCovers(player, thisMove,
                thisMove.xAllowedDistance, thisMove.zAllowedDistance,
                thisMove.xDistance - thisMove.xAllowedDistance,
                thisMove.zDistance - thisMove.zAllowedDistance)) {
            thisMove.xAllowedDistance = thisMove.xDistance;
            thisMove.zAllowedDistance = thisMove.zDistance;
            thisMove.hAllowedDistance = thisMove.hDistance;
            addTag("glide_current_velocity_horizontal_model");
        }
        else if (offsetH <= GLIDING_HORIZONTAL_PRECISION_GRACE) {
            thisMove.xAllowedDistance = thisMove.xDistance;
            thisMove.zAllowedDistance = thisMove.zDistance;
            thisMove.hAllowedDistance = thisMove.hDistance;
            addTag("glide_horizontal_precision_model");
        }
        else {
            hDistanceAboveLimit = Math.max(hDistanceAboveLimit, offsetH);
            addGlidingHorizontalPredictionTags(offsetH);
            addTag("hdistrel");
        }
        addGlidingLookAndFireworkTags(data, to, thisMove, offsetV, offsetH);
        if (debug) {
            player.sendMessage(ChatColor.RED + "[SurvivalFly] vdistrel: predict=" + StringUtil.fdec6.format(thisMove.yAllowedDistance) + ", actual=" + StringUtil.fdec6.format(thisMove.yDistance) + ", offset=" + StringUtil.fdec6.format(offsetV));
            player.sendMessage(ChatColor.YELLOW + "[SurvivalFly] hdistrel: predict=" + StringUtil.fdec6.format(thisMove.hAllowedDistance) + ", actual=" + StringUtil.fdec6.format(thisMove.hDistance) + ", offset=" + StringUtil.fdec6.format(offsetH));
        }
        return new double[]{thisMove.hAllowedDistance, hDistanceAboveLimit, thisMove.yAllowedDistance, yDistanceAboveLimit};
    }

    private boolean isElytraModelDataOnly(final Player player, final MovingConfig cc) {
        return !cc.sfElytraEnforce && Bridge1_9.isGliding(player);
    }
    
    /**
     * Reset the given speed upon wall collision.
     * 
     * @param lastMove
     * @param thisMove
     */
    private void doWallCollision(PlayerMoveData lastMove, PlayerMoveData thisMove) {
        if (lastMove.collideX) {
            thisMove.xAllowedDistance = 0.0;
        }
        if (lastMove.collideZ) {
            thisMove.zAllowedDistance = 0.0;
        }
    }
    
    /**
     * Check if the allowed speed set in thisMove should be canceled due to it being lower than the negligible speed threshold. <br>
     * (Horizontal only)
     * 
     * @param pData
     * @param thisMove
     */
    private void checkNegligibleMomentum(IPlayerData pData, PlayerMoveData thisMove) {
        final ClientVersion clientVersion = getMovementClientVersion(pData);
        if (clientVersion.isAtLeast(ClientVersion.V_1_21_5)) {
            // This condition was added on 1.21.5. If the horizontal distance squared is below 0.000009 and the entity is a player, both horizontal momenta are set to 0.0.
            // This means that both x/z momenta can be reset even if one of them is above the threshold, as long as the overall horizontal momentum is below the threshold.
            // EntityLiving.java -> aiStep
            // We use the unchecked hDistance for performance reasons (no sqrt needed).
            if (thisMove.hDistance < Magic.NEGLIGIBLE_SPEED_THRESHOLD) {
                thisMove.xAllowedDistance = 0.0;
                thisMove.zAllowedDistance = 0.0;
            }
        }
        else if (clientVersion.isAtLeast(ClientVersion.V_1_9)) {
            if (Math.abs(thisMove.xAllowedDistance) < Magic.NEGLIGIBLE_SPEED_THRESHOLD) {
                thisMove.xAllowedDistance = 0.0;
            }
            if (Math.abs(thisMove.zAllowedDistance) < Magic.NEGLIGIBLE_SPEED_THRESHOLD) {
                thisMove.zAllowedDistance = 0.0;
            }
        }
        else {
            // In 1.8 and lower, momentum is compared to 0.005 instead.
            if (Math.abs(thisMove.xAllowedDistance) < Magic.NEGLIGIBLE_SPEED_THRESHOLD_LEGACY) {
                thisMove.xAllowedDistance = 0.0;
            }
            if (Math.abs(thisMove.zAllowedDistance) < Magic.NEGLIGIBLE_SPEED_THRESHOLD_LEGACY) {
                thisMove.zAllowedDistance = 0.0;
            }
        }
    }
    
    /**
     * Check if the allowed speed set in thisMove should be canceled due to it being lower than the negligible speed threshold. <br>
     * (Vertical only).
     * 
     * @param pData
     * @param thisMove
     */
    private void checkNegligibleMomentumVertical(IPlayerData pData, PlayerMoveData thisMove) {
        if (Math.abs(thisMove.yAllowedDistance) < (getMovementClientVersion(pData).isAtLeast(ClientVersion.V_1_9) ? Magic.NEGLIGIBLE_SPEED_THRESHOLD : Magic.NEGLIGIBLE_SPEED_THRESHOLD_LEGACY)) {
            thisMove.yAllowedDistance = 0.0;
        }
    }
    
    /**
     * Prepares the estimation for horizontal speed of the player based on various conditions.
     *
     * @param forceSetOnGround Whether to forcibly consider the ground status of the player (despite being off ground).
     * @param forceSetOffGround Whether to forcibly ignore the ground status of the player (despite being on the ground).
     * @param isNormalOrPacketSplitMove Whether this movement has been corrected due to a faulty PlayerMoveEvent or is normal (no correction needed)
     *
     * @return hAllowedDistance, hDistanceAboveLimit
     */
    private double[] prepareSpeedEstimation(final PlayerLocation from, final PlayerLocation to, final IPlayerData pData, final Player player,
                                            final MovingData data, final PlayerMoveData thisMove, final PlayerMoveData lastMove,
                                            final boolean fromOnGround, final boolean toOnGround, final boolean debug,
                                            final boolean isNormalOrPacketSplitMove, boolean forceSetOnGround, boolean forceSetOffGround) {
        double hDistanceAboveLimit;
        final ClientVersion clientVersion = getMovementClientVersion(pData);
        //////////////////////////
        // Early return(s)      //
        //////////////////////////
        if (!isNormalOrPacketSplitMove) {
            // Bukkit-based split move: predicting the next speed is not possible due to coordinates not being reported correctly by Bukkit (and without ProtocolLib, it's nearly impossible to achieve precision here)
            thisMove.xAllowedDistance = thisMove.xDistance;
            thisMove.zAllowedDistance = thisMove.zDistance;
            thisMove.hAllowedDistance = thisMove.hDistance;
            hDistanceAboveLimit = 0.0;
            return new double[]{thisMove.hAllowedDistance, hDistanceAboveLimit};
        }
        
        boolean onGround = from.isOnGround() || lastMove.toIsValid && lastMove.yDistance <= 0.0 && lastMove.from.onGround || lastMove.yDistance < 0.0 && thisMove.fromLostGround || forceSetOnGround;
        // Override ground status if needed.
        if (forceSetOffGround) onGround = false;
        /* All moves are assumed to be predictable, unless there are technical limitations / bugs / glitches that we cannot solve */
        boolean isPredictable;
        //////////////////////////////////////////////////////////////
        // Estimate the horizontal speed (per-move distance check)  //                      
        //////////////////////////////////////////////////////////////
        // Determine inertia and acceleration to calculate speed with.
        // Only check using the 'from' position because it is the current location of the player (NMS-wise)
        if (from.isInWater()) {
            data.nextInertia = Bridge1_13.isSwimming(player) ? Magic.HORIZONTAL_SWIMMING_INERTIA : Magic.WATER_HORIZONTAL_INERTIA;
            /* Per-tick speed gain. */
            float acceleration = Magic.LIQUID_ACCELERATION;
            float StriderLevel = attributeAccess.getHandle().getWaterMovementEfficiency(player);
            if (!onGround) {
                StriderLevel *= Magic.STRIDER_OFF_GROUND_MULTIPLIER;
            }
            if (StriderLevel > 0.0) {
                // (Less speed conservation (or in other words, more friction))
                data.nextInertia += (0.54600006f - data.nextInertia) * StriderLevel / (clientVersion.isAtMost(ClientVersion.V_1_20_6) ? 3.0f : 1.0f); // Mojang removed this / by 3 in 1.21 and switched to the WATER_MOVEMENT_EFFICIENCY attribute
                // (More per-tick speed gain)
                acceleration += (data.walkSpeed - acceleration) * StriderLevel / (clientVersion.isAtMost(ClientVersion.V_1_20_6) ? 3.0f : 1.0f);
            }
            if (!Double.isInfinite(Bridge1_13.getDolphinGraceAmplifier(player))) {
                // (Much more speed conservation (or in other words, much less friction))
                // (Overrides swimming AND depth strider friction)
                data.nextInertia = Magic.DOLPHIN_GRACE_INERTIA;
            }
            // Run through all operations
            isPredictable = estimateNextSpeed(player, acceleration, pData, tags, to, from, debug, fromOnGround, toOnGround, onGround, forceSetOffGround);
        }
        else if (from.isInLava()) {
            data.nextInertia = Magic.LAVA_HORIZONTAL_INERTIA;
            isPredictable = estimateNextSpeed(player, Magic.LIQUID_ACCELERATION, pData, tags, to, from, debug, fromOnGround, toOnGround, onGround, forceSetOffGround);
        }
        else {
            data.nextInertia = onGround ? data.nextFrictionHorizontal * Magic.AIR_HORIZONTAL_INERTIA : Magic.AIR_HORIZONTAL_INERTIA;
            // 1.12 (and below) clients will use cubed inertia, not cubed friction here. The difference isn't significant except for blocking speed and bunnyhopping on soul sand, which are both slower on 1.8
            float frictionMediumFactor = clientVersion.isAtLeast(ClientVersion.V_1_13) ? data.nextFrictionHorizontal : data.nextFrictionHorizontal * Magic.AIR_HORIZONTAL_INERTIA;
            float acceleration = onGround ? data.walkSpeed * ((clientVersion.isAtLeast(ClientVersion.V_1_13) ? Magic.DEFAULT_FRICTION_CUBED : Magic.DEFAULT_FRICTION_MULTIPLIED_BY_091_CUBED) / (frictionMediumFactor * frictionMediumFactor * frictionMediumFactor)) : Magic.AIR_ACCELERATION;
            if (pData.isSprinting()) {
                // (We don't use the attribute here due to desync issues, just detect when the player is sprinting and apply the multiplier manually)
                acceleration += acceleration * 0.3f; // 0.3 is the effective sprinting speed (EntityLiving).
            }
            isPredictable = estimateNextSpeed(player, acceleration, pData, tags, to, from, debug, fromOnGround, toOnGround, onGround, forceSetOffGround);
        }
        
        /////////////////////////////////////////////////
        // Set the combined allowed horizontal speed   //
        /////////////////////////////////////////////////
        thisMove.hAllowedDistance = MathUtil.dist(thisMove.xAllowedDistance, thisMove.zAllowedDistance);
        
        ////////////////////////
        // Calculate offsets  //
        ////////////////////////
        final MovingConfig cc = pData.getGenericInstance(MovingConfig.class);
        if (isPredictable) {
            hDistanceAboveLimit = handlePredictableMove(thisMove, cc.survivalFlyStrictHorizontal); 
        }
        else hDistanceAboveLimit = handleUnpredictableMove(thisMove, cc.survivalFlyStrictHorizontal);
        if (hDistanceAboveLimit > 0.0) {
            tags.add("hdistrel");
            if (debug) {
                player.sendMessage(ChatColor.YELLOW + "[SurvivalFly] hdistrel: predicted=" + StringUtil.fdec6.format(thisMove.hAllowedDistance) + ", actual=" + StringUtil.fdec6.format(thisMove.hDistance));
            }
        }
        return new double[]{thisMove.hAllowedDistance, hDistanceAboveLimit};
    }

    private ClientVersion getMovementClientVersion(final IPlayerData pData) {
        final ClientVersion clientVersion = pData.getClientVersion();
        return clientVersion == ClientVersion.UNKNOWN ? ClientVersion.getLatest() : clientVersion;
    }

    // Movement client models: Bedrock and newer clients can round or packetize movement differently than legacy Java clients.
    private boolean isBedrockPacketPrecisionContext(final Player player, final IPlayerData pData,
                                                    final PlayerLocation from, final PlayerLocation to,
                                                    final PlayerMoveData thisMove) {
        return isBedrockPlayer(player, pData)
                && !Bridge1_9.isGliding(player)
                && Math.abs(thisMove.yDistance) <= GROUNDED_JUMP_VERTICAL_MOVE_GRACE
                && thisMove.hDistance <= thisMove.hAllowedDistance + BEDROCK_HORIZONTAL_PREDICTION_EPSILON
                && !from.isInLiquid() && !to.isInLiquid()
                && !thisMove.from.inLiquid && !thisMove.to.inLiquid;
    }

    private boolean acceptsBedrockPacketPrecisionHorizontalModel(final Player player, final IPlayerData pData,
                                                                 final PlayerLocation from, final PlayerLocation to,
                                                                 final PlayerMoveData thisMove,
                                                                 final double hDistanceAboveLimit) {
        if (!isBedrockPacketPrecisionContext(player, pData, from, to, thisMove)) {
            return false;
        }
        final double horizontalLimit = thisMove.hAllowedDistance + BEDROCK_HORIZONTAL_PREDICTION_EPSILON;
        if (thisMove.hDistance <= horizontalLimit
                && hDistanceAboveLimit <= getModelOverLimit(thisMove.hAllowedDistance,
                        horizontalLimit, BEDROCK_HORIZONTAL_PREDICTION_EPSILON)) {
            tags.add("bedrock_packet_precision_horizontal_model");
            return true;
        }
        tags.add("bedrock_packet_precision_horizontal_model_miss");
        return false;
    }

    private boolean acceptsBedrockPacketPrecisionVerticalModel(final Player player, final IPlayerData pData,
                                                               final PlayerLocation from, final PlayerLocation to,
                                                               final PlayerMoveData thisMove,
                                                               final double yDistanceAboveLimit,
                                                               final double hDistanceAboveLimit) {
        if (!isBedrockPacketPrecisionContext(player, pData, from, to, thisMove)
                || hDistanceAboveLimit > BEDROCK_HORIZONTAL_PREDICTION_EPSILON) {
            return false;
        }
        /*
         * Bedrock model: Floodgate/Geyser packets can expose a tiny Y snap around
         * ground/partial-block transitions. This is packet precision, not a fall
         * bypass, so keep it under one-centimeter movement and one-centimeter over.
         */
        if (Math.abs(thisMove.yDistance) <= BEDROCK_PACKET_VERTICAL_PRECISION
                && yDistanceAboveLimit <= BEDROCK_PACKET_VERTICAL_PRECISION) {
            tags.add("bedrock_packet_precision_vertical_model");
            return true;
        }
        tags.add("bedrock_packet_precision_vertical_model_miss");
        return false;
    }

    private boolean isNewerClientGroundPredictionContext(final IPlayerData pData,
                                                        final PlayerLocation from, final PlayerLocation to,
                                                        final PlayerMoveData thisMove) {
        return pData.getClientVersion() == ClientVersion.HIGHER_THAN_KNOWN_VERSIONS
                && thisMove.hDistance > thisMove.hAllowedDistance + Magic.PREDICTION_EPSILON
                && !from.isInLiquid() && !to.isInLiquid()
                && !thisMove.from.inLiquid && !thisMove.to.inLiquid
                && (isNewerClientGroundedPredictionState(from, to, thisMove)
                        || isNewerClientStepPredictionState(from, to, thisMove)
                        || isNewerClientJumpPredictionState(from, to, thisMove)
                        || isNewerClientPrecisionPredictionState(thisMove))
                && thisMove.hDistance <= getNewerClientGroundHorizontalModelLimit(from, to, thisMove);
    }

    private boolean acceptsNewerClientGroundHorizontalModel(final IPlayerData pData,
                                                            final PlayerLocation from, final PlayerLocation to,
                                                            final PlayerMoveData thisMove,
                                                            final double hDistanceAboveLimit) {
        if (!isNewerClientGroundPredictionContext(pData, from, to, thisMove)) {
            return false;
        }
        final double horizontalLimit = getNewerClientGroundHorizontalModelLimit(from, to, thisMove);
        final double residual = getNewerClientGroundHorizontalResidual(from, to, thisMove);
        if (thisMove.hDistance <= horizontalLimit
                && hDistanceAboveLimit <= getModelOverLimit(thisMove.hAllowedDistance, horizontalLimit, residual)) {
            tags.add(getNewerClientGroundModelTag(from, to, thisMove));
            return true;
        }
        tags.add("modern_client_ground_horizontal_model_miss");
        return false;
    }

    private boolean acceptsModernClientGroundVerticalModel(final IPlayerData pData,
                                                           final PlayerLocation from,
                                                           final PlayerLocation to,
                                                           final PlayerMoveData thisMove,
                                                           final double yDistanceAboveLimit,
                                                           final double hDistanceAboveLimit) {
        if (!isNewerClientGroundPredictionContext(pData, from, to, thisMove)
                || hDistanceAboveLimit > GROUNDED_MICRO_HORIZONTAL_GRACE
                || !isGroundishStepMove(from, to, thisMove)
                || !to.isOnGroundOrResetCond()
                || thisMove.yDistance >= -Magic.PREDICTION_EPSILON) {
            return false;
        }
        final double landingDistance = to.getBlockY() - from.getY();
        final double descend = -thisMove.yDistance;
        final double quantized = Math.round(descend / PARTIAL_SUPPORT_VERTICAL_CLAMP_UNIT)
                * PARTIAL_SUPPORT_VERTICAL_CLAMP_UNIT;
        final boolean quantizedLanding = Math.abs(thisMove.yDistance - landingDistance)
                <= PARTIAL_SUPPORT_VERTICAL_CLAMP_EPSILON
                || Math.abs(descend - quantized) <= PARTIAL_SUPPORT_VERTICAL_CLAMP_EPSILON;
        if (quantizedLanding
                && descend <= PARTIAL_SUPPORT_VERTICAL_CLAMP_UNIT + PARTIAL_SUPPORT_VERTICAL_CLAMP_EPSILON
                && yDistanceAboveLimit <= Math.abs(thisMove.yAllowedDistance - thisMove.yDistance)
                        + PARTIAL_SUPPORT_VERTICAL_CLAMP_EPSILON) {
            tags.add("modern_client_ground_quantized_landing_vertical_model");
            return true;
        }
        tags.add("modern_client_ground_quantized_landing_vertical_model_miss");
        return false;
    }

    private boolean isNewerClientGroundedPredictionState(final PlayerLocation from, final PlayerLocation to,
                                                         final PlayerMoveData thisMove) {
        // Model cleanup: modern clients can miss the ground model by tiny amounts while sprinting/turning.
        return isGroundishStepMove(from, to, thisMove)
                && Math.abs(thisMove.yDistance) <= Magic.PREDICTION_EPSILON
                && thisMove.hDistance <= GROUNDED_MICRO_MOVE_GRACE;
    }

    private boolean isNewerClientStepPredictionState(final PlayerLocation from, final PlayerLocation to,
                                                     final PlayerMoveData thisMove) {
        // Model cleanup: stair/slab step packets use the step-height envelope instead of a post-failure H grace.
        return isGroundishStepMove(from, to, thisMove)
                && isStepBlockNear(from, to)
                && thisMove.yDistance >= -Magic.PREDICTION_EPSILON
                && thisMove.yDistance <= GROUNDED_STEP_VERTICAL_MOVE_GRACE
                && thisMove.hDistance <= GROUNDED_STEP_HORIZONTAL_MOVE_GRACE;
    }

    private boolean isNewerClientJumpPredictionState(final PlayerLocation from, final PlayerLocation to,
                                                     final PlayerMoveData thisMove) {
        // Model cleanup: jump-start packets keep one tick of sprint carry rather than accepting actual H afterward.
        return (tags.contains("jump_env") || tags.contains("bunnyhop"))
                && isGroundishStepMove(from, to, thisMove)
                && thisMove.yDistance >= -Magic.PREDICTION_EPSILON
                && thisMove.yDistance <= GROUNDED_JUMP_VERTICAL_MOVE_GRACE
                && thisMove.hDistance <= GROUNDED_JUMP_HORIZONTAL_MOVE_GRACE;
    }

    private boolean isNewerClientPrecisionPredictionState(final PlayerMoveData thisMove) {
        // Model cleanup: preserve a tiny client-version precision envelope for unknown newer clients.
        return thisMove.hDistance <= NEWER_CLIENT_HORIZONTAL_MOVE_GRACE
                && Math.abs(thisMove.yDistance) <= GROUNDED_JUMP_VERTICAL_MOVE_GRACE;
    }

    private double getNewerClientGroundHorizontalModelLimit(final PlayerLocation from, final PlayerLocation to,
                                                            final PlayerMoveData thisMove) {
        if (isNewerClientStepPredictionState(from, to, thisMove)) {
            return Math.min(GROUNDED_STEP_HORIZONTAL_MOVE_GRACE,
                    Math.max(playerStepHorizontalModel(thisMove),
                            thisMove.hAllowedDistance + GROUNDED_STEP_HORIZONTAL_OVER_GRACE));
        }
        if (isNewerClientJumpPredictionState(from, to, thisMove)) {
            return Math.min(GROUNDED_JUMP_HORIZONTAL_MOVE_GRACE,
                    Math.max(playerStepHorizontalModel(thisMove),
                            thisMove.hAllowedDistance + GROUNDED_JUMP_HORIZONTAL_OVER_GRACE));
        }
        if (isNewerClientGroundedPredictionState(from, to, thisMove)) {
            return Math.min(GROUNDED_MICRO_MOVE_GRACE,
                    thisMove.hAllowedDistance + GROUNDED_MICRO_OVER_GRACE);
        }
        return Math.min(NEWER_CLIENT_HORIZONTAL_MOVE_GRACE,
                thisMove.hAllowedDistance + NEWER_CLIENT_HORIZONTAL_OVER_GRACE);
    }

    private double getNewerClientGroundHorizontalResidual(final PlayerLocation from, final PlayerLocation to,
                                                          final PlayerMoveData thisMove) {
        if (isNewerClientStepPredictionState(from, to, thisMove)) {
            return GROUNDED_STEP_HORIZONTAL_OVER_GRACE;
        }
        if (isNewerClientJumpPredictionState(from, to, thisMove)) {
            return GROUNDED_JUMP_HORIZONTAL_OVER_GRACE;
        }
        if (isNewerClientGroundedPredictionState(from, to, thisMove)) {
            return GROUNDED_MICRO_OVER_GRACE;
        }
        return NEWER_CLIENT_HORIZONTAL_OVER_GRACE;
    }

    private String getNewerClientGroundModelTag(final PlayerLocation from, final PlayerLocation to,
                                                final PlayerMoveData thisMove) {
        if (isNewerClientStepPredictionState(from, to, thisMove)) {
            return "modern_client_step_horizontal_model";
        }
        if (isNewerClientJumpPredictionState(from, to, thisMove)) {
            return "modern_client_jump_horizontal_model";
        }
        if (isNewerClientGroundedPredictionState(from, to, thisMove)) {
            return "modern_client_ground_horizontal_model";
        }
        return "modern_client_precision_horizontal_model";
    }

    private boolean isGroundedRecoveryContext(final PlayerLocation from, final PlayerLocation to,
                                              final PlayerMoveData thisMove, final PlayerMoveData lastMove) {
        return tags.contains("onground_env")
                && Math.abs(thisMove.yDistance) <= Magic.PREDICTION_EPSILON
                && (from.isOnGroundOrResetCond() || thisMove.from.onGroundOrResetCond)
                && (to.isOnGroundOrResetCond() || thisMove.to.onGroundOrResetCond)
                && (!lastMove.toIsValid || thisMove.multiMoveCount > 0
                        || thisMove.hDistance <= GROUNDED_SETBACK_MOVE_GRACE)
                && thisMove.hDistance <= getGroundedRecoveryHorizontalModelLimit(thisMove, lastMove)
                && !from.isInLiquid() && !to.isInLiquid()
                && !thisMove.from.inLiquid && !thisMove.to.inLiquid;
    }

    private boolean acceptsGroundedRecoveryHorizontalModel(final PlayerLocation from, final PlayerLocation to,
                                                           final PlayerMoveData thisMove, final PlayerMoveData lastMove,
                                                           final double hDistanceAboveLimit) {
        if (!isGroundedRecoveryContext(from, to, thisMove, lastMove)) {
            return false;
        }
        final double horizontalLimit = getGroundedRecoveryHorizontalModelLimit(thisMove, lastMove);
        if (thisMove.hDistance <= horizontalLimit
                && hDistanceAboveLimit <= getModelOverLimit(thisMove.hAllowedDistance,
                        horizontalLimit, GROUNDED_MICRO_HORIZONTAL_GRACE)) {
            tags.add("grounded_recovery_horizontal_model");
            return true;
        }
        tags.add("grounded_recovery_horizontal_model_miss");
        return false;
    }

    private double getGroundedRecoveryHorizontalModelLimit(final PlayerMoveData thisMove,
                                                           final PlayerMoveData lastMove) {
        final double historyCarry = lastMove.toIsValid ? lastMove.hDistance : thisMove.hAllowedDistance;
        // Model cleanup: grounded recovery uses prior movement plus current input, capped by the old empirical recovery window.
        return Math.min(GROUNDED_SETBACK_MOVE_GRACE,
                Math.max(thisMove.hAllowedDistance, historyCarry + playerInputHorizontalCarry(thisMove)
                        + GROUNDED_MICRO_HORIZONTAL_GRACE));
    }

    // Explicit model dispatch: select the movement state first, then compare against that state's envelope.
    private double[] applyExplicitMovementModel(final Player player, final IPlayerData pData, final MovingData data,
                                                final PlayerLocation from, final PlayerLocation to,
                                                final PlayerMoveData thisMove, final PlayerMoveData lastMove,
                                                double hDistanceAboveLimit, double yDistanceAboveLimit,
                                                final boolean resetFrom, final boolean resetTo) {
        if (hDistanceAboveLimit <= 0.0 && yDistanceAboveLimit <= 0.0) {
            return new double[]{hDistanceAboveLimit, yDistanceAboveLimit};
        }
        final MovementModelBranch branch = selectExplicitMovementModel(player, pData, data, from, to, thisMove, lastMove);
        if (branch == MovementModelBranch.NONE) {
            return new double[]{hDistanceAboveLimit, yDistanceAboveLimit};
        }

        // Model pass: evaluate the chosen state before the old one-axis fallbacks run.
        boolean acceptedH = hDistanceAboveLimit > 0.0
                && acceptsExplicitHorizontalModel(branch, player, pData, data, from, to, thisMove, lastMove, hDistanceAboveLimit);
        final double hDistanceForVertical = acceptedH ? 0.0D : hDistanceAboveLimit;
        boolean acceptedY = yDistanceAboveLimit > 0.0
                && acceptsExplicitVerticalModel(branch, player, pData, data, from, to, thisMove, lastMove,
                        yDistanceAboveLimit, hDistanceForVertical, resetFrom, resetTo);

        if (!acceptedH && !acceptedY) {
            tags.add("model_" + branch.tag + "_miss");
            return new double[]{hDistanceAboveLimit, yDistanceAboveLimit};
        }
        tags.add("model_" + branch.tag);
        if (acceptedH) {
            applyHorizontalModelAllowance(thisMove, branch);
            hDistanceAboveLimit = 0.0;
        }
        if (acceptedY) {
            applyVerticalModelAllowance(thisMove, branch);
            yDistanceAboveLimit = 0.0;
        }
        return new double[]{hDistanceAboveLimit, yDistanceAboveLimit};
    }

    private MovementModelBranch selectExplicitMovementModel(final Player player, final IPlayerData pData,
                                                            final MovingData data, final PlayerLocation from,
                                                            final PlayerLocation to, final PlayerMoveData thisMove,
                                                            final PlayerMoveData lastMove) {
        // Order matters: choose the most concrete state first so logs point at the real model, not a broader fallback.
        if (Bridge1_9.isGliding(player)) {
            return data.fireworksBoostDuration > 0
                    ? MovementModelBranch.ELYTRA_FIREWORK : MovementModelBranch.ELYTRA_GLIDING;
        }
        if (isPortalNear(from, to) && (!lastMove.toIsValid || data.liftOffEnvelope == LiftOffEnvelope.UNKNOWN)) {
            return MovementModelBranch.PORTAL_TRANSITION;
        }
        if (isServerPositionJumpResyncContext(pData, from, to, thisMove, lastMove)) {
            return MovementModelBranch.SERVER_POSITION_JUMP_RESYNC;
        }
        if (isWaterMovementContext(from, to, thisMove)) {
            return !Double.isInfinite(Bridge1_13.getDolphinGraceAmplifier(player))
                    ? MovementModelBranch.WATER_DOLPHIN : MovementModelBranch.WATER;
        }
        if (isLavaMovementContext(from, to, thisMove)) {
            return MovementModelBranch.LAVA;
        }
        if (isClimbableMovementContext(from, to, thisMove)) {
            return MovementModelBranch.CLIMBABLE;
        }
        if (Bridge1_9.isWearingElytra(player) && data.fireworksBoostDuration > 0) {
            return MovementModelBranch.ELYTRA_EQUIPPED_FIREWORK;
        }
        if (isBedrockStepContext(player, pData, from, to, thisMove, lastMove)) {
            return MovementModelBranch.BEDROCK_STEP;
        }
        if (isElytraEquippedTransitionContext(player, data, from, to, thisMove, lastMove)) {
            return MovementModelBranch.ELYTRA_EQUIPPED_TRANSITION;
        }
        if (isPartialSupportMovementContext(from, to, thisMove, lastMove)) {
            return MovementModelBranch.PARTIAL_SUPPORT;
        }
        if (isLastInvalidResyncContext(player, from, to, thisMove, lastMove)) {
            return MovementModelBranch.LAST_INVALID_RESYNC;
        }
        if (isModernHalfStepContext(player, pData, from, to, thisMove, lastMove)) {
            return MovementModelBranch.MODERN_HALF_STEP;
        }
        if (isNewerClientGroundPredictionContext(pData, from, to, thisMove)) {
            return MovementModelBranch.MODERN_CLIENT_GROUND;
        }
        if (isBedrockGroundedCombatContext(player, pData, from, to, thisMove)) {
            return MovementModelBranch.BEDROCK_GROUNDED_COMBAT;
        }
        if (isBedrockPacketPrecisionContext(player, pData, from, to, thisMove)) {
            return MovementModelBranch.BEDROCK_PACKET_PRECISION;
        }
        if (isJumpCarryContext(player, from, to, thisMove, lastMove)) {
            return MovementModelBranch.JUMP_CARRY;
        }
        if (isGroundPassablePlantContext(player, from, to, thisMove, lastMove)) {
            return MovementModelBranch.GROUND_PASSABLE_PLANT;
        }
        if (isGroundedRecoveryContext(from, to, thisMove, lastMove)) {
            return MovementModelBranch.GROUNDED_RECOVERY;
        }
        if (isQueuedVelocityContext(data, thisMove)) {
            return MovementModelBranch.QUEUED_VELOCITY;
        }
        if (isGroundJumpTinyContext(thisMove)) {
            return MovementModelBranch.GROUND_JUMP_TINY;
        }
        if (isGroundLandingCarryContext(player, from, to, thisMove, lastMove)) {
            return MovementModelBranch.GROUND_LANDING_CARRY;
        }
        if (isGroundVelocityCarryContext(player, from, to, thisMove)) {
            return MovementModelBranch.GROUND_VELOCITY_CARRY;
        }
        if (isGroundedVerticalVelocityContext(player, from, to, thisMove)) {
            return MovementModelBranch.GROUNDED_VERTICAL_VELOCITY;
        }
        if (isServerVerticalVelocityContext(player, data, from, to, thisMove)) {
            return MovementModelBranch.SERVER_VERTICAL_VELOCITY;
        }
        if (isGroundedItemResyncContext(thisMove)) {
            return MovementModelBranch.GROUNDED_ITEM_RESYNC;
        }
        if (isItemResyncMovementContext(player, from, to, thisMove)) {
            return MovementModelBranch.ITEM_RESYNC;
        }
        if (isAirCurrentVelocityContext(player, from, to, thisMove)) {
            return MovementModelBranch.AIR_CURRENT_VELOCITY;
        }
        if (isAirInertiaMovementContext(player, from, to, thisMove, lastMove)) {
            return MovementModelBranch.AIR_INERTIA;
        }
        if (isCollisionMovementContext(player, from, to, thisMove, lastMove)) {
            return MovementModelBranch.COLLISION;
        }
        if (isModernVerticalImpulseContext(player, pData, from, to, thisMove)) {
            return MovementModelBranch.MODERN_VERTICAL_IMPULSE;
        }
        if (isCurrentServerVelocityVerticalContext(player, from, to, thisMove)) {
            return MovementModelBranch.CURRENT_SERVER_VELOCITY;
        }
        if (isSetbackGravityRecoveryContext(data, from, to, thisMove, lastMove)) {
            return MovementModelBranch.SETBACK_GRAVITY;
        }
        if (isLevitationMovementContext(player, thisMove)) {
            return MovementModelBranch.LEVITATION;
        }
        return MovementModelBranch.NONE;
    }

    private boolean acceptsExplicitHorizontalModel(final MovementModelBranch branch, final Player player,
                                                   final IPlayerData pData, final MovingData data,
                                                   final PlayerLocation from, final PlayerLocation to,
                                                   final PlayerMoveData thisMove, final PlayerMoveData lastMove,
        final double hDistanceAboveLimit) {
        switch (branch) {
            case ELYTRA_GLIDING:
                return acceptsGlidingHorizontalVelocityModel(player, data, from, to, thisMove, hDistanceAboveLimit)
                        || acceptsElytraLandingInertiaHorizontalModel(player, data, from, to, thisMove, lastMove,
                                hDistanceAboveLimit, true, false, "elytra_glide_landing_inertia_horizontal_model")
                        || acceptsGlidingSteepDiveEnergyModel(player, thisMove, hDistanceAboveLimit);
            case ELYTRA_FIREWORK:
                return acceptsGlidingHorizontalVelocityModel(player, data, from, to, thisMove, hDistanceAboveLimit)
                        || acceptsElytraLandingInertiaHorizontalModel(player, data, from, to, thisMove, lastMove,
                                hDistanceAboveLimit, true, true, "elytra_firework_landing_inertia_horizontal_model")
                        || acceptsGlidingFireworkHorizontalModel(player, pData, data, from, to, thisMove, lastMove,
                                hDistanceAboveLimit);
            case ELYTRA_EQUIPPED_FIREWORK:
                return acceptsElytraLandingInertiaHorizontalModel(player, data, from, to, thisMove, lastMove,
                        hDistanceAboveLimit, false, true, "elytra_equipped_firework_landing_inertia_horizontal_model")
                        || acceptsElytraEquippedFireworkHorizontalModel(player, data, from, to, thisMove, lastMove,
                                hDistanceAboveLimit);
            case ELYTRA_EQUIPPED_TRANSITION:
                return acceptsElytraEquippedGlideCoastHorizontalModel(player, pData, data, from, to,
                        thisMove, lastMove, hDistanceAboveLimit)
                        || acceptsElytraEquippedQueuedVelocityHorizontalModel(player, data, from, to, thisMove, hDistanceAboveLimit)
                        || acceptsElytraEquippedVelocityHorizontalModel(player, from, to, thisMove, lastMove, hDistanceAboveLimit)
                        || acceptsElytraEquippedGroundHorizontalModel(player, from, to, thisMove, lastMove, hDistanceAboveLimit)
                        || acceptsElytraEquippedVelocityHandoffHorizontalModel(player, from, to, thisMove, lastMove, hDistanceAboveLimit)
                        || acceptsElytraEquippedPartialSupportHorizontalModel(player, from, to, thisMove, lastMove, hDistanceAboveLimit)
                        || acceptsJumpCarryHorizontalModel(player, from, to, thisMove, lastMove, hDistanceAboveLimit)
                        || acceptsElytraLandingInertiaHorizontalModel(player, data, from, to, thisMove, lastMove,
                                hDistanceAboveLimit, false, false, "elytra_equipped_landing_inertia_horizontal_model")
                        || acceptsElytraEquippedDescendHorizontalModel(player, thisMove, hDistanceAboveLimit)
                        || acceptsElytraGeometryStallHorizontalModel(player, thisMove, lastMove, hDistanceAboveLimit);
            case PORTAL_TRANSITION:
                return isPortalTransitionHorizontalGrace(from, to, data, thisMove, lastMove, hDistanceAboveLimit);
            case SERVER_POSITION_JUMP_RESYNC:
                return acceptsServerPositionJumpResyncHorizontalModel(pData, from, to, thisMove, lastMove,
                        hDistanceAboveLimit);
            case WATER_DOLPHIN:
                return acceptsWaterHorizontalModel(player, data, from, to, thisMove, hDistanceAboveLimit, true);
            case WATER:
                return acceptsWaterHorizontalModel(player, data, from, to, thisMove, hDistanceAboveLimit, false);
            case LAVA:
                return acceptsLavaHorizontalModel(player, data, from, to, thisMove, hDistanceAboveLimit);
            case CLIMBABLE:
                return acceptsClimbableHorizontalModel(from, to, thisMove, lastMove, hDistanceAboveLimit);
            case BEDROCK_STEP:
                return acceptsBedrockStepHorizontalModel(player, pData, from, to, thisMove, lastMove, hDistanceAboveLimit);
            case BEDROCK_PACKET_PRECISION:
                return acceptsBedrockPacketPrecisionHorizontalModel(player, pData, from, to, thisMove, hDistanceAboveLimit);
            case BEDROCK_GROUNDED_COMBAT:
                return acceptsBedrockGroundedCombatHorizontalModel(player, pData, from, to, thisMove, hDistanceAboveLimit);
            case MODERN_HALF_STEP:
                return acceptsModernHalfStepHorizontalModel(player, pData, from, to, thisMove, lastMove, hDistanceAboveLimit);
            case MODERN_CLIENT_GROUND:
                return acceptsNewerClientGroundHorizontalModel(pData, from, to, thisMove, hDistanceAboveLimit);
            case JUMP_CARRY:
                return acceptsJumpCarryHorizontalModel(player, from, to, thisMove, lastMove, hDistanceAboveLimit);
            case GROUND_JUMP_TINY:
                return acceptsGroundJumpTinyHorizontalModel(thisMove, hDistanceAboveLimit);
            case GROUND_LANDING_CARRY:
                return acceptsGroundLandingCarryHorizontalModel(player, from, to, thisMove, lastMove, hDistanceAboveLimit);
            case GROUND_VELOCITY_CARRY:
                return acceptsGroundVelocityCarryHorizontalModel(player, from, to, thisMove, hDistanceAboveLimit);
            case GROUND_PASSABLE_PLANT:
                return acceptsGroundPassablePlantHorizontalModel(player, from, to, thisMove, lastMove, hDistanceAboveLimit);
            case GROUNDED_RECOVERY:
                return acceptsGroundedRecoveryHorizontalModel(from, to, thisMove, lastMove, hDistanceAboveLimit);
            case GROUNDED_VERTICAL_VELOCITY:
                return acceptsGroundedVerticalVelocityHorizontalModel(player, from, to, thisMove, hDistanceAboveLimit);
            case SERVER_VERTICAL_VELOCITY:
                return acceptsServerVerticalVelocityHorizontalModel(player, data, from, to, thisMove, hDistanceAboveLimit);
            case GROUNDED_ITEM_RESYNC:
                return acceptsGroundedItemResyncHorizontalModel(thisMove, hDistanceAboveLimit);
            case ITEM_RESYNC:
                return acceptsItemResyncHorizontalModel(player, from, to, thisMove, hDistanceAboveLimit);
            case PARTIAL_SUPPORT:
                return acceptsPartialSupportHorizontalModel(from, to, thisMove, lastMove, hDistanceAboveLimit);
            case AIR_CURRENT_VELOCITY:
                return acceptsAirCurrentVelocityHorizontalModel(player, from, to, thisMove, hDistanceAboveLimit);
            case AIR_INERTIA:
                return acceptsAirInertiaHorizontalModel(player, from, to, thisMove, lastMove, hDistanceAboveLimit);
            case MODERN_VERTICAL_IMPULSE:
                return isModernVerticalImpulseHorizontalGrace(player, pData, from, to, thisMove, hDistanceAboveLimit);
            case QUEUED_VELOCITY:
                return isQueuedVelocityHorizontalGrace(player, data, from, to, thisMove, hDistanceAboveLimit);
            case CURRENT_SERVER_VELOCITY:
                return acceptsCurrentServerVelocityHorizontalModel(player, from, to, thisMove, lastMove, hDistanceAboveLimit);
            case LEVITATION:
            case SETBACK_GRAVITY:
                return false;
            case COLLISION:
                return acceptsCollisionHorizontalModel(player, from, to, thisMove, lastMove, hDistanceAboveLimit);
            case LAST_INVALID_RESYNC:
                return acceptsLastInvalidResyncHorizontalModel(player, from, to, thisMove, lastMove, hDistanceAboveLimit);
            default:
                return false;
        }
    }

    private boolean acceptsExplicitVerticalModel(final MovementModelBranch branch, final Player player,
                                                 final IPlayerData pData, final MovingData data,
                                                 final PlayerLocation from, final PlayerLocation to,
                                                 final PlayerMoveData thisMove, final PlayerMoveData lastMove,
                                                 final double yDistanceAboveLimit, final double hDistanceAboveLimit,
                                                 final boolean resetFrom, final boolean resetTo) {
        switch (branch) {
            case ELYTRA_GLIDING:
                return acceptsGlidingBelowVerticalModel(player, data, thisMove, yDistanceAboveLimit, hDistanceAboveLimit)
                        || acceptsGlidingSmallVerticalPredictionModel(player, thisMove, yDistanceAboveLimit, hDistanceAboveLimit)
                        || acceptsGlidingVerticalPrecisionModel(player, yDistanceAboveLimit)
                        || acceptsGlidingStallVerticalModel(player, data, thisMove, yDistanceAboveLimit, hDistanceAboveLimit)
                        || acceptsGlidingVelocityVerticalModel(player, thisMove, yDistanceAboveLimit, hDistanceAboveLimit);
            case ELYTRA_FIREWORK:
                return acceptsGlidingFireworkVerticalModel(player, pData, data, from, to, thisMove, lastMove,
                        yDistanceAboveLimit, hDistanceAboveLimit);
            case ELYTRA_EQUIPPED_FIREWORK:
                return acceptsElytraEquippedFireworkVerticalModel(player, data, from, to, thisMove, lastMove,
                        yDistanceAboveLimit, hDistanceAboveLimit)
                        || acceptsElytraEquippedPartialSupportVerticalModel(player, from, to, thisMove, lastMove,
                                yDistanceAboveLimit, hDistanceAboveLimit);
            case ELYTRA_EQUIPPED_TRANSITION:
                return isElytraEquippedHalfStepVerticalModel(player, from, to, thisMove, lastMove, yDistanceAboveLimit, hDistanceAboveLimit)
                        || acceptsElytraEquippedGlideCoastVerticalModel(player, pData, data, from, to,
                                thisMove, lastMove, yDistanceAboveLimit, hDistanceAboveLimit)
                        || acceptsElytraEquippedPartialSupportVerticalModel(player, from, to, thisMove, lastMove,
                                yDistanceAboveLimit, hDistanceAboveLimit)
                        || acceptsElytraEquippedVelocityVerticalModel(player, from, to, thisMove, lastMove, yDistanceAboveLimit, hDistanceAboveLimit)
                        || acceptsElytraEquippedLastInvalidAscendVerticalModel(player, from, to, thisMove, lastMove,
                                yDistanceAboveLimit, hDistanceAboveLimit)
                        || acceptsElytraEquippedStaleAscendVerticalModel(player, from, to, thisMove, lastMove, yDistanceAboveLimit, hDistanceAboveLimit)
                        || acceptsElytraEquippedVelocityHandoffVerticalModel(player, from, to, thisMove, lastMove, yDistanceAboveLimit, hDistanceAboveLimit)
                        || acceptsElytraEquippedGlideExitVerticalModel(player, thisMove, lastMove,
                                yDistanceAboveLimit, hDistanceAboveLimit)
                        || acceptsElytraEquippedDescendVerticalModel(player, thisMove, lastMove, yDistanceAboveLimit, hDistanceAboveLimit)
                        || acceptsElytraEquippedNeutralVerticalModel(player, thisMove, yDistanceAboveLimit, hDistanceAboveLimit)
                        || acceptsElytraEquippedSmallVerticalResyncModel(player, from, to, thisMove, lastMove, yDistanceAboveLimit, hDistanceAboveLimit)
                        || acceptsElytraLiftOffVerticalModel(player, thisMove, lastMove, yDistanceAboveLimit, hDistanceAboveLimit)
                        || acceptsElytraGeometryStallVerticalModel(player, thisMove, lastMove, yDistanceAboveLimit, hDistanceAboveLimit);
            case PORTAL_TRANSITION:
                return isPortalTransitionVerticalGrace(from, to, data, thisMove, lastMove, yDistanceAboveLimit, hDistanceAboveLimit);
            case SERVER_POSITION_JUMP_RESYNC:
                return acceptsServerPositionJumpResyncVerticalModel(pData, from, to, thisMove, lastMove,
                        yDistanceAboveLimit, hDistanceAboveLimit);
            case WATER_DOLPHIN:
            case WATER:
                return isWaterVerticalGrace(player, from, to, thisMove, lastMove, yDistanceAboveLimit, hDistanceAboveLimit, resetFrom, resetTo)
                        || isWaterTagVerticalGrace(yDistanceAboveLimit, hDistanceAboveLimit);
            case LAVA:
                return isLavaVerticalGrace(player, from, to, thisMove, yDistanceAboveLimit, hDistanceAboveLimit);
            case CLIMBABLE:
                return acceptsClimbableVerticalModel(from, to, thisMove, yDistanceAboveLimit, hDistanceAboveLimit);
            case BEDROCK_STEP:
                return acceptsBedrockStepVerticalModel(player, pData, from, to, thisMove, lastMove,
                        yDistanceAboveLimit, hDistanceAboveLimit);
            case BEDROCK_PACKET_PRECISION:
                return acceptsBedrockPacketPrecisionVerticalModel(player, pData, from, to, thisMove,
                        yDistanceAboveLimit, hDistanceAboveLimit)
                        || acceptsModernClientGroundVerticalModel(pData, from, to, thisMove,
                                yDistanceAboveLimit, hDistanceAboveLimit);
            case MODERN_CLIENT_GROUND:
                return acceptsModernClientGroundVerticalModel(pData, from, to, thisMove, yDistanceAboveLimit,
                        hDistanceAboveLimit);
            case GROUNDED_RECOVERY:
                return false;
            case BEDROCK_GROUNDED_COMBAT:
                return acceptsBedrockGroundedCombatVerticalModel(player, pData, from, to, thisMove, lastMove,
                        yDistanceAboveLimit, hDistanceAboveLimit);
            case MODERN_HALF_STEP:
                return acceptsModernHalfStepVerticalModel(player, pData, from, to, thisMove, lastMove,
                        yDistanceAboveLimit, hDistanceAboveLimit);
            case JUMP_CARRY:
                return acceptsJumpCarryVerticalModel(player, from, to, thisMove, lastMove,
                        yDistanceAboveLimit, hDistanceAboveLimit);
            case GROUND_JUMP_TINY:
            case GROUNDED_ITEM_RESYNC:
            case SERVER_VERTICAL_VELOCITY:
                return false;
            case GROUNDED_VERTICAL_VELOCITY:
                return acceptsGroundedVerticalVelocityVerticalModel(player, from, to, thisMove, lastMove,
                        yDistanceAboveLimit, hDistanceAboveLimit);
            case PARTIAL_SUPPORT:
                return acceptsPartialSupportVerticalModel(player, from, to, thisMove, lastMove,
                        yDistanceAboveLimit, hDistanceAboveLimit);
            case AIR_INERTIA:
                return acceptsAirInertiaVerticalModel(player, from, to, thisMove, lastMove,
                        yDistanceAboveLimit, hDistanceAboveLimit);
            case MODERN_VERTICAL_IMPULSE:
                return isModernVerticalImpulseVerticalGrace(player, pData, from, to, thisMove, yDistanceAboveLimit, hDistanceAboveLimit);
            case CURRENT_SERVER_VELOCITY:
                return acceptsCurrentServerVelocityVerticalModel(player, from, to, thisMove, lastMove,
                        yDistanceAboveLimit, hDistanceAboveLimit);
            case SETBACK_GRAVITY:
                return acceptsSetbackGravityVerticalModel(data, from, to, thisMove, lastMove,
                        yDistanceAboveLimit, hDistanceAboveLimit);
            case LEVITATION:
                return acceptsLevitationVerticalModel(player, thisMove, yDistanceAboveLimit, hDistanceAboveLimit);
            case QUEUED_VELOCITY:
                return isCurrentServerVelocityVerticalGrace(player, thisMove, yDistanceAboveLimit, hDistanceAboveLimit)
                        || isQueuedVelocityVerticalInertiaHandoffModel(player, data, from, to, thisMove, lastMove,
                                yDistanceAboveLimit, hDistanceAboveLimit)
                        || isQueuedVelocityVerticalPacketOrderModel(player, data, from, to, thisMove,
                                yDistanceAboveLimit, hDistanceAboveLimit);
            case COLLISION:
                return acceptsCollisionVerticalModel(player, from, to, thisMove, lastMove,
                        yDistanceAboveLimit, hDistanceAboveLimit);
            case LAST_INVALID_RESYNC:
                return acceptsLastInvalidResyncVerticalModel(player, from, to, thisMove, lastMove,
                        yDistanceAboveLimit, hDistanceAboveLimit);
            default:
                return false;
        }
    }

    private void applyHorizontalModelAllowance(final PlayerMoveData thisMove, final MovementModelBranch branch) {
        // The allowed vector is only aligned to the actual move after a model has already accepted this envelope.
        thisMove.xAllowedDistance = thisMove.xDistance;
        thisMove.zAllowedDistance = thisMove.zDistance;
        thisMove.hAllowedDistance = thisMove.hDistance;
        tags.add("model_" + branch.tag + "_h");
    }

    private void applyVerticalModelAllowance(final PlayerMoveData thisMove, final MovementModelBranch branch) {
        // The allowed Y is only aligned to the actual move after a model has already accepted this envelope.
        thisMove.yAllowedDistance = thisMove.yDistance;
        tags.add("model_" + branch.tag + "_y");
    }

    private boolean isWaterMovementContext(final PlayerLocation from, final PlayerLocation to,
                                           final PlayerMoveData thisMove) {
        return from.isInWater() || to.isInWater()
                || thisMove.from.inWater || thisMove.to.inWater || tags.contains("v_water");
    }

    private boolean isLavaMovementContext(final PlayerLocation from, final PlayerLocation to,
                                          final PlayerMoveData thisMove) {
        return from.isInLava() || to.isInLava()
                || thisMove.from.inLava || thisMove.to.inLava || tags.contains("v_lava");
    }

    private boolean isClimbableMovementContext(final PlayerLocation from, final PlayerLocation to,
                                               final PlayerMoveData thisMove) {
        return from.isOnClimbable() || to.isOnClimbable()
                || thisMove.from.onClimbable || thisMove.to.onClimbable || tags.contains("v_climbable");
    }

    private boolean isServerPositionJumpResyncContext(final IPlayerData pData,
                                                       final PlayerLocation from, final PlayerLocation to,
                                                       final PlayerMoveData thisMove,
                                                       final PlayerMoveData lastMove) {
        final NetData netData = pData.getGenericInstance(NetData.class);
        final long age = netData.getServerPositionJumpGraceAge(System.currentTimeMillis());
        // Teleport model: async/server-position jumps can leave one or two air packets attached to old movement history.
        final double horizontalLimit = getServerPositionJumpResyncHorizontalModelLimit(thisMove, lastMove);
        return age >= 0L
                && age <= SERVER_POSITION_JUMP_SURVIVALFLY_GRACE_MS
                && thisMove.hDistance <= horizontalLimit
                && Math.abs(thisMove.yDistance) <= SERVER_POSITION_JUMP_AIR_VERTICAL_MOVE_MODEL
                && !from.isInLiquid() && !to.isInLiquid()
                && !thisMove.from.inLiquid && !thisMove.to.inLiquid;
    }

    private boolean isBedrockGroundedCombatContext(final Player player, final IPlayerData pData,
                                                   final PlayerLocation from, final PlayerLocation to,
                                                   final PlayerMoveData thisMove) {
        return isBedrockPlayer(player, pData)
                && !Bridge1_9.isGliding(player)
                && thisMove.hDistance <= BEDROCK_GROUNDED_COMBAT_MOVE_GRACE
                && Math.abs(thisMove.yDistance) <= BEDROCK_GROUNDED_COMBAT_VERTICAL_MOVE_GRACE
                && !from.isInLiquid() && !to.isInLiquid()
                && !thisMove.from.inLiquid && !thisMove.to.inLiquid
                && isGroundedCombatMove(player, from, to, thisMove);
    }

    private boolean isBedrockStepContext(final Player player, final IPlayerData pData,
                                         final PlayerLocation from, final PlayerLocation to,
                                         final PlayerMoveData thisMove, final PlayerMoveData lastMove) {
        final double horizontalLimit = getBedrockStepHorizontalModelLimit(from, to, thisMove, lastMove);
        return isBedrockPlayer(player, pData)
                && !Bridge1_9.isGliding(player)
                && isGroundishStepMove(from, to, thisMove)
                && isStepBlockNear(from, to)
                && thisMove.hDistance <= horizontalLimit
                && (Math.abs(thisMove.yDistance - BEDROCK_HALF_STEP_VERTICAL_MOVE) <= BEDROCK_HALF_STEP_VERTICAL_EPSILON
                        || Math.abs(thisMove.yDistance) <= BEDROCK_STEP_VERTICAL_UNDERSHOOT_MOVE_GRACE)
                && !from.isInLiquid() && !to.isInLiquid()
                && !thisMove.from.inLiquid && !thisMove.to.inLiquid;
    }

    private boolean isGroundedVerticalVelocityContext(final Player player,
                                                      final PlayerLocation from, final PlayerLocation to,
                                                      final PlayerMoveData thisMove) {
        if (Bridge1_9.isGliding(player)
                || thisMove.hDistance > GROUNDED_VERTICAL_VELOCITY_MOVE_GRACE
                || Math.abs(thisMove.yDistance) > GROUNDED_VERTICAL_VELOCITY_MOVE_Y_GRACE
                || from.isInLiquid() || to.isInLiquid()
                || thisMove.from.inLiquid || thisMove.to.inLiquid) {
            return false;
        }
        final boolean groundish = isGroundishStepMove(from, to, thisMove);
        final double velocityY = player.getVelocity().getY();
        return groundish
                && velocityY > 0.30D && velocityY <= GROUNDED_VERTICAL_VELOCITY_MOVE_Y_GRACE
                && (thisMove.collidesHorizontally || thisMove.collideY || tags.contains("v_air"));
    }

    private boolean isServerVerticalVelocityContext(final Player player, final MovingData data,
                                                    final PlayerLocation from, final PlayerLocation to,
                                                    final PlayerMoveData thisMove) {
        return !Bridge1_9.isGliding(player)
                && !thisMove.verVelUsed.isEmpty()
                && data.getHorizontalVelocityTracker().hasQueued()
                && thisMove.yDistance > 0.0D
                && thisMove.yDistance <= SERVER_VERTICAL_VELOCITY_ASCEND_GRACE
                && thisMove.hDistance <= SERVER_VERTICAL_VELOCITY_HORIZONTAL_MOVE_GRACE
                && isGroundishStepMove(from, to, thisMove)
                && !from.isInLiquid() && !to.isInLiquid()
                && !thisMove.from.inLiquid && !thisMove.to.inLiquid;
    }

    private boolean isGroundJumpTinyContext(final PlayerMoveData thisMove) {
        return (tags.contains("jump_env") || tags.contains("bunnyhop"))
                && thisMove.yDistance > 0.0D
                && thisMove.yDistance <= GROUNDED_JUMP_VERTICAL_MOVE_GRACE
                && thisMove.hDistance <= GROUND_JUMP_TINY_HORIZONTAL_MOVE_GRACE;
    }

    private boolean isGroundedItemResyncContext(final PlayerMoveData thisMove) {
        return tags.contains("itemresync")
                && tags.contains("usingitem")
                && tags.contains("onground_env")
                && Math.abs(thisMove.yDistance) <= Magic.PREDICTION_EPSILON
                && thisMove.hDistance <= GROUNDED_ITEM_RESYNC_MOVE_GRACE;
    }

    private boolean isCurrentServerVelocityVerticalContext(final Player player,
                                                           final PlayerLocation from, final PlayerLocation to,
                                                           final PlayerMoveData thisMove) {
        return !Bridge1_9.isGliding(player)
                && Math.abs(thisMove.yDistance) > Magic.PREDICTION_EPSILON
                && Math.abs(thisMove.yDistance - player.getVelocity().getY()) <= CURRENT_SERVER_VELOCITY_VERTICAL_MATCH_GRACE
                && !from.isInLiquid() && !to.isInLiquid()
                && !thisMove.from.inLiquid && !thisMove.to.inLiquid;
    }

    private boolean isLevitationMovementContext(final Player player, final PlayerMoveData thisMove) {
        return !Double.isInfinite(Bridge1_9.getLevitationAmplifier(player))
                && Math.abs(thisMove.yDistance) <= LEVITATION_STALL_VERTICAL_GRACE;
    }

    private boolean isElytraEquippedTransitionContext(final Player player, final MovingData data,
                                                      final PlayerLocation from, final PlayerLocation to,
                                                      final PlayerMoveData thisMove, final PlayerMoveData lastMove) {
        return Bridge1_9.isWearingElytra(player)
                && !Bridge1_9.isGliding(player)
                && !from.isInLiquid() && !to.isInLiquid()
                && !thisMove.from.inLiquid && !thisMove.to.inLiquid
                && (isQueuedVelocityContext(data, thisMove)
                        || player.getVelocity().getY() > 0.10D
                        || lastMove.toIsValid && lastMove.yDistance > 0.25D
                        || isGroundishStepMove(from, to, thisMove));
    }

    private boolean isModernVerticalImpulseContext(final Player player, final IPlayerData pData,
                                                   final PlayerLocation from, final PlayerLocation to,
                                                   final PlayerMoveData thisMove) {
        return isModernMovementClient(pData)
                && !Bridge1_9.isGliding(player)
                && thisMove.yDistance > Magic.PREDICTION_EPSILON
                && hasModernVerticalImpulseSource(player, thisMove)
                && !from.isInLiquid() && !to.isInLiquid()
                && !thisMove.from.inLiquid && !thisMove.to.inLiquid;
    }

    private boolean isQueuedVelocityContext(final MovingData data, final PlayerMoveData thisMove) {
        return data.getHorizontalVelocityTracker().hasQueued()
                || !thisMove.verVelUsed.isEmpty()
                || tags.contains("hvel_current") || tags.contains("hvel");
    }

    private boolean isCollisionMovementContext(final Player player, final PlayerLocation from,
                                                final PlayerLocation to, final PlayerMoveData thisMove,
                                                final PlayerMoveData lastMove) {
        return hasCollisionSignal(thisMove)
                && !Bridge1_9.isGliding(player)
                && !from.isInLiquid() && !to.isInLiquid()
                && !thisMove.from.inLiquid && !thisMove.to.inLiquid
                && (isCollisionHorizontalSlideCandidate(thisMove)
                        || isCollisionLandingHorizontalCandidate(thisMove, lastMove)
                        || isCollisionQuantizedStepHorizontalCandidate(thisMove)
                        || isCollisionVerticalTruncationCandidate(thisMove)
                        || isCollisionVerticalCorrectionCandidate(thisMove));
    }

    private boolean hasCollisionSignal(final PlayerMoveData thisMove) {
        return thisMove.collideX || thisMove.collideY || thisMove.collideZ
                || thisMove.collidesHorizontally || thisMove.negligibleHorizontalCollision;
    }

    private boolean isCollisionHorizontalSlideCandidate(final PlayerMoveData thisMove) {
        return Math.abs(thisMove.yDistance) <= Magic.PREDICTION_EPSILON
                && thisMove.hDistance <= COLLISION_HORIZONTAL_SLIDE_MOVE_CAP;
    }

    private boolean isCollisionVerticalTruncationCandidate(final PlayerMoveData thisMove) {
        final double truncatedY = thisMove.yAllowedDistance - thisMove.yDistance;
        return thisMove.yDistance >= -Magic.PREDICTION_EPSILON
                && truncatedY >= -Magic.PREDICTION_EPSILON
                && truncatedY <= COLLISION_VERTICAL_TRUNCATION_MAX
                && thisMove.hDistance <= COLLISION_HORIZONTAL_SLIDE_MOVE_CAP;
    }

    private boolean isCollisionVerticalCorrectionCandidate(final PlayerMoveData thisMove) {
        return Math.abs(thisMove.yDistance) <= COLLISION_VERTICAL_CORRECTION_Y_MOVE_GRACE
                && thisMove.hDistance <= COLLISION_VERTICAL_CORRECTION_MOVE_GRACE;
    }

    private boolean isCollisionLandingHorizontalCandidate(final PlayerMoveData thisMove,
                                                          final PlayerMoveData lastMove) {
        if (!lastMove.toIsValid || !thisMove.collideY
                || thisMove.yDistance >= -Magic.PREDICTION_EPSILON
                || thisMove.hDistance > COLLISION_HORIZONTAL_SLIDE_MOVE_CAP) {
            return false;
        }
        final double gravityModel = getAirInertiaVerticalModel(lastMove);
        // Model cleanup: landing collision truncates the vertical fall tick but preserves normal air H inertia.
        return thisMove.yDistance >= gravityModel - COLLISION_VERTICAL_TRUNCATION_MAX;
    }

    private boolean isCollisionQuantizedStepHorizontalCandidate(final PlayerMoveData thisMove) {
        if (!thisMove.collideY
                || thisMove.yDistance <= Magic.PREDICTION_EPSILON
                || thisMove.yDistance > PARTIAL_SUPPORT_STEP_HEIGHT_MODEL
                || thisMove.hDistance > COLLISION_HORIZONTAL_SLIDE_MOVE_CAP) {
            return false;
        }
        final double units = thisMove.yDistance / PARTIAL_SUPPORT_VERTICAL_CLAMP_UNIT;
        // Model cleanup: modern collision can report a 1/16th-height Y correction while preserving ground H carry.
        return Math.abs(units - Math.round(units)) <= PARTIAL_SUPPORT_VERTICAL_CLAMP_EPSILON;
    }

    // Collision and invalid-history models: recover from collision-shape snaps without treating them as open-air movement.
    private boolean acceptsCollisionHorizontalModel(final Player player, final PlayerLocation from,
                                                    final PlayerLocation to, final PlayerMoveData thisMove,
                                                    final PlayerMoveData lastMove,
                                                    final double hDistanceAboveLimit) {
        if (!isCollisionMovementContext(player, from, to, thisMove, lastMove)) {
            return false;
        }
        if (isCollisionLandingHorizontalCandidate(thisMove, lastMove)) {
            final double limit = getAirInertiaHorizontalModel(thisMove, lastMove) + COLLISION_HORIZONTAL_SLIDE_RESIDUAL;
            if (thisMove.hDistance <= limit
                    && hDistanceAboveLimit <= getModelOverLimit(thisMove.hAllowedDistance,
                            limit, COLLISION_HORIZONTAL_SLIDE_RESIDUAL)) {
                tags.add("collision_landing_horizontal_model");
                return true;
            }
        }
        if (isCollisionQuantizedStepHorizontalCandidate(thisMove)) {
            final double limit = getCollisionHorizontalSlideModelLimit(thisMove);
            if (thisMove.hDistance <= limit
                    && hDistanceAboveLimit <= getModelOverLimit(thisMove.hAllowedDistance,
                            limit, COLLISION_HORIZONTAL_SLIDE_RESIDUAL)) {
                tags.add("collision_quantized_step_horizontal_model");
                return true;
            }
        }
        if (!isCollisionHorizontalSlideCandidate(thisMove)) {
            return false;
        }
        // False-flag model: a collision slide trims one axis while normal input still carries the other axis.
        final double limit = getCollisionHorizontalSlideModelLimit(thisMove);
        if (thisMove.hDistance <= limit
                && hDistanceAboveLimit <= getModelOverLimit(thisMove.hAllowedDistance,
                        limit, COLLISION_HORIZONTAL_SLIDE_RESIDUAL)) {
            tags.add("collision_horizontal_slide_model");
            return true;
        }
        tags.add("collision_horizontal_slide_model_miss");
        return false;
    }

    private double getCollisionHorizontalSlideModelLimit(final PlayerMoveData thisMove) {
        final double inputCarry = COLLISION_HORIZONTAL_SLIDE_INPUT_CARRY * getHorizontalInputScale(thisMove);
        return Math.min(COLLISION_HORIZONTAL_SLIDE_MOVE_CAP,
                Math.max(thisMove.hAllowedDistance, thisMove.hAllowedDistance + inputCarry
                        + COLLISION_HORIZONTAL_SLIDE_RESIDUAL));
    }

    private boolean acceptsCollisionVerticalModel(final Player player, final PlayerLocation from,
                                                  final PlayerLocation to, final PlayerMoveData thisMove,
                                                  final PlayerMoveData lastMove,
                                                  final double yDistanceAboveLimit,
                                                  final double hDistanceAboveLimit) {
        if (!isCollisionMovementContext(player, from, to, thisMove, lastMove)
                || hDistanceAboveLimit > COLLISION_VERTICAL_CORRECTION_HORIZONTAL_OVER_GRACE) {
            return false;
        }
        // False-flag model: block collision can truncate upward/step Y below the vanilla predicted Y.
        if (isCollisionVerticalTruncationCandidate(thisMove)
                && hDistanceAboveLimit <= COLLISION_VERTICAL_TRUNCATION_HORIZONTAL_OVER) {
            final double modelDelta = Math.abs(thisMove.yAllowedDistance - thisMove.yDistance);
            if (yDistanceAboveLimit <= modelDelta + Magic.PREDICTION_EPSILON) {
                tags.add("collision_vertical_truncation_model");
                return true;
            }
        }
        if (acceptsCollisionVerticalCorrectionModel(player, from, to, thisMove, lastMove,
                yDistanceAboveLimit, hDistanceAboveLimit)) {
            tags.add("collision_vertical_correction_model");
            return true;
        }
        tags.add("collision_vertical_truncation_model_miss");
        return false;
    }

    private boolean acceptsCollisionVerticalCorrectionModel(final Player player,
                                                           final PlayerLocation from, final PlayerLocation to,
                                                           final PlayerMoveData thisMove, final PlayerMoveData lastMove,
                                                           final double yDistanceAboveLimit,
                                                           final double hDistanceAboveLimit) {
        if (!isCollisionVerticalCorrectionCandidate(thisMove)
                || hDistanceAboveLimit > COLLISION_VERTICAL_CORRECTION_HORIZONTAL_OVER_GRACE
                || from.isInLiquid() || to.isInLiquid()
                || thisMove.from.inLiquid || thisMove.to.inLiquid) {
            return false;
        }
        final boolean yMatchesVelocity = Math.abs(thisMove.yDistance - player.getVelocity().getY())
                <= COLLISION_VERTICAL_CORRECTION_Y_MOVE_GRACE;
        if (lastMove.toIsValid && !yMatchesVelocity) {
            return false;
        }
        return yDistanceAboveLimit <= getModelOverLimit(thisMove.yAllowedDistance,
                player.getVelocity().getY(), COLLISION_VERTICAL_CORRECTION_Y_MOVE_GRACE);
    }

    private boolean isLastInvalidResyncContext(final Player player, final PlayerLocation from,
                                               final PlayerLocation to, final PlayerMoveData thisMove,
                                               final PlayerMoveData lastMove) {
        return !lastMove.toIsValid
                && !Bridge1_9.isGliding(player)
                && !from.isInLiquid() && !to.isInLiquid()
                && !thisMove.from.inLiquid && !thisMove.to.inLiquid
                && (isLastInvalidStandstillResyncModel(thisMove)
                        || isLastInvalidFirstJumpCandidate(thisMove)
                        || isLastInvalidVelocityResyncCandidate(player, thisMove)
                        || isLastInvalidGroundInputCandidate(thisMove)
                        || isLastInvalidVelocityHandoffCandidate(player, thisMove)
                        || isLastInvalidAirStallCandidate(player, thisMove)
                        || isLastInvalidJumpContinuationCandidate(thisMove));
    }

    private boolean isLastInvalidStandstillResyncModel(final PlayerMoveData thisMove) {
        return thisMove.multiMoveCount > 0
                && Math.abs(thisMove.yDistance) <= Magic.PREDICTION_EPSILON
                && thisMove.hDistance <= LAST_INVALID_STANDSTILL_HORIZONTAL_MOVE;
    }

    private boolean isLastInvalidFirstJumpCandidate(final PlayerMoveData thisMove) {
        // Model cleanup: after an invalid history gap, the next valid packet can be the vanilla first jump tick.
        return (tags.contains("jump_env") || thisMove.isJump)
                && Math.abs(thisMove.yDistance - LiftOffEnvelope.NORMAL.getJumpGain(0.0D))
                        <= LAST_INVALID_FIRST_JUMP_VERTICAL_EPSILON
                && thisMove.hDistance <= getLastInvalidJumpContinuationHorizontalModelLimit(thisMove)
                && getHorizontalInputScale(thisMove) > 0.0D;
    }

    private boolean isLastInvalidVelocityResyncCandidate(final Player player, final PlayerMoveData thisMove) {
        final Vector velocity = player.getVelocity();
        return Math.abs(thisMove.yDistance - velocity.getY()) <= LAST_INVALID_RESYNC_VERTICAL_MATCH
                && Math.abs(thisMove.yDistance) <= LAST_INVALID_RESYNC_MAX_VERTICAL_MOVE
                && thisMove.hDistance <= getLastInvalidResyncHorizontalModelLimit(player, thisMove);
    }

    private boolean isLastInvalidVelocityHandoffCandidate(final Player player, final PlayerMoveData thisMove) {
        final double expectedY = getLastInvalidVelocityHandoffVerticalModel(player);
        final double velocityY = player.getVelocity().getY();
        return Math.abs(thisMove.yDistance) <= LAST_INVALID_VELOCITY_HANDOFF_MAX_VERTICAL_MOVE
                && (Math.abs(thisMove.yDistance - expectedY) <= LAST_INVALID_VELOCITY_HANDOFF_VERTICAL_MATCH
                        || Math.abs(thisMove.yDistance - velocityY) <= LAST_INVALID_VELOCITY_HANDOFF_VERTICAL_MATCH)
                && thisMove.hDistance <= getLastInvalidResyncHorizontalModelLimit(player, thisMove);
    }

    private boolean isLastInvalidAirStallCandidate(final Player player, final PlayerMoveData thisMove) {
        // Model cleanup: invalid-history air packets can arrive between gravity ticks with near-zero Y motion.
        return Math.abs(thisMove.yDistance) <= LAST_INVALID_AIR_STALL_VERTICAL_MOVE
                && Math.abs(player.getVelocity().getY()) <= LAST_INVALID_AIR_STALL_SERVER_Y
                && thisMove.hDistance <= getLastInvalidResyncHorizontalModelLimit(player, thisMove);
    }

    private boolean isLastInvalidJumpContinuationCandidate(final PlayerMoveData thisMove) {
        return isLastInvalidNormalJumpContinuationCandidate(thisMove)
                || isLastInvalidLowJumpContinuationCandidate(thisMove);
    }

    private boolean isLastInvalidNormalJumpContinuationCandidate(final PlayerMoveData thisMove) {
        final double expectedY = getLastInvalidJumpContinuationVerticalModel();
        return thisMove.yDistance > Magic.PREDICTION_EPSILON
                && Math.abs(thisMove.yDistance - expectedY) <= LAST_INVALID_JUMP_CONTINUATION_VERTICAL_EPSILON
                && thisMove.hDistance <= getLastInvalidJumpContinuationHorizontalModelLimit(thisMove)
                && getHorizontalInputScale(thisMove) > 0.0D;
    }

    private boolean isLastInvalidLowJumpContinuationCandidate(final PlayerMoveData thisMove) {
        // Model cleanup: invalid-history short-hop packets keep jump input carry but cap Y to the low-jump envelope.
        return thisMove.yDistance >= LAST_INVALID_LOW_JUMP_CONTINUATION_VERTICAL_MIN
                && thisMove.yDistance <= LAST_INVALID_LOW_JUMP_CONTINUATION_VERTICAL_MAX
                && thisMove.hDistance <= getLastInvalidJumpContinuationHorizontalModelLimit(thisMove)
                && getHorizontalInputScale(thisMove) > 0.0D;
    }

    private boolean isLastInvalidGroundInputCandidate(final PlayerMoveData thisMove) {
        return Math.abs(thisMove.yDistance) <= Magic.PREDICTION_EPSILON
                && getHorizontalInputScale(thisMove) > 0.0D
                && thisMove.hDistance <= getLastInvalidGroundInputHorizontalModelLimit(thisMove);
    }

    private boolean acceptsLastInvalidResyncHorizontalModel(final Player player, final PlayerLocation from,
                                                            final PlayerLocation to,
                                                            final PlayerMoveData thisMove,
                                                            final PlayerMoveData lastMove,
                                                            final double hDistanceAboveLimit) {
        if (!isLastInvalidResyncContext(player, from, to, thisMove, lastMove)) {
            return false;
        }
        if (isLastInvalidStandstillResyncModel(thisMove)) {
            tags.add("last_invalid_standstill_resync_horizontal_model");
            return true;
        }
        if (isLastInvalidFirstJumpCandidate(thisMove)) {
            final double limit = getLastInvalidJumpContinuationHorizontalModelLimit(thisMove);
            if (hDistanceAboveLimit <= getModelOverLimit(thisMove.hAllowedDistance,
                    limit, LAST_INVALID_JUMP_CONTINUATION_HORIZONTAL_RESIDUAL)) {
                tags.add("last_invalid_first_jump_horizontal_model");
                return true;
            }
        }
        if (isLastInvalidJumpContinuationCandidate(thisMove)) {
            final double limit = getLastInvalidJumpContinuationHorizontalModelLimit(thisMove);
            if (hDistanceAboveLimit <= getModelOverLimit(thisMove.hAllowedDistance,
                    limit, LAST_INVALID_JUMP_CONTINUATION_HORIZONTAL_RESIDUAL)) {
                tags.add("last_invalid_jump_continuation_horizontal_model");
                return true;
            }
        }
        if (isLastInvalidGroundInputCandidate(thisMove)) {
            final double limit = getLastInvalidGroundInputHorizontalModelLimit(thisMove);
            if (hDistanceAboveLimit <= getModelOverLimit(thisMove.hAllowedDistance,
                    limit, LAST_INVALID_GROUND_INPUT_HORIZONTAL_RESIDUAL)) {
                tags.add("last_invalid_ground_input_horizontal_model");
                return true;
            }
        }
        final double limit = getLastInvalidResyncHorizontalModelLimit(player, thisMove);
        if ((isLastInvalidVelocityResyncCandidate(player, thisMove)
                || isLastInvalidVelocityHandoffCandidate(player, thisMove)
                || isLastInvalidAirStallCandidate(player, thisMove))
                && hDistanceAboveLimit <= getModelOverLimit(thisMove.hAllowedDistance,
                        limit, LAST_INVALID_RESYNC_HORIZONTAL_RESIDUAL)) {
            tags.add("last_invalid_velocity_resync_horizontal_model");
            return true;
        }
        tags.add("last_invalid_resync_horizontal_model_miss");
        return false;
    }

    private boolean acceptsLastInvalidResyncVerticalModel(final Player player, final PlayerLocation from,
                                                          final PlayerLocation to,
                                                          final PlayerMoveData thisMove,
                                                          final PlayerMoveData lastMove,
                                                          final double yDistanceAboveLimit,
                                                          final double hDistanceAboveLimit) {
        if (!isLastInvalidResyncContext(player, from, to, thisMove, lastMove)) {
            return false;
        }
        if (isLastInvalidStandstillResyncModel(thisMove)
                && yDistanceAboveLimit <= LAST_INVALID_STANDSTILL_VERTICAL_OVER) {
            tags.add("last_invalid_standstill_resync_vertical_model");
            return true;
        }
        if (isLastInvalidFirstJumpCandidate(thisMove)) {
            final double horizontalLimit = getLastInvalidJumpContinuationHorizontalModelLimit(thisMove);
            if (thisMove.hDistance <= horizontalLimit
                    && hDistanceAboveLimit <= getModelOverLimit(thisMove.hAllowedDistance,
                            horizontalLimit, LAST_INVALID_JUMP_CONTINUATION_HORIZONTAL_RESIDUAL)
                    && yDistanceAboveLimit <= getModelOverLimit(thisMove.yAllowedDistance,
                            LiftOffEnvelope.NORMAL.getJumpGain(0.0D), LAST_INVALID_FIRST_JUMP_VERTICAL_EPSILON)) {
                tags.add("last_invalid_first_jump_vertical_model");
                return true;
            }
        }
        if (isLastInvalidJumpContinuationCandidate(thisMove)) {
            final double horizontalLimit = getLastInvalidJumpContinuationHorizontalModelLimit(thisMove);
            final double verticalModel = getLastInvalidJumpContinuationVerticalModel(thisMove);
            final double verticalEpsilon = isLastInvalidLowJumpContinuationCandidate(thisMove)
                    ? LAST_INVALID_LOW_JUMP_CONTINUATION_VERTICAL_EPSILON : LAST_INVALID_JUMP_CONTINUATION_VERTICAL_EPSILON;
            if (thisMove.hDistance <= horizontalLimit
                    && hDistanceAboveLimit <= getModelOverLimit(thisMove.hAllowedDistance,
                            horizontalLimit, LAST_INVALID_JUMP_CONTINUATION_HORIZONTAL_RESIDUAL)
                    && yDistanceAboveLimit <= getModelOverLimit(thisMove.yAllowedDistance,
                            verticalModel, verticalEpsilon)) {
                tags.add("last_invalid_jump_continuation_vertical_model");
                return true;
            }
        }
        if (isLastInvalidGroundInputCandidate(thisMove)) {
            final double groundInputLimit = getLastInvalidGroundInputHorizontalModelLimit(thisMove);
            if (hDistanceAboveLimit <= getModelOverLimit(thisMove.hAllowedDistance,
                    groundInputLimit, LAST_INVALID_GROUND_INPUT_HORIZONTAL_RESIDUAL)
                    && yDistanceAboveLimit <= Math.abs(thisMove.yAllowedDistance) + LAST_INVALID_AIR_STALL_VERTICAL_OVER) {
                tags.add("last_invalid_ground_input_vertical_stall_model");
                return true;
            }
        }
        final Vector velocity = player.getVelocity();
        final double horizontalLimit = getLastInvalidResyncHorizontalModelLimit(player, thisMove);
        final double verticalDelta = Math.abs(thisMove.yAllowedDistance - velocity.getY());
        if (Math.abs(thisMove.yDistance - velocity.getY()) <= LAST_INVALID_RESYNC_VERTICAL_MATCH
                && Math.abs(thisMove.yDistance) <= LAST_INVALID_RESYNC_MAX_VERTICAL_MOVE
                && thisMove.hDistance <= horizontalLimit
                && hDistanceAboveLimit <= getModelOverLimit(thisMove.hAllowedDistance,
                        horizontalLimit, LAST_INVALID_RESYNC_HORIZONTAL_RESIDUAL)
                && yDistanceAboveLimit <= verticalDelta + LAST_INVALID_RESYNC_VERTICAL_MATCH) {
            tags.add("last_invalid_velocity_resync_vertical_model");
            return true;
        }
        if (isLastInvalidVelocityHandoffCandidate(player, thisMove)
                && hDistanceAboveLimit <= getModelOverLimit(thisMove.hAllowedDistance,
                        horizontalLimit, LAST_INVALID_RESYNC_HORIZONTAL_RESIDUAL)
                && yDistanceAboveLimit <= Math.abs(thisMove.yAllowedDistance
                        - getLastInvalidVelocityHandoffVerticalModel(player, thisMove))
                        + LAST_INVALID_VELOCITY_HANDOFF_VERTICAL_MATCH) {
            tags.add("last_invalid_velocity_handoff_vertical_model");
            return true;
        }
        if (isLastInvalidAirStallCandidate(player, thisMove)
                && hDistanceAboveLimit <= getModelOverLimit(thisMove.hAllowedDistance,
                        horizontalLimit, LAST_INVALID_RESYNC_HORIZONTAL_RESIDUAL)
                && yDistanceAboveLimit <= Math.abs(thisMove.yAllowedDistance) + LAST_INVALID_AIR_STALL_VERTICAL_MOVE) {
            tags.add("last_invalid_air_stall_vertical_model");
            return true;
        }
        tags.add("last_invalid_resync_vertical_model_miss");
        return false;
    }

    private double getLastInvalidResyncHorizontalModelLimit(final Player player, final PlayerMoveData thisMove) {
        final Vector velocity = player.getVelocity();
        final double velocityH = MathUtil.dist(velocity.getX(), velocity.getZ());
        final double inputCarry = Math.max(LAST_INVALID_RESYNC_HORIZONTAL_INPUT_CARRY * getHorizontalInputScale(thisMove),
                playerInputHorizontalCarry(thisMove));
        return Math.min(LAST_INVALID_RESYNC_MAX_HORIZONTAL_MOVE,
                Math.max(thisMove.hAllowedDistance,
                        velocityH + inputCarry + LAST_INVALID_RESYNC_HORIZONTAL_RESIDUAL));
    }

    private double getLastInvalidJumpContinuationVerticalModel() {
        return (LiftOffEnvelope.NORMAL.getJumpGain(0.0D) - Magic.DEFAULT_GRAVITY) * Magic.FRICTION_MEDIUM_AIR;
    }

    private double getLastInvalidJumpContinuationVerticalModel(final PlayerMoveData thisMove) {
        return isLastInvalidLowJumpContinuationCandidate(thisMove)
                ? LAST_INVALID_LOW_JUMP_CONTINUATION_VERTICAL_MAX : getLastInvalidJumpContinuationVerticalModel();
    }

    private double getLastInvalidVelocityHandoffVerticalModel(final Player player) {
        final double velocityY = player.getVelocity().getY();
        final double previousGravityTick = (velocityY + Magic.DEFAULT_GRAVITY) / Magic.FRICTION_MEDIUM_AIR;
        final double nextGravityTick = (velocityY - Magic.DEFAULT_GRAVITY) * Magic.FRICTION_MEDIUM_AIR;
        return Math.abs(previousGravityTick) < Math.abs(nextGravityTick) ? previousGravityTick : nextGravityTick;
    }

    private double getLastInvalidVelocityHandoffVerticalModel(final Player player,
                                                              final PlayerMoveData thisMove) {
        final double velocityY = player.getVelocity().getY();
        final double gravityModel = getLastInvalidVelocityHandoffVerticalModel(player);
        return Math.abs(thisMove.yDistance - velocityY) <= Math.abs(thisMove.yDistance - gravityModel)
                ? velocityY : gravityModel;
    }

    private double getLastInvalidJumpContinuationHorizontalModelLimit(final PlayerMoveData thisMove) {
        final double inputCarry = LAST_INVALID_JUMP_CONTINUATION_HORIZONTAL_INPUT_CARRY * getHorizontalInputScale(thisMove);
        return Math.min(LAST_INVALID_JUMP_CONTINUATION_HORIZONTAL_CAP,
                thisMove.hAllowedDistance + inputCarry + LAST_INVALID_JUMP_CONTINUATION_HORIZONTAL_RESIDUAL);
    }

    private double getLastInvalidGroundInputHorizontalModelLimit(final PlayerMoveData thisMove) {
        // Model cleanup: after an invalid history gap, one grounded input packet can retain normal walk carry.
        return Math.min(LAST_INVALID_GROUND_INPUT_HORIZONTAL_CAP,
                playerStepHorizontalModel(thisMove) + LAST_INVALID_GROUND_INPUT_HORIZONTAL_RESIDUAL);
    }

    // Elytra and firework models: compare gliding packets to velocity, look vector, and launch-energy envelopes.
    private boolean acceptsGlidingHorizontalVelocityModel(final Player player, final MovingData data,
                                                          final PlayerLocation from, final PlayerLocation to,
                                                          final PlayerMoveData thisMove,
                                                          final double hDistanceAboveLimit) {
        if (!Bridge1_9.isGliding(player)
                || !tags.contains(SurvivalFlyTags.GLIDE_HORIZONTAL_PREDICTION_MISS)
                || thisMove.from.inLiquid || thisMove.to.inLiquid) {
            return false;
        }
        final double limit = getGlidingHorizontalVelocityModelLimit(player, data, thisMove);
        if (thisMove.hDistance > limit
                || hDistanceAboveLimit > getModelOverLimit(thisMove.hAllowedDistance,
                        limit, GLIDING_HORIZONTAL_PRECISION_GRACE)) {
            tags.add("glide_horizontal_velocity_model_miss");
            return false;
        }
        if (currentGlidingActualVelocityMatches(player, thisMove)) {
            tags.add("glide_current_velocity_horizontal_model");
            return true;
        }
        if (turningCurrentGlidingVelocityMatches(player, from, to, thisMove)) {
            tags.add("glide_current_velocity_turn_horizontal_model");
            return true;
        }
        if (splitCurrentGlidingVelocityMagnitudeCovers(player, thisMove)) {
            tags.add("glide_split_velocity_horizontal_model");
            return true;
        }
        if (queuedGlidingVelocityMatches(data, thisMove)) {
            tags.add("glide_queued_velocity_horizontal_model");
            return true;
        }
        if (turningGlidingVelocityMatches(player, data, from, to, thisMove)) {
            tags.add("glide_turning_velocity_horizontal_model");
            return true;
        }
        tags.add("glide_horizontal_velocity_vector_miss");
        return false;
    }

    private boolean acceptsGlidingSteepDiveEnergyModel(final Player player, final PlayerMoveData thisMove,
                                                       final double hDistanceAboveLimit) {
        if (!Bridge1_9.isGliding(player)
                || !tags.contains("glide_pitch_down_steep")
                || !tags.contains(SurvivalFlyTags.GLIDE_HORIZONTAL_PREDICTION_MISS)
                || !tags.contains(SurvivalFlyTags.GLIDE_VERTICAL_ACTUAL_ABOVE_MODEL)
                || thisMove.yDistance >= -Magic.GRAVITY_MAX
                || thisMove.from.inLiquid || thisMove.to.inLiquid) {
            return false;
        }
        // Elytra model: steep dives can preserve total speed while redistributing too much into H for the axis model.
        final double modeledEnergy = MathUtil.dist(thisMove.hAllowedDistance, thisMove.yAllowedDistance);
        final double horizontalLimitSq = modeledEnergy * modeledEnergy - thisMove.yDistance * thisMove.yDistance;
        if (horizontalLimitSq <= 0.0D) {
            return false;
        }
        final double horizontalLimit = Math.sqrt(horizontalLimitSq) + GLIDING_STEEP_DIVE_ENERGY_EPSILON;
        if (thisMove.hDistance <= horizontalLimit
                && hDistanceAboveLimit <= getModelOverLimit(thisMove.hAllowedDistance,
                        horizontalLimit, GLIDING_HORIZONTAL_PRECISION_GRACE)) {
            tags.add("glide_steep_dive_energy_model");
            return true;
        }
        tags.add("glide_steep_dive_energy_model_miss");
        return false;
    }

    private double getGlidingHorizontalVelocityModelLimit(final Player player, final MovingData data,
                                                         final PlayerMoveData thisMove) {
        // Elytra model: current/queued velocity can be one packet ahead of the vanilla glide prediction.
        // The residual remains because packet ordering for velocity handoff is not perfectly modelable on Folia.
        final Vector velocity = player.getVelocity();
        double limit = Math.max(thisMove.hAllowedDistance, MathUtil.dist(velocity.getX(), velocity.getZ()));
        final double[] queued = getQueuedGlidingVelocity(data, thisMove);
        if (queued != null) {
            limit = Math.max(limit, MathUtil.dist(queued[0], queued[1]));
            // Elytra model: Folia can expose the prior current velocity and the next queued boost in the same move.
            limit = Math.max(limit, MathUtil.dist(velocity.getX() + queued[0], velocity.getZ() + queued[1])
                    + GLIDING_CURRENT_VELOCITY_HORIZONTAL_AMOUNT_GRACE);
        }
        return limit + GLIDING_CURRENT_VELOCITY_HORIZONTAL_PERPENDICULAR_GRACE;
    }

    private boolean currentGlidingActualVelocityMatches(final Player player, final PlayerMoveData thisMove) {
        final Vector velocity = player.getVelocity();
        return horizontalVelocityVectorMatches(velocity.getX(), velocity.getZ(), thisMove.xDistance, thisMove.zDistance,
                GLIDING_CURRENT_VELOCITY_HORIZONTAL_AMOUNT_GRACE,
                GLIDING_CURRENT_VELOCITY_HORIZONTAL_PERPENDICULAR_GRACE);
    }

    private boolean queuedGlidingVelocityMatches(final MovingData data, final PlayerMoveData thisMove) {
        final double[] queued = getQueuedGlidingVelocity(data, thisMove);
        if (queued == null) {
            return false;
        }
        if (!horizontalVelocityVectorMatches(queued[0], queued[1], thisMove.xDistance, thisMove.zDistance,
                GLIDING_CURRENT_VELOCITY_HORIZONTAL_AMOUNT_GRACE,
                GLIDING_CURRENT_VELOCITY_HORIZONTAL_PERPENDICULAR_GRACE)) {
            return false;
        }
        data.getHorizontalVelocityTracker().use((int) queued[2]);
        return true;
    }

    private boolean turningGlidingVelocityMatches(final Player player, final MovingData data,
                                                  final PlayerLocation from, final PlayerLocation to,
                                                  final PlayerMoveData thisMove) {
        final double[] queued = getQueuedGlidingVelocity(data, thisMove);
        if (queued == null) {
            return false;
        }
        final Vector velocity = player.getVelocity();
        final double modelX = velocity.getX() + queued[0];
        final double modelZ = velocity.getZ() + queued[1];
        final double yawTurn = Math.min(90.0D,
                Math.abs(getYawDelta(from.getYaw(), to.getYaw())) + GLIDING_CURRENT_VELOCITY_TURN_YAW_EXTRA);
        final double turnPerpendicular = MathUtil.dist(modelX, modelZ) * Math.sin(yawTurn * TrigUtil.toRadians)
                + GLIDING_CURRENT_VELOCITY_HORIZONTAL_PERPENDICULAR_GRACE;
        if (!horizontalVelocityVectorMatches(modelX, modelZ, thisMove.xDistance, thisMove.zDistance,
                GLIDING_CURRENT_VELOCITY_HORIZONTAL_AMOUNT_GRACE, turnPerpendicular)) {
            return false;
        }
        data.getHorizontalVelocityTracker().use((int) queued[2]);
        return true;
    }

    private boolean turningCurrentGlidingVelocityMatches(final Player player, final PlayerLocation from,
                                                        final PlayerLocation to,
                                                        final PlayerMoveData thisMove) {
        final Vector velocity = player.getVelocity();
        final double velocityH = MathUtil.dist(velocity.getX(), velocity.getZ());
        if (velocityH <= Magic.PREDICTION_EPSILON) {
            return false;
        }
        final double yawTurn = Math.min(90.0D,
                Math.abs(getYawDelta(from.getYaw(), to.getYaw())) + GLIDING_CURRENT_VELOCITY_TURN_YAW_EXTRA);
        final double turnPerpendicular = velocityH * Math.sin(yawTurn * TrigUtil.toRadians)
                + GLIDING_CURRENT_VELOCITY_HORIZONTAL_PERPENDICULAR_GRACE;
        return horizontalVelocityVectorMatches(velocity.getX(), velocity.getZ(),
                thisMove.xDistance, thisMove.zDistance,
                GLIDING_CURRENT_VELOCITY_HORIZONTAL_AMOUNT_GRACE, turnPerpendicular);
    }

    private boolean splitCurrentGlidingVelocityMagnitudeCovers(final Player player,
                                                              final PlayerMoveData thisMove) {
        if (thisMove.multiMoveCount <= 0) {
            return false;
        }
        final Vector velocity = player.getVelocity();
        final double velocityH = MathUtil.dist(velocity.getX(), velocity.getZ());
        return velocityH > Magic.PREDICTION_EPSILON
                && thisMove.hDistance <= velocityH + GLIDING_CURRENT_VELOCITY_HORIZONTAL_AMOUNT_GRACE;
    }

    private double[] getQueuedGlidingVelocity(final MovingData data, final PlayerMoveData thisMove) {
        final List<PairEntry> queued = data.getHorizontalVelocityTracker().peekCovering(thisMove.xDistance, thisMove.zDistance,
                1, Integer.MAX_VALUE, GLIDING_CURRENT_VELOCITY_HORIZONTAL_PERPENDICULAR_GRACE);
        if (queued.isEmpty()) {
            return null;
        }
        double queuedX = 0.0D;
        double queuedZ = 0.0D;
        final int tick = queued.get(0).tick;
        for (final PairEntry entry : queued) {
            if (entry.tick == tick) {
                queuedX += entry.x;
                queuedZ += entry.z;
            }
        }
        return new double[] { queuedX, queuedZ, tick };
    }

    private boolean horizontalVelocityVectorMatches(final double velocityX, final double velocityZ,
                                                    final double actualX, final double actualZ,
                                                    final double amountResidual,
                                                    final double perpendicularResidual) {
        final double velocitySq = velocityX * velocityX + velocityZ * velocityZ;
        if (velocitySq <= Magic.PREDICTION_EPSILON * Magic.PREDICTION_EPSILON) {
            return false;
        }
        final double actualSq = actualX * actualX + actualZ * actualZ;
        if (actualSq <= Magic.PREDICTION_EPSILON * Magic.PREDICTION_EPSILON) {
            return false;
        }
        final double dot = velocityX * actualX + velocityZ * actualZ;
        if (dot < -Magic.PREDICTION_EPSILON) {
            return false;
        }
        final double velocityAmount = Math.sqrt(velocitySq);
        final double actualAmount = Math.sqrt(actualSq);
        if (actualAmount > velocityAmount + amountResidual) {
            return false;
        }
        final double perpendicular = Math.abs(velocityX * actualZ - velocityZ * actualX) / velocityAmount;
        return perpendicular <= perpendicularResidual;
    }

    private boolean acceptsElytraLandingInertiaHorizontalModel(final Player player, final MovingData data,
                                                               final PlayerLocation from, final PlayerLocation to,
                                                               final PlayerMoveData thisMove,
                                                               final PlayerMoveData lastMove,
                                                               final double hDistanceAboveLimit,
                                                               final boolean requireGliding,
                                                               final boolean requireFirework,
                                                               final String acceptedTag) {
        if (!isElytraLandingInertiaContext(player, data, from, to, thisMove, lastMove,
                requireGliding, requireFirework)) {
            return false;
        }
        final double horizontalLimit = getElytraLandingInertiaHorizontalModelLimit(player, data, from, to,
                thisMove, lastMove);
        if (thisMove.hDistance > horizontalLimit
                || hDistanceAboveLimit > getModelOverLimit(thisMove.hAllowedDistance,
                        horizontalLimit, ELYTRA_LANDING_INERTIA_HORIZONTAL_RESIDUAL)) {
            tags.add(acceptedTag + "_limit_miss");
            return false;
        }
        if (horizontalLookDirectionMatches(player, to, thisMove, horizontalLimit,
                ELYTRA_LANDING_INERTIA_HORIZONTAL_RESIDUAL,
                ELYTRA_LANDING_INERTIA_PERPENDICULAR_RESIDUAL)) {
            tags.add(acceptedTag + "_look_vector");
            tags.add(acceptedTag);
            return true;
        }
        if (horizontalLandingInertiaVectorMatches(player, data, thisMove, lastMove, acceptedTag)) {
            tags.add(acceptedTag);
            return true;
        }
        tags.add(acceptedTag + "_vector_miss");
        return false;
    }

    private boolean horizontalLandingInertiaVectorMatches(final Player player, final MovingData data,
                                                          final PlayerMoveData thisMove,
                                                          final PlayerMoveData lastMove,
                                                          final String acceptedTag) {
        /*
         * Elytra landing model: contact with ground, lanterns, carpets, slabs, or
         * similar support shapes keeps the incoming glide vector for a packet.
         * It should not be forced to match the player's current look direction.
         */
        final Vector velocity = player.getVelocity();
        if (horizontalVelocityVectorMatches(velocity.getX(), velocity.getZ(), thisMove.xDistance, thisMove.zDistance,
                ELYTRA_LANDING_INERTIA_HORIZONTAL_RESIDUAL,
                ELYTRA_LANDING_INERTIA_PERPENDICULAR_RESIDUAL)) {
            tags.add(acceptedTag + "_current_velocity_vector");
            return true;
        }
        if (lastMove.toIsValid
                && horizontalVelocityVectorMatches(lastMove.xDistance, lastMove.zDistance,
                        thisMove.xDistance, thisMove.zDistance,
                        ELYTRA_LANDING_INERTIA_HORIZONTAL_RESIDUAL,
                        ELYTRA_LANDING_INERTIA_PERPENDICULAR_RESIDUAL)) {
            tags.add(acceptedTag + "_last_move_vector");
            return true;
        }
        if (queuedGlidingVelocityMatches(data, thisMove)) {
            tags.add(acceptedTag + "_queued_velocity_vector");
            return true;
        }
        return false;
    }

    private boolean isElytraLandingInertiaContext(final Player player, final MovingData data,
                                                  final PlayerLocation from, final PlayerLocation to,
                                                  final PlayerMoveData thisMove,
                                                  final PlayerMoveData lastMove,
                                                  final boolean requireGliding,
                                                  final boolean requireFirework) {
        final boolean gliding = Bridge1_9.isGliding(player);
        if (requireGliding != gliding
                || !gliding && !Bridge1_9.isWearingElytra(player)
                || requireFirework && data.fireworksBoostDuration <= 0
                || from.isInLiquid() || to.isInLiquid()
                || thisMove.from.inLiquid || thisMove.to.inLiquid
                || Math.abs(thisMove.yDistance) > ELYTRA_LANDING_INERTIA_MAX_VERTICAL_MOVE) {
            return false;
        }
        final boolean partialSupport = isPartialSupportNear(from, to) || isPartialSupportLandingBlock(to);
        final boolean landingContact = isGroundishStepMove(from, to, thisMove)
                || thisMove.collideY
                || partialSupport && getPartialSupportVerticalClampModel(from, to, thisMove.yDistance) > 0.0D
                || partialSupport && getPartialSupportLandingClampFraction(to) >= 0.0D;
        if (!landingContact && !partialSupport) {
            return false;
        }
        final Vector look = TrigUtil.getLookingDirection(to, player);
        if (MathUtil.dist(look.getX(), look.getZ()) <= Magic.PREDICTION_EPSILON) {
            return false;
        }
        final double velocityH = MathUtil.dist(player.getVelocity().getX(), player.getVelocity().getZ());
        final boolean knownInertiaSource = gliding
                || data.fireworksBoostDuration > 0
                || lastMove.toIsValid && lastMove.hDistance >= ELYTRA_LANDING_INERTIA_MIN_LAST_HORIZONTAL
                || !lastMove.toIsValid && partialSupport
                        && (data.getHorizontalVelocityTracker().hasQueued()
                                || velocityH >= ELYTRA_EQUIPPED_VELOCITY_HANDOFF_HORIZONTAL_INPUT_CARRY
                                || thisMove.hDistance >= ELYTRA_LANDING_INERTIA_MIN_LAST_HORIZONTAL);
        return knownInertiaSource && getHorizontalInputScale(thisMove) > 0.0D;
    }

    private double getElytraLandingInertiaHorizontalModelLimit(final Player player, final MovingData data,
                                                               final PlayerLocation from, final PlayerLocation to,
                                                               final PlayerMoveData thisMove,
                                                               final PlayerMoveData lastMove) {
        final Vector look = TrigUtil.getLookingDirection(to, player);
        final double lookH = MathUtil.dist(look.getX(), look.getZ());
        final Vector velocity = player.getVelocity();
        final double velocityH = MathUtil.dist(velocity.getX(), velocity.getZ());
        final double lastH = lastMove.toIsValid ? lastMove.hDistance : 0.0D;
        final double supportCarry = isPartialSupportNear(from, to) || isPartialSupportLandingBlock(to)
                ? Math.max(PARTIAL_SUPPORT_STEP_HEIGHT_MODEL, getPartialSupportStepHeightModel(from, to))
                : ELYTRA_LANDING_INERTIA_GENERAL_SUPPORT_CARRY;
        double limit = Math.max(thisMove.hAllowedDistance, Math.max(velocityH, lastH))
                + playerInputHorizontalCarry(thisMove) + ELYTRA_LANDING_INERTIA_HORIZONTAL_RESIDUAL;
        // Elytra landing model: when glide/firework motion hits a support shape, preserve look-directed inertia through the contact packet.
        limit = Math.max(limit, lookH + supportCarry + playerInputHorizontalCarry(thisMove)
                + ELYTRA_LANDING_INERTIA_HORIZONTAL_RESIDUAL);
        if (data.fireworksBoostDuration > 0) {
            final double[] fireworkModel = Bridge1_9.isGliding(player)
                    ? getFireworkPacketOrderModelVector(player, data, to, thisMove, 1)
                    : getElytraEquippedFireworkModelVector(player, data, to, thisMove, lastMove);
            limit = Math.max(limit, MathUtil.dist(fireworkModel[0], fireworkModel[2])
                    + supportCarry + ELYTRA_LANDING_INERTIA_HORIZONTAL_RESIDUAL);
        }
        return Math.min(ELYTRA_LANDING_INERTIA_MAX_HORIZONTAL_MOVE, limit);
    }

    private boolean acceptsElytraEquippedPartialSupportVerticalModel(final Player player,
                                                                     final PlayerLocation from, final PlayerLocation to,
                                                                     final PlayerMoveData thisMove,
                                                                     final PlayerMoveData lastMove,
                                                                     final double yDistanceAboveLimit,
                                                                     final double hDistanceAboveLimit) {
        if (!Bridge1_9.isWearingElytra(player) || Bridge1_9.isGliding(player)) {
            return false;
        }
        // Elytra model: wearing elytra changes inertia, not the lantern/carpet/slab collision shape.
        if (acceptsPartialSupportVerticalModel(player, from, to, thisMove, lastMove,
                yDistanceAboveLimit, hDistanceAboveLimit)) {
            tags.add("elytra_equipped_partial_support_vertical_model");
            return true;
        }
        return false;
    }

    private boolean acceptsElytraEquippedPartialSupportHorizontalModel(final Player player,
                                                                       final PlayerLocation from, final PlayerLocation to,
                                                                       final PlayerMoveData thisMove,
                                                                       final PlayerMoveData lastMove,
                                                                       final double hDistanceAboveLimit) {
        if (!Bridge1_9.isWearingElytra(player) || Bridge1_9.isGliding(player)) {
            return false;
        }
        // Elytra model: thin-support geometry is shared with normal movement; only the inertia source changes.
        if (acceptsPartialSupportHorizontalModel(from, to, thisMove, lastMove, hDistanceAboveLimit)) {
            tags.add("elytra_equipped_partial_support_horizontal_model");
            return true;
        }
        return false;
    }

    private boolean horizontalLookDirectionMatches(final Player player, final PlayerLocation to,
                                                   final PlayerMoveData thisMove, final double horizontalLimit,
                                                   final double amountResidual,
                                                   final double perpendicularResidual) {
        final Vector look = TrigUtil.getLookingDirection(to, player);
        final double lookH = MathUtil.dist(look.getX(), look.getZ());
        if (lookH <= Magic.PREDICTION_EPSILON) {
            return false;
        }
        final double modelX = look.getX() / lookH * horizontalLimit;
        final double modelZ = look.getZ() / lookH * horizontalLimit;
        return horizontalVelocityVectorMatches(modelX, modelZ, thisMove.xDistance, thisMove.zDistance,
                amountResidual, perpendicularResidual);
    }

    // Liquid models: swimming and lava movement use liquid input acceleration plus current/queued server velocity.
    private boolean acceptsWaterHorizontalModel(final Player player, final MovingData data,
                                                final PlayerLocation from,
                                                final PlayerLocation to, final PlayerMoveData thisMove,
                                                final double hDistanceAboveLimit,
                                                final boolean requireDolphinGrace) {
        if (!isWaterMovementContext(from, to, thisMove)) {
            return false;
        }
        if (requireDolphinGrace) {
            if (Double.isInfinite(Bridge1_13.getDolphinGraceAmplifier(player))) {
                return false;
            }
            final double limit = getWaterHorizontalModelLimit(player, thisMove, true);
            if (thisMove.hDistance <= limit
                    && hDistanceAboveLimit <= getModelOverLimit(thisMove.hAllowedDistance, limit, WATER_HORIZONTAL_MODEL_EPSILON)) {
                tags.add("water_dolphin_horizontal_model");
                return true;
            }
            return acceptsWaterQueuedVelocityHorizontalModel(data, thisMove, hDistanceAboveLimit);
        }
        final double limit = getWaterHorizontalModelLimit(player, thisMove, false);
        if (thisMove.hDistance <= limit
                && hDistanceAboveLimit <= getModelOverLimit(thisMove.hAllowedDistance, limit, WATER_HORIZONTAL_MODEL_EPSILON)) {
            tags.add("water_current_horizontal_model");
            return true;
        }
        if (acceptsWaterCurrentVelocityHorizontalModel(player, thisMove, hDistanceAboveLimit)) {
            tags.add("water_current_velocity_horizontal_model");
            return true;
        }
        if (acceptsWaterImplicitSwimHorizontalModel(thisMove, hDistanceAboveLimit)) {
            tags.add("water_implicit_swim_horizontal_model");
            return true;
        }
        return acceptsWaterQueuedVelocityHorizontalModel(data, thisMove, hDistanceAboveLimit);
    }

    private boolean acceptsWaterCurrentVelocityHorizontalModel(final Player player, final PlayerMoveData thisMove,
                                                               final double hDistanceAboveLimit) {
        // False-flag model: arrow/combat knockback in water is current velocity plus swim input drag.
        final Vector velocity = player.getVelocity();
        final double velocityH = MathUtil.dist(velocity.getX(), velocity.getZ());
        if (velocityH <= Magic.PREDICTION_EPSILON
                || velocity.getX() * thisMove.xDistance + velocity.getZ() * thisMove.zDistance < -Magic.PREDICTION_EPSILON) {
            return false;
        }
        final double inputCarry = Magic.LIQUID_ACCELERATION * getHorizontalInputScale(thisMove);
        final double limit = Math.min(WATER_CURRENT_VELOCITY_HORIZONTAL_CAP,
                Math.max(thisMove.hAllowedDistance,
                        velocityH + inputCarry + WATER_CURRENT_VELOCITY_HORIZONTAL_RESIDUAL));
        return thisMove.hDistance <= limit
                && hDistanceAboveLimit <= getModelOverLimit(thisMove.hAllowedDistance,
                        limit, WATER_CURRENT_VELOCITY_HORIZONTAL_RESIDUAL);
    }

    private boolean acceptsWaterImplicitSwimHorizontalModel(final PlayerMoveData thisMove,
                                                            final double hDistanceAboveLimit) {
        /*
         * Water model: Bedrock and Java can send small swim/body pushes even when
         * Bukkit exposes no useful current velocity. Keep this bounded to normal
         * submerged swim speed instead of setting allowed movement to actual.
         */
        final double verticalEnvelope = WATER_SURFACE_ASCEND_MODEL + WATER_VERTICAL_MODEL_EPSILON;
        final double limit = Math.max(thisMove.hAllowedDistance, WATER_IMPLICIT_SWIM_HORIZONTAL_CAP);
        return thisMove.hDistance <= limit
                && Math.abs(thisMove.yDistance) <= verticalEnvelope
                && hDistanceAboveLimit <= getModelOverLimit(thisMove.hAllowedDistance,
                        limit, WATER_IMPLICIT_SWIM_HORIZONTAL_RESIDUAL);
    }

    private boolean acceptsLavaHorizontalModel(final Player player, final MovingData data,
                                               final PlayerLocation from, final PlayerLocation to,
                                               final PlayerMoveData thisMove,
                                               final double hDistanceAboveLimit) {
        if (!isLavaMovementContext(from, to, thisMove)) {
            return false;
        }
        final double horizontalLimit = getLavaHorizontalModelLimit(player, data, thisMove);
        if (thisMove.hDistance > horizontalLimit
                || hDistanceAboveLimit > getModelOverLimit(thisMove.hAllowedDistance,
                        horizontalLimit, LAVA_VELOCITY_HORIZONTAL_RESIDUAL)) {
            tags.add("lava_velocity_horizontal_model_limit_miss");
            return false;
        }
        final List<PairEntry> queued = data.getHorizontalVelocityTracker().peekCovering(thisMove.xDistance,
                thisMove.zDistance, 1, Integer.MAX_VALUE, LAVA_VELOCITY_HORIZONTAL_RESIDUAL);
        if (!queued.isEmpty()) {
            data.getHorizontalVelocityTracker().use(queued.get(0).tick);
            tags.add("lava_queued_velocity_horizontal_model");
            return true;
        }
        final Vector velocity = player.getVelocity();
        if (horizontalVelocityVectorMatches(velocity.getX(), velocity.getZ(), thisMove.xDistance,
                thisMove.zDistance, LAVA_VELOCITY_HORIZONTAL_RESIDUAL,
                LAVA_VELOCITY_HORIZONTAL_PERPENDICULAR_RESIDUAL)) {
            tags.add("lava_current_velocity_horizontal_model");
            return true;
        }
        tags.add("lava_velocity_horizontal_vector_miss");
        return false;
    }

    private double getLavaHorizontalModelLimit(final Player player, final MovingData data,
                                               final PlayerMoveData thisMove) {
        final Vector velocity = player.getVelocity();
        final double velocityH = MathUtil.dist(velocity.getX(), velocity.getZ());
        final List<PairEntry> queued = data.getHorizontalVelocityTracker().peekCovering(thisMove.xDistance,
                thisMove.zDistance, 1, Integer.MAX_VALUE, LAVA_VELOCITY_HORIZONTAL_RESIDUAL);
        final double queuedH = queued.isEmpty() ? 0.0D : getHorizontalVelocityAmount(queued);
        // Lava model: liquid flow and server velocity can apply one packet apart, but the vector must still match.
        return Math.min(LAVA_HORIZONTAL_MOVE_GRACE,
                Math.max(thisMove.hAllowedDistance,
                        Math.max(velocityH, queuedH) + Magic.LIQUID_ACCELERATION * getHorizontalInputScale(thisMove)
                                + LAVA_VELOCITY_HORIZONTAL_RESIDUAL));
    }

    // Step and partial-support models: half blocks, lanterns, carpet, fences, and similar shapes have their own support envelope.
    private boolean isModernHalfStepContext(final Player player, final IPlayerData pData,
                                            final PlayerLocation from, final PlayerLocation to,
                                            final PlayerMoveData thisMove, final PlayerMoveData lastMove) {
        return isModernMovementClient(pData)
                && !isBedrockPlayer(player, pData)
                && !Bridge1_9.isGliding(player)
                && isGroundishStepMove(from, to, thisMove)
                && (isModernHalfStepRiseModel(from, to, thisMove) || isModernHalfStepPlateauModel(thisMove, lastMove))
                && !from.isInLiquid() && !to.isInLiquid()
                && !thisMove.from.inLiquid && !thisMove.to.inLiquid;
    }

    private boolean acceptsModernHalfStepHorizontalModel(final Player player, final IPlayerData pData,
                                                         final PlayerLocation from, final PlayerLocation to,
                                                         final PlayerMoveData thisMove, final PlayerMoveData lastMove,
                                                         final double hDistanceAboveLimit) {
        if (!isModernHalfStepContext(player, pData, from, to, thisMove, lastMove)) {
            return false;
        }
        // False-flag model: modern clients can report the half-step rise before the sampled blocks expose support.
        final double limit = getModernHalfStepHorizontalModelLimit(thisMove, lastMove);
        if (thisMove.hDistance <= limit
                && hDistanceAboveLimit <= getModelOverLimit(thisMove.hAllowedDistance,
                        limit, MODERN_HALF_STEP_HORIZONTAL_RESIDUAL)) {
            tags.add("modern_half_step_horizontal_model");
            return true;
        }
        tags.add("modern_half_step_horizontal_model_miss");
        return false;
    }

    private boolean acceptsModernHalfStepVerticalModel(final Player player, final IPlayerData pData,
                                                       final PlayerLocation from, final PlayerLocation to,
                                                       final PlayerMoveData thisMove, final PlayerMoveData lastMove,
                                                       final double yDistanceAboveLimit,
                                                       final double hDistanceAboveLimit) {
        if (!isModernHalfStepContext(player, pData, from, to, thisMove, lastMove)) {
            return false;
        }
        final double horizontalLimit = getModernHalfStepHorizontalModelLimit(thisMove, lastMove);
        if (thisMove.hDistance > horizontalLimit
                || hDistanceAboveLimit > getModelOverLimit(thisMove.hAllowedDistance,
                        horizontalLimit, MODERN_HALF_STEP_HORIZONTAL_RESIDUAL)) {
            tags.add("modern_half_step_vertical_h_miss");
            return false;
        }
        if (isModernHalfStepRiseModel(from, to, thisMove)
                && yDistanceAboveLimit <= getModelOverLimit(thisMove.yAllowedDistance,
                        getModernHalfStepRiseVerticalModel(from, to, thisMove),
                        MODERN_HALF_STEP_PLATEAU_EPSILON)) {
            tags.add("modern_half_step_rise_vertical_model");
            return true;
        }
        if (isModernHalfStepPlateauModel(thisMove, lastMove)
                && yDistanceAboveLimit <= Math.abs(thisMove.yAllowedDistance)
                        + MODERN_HALF_STEP_PLATEAU_EPSILON) {
            tags.add("modern_half_step_plateau_vertical_model");
            return true;
        }
        tags.add("modern_half_step_vertical_model_miss");
        return false;
    }

    private boolean isModernHalfStepRiseModel(final PlayerLocation from, final PlayerLocation to,
                                              final PlayerMoveData thisMove) {
        return Math.abs(thisMove.yDistance - BEDROCK_HALF_STEP_VERTICAL_MOVE) <= BEDROCK_HALF_STEP_VERTICAL_EPSILON
                || isModernHalfStepLandingModel(from, to, thisMove);
    }

    private boolean isModernHalfStepLandingModel(final PlayerLocation from, final PlayerLocation to,
                                                 final PlayerMoveData thisMove) {
        if (!to.isOnGroundOrResetCond() || !thisMove.collideY || thisMove.yDistance <= Magic.PREDICTION_EPSILON) {
            return false;
        }
        final double landingDistance = to.getBlockY() + BEDROCK_HALF_STEP_VERTICAL_MOVE - from.getY();
        return Math.abs(thisMove.yDistance - landingDistance) <= MODERN_HALF_STEP_PLATEAU_EPSILON;
    }

    private double getModernHalfStepRiseVerticalModel(final PlayerLocation from, final PlayerLocation to,
                                                      final PlayerMoveData thisMove) {
        if (isModernHalfStepLandingModel(from, to, thisMove)) {
            return to.getBlockY() + BEDROCK_HALF_STEP_VERTICAL_MOVE - from.getY();
        }
        return BEDROCK_HALF_STEP_VERTICAL_MOVE;
    }

    private boolean isModernHalfStepPlateauModel(final PlayerMoveData thisMove, final PlayerMoveData lastMove) {
        return lastMove.toIsValid
                && Math.abs(lastMove.yDistance - BEDROCK_HALF_STEP_VERTICAL_MOVE) <= BEDROCK_HALF_STEP_VERTICAL_EPSILON
                && Math.abs(thisMove.yDistance) <= Magic.PREDICTION_EPSILON
                && Math.abs(thisMove.yAllowedDistance - getModernHalfStepFollowupVerticalModel())
                        <= MODERN_HALF_STEP_PLATEAU_EPSILON;
    }

    private double getModernHalfStepFollowupVerticalModel() {
        return (BEDROCK_HALF_STEP_VERTICAL_MOVE - Magic.DEFAULT_GRAVITY) * Magic.FRICTION_MEDIUM_AIR;
    }

    private double getModernHalfStepHorizontalModelLimit(final PlayerMoveData thisMove, final PlayerMoveData lastMove) {
        final double lastCarry = lastMove.toIsValid ? lastMove.hDistance : thisMove.hAllowedDistance;
        final double inputCarry = MODERN_HALF_STEP_HORIZONTAL_INPUT_CARRY * getHorizontalInputScale(thisMove);
        return Math.min(MODERN_HALF_STEP_HORIZONTAL_CAP,
                Math.max(thisMove.hAllowedDistance,
                        Math.max(lastCarry, thisMove.hAllowedDistance) + inputCarry
                                + MODERN_HALF_STEP_HORIZONTAL_RESIDUAL));
    }

    private boolean isJumpCarryContext(final Player player, final PlayerLocation from,
                                       final PlayerLocation to, final PlayerMoveData thisMove,
                                       final PlayerMoveData lastMove) {
        return lastMove.toIsValid
                && !Bridge1_9.isGliding(player)
                && (isNormalJumpCarryContext(thisMove, lastMove) || isLowJumpCarryContext(thisMove, lastMove))
                && !from.isInLiquid() && !to.isInLiquid()
                && !thisMove.from.inLiquid && !thisMove.to.inLiquid;
    }

    private boolean isNormalJumpCarryContext(final PlayerMoveData thisMove, final PlayerMoveData lastMove) {
        return (tags.contains("jump_env") || tags.contains("bunnyhop"))
                && Math.abs(thisMove.yDistance - LiftOffEnvelope.NORMAL.getJumpGain(0.0D)) <= Magic.PREDICTION_EPSILON
                && Math.abs(thisMove.yAllowedDistance - thisMove.yDistance) <= Magic.PREDICTION_EPSILON
                && thisMove.hDistance <= getJumpCarryHorizontalModelLimit(thisMove, lastMove);
    }

    private boolean isLowJumpCarryContext(final PlayerMoveData thisMove, final PlayerMoveData lastMove) {
        // Model cleanup: head-obstructed short hops carry the previous ground H plus one normal input step.
        return getHorizontalInputScale(thisMove) > 0.0D
                && (lastMove.touchedGround || lastMove.from.onGroundOrResetCond || lastMove.to.onGroundOrResetCond)
                && thisMove.yDistance >= LOW_JUMP_CARRY_VERTICAL_MIN
                && thisMove.yDistance <= LOW_JUMP_CARRY_VERTICAL_MAX
                && thisMove.hDistance <= getJumpCarryHorizontalModelLimit(thisMove, lastMove);
    }

    private boolean acceptsJumpCarryHorizontalModel(final Player player, final PlayerLocation from,
                                                    final PlayerLocation to, final PlayerMoveData thisMove,
                                                    final PlayerMoveData lastMove,
                                                    final double hDistanceAboveLimit) {
        if (!isJumpCarryContext(player, from, to, thisMove, lastMove)) {
            return false;
        }
        // False-flag model: bound jump carry to previous H plus one current input step, not the actual move.
        final double limit = getJumpCarryHorizontalModelLimit(thisMove, lastMove);
        if (hDistanceAboveLimit <= getModelOverLimit(thisMove.hAllowedDistance,
                limit, JUMP_CARRY_HORIZONTAL_RESIDUAL)) {
            tags.add(isLowJumpCarryContext(thisMove, lastMove)
                    ? "low_jump_carry_horizontal_model" : "jump_carry_horizontal_model");
            return true;
        }
        tags.add("jump_carry_horizontal_model_miss");
        return false;
    }

    private boolean acceptsJumpCarryVerticalModel(final Player player, final PlayerLocation from,
                                                  final PlayerLocation to, final PlayerMoveData thisMove,
                                                  final PlayerMoveData lastMove,
                                                  final double yDistanceAboveLimit,
                                                  final double hDistanceAboveLimit) {
        if (!isJumpCarryContext(player, from, to, thisMove, lastMove)) {
            return false;
        }
        final double horizontalLimit = getJumpCarryHorizontalModelLimit(thisMove, lastMove);
        if (thisMove.hDistance > horizontalLimit
                || hDistanceAboveLimit > getModelOverLimit(thisMove.hAllowedDistance,
                        horizontalLimit, JUMP_CARRY_HORIZONTAL_RESIDUAL)) {
            tags.add("jump_carry_vertical_h_miss");
            return false;
        }
        if (isLowJumpCarryContext(thisMove, lastMove)
                && yDistanceAboveLimit <= getModelOverLimit(thisMove.yAllowedDistance,
                        LOW_JUMP_CARRY_VERTICAL_MAX, LOW_JUMP_CARRY_VERTICAL_EPSILON)) {
            tags.add("low_jump_carry_vertical_model");
            return true;
        }
        if (isNormalJumpCarryContext(thisMove, lastMove)
                && yDistanceAboveLimit <= Magic.PREDICTION_EPSILON) {
            tags.add("jump_carry_vertical_model");
            return true;
        }
        tags.add("jump_carry_vertical_model_miss");
        return false;
    }

    private double getJumpCarryHorizontalModelLimit(final PlayerMoveData thisMove, final PlayerMoveData lastMove) {
        final double lastCarry = lastMove.toIsValid ? lastMove.hDistance : thisMove.hAllowedDistance;
        return Math.min(JUMP_CARRY_MAX_MOVE,
                Math.max(thisMove.hAllowedDistance, lastCarry + playerInputHorizontalCarry(thisMove))
                        + JUMP_CARRY_HORIZONTAL_RESIDUAL);
    }

    private boolean isGroundLandingCarryContext(final Player player, final PlayerLocation from,
                                                final PlayerLocation to, final PlayerMoveData thisMove,
                                                final PlayerMoveData lastMove) {
        return lastMove.toIsValid
                && !Bridge1_9.isGliding(player)
                && isGroundishStepMove(from, to, thisMove)
                && Math.abs(thisMove.yDistance) <= Magic.PREDICTION_EPSILON
                && lastMove.yDistance < -Magic.PREDICTION_EPSILON
                && thisMove.hDistance <= getGroundLandingCarryHorizontalModelLimit(player, thisMove, lastMove)
                && !from.isInLiquid() && !to.isInLiquid()
                && !thisMove.from.inLiquid && !thisMove.to.inLiquid;
    }

    private boolean acceptsGroundLandingCarryHorizontalModel(final Player player, final PlayerLocation from,
                                                             final PlayerLocation to, final PlayerMoveData thisMove,
                                                             final PlayerMoveData lastMove,
                                                             final double hDistanceAboveLimit) {
        if (!isGroundLandingCarryContext(player, from, to, thisMove, lastMove)) {
            return false;
        }
        // False-flag model: the landing packet keeps the previous air speed plus the next ground input step.
        final double limit = getGroundLandingCarryHorizontalModelLimit(player, thisMove, lastMove);
        if (hDistanceAboveLimit <= getModelOverLimit(thisMove.hAllowedDistance,
                limit, GROUND_LANDING_CARRY_HORIZONTAL_RESIDUAL)) {
            tags.add("ground_landing_carry_horizontal_model");
            return true;
        }
        tags.add("ground_landing_carry_horizontal_model_miss");
        return false;
    }

    private double getGroundLandingCarryHorizontalModelLimit(final Player player,
                                                            final PlayerMoveData thisMove,
                                                            final PlayerMoveData lastMove) {
        final double inputCarry = player.getWalkSpeed() * getHorizontalInputScale(thisMove)
                * GROUND_LANDING_CARRY_INPUT_MULTIPLIER;
        return Math.min(GROUND_LANDING_CARRY_MAX_MOVE,
                Math.max(thisMove.hAllowedDistance,
                        lastMove.hDistance + inputCarry + GROUND_LANDING_CARRY_HORIZONTAL_RESIDUAL));
    }

    private boolean isGroundPassablePlantContext(final Player player, final PlayerLocation from,
                                                 final PlayerLocation to, final PlayerMoveData thisMove,
                                                 final PlayerMoveData lastMove) {
        return lastMove.toIsValid
                && !Bridge1_9.isGliding(player)
                && isGroundishStepMove(from, to, thisMove)
                && isPassablePlantMove(from, to)
                && getHorizontalInputScale(thisMove) > 0.0D
                && Math.abs(thisMove.yDistance) <= Magic.PREDICTION_EPSILON
                && thisMove.hDistance > thisMove.hAllowedDistance + Magic.PREDICTION_EPSILON
                && thisMove.hDistance <= getGroundPassablePlantHorizontalModelLimit(thisMove, lastMove)
                && !from.isInLiquid() && !to.isInLiquid()
                && !thisMove.from.inLiquid && !thisMove.to.inLiquid;
    }

    private boolean acceptsGroundPassablePlantHorizontalModel(final Player player, final PlayerLocation from,
                                                              final PlayerLocation to, final PlayerMoveData thisMove,
                                                              final PlayerMoveData lastMove,
                                                              final double hDistanceAboveLimit) {
        if (!isGroundPassablePlantContext(player, from, to, thisMove, lastMove)) {
            return false;
        }
        // Model cleanup: instant plants are passable, but can leave one grounded carry packet above the block predictor.
        final double limit = getGroundPassablePlantHorizontalModelLimit(thisMove, lastMove);
        if (hDistanceAboveLimit <= getModelOverLimit(thisMove.hAllowedDistance,
                limit, GROUND_PASSABLE_PLANT_HORIZONTAL_RESIDUAL)) {
            tags.add("ground_passable_plant_horizontal_model");
            return true;
        }
        tags.add("ground_passable_plant_horizontal_model_miss");
        return false;
    }

    private double getGroundPassablePlantHorizontalModelLimit(final PlayerMoveData thisMove,
                                                              final PlayerMoveData lastMove) {
        final double inputCarry = playerInputHorizontalCarry(thisMove) * GROUND_PASSABLE_PLANT_INPUT_CARRY;
        return Math.min(GROUND_PASSABLE_PLANT_MAX_MOVE,
                Math.max(thisMove.hAllowedDistance, lastMove.hDistance + inputCarry
                        + GROUND_PASSABLE_PLANT_HORIZONTAL_RESIDUAL));
    }

    private boolean isPassablePlantMove(final PlayerLocation from, final PlayerLocation to) {
        return MaterialUtil.INSTANT_PLANTS.contains(from.getBlockType())
                || MaterialUtil.INSTANT_PLANTS.contains(to.getBlockType());
    }

    private boolean isGroundVelocityCarryContext(final Player player, final PlayerLocation from,
                                                 final PlayerLocation to, final PlayerMoveData thisMove) {
        return !Bridge1_9.isGliding(player)
                && isGroundishStepMove(from, to, thisMove)
                && Math.abs(thisMove.yDistance) <= Magic.PREDICTION_EPSILON
                && thisMove.hDistance <= getGroundVelocityCarryHorizontalModelLimit(player, thisMove)
                && groundVelocityCarryDirectionMatches(player, thisMove)
                && !from.isInLiquid() && !to.isInLiquid()
                && !thisMove.from.inLiquid && !thisMove.to.inLiquid;
    }

    private boolean acceptsGroundVelocityCarryHorizontalModel(final Player player, final PlayerLocation from,
                                                              final PlayerLocation to, final PlayerMoveData thisMove,
                                                              final double hDistanceAboveLimit) {
        if (!isGroundVelocityCarryContext(player, from, to, thisMove)) {
            return false;
        }
        // False-flag model: landing/ground packets can add current server velocity to the walk prediction.
        final double limit = getGroundVelocityCarryHorizontalModelLimit(player, thisMove);
        if (hDistanceAboveLimit <= getModelOverLimit(thisMove.hAllowedDistance,
                limit, GROUND_VELOCITY_CARRY_HORIZONTAL_RESIDUAL)) {
            tags.add("ground_velocity_carry_horizontal_model");
            return true;
        }
        tags.add("ground_velocity_carry_horizontal_model_miss");
        return false;
    }

    private double getGroundVelocityCarryHorizontalModelLimit(final Player player, final PlayerMoveData thisMove) {
        final Vector velocity = player.getVelocity();
        final double velocityH = MathUtil.dist(velocity.getX(), velocity.getZ());
        return Math.min(GROUND_VELOCITY_CARRY_MAX_MOVE,
                thisMove.hAllowedDistance + velocityH + GROUND_VELOCITY_CARRY_HORIZONTAL_RESIDUAL);
    }

    private boolean groundVelocityCarryDirectionMatches(final Player player, final PlayerMoveData thisMove) {
        final Vector velocity = player.getVelocity();
        final double velocityH = MathUtil.dist(velocity.getX(), velocity.getZ());
        if (velocityH <= Magic.PREDICTION_EPSILON) {
            return false;
        }
        final double overX = thisMove.xDistance - thisMove.xAllowedDistance;
        final double overZ = thisMove.zDistance - thisMove.zAllowedDistance;
        return velocity.getX() * overX + velocity.getZ() * overZ >= -Magic.PREDICTION_EPSILON;
    }

    private boolean isItemResyncMovementContext(final Player player, final PlayerLocation from,
                                                final PlayerLocation to, final PlayerMoveData thisMove) {
        return tags.contains("itemresync")
                && tags.contains("usingitem")
                && !Bridge1_9.isGliding(player)
                && thisMove.hDistance > thisMove.hAllowedDistance + Magic.PREDICTION_EPSILON
                && Math.abs(thisMove.yDistance - thisMove.yAllowedDistance) <= Magic.PREDICTION_EPSILON
                && thisMove.hDistance <= getItemResyncHorizontalModelLimit(player, thisMove)
                && !from.isInLiquid() && !to.isInLiquid()
                && !thisMove.from.inLiquid && !thisMove.to.inLiquid;
    }

    private boolean acceptsItemResyncHorizontalModel(final Player player, final PlayerLocation from,
                                                     final PlayerLocation to, final PlayerMoveData thisMove,
                                                     final double hDistanceAboveLimit) {
        if (!isItemResyncMovementContext(player, from, to, thisMove)) {
            return false;
        }
        // False-flag model: item-use state can resync one packet late, so use the normal walk envelope.
        final double limit = getItemResyncHorizontalModelLimit(player, thisMove);
        if (hDistanceAboveLimit <= getModelOverLimit(thisMove.hAllowedDistance,
                limit, GROUNDED_ITEM_RESYNC_HORIZONTAL_OVER_GRACE)) {
            tags.add("itemresync_horizontal_model");
            return true;
        }
        tags.add("itemresync_horizontal_model_miss");
        return false;
    }

    private double getItemResyncHorizontalModelLimit(final Player player, final PlayerMoveData thisMove) {
        final double inputScale = Math.max(1.0D, getHorizontalInputScale(thisMove));
        final double normalWalkEnvelope = player.getWalkSpeed() * (inputScale > 1.0D ? 1.25D : 1.15D);
        final double velocityEnvelope = MathUtil.dist(player.getVelocity().getX(), player.getVelocity().getZ())
                + AIR_CURRENT_VELOCITY_HORIZONTAL_RESIDUAL;
        return Math.min(GROUNDED_ITEM_RESYNC_MOVE_GRACE,
                Math.max(thisMove.hAllowedDistance, Math.max(normalWalkEnvelope, velocityEnvelope)));
    }

    private boolean isAirCurrentVelocityContext(final Player player, final PlayerLocation from,
                                                final PlayerLocation to, final PlayerMoveData thisMove) {
        return !Bridge1_9.isGliding(player)
                && Math.abs(thisMove.yDistance - player.getVelocity().getY()) <= AIR_CURRENT_VELOCITY_VERTICAL_MATCH
                && thisMove.hDistance <= getAirCurrentVelocityHorizontalModelLimit(player, thisMove)
                && !from.isInLiquid() && !to.isInLiquid()
                && !thisMove.from.inLiquid && !thisMove.to.inLiquid;
    }

    private boolean acceptsAirCurrentVelocityHorizontalModel(final Player player, final PlayerLocation from,
                                                             final PlayerLocation to, final PlayerMoveData thisMove,
                                                             final double hDistanceAboveLimit) {
        if (!isAirCurrentVelocityContext(player, from, to, thisMove)) {
            return false;
        }
        // False-flag model: server velocity can be exact for Y while X/Z still needs the air input carry.
        final double limit = getAirCurrentVelocityHorizontalModelLimit(player, thisMove);
        if (hDistanceAboveLimit <= getModelOverLimit(thisMove.hAllowedDistance,
                limit, AIR_CURRENT_VELOCITY_HORIZONTAL_RESIDUAL)) {
            tags.add("air_current_velocity_horizontal_model");
            return true;
        }
        tags.add("air_current_velocity_horizontal_model_miss");
        return false;
    }

    private double getAirCurrentVelocityHorizontalModelLimit(final Player player, final PlayerMoveData thisMove) {
        final Vector velocity = player.getVelocity();
        final double velocityH = MathUtil.dist(velocity.getX(), velocity.getZ());
        return Math.min(AIR_CURRENT_VELOCITY_MAX_HORIZONTAL_MOVE,
                velocityH + Magic.AIR_ACCELERATION * getHorizontalInputScale(thisMove)
                        + AIR_CURRENT_VELOCITY_HORIZONTAL_RESIDUAL);
    }

    private boolean isAirInertiaMovementContext(final Player player, final PlayerLocation from,
                                                final PlayerLocation to, final PlayerMoveData thisMove,
                                                final PlayerMoveData lastMove) {
        return lastMove.toIsValid
                && !Bridge1_9.isGliding(player)
                && thisMove.hDistance <= AIR_INERTIA_MAX_HORIZONTAL_MOVE
                && lastMove.hDistance <= AIR_INERTIA_MAX_HORIZONTAL_MOVE
                && thisMove.hDistance <= getAirInertiaHorizontalModel(thisMove, lastMove)
                        + AIR_INERTIA_HORIZONTAL_EPSILON
                && Math.abs(thisMove.yDistance - getAirInertiaVerticalModel(lastMove))
                        <= AIR_INERTIA_VERTICAL_EPSILON
                && !from.isInLiquid() && !to.isInLiquid()
                && !thisMove.from.inLiquid && !thisMove.to.inLiquid;
    }

    private boolean acceptsAirInertiaHorizontalModel(final Player player, final PlayerLocation from,
                                                     final PlayerLocation to, final PlayerMoveData thisMove,
                                                     final PlayerMoveData lastMove,
                                                     final double hDistanceAboveLimit) {
        if (!isAirInertiaMovementContext(player, from, to, thisMove, lastMove)) {
            return false;
        }
        // False-flag model: ordinary air carry is last horizontal motion times vanilla air friction.
        final double limit = getAirInertiaHorizontalModel(thisMove, lastMove) + AIR_INERTIA_HORIZONTAL_EPSILON;
        if (hDistanceAboveLimit <= getModelOverLimit(thisMove.hAllowedDistance,
                limit, AIR_INERTIA_HORIZONTAL_EPSILON)) {
            tags.add("air_inertia_horizontal_model");
            return true;
        }
        tags.add("air_inertia_horizontal_model_miss");
        return false;
    }

    private boolean acceptsAirInertiaVerticalModel(final Player player, final PlayerLocation from,
                                                   final PlayerLocation to, final PlayerMoveData thisMove,
                                                   final PlayerMoveData lastMove,
                                                   final double yDistanceAboveLimit,
                                                   final double hDistanceAboveLimit) {
        if (!isAirInertiaMovementContext(player, from, to, thisMove, lastMove)
                || hDistanceAboveLimit > getModelOverLimit(thisMove.hAllowedDistance,
                        getAirInertiaHorizontalModel(thisMove, lastMove) + AIR_INERTIA_HORIZONTAL_EPSILON,
                        AIR_INERTIA_HORIZONTAL_EPSILON)) {
            return false;
        }
        final double expectedY = getAirInertiaVerticalModel(lastMove);
        if (yDistanceAboveLimit <= Math.abs(thisMove.yAllowedDistance - expectedY)
                + AIR_INERTIA_VERTICAL_EPSILON) {
            tags.add(lastMove.yDistance <= Magic.PREDICTION_EPSILON
                    ? "air_inertia_first_gravity_vertical_model" : "air_inertia_continued_gravity_vertical_model");
            return true;
        }
        tags.add("air_inertia_vertical_model_miss");
        return false;
    }

    private double getAirInertiaHorizontalModel(final PlayerMoveData thisMove, final PlayerMoveData lastMove) {
        // Model cleanup: horizontal air carry uses vanilla's 0.91 inertia, not vertical air friction.
        return lastMove.hDistance * Magic.AIR_HORIZONTAL_INERTIA
                + Magic.AIR_ACCELERATION * getHorizontalInputScale(thisMove);
    }

    private double getAirInertiaFirstGravityModel() {
        return -Magic.DEFAULT_GRAVITY * Magic.FRICTION_MEDIUM_AIR;
    }

    private double getAirInertiaVerticalModel(final PlayerMoveData lastMove) {
        // Model cleanup: use vanilla gravity continuation once the first air tick is already known.
        return lastMove.toIsValid
                ? (lastMove.yDistance - Magic.DEFAULT_GRAVITY) * Magic.FRICTION_MEDIUM_AIR
                : getAirInertiaFirstGravityModel();
    }

    private boolean isPartialSupportMovementContext(final PlayerLocation from, final PlayerLocation to,
                                                    final PlayerMoveData thisMove, final PlayerMoveData lastMove) {
        if (!isPartialSupportNear(from, to)
                || !isGroundishStepMove(from, to, thisMove)
                || from.isInLiquid() || to.isInLiquid()
                || thisMove.from.inLiquid || thisMove.to.inLiquid) {
            return false;
        }
        final double horizontalLimit = getPartialSupportHorizontalModelLimit(from, to, thisMove, lastMove);
        final double verticalLimit = getPartialSupportVerticalModelLimit(from, to, thisMove);
        // Model cleanup: only select partial-support when the collision-shape envelope can plausibly own this move.
        return thisMove.hDistance <= horizontalLimit
                && (thisMove.yDistance <= verticalLimit
                        || getPartialSupportVerticalClampModel(from, to, thisMove.yDistance) > 0.0D
                        || getPartialSupportLandingClampFraction(to) >= 0.0D);
    }

    private boolean acceptsPartialSupportHorizontalModel(final PlayerLocation from, final PlayerLocation to,
                                                         final PlayerMoveData thisMove, final PlayerMoveData lastMove,
                                                         final double hDistanceAboveLimit) {
        // False-flag model: partial support blocks use the player's normal step height instead of a fixed horizontal grace.
        final double limit = getPartialSupportHorizontalModelLimit(from, to, thisMove, lastMove);
        final boolean accepted = isPartialSupportNear(from, to)
                && isGroundishStepMove(from, to, thisMove)
                && thisMove.hDistance <= limit
                && hDistanceAboveLimit <= getModelOverLimit(thisMove.hAllowedDistance, limit, PARTIAL_SUPPORT_HORIZONTAL_MODEL_EPSILON)
                && !from.isInLiquid() && !to.isInLiquid()
                && !thisMove.from.inLiquid && !thisMove.to.inLiquid;
        if (accepted) {
            addPartialSupportTypeTag(from, to, "horizontal_model");
        }
        return accepted;
    }

    private boolean acceptsPartialSupportVerticalModel(final Player player,
                                                       final PlayerLocation from, final PlayerLocation to,
                                                       final PlayerMoveData thisMove, final PlayerMoveData lastMove,
                                                       final double yDistanceAboveLimit,
                                                       final double hDistanceAboveLimit) {
        // False-flag model: partial support uses collision-shape height, not a post-failure vertical grace.
        final double verticalLimit = getPartialSupportVerticalModelLimit(from, to, thisMove);
        final double horizontalLimit = getPartialSupportHorizontalModelLimit(from, to, thisMove, lastMove);
        if (!isPartialSupportNear(from, to)
                || !isGroundishStepMove(from, to, thisMove)
                || thisMove.hDistance > horizontalLimit
                || hDistanceAboveLimit > getModelOverLimit(thisMove.hAllowedDistance, horizontalLimit, PARTIAL_SUPPORT_HORIZONTAL_MODEL_EPSILON)
                || from.isInLiquid() || to.isInLiquid()
                || thisMove.from.inLiquid || thisMove.to.inLiquid) {
            return false;
        }
        if (thisMove.yDistance >= -Magic.PREDICTION_EPSILON
                && thisMove.yDistance <= verticalLimit
                && yDistanceAboveLimit <= getModelOverLimit(thisMove.yAllowedDistance, verticalLimit, PARTIAL_SUPPORT_VERTICAL_MODEL_EPSILON)) {
            tags.add("partial_support_vertical_model");
            addPartialSupportTypeTag(from, to, "vertical_model");
            return true;
        }
        if (acceptsPartialSupportVerticalClampModel(from, to, thisMove, yDistanceAboveLimit)) {
            tags.add("partial_support_vertical_clamp_model");
            addPartialSupportTypeTag(from, to, "vertical_clamp_model");
            return true;
        }
        if (acceptsPartialSupportLastInvalidVelocityModel(player, thisMove, lastMove,
                yDistanceAboveLimit, hDistanceAboveLimit)) {
            tags.add("partial_support_last_invalid_velocity_model");
            addPartialSupportTypeTag(from, to, "last_invalid_velocity_model");
            return true;
        }
        if (acceptsPartialSupportLastInvalidGravityModel(player, thisMove, lastMove,
                yDistanceAboveLimit, hDistanceAboveLimit)) {
            tags.add("partial_support_last_invalid_gravity_model");
            addPartialSupportTypeTag(from, to, "last_invalid_gravity_model");
            return true;
        }
        if (thisMove.yDistance < -Magic.PREDICTION_EPSILON
                && yDistanceAboveLimit > Magic.PREDICTION_EPSILON) {
            tags.add("partial_support_vertical_clamp_model_miss");
            addPartialSupportTypeTag(from, to, "vertical_model_miss");
        }
        return false;
    }

    private boolean acceptsPartialSupportLastInvalidVelocityModel(final Player player,
                                                                  final PlayerMoveData thisMove,
                                                                  final PlayerMoveData lastMove,
                                                                  final double yDistanceAboveLimit,
                                                                  final double hDistanceAboveLimit) {
        // False-flag model: partial support can invalidate the previous packet while the server fall velocity is exact.
        if (lastMove.toIsValid
                || hDistanceAboveLimit > LAST_INVALID_RESYNC_HORIZONTAL_RESIDUAL
                || !isLastInvalidVelocityResyncCandidate(player, thisMove)) {
            return false;
        }
        final double velocityY = player.getVelocity().getY();
        return yDistanceAboveLimit <= Math.abs(thisMove.yAllowedDistance - velocityY)
                + LAST_INVALID_RESYNC_VERTICAL_MATCH;
    }

    private boolean acceptsPartialSupportLastInvalidGravityModel(final Player player,
                                                                 final PlayerMoveData thisMove,
                                                                 final PlayerMoveData lastMove,
                                                                 final double yDistanceAboveLimit,
                                                                 final double hDistanceAboveLimit) {
        // False-flag model: one lost support packet can apply the next vanilla gravity step after current velocity.
        if (lastMove.toIsValid
                || hDistanceAboveLimit > LAST_INVALID_RESYNC_HORIZONTAL_RESIDUAL) {
            return false;
        }
        final double expectedY = (player.getVelocity().getY() - Magic.DEFAULT_GRAVITY) * Magic.FRICTION_MEDIUM_AIR;
        return Math.abs(thisMove.yDistance - expectedY) <= LAST_INVALID_RESYNC_VERTICAL_MATCH
                && yDistanceAboveLimit <= Math.abs(thisMove.yAllowedDistance - expectedY)
                        + LAST_INVALID_RESYNC_VERTICAL_MATCH;
    }

    private boolean acceptsPartialSupportVerticalClampModel(final PlayerLocation from, final PlayerLocation to,
                                                            final PlayerMoveData thisMove,
                                                            final double yDistanceAboveLimit) {
        final double verticalClamp = getPartialSupportVerticalClampModel(from, to, thisMove.yDistance);
        if (verticalClamp > 0.0D && Math.abs(thisMove.yDistance + verticalClamp) <= PARTIAL_SUPPORT_VERTICAL_CLAMP_EPSILON) {
            if (thisMove.yDistance > thisMove.yAllowedDistance
                    && yDistanceAboveLimit <= getModelOverLimit(thisMove.yAllowedDistance,
                            -verticalClamp, PARTIAL_SUPPORT_VERTICAL_CLAMP_EPSILON)) {
                return true;
            }
            if (thisMove.yDistance < thisMove.yAllowedDistance
                    && yDistanceAboveLimit <= Math.abs(thisMove.yAllowedDistance + verticalClamp)
                            + PARTIAL_SUPPORT_VERTICAL_CLAMP_EPSILON) {
                tags.add("partial_support_vertical_clamp_below_model");
                return true;
            }
        }
        if (acceptsPartialSupportLandingClampModel(from, to, thisMove, yDistanceAboveLimit)) {
            tags.add("partial_support_landing_clamp_model");
            return true;
        }
        return false;
    }

    private boolean acceptsPartialSupportLandingClampModel(final PlayerLocation from, final PlayerLocation to,
                                                           final PlayerMoveData thisMove,
                                                           final double yDistanceAboveLimit) {
        // False-flag model: thin supports can be sampled as air, but the Y packet still lands on their quantized shape.
        if (thisMove.yDistance >= -Magic.PREDICTION_EPSILON
                || !isPartialSupportLandingBlock(to)) {
            return false;
        }
        final double supportFraction = getPartialSupportLandingClampFraction(to);
        if (supportFraction < 0.0D) {
            return false;
        }
        final double landingDistance = to.getBlockY() + supportFraction - from.getY();
        return Math.abs(thisMove.yDistance - landingDistance) <= PARTIAL_SUPPORT_VERTICAL_CLAMP_EPSILON
                && yDistanceAboveLimit <= Math.abs(thisMove.yAllowedDistance - landingDistance)
                        + PARTIAL_SUPPORT_VERTICAL_CLAMP_EPSILON;
    }

    private double getPartialSupportLandingClampFraction(final PlayerLocation to) {
        final double fraction = to.getY() - to.getBlockY();
        final double clamp = Math.round(fraction / PARTIAL_SUPPORT_VERTICAL_CLAMP_UNIT)
                * PARTIAL_SUPPORT_VERTICAL_CLAMP_UNIT;
        return Math.abs(fraction - clamp) <= PARTIAL_SUPPORT_VERTICAL_CLAMP_EPSILON ? clamp : -1.0D;
    }

    private boolean isPartialSupportLandingBlock(final PlayerLocation to) {
        return isPartialSupportBlock(to.getBlockTypeBelow()) || isPartialSupportBlock(to.getBlockType());
    }

    private double getPartialSupportVerticalClampModel(final PlayerLocation from, final PlayerLocation to,
                                                       final double yDistance) {
        final double descend = -yDistance;
        final boolean snowSupport = from != null && to != null && isSnowSupportNear(from, to);
        final double maxDescend = snowSupport
                ? SNOW_SUPPORT_MAX_COLLISION_HEIGHT : PARTIAL_SUPPORT_VERTICAL_CLAMP_MAX_DESCEND;
        final double unit = snowSupport ? SNOW_SUPPORT_LAYER_HEIGHT : PARTIAL_SUPPORT_VERTICAL_CLAMP_UNIT;
        if (descend <= Magic.PREDICTION_EPSILON
                || descend > maxDescend) {
            return 0.0D;
        }
        final double clamp = Math.round(descend / unit) * unit;
        if (clamp <= 0.0D
                || Math.abs(descend - clamp) > PARTIAL_SUPPORT_VERTICAL_CLAMP_EPSILON) {
            return 0.0D;
        }
        return clamp;
    }

    private double getWaterHorizontalModelLimit(final Player player, final PlayerMoveData thisMove,
                                                final boolean dolphinGrace) {
        // False-flag model: water H is driven by current server velocity plus liquid input acceleration.
        final Vector velocity = player.getVelocity();
        final double velocityH = MathUtil.dist(velocity.getX(), velocity.getZ());
        final double inputAcceleration = Magic.LIQUID_ACCELERATION * getHorizontalInputScale(thisMove);
        final double velocityModel = velocityH > Magic.PREDICTION_EPSILON
                ? velocityH + inputAcceleration + WATER_HORIZONTAL_MODEL_EPSILON : 0.0D;
        final double allowedModel = thisMove.hAllowedDistance + inputAcceleration + WATER_HORIZONTAL_MODEL_EPSILON;
        if (!dolphinGrace) {
            return Math.max(thisMove.hAllowedDistance, Math.max(velocityModel, allowedModel));
        }
        final double dolphinAmplifier = Math.max(0.0D, Bridge1_13.getDolphinGraceAmplifier(player) + 1.0D);
        final double dolphinModel = allowedModel + dolphinAmplifier * Magic.LIQUID_ACCELERATION * 4.0D;
        return Math.max(dolphinModel, Math.max(velocityModel, thisMove.hAllowedDistance));
    }

    private boolean acceptsWaterQueuedVelocityHorizontalModel(final MovingData data, final PlayerMoveData thisMove,
                                                             final double hDistanceAboveLimit) {
        final List<PairEntry> queued = data.getHorizontalVelocityTracker().peekCovering(thisMove.xDistance, thisMove.zDistance,
                1, Integer.MAX_VALUE, WATER_QUEUED_VELOCITY_HORIZONTAL_RESIDUAL);
        if (queued.isEmpty()) {
            tags.add("water_queued_velocity_horizontal_model_miss");
            return false;
        }
        final double queuedLimit = getHorizontalVelocityAmount(queued)
                + Magic.LIQUID_ACCELERATION * getHorizontalInputScale(thisMove)
                + WATER_QUEUED_VELOCITY_HORIZONTAL_RESIDUAL;
        if (thisMove.hDistance > queuedLimit
                || hDistanceAboveLimit > getModelOverLimit(thisMove.hAllowedDistance,
                        queuedLimit, WATER_QUEUED_VELOCITY_HORIZONTAL_RESIDUAL)) {
            tags.add("water_queued_velocity_horizontal_limit_miss");
            return false;
        }
        // Water model: knockback/current packets can apply through liquid before the normal swim predictor catches up.
        data.getHorizontalVelocityTracker().use(queued.get(0).tick);
        tags.add("water_queued_velocity_horizontal_model");
        return true;
    }

    private double getHorizontalVelocityAmount(final List<PairEntry> queued) {
        double x = 0.0D;
        double z = 0.0D;
        final int tick = queued.get(0).tick;
        for (final PairEntry entry : queued) {
            if (entry.tick == tick) {
                x += entry.x;
                z += entry.z;
            }
        }
        return MathUtil.dist(x, z);
    }

    private double getPartialSupportHorizontalModelLimit(final PlayerLocation from, final PlayerLocation to,
                                                         final PlayerMoveData thisMove, final PlayerMoveData lastMove) {
        final double stepHeight = getPartialSupportStepHeightModel(from, to);
        final double lastCarry = lastMove.toIsValid ? lastMove.hDistance : thisMove.hAllowedDistance;
        final double inputCarry = thisMove.hasImpulse.decideOptimistically()
                ? stepHeight * (isDiagonalImpulse(thisMove) ? 0.9D : 0.75D) : stepHeight * 0.35D;
        return Math.max(thisMove.hAllowedDistance + stepHeight, lastCarry + inputCarry);
    }

    private double getPartialSupportVerticalModelLimit(final PlayerLocation from, final PlayerLocation to,
                                                       final PlayerMoveData thisMove) {
        final double stepHeight = getPartialSupportStepHeightModel(from, to);
        return Math.max(stepHeight, thisMove.yAllowedDistance + stepHeight);
    }

    private double getBedrockStepHorizontalModelLimit(final PlayerLocation from, final PlayerLocation to,
                                                      final PlayerMoveData thisMove, final PlayerMoveData lastMove) {
        final double lastCarry = lastMove.toIsValid ? lastMove.hDistance : thisMove.hAllowedDistance;
        final double inputCarry = BEDROCK_HALF_STEP_VERTICAL_MOVE
                * (isDiagonalImpulse(thisMove) ? 0.9D : 0.75D);
        return Math.max(getPartialSupportHorizontalModelLimit(from, to, thisMove, lastMove),
                lastCarry + inputCarry + BEDROCK_HORIZONTAL_PREDICTION_EPSILON);
    }

    private double getPartialSupportStepHeightModel(final PlayerLocation from, final PlayerLocation to) {
        if (isSnowSupportNear(from, to)) {
            // Snow model: layer collision is quantized in 1/8-block steps and can rise above a half block.
            return Math.max(SNOW_SUPPORT_LAYER_HEIGHT, getSnowSupportHeightModel(from, to));
        }
        return isPartialSupportNear(from, to) ? PARTIAL_SUPPORT_STEP_HEIGHT_MODEL : 0.0D;
    }

    private double getHorizontalInputScale(final PlayerMoveData thisMove) {
        if (!thisMove.hasImpulse.decideOptimistically()
                && thisMove.forwardImpulse == ForwardDirection.NONE
                && thisMove.strafeImpulse == StrafeDirection.NONE) {
            return 0.0D;
        }
        return isDiagonalImpulse(thisMove) ? Math.sqrt(2.0D) : 1.0D;
    }

    private boolean isDiagonalImpulse(final PlayerMoveData thisMove) {
        return thisMove.forwardImpulse != ForwardDirection.NONE
                && thisMove.strafeImpulse != StrafeDirection.NONE;
    }

    private double getModelOverLimit(final double allowedDistance, final double modelLimit, final double epsilon) {
        return Math.max(0.0D, modelLimit - allowedDistance) + epsilon;
    }

    // Teleport/server-position models: clear stale movement history around async teleports, portals, respawn, and RTP-style jumps.
    private boolean acceptsServerPositionJumpResyncHorizontalModel(final IPlayerData pData,
                                                                   final PlayerLocation from,
                                                                   final PlayerLocation to,
                                                                   final PlayerMoveData thisMove,
                                                                   final PlayerMoveData lastMove,
                                                                   final double hDistanceAboveLimit) {
        if (!isServerPositionJumpResyncContext(pData, from, to, thisMove, lastMove)) {
            return false;
        }
        final double limit = getServerPositionJumpResyncHorizontalModelLimit(thisMove, lastMove);
        if (hDistanceAboveLimit <= getModelOverLimit(thisMove.hAllowedDistance,
                limit, SERVER_POSITION_JUMP_AIR_HORIZONTAL_RESIDUAL)) {
            tags.add(isGroundishStepMove(from, to, thisMove)
                    ? "server_position_jump_ground_resync_horizontal_model"
                    : "server_position_jump_air_resync_horizontal_model");
            return true;
        }
        tags.add("server_position_jump_resync_horizontal_model_miss");
        return false;
    }

    private double getServerPositionJumpResyncHorizontalModelLimit(final PlayerMoveData thisMove,
                                                                   final PlayerMoveData lastMove) {
        final double historyCarry = lastMove.toIsValid ? lastMove.hDistance : thisMove.hAllowedDistance;
        final double inputCarry = playerInputHorizontalCarry(thisMove);
        // Teleport model: keep post-teleport H to one normal input/history packet plus a small packet-order residual.
        return Math.min(SERVER_POSITION_JUMP_HORIZONTAL_MOVE_GRACE,
                Math.max(thisMove.hAllowedDistance + SERVER_POSITION_JUMP_AIR_HORIZONTAL_RESIDUAL,
                        historyCarry + inputCarry + SERVER_POSITION_JUMP_AIR_HORIZONTAL_RESIDUAL));
    }

    private boolean acceptsServerPositionJumpResyncVerticalModel(final IPlayerData pData,
                                                                 final PlayerLocation from,
                                                                 final PlayerLocation to,
                                                                 final PlayerMoveData thisMove,
                                                                 final PlayerMoveData lastMove,
                                                                 final double yDistanceAboveLimit,
                                                                 final double hDistanceAboveLimit) {
        if (!isServerPositionJumpResyncContext(pData, from, to, thisMove, lastMove)) {
            return false;
        }
        final double horizontalLimit = getServerPositionJumpResyncHorizontalModelLimit(thisMove, lastMove);
        if (hDistanceAboveLimit > getModelOverLimit(thisMove.hAllowedDistance,
                horizontalLimit, SERVER_POSITION_JUMP_AIR_HORIZONTAL_RESIDUAL)) {
            tags.add("server_position_jump_resync_vertical_h_miss");
            return false;
        }
        final boolean firstAirPacketAfterResync = isFirstAirPacketAfterServerPositionJumpResync(from, to, thisMove, lastMove);
        final double verticalModel = getServerPositionJumpResyncVerticalModel(thisMove, lastMove, firstAirPacketAfterResync);
        if (matchesVerticalModel(thisMove.yDistance, verticalModel, SERVER_POSITION_JUMP_AIR_VERTICAL_RESIDUAL)
                && yDistanceAboveLimit <= Math.abs(thisMove.yAllowedDistance - verticalModel)
                        + SERVER_POSITION_JUMP_AIR_VERTICAL_RESIDUAL) {
            if (firstAirPacketAfterResync) {
                tags.add("server_position_jump_air_resync_first_gravity_model");
            }
            tags.add("server_position_jump_air_resync_vertical_model");
            return true;
        }
        tags.add("server_position_jump_resync_vertical_model_miss");
        return false;
    }

    private double getServerPositionJumpResyncVerticalModel(final PlayerMoveData thisMove,
                                                            final PlayerMoveData lastMove,
                                                            final boolean firstAirPacketAfterResync) {
        if (!lastMove.toIsValid) {
            if (firstAirPacketAfterResync) {
                // Teleport model: the first air packet after stale movement history starts vanilla gravity from rest.
                return -Magic.DEFAULT_GRAVITY * Magic.FRICTION_MEDIUM_AIR;
            }
            return thisMove.yAllowedDistance;
        }
        // Teleport model: after an async position jump, the next air packet should continue vanilla gravity from history.
        return (lastMove.yDistance - Magic.DEFAULT_GRAVITY) * Magic.FRICTION_MEDIUM_AIR;
    }

    private boolean isFirstAirPacketAfterServerPositionJumpResync(final PlayerLocation from,
                                                                  final PlayerLocation to,
                                                                  final PlayerMoveData thisMove,
                                                                  final PlayerMoveData lastMove) {
        return !lastMove.toIsValid
                && thisMove.yDistance < 0.0D
                && !from.isOnGroundOrResetCond() && !to.isOnGroundOrResetCond()
                && !thisMove.from.onGroundOrResetCond && !thisMove.to.onGroundOrResetCond;
    }

    // Bedrock movement models: keep Bedrock packet behavior separate unless it shares the exact same shape envelope as Java.
    private boolean acceptsBedrockGroundedCombatHorizontalModel(final Player player, final IPlayerData pData,
                                                                final PlayerLocation from, final PlayerLocation to,
                                                                final PlayerMoveData thisMove,
                                                                final double hDistanceAboveLimit) {
        if (!isBedrockGroundedCombatContext(player, pData, from, to, thisMove)) {
            return false;
        }
        // Model cleanup: Bedrock combat is bounded by ground input plus the current server knockback/launch vector.
        final double limit = Math.min(BEDROCK_GROUNDED_COMBAT_MOVE_GRACE,
                Math.max(thisMove.hAllowedDistance, getGroundCombatHorizontalModelLimit(player, thisMove)));
        return hDistanceAboveLimit <= getModelOverLimit(thisMove.hAllowedDistance,
                limit, BEDROCK_HORIZONTAL_PREDICTION_EPSILON);
    }

    private boolean acceptsBedrockGroundedCombatVerticalModel(final Player player, final IPlayerData pData,
                                                              final PlayerLocation from, final PlayerLocation to,
                                                              final PlayerMoveData thisMove,
                                                              final PlayerMoveData lastMove,
                                                              final double yDistanceAboveLimit,
                                                              final double hDistanceAboveLimit) {
        if (!isBedrockGroundedCombatContext(player, pData, from, to, thisMove)
                || hDistanceAboveLimit > BEDROCK_GROUNDED_COMBAT_HORIZONTAL_OVER_GRACE) {
            return false;
        }
        if (acceptsBedrockGroundVerticalSnapModel(thisMove, yDistanceAboveLimit)) {
            tags.add("bedrock_grounded_combat_vertical_snap_model");
            return true;
        }
        if (acceptsBedrockAirGravityResetModel(thisMove, lastMove, yDistanceAboveLimit)) {
            tags.add("bedrock_grounded_combat_air_gravity_reset_model");
            return true;
        }
        if (acceptsBedrockGroundVerticalQuantumModel(thisMove, yDistanceAboveLimit)) {
            tags.add("bedrock_grounded_combat_vertical_quantum_model");
            return true;
        }
        final double verticalLimit = Math.min(BEDROCK_GROUNDED_COMBAT_VERTICAL_MOVE_GRACE,
                Math.max(thisMove.yAllowedDistance, Math.abs(player.getVelocity().getY())
                        + Magic.PREDICTION_EPSILON));
        return Math.abs(thisMove.yDistance) <= verticalLimit
                && yDistanceAboveLimit <= getModelOverLimit(thisMove.yAllowedDistance,
                        verticalLimit, Magic.PREDICTION_EPSILON);
    }

    private boolean acceptsBedrockGroundVerticalSnapModel(final PlayerMoveData thisMove,
                                                          final double yDistanceAboveLimit) {
        /*
         * Bedrock model: after combat/ground packets, Geyser can report the
         * player as vertically snapped to the floor while Java prediction still
         * carries the launch Y. Bound by the predicted Y difference itself.
         */
        return Math.abs(thisMove.yDistance) <= Magic.PREDICTION_EPSILON
                && thisMove.yAllowedDistance > Magic.PREDICTION_EPSILON
                && thisMove.yAllowedDistance <= BEDROCK_GROUNDED_COMBAT_VERTICAL_MOVE_GRACE
                && yDistanceAboveLimit <= thisMove.yAllowedDistance + Magic.PREDICTION_EPSILON;
    }

    private boolean acceptsBedrockAirGravityResetModel(final PlayerMoveData thisMove,
                                                       final PlayerMoveData lastMove,
                                                       final double yDistanceAboveLimit) {
        /*
         * Bedrock model: after a jump/step ascent, Geyser can expose the first
         * air-gravity tick while Java prediction still carries the ascent.
         */
        if (!lastMove.toIsValid
                || lastMove.yDistance <= Magic.PREDICTION_EPSILON
                || thisMove.yAllowedDistance <= Magic.PREDICTION_EPSILON) {
            return false;
        }
        final double firstAirGravity = getAirInertiaFirstGravityModel();
        return matchesVerticalModel(thisMove.yDistance, firstAirGravity,
                BEDROCK_AIR_GRAVITY_RESET_EPSILON)
                && yDistanceAboveLimit <= Math.abs(thisMove.yAllowedDistance - firstAirGravity)
                        + BEDROCK_AIR_GRAVITY_RESET_EPSILON;
    }

    private boolean acceptsBedrockGroundVerticalQuantumModel(final PlayerMoveData thisMove,
                                                             final double yDistanceAboveLimit) {
        /*
         * Bedrock model: partial-block support can surface one 1/16th-block
         * vertical quantum while Java's ground model predicts zero or slight
         * descent. This is deliberately limited to that quantized step.
         */
        final double quantum = Math.abs(thisMove.yDistance);
        return thisMove.yAllowedDistance <= Magic.PREDICTION_EPSILON
                && Math.abs(quantum - BEDROCK_GROUND_VERTICAL_QUANTUM) <= BEDROCK_GROUND_VERTICAL_QUANTUM_EPSILON
                && yDistanceAboveLimit <= BEDROCK_GROUND_VERTICAL_QUANTUM
                        + BEDROCK_GROUND_VERTICAL_QUANTUM_EPSILON;
    }

    private boolean acceptsBedrockStepHorizontalModel(final Player player, final IPlayerData pData,
                                                      final PlayerLocation from, final PlayerLocation to,
                                                      final PlayerMoveData thisMove, final PlayerMoveData lastMove,
                                                      final double hDistanceAboveLimit) {
        if (!isBedrockStepContext(player, pData, from, to, thisMove, lastMove)) {
            return false;
        }
        final double limit = getBedrockStepHorizontalModelLimit(from, to, thisMove, lastMove);
        return hDistanceAboveLimit <= getModelOverLimit(thisMove.hAllowedDistance,
                limit, PARTIAL_SUPPORT_HORIZONTAL_MODEL_EPSILON);
    }

    private boolean acceptsBedrockStepVerticalModel(final Player player, final IPlayerData pData,
                                                    final PlayerLocation from, final PlayerLocation to,
                                                    final PlayerMoveData thisMove, final PlayerMoveData lastMove,
                                                    final double yDistanceAboveLimit,
                                                    final double hDistanceAboveLimit) {
        if (!isBedrockStepContext(player, pData, from, to, thisMove, lastMove)) {
            return false;
        }
        final double horizontalLimit = getBedrockStepHorizontalModelLimit(from, to, thisMove, lastMove);
        if (hDistanceAboveLimit > getModelOverLimit(thisMove.hAllowedDistance,
                horizontalLimit, PARTIAL_SUPPORT_HORIZONTAL_MODEL_EPSILON)) {
            return false;
        }
        if (isBedrockStepVerticalUndershootModel(thisMove)
                && yDistanceAboveLimit <= Math.abs(thisMove.yAllowedDistance)
                        + PARTIAL_SUPPORT_VERTICAL_MODEL_EPSILON) {
            tags.add("bedrock_step_vertical_undershoot_model");
            return true;
        }
        final double verticalModel = getBedrockStepVerticalModel(thisMove);
        return yDistanceAboveLimit <= getModelOverLimit(thisMove.yAllowedDistance,
                verticalModel, PARTIAL_SUPPORT_VERTICAL_MODEL_EPSILON);
    }

    private boolean acceptsGroundedVerticalVelocityHorizontalModel(final Player player,
                                                                   final PlayerLocation from,
                                                                   final PlayerLocation to,
                                                                   final PlayerMoveData thisMove,
                                                                   final double hDistanceAboveLimit) {
        if (!isGroundedVerticalVelocityContext(player, from, to, thisMove)) {
            return false;
        }
        final double limit = getGroundedVerticalVelocityHorizontalModelLimit(player, thisMove);
        return hDistanceAboveLimit <= getModelOverLimit(thisMove.hAllowedDistance,
                limit, BEDROCK_HORIZONTAL_PREDICTION_EPSILON);
    }

    private boolean acceptsGroundedVerticalVelocityVerticalModel(final Player player,
                                                                 final PlayerLocation from,
                                                                 final PlayerLocation to,
                                                                 final PlayerMoveData thisMove,
                                                                 final PlayerMoveData lastMove,
                                                                 final double yDistanceAboveLimit,
                                                                 final double hDistanceAboveLimit) {
        if (!isGroundedVerticalVelocityContext(player, from, to, thisMove)) {
            return false;
        }
        final double horizontalLimit = getGroundedVerticalVelocityHorizontalModelLimit(player, thisMove);
        if (hDistanceAboveLimit > getModelOverLimit(thisMove.hAllowedDistance,
                horizontalLimit, BEDROCK_HORIZONTAL_PREDICTION_EPSILON)) {
            return false;
        }
        final double velocityY = player.getVelocity().getY();
        if (Math.abs(thisMove.yDistance - velocityY) <= CURRENT_SERVER_VELOCITY_VERTICAL_MATCH_GRACE
                && yDistanceAboveLimit <= Math.abs(thisMove.yAllowedDistance - velocityY)
                        + CURRENT_SERVER_VELOCITY_VERTICAL_MATCH_GRACE) {
            tags.add("grounded_vertical_velocity_y_model");
            return true;
        }
        if (acceptsGroundedVerticalVelocityGravityContinuationModel(thisMove, lastMove, yDistanceAboveLimit)) {
            tags.add("grounded_vertical_velocity_gravity_continuation_model");
            return true;
        }
        if (acceptsGroundedBouncyVerticalVelocityModel(player, from, to, thisMove, yDistanceAboveLimit)) {
            tags.add("grounded_bouncy_vertical_velocity_model");
            return true;
        }
        tags.add("grounded_vertical_velocity_y_model_miss");
        return false;
    }

    private boolean acceptsGroundedVerticalVelocityGravityContinuationModel(final PlayerMoveData thisMove,
                                                                            final PlayerMoveData lastMove,
                                                                            final double yDistanceAboveLimit) {
        /*
         * Velocity/collision model: when vertical server velocity is truncated by
         * collision, the next packet can match vanilla gravity continuation from
         * the previous move better than the live Bukkit velocity vector.
         */
        if (!lastMove.toIsValid) {
            return false;
        }
        final double gravityModel = getAirInertiaVerticalModel(lastMove);
        return matchesVerticalModel(thisMove.yDistance, gravityModel, AIR_INERTIA_VERTICAL_EPSILON)
                && yDistanceAboveLimit <= Math.abs(thisMove.yAllowedDistance - gravityModel)
                        + AIR_INERTIA_VERTICAL_EPSILON;
    }

    private boolean acceptsGroundedBouncyVerticalVelocityModel(final Player player,
                                                               final PlayerLocation from,
                                                               final PlayerLocation to,
                                                               final PlayerMoveData thisMove,
                                                               final double yDistanceAboveLimit) {
        final double velocityY = player.getVelocity().getY();
        // Model cleanup: beds/slime can expose a positive server velocity while collision clips the actual Y packet.
        return (from.isOnBouncyBlock() || to.isOnBouncyBlock()
                    || thisMove.from.onBouncyBlock || thisMove.to.onBouncyBlock)
                && thisMove.collideY
                && velocityY > Magic.PREDICTION_EPSILON
                && thisMove.yDistance > Magic.PREDICTION_EPSILON
                && thisMove.yDistance <= velocityY + CURRENT_SERVER_VELOCITY_VERTICAL_MATCH_GRACE
                && yDistanceAboveLimit <= Math.abs(thisMove.yAllowedDistance - velocityY)
                        + CURRENT_SERVER_VELOCITY_VERTICAL_MATCH_GRACE;
    }

    private boolean acceptsServerVerticalVelocityHorizontalModel(final Player player, final MovingData data,
                                                                 final PlayerLocation from,
                                                                 final PlayerLocation to,
                                                                 final PlayerMoveData thisMove,
                                                                 final double hDistanceAboveLimit) {
        if (!isServerVerticalVelocityContext(player, data, from, to, thisMove)) {
            return false;
        }
        final double limit = getServerVerticalVelocityHorizontalModelLimit(player, data, thisMove);
        return hDistanceAboveLimit <= getModelOverLimit(thisMove.hAllowedDistance,
                limit, BEDROCK_HORIZONTAL_PREDICTION_EPSILON);
    }

    private boolean acceptsGroundJumpTinyHorizontalModel(final PlayerMoveData thisMove,
                                                         final double hDistanceAboveLimit) {
        if (!isGroundJumpTinyContext(thisMove)) {
            return false;
        }
        final double limit = Math.min(GROUND_JUMP_TINY_HORIZONTAL_MOVE_GRACE,
                thisMove.hAllowedDistance + GROUND_JUMP_TINY_HORIZONTAL_OVER_GRACE);
        return hDistanceAboveLimit <= getModelOverLimit(thisMove.hAllowedDistance,
                limit, Magic.PREDICTION_EPSILON);
    }

    private boolean acceptsGroundedItemResyncHorizontalModel(final PlayerMoveData thisMove,
                                                             final double hDistanceAboveLimit) {
        if (!isGroundedItemResyncContext(thisMove)) {
            return false;
        }
        final double limit = Math.min(GROUNDED_ITEM_RESYNC_MOVE_GRACE,
                thisMove.hAllowedDistance + GROUNDED_ITEM_RESYNC_HORIZONTAL_OVER_GRACE);
        return hDistanceAboveLimit <= getModelOverLimit(thisMove.hAllowedDistance,
                limit, Magic.PREDICTION_EPSILON);
    }

    private boolean acceptsCurrentServerVelocityVerticalModel(final Player player,
                                                              final PlayerLocation from, final PlayerLocation to,
                                                              final PlayerMoveData thisMove,
                                                              final PlayerMoveData lastMove,
                                                              final double yDistanceAboveLimit,
                                                              final double hDistanceAboveLimit) {
        if (!isCurrentServerVelocityVerticalContext(player, from, to, thisMove)) {
            return false;
        }
        final double horizontalLimit = getCurrentServerVelocityHorizontalModelLimit(player, thisMove, lastMove);
        // Model cleanup: the Y packet must match the current server velocity vector; only a tiny H miss is tolerated.
        return yDistanceAboveLimit <= getModelOverLimit(thisMove.yAllowedDistance,
                player.getVelocity().getY(), CURRENT_SERVER_VELOCITY_VERTICAL_MATCH_GRACE)
                && hDistanceAboveLimit <= getModelOverLimit(thisMove.hAllowedDistance,
                        horizontalLimit, CURRENT_SERVER_VELOCITY_HORIZONTAL_OVER_GRACE);
    }

    private boolean acceptsCurrentServerVelocityHorizontalModel(final Player player,
                                                                final PlayerLocation from, final PlayerLocation to,
                                                                final PlayerMoveData thisMove,
                                                                final PlayerMoveData lastMove,
                                                                final double hDistanceAboveLimit) {
        if (!isCurrentServerVelocityVerticalContext(player, from, to, thisMove)) {
            return false;
        }
        final Vector velocity = player.getVelocity();
        final double velocityH = MathUtil.dist(velocity.getX(), velocity.getZ());
        if (velocityH > Magic.PREDICTION_EPSILON
                && velocity.getX() * thisMove.xDistance + velocity.getZ() * thisMove.zDistance < -Magic.PREDICTION_EPSILON) {
            return false;
        }
        // Model cleanup: pair the current server Y velocity with its H vector plus one normal input carry.
        final double horizontalLimit = getCurrentServerVelocityHorizontalModelLimit(player, thisMove, lastMove);
        return hDistanceAboveLimit <= getModelOverLimit(thisMove.hAllowedDistance,
                horizontalLimit, CURRENT_SERVER_VELOCITY_HORIZONTAL_OVER_GRACE);
    }

    private double getCurrentServerVelocityHorizontalModelLimit(final Player player,
                                                                final PlayerMoveData thisMove,
                                                                final PlayerMoveData lastMove) {
        final Vector velocity = player.getVelocity();
        final double velocityH = MathUtil.dist(velocity.getX(), velocity.getZ());
        final double inertiaH = lastMove.toIsValid ? getAirInertiaHorizontalModel(thisMove, lastMove) : 0.0D;
        final double inputCarry = !lastMove.toIsValid
                ? Math.max(playerInputHorizontalCarry(thisMove), LAST_INVALID_RESYNC_HORIZONTAL_INPUT_CARRY)
                : playerInputHorizontalCarry(thisMove);
        // Model cleanup: current server Y velocity can pair with either current H velocity or ordinary air H carry.
        return Math.max(thisMove.hAllowedDistance,
                Math.max(velocityH + inputCarry, inertiaH)
                        + CURRENT_SERVER_VELOCITY_HORIZONTAL_OVER_GRACE);
    }

    private boolean acceptsLevitationVerticalModel(final Player player,
                                                   final PlayerMoveData thisMove,
                                                   final double yDistanceAboveLimit,
                                                   final double hDistanceAboveLimit) {
        return isLevitationMovementContext(player, thisMove)
                && hDistanceAboveLimit <= LEVITATION_HORIZONTAL_OVER_GRACE
                && yDistanceAboveLimit <= getModelOverLimit(thisMove.yAllowedDistance,
                        0.0D, LEVITATION_STALL_OVER_GRACE);
    }

    private double playerStepHorizontalModel(final PlayerMoveData thisMove) {
        return thisMove.hAllowedDistance + playerInputHorizontalCarry(thisMove);
    }

    private double getGroundCombatHorizontalModelLimit(final Player player, final PlayerMoveData thisMove) {
        final Vector velocity = player.getVelocity();
        final double velocityH = MathUtil.dist(velocity.getX(), velocity.getZ());
        double limit = velocityH + playerInputHorizontalCarry(thisMove) + BEDROCK_HORIZONTAL_PREDICTION_EPSILON;
        final double verticalLaunch = Math.max(thisMove.yDistance, velocity.getY());
        if (verticalLaunch >= BEDROCK_STEP_VERTICAL_UNDERSHOOT_MIN_MODEL
                && verticalLaunch <= BEDROCK_GROUNDED_COMBAT_VERTICAL_MOVE_GRACE) {
            limit = Math.max(limit, verticalLaunch + BEDROCK_GROUNDED_COMBAT_VERTICAL_LAUNCH_HORIZONTAL_RESIDUAL);
        }
        return limit;
    }

    private double getBedrockStepVerticalModel(final PlayerMoveData thisMove) {
        if (Math.abs(thisMove.yDistance - BEDROCK_HALF_STEP_VERTICAL_MOVE) <= BEDROCK_HALF_STEP_VERTICAL_EPSILON) {
            return BEDROCK_HALF_STEP_VERTICAL_MOVE;
        }
        if (isBedrockStepVerticalUndershootModel(thisMove)) {
            // Bedrock step model: client may send the horizontal step packet before the Java Y rise is visible.
            return 0.0D;
        }
        return thisMove.yAllowedDistance;
    }

    private boolean isBedrockStepVerticalUndershootModel(final PlayerMoveData thisMove) {
        return Math.abs(thisMove.yDistance) <= BEDROCK_STEP_VERTICAL_UNDERSHOOT_MOVE_GRACE
                && thisMove.yAllowedDistance >= BEDROCK_STEP_VERTICAL_UNDERSHOOT_MIN_MODEL;
    }

    private double getGroundedVerticalVelocityHorizontalModelLimit(final Player player,
                                                                  final PlayerMoveData thisMove) {
        final Vector velocity = player.getVelocity();
        final double velocityH = MathUtil.dist(velocity.getX(), velocity.getZ());
        return Math.min(GROUNDED_VERTICAL_VELOCITY_MOVE_GRACE,
                Math.max(thisMove.hAllowedDistance, velocityH + playerInputHorizontalCarry(thisMove)
                        + BEDROCK_HORIZONTAL_PREDICTION_EPSILON));
    }

    private double getServerVerticalVelocityHorizontalModelLimit(final Player player, final MovingData data,
                                                                 final PlayerMoveData thisMove) {
        double velocityH = MathUtil.dist(player.getVelocity().getX(), player.getVelocity().getZ());
        final List<PairEntry> queued = data.getHorizontalVelocityTracker().peekCovering(thisMove.xDistance, thisMove.zDistance,
                1, Integer.MAX_VALUE, SERVER_VERTICAL_VELOCITY_HORIZONTAL_OVER_GRACE);
        if (!queued.isEmpty()) {
            velocityH = Math.max(velocityH, getHorizontalVelocityAmount(queued));
        }
        return Math.min(SERVER_VERTICAL_VELOCITY_HORIZONTAL_MOVE_GRACE,
                Math.max(thisMove.hAllowedDistance, velocityH + playerInputHorizontalCarry(thisMove)
                        + BEDROCK_HORIZONTAL_PREDICTION_EPSILON));
    }

    private double playerInputHorizontalCarry(final PlayerMoveData thisMove) {
        return 0.20D * getHorizontalInputScale(thisMove);
    }

    private double applyEnvironmentalHorizontalLeniency(final Player player, final IPlayerData pData, final MovingData data,
                                                       final PlayerLocation from, final PlayerLocation to,
                                                       final PlayerMoveData thisMove, final PlayerMoveData lastMove,
                                                       final double hDistanceAboveLimit) {
        if (hDistanceAboveLimit <= 0.0) {
            return hDistanceAboveLimit;
        }
        if (selectExplicitMovementModel(player, pData, data, from, to, thisMove, lastMove) != MovementModelBranch.NONE) {
            return hDistanceAboveLimit;
        }
        // Model cleanup: the former environmental H graces now enter through selectExplicitMovementModel().
        return hDistanceAboveLimit;
    }

    private boolean acceptsGlidingFireworkHorizontalModel(final Player player, final IPlayerData pData,
                                                          final MovingData data,
                                                          final PlayerLocation from, final PlayerLocation to,
                                                          final PlayerMoveData thisMove,
                                                          final PlayerMoveData lastMove,
                                                          final double hDistanceAboveLimit) {
        if (!isGlidingFireworkModelContext(player, data, from, to, thisMove)
                || !tags.contains(SurvivalFlyTags.GLIDE_HORIZONTAL_PREDICTION_MISS)) {
            return false;
        }
        if (acceptsGlidingFireworkSkippedBoostHorizontalModel(player, pData, data, from, to, thisMove, lastMove,
                hDistanceAboveLimit)) {
            return true;
        }
        final double[] packetOrderModel = getFireworkPacketOrderModelVector(player, data, to, thisMove, 1);
        final double horizontalLimit = getFireworkHorizontalModelLimit(player, thisMove, packetOrderModel,
                GLIDING_FIREWORK_PACKET_ORDER_HORIZONTAL_RESIDUAL);
        if (thisMove.hDistance > horizontalLimit
                || hDistanceAboveLimit > getModelOverLimit(thisMove.hAllowedDistance,
                        horizontalLimit, GLIDING_HORIZONTAL_PRECISION_GRACE)) {
            tags.add("elytra_firework_horizontal_model_limit_miss");
            return false;
        }
        if (horizontalFireworkVectorMatches(from, to, thisMove, packetOrderModel,
                GLIDING_FIREWORK_PACKET_ORDER_HORIZONTAL_RESIDUAL,
                GLIDING_FIREWORK_PERPENDICULAR_RESIDUAL)) {
            tags.add("elytra_firework_packet_order_horizontal_model");
            return true;
        }
        final Vector velocity = player.getVelocity();
        if (horizontalFireworkVectorMatches(from, to, thisMove,
                new double[] { velocity.getX(), velocity.getY(), velocity.getZ() },
                GLIDING_FIREWORK_PACKET_ORDER_HORIZONTAL_RESIDUAL,
                GLIDING_FIREWORK_PERPENDICULAR_RESIDUAL)) {
            tags.add("elytra_firework_current_velocity_horizontal_model");
            return true;
        }
        final Vector look = TrigUtil.getLookingDirection(to, player);
        if (horizontalFireworkVectorMatches(from, to, thisMove,
                new double[] { look.getX() * 1.5D, look.getY() * 1.5D, look.getZ() * 1.5D },
                GLIDING_FIREWORK_PACKET_ORDER_HORIZONTAL_RESIDUAL,
                GLIDING_FIREWORK_PERPENDICULAR_RESIDUAL)) {
            tags.add("elytra_firework_look_horizontal_model");
            return true;
        }
        tags.add("elytra_firework_horizontal_vector_miss");
        return false;
    }

    private boolean acceptsGlidingFireworkSkippedBoostHorizontalModel(final Player player, final IPlayerData pData,
                                                                      final MovingData data,
                                                                      final PlayerLocation from,
                                                                      final PlayerLocation to,
                                                                      final PlayerMoveData thisMove,
                                                                      final PlayerMoveData lastMove,
                                                                      final double hDistanceAboveLimit) {
        if (!isGlidingFireworkModelContext(player, data, from, to, thisMove)
                || !lastMove.toIsValid
                || from.isInLiquid() || to.isInLiquid()
                || thisMove.from.inLiquid || thisMove.to.inLiquid) {
            return false;
        }
        final double[] skippedBoostModel = getGlidingNoFireworkModelVector(player, pData, data, to, lastMove);
        final double horizontalLimit = Math.max(thisMove.hAllowedDistance,
                MathUtil.dist(skippedBoostModel[0], skippedBoostModel[2])
                        + GLIDING_FIREWORK_SKIPPED_BOOST_HORIZONTAL_RESIDUAL);
        if (thisMove.hDistance > horizontalLimit
                || hDistanceAboveLimit > getModelOverLimit(thisMove.hAllowedDistance,
                        horizontalLimit, GLIDING_FIREWORK_SKIPPED_BOOST_HORIZONTAL_RESIDUAL)) {
            tags.add("elytra_firework_skipped_boost_horizontal_limit_miss");
            return false;
        }
        if (horizontalFireworkVectorMatches(from, to, thisMove, skippedBoostModel,
                GLIDING_FIREWORK_SKIPPED_BOOST_HORIZONTAL_RESIDUAL,
                GLIDING_FIREWORK_PERPENDICULAR_RESIDUAL)) {
            tags.add("elytra_firework_skipped_boost_horizontal_model");
            return true;
        }
        tags.add("elytra_firework_skipped_boost_horizontal_vector_miss");
        return false;
    }

    private boolean acceptsElytraEquippedFireworkHorizontalModel(final Player player, final MovingData data,
                                                                 final PlayerLocation from, final PlayerLocation to,
                                                                 final PlayerMoveData thisMove,
                                                                 final PlayerMoveData lastMove,
                                                                 final double hDistanceAboveLimit) {
        if (!isElytraEquippedFireworkModelContext(player, data, from, to, thisMove)) {
            return false;
        }
        final double[] packetOrderModel = getElytraEquippedFireworkModelVector(player, data, to, thisMove, lastMove);
        final double horizontalLimit = getElytraEquippedFireworkHorizontalModelLimit(player, from, to,
                thisMove, packetOrderModel);
        if (thisMove.hDistance > horizontalLimit
                || hDistanceAboveLimit > getModelOverLimit(thisMove.hAllowedDistance,
                        horizontalLimit, ELYTRA_EQUIPPED_FIREWORK_HORIZONTAL_RESIDUAL)) {
            tags.add("elytra_equipped_firework_horizontal_model_limit_miss");
            return false;
        }
        if (horizontalFireworkVectorMatches(from, to, thisMove, packetOrderModel,
                ELYTRA_EQUIPPED_FIREWORK_HORIZONTAL_RESIDUAL,
                GLIDING_FIREWORK_PERPENDICULAR_RESIDUAL)) {
            tags.add("elytra_equipped_firework_packet_order_horizontal_model");
            return true;
        }
        final Vector velocity = player.getVelocity();
        if (horizontalFireworkVectorMatches(from, to, thisMove,
                new double[] { velocity.getX(), velocity.getY(), velocity.getZ() },
                ELYTRA_EQUIPPED_FIREWORK_HORIZONTAL_RESIDUAL,
                GLIDING_FIREWORK_PERPENDICULAR_RESIDUAL)) {
            tags.add("elytra_equipped_firework_current_velocity_horizontal_model");
            return true;
        }
        tags.add("elytra_equipped_firework_horizontal_vector_miss");
        return false;
    }

    private double getElytraEquippedFireworkHorizontalModelLimit(final Player player,
                                                                 final PlayerLocation from,
                                                                 final PlayerLocation to,
                                                                 final PlayerMoveData thisMove,
                                                                 final double[] model) {
        double limit = getFireworkHorizontalModelLimit(player, thisMove, model,
                ELYTRA_EQUIPPED_FIREWORK_HORIZONTAL_RESIDUAL);
        if (isGroundishStepMove(from, to, thisMove)) {
            /*
             * Elytra model: if Bukkit still reports "wearing elytra on ground"
             * while a rocket boost is active, the packet can contain both the
             * boost vector and one normal ground-input carry.
             */
            final double modelH = MathUtil.dist(model[0], model[2]);
            limit = Math.max(limit, Math.max(thisMove.hAllowedDistance,
                    modelH + playerInputHorizontalCarry(thisMove))
                    + ELYTRA_EQUIPPED_FIREWORK_HORIZONTAL_RESIDUAL);
        }
        return limit;
    }

    private boolean isGlidingFireworkModelContext(final Player player, final MovingData data,
                                                  final PlayerLocation from, final PlayerLocation to,
                                                  final PlayerMoveData thisMove) {
        // Model cleanup: firework boost prediction is only valid while the client is actively gliding in open space.
        return Bridge1_9.isGliding(player)
                && data.fireworksBoostDuration > 0
                && tags.contains(SurvivalFlyTags.GLIDE_FIREWORK_ACTIVE)
                && !from.isInLiquid() && !to.isInLiquid()
                && !thisMove.from.inLiquid && !thisMove.to.inLiquid;
    }

    private boolean isElytraEquippedFireworkModelContext(final Player player, final MovingData data,
                                                         final PlayerLocation from, final PlayerLocation to,
                                                         final PlayerMoveData thisMove) {
        // Bedrock/modern client model: rockets can arrive while elytra is equipped before Bukkit reports gliding.
        return Bridge1_9.isWearingElytra(player)
                && !Bridge1_9.isGliding(player)
                && data.fireworksBoostDuration > 0
                && !from.isInLiquid() && !to.isInLiquid()
                && !thisMove.from.inLiquid && !thisMove.to.inLiquid;
    }

    private double[] getFireworkPacketOrderModelVector(final Player player, final MovingData data,
                                                       final PlayerLocation to, final PlayerMoveData thisMove,
                                                       final int extraTicks) {
        double x = thisMove.xAllowedDistance;
        double y = thisMove.yAllowedDistance;
        double z = thisMove.zAllowedDistance;
        for (int i = 0; i < extraTicks; i++) {
            final double[] next = applyFireworkBoostTick(player, data, to, x, y, z);
            x = next[0];
            y = next[1];
            z = next[2];
        }
        return new double[] { x, y, z };
    }

    private double[] getElytraEquippedFireworkModelVector(final Player player, final MovingData data,
                                                          final PlayerLocation to, final PlayerMoveData thisMove,
                                                          final PlayerMoveData lastMove) {
        final Vector velocity = player.getVelocity();
        final double baseX = lastMove.toIsValid ? lastMove.xDistance : velocity.getX();
        final double baseY = lastMove.toIsValid ? lastMove.yDistance : velocity.getY();
        final double baseZ = lastMove.toIsValid ? lastMove.zDistance : velocity.getZ();
        return applyFireworkBoostTick(player, data, to, baseX, baseY, baseZ);
    }

    private double[] getFireworkInertialLookModelVector(final Player player, final MovingData data,
                                                        final PlayerLocation to, final PlayerMoveData lastMove) {
        final Vector velocity = player.getVelocity();
        final double baseX = lastMove.toIsValid ? lastMove.xDistance : velocity.getX();
        final double baseY = lastMove.toIsValid ? lastMove.yDistance : velocity.getY();
        final double baseZ = lastMove.toIsValid ? lastMove.zDistance : velocity.getZ();
        return applyFireworkBoostTick(player, data, to, baseX, baseY, baseZ);
    }

    private double[] applyFireworkBoostTick(final Player player, final MovingData data, final PlayerLocation to,
                                            final double baseX, final double baseY, final double baseZ) {
        // Model cleanup: mirror the vanilla firework boost step instead of accepting the actual packet distance.
        final Vector look = TrigUtil.getLookingDirection(to, player);
        double x = baseX + look.getX() * 0.1D + (look.getX() * 1.5D - baseX) * 0.5D;
        double y = baseY + look.getY() * 0.1D + (look.getY() * 1.5D - baseY) * 0.5D;
        double z = baseZ + look.getZ() * 0.1D + (look.getZ() * 1.5D - baseZ) * 0.5D;
        x *= 0.99D;
        y *= data.lastFrictionVertical;
        z *= 0.99D;
        return new double[] { x, y, z };
    }

    private double getFireworkHorizontalModelLimit(final Player player, final PlayerMoveData thisMove,
                                                   final double[] model, final double residual) {
        final Vector velocity = player.getVelocity();
        final double velocityH = MathUtil.dist(velocity.getX(), velocity.getZ());
        final double modelH = MathUtil.dist(model[0], model[2]);
        return Math.max(thisMove.hAllowedDistance, Math.max(velocityH, modelH)) + residual;
    }

    private boolean horizontalFireworkVectorMatches(final PlayerLocation from, final PlayerLocation to,
                                                    final PlayerMoveData thisMove, final double[] model,
                                                    final double amountResidual,
                                                    final double perpendicularResidual) {
        final double modelH = MathUtil.dist(model[0], model[2]);
        if (modelH <= Magic.PREDICTION_EPSILON) {
            return thisMove.hDistance <= amountResidual;
        }
        final double yawTurn = Math.min(90.0D,
                Math.abs(getYawDelta(from.getYaw(), to.getYaw())) + GLIDING_FIREWORK_TURN_YAW_EXTRA);
        final double turnPerpendicular = modelH * Math.sin(yawTurn * TrigUtil.toRadians) + perpendicularResidual;
        return horizontalVelocityVectorMatches(model[0], model[2], thisMove.xDistance, thisMove.zDistance,
                amountResidual, turnPerpendicular);
    }

    private boolean verticalFireworkModelMatches(final Player player, final PlayerMoveData thisMove,
                                                 final double modelY, final double residual,
                                                 final double yDistanceAboveLimit) {
        if (Math.abs(thisMove.yDistance - modelY) <= residual) {
            return true;
        }
        final double velocityY = player.getVelocity().getY();
        final double verticalLimit = Math.max(Math.abs(modelY), Math.abs(velocityY)) + residual;
        return Math.abs(thisMove.yDistance) <= verticalLimit
                && yDistanceAboveLimit <= Math.abs(thisMove.yAllowedDistance - modelY) + residual;
    }

    private boolean isPortalTransitionHorizontalGrace(final PlayerLocation from, final PlayerLocation to,
                                                      final MovingData data, final PlayerMoveData thisMove,
                                                      final PlayerMoveData lastMove,
                                                      final double hDistanceAboveLimit) {
        // False-positive tuning: portal transitions can invalidate the previous move before the client finishes drifting.
        return isPortalNear(from, to)
                && (!lastMove.toIsValid || data.liftOffEnvelope == LiftOffEnvelope.UNKNOWN)
                && hDistanceAboveLimit <= PORTAL_TRANSITION_HORIZONTAL_OVER_GRACE
                && thisMove.hDistance <= PORTAL_TRANSITION_HORIZONTAL_MOVE_GRACE;
    }

    private boolean isServerPositionJumpGroundResyncHorizontalGrace(final IPlayerData pData,
                                                                    final PlayerLocation from, final PlayerLocation to,
                                                                    final PlayerMoveData thisMove,
                                                                    final double hDistanceAboveLimit) {
        // Folia/teleport compatibility: NET_MOVING may grace a server-side position jump one packet before SurvivalFly catches up.
        final NetData netData = pData.getGenericInstance(NetData.class);
        final long age = netData.getServerPositionJumpGraceAge(System.currentTimeMillis());
        return age >= 0L
                && age <= SERVER_POSITION_JUMP_SURVIVALFLY_GRACE_MS
                && isGroundishStepMove(from, to, thisMove)
                && Math.abs(thisMove.yDistance) <= Magic.PREDICTION_EPSILON
                && hDistanceAboveLimit <= SERVER_POSITION_JUMP_HORIZONTAL_OVER_GRACE
                && thisMove.hDistance <= SERVER_POSITION_JUMP_HORIZONTAL_MOVE_GRACE
                && !from.isInLiquid() && !to.isInLiquid()
                && !thisMove.from.inLiquid && !thisMove.to.inLiquid;
    }

    private boolean acceptsElytraEquippedQueuedVelocityHorizontalModel(final Player player, final MovingData data,
                                                                       final PlayerLocation from, final PlayerLocation to,
                                                                       final PlayerMoveData thisMove,
                                                                       final double hDistanceAboveLimit) {
        if (!Bridge1_9.isWearingElytra(player)
                || Bridge1_9.isGliding(player)
                || !(data.getHorizontalVelocityTracker().hasQueued() || !thisMove.verVelUsed.isEmpty())
                || from.isInLiquid() || to.isInLiquid()
                || thisMove.from.inLiquid || thisMove.to.inLiquid) {
            return false;
        }
        final List<PairEntry> queued = data.getHorizontalVelocityTracker().peekCovering(thisMove.xDistance,
                thisMove.zDistance, 1, Integer.MAX_VALUE, ELYTRA_EQUIPPED_VELOCITY_HORIZONTAL_RESIDUAL);
        final double horizontalLimit = getElytraEquippedQueuedVelocityHorizontalModelLimit(player, thisMove, queued);
        final double verticalLimit = getElytraEquippedQueuedVelocityVerticalModelLimit(player, thisMove);
        if (thisMove.hDistance > horizontalLimit
                || hDistanceAboveLimit > getModelOverLimit(thisMove.hAllowedDistance,
                        horizontalLimit, ELYTRA_EQUIPPED_VELOCITY_HORIZONTAL_RESIDUAL)) {
            tags.add("elytra_equipped_queued_velocity_horizontal_limit_miss");
            return false;
        }
        if (Math.abs(thisMove.yDistance) > verticalLimit) {
            tags.add("elytra_equipped_queued_velocity_vertical_limit_miss");
            return false;
        }
        if (!queued.isEmpty()) {
            data.getHorizontalVelocityTracker().use(queued.get(0).tick);
        }
        tags.add("elytra_equipped_queued_velocity_horizontal_model");
        return true;
    }

    private double getElytraEquippedQueuedVelocityHorizontalModelLimit(final Player player,
                                                                       final PlayerMoveData thisMove,
                                                                       final List<PairEntry> queued) {
        final Vector velocity = player.getVelocity();
        final double velocityH = MathUtil.dist(velocity.getX(), velocity.getZ());
        final double queuedH = queued.isEmpty() ? 0.0D : getHorizontalVelocityAmount(queued);
        final double inputCarry = playerInputHorizontalCarry(thisMove);
        // Model cleanup: queued elytra velocity is capped by the old empirical window, but derived from actual velocity vectors.
        return Math.min(ELYTRA_EQUIPPED_QUEUED_VELOCITY_MOVE_GRACE,
                Math.max(thisMove.hAllowedDistance, Math.max(velocityH, queuedH) + inputCarry
                        + ELYTRA_EQUIPPED_VELOCITY_HORIZONTAL_RESIDUAL));
    }

    private double getElytraEquippedQueuedVelocityVerticalModelLimit(final Player player,
                                                                     final PlayerMoveData thisMove) {
        final double velocityY = Math.abs(player.getVelocity().getY());
        final double allowedY = Math.abs(thisMove.yAllowedDistance);
        return Math.min(ELYTRA_EQUIPPED_QUEUED_VELOCITY_Y_GRACE,
                Math.max(velocityY, allowedY) + ELYTRA_EQUIPPED_VELOCITY_VERTICAL_RESIDUAL);
    }

    private boolean isBedrockGroundedCombatHorizontalGrace(final Player player, final IPlayerData pData,
                                                           final PlayerLocation from, final PlayerLocation to,
                                                           final PlayerMoveData thisMove,
                                                           final double hDistanceAboveLimit) {
        // Bedrock compatibility: combat knockback packets can look like short grounded speed spikes.
        if (!isBedrockPlayer(player, pData)
                || Bridge1_9.isGliding(player)
                || hDistanceAboveLimit > BEDROCK_GROUNDED_COMBAT_HORIZONTAL_OVER_GRACE
                || thisMove.hDistance > BEDROCK_GROUNDED_COMBAT_MOVE_GRACE
                || Math.abs(thisMove.yDistance) > BEDROCK_GROUNDED_COMBAT_VERTICAL_MOVE_GRACE
                || from.isInLiquid() || to.isInLiquid()
                || thisMove.from.inLiquid || thisMove.to.inLiquid) {
            return false;
        }
        return isGroundedCombatMove(player, from, to, thisMove);
    }

    private boolean isGroundedCombatMove(final Player player, final PlayerLocation from, final PlayerLocation to,
                                         final PlayerMoveData thisMove) {
        final boolean groundish = from.isOnGroundOrResetCond() || to.isOnGroundOrResetCond()
                || thisMove.from.onGroundOrResetCond || thisMove.to.onGroundOrResetCond
                || thisMove.touchedGround || thisMove.touchedGroundWorkaround
                || tags.contains("onground_env") || tags.contains("v_air");
        if (!groundish) {
            return false;
        }
        final double velocityY = Math.abs(player.getVelocity().getY());
        return !thisMove.hasImpulse.decideOptimistically()
                || thisMove.hasAttackSlowDown
                || velocityY > Magic.PREDICTION_EPSILON
                        && velocityY <= BEDROCK_GROUNDED_COMBAT_VERTICAL_VELOCITY_GRACE;
    }

    private boolean isGroundedVerticalVelocityHorizontalGrace(final Player player,
                                                             final PlayerLocation from, final PlayerLocation to,
                                                             final PlayerMoveData thisMove,
                                                             final double hDistanceAboveLimit) {
        if (Bridge1_9.isGliding(player)
                || hDistanceAboveLimit > GROUNDED_VERTICAL_VELOCITY_HORIZONTAL_OVER_GRACE
                || thisMove.hDistance > GROUNDED_VERTICAL_VELOCITY_MOVE_GRACE
                || Math.abs(thisMove.yDistance) > GROUNDED_VERTICAL_VELOCITY_MOVE_Y_GRACE
                || from.isInLiquid() || to.isInLiquid()
                || thisMove.from.inLiquid || thisMove.to.inLiquid) {
            return false;
        }
        final boolean groundish = from.isOnGroundOrResetCond() || to.isOnGroundOrResetCond()
                || thisMove.from.onGroundOrResetCond || thisMove.to.onGroundOrResetCond
                || thisMove.touchedGround || thisMove.touchedGroundWorkaround
                || tags.contains("onground_env") || tags.contains("v_air");
        final double velocityY = player.getVelocity().getY();
        return groundish
                && velocityY > 0.30D && velocityY <= GROUNDED_VERTICAL_VELOCITY_MOVE_Y_GRACE
                && (thisMove.collidesHorizontally || thisMove.collideY || tags.contains("v_air"));
    }

    private boolean isBedrockStepHorizontalGrace(final Player player, final IPlayerData pData,
                                                 final PlayerLocation from, final PlayerLocation to,
                                                 final PlayerMoveData thisMove, final PlayerMoveData lastMove,
                                                 final double hDistanceAboveLimit) {
        // Bedrock model: step packets can report the 0.5 rise before Java's horizontal prediction catches the carry.
        final double limit = getBedrockStepHorizontalModelLimit(from, to, thisMove, lastMove);
        if (!isBedrockPlayer(player, pData)
                || Bridge1_9.isGliding(player)
                || Math.abs(thisMove.yDistance - BEDROCK_HALF_STEP_VERTICAL_MOVE) > BEDROCK_HALF_STEP_VERTICAL_EPSILON
                || !isGroundishStepMove(from, to, thisMove)
                || !isStepBlockNear(from, to)
                || thisMove.hDistance > limit
                || hDistanceAboveLimit > getModelOverLimit(thisMove.hAllowedDistance, limit, PARTIAL_SUPPORT_HORIZONTAL_MODEL_EPSILON)) {
            return false;
        }
        return !from.isInLiquid() && !to.isInLiquid()
                && !thisMove.from.inLiquid && !thisMove.to.inLiquid;
    }

    private boolean isServerVerticalVelocityHorizontalGrace(final Player player, final MovingData data,
                                                            final PlayerLocation from, final PlayerLocation to,
                                                            final PlayerMoveData thisMove,
                                                            final double hDistanceAboveLimit) {
        if (thisMove.verVelUsed.isEmpty()
                || !data.getHorizontalVelocityTracker().hasQueued()
                || hDistanceAboveLimit > SERVER_VERTICAL_VELOCITY_HORIZONTAL_OVER_GRACE
                || thisMove.hDistance > SERVER_VERTICAL_VELOCITY_HORIZONTAL_MOVE_GRACE
                || thisMove.yDistance <= 0.0D
                || thisMove.yDistance > SERVER_VERTICAL_VELOCITY_ASCEND_GRACE
                || Bridge1_9.isGliding(player)) {
            return false;
        }
        final boolean groundish = from.isOnGroundOrResetCond() || to.isOnGroundOrResetCond()
                || thisMove.from.onGroundOrResetCond || thisMove.to.onGroundOrResetCond
                || thisMove.touchedGround || thisMove.touchedGroundWorkaround
                || tags.contains("onground_env") || tags.contains("v_air");
        return groundish
                && !from.isInLiquid() && !to.isInLiquid()
                && !thisMove.from.inLiquid && !thisMove.to.inLiquid;
    }

    private boolean isQueuedVelocityHorizontalGrace(final Player player, final MovingData data,
                                                    final PlayerLocation from, final PlayerLocation to,
                                                    final PlayerMoveData thisMove,
                                                    final double hDistanceAboveLimit) {
        if (Bridge1_9.isGliding(player)
                || !(data.getHorizontalVelocityTracker().hasQueued() || !thisMove.verVelUsed.isEmpty())
                || Math.abs(thisMove.yDistance) > QUEUED_VELOCITY_VERTICAL_MOVE_GRACE
                || from.isInLiquid() || to.isInLiquid()
                || thisMove.from.inLiquid || thisMove.to.inLiquid) {
            return false;
        }
        final double limit = getQueuedVelocityHorizontalModelLimit(player, data, thisMove);
        // Model cleanup: queued velocity uses the pending vector plus one input packet, not a flat H grace.
        return thisMove.hDistance <= limit
                && hDistanceAboveLimit <= getModelOverLimit(thisMove.hAllowedDistance,
                        limit, QUEUED_VELOCITY_HORIZONTAL_RESIDUAL);
    }

    private double getQueuedVelocityHorizontalModelLimit(final Player player, final MovingData data,
                                                        final PlayerMoveData thisMove) {
        final List<PairEntry> queued = data.getHorizontalVelocityTracker().peekCovering(thisMove.xDistance, thisMove.zDistance,
                1, Integer.MAX_VALUE, QUEUED_VELOCITY_HORIZONTAL_RESIDUAL);
        final Vector velocity = player.getVelocity();
        final double queuedH = queued.isEmpty() ? 0.0D : getHorizontalVelocityAmount(queued);
        final double serverH = MathUtil.dist(velocity.getX(), velocity.getZ());
        final double model = Math.max(queuedH, serverH) + playerInputHorizontalCarry(thisMove)
                + QUEUED_VELOCITY_HORIZONTAL_RESIDUAL;
        return Math.min(QUEUED_VELOCITY_HORIZONTAL_MOVE_GRACE,
                Math.max(thisMove.hAllowedDistance, model));
    }

    private boolean isModernVerticalImpulseHorizontalGrace(final Player player, final IPlayerData pData,
                                                           final PlayerLocation from, final PlayerLocation to,
                                                           final PlayerMoveData thisMove,
                                                           final double hDistanceAboveLimit) {
        // False-flag model: 1.21+ impulse packets are accepted only if they match the server velocity vector.
        final double horizontalLimit = getModernVerticalImpulseHorizontalModelLimit(player, thisMove);
        final double verticalLimit = getModernVerticalImpulseVerticalModelLimit(player, thisMove);
        return isModernMovementClient(pData)
                && !Bridge1_9.isGliding(player)
                && thisMove.yDistance > Magic.PREDICTION_EPSILON
                && thisMove.yDistance <= verticalLimit
                && thisMove.hDistance <= horizontalLimit
                && hDistanceAboveLimit <= getModelOverLimit(thisMove.hAllowedDistance,
                        horizontalLimit, MODERN_VERTICAL_IMPULSE_HORIZONTAL_VELOCITY_GRACE)
                && hasModernVerticalImpulseSource(player, thisMove)
                && currentHorizontalVelocityMatches(player, thisMove, MODERN_VERTICAL_IMPULSE_HORIZONTAL_VELOCITY_GRACE)
                && !from.isInLiquid() && !to.isInLiquid()
                && !thisMove.from.inLiquid && !thisMove.to.inLiquid;
    }

    private boolean isGroundJumpTinyHorizontalGrace(final PlayerMoveData thisMove,
                                                    final double hDistanceAboveLimit) {
        // False-positive tuning: ordinary jump packets can miss X/Z by a centimeter or two on newer clients.
        return (tags.contains("jump_env") || tags.contains("bunnyhop"))
                && thisMove.yDistance > 0.0D
                && thisMove.yDistance <= GROUNDED_JUMP_VERTICAL_MOVE_GRACE
                && hDistanceAboveLimit <= GROUND_JUMP_TINY_HORIZONTAL_OVER_GRACE
                && thisMove.hDistance <= GROUND_JUMP_TINY_HORIZONTAL_MOVE_GRACE;
    }

    private boolean isGroundedItemResyncHorizontalGrace(final PlayerMoveData thisMove,
                                                        final double hDistanceAboveLimit) {
        return tags.contains("itemresync")
                && tags.contains("usingitem")
                && tags.contains("onground_env")
                && Math.abs(thisMove.yDistance) <= Magic.PREDICTION_EPSILON
                && hDistanceAboveLimit <= GROUNDED_ITEM_RESYNC_HORIZONTAL_OVER_GRACE
                && thisMove.hDistance <= GROUNDED_ITEM_RESYNC_MOVE_GRACE;
    }

    private boolean isThinSupportHorizontalGrace(final Player player,
                                                 final PlayerLocation from, final PlayerLocation to,
                                                 final PlayerMoveData thisMove,
                                                 final double hDistanceAboveLimit) {
        // False-positive tuning: thin supports can be valid while the surrounding movement model still looks like air.
        return !Bridge1_9.isGliding(player)
                && isThinSupportNear(from, to)
                && isGroundishStepMove(from, to, thisMove)
                && hDistanceAboveLimit <= THIN_SUPPORT_HORIZONTAL_OVER_GRACE
                && thisMove.hDistance <= THIN_SUPPORT_HORIZONTAL_MOVE_GRACE
                && thisMove.yDistance >= -Magic.PREDICTION_EPSILON
                && thisMove.yDistance <= THIN_SUPPORT_VERTICAL_MOVE_GRACE
                && !from.isInLiquid() && !to.isInLiquid()
                && !thisMove.from.inLiquid && !thisMove.to.inLiquid;
    }

    private boolean isWaterHorizontalGrace(final Player player, final MovingData data,
                                           final PlayerLocation from, final PlayerLocation to,
                                           final PlayerMoveData thisMove,
                                           final double hDistanceAboveLimit) {
        // False-flag model: reuse the liquid velocity model in legacy fallback paths.
        final boolean inWater = from.isInWater() || to.isInWater()
                || thisMove.from.inWater || thisMove.to.inWater;
        if (!inWater) {
            return false;
        }
        if (tags.contains("v_water") && acceptsWaterHorizontalModel(player, data, from, to, thisMove, hDistanceAboveLimit, true)) {
            tags.add("water_dolphin_horizontal_model");
            return true;
        }
        return acceptsWaterHorizontalModel(player, data, from, to, thisMove, hDistanceAboveLimit, false);
    }

    // Climbable models: vines, ladders, and scaffolding share climbable physics with surface-specific caps.
    private ClimbableSurfaceModel getClimbableSurfaceModel(final PlayerLocation from, final PlayerLocation to) {
        if (isScaffoldingNear(from, to)) {
            return ClimbableSurfaceModel.SCAFFOLDING;
        }
        if (isVineClimbableNear(from, to)) {
            return ClimbableSurfaceModel.VINES;
        }
        return ClimbableSurfaceModel.GENERIC;
    }

    private double getClimbableHorizontalModelLimit(final ClimbableSurfaceModel model,
                                                    final PlayerMoveData thisMove) {
        // Model cleanup: vanilla clamps climbable X/Z per axis, so diagonal vine movement can exceed the old scalar cap.
        final double axisClampCap = Math.max(model.horizontalMoveCap, CLIMBABLE_DIAGONAL_AXIS_CAP);
        final double climbableLimit = Math.min(axisClampCap,
                thisMove.hAllowedDistance + playerInputHorizontalCarry(thisMove) + model.horizontalResidual);
        return Math.max(thisMove.hAllowedDistance + model.horizontalResidual, climbableLimit);
    }

    private boolean isScaffoldingNear(final PlayerLocation from, final PlayerLocation to) {
        return isScaffoldingBlock(from.getBlockType())
                || isScaffoldingBlock(from.getBlockTypeBelow())
                || isScaffoldingBlock(to.getBlockType())
                || isScaffoldingBlock(to.getBlockTypeBelow());
    }

    private boolean isScaffoldingBlock(final Material material) {
        return material != null && material == BridgeMaterial.SCAFFOLDING;
    }

    private boolean isVineClimbableNear(final PlayerLocation from, final PlayerLocation to) {
        return isVineClimbableBlock(from.getBlockType())
                || isVineClimbableBlock(from.getBlockTypeBelow())
                || isVineClimbableBlock(to.getBlockType())
                || isVineClimbableBlock(to.getBlockTypeBelow());
    }

    private boolean isVineClimbableBlock(final Material material) {
        return material != null && material.name().contains("VINE");
    }

    private boolean acceptsBaseClimbableHorizontalModel(final PlayerLocation from, final PlayerLocation to,
                                                        final PlayerMoveData thisMove,
                                                        final double hDistanceAboveLimit) {
        // Model cleanup: vines, ladders, and scaffolding use climbable drag instead of the open-air H envelope.
        final boolean climbable = from.isOnClimbable() || to.isOnClimbable()
                || thisMove.from.onClimbable || thisMove.to.onClimbable;
        final ClimbableSurfaceModel model = getClimbableSurfaceModel(from, to);
        final double horizontalLimit = getClimbableHorizontalModelLimit(model, thisMove);
        final double ascendLimit = getClimbableAscendModelLimit(model, thisMove);
        final double descendLimit = getClimbableDescendModelLimit(model, thisMove);
        final boolean accepted = climbable
                && !from.isInLiquid() && !to.isInLiquid()
                && !thisMove.from.inLiquid && !thisMove.to.inLiquid
                && thisMove.hDistance <= horizontalLimit
                && hDistanceAboveLimit <= getModelOverLimit(thisMove.hAllowedDistance,
                        horizontalLimit, model.horizontalResidual)
                && thisMove.yDistance >= -descendLimit
                && thisMove.yDistance <= ascendLimit;
        if (accepted) {
            tags.add(model.tag + "_horizontal_model");
            if (ascendLimit > model.ascendLimit || descendLimit > model.descendLimit) {
                tags.add(model.tag + "_vertical_envelope_horizontal_model");
            }
        }
        return accepted;
    }

    private double getClimbableAscendModelLimit(final ClimbableSurfaceModel model,
                                                final PlayerMoveData thisMove) {
        // Model cleanup: entering vines can preserve a server-predicted upward packet; use that Y envelope instead of a flat grace.
        final double predictedAscend = Math.max(thisMove.yAllowedDistance, 0.0D)
                + model.verticalPrecision;
        return Math.max(model.ascendLimit, predictedAscend);
    }

    private double getClimbableDescendModelLimit(final ClimbableSurfaceModel model,
                                                 final PlayerMoveData thisMove) {
        // Model cleanup: falling into vines can keep the normal gravity packet while horizontal motion is already climbable-clamped.
        final double predictedDescend = Math.max(-thisMove.yAllowedDistance, 0.0D)
                + model.verticalPrecision;
        return Math.max(model.descendLimit, predictedDescend);
    }

    private boolean acceptsClimbableHorizontalModel(final PlayerLocation from, final PlayerLocation to,
                                                    final PlayerMoveData thisMove, final PlayerMoveData lastMove,
                                                    final double hDistanceAboveLimit) {
        if (acceptsBaseClimbableHorizontalModel(from, to, thisMove, hDistanceAboveLimit)) {
            return true;
        }
        if (acceptsClimbableEntryCarryHorizontalModel(from, to, thisMove, hDistanceAboveLimit)) {
            return true;
        }
        return acceptsClimbableJumpCarryHorizontalModel(from, to, thisMove, lastMove, hDistanceAboveLimit);
    }

    private boolean acceptsClimbableEntryCarryHorizontalModel(final PlayerLocation from, final PlayerLocation to,
                                                              final PlayerMoveData thisMove,
                                                              final double hDistanceAboveLimit) {
        final boolean climbable = from.isOnClimbable() || to.isOnClimbable()
                || thisMove.from.onClimbable || thisMove.to.onClimbable;
        if (!climbable
                || from.isInLiquid() || to.isInLiquid()
                || thisMove.from.inLiquid || thisMove.to.inLiquid
                || Math.abs(thisMove.yDistance) > CLIMBABLE_VERTICAL_PRECISION_GRACE
                || !(from.isOnGroundOrResetCond() || to.isOnGroundOrResetCond()
                        || thisMove.from.onGroundOrResetCond || thisMove.to.onGroundOrResetCond)) {
            return false;
        }
        /*
         * Climbable model: entering vines/ladders can keep one ground-input
         * horizontal packet while vertical motion is clamped to zero.
         */
        final ClimbableSurfaceModel model = getClimbableSurfaceModel(from, to);
        final double limit = Math.max(getClimbableHorizontalModelLimit(model, thisMove),
                CLIMBABLE_ENTRY_HORIZONTAL_CARRY);
        if (thisMove.hDistance <= limit
                && hDistanceAboveLimit <= getModelOverLimit(thisMove.hAllowedDistance,
                        limit, model.horizontalResidual)) {
            tags.add(model.tag + "_entry_horizontal_model");
            return true;
        }
        tags.add(model.tag + "_entry_horizontal_model_miss");
        return false;
    }

    private boolean acceptsClimbableJumpCarryHorizontalModel(final PlayerLocation from, final PlayerLocation to,
                                                            final PlayerMoveData thisMove, final PlayerMoveData lastMove,
                                                            final double hDistanceAboveLimit) {
        final boolean climbable = from.isOnClimbable() || to.isOnClimbable()
                || thisMove.from.onClimbable || thisMove.to.onClimbable;
        final ClimbableSurfaceModel model = getClimbableSurfaceModel(from, to);
        if (!climbable
                || from.isInLiquid() || to.isInLiquid()
                || thisMove.from.inLiquid || thisMove.to.inLiquid
                || !lastMove.toIsValid
                || !(tags.contains("jump_env") || thisMove.isJump)
                || thisMove.yDistance < model.ascendLimit
                || thisMove.yDistance > GROUNDED_JUMP_VERTICAL_MOVE_GRACE) {
            return false;
        }
        // Climbable model: scaffolding/ladder jumps keep last-tick horizontal carry even when current input is zero.
        final double horizontalLimit = Math.max(model.horizontalMoveCap,
                lastMove.hDistance + CLIMBABLE_JUMP_CARRY_HORIZONTAL_EPSILON);
        if (thisMove.hDistance <= horizontalLimit
                && hDistanceAboveLimit <= getModelOverLimit(thisMove.hAllowedDistance,
                        horizontalLimit, CLIMBABLE_JUMP_CARRY_HORIZONTAL_EPSILON)) {
            tags.add(model.tag + "_jump_carry_horizontal_model");
            return true;
        }
        tags.add(model.tag + "_jump_carry_horizontal_model_miss");
        return false;
    }

    private boolean acceptsElytraEquippedVelocityHorizontalModel(final Player player,
                                                                 final PlayerLocation from, final PlayerLocation to,
                                                                 final PlayerMoveData thisMove, final PlayerMoveData lastMove,
                                                                 final double hDistanceAboveLimit) {
        return acceptsElytraEquippedVelocityMoveModel(player, from, to, thisMove, lastMove, 0.0D, hDistanceAboveLimit, false);
    }

    private boolean acceptsElytraEquippedGroundHorizontalModel(final Player player,
                                                               final PlayerLocation from, final PlayerLocation to,
                                                               final PlayerMoveData thisMove,
                                                               final PlayerMoveData lastMove,
                                                               final double hDistanceAboveLimit) {
        if (!Bridge1_9.isWearingElytra(player)
                || Bridge1_9.isGliding(player)
                || !isGroundishStepMove(from, to, thisMove)
                || Math.abs(thisMove.yDistance) > ELYTRA_EQUIPPED_GROUND_STEP_VERTICAL_GRACE
                || from.isInLiquid() || to.isInLiquid()
                || thisMove.from.inLiquid || thisMove.to.inLiquid) {
            return false;
        }
        final double horizontalLimit = getElytraEquippedGroundHorizontalModelLimit(player, thisMove, lastMove);
        if (thisMove.hDistance <= horizontalLimit
                && hDistanceAboveLimit <= getModelOverLimit(thisMove.hAllowedDistance,
                        horizontalLimit, ELYTRA_EQUIPPED_GROUND_HORIZONTAL_OVER_GRACE)) {
            tags.add("elytra_equipped_ground_horizontal_model");
            return true;
        }
        tags.add("elytra_equipped_ground_horizontal_model_miss");
        return false;
    }

    private double getElytraEquippedGroundHorizontalModelLimit(final Player player,
                                                               final PlayerMoveData thisMove,
                                                               final PlayerMoveData lastMove) {
        final Vector velocity = player.getVelocity();
        final double velocityH = MathUtil.dist(velocity.getX(), velocity.getZ());
        final double lastCarry = lastMove.toIsValid ? lastMove.hDistance : thisMove.hAllowedDistance;
        final double landingInertia = lastMove.toIsValid ? lastMove.hDistance * Magic.AIR_HORIZONTAL_INERTIA : 0.0D;
        // Model cleanup: pre-glide ground packets use normal step input, server velocity, or one landing-inertia tick.
        return Math.min(ELYTRA_EQUIPPED_GROUND_MOVE_GRACE,
                Math.max(Math.max(playerStepHorizontalModel(thisMove),
                                Math.max(lastCarry, landingInertia) + ELYTRA_EQUIPPED_GROUND_HORIZONTAL_OVER_GRACE),
                        velocityH + playerInputHorizontalCarry(thisMove)
                        + ELYTRA_EQUIPPED_GROUND_HORIZONTAL_OVER_GRACE));
    }

    private boolean acceptsElytraEquippedGlideCoastHorizontalModel(final Player player,
                                                                   final IPlayerData pData,
                                                                   final MovingData data,
                                                                   final PlayerLocation from,
                                                                   final PlayerLocation to,
                                                                   final PlayerMoveData thisMove,
                                                                   final PlayerMoveData lastMove,
                                                                   final double hDistanceAboveLimit) {
        final double[] model = getElytraEquippedGlideCoastModel(player, pData, data, from, to, thisMove, lastMove);
        if (model == null) {
            return false;
        }
        final double horizontalLimit = Math.max(thisMove.hAllowedDistance,
                MathUtil.dist(model[0], model[2]) + ELYTRA_EQUIPPED_GLIDE_COAST_HORIZONTAL_RESIDUAL);
        if (thisMove.hDistance <= horizontalLimit
                && hDistanceAboveLimit <= getModelOverLimit(thisMove.hAllowedDistance,
                        horizontalLimit, ELYTRA_EQUIPPED_GLIDE_COAST_HORIZONTAL_RESIDUAL)
                && horizontalVelocityVectorMatches(model[0], model[2], thisMove.xDistance, thisMove.zDistance,
                        ELYTRA_EQUIPPED_GLIDE_COAST_HORIZONTAL_RESIDUAL,
                        ELYTRA_EQUIPPED_GLIDE_COAST_PERPENDICULAR_RESIDUAL)) {
            tags.add("elytra_equipped_glide_coast_horizontal_model");
            return true;
        }
        tags.add("elytra_equipped_glide_coast_horizontal_model_miss");
        return false;
    }

    private boolean acceptsElytraEquippedGlideCoastVerticalModel(final Player player,
                                                                 final IPlayerData pData,
                                                                 final MovingData data,
                                                                 final PlayerLocation from,
                                                                 final PlayerLocation to,
                                                                 final PlayerMoveData thisMove,
                                                                 final PlayerMoveData lastMove,
                                                                 final double yDistanceAboveLimit,
                                                                 final double hDistanceAboveLimit) {
        final double[] model = getElytraEquippedGlideCoastModel(player, pData, data, from, to, thisMove, lastMove);
        if (model == null) {
            return false;
        }
        final double horizontalLimit = Math.max(thisMove.hAllowedDistance,
                MathUtil.dist(model[0], model[2]) + ELYTRA_EQUIPPED_GLIDE_COAST_HORIZONTAL_RESIDUAL);
        if (thisMove.hDistance <= horizontalLimit
                && hDistanceAboveLimit <= getModelOverLimit(thisMove.hAllowedDistance,
                        horizontalLimit, ELYTRA_EQUIPPED_GLIDE_COAST_HORIZONTAL_RESIDUAL)
                && horizontalVelocityVectorMatches(model[0], model[2], thisMove.xDistance, thisMove.zDistance,
                        ELYTRA_EQUIPPED_GLIDE_COAST_HORIZONTAL_RESIDUAL,
                        ELYTRA_EQUIPPED_GLIDE_COAST_PERPENDICULAR_RESIDUAL)
                && matchesVerticalModel(thisMove.yDistance, model[1], ELYTRA_EQUIPPED_GLIDE_COAST_VERTICAL_RESIDUAL)
                && yDistanceAboveLimit <= Math.abs(thisMove.yAllowedDistance - model[1])
                        + ELYTRA_EQUIPPED_GLIDE_COAST_VERTICAL_RESIDUAL) {
            tags.add("elytra_equipped_glide_coast_vertical_model");
            return true;
        }
        tags.add("elytra_equipped_glide_coast_vertical_model_miss");
        return false;
    }

    private double[] getElytraEquippedGlideCoastModel(final Player player,
                                                      final IPlayerData pData,
                                                      final MovingData data,
                                                      final PlayerLocation from,
                                                      final PlayerLocation to,
                                                      final PlayerMoveData thisMove,
                                                      final PlayerMoveData lastMove) {
        /*
         * Elytra transition model: Folia/Bukkit can clear the gliding flag before
         * the client stops applying vanilla glide physics. In that case the next
         * packet should match the normal no-firework glide tick, not walking or
         * plain air gravity.
         */
        if (!Bridge1_9.isWearingElytra(player)
                || Bridge1_9.isGliding(player)
                || data.fireworksBoostDuration > 0
                || !lastMove.toIsValid
                || lastMove.hDistance < ELYTRA_EQUIPPED_GLIDE_COAST_MIN_LAST_HORIZONTAL
                || from.isInLiquid() || to.isInLiquid()
                || thisMove.from.inLiquid || thisMove.to.inLiquid) {
            return null;
        }
        return getGlidingNoFireworkModelVector(player, pData, data, to, lastMove);
    }

    private boolean acceptsElytraEquippedDescendHorizontalModel(final Player player,
                                                                final PlayerMoveData thisMove,
                                                                final double hDistanceAboveLimit) {
        if (!Bridge1_9.isWearingElytra(player)
                || Bridge1_9.isGliding(player)
                || thisMove.yDistance >= 0.0D
                || thisMove.from.inLiquid || thisMove.to.inLiquid) {
            return false;
        }
        final double horizontalLimit = getElytraEquippedDescendHorizontalModelLimit(player, thisMove);
        if (thisMove.hDistance <= horizontalLimit
                && hDistanceAboveLimit <= getModelOverLimit(thisMove.hAllowedDistance,
                        horizontalLimit, ELYTRA_EQUIPPED_DESCEND_HORIZONTAL_OVER_GRACE)) {
            tags.add("elytra_equipped_descend_horizontal_model");
            return true;
        }
        tags.add("elytra_equipped_descend_horizontal_model_miss");
        return false;
    }

    private double getElytraEquippedDescendHorizontalModelLimit(final Player player,
                                                                final PlayerMoveData thisMove) {
        final Vector velocity = player.getVelocity();
        final double velocityH = MathUtil.dist(velocity.getX(), velocity.getZ());
        // Model cleanup: descending pre-glide packets may carry current velocity plus one normal air-input tick.
        return Math.min(ELYTRA_EQUIPPED_DESCEND_MOVE_GRACE,
                Math.max(thisMove.hAllowedDistance, velocityH + Magic.AIR_ACCELERATION * getHorizontalInputScale(thisMove)
                        + ELYTRA_EQUIPPED_DESCEND_HORIZONTAL_OVER_GRACE));
    }

    private boolean acceptsElytraEquippedVelocityHandoffHorizontalModel(final Player player,
                                                                       final PlayerLocation from,
                                                                       final PlayerLocation to,
                                                                       final PlayerMoveData thisMove,
                                                                       final PlayerMoveData lastMove,
                                                                       final double hDistanceAboveLimit) {
        if (!isElytraEquippedVelocityHandoffContext(player, from, to, thisMove, lastMove)) {
            return false;
        }
        final double horizontalLimit = getElytraEquippedVelocityHandoffHorizontalModelLimit(player, thisMove);
        return hDistanceAboveLimit <= getModelOverLimit(thisMove.hAllowedDistance,
                horizontalLimit, ELYTRA_EQUIPPED_VELOCITY_HANDOFF_HORIZONTAL_RESIDUAL);
    }

    private boolean acceptsElytraGeometryStallHorizontalModel(final Player player,
                                                              final PlayerMoveData thisMove, final PlayerMoveData lastMove,
                                                              final double hDistanceAboveLimit) {
        if (!isElytraGeometryStallContext(player, thisMove, lastMove)) {
            return false;
        }
        final double horizontalLimit = getElytraGeometryStallHorizontalModelLimit(thisMove, lastMove);
        if (thisMove.hDistance <= horizontalLimit
                && hDistanceAboveLimit <= getModelOverLimit(thisMove.hAllowedDistance,
                        horizontalLimit, ELYTRA_GEOMETRY_STALL_HORIZONTAL_OVER_GRACE)) {
            tags.add("elytra_geometry_stall_horizontal_model");
            return true;
        }
        tags.add("elytra_geometry_stall_horizontal_model_miss");
        return false;
    }

    private double applyEnvironmentalVerticalLeniency(final Player player, final IPlayerData pData, final MovingData data,
                                                     final PlayerLocation from, final PlayerLocation to,
                                                     final PlayerMoveData thisMove, final PlayerMoveData lastMove,
                                                     final double yDistanceAboveLimit,
                                                     final double hDistanceAboveLimit,
                                                     final boolean resetFrom, final boolean resetTo) {
        if (yDistanceAboveLimit <= 0.0) {
            return yDistanceAboveLimit;
        }
        if (selectExplicitMovementModel(player, pData, data, from, to, thisMove, lastMove) != MovementModelBranch.NONE) {
            return yDistanceAboveLimit;
        }
        // Model cleanup: the former environmental Y graces now enter through selectExplicitMovementModel().
        return yDistanceAboveLimit;
    }

    private boolean isSetbackGravityRecoveryContext(final MovingData data,
                                                    final PlayerLocation from, final PlayerLocation to,
                                                    final PlayerMoveData thisMove, final PlayerMoveData lastMove) {
        return data.hasSetBack()
                && data.timeSinceSetBack <= 5
                && (!lastMove.toIsValid || thisMove.multiMoveCount == 0)
                && thisMove.hDistance <= Magic.NEGLIGIBLE_SPEED_THRESHOLD
                && thisMove.yDistance < -Magic.PREDICTION_EPSILON
                && thisMove.yDistance >= -SETBACK_GRAVITY_VERTICAL_GRACE
                && !from.isOnGroundOrResetCond() && !to.isOnGroundOrResetCond()
                && !thisMove.from.onGroundOrResetCond && !thisMove.to.onGroundOrResetCond
                && !from.isInLiquid() && !to.isInLiquid()
                && !thisMove.from.inLiquid && !thisMove.to.inLiquid
                && Math.abs(from.getY() - data.getSetBackY()) <= SETBACK_GRAVITY_SETBACK_Y_GRACE;
    }

    private boolean acceptsSetbackGravityVerticalModel(final MovingData data,
                                                       final PlayerLocation from, final PlayerLocation to,
                                                       final PlayerMoveData thisMove, final PlayerMoveData lastMove,
                                                       final double yDistanceAboveLimit,
                                                       final double hDistanceAboveLimit) {
        if (!isSetbackGravityRecoveryContext(data, from, to, thisMove, lastMove)
                || hDistanceAboveLimit > GROUNDED_MICRO_HORIZONTAL_GRACE) {
            return false;
        }
        final double verticalModel = getSetbackGravityVerticalModel(thisMove, lastMove);
        if (thisMove.yDistance >= verticalModel - SETBACK_GRAVITY_OVER_GRACE
                && thisMove.yDistance <= Magic.PREDICTION_EPSILON
                && yDistanceAboveLimit <= getModelOverLimit(thisMove.yAllowedDistance,
                        verticalModel, SETBACK_GRAVITY_OVER_GRACE)) {
            tags.add("setback_gravity_vertical_model");
            return true;
        }
        tags.add("setback_gravity_vertical_model_miss");
        return false;
    }

    private double getSetbackGravityVerticalModel(final PlayerMoveData thisMove,
                                                  final PlayerMoveData lastMove) {
        // Model cleanup: after a setback, the next air packet should be one gravity tick below the reset speed.
        final double lastY = lastMove.toIsValid && lastMove.yDistance < 0.0D ? lastMove.yDistance : 0.0D;
        return Math.max(-SETBACK_GRAVITY_VERTICAL_GRACE,
                (lastY - Magic.DEFAULT_GRAVITY) * Magic.FRICTION_MEDIUM_AIR);
    }

    private boolean isLevitationVerticalGrace(final Player player,
                                              final PlayerMoveData thisMove,
                                              final double yDistanceAboveLimit,
                                              final double hDistanceAboveLimit) {
        return !Double.isInfinite(Bridge1_9.getLevitationAmplifier(player))
                && hDistanceAboveLimit <= LEVITATION_HORIZONTAL_OVER_GRACE
                && Math.abs(thisMove.yDistance) <= LEVITATION_STALL_VERTICAL_GRACE
                && yDistanceAboveLimit <= LEVITATION_STALL_OVER_GRACE;
    }

    private boolean acceptsGlidingVerticalPrecisionModel(final Player player,
                                                         final double yDistanceAboveLimit) {
        final boolean accepted = Bridge1_9.isGliding(player)
                && yDistanceAboveLimit <= GLIDING_VERTICAL_PRECISION_GRACE;
        if (accepted) {
            tags.add("glide_vertical_precision_model");
        }
        return accepted;
    }

    private boolean acceptsGlidingVelocityVerticalModel(final Player player,
                                                        final PlayerMoveData thisMove,
                                                        final double yDistanceAboveLimit,
                                                        final double hDistanceAboveLimit) {
        final boolean accepted = Bridge1_9.isGliding(player)
                && yDistanceAboveLimit <= GLIDING_VELOCITY_VERTICAL_OVER_GRACE
                && hDistanceAboveLimit <= GLIDING_VELOCITY_HORIZONTAL_OVER_GRACE
                && thisMove.hDistance >= GLIDING_VELOCITY_MIN_HORIZONTAL_MOVE
                && Math.abs(thisMove.yDistance) <= GLIDING_VELOCITY_MAX_VERTICAL_MOVE
                && (tags.contains("hvel_current") || tags.contains("hvel"));
        if (accepted) {
            tags.add("glide_velocity_vertical_model");
        }
        return accepted;
    }

    private boolean isCurrentServerVelocityVerticalGrace(final Player player,
                                                         final PlayerMoveData thisMove,
                                                         final double yDistanceAboveLimit,
                                                         final double hDistanceAboveLimit) {
        // False-positive tuning: normal falls/knockback can be modeled late but still match the server velocity.
        return yDistanceAboveLimit <= CURRENT_SERVER_VELOCITY_VERTICAL_OVER_GRACE
                && hDistanceAboveLimit <= CURRENT_SERVER_VELOCITY_HORIZONTAL_OVER_GRACE
                && Math.abs(thisMove.yDistance - player.getVelocity().getY()) <= CURRENT_SERVER_VELOCITY_VERTICAL_MATCH_GRACE;
    }

    private boolean isQueuedVelocityVerticalInertiaHandoffModel(final Player player, final MovingData data,
                                                                final PlayerLocation from, final PlayerLocation to,
                                                                final PlayerMoveData thisMove,
                                                                final PlayerMoveData lastMove,
                                                                final double yDistanceAboveLimit,
                                                                final double hDistanceAboveLimit) {
        /*
         * Velocity/firework handoff model: the horizontal velocity packet can be
         * visible one tick before the vertical prediction decays. In that case Y
         * should remain inside the envelope between previous Y and next vanilla
         * gravity Y, not inside a flat post-failure grace.
         */
        if (Bridge1_9.isGliding(player)
                || !lastMove.toIsValid
                || thisMove.verVelUsed.size() > 0
                || hDistanceAboveLimit > Magic.PREDICTION_EPSILON
                || thisMove.hDistance < QUEUED_VELOCITY_VERTICAL_INERTIA_MIN_H
                || !(data.getHorizontalVelocityTracker().hasQueued()
                        || tags.contains("hvel_current") || tags.contains("hvel"))
                || from.isInLiquid() || to.isInLiquid()
                || thisMove.from.inLiquid || thisMove.to.inLiquid) {
            return false;
        }
        final double gravityY = getAirInertiaVerticalModel(lastMove);
        final double lower = Math.min(gravityY, lastMove.yDistance) - QUEUED_VELOCITY_VERTICAL_INERTIA_RESIDUAL;
        final double upper = Math.max(gravityY, lastMove.yDistance) + QUEUED_VELOCITY_VERTICAL_INERTIA_RESIDUAL;
        final double overLimit = Math.abs(thisMove.yAllowedDistance - upper)
                + QUEUED_VELOCITY_VERTICAL_INERTIA_RESIDUAL;
        final boolean accepted = thisMove.yDistance >= lower
                && thisMove.yDistance <= upper
                && yDistanceAboveLimit <= overLimit;
        if (accepted) {
            tags.add("queued_velocity_vertical_inertia_handoff_model");
        }
        return accepted;
    }

    private boolean isQueuedVelocityVerticalPacketOrderModel(final Player player, final MovingData data,
                                                             final PlayerLocation from, final PlayerLocation to,
                                                             final PlayerMoveData thisMove,
                                                             final double yDistanceAboveLimit,
                                                             final double hDistanceAboveLimit) {
        // Packet-order tolerance: horizontal velocity can be consumed while the tiny Y correction arrives one packet later.
        return data.getHorizontalVelocityTracker().hasQueued()
                && !Bridge1_9.isGliding(player)
                && thisMove.verVelUsed.isEmpty()
                && hDistanceAboveLimit <= Magic.PREDICTION_EPSILON
                && thisMove.yDistance >= -Magic.PREDICTION_EPSILON
                && thisMove.yDistance <= QUEUED_VELOCITY_VERTICAL_PACKET_ORDER_MOVE
                && yDistanceAboveLimit <= QUEUED_VELOCITY_VERTICAL_PACKET_ORDER_OVER
                && !from.isInLiquid() && !to.isInLiquid()
                && !thisMove.from.inLiquid && !thisMove.to.inLiquid;
    }

    private boolean isCollisionVerticalCorrectionGrace(final Player player,
                                                       final PlayerLocation from, final PlayerLocation to,
                                                       final PlayerMoveData thisMove, final PlayerMoveData lastMove,
                                                       final double yDistanceAboveLimit,
                                                       final double hDistanceAboveLimit) {
        // False-positive tuning: collision resolution can report one small Y correction after a recovery/edge packet.
        final boolean collision = thisMove.collideX || thisMove.collideY || thisMove.collideZ
                || thisMove.collidesHorizontally || thisMove.negligibleHorizontalCollision;
        return collision
                && !Bridge1_9.isGliding(player)
                && (!lastMove.toIsValid || Math.abs(thisMove.yDistance - player.getVelocity().getY()) <= COLLISION_VERTICAL_CORRECTION_Y_MOVE_GRACE)
                && yDistanceAboveLimit <= COLLISION_VERTICAL_CORRECTION_OVER_GRACE
                && hDistanceAboveLimit <= COLLISION_VERTICAL_CORRECTION_HORIZONTAL_OVER_GRACE
                && thisMove.hDistance <= COLLISION_VERTICAL_CORRECTION_MOVE_GRACE
                && Math.abs(thisMove.yDistance) <= COLLISION_VERTICAL_CORRECTION_Y_MOVE_GRACE
                && !from.isInLiquid() && !to.isInLiquid()
                && !thisMove.from.inLiquid && !thisMove.to.inLiquid;
    }

    private boolean isWaterTagVerticalGrace(final double yDistanceAboveLimit,
                                            final double hDistanceAboveLimit) {
        // False-positive tuning: v_water tags can persist one packet after the sampled locations read as air.
        return tags.contains("v_water")
                && yDistanceAboveLimit <= WATER_TAG_VERTICAL_OVER_GRACE
                && hDistanceAboveLimit <= WATER_TAG_HORIZONTAL_OVER_GRACE;
    }

    private boolean isLavaVerticalGrace(final Player player, final PlayerLocation from, final PlayerLocation to,
                                        final PlayerMoveData thisMove,
                                        final double yDistanceAboveLimit,
                                        final double hDistanceAboveLimit) {
        // False-positive tuning: lava surface/exiting packets use liquid motion even when one sampled point reads as air.
        if (Bridge1_9.isGliding(player)
                || hDistanceAboveLimit > LAVA_HORIZONTAL_OVER_GRACE) {
            return false;
        }
        final boolean inLava = from.isInLava() || to.isInLava()
                || thisMove.from.inLava || thisMove.to.inLava || tags.contains("v_lava");
        if (!inLava) {
            return false;
        }
        if (thisMove.yDistance > 0.0D
                && thisMove.yDistance <= LAVA_ASCEND_MOVE_GRACE
                && yDistanceAboveLimit <= LAVA_VERTICAL_OVER_GRACE) {
            tags.add("lava_ascend_vertical_model");
            return true;
        }
        if (acceptsLavaCurrentVelocityVerticalModel(player, thisMove, yDistanceAboveLimit)) {
            tags.add("lava_current_velocity_vertical_model");
            return true;
        }
        if (tags.contains("v_lava")
                && yDistanceAboveLimit <= LAVA_TAG_VERTICAL_OVER_GRACE) {
            tags.add("lava_tag_vertical_model");
            return true;
        }
        return false;
    }

    private boolean acceptsLavaCurrentVelocityVerticalModel(final Player player, final PlayerMoveData thisMove,
                                                            final double yDistanceAboveLimit) {
        final double velocityY = player.getVelocity().getY();
        if (!tags.contains("v_lava")
                || velocityY >= -Magic.PREDICTION_EPSILON
                || thisMove.yDistance >= -Magic.PREDICTION_EPSILON) {
            return false;
        }
        // Lava model: a downward server velocity can be exposed before the next lava-friction/gravity packet is sampled.
        final double verticalModel = velocityY + velocityY * Magic.LAVA_VERTICAL_INERTIA
                - Magic.DEFAULT_GRAVITY / 4.0D;
        return Math.abs(thisMove.yDistance - verticalModel) <= LAVA_CURRENT_VERTICAL_RESIDUAL
                && yDistanceAboveLimit <= Math.abs(thisMove.yAllowedDistance - verticalModel)
                        + LAVA_CURRENT_VERTICAL_RESIDUAL;
    }

    private boolean isPortalTransitionVerticalGrace(final PlayerLocation from, final PlayerLocation to,
                                                    final MovingData data, final PlayerMoveData thisMove,
                                                    final PlayerMoveData lastMove,
                                                    final double yDistanceAboveLimit,
                                                    final double hDistanceAboveLimit) {
        // False-positive tuning: portal collision is passable, so the first move after dimension sync can lack history.
        return isPortalNear(from, to)
                && (!lastMove.toIsValid || data.liftOffEnvelope == LiftOffEnvelope.UNKNOWN)
                && yDistanceAboveLimit <= PORTAL_TRANSITION_VERTICAL_OVER_GRACE
                && hDistanceAboveLimit <= PORTAL_TRANSITION_HORIZONTAL_OVER_GRACE
                && Math.abs(thisMove.yDistance) <= PORTAL_TRANSITION_VERTICAL_MOVE_GRACE
                && thisMove.hDistance <= PORTAL_TRANSITION_HORIZONTAL_MOVE_GRACE;
    }

    private boolean acceptsElytraEquippedFireworkVerticalModel(final Player player, final MovingData data,
                                                               final PlayerLocation from, final PlayerLocation to,
                                                               final PlayerMoveData thisMove,
                                                               final PlayerMoveData lastMove,
                                                               final double yDistanceAboveLimit,
                                                               final double hDistanceAboveLimit) {
        if (!isElytraEquippedFireworkModelContext(player, data, from, to, thisMove)) {
            return false;
        }
        final double[] packetOrderModel = getElytraEquippedFireworkModelVector(player, data, to, thisMove, lastMove);
        final double horizontalLimit = getElytraEquippedFireworkHorizontalModelLimit(player, from, to,
                thisMove, packetOrderModel);
        if (thisMove.hDistance > horizontalLimit
                || hDistanceAboveLimit > getModelOverLimit(thisMove.hAllowedDistance,
                        horizontalLimit, ELYTRA_EQUIPPED_FIREWORK_HORIZONTAL_RESIDUAL)) {
            tags.add("elytra_equipped_firework_vertical_h_miss");
            return false;
        }
        if (verticalFireworkModelMatches(player, thisMove, packetOrderModel[1],
                ELYTRA_EQUIPPED_FIREWORK_VERTICAL_RESIDUAL, yDistanceAboveLimit)) {
            tags.add("elytra_equipped_firework_packet_order_vertical_model");
            return true;
        }
        if (verticalFireworkModelMatches(player, thisMove, player.getVelocity().getY(),
                ELYTRA_EQUIPPED_FIREWORK_VERTICAL_RESIDUAL, yDistanceAboveLimit)) {
            tags.add("elytra_equipped_firework_current_velocity_vertical_model");
            return true;
        }
        tags.add("elytra_equipped_firework_vertical_model_miss");
        return false;
    }

    private boolean acceptsGlidingFireworkVerticalModel(final Player player, final IPlayerData pData,
                                                        final MovingData data,
                                                        final PlayerLocation from, final PlayerLocation to,
                                                        final PlayerMoveData thisMove,
                                                        final PlayerMoveData lastMove,
                                                        final double yDistanceAboveLimit,
                                                        final double hDistanceAboveLimit) {
        if (!isGlidingFireworkModelContext(player, data, from, to, thisMove)
                || !tags.contains(SurvivalFlyTags.GLIDE_VERTICAL_PREDICTION_MISS)) {
            return false;
        }
        if (acceptsGlidingFireworkSkippedBoostVerticalModel(player, pData, data, from, to,
                thisMove, lastMove, yDistanceAboveLimit, hDistanceAboveLimit)) {
            return true;
        }
        if (acceptsGlidingFireworkGravityContinuationVerticalModel(player, data, from, to,
                thisMove, lastMove, yDistanceAboveLimit, hDistanceAboveLimit)) {
            return true;
        }
        final double[] packetOrderModel = getFireworkPacketOrderModelVector(player, data, to, thisMove, 1);
        final double horizontalLimit = getFireworkHorizontalModelLimit(player, thisMove, packetOrderModel,
                GLIDING_FIREWORK_PACKET_ORDER_HORIZONTAL_RESIDUAL);
        if (thisMove.hDistance > horizontalLimit
                || hDistanceAboveLimit > getModelOverLimit(thisMove.hAllowedDistance,
                        horizontalLimit, GLIDING_FIREWORK_PACKET_ORDER_HORIZONTAL_RESIDUAL)) {
            tags.add("elytra_firework_vertical_h_miss");
            return false;
        }
        if (yDistanceAboveLimit <= GLIDING_FIREWORK_VERTICAL_PRECISION) {
            tags.add("elytra_firework_vertical_precision_model");
            return true;
        }
        if (verticalFireworkModelMatches(player, thisMove, packetOrderModel[1],
                GLIDING_FIREWORK_PACKET_ORDER_VERTICAL_RESIDUAL, yDistanceAboveLimit)) {
            tags.add("elytra_firework_packet_order_vertical_model");
            return true;
        }
        if (verticalFireworkModelMatches(player, thisMove, player.getVelocity().getY(),
                GLIDING_FIREWORK_PACKET_ORDER_VERTICAL_RESIDUAL, yDistanceAboveLimit)) {
            tags.add("elytra_firework_current_velocity_vertical_model");
            return true;
        }
        final double[] inertialModel = getFireworkInertialLookModelVector(player, data, to, lastMove);
        if (acceptsGlidingFireworkPartialVerticalEnvelopeModel(player, thisMove, lastMove, packetOrderModel,
                inertialModel, yDistanceAboveLimit, hDistanceAboveLimit)) {
            return true;
        }
        if (verticalFireworkModelMatches(player, thisMove, inertialModel[1],
                GLIDING_FIREWORK_PACKET_ORDER_VERTICAL_RESIDUAL, yDistanceAboveLimit)) {
            tags.add("elytra_firework_inertial_vertical_model");
            return true;
        }
        if (acceptsGlidingFireworkGroundProximityVerticalModel(from, to, thisMove, lastMove,
                yDistanceAboveLimit, hDistanceAboveLimit)) {
            return true;
        }
        tags.add("elytra_firework_vertical_model_miss");
        return false;
    }

    private boolean acceptsGlidingFireworkPartialVerticalEnvelopeModel(final Player player,
                                                                       final PlayerMoveData thisMove,
                                                                       final PlayerMoveData lastMove,
                                                                       final double[] packetOrderModel,
                                                                       final double[] inertialModel,
                                                                       final double yDistanceAboveLimit,
                                                                       final double hDistanceAboveLimit) {
        if (!lastMove.toIsValid
                || hDistanceAboveLimit > GLIDING_HORIZONTAL_PRECISION_GRACE
                || !tags.contains(SurvivalFlyTags.GLIDE_VERTICAL_ACTUAL_BELOW_MODEL)) {
            return false;
        }
        /*
         * Elytra firework model: Folia/packet order can expose only part of the
         * rocket Y for one packet. The packet must still sit inside the vanilla
         * interval between gravity continuation and the computed boost vectors.
         */
        final double gravityModel = getAirInertiaVerticalModel(lastMove);
        final double velocityY = player.getVelocity().getY();
        final double boostModel = Math.max(Math.max(packetOrderModel[1], inertialModel[1]), velocityY);
        final double lower = Math.min(gravityModel, boostModel) - GLIDING_FIREWORK_PARTIAL_VERTICAL_RESIDUAL;
        final double upper = Math.max(gravityModel, boostModel) + GLIDING_FIREWORK_PARTIAL_VERTICAL_RESIDUAL;
        if (thisMove.yDistance < lower || thisMove.yDistance > upper) {
            tags.add("elytra_firework_partial_vertical_envelope_miss");
            return false;
        }
        final double nearestModel = Math.abs(thisMove.yDistance - gravityModel) <= Math.abs(thisMove.yDistance - boostModel)
                ? gravityModel : boostModel;
        if (yDistanceAboveLimit <= Math.abs(thisMove.yAllowedDistance - nearestModel)
                + GLIDING_FIREWORK_PARTIAL_VERTICAL_RESIDUAL) {
            tags.add("elytra_firework_partial_vertical_envelope_model");
            return true;
        }
        tags.add("elytra_firework_partial_vertical_over_miss");
        return false;
    }

    private boolean acceptsGlidingFireworkSkippedBoostVerticalModel(final Player player, final IPlayerData pData,
                                                                    final MovingData data,
                                                                    final PlayerLocation from,
                                                                    final PlayerLocation to,
                                                                    final PlayerMoveData thisMove,
                                                                    final PlayerMoveData lastMove,
                                                                    final double yDistanceAboveLimit,
                                                                    final double hDistanceAboveLimit) {
        if (!isGlidingFireworkModelContext(player, data, from, to, thisMove)
                || !tags.contains(SurvivalFlyTags.GLIDE_VERTICAL_PREDICTION_MISS)
                || from.isInLiquid() || to.isInLiquid()
                || thisMove.from.inLiquid || thisMove.to.inLiquid) {
            return false;
        }
        final double[] skippedBoostModel = lastMove.toIsValid
                ? getGlidingNoFireworkModelVector(player, pData, data, to, lastMove)
                : new double[] { 0.0D, getAirInertiaFirstGravityModel(), 0.0D };
        final double horizontalLimit = Math.max(thisMove.hAllowedDistance,
                MathUtil.dist(skippedBoostModel[0], skippedBoostModel[2])
                        + GLIDING_FIREWORK_SKIPPED_BOOST_HORIZONTAL_RESIDUAL);
        if (thisMove.hDistance > horizontalLimit
                || hDistanceAboveLimit > getModelOverLimit(thisMove.hAllowedDistance,
                        horizontalLimit, GLIDING_FIREWORK_SKIPPED_BOOST_HORIZONTAL_RESIDUAL)) {
            tags.add("elytra_firework_skipped_boost_horizontal_miss");
            return false;
        }
        if (matchesVerticalModel(thisMove.yDistance, skippedBoostModel[1],
                GLIDING_FIREWORK_SKIPPED_BOOST_VERTICAL_RESIDUAL)
                && yDistanceAboveLimit <= Math.abs(thisMove.yAllowedDistance - skippedBoostModel[1])
                        + GLIDING_FIREWORK_SKIPPED_BOOST_VERTICAL_RESIDUAL) {
            tags.add(lastMove.toIsValid
                    ? "elytra_firework_skipped_boost_vertical_model"
                    : "elytra_firework_last_invalid_gravity_vertical_model");
            return true;
        }
        tags.add("elytra_firework_skipped_boost_vertical_miss");
        return false;
    }

    private boolean acceptsGlidingFireworkGravityContinuationVerticalModel(final Player player,
                                                                           final MovingData data,
                                                                           final PlayerLocation from,
                                                                           final PlayerLocation to,
                                                                           final PlayerMoveData thisMove,
                                                                           final PlayerMoveData lastMove,
                                                                           final double yDistanceAboveLimit,
                                                                           final double hDistanceAboveLimit) {
        if (!isGlidingFireworkModelContext(player, data, from, to, thisMove)
                || !tags.contains(SurvivalFlyTags.GLIDE_VERTICAL_PREDICTION_MISS)
                || !lastMove.toIsValid
                || hDistanceAboveLimit > GLIDING_HORIZONTAL_PRECISION_GRACE) {
            return false;
        }
        final double gravityModel = getAirInertiaVerticalModel(lastMove);
        if (matchesVerticalModel(thisMove.yDistance, gravityModel,
                GLIDING_FIREWORK_GRAVITY_VERTICAL_MATCH)
                && yDistanceAboveLimit <= Math.abs(thisMove.yAllowedDistance - gravityModel)
                        + GLIDING_FIREWORK_GRAVITY_VERTICAL_MATCH) {
            /*
             * Elytra firework model: some rocket packet-order cases skip the
             * boost Y for one packet but still continue vanilla gravity exactly.
             */
            tags.add("elytra_firework_gravity_continuation_vertical_model");
            return true;
        }
        tags.add("elytra_firework_gravity_continuation_vertical_model_miss");
        return false;
    }

    private double[] getGlidingNoFireworkModelVector(final Player player, final IPlayerData pData,
                                                     final MovingData data, final PlayerLocation to,
                                                     final PlayerMoveData lastMove) {
        // Model cleanup: firework packet order can skip the boost while still applying the normal glide tick.
        final CombinedData cData = pData.getGenericInstance(CombinedData.class);
        double x = lastMove.toIsValid ? lastMove.xDistance : 0.0D;
        double y = lastMove.toIsValid ? lastMove.yDistance : 0.0D;
        double z = lastMove.toIsValid ? lastMove.zDistance : 0.0D;
        final Vector viewVector = TrigUtil.getLookingDirection(to, player);
        final float radianPitch = to.getPitch() * TrigUtil.toRadians;
        final double viewVecHorizontalLength = MathUtil.dist(viewVector.getX(), viewVector.getZ());
        final double thisMoveHDistance = MathUtil.dist(x, z);
        final double viewVectorLength = viewVector.length();
        final ClientVersion clientVersion = getMovementClientVersion(pData);
        double cosPitch = clientVersion.isAtMost(ClientVersion.V_1_18_2)
                ? TrigUtil.cos((double) radianPitch) : Math.cos((double) radianPitch);
        cosPitch = cosPitch * cosPitch * Math.min(1.0D, viewVectorLength / 0.4D);
        y += (cData.wasSlowFalling && lastMove.yDistance <= 0.0D ? Magic.SLOW_FALL_GRAVITY : Magic.DEFAULT_GRAVITY)
                * (-1.0D + cosPitch * 0.75D);
        double baseSpeed;
        if (y < 0.0D && viewVecHorizontalLength > 0.0D) {
            baseSpeed = y * -0.1D * cosPitch;
            x += viewVector.getX() * baseSpeed / viewVecHorizontalLength;
            y += baseSpeed;
            z += viewVector.getZ() * baseSpeed / viewVecHorizontalLength;
        }
        if (radianPitch < 0.0D && viewVecHorizontalLength > 0.0D) {
            baseSpeed = thisMoveHDistance * (double) (-TrigUtil.sin(radianPitch)) * 0.04D;
            x += -viewVector.getX() * baseSpeed / viewVecHorizontalLength;
            y += baseSpeed * 3.2D;
            z += -viewVector.getZ() * baseSpeed / viewVecHorizontalLength;
        }
        if (viewVecHorizontalLength > 0.0D) {
            x += (viewVector.getX() / viewVecHorizontalLength * thisMoveHDistance - x) * 0.1D;
            z += (viewVector.getZ() / viewVecHorizontalLength * thisMoveHDistance - z) * 0.1D;
        }
        x *= 0.99D;
        y *= data.lastFrictionVertical;
        z *= 0.99D;
        return new double[] { x, y, z };
    }

    private boolean acceptsGlidingFireworkGroundProximityVerticalModel(final PlayerLocation from,
                                                                       final PlayerLocation to,
                                                                       final PlayerMoveData thisMove,
                                                                       final PlayerMoveData lastMove,
                                                                       final double yDistanceAboveLimit,
                                                                       final double hDistanceAboveLimit) {
        if (!lastMove.toIsValid
                || !tags.contains(SurvivalFlyTags.GLIDE_VERTICAL_ACTUAL_BELOW_MODEL)
                || thisMove.yDistance >= -Magic.PREDICTION_EPSILON
                || hDistanceAboveLimit > GLIDING_HORIZONTAL_PRECISION_GRACE
                || thisMove.hDistance > thisMove.hAllowedDistance + GLIDING_HORIZONTAL_PRECISION_GRACE
                || !isGroundBlockBelow(from, to)) {
            return false;
        }
        // Model cleanup: a rocket packet near ground can keep the normal fall tick instead of applying upward boost Y.
        final double gravityModel = getAirInertiaVerticalModel(lastMove);
        if (matchesVerticalModel(thisMove.yDistance, gravityModel,
                GLIDING_FIREWORK_GROUND_PROXIMITY_VERTICAL_RESIDUAL)
                && yDistanceAboveLimit <= Math.abs(thisMove.yAllowedDistance - gravityModel)
                        + GLIDING_FIREWORK_GROUND_PROXIMITY_VERTICAL_RESIDUAL) {
            tags.add("elytra_firework_ground_proximity_gravity_vertical_model");
            return true;
        }
        return false;
    }

    private boolean isGroundBlockBelow(final PlayerLocation from, final PlayerLocation to) {
        final Material fromBelow = from.getBlockTypeBelow();
        final Material toBelow = to.getBlockTypeBelow();
        return fromBelow != null && BlockProperties.isGround(fromBelow)
                || toBelow != null && BlockProperties.isGround(toBelow);
    }

    private boolean acceptsGlidingSmallVerticalPredictionModel(final Player player,
                                                               final PlayerMoveData thisMove,
                                                               final double yDistanceAboveLimit,
                                                               final double hDistanceAboveLimit) {
        // Model cleanup: normal elytra glides can miss the vertical curve by a few centimeters only.
        final boolean accepted = Bridge1_9.isGliding(player)
                && tags.contains(SurvivalFlyTags.GLIDE_VERTICAL_PREDICTION_MISS)
                && yDistanceAboveLimit <= GLIDING_VERTICAL_SMALL_MISS_GRACE
                && thisMove.hDistance <= GLIDING_VERTICAL_SMALL_MISS_MOVE_GRACE
                && (hDistanceAboveLimit <= GLIDING_HORIZONTAL_PRECISION_GRACE
                        || tags.contains("glide_current_velocity_horizontal_model"));
        if (accepted) {
            tags.add("glide_small_vertical_prediction_model");
        }
        return accepted;
    }

    private boolean acceptsGlidingBelowVerticalModel(final Player player, final MovingData data,
                                                     final PlayerMoveData thisMove,
                                                     final double yDistanceAboveLimit,
                                                     final double hDistanceAboveLimit) {
        // Model cleanup: bound "below predicted" gliding by server velocity/rocket packet order instead of a flat fallback.
        if (!Bridge1_9.isGliding(player)
                || !tags.contains(SurvivalFlyTags.GLIDE_VERTICAL_ACTUAL_BELOW_MODEL)
                || thisMove.yDistance > thisMove.yAllowedDistance
                || hDistanceAboveLimit > GLIDING_HORIZONTAL_PRECISION_GRACE
                || thisMove.from.inLiquid || thisMove.to.inLiquid) {
            return false;
        }
        final double velocityY = player.getVelocity().getY();
        if (currentGlidingFullVelocityMatches(player, thisMove, GLIDING_CURRENT_VELOCITY_VERTICAL_MATCH_GRACE)
                && yDistanceAboveLimit <= Math.abs(thisMove.yAllowedDistance - velocityY)
                        + GLIDING_CURRENT_VELOCITY_VERTICAL_MATCH_GRACE) {
            /*
             * Elytra model: steep dives can have an invalid previous move after
             * resync, but the current server velocity vector still exactly owns
             * the packet. Do not cap those by the generic below-model window.
             */
            tags.add("glide_below_current_velocity_vector_model");
            return true;
        }
        final double lowerModel = Math.min(thisMove.yAllowedDistance, velocityY);
        final double residual = data.fireworksBoostDuration > 0
                ? GLIDING_FIREWORK_PACKET_ORDER_VERTICAL_RESIDUAL
                : GLIDING_VERTICAL_SMALL_MISS_GRACE;
        final double empiricalCap = data.fireworksBoostDuration > 0
                ? GLIDING_VERTICAL_BELOW_MODEL_FIREWORK_GRACE
                : GLIDING_VERTICAL_BELOW_MODEL_GRACE;
        final boolean accepted = thisMove.yDistance >= lowerModel - residual
                && yDistanceAboveLimit <= Math.min(empiricalCap,
                        Math.abs(thisMove.yAllowedDistance - lowerModel) + residual);
        if (accepted) {
            tags.add(data.fireworksBoostDuration > 0
                    ? "glide_firework_below_velocity_vertical_model"
                    : "glide_below_velocity_vertical_model");
        }
        return accepted;
    }

    private boolean currentGlidingFullVelocityMatches(final Player player, final PlayerMoveData thisMove,
                                                       final double verticalResidual) {
        return currentGlidingActualVelocityMatches(player, thisMove)
                && Math.abs(thisMove.yDistance - player.getVelocity().getY()) <= verticalResidual;
    }

    private boolean acceptsElytraEquippedDescendVerticalModel(final Player player,
                                                              final PlayerMoveData thisMove,
                                                              final PlayerMoveData lastMove,
                                                              final double yDistanceAboveLimit,
                                                              final double hDistanceAboveLimit) {
        if (!Bridge1_9.isWearingElytra(player)
                || Bridge1_9.isGliding(player)
                || thisMove.yDistance >= 0.0D
                || thisMove.from.inLiquid || thisMove.to.inLiquid) {
            return false;
        }
        final double horizontalLimit = getElytraEquippedDescendHorizontalModelLimit(player, thisMove);
        final double verticalModel = getElytraEquippedDescendVerticalModel(player, thisMove, lastMove);
        if (thisMove.hDistance <= horizontalLimit
                && hDistanceAboveLimit <= getModelOverLimit(thisMove.hAllowedDistance,
                        horizontalLimit, ELYTRA_EQUIPPED_DESCEND_HORIZONTAL_OVER_GRACE)
                && yDistanceAboveLimit <= Math.abs(thisMove.yAllowedDistance - verticalModel)
                        + ELYTRA_EQUIPPED_DESCEND_VERTICAL_OVER_GRACE) {
            tags.add("elytra_equipped_descend_vertical_model");
            return true;
        }
        tags.add("elytra_equipped_descend_vertical_model_miss");
        return false;
    }

    private double getElytraEquippedDescendVerticalModel(final Player player,
                                                         final PlayerMoveData thisMove,
                                                         final PlayerMoveData lastMove) {
        final double velocityY = player.getVelocity().getY();
        double model = thisMove.yAllowedDistance;
        if (velocityY < -Magic.PREDICTION_EPSILON && velocityY > model) {
            /*
             * Elytra-equipped transition model: when Bukkit has already exposed
             * a slower downward velocity than the vanilla air prediction, use
             * that server velocity as the upper fall boundary. This covers legit
             * soft glide-exit/landing packets without widening true hover.
             */
            model = velocityY;
        }
        if (lastMove.toIsValid && lastMove.yDistance < -Magic.PREDICTION_EPSILON
                && (hasCollisionSignal(thisMove) || tags.contains("hvel") || tags.contains("hvel_current"))) {
            // Model cleanup: a collision/velocity handoff can repeat the previous descending packet for one tick.
            model = Math.max(model, lastMove.yDistance);
        }
        return model;
    }

    private boolean acceptsElytraEquippedGlideExitVerticalModel(final Player player,
                                                                final PlayerMoveData thisMove,
                                                                final PlayerMoveData lastMove,
                                                                final double yDistanceAboveLimit,
                                                                final double hDistanceAboveLimit) {
        /*
         * Elytra model: Bukkit can clear gliding one packet before the client
         * applies normal air gravity. Keep the last glide Y for exactly this
         * transition instead of treating it as a generic vertical grace.
         */
        if (!Bridge1_9.isWearingElytra(player)
                || Bridge1_9.isGliding(player)
                || !lastMove.toIsValid
                || lastMove.yDistance >= -Magic.PREDICTION_EPSILON
                || hDistanceAboveLimit > GLIDING_HORIZONTAL_PRECISION_GRACE
                || !(tags.contains("hvel_current") || tags.contains("hvel"))
                || thisMove.from.inLiquid || thisMove.to.inLiquid) {
            return false;
        }
        if (matchesVerticalModel(thisMove.yDistance, lastMove.yDistance,
                ELYTRA_EQUIPPED_GLIDE_EXIT_VERTICAL_MATCH)
                && yDistanceAboveLimit <= Math.abs(thisMove.yAllowedDistance - lastMove.yDistance)
                        + ELYTRA_EQUIPPED_GLIDE_EXIT_VERTICAL_MATCH) {
            tags.add("elytra_equipped_glide_exit_vertical_model");
            return true;
        }
        tags.add("elytra_equipped_glide_exit_vertical_model_miss");
        return false;
    }

    private boolean acceptsElytraEquippedVelocityHandoffVerticalModel(final Player player,
                                                                      final PlayerLocation from,
                                                                      final PlayerLocation to,
                                                                      final PlayerMoveData thisMove,
                                                                      final PlayerMoveData lastMove,
                                                                      final double yDistanceAboveLimit,
                                                                      final double hDistanceAboveLimit) {
        if (!isElytraEquippedVelocityHandoffContext(player, from, to, thisMove, lastMove)) {
            return false;
        }
        final double horizontalLimit = getElytraEquippedVelocityHandoffHorizontalModelLimit(player, thisMove);
        if (hDistanceAboveLimit > getModelOverLimit(thisMove.hAllowedDistance,
                horizontalLimit, ELYTRA_EQUIPPED_VELOCITY_HANDOFF_HORIZONTAL_RESIDUAL)) {
            return false;
        }
        // Elytra model: descending handoff packets must match current server Y velocity, not a wide hover grace.
        return yDistanceAboveLimit <= Math.abs(thisMove.yAllowedDistance - player.getVelocity().getY())
                + ELYTRA_EQUIPPED_VELOCITY_HANDOFF_VERTICAL_MATCH;
    }

    private boolean acceptsElytraEquippedNeutralVerticalModel(final Player player,
                                                              final PlayerMoveData thisMove,
                                                              final double yDistanceAboveLimit,
                                                              final double hDistanceAboveLimit) {
        if (!Bridge1_9.isWearingElytra(player)
                || Bridge1_9.isGliding(player)
                || Math.abs(thisMove.yDistance) > Magic.PREDICTION_EPSILON
                || thisMove.from.inLiquid || thisMove.to.inLiquid) {
            return false;
        }
        final double horizontalLimit = Math.min(ELYTRA_EQUIPPED_NEUTRAL_MOVE_GRACE,
                Math.max(thisMove.hAllowedDistance, MathUtil.dist(player.getVelocity().getX(), player.getVelocity().getZ())
                        + ELYTRA_EQUIPPED_NEUTRAL_HORIZONTAL_OVER_GRACE));
        if (thisMove.hDistance <= horizontalLimit
                && hDistanceAboveLimit <= getModelOverLimit(thisMove.hAllowedDistance,
                        horizontalLimit, ELYTRA_EQUIPPED_NEUTRAL_HORIZONTAL_OVER_GRACE)
                && yDistanceAboveLimit <= getModelOverLimit(thisMove.yAllowedDistance,
                        0.0D, ELYTRA_EQUIPPED_NEUTRAL_VERTICAL_OVER_GRACE)) {
            tags.add("elytra_equipped_neutral_vertical_model");
            return true;
        }
        tags.add("elytra_equipped_neutral_vertical_model_miss");
        return false;
    }

    private boolean acceptsElytraEquippedLastInvalidAscendVerticalModel(final Player player,
                                                                        final PlayerLocation from,
                                                                        final PlayerLocation to,
                                                                        final PlayerMoveData thisMove,
                                                                        final PlayerMoveData lastMove,
                                                                        final double yDistanceAboveLimit,
                                                                        final double hDistanceAboveLimit) {
        if (!Bridge1_9.isWearingElytra(player)
                || Bridge1_9.isGliding(player)
                || lastMove.toIsValid
                || thisMove.yDistance <= Magic.PREDICTION_EPSILON
                || from.isInLiquid() || to.isInLiquid()
                || thisMove.from.inLiquid || thisMove.to.inLiquid) {
            return false;
        }
        final boolean lowJumpContinuation = isElytraEquippedLastInvalidLowJumpContinuation(thisMove);
        if (!lowJumpContinuation && thisMove.yDistance > ELYTRA_EQUIPPED_LAST_INVALID_ASCEND_MAX_MOVE) {
            return false;
        }
        final double horizontalLimit = Math.max(thisMove.hAllowedDistance,
                Math.max(MathUtil.dist(player.getVelocity().getX(), player.getVelocity().getZ())
                        + playerInputHorizontalCarry(thisMove)
                        + ELYTRA_EQUIPPED_VELOCITY_HORIZONTAL_RESIDUAL,
                        lowJumpContinuation ? getElytraEquippedGroundHorizontalModelLimit(player, thisMove, lastMove) : 0.0D));
        if (thisMove.hDistance > horizontalLimit
                || hDistanceAboveLimit > getModelOverLimit(thisMove.hAllowedDistance,
                        horizontalLimit, ELYTRA_EQUIPPED_VELOCITY_HORIZONTAL_RESIDUAL)) {
            tags.add("elytra_equipped_last_invalid_ascend_horizontal_miss");
            return false;
        }
        final double verticalModel = lowJumpContinuation
                ? LAST_INVALID_LOW_JUMP_CONTINUATION_VERTICAL_MAX : Math.max(0.0D, player.getVelocity().getY());
        final double verticalResidual = lowJumpContinuation
                ? LAST_INVALID_LOW_JUMP_CONTINUATION_VERTICAL_EPSILON
                : ELYTRA_EQUIPPED_LAST_INVALID_ASCEND_VERTICAL_RESIDUAL;
        if (thisMove.yDistance <= verticalModel + verticalResidual
                && yDistanceAboveLimit <= getModelOverLimit(thisMove.yAllowedDistance,
                        verticalModel, verticalResidual)) {
            /*
             * Elytra model: after stale/invalid movement history, a low pre-glide launch
             * packet can expose current server Y velocity, or one vanilla low-jump
             * continuation while the player is still only wearing an elytra.
             */
            tags.add(lowJumpContinuation ? "elytra_equipped_last_invalid_low_jump_vertical_model"
                    : "elytra_equipped_last_invalid_ascend_vertical_model");
            return true;
        }
        tags.add("elytra_equipped_last_invalid_ascend_vertical_model_miss");
        return false;
    }

    private boolean isElytraEquippedLastInvalidLowJumpContinuation(final PlayerMoveData thisMove) {
        return thisMove.yDistance >= LAST_INVALID_LOW_JUMP_CONTINUATION_VERTICAL_MIN
                && thisMove.yDistance <= LAST_INVALID_LOW_JUMP_CONTINUATION_VERTICAL_MAX
                && getHorizontalInputScale(thisMove) > 0.0D;
    }

    private boolean acceptsElytraEquippedSmallVerticalResyncModel(final Player player,
                                                                  final PlayerLocation from, final PlayerLocation to,
                                                                  final PlayerMoveData thisMove, final PlayerMoveData lastMove,
                                                                  final double yDistanceAboveLimit,
                                                                  final double hDistanceAboveLimit) {
        // Packet-order model: tiny non-gliding Y resync packets happen after item-use, collision, or velocity handoff while elytra is worn.
        final boolean resyncContext = !lastMove.toIsValid
                || tags.contains("itemresync")
                || tags.contains("hvel") || tags.contains("hvel_current")
                || thisMove.collideX || thisMove.collideY || thisMove.collideZ
                || isGroundishStepMove(from, to, thisMove);
        if (!Bridge1_9.isWearingElytra(player)
                || Bridge1_9.isGliding(player)
                || !resyncContext
                || thisMove.yDistance < -Magic.PREDICTION_EPSILON
                || thisMove.yDistance > ELYTRA_EQUIPPED_SMALL_VERTICAL_MOVE_GRACE
                || from.isInLiquid() || to.isInLiquid()
                || thisMove.from.inLiquid || thisMove.to.inLiquid) {
            return false;
        }
        final double horizontalLimit = Math.min(ELYTRA_EQUIPPED_SMALL_VERTICAL_HORIZONTAL_MOVE_GRACE,
                Math.max(thisMove.hAllowedDistance, MathUtil.dist(player.getVelocity().getX(), player.getVelocity().getZ())
                        + playerInputHorizontalCarry(thisMove)
                        + ELYTRA_EQUIPPED_SMALL_VERTICAL_HORIZONTAL_OVER_GRACE));
        if (thisMove.hDistance <= horizontalLimit
                && hDistanceAboveLimit <= getModelOverLimit(thisMove.hAllowedDistance,
                        horizontalLimit, ELYTRA_EQUIPPED_SMALL_VERTICAL_HORIZONTAL_OVER_GRACE)
                && yDistanceAboveLimit <= getModelOverLimit(thisMove.yAllowedDistance,
                        getElytraEquippedSmallResyncVerticalModel(player, thisMove, lastMove),
                        ELYTRA_EQUIPPED_SMALL_VERTICAL_OVER_GRACE)) {
            tags.add("elytra_equipped_small_resync_vertical_model");
            return true;
        }
        tags.add("elytra_equipped_small_resync_vertical_model_miss");
        return false;
    }

    private double getElytraEquippedSmallResyncVerticalModel(final Player player,
                                                             final PlayerMoveData thisMove,
                                                             final PlayerMoveData lastMove) {
        final double velocityY = Math.max(0.0D, player.getVelocity().getY());
        final double followUpY = lastMove.toIsValid
                ? Math.max(0.0D, (lastMove.yDistance - Magic.DEFAULT_GRAVITY) * Magic.FRICTION_MEDIUM_AIR)
                : 0.0D;
        return Math.min(ELYTRA_EQUIPPED_SMALL_VERTICAL_MOVE_GRACE,
                Math.max(thisMove.yAllowedDistance, Math.max(velocityY, followUpY)));
    }

    private boolean acceptsElytraEquippedVelocityVerticalModel(final Player player,
                                                               final PlayerLocation from, final PlayerLocation to,
                                                               final PlayerMoveData thisMove, final PlayerMoveData lastMove,
                                                               final double yDistanceAboveLimit,
                                                               final double hDistanceAboveLimit) {
        return acceptsElytraEquippedVelocityMoveModel(player, from, to, thisMove, lastMove, yDistanceAboveLimit, hDistanceAboveLimit, true);
    }

    private boolean isElytraEquippedHalfStepVerticalModel(final Player player,
                                                          final PlayerLocation from, final PlayerLocation to,
                                                          final PlayerMoveData thisMove, final PlayerMoveData lastMove,
                                                          final double yDistanceAboveLimit,
                                                          final double hDistanceAboveLimit) {
        // Elytra model: wearing an elytra must still allow the normal 0.5-block slab/stair step shape before gliding starts.
        if (!Bridge1_9.isWearingElytra(player)
                || Bridge1_9.isGliding(player)
                || !isGroundishStepMove(from, to, thisMove)
                || Math.abs(thisMove.yDistance - BEDROCK_HALF_STEP_VERTICAL_MOVE) > BEDROCK_HALF_STEP_VERTICAL_EPSILON
                || from.isInLiquid() || to.isInLiquid()
                || thisMove.from.inLiquid || thisMove.to.inLiquid) {
            return false;
        }
        final double horizontalLimit = getBedrockStepHorizontalModelLimit(from, to, thisMove, lastMove);
        if (thisMove.hDistance <= horizontalLimit
                && yDistanceAboveLimit <= getModelOverLimit(thisMove.yAllowedDistance,
                        BEDROCK_HALF_STEP_VERTICAL_MOVE, PARTIAL_SUPPORT_VERTICAL_MODEL_EPSILON)
                && hDistanceAboveLimit <= getModelOverLimit(thisMove.hAllowedDistance,
                        horizontalLimit, PARTIAL_SUPPORT_HORIZONTAL_MODEL_EPSILON)) {
            tags.add("elytra_equipped_half_step_vertical_model");
            return true;
        }
        tags.add("elytra_equipped_half_step_vertical_model_miss");
        return false;
    }

    private boolean acceptsElytraEquippedStaleAscendVerticalModel(final Player player,
                                                                  final PlayerLocation from, final PlayerLocation to,
                                                                  final PlayerMoveData thisMove, final PlayerMoveData lastMove,
                                                                  final double yDistanceAboveLimit,
                                                                  final double hDistanceAboveLimit) {
        if (!Bridge1_9.isWearingElytra(player)
                || Bridge1_9.isGliding(player)
                || !lastMove.toIsValid
                || lastMove.yDistance <= 0.25D
                || thisMove.yDistance > Magic.PREDICTION_EPSILON
                || player.getVelocity().getY() <= 0.10D
                || from.isInLiquid() || to.isInLiquid()
                || thisMove.from.inLiquid || thisMove.to.inLiquid) {
            return false;
        }
        final double verticalModel = getElytraEquippedStaleAscendVerticalModel(player, lastMove);
        final double horizontalLimit = getElytraEquippedStaleAscendHorizontalModelLimit(player, thisMove, lastMove);
        if (thisMove.hDistance <= horizontalLimit
                && hDistanceAboveLimit <= getModelOverLimit(thisMove.hAllowedDistance,
                        horizontalLimit, ELYTRA_EQUIPPED_STALE_ASCEND_HORIZONTAL_OVER_GRACE)
                && yDistanceAboveLimit <= getModelOverLimit(thisMove.yAllowedDistance,
                        verticalModel, ELYTRA_EQUIPPED_STALE_ASCEND_VERTICAL_OVER_GRACE)) {
            tags.add("elytra_equipped_stale_ascend_vertical_model");
            return true;
        }
        tags.add("elytra_equipped_stale_ascend_vertical_model_miss");
        return false;
    }

    private double getElytraEquippedStaleAscendVerticalModel(final Player player,
                                                             final PlayerMoveData lastMove) {
        final double followUpY = (lastMove.yDistance - Magic.DEFAULT_GRAVITY) * Magic.FRICTION_MEDIUM_AIR;
        return Math.max(0.0D, Math.max(player.getVelocity().getY(), followUpY));
    }

    private double getElytraEquippedStaleAscendHorizontalModelLimit(final Player player,
                                                                    final PlayerMoveData thisMove,
                                                                    final PlayerMoveData lastMove) {
        final double velocityH = MathUtil.dist(player.getVelocity().getX(), player.getVelocity().getZ());
        return Math.min(ELYTRA_EQUIPPED_STALE_ASCEND_MOVE_GRACE,
                Math.max(thisMove.hAllowedDistance, Math.max(velocityH, lastMove.hDistance)
                        + ELYTRA_EQUIPPED_STALE_ASCEND_HORIZONTAL_OVER_GRACE));
    }

    private boolean acceptsElytraEquippedVelocityMoveModel(final Player player,
                                                          final PlayerLocation from, final PlayerLocation to,
                                                          final PlayerMoveData thisMove, final PlayerMoveData lastMove,
                                                          final double yDistanceAboveLimit,
                                                          final double hDistanceAboveLimit,
                                                          final boolean checkVerticalLimit) {
        // Elytra model: match the pre-glide launch packet to the server velocity handoff instead of a wide Y grace.
        if (!Bridge1_9.isWearingElytra(player)
                || Bridge1_9.isGliding(player)
                || thisMove.yDistance <= 0.0D
                || from.isInLiquid() || to.isInLiquid()
                || thisMove.from.inLiquid || thisMove.to.inLiquid) {
            return false;
        }
        final double verticalLimit = getElytraEquippedVelocityVerticalModelLimit(player, thisMove, lastMove);
        final double horizontalLimit = getElytraEquippedVelocityHorizontalModelLimit(player, thisMove, lastMove);
        if (thisMove.yDistance > verticalLimit
                || thisMove.hDistance > horizontalLimit
                || hDistanceAboveLimit > getModelOverLimit(thisMove.hAllowedDistance,
                        horizontalLimit, ELYTRA_EQUIPPED_VELOCITY_HORIZONTAL_RESIDUAL)
                || checkVerticalLimit && yDistanceAboveLimit > getModelOverLimit(thisMove.yAllowedDistance,
                        verticalLimit, ELYTRA_EQUIPPED_VELOCITY_VERTICAL_RESIDUAL)) {
            return false;
        }
        final boolean groundish = from.isOnGroundOrResetCond() || to.isOnGroundOrResetCond()
                || thisMove.from.onGroundOrResetCond || thisMove.to.onGroundOrResetCond
                || thisMove.touchedGround || thisMove.touchedGroundWorkaround
                || tags.contains("onground_env") || tags.contains("v_air");
        final boolean currentVelocity = currentMotionVectorMatches(player, thisMove,
                ELYTRA_EQUIPPED_VELOCITY_HORIZONTAL_RESIDUAL, ELYTRA_EQUIPPED_VELOCITY_VERTICAL_RESIDUAL);
        final boolean followUp = matchesElytraVelocityFollowupModel(player, thisMove, lastMove);
        if (currentVelocity) {
            tags.add("elytra_equipped_current_velocity_model");
            return true;
        }
        if (followUp) {
            tags.add("elytra_equipped_velocity_followup_model");
            return true;
        }
        if (groundish && player.getVelocity().getY() > 0.20D) {
            tags.add("elytra_equipped_grounded_velocity_model");
            return true;
        }
        tags.add("elytra_equipped_velocity_model_miss");
        return false;
    }

    private boolean isElytraEquippedVelocityHandoffContext(final Player player,
                                                          final PlayerLocation from, final PlayerLocation to,
                                                          final PlayerMoveData thisMove,
                                                          final PlayerMoveData lastMove) {
        return Bridge1_9.isWearingElytra(player)
                && !Bridge1_9.isGliding(player)
                && !lastMove.toIsValid
                && thisMove.yDistance < -Magic.PREDICTION_EPSILON
                && Math.abs(thisMove.yDistance - player.getVelocity().getY())
                        <= ELYTRA_EQUIPPED_VELOCITY_HANDOFF_VERTICAL_MATCH
                && thisMove.hDistance <= getElytraEquippedVelocityHandoffHorizontalModelLimit(player, thisMove)
                && !from.isInLiquid() && !to.isInLiquid()
                && !thisMove.from.inLiquid && !thisMove.to.inLiquid;
    }

    private double getElytraEquippedVelocityHandoffHorizontalModelLimit(final Player player,
                                                                       final PlayerMoveData thisMove) {
        final Vector velocity = player.getVelocity();
        final double velocityH = MathUtil.dist(velocity.getX(), velocity.getZ());
        final double inputCarry = ELYTRA_EQUIPPED_VELOCITY_HANDOFF_HORIZONTAL_INPUT_CARRY
                * getHorizontalInputScale(thisMove);
        // Elytra model: after invalid history, combine the current server vector with one air-input packet.
        return Math.min(ELYTRA_EQUIPPED_DESCEND_MOVE_GRACE,
                Math.max(thisMove.hAllowedDistance, velocityH + inputCarry
                        + ELYTRA_EQUIPPED_VELOCITY_HANDOFF_HORIZONTAL_RESIDUAL));
    }

    private double getElytraEquippedVelocityVerticalModelLimit(final Player player,
                                                              final PlayerMoveData thisMove,
                                                              final PlayerMoveData lastMove) {
        final double velocityY = player.getVelocity().getY();
        final double followUpY = lastMove.toIsValid
                ? Math.max(0.0D, (lastMove.yDistance - Magic.DEFAULT_GRAVITY) * Magic.FRICTION_MEDIUM_AIR)
                : 0.0D;
        final double model = Math.max(velocityY, followUpY);
        return Math.min(ELYTRA_EQUIPPED_VERTICAL_VELOCITY_Y_GRACE,
                Math.max(model + ELYTRA_EQUIPPED_VELOCITY_VERTICAL_RESIDUAL, thisMove.yAllowedDistance));
    }

    private double getElytraEquippedVelocityHorizontalModelLimit(final Player player,
                                                                final PlayerMoveData thisMove,
                                                                final PlayerMoveData lastMove) {
        final Vector velocity = player.getVelocity();
        final double velocityH = MathUtil.dist(velocity.getX(), velocity.getZ());
        final double lastCarry = lastMove.toIsValid ? lastMove.hDistance : thisMove.hAllowedDistance;
        final double model = Math.max(velocityH, lastCarry);
        return Math.min(ELYTRA_EQUIPPED_VERTICAL_VELOCITY_MOVE_GRACE,
                Math.max(model + ELYTRA_EQUIPPED_VELOCITY_HORIZONTAL_RESIDUAL, thisMove.hAllowedDistance));
    }

    private boolean matchesElytraVelocityFollowupModel(final Player player,
                                                       final PlayerMoveData thisMove,
                                                       final PlayerMoveData lastMove) {
        if (!lastMove.toIsValid || lastMove.yDistance <= 0.30D) {
            return false;
        }
        final double expectedY = Math.max(player.getVelocity().getY(),
                (lastMove.yDistance - Magic.DEFAULT_GRAVITY) * Magic.FRICTION_MEDIUM_AIR);
        final double expectedH = Math.max(MathUtil.dist(player.getVelocity().getX(), player.getVelocity().getZ()),
                lastMove.hDistance);
        return Math.abs(thisMove.yDistance - expectedY) <= ELYTRA_EQUIPPED_VELOCITY_FOLLOWUP_RESIDUAL
                && thisMove.hDistance <= expectedH + ELYTRA_EQUIPPED_VELOCITY_HORIZONTAL_RESIDUAL;
    }

    private boolean isBedrockGroundedCombatVerticalGrace(final Player player, final IPlayerData pData,
                                                         final PlayerLocation from, final PlayerLocation to,
                                                         final PlayerMoveData thisMove,
                                                         final double yDistanceAboveLimit,
                                                         final double hDistanceAboveLimit) {
        // Bedrock compatibility: combat knockback can also show up as a small vertical model miss.
        return isBedrockPlayer(player, pData)
                && !Bridge1_9.isGliding(player)
                && yDistanceAboveLimit <= BEDROCK_GROUNDED_COMBAT_VERTICAL_OVER_GRACE
                && hDistanceAboveLimit <= BEDROCK_GROUNDED_COMBAT_HORIZONTAL_OVER_GRACE
                && thisMove.hDistance <= BEDROCK_GROUNDED_COMBAT_MOVE_GRACE
                && Math.abs(thisMove.yDistance) <= BEDROCK_GROUNDED_COMBAT_VERTICAL_MOVE_GRACE
                && !from.isInLiquid() && !to.isInLiquid()
                && !thisMove.from.inLiquid && !thisMove.to.inLiquid
                && isGroundedCombatMove(player, from, to, thisMove);
    }

    private boolean isModernVerticalImpulseVerticalGrace(final Player player, final IPlayerData pData,
                                                         final PlayerLocation from, final PlayerLocation to,
                                                         final PlayerMoveData thisMove,
                                                         final double yDistanceAboveLimit,
                                                         final double hDistanceAboveLimit) {
        // False-flag model: vertical impulse must fit the current server velocity vector.
        final double horizontalLimit = getModernVerticalImpulseHorizontalModelLimit(player, thisMove);
        final double verticalLimit = getModernVerticalImpulseVerticalModelLimit(player, thisMove);
        return isModernMovementClient(pData)
                && !Bridge1_9.isGliding(player)
                && thisMove.yDistance > Magic.PREDICTION_EPSILON
                && thisMove.yDistance <= verticalLimit
                && thisMove.hDistance <= horizontalLimit
                && yDistanceAboveLimit <= getModelOverLimit(thisMove.yAllowedDistance,
                        verticalLimit, MODERN_VERTICAL_IMPULSE_VERTICAL_RESIDUAL)
                && hDistanceAboveLimit <= getModelOverLimit(thisMove.hAllowedDistance,
                        horizontalLimit, MODERN_VERTICAL_IMPULSE_HORIZONTAL_VELOCITY_GRACE)
                && hasModernVerticalImpulseSource(player, thisMove)
                && currentHorizontalVelocityMatches(player, thisMove, MODERN_VERTICAL_IMPULSE_HORIZONTAL_VELOCITY_GRACE)
                && !from.isInLiquid() && !to.isInLiquid()
                && !thisMove.from.inLiquid && !thisMove.to.inLiquid;
    }

    private double getModernVerticalImpulseHorizontalModelLimit(final Player player,
                                                               final PlayerMoveData thisMove) {
        final double velocityH = MathUtil.dist(player.getVelocity().getX(), player.getVelocity().getZ());
        return Math.min(MODERN_VERTICAL_IMPULSE_MOVE_GRACE,
                Math.max(thisMove.hAllowedDistance, velocityH + MODERN_VERTICAL_IMPULSE_HORIZONTAL_VELOCITY_GRACE));
    }

    private double getModernVerticalImpulseVerticalModelLimit(final Player player,
                                                             final PlayerMoveData thisMove) {
        final double velocityY = player.getVelocity().getY();
        final double usedVelocityY = thisMove.verVelUsed.isEmpty() ? 0.0D : thisMove.verVelUsed.get(0).value;
        return Math.min(MODERN_VERTICAL_IMPULSE_Y_GRACE,
                Math.max(thisMove.yAllowedDistance, Math.max(velocityY, usedVelocityY) + MODERN_VERTICAL_IMPULSE_VERTICAL_RESIDUAL));
    }

    private boolean isModernMovementClient(final IPlayerData pData) {
        return pData.getClientVersion() == ClientVersion.HIGHER_THAN_KNOWN_VERSIONS
                || pData.getClientVersion().isAtLeast(ClientVersion.V_1_21);
    }

    private boolean hasModernVerticalImpulseSource(final Player player, final PlayerMoveData thisMove) {
        return player.getVelocity().getY() >= MODERN_VERTICAL_IMPULSE_MIN_SERVER_VELOCITY
                || !thisMove.verVelUsed.isEmpty()
                || tags.contains("hvel_current")
                || tags.contains("hvel");
    }

    private boolean currentHorizontalVelocityMatches(final Player player, final PlayerMoveData thisMove,
                                                     final double grace) {
        final Vector velocity = player.getVelocity();
        final double velocityH = MathUtil.dist(velocity.getX(), velocity.getZ());
        if (velocityH <= Magic.PREDICTION_EPSILON) {
            return thisMove.hDistance <= grace;
        }
        final double actualH = thisMove.hDistance;
        final double dot = velocity.getX() * thisMove.xDistance + velocity.getZ() * thisMove.zDistance;
        return dot >= -Magic.PREDICTION_EPSILON
                && actualH <= velocityH + grace;
    }

    private boolean currentMotionVectorMatches(final Player player, final PlayerMoveData thisMove,
                                               final double horizontalResidual,
                                               final double verticalResidual) {
        final Vector velocity = player.getVelocity();
        final boolean verticalMatch = velocity.getY() > 0.0D
                && Math.abs(thisMove.yDistance - velocity.getY()) <= verticalResidual;
        return verticalMatch && currentHorizontalVelocityMatches(player, thisMove, horizontalResidual);
    }

    private boolean isBedrockHalfStepVerticalGrace(final Player player, final IPlayerData pData,
                                                   final PlayerLocation from, final PlayerLocation to,
                                                   final PlayerMoveData thisMove, final PlayerMoveData lastMove,
                                                   final double yDistanceAboveLimit,
                                                   final double hDistanceAboveLimit) {
        // Bedrock model: mirror the 0.5 block step geometry for the vertical violation path.
        final boolean groundish = from.isOnGroundOrResetCond() || to.isOnGroundOrResetCond()
                || thisMove.from.onGroundOrResetCond || thisMove.to.onGroundOrResetCond
                || thisMove.touchedGround || thisMove.touchedGroundWorkaround
                || tags.contains("onground_env") || tags.contains("v_air");
        final double horizontalLimit = getBedrockStepHorizontalModelLimit(from, to, thisMove, lastMove);
        return isBedrockPlayer(player, pData)
                && !Bridge1_9.isGliding(player)
                && groundish
                && Math.abs(thisMove.yDistance - BEDROCK_HALF_STEP_VERTICAL_MOVE) <= BEDROCK_HALF_STEP_VERTICAL_EPSILON
                && yDistanceAboveLimit <= getModelOverLimit(thisMove.yAllowedDistance,
                        BEDROCK_HALF_STEP_VERTICAL_MOVE, PARTIAL_SUPPORT_VERTICAL_MODEL_EPSILON)
                && hDistanceAboveLimit <= getModelOverLimit(thisMove.hAllowedDistance,
                        horizontalLimit, PARTIAL_SUPPORT_HORIZONTAL_MODEL_EPSILON)
                && thisMove.hDistance <= horizontalLimit
                && !from.isInLiquid() && !to.isInLiquid()
                && !thisMove.from.inLiquid && !thisMove.to.inLiquid;
    }

    private boolean isBedrockStepVerticalUndershootGrace(final Player player, final IPlayerData pData,
                                                         final PlayerLocation from, final PlayerLocation to,
                                                         final PlayerMoveData thisMove, final PlayerMoveData lastMove,
                                                         final double yDistanceAboveLimit,
                                                         final double hDistanceAboveLimit) {
        // Bedrock model: allow the horizontal half-step packet when the matching Y correction is one packet late.
        final double horizontalLimit = getBedrockStepHorizontalModelLimit(from, to, thisMove, lastMove);
        return isBedrockPlayer(player, pData)
                && !Bridge1_9.isGliding(player)
                && isGroundishStepMove(from, to, thisMove)
                && isStepBlockNear(from, to)
                && Math.abs(thisMove.yDistance) <= BEDROCK_STEP_VERTICAL_UNDERSHOOT_MOVE_GRACE
                && Math.abs(thisMove.yAllowedDistance - BEDROCK_HALF_STEP_VERTICAL_MOVE) <= BEDROCK_STEP_VERTICAL_MODEL_GRACE
                && yDistanceAboveLimit <= getModelOverLimit(thisMove.yAllowedDistance,
                        BEDROCK_HALF_STEP_VERTICAL_MOVE, PARTIAL_SUPPORT_VERTICAL_MODEL_EPSILON)
                && hDistanceAboveLimit <= getModelOverLimit(thisMove.hAllowedDistance,
                        horizontalLimit, PARTIAL_SUPPORT_HORIZONTAL_MODEL_EPSILON)
                && thisMove.hDistance <= horizontalLimit
                && !from.isInLiquid() && !to.isInLiquid()
                && !thisMove.from.inLiquid && !thisMove.to.inLiquid;
    }

    private boolean isGroundishStepMove(final PlayerLocation from, final PlayerLocation to,
                                        final PlayerMoveData thisMove) {
        return from.isOnGroundOrResetCond() || to.isOnGroundOrResetCond()
                || thisMove.from.onGroundOrResetCond || thisMove.to.onGroundOrResetCond
                || thisMove.touchedGround || thisMove.touchedGroundWorkaround
                || tags.contains("onground_env") || tags.contains("v_air");
    }

    private boolean isStepBlockNear(final PlayerLocation from, final PlayerLocation to) {
        return isStepBlock(from.getBlockType())
                || isStepBlock(from.getBlockTypeBelow())
                || isStepBlock(to.getBlockType())
                || isStepBlock(to.getBlockTypeBelow());
    }

    private boolean isStepBlock(final Material material) {
        return material != null
                && (BlockProperties.isStairs(material) || MaterialUtil.SLABS.contains(material));
    }

    private boolean isThinSupportVerticalGrace(final Player player,
                                               final PlayerLocation from, final PlayerLocation to,
                                               final PlayerMoveData thisMove,
                                               final double yDistanceAboveLimit,
                                               final double hDistanceAboveLimit) {
        // False-positive tuning: prevent lantern/trapdoor/carpet support from becoming a vertical setback loop.
        return !Bridge1_9.isGliding(player)
                && isThinSupportNear(from, to)
                && isGroundishStepMove(from, to, thisMove)
                && yDistanceAboveLimit <= THIN_SUPPORT_VERTICAL_OVER_GRACE
                && hDistanceAboveLimit <= THIN_SUPPORT_HORIZONTAL_OVER_GRACE
                && thisMove.hDistance <= THIN_SUPPORT_HORIZONTAL_MOVE_GRACE
                && thisMove.yDistance >= -Magic.PREDICTION_EPSILON
                && thisMove.yDistance <= THIN_SUPPORT_VERTICAL_MOVE_GRACE
                && !from.isInLiquid() && !to.isInLiquid()
                && !thisMove.from.inLiquid && !thisMove.to.inLiquid;
    }

    private boolean isThinSupportNear(final PlayerLocation from, final PlayerLocation to) {
        return isThinSupportBlock(from.getBlockType())
                || isThinSupportBlock(from.getBlockTypeBelow())
                || isThinSupportBlock(to.getBlockType())
                || isThinSupportBlock(to.getBlockTypeBelow());
    }

    private void addPartialSupportTypeTag(final PlayerLocation from, final PlayerLocation to,
                                          final String suffix) {
        tags.add(getPartialSupportTypeTag(from, to) + '_' + suffix);
    }

    private String getPartialSupportTypeTag(final PlayerLocation from, final PlayerLocation to) {
        if (isLanternSupportNear(from, to)) {
            return "lantern_partial_support";
        }
        if (isCarpetSupportNear(from, to)) {
            return "carpet_partial_support";
        }
        if (isStepBlockNear(from, to)) {
            return "step_partial_support";
        }
        if (isFenceLikeSupportNear(from, to)) {
            return "fence_partial_support";
        }
        if (isLeafLitterNear(from, to)) {
            return "leaf_litter_partial_support";
        }
        if (isSnowSupportNear(from, to)) {
            return "snow_partial_support";
        }
        return "partial_support";
    }

    private boolean isLanternSupportNear(final PlayerLocation from, final PlayerLocation to) {
        return isLanternSupportBlock(from.getBlockType())
                || isLanternSupportBlock(from.getBlockTypeBelow())
                || isLanternSupportBlock(to.getBlockType())
                || isLanternSupportBlock(to.getBlockTypeBelow());
    }

    private boolean isCarpetSupportNear(final PlayerLocation from, final PlayerLocation to) {
        return isCarpetSupportBlock(from.getBlockType())
                || isCarpetSupportBlock(from.getBlockTypeBelow())
                || isCarpetSupportBlock(to.getBlockType())
                || isCarpetSupportBlock(to.getBlockTypeBelow());
    }

    private boolean isFenceLikeSupportNear(final PlayerLocation from, final PlayerLocation to) {
        return isFenceLikeSupport(from.getBlockType())
                || isFenceLikeSupport(from.getBlockTypeBelow())
                || isFenceLikeSupport(to.getBlockType())
                || isFenceLikeSupport(to.getBlockTypeBelow());
    }

    private boolean isLeafLitterNear(final PlayerLocation from, final PlayerLocation to) {
        return isLeafLitter(from.getBlockType())
                || isLeafLitter(from.getBlockTypeBelow())
                || isLeafLitter(to.getBlockType())
                || isLeafLitter(to.getBlockTypeBelow());
    }

    private boolean isSnowSupportNear(final PlayerLocation from, final PlayerLocation to) {
        return isSnowSupportBlock(from.getBlockType())
                || isSnowSupportBlock(from.getBlockTypeBelow())
                || isSnowSupportBlock(to.getBlockType())
                || isSnowSupportBlock(to.getBlockTypeBelow());
    }

    private boolean isPartialSupportNear(final PlayerLocation from, final PlayerLocation to) {
        return isPartialSupportBlock(from.getBlockType())
                || isPartialSupportBlock(from.getBlockTypeBelow())
                || isPartialSupportBlock(to.getBlockType())
                || isPartialSupportBlock(to.getBlockTypeBelow());
    }

    private boolean isPartialSupportBlock(final Material material) {
        return isThinSupportBlock(material)
                || isStepBlock(material)
                || isFenceLikeSupport(material)
                || isLeafLitter(material)
                || isSnowSupportBlock(material);
    }

    private boolean isThinSupportBlock(final Material material) {
        return material != null
                && (isLanternSupportBlock(material)
                    || MaterialUtil.ALL_TRAP_DOORS.contains(material)
                    || isCarpetSupportBlock(material));
    }

    private boolean isLanternSupportBlock(final Material material) {
        return material != null
                && (MaterialUtil.LANTERNS.contains(material)
                    || MaterialUtil.COPPER_LANTERNS.contains(material));
    }

    private boolean isCarpetSupportBlock(final Material material) {
        return material != null && MaterialUtil.CARPETS.contains(material);
    }

    private boolean isFenceLikeSupport(final Material material) {
        return material != null
                && (MaterialUtil.WOODEN_FENCES.contains(material)
                    || MaterialUtil.WOODEN_FENCE_GATES.contains(material)
                    || MaterialUtil.ALL_WALLS.contains(material));
    }

    private boolean isLeafLitter(final Material material) {
        return material != null && material.name().endsWith("LEAF_LITTER");
    }

    private boolean isSnowSupportBlock(final Material material) {
        return material == Material.SNOW;
    }

    private double getSnowSupportHeightModel(final PlayerLocation from, final PlayerLocation to) {
        return Math.max(Math.max(getSnowSupportHeight(from, false), getSnowSupportHeight(from, true)),
                Math.max(getSnowSupportHeight(to, false), getSnowSupportHeight(to, true)));
    }

    private double getSnowSupportHeight(final PlayerLocation location, final boolean below) {
        final int blockY = location.getBlockY() - (below ? 1 : 0);
        final Material material = below ? location.getBlockTypeBelow() : location.getBlockType();
        if (!isSnowSupportBlock(material)) {
            return 0.0D;
        }
        final double boundsHeight = getSnowSupportBoundsHeight(location, blockY);
        if (boundsHeight > 0.0D) {
            return boundsHeight;
        }
        final int data = location.getData(location.getBlockX(), blockY, location.getBlockZ()) & 0xF;
        return Math.min(SNOW_SUPPORT_MAX_COLLISION_HEIGHT, Math.max(0.0D, data * SNOW_SUPPORT_LAYER_HEIGHT));
    }

    private double getSnowSupportBoundsHeight(final PlayerLocation location, final int blockY) {
        final double[] bounds = location.getBlockCache().getBounds(location.getBlockX(), blockY, location.getBlockZ());
        if (bounds == null || bounds.length < 5) {
            return 0.0D;
        }
        double height = bounds[4];
        for (int i = 10; i < bounds.length; i += 6) {
            height = Math.max(height, bounds[i]);
        }
        return Math.min(SNOW_SUPPORT_MAX_COLLISION_HEIGHT, Math.max(0.0D, height));
    }

    private boolean isPortalNear(final PlayerLocation from, final PlayerLocation to) {
        return isPortalBlock(from.getBlockType())
                || isPortalBlock(from.getBlockTypeBelow())
                || isPortalBlock(from.getBlockTypeAbove())
                || isPortalBlock(to.getBlockType())
                || isPortalBlock(to.getBlockTypeBelow())
                || isPortalBlock(to.getBlockTypeAbove());
    }

    private boolean isPortalBlock(final Material material) {
        return material != null
                && (material == BridgeMaterial.NETHER_PORTAL || material == BridgeMaterial.END_PORTAL);
    }

    private boolean acceptsGlidingStallVerticalModel(final Player player, final MovingData data,
                                                     final PlayerMoveData thisMove,
                                                     final double yDistanceAboveLimit,
                                                     final double hDistanceAboveLimit) {
        final boolean accepted = Bridge1_9.isGliding(player)
                && data.hasSetBack()
                && yDistanceAboveLimit <= GLIDING_STALL_VERTICAL_OVER_GRACE
                && hDistanceAboveLimit <= GLIDING_STALL_HORIZONTAL_OVER_GRACE
                && Math.abs(thisMove.yDistance) <= GLIDING_STALL_VERTICAL_MOVE_GRACE
                && thisMove.hDistance <= GLIDING_STALL_HORIZONTAL_MOVE_GRACE;
        if (accepted) {
            tags.add("glide_setback_stall_vertical_model");
        }
        return accepted;
    }

    private boolean acceptsElytraLiftOffVerticalModel(final Player player,
                                                      final PlayerMoveData thisMove, final PlayerMoveData lastMove,
                                                      final double yDistanceAboveLimit,
                                                      final double hDistanceAboveLimit) {
        if (!Bridge1_9.isWearingElytra(player)
                || Bridge1_9.isGliding(player)
                || !lastMove.toIsValid
                || lastMove.yDistance <= 0.0D
                || thisMove.yDistance <= 0.0D) {
            return false;
        }
        final double verticalModel = getElytraLiftOffVerticalModel(thisMove, lastMove);
        final double horizontalLimit = getElytraLiftOffHorizontalModelLimit(thisMove, lastMove);
        if (thisMove.yDistance <= verticalModel
                && thisMove.hDistance <= horizontalLimit
                && yDistanceAboveLimit <= getModelOverLimit(thisMove.yAllowedDistance,
                        verticalModel, ELYTRA_LIFTOFF_VERTICAL_OVER_GRACE)
                && hDistanceAboveLimit <= getModelOverLimit(thisMove.hAllowedDistance,
                        horizontalLimit, ELYTRA_LIFTOFF_HORIZONTAL_OVER_GRACE)) {
            tags.add("elytra_liftoff_vertical_model");
            return true;
        }
        tags.add("elytra_liftoff_vertical_model_miss");
        return false;
    }

    private double getElytraLiftOffVerticalModel(final PlayerMoveData thisMove,
                                                 final PlayerMoveData lastMove) {
        return Math.min(ELYTRA_LIFTOFF_MAX_ASCEND, lastMove.yDistance + ELYTRA_LIFTOFF_LAST_Y_GRACE);
    }

    private double getElytraLiftOffHorizontalModelLimit(final PlayerMoveData thisMove,
                                                        final PlayerMoveData lastMove) {
        return Math.min(ELYTRA_LIFTOFF_HORIZONTAL_MOVE_GRACE,
                Math.max(thisMove.hAllowedDistance, lastMove.hDistance + ELYTRA_LIFTOFF_HORIZONTAL_OVER_GRACE));
    }

    private boolean acceptsElytraGeometryStallVerticalModel(final Player player,
                                                            final PlayerMoveData thisMove, final PlayerMoveData lastMove,
                                                            final double yDistanceAboveLimit,
                                                            final double hDistanceAboveLimit) {
        if (!isElytraGeometryStallContext(player, thisMove, lastMove)) {
            return false;
        }
        final double horizontalLimit = getElytraGeometryStallHorizontalModelLimit(thisMove, lastMove);
        final double verticalModel = getElytraGeometryStallVerticalModel(thisMove);
        if (thisMove.hDistance <= horizontalLimit
                && yDistanceAboveLimit <= getModelOverLimit(thisMove.yAllowedDistance,
                        verticalModel, ELYTRA_GEOMETRY_STALL_VERTICAL_OVER_GRACE)
                && hDistanceAboveLimit <= getModelOverLimit(thisMove.hAllowedDistance,
                        horizontalLimit, ELYTRA_GEOMETRY_STALL_HORIZONTAL_OVER_GRACE)) {
            tags.add("elytra_geometry_stall_vertical_model");
            return true;
        }
        tags.add("elytra_geometry_stall_vertical_model_miss");
        return false;
    }

    private boolean isElytraGeometryStallContext(final Player player,
                                                 final PlayerMoveData thisMove,
                                                 final PlayerMoveData lastMove) {
        return Bridge1_9.isWearingElytra(player)
                && !Bridge1_9.isGliding(player)
                && lastMove.toIsValid
                && lastMove.yDistance >= ELYTRA_GEOMETRY_STALL_LAST_ASCEND
                && Math.abs(thisMove.yDistance) <= ELYTRA_GEOMETRY_STALL_MAX_VERTICAL_MOVE;
    }

    private double getElytraGeometryStallHorizontalModelLimit(final PlayerMoveData thisMove,
                                                              final PlayerMoveData lastMove) {
        return Math.min(ELYTRA_GEOMETRY_STALL_HORIZONTAL_MOVE_GRACE,
                Math.max(thisMove.hAllowedDistance, lastMove.hDistance
                        + ELYTRA_GEOMETRY_STALL_HORIZONTAL_OVER_GRACE));
    }

    private double getElytraGeometryStallVerticalModel(final PlayerMoveData thisMove) {
        return thisMove.yDistance >= 0.0D ? ELYTRA_GEOMETRY_STALL_MAX_VERTICAL_MOVE
                : -ELYTRA_GEOMETRY_STALL_MAX_VERTICAL_MOVE;
    }

    private boolean acceptsClimbableVerticalModel(final PlayerLocation from, final PlayerLocation to,
                                                  final PlayerMoveData thisMove,
                                                  final double yDistanceAboveLimit,
                                                  final double hDistanceAboveLimit) {
        // Model cleanup: climbing blocks use their own vertical envelope from open air.
        final boolean climbable = from.isOnClimbable() || to.isOnClimbable()
                || thisMove.from.onClimbable || thisMove.to.onClimbable;
        final ClimbableSurfaceModel model = getClimbableSurfaceModel(from, to);
        if (!climbable || from.isInLiquid() || to.isInLiquid()
                || thisMove.from.inLiquid || thisMove.to.inLiquid
                || hDistanceAboveLimit > model.horizontalResidual) {
            return false;
        }
        final double descendLimit = getClimbableDescendModelLimit(model, thisMove);
        final double descendOverLimit = Math.max(model.descendOverLimit,
                Math.max(0.0D, thisMove.yAllowedDistance + descendLimit)
                        + model.verticalPrecision);
        final boolean accepted = yDistanceAboveLimit <= model.verticalPrecision
                || thisMove.yDistance >= -Magic.PREDICTION_EPSILON
                && thisMove.yDistance <= model.ascendLimit
                || thisMove.yDistance >= -descendLimit
                && thisMove.yDistance <= Magic.PREDICTION_EPSILON
                && yDistanceAboveLimit <= descendOverLimit;
        if (accepted) {
            tags.add(model.tag + "_vertical_model");
            if (descendOverLimit > model.descendOverLimit) {
                tags.add(model.tag + "_descend_clamp_vertical_model");
            }
        }
        return accepted;
    }

    private boolean isWaterVerticalGrace(final Player player, final PlayerLocation from, final PlayerLocation to,
                                         final PlayerMoveData thisMove, final PlayerMoveData lastMove,
                                         final double yDistanceAboveLimit,
                                         final double hDistanceAboveLimit,
                                         final boolean resetFrom, final boolean resetTo) {
        // False-flag model: water Y is matched against buoyancy, liquid exit, or surface-swim physics.
        final boolean inWater = from.isInWater() || to.isInWater()
                || thisMove.from.inWater || thisMove.to.inWater;
        if (!inWater
                || hDistanceAboveLimit > getModelOverLimit(thisMove.hAllowedDistance,
                        getWaterHorizontalModelLimit(player, thisMove, false), WATER_HORIZONTAL_MODEL_EPSILON)) {
            return false;
        }
        if (thisMove.yDistance < -Magic.PREDICTION_EPSILON
                && matchesWaterDescendModel(player, thisMove, lastMove)) {
            return true;
        }
        final double buoyancyModel = getWaterBuoyancyModel(player, thisMove, lastMove);
        if (thisMove.yDistance <= buoyancyModel
                && yDistanceAboveLimit <= getModelOverLimit(thisMove.yAllowedDistance, buoyancyModel, WATER_VERTICAL_MODEL_EPSILON)) {
            return true;
        }
        final boolean setbackLike = !lastMove.toIsValid
                && (resetFrom || thisMove.from.resetCond)
                && (resetTo || thisMove.to.resetCond);
        final boolean resetLike = (resetFrom || thisMove.from.resetCond)
                && (resetTo || thisMove.to.resetCond);
        final boolean surfaceLike = resetLike || tags.contains("v_exiting_liquid")
                || thisMove.collideX || thisMove.collideY || thisMove.collideZ;
        final double surfaceModel = getWaterSurfaceVerticalModel(player, to, thisMove);
        if (surfaceLike && thisMove.yDistance >= -WATER_VERTICAL_MODEL_EPSILON
                && thisMove.yDistance <= surfaceModel
                && yDistanceAboveLimit <= getModelOverLimit(thisMove.yAllowedDistance, surfaceModel, WATER_VERTICAL_MODEL_EPSILON)) {
            tags.add("water_surface_vertical_model");
            return true;
        }
        if (surfaceLike
                && thisMove.yAllowedDistance > Magic.PREDICTION_EPSILON
                && thisMove.yAllowedDistance <= surfaceModel
                && Math.abs(thisMove.yDistance) <= WATER_VERTICAL_MODEL_EPSILON
                && yDistanceAboveLimit <= thisMove.yAllowedDistance + WATER_VERTICAL_MODEL_EPSILON) {
            /*
             * Water model: surface/reset collisions can clamp the vertical packet
             * to zero while buoyancy predicted an upward swim step.
             */
            tags.add("water_surface_still_vertical_model");
            return true;
        }
        if (setbackLike && thisMove.yDistance <= surfaceModel
                && yDistanceAboveLimit <= getModelOverLimit(thisMove.yAllowedDistance, surfaceModel, WATER_VERTICAL_MODEL_EPSILON)) {
            return true;
        }
        return resetLike && matchesVerticalModel(thisMove.yDistance, thisMove.yAllowedDistance, WATER_VERTICAL_MODEL_EPSILON);
    }

    private boolean matchesWaterDescendModel(final Player player, final PlayerMoveData thisMove,
                                             final PlayerMoveData lastMove) {
        final double expectedExitFall = -WATER_EXIT_DESCEND_MODEL;
        if (tags.contains("v_exiting_liquid")
                && matchesVerticalModel(thisMove.yDistance, expectedExitFall, WATER_VERTICAL_MODEL_EPSILON)) {
            tags.add("water_exit_first_gravity_vertical_model");
            return true;
        }
        final double velocityY = player.getVelocity().getY();
        if (matchesVerticalModel(thisMove.yDistance, velocityY, WATER_VERTICAL_MODEL_EPSILON)) {
            tags.add("water_current_velocity_vertical_model");
            return true;
        }
        if (!lastMove.toIsValid) {
            return false;
        }
        if (tags.contains("v_exiting_liquid")
                && matchesVerticalModel(thisMove.yDistance, getWaterExitAirGravityModel(lastMove),
                        WATER_VERTICAL_MODEL_EPSILON)) {
            // Model cleanup: Bedrock and modern clients can switch to air gravity while still sampled as water.
            tags.add("water_exit_air_gravity_vertical_model");
            return true;
        }
        final double liquidDescend = (lastMove.yDistance - Magic.LEGACY_LIQUID_GRAVITY) * Magic.WATER_VERTICAL_INERTIA;
        if (matchesVerticalModel(thisMove.yDistance, liquidDescend, WATER_VERTICAL_MODEL_EPSILON)) {
            tags.add("water_liquid_descend_vertical_model");
            return true;
        }
        return false;
    }

    private double getWaterExitAirGravityModel(final PlayerMoveData lastMove) {
        return (lastMove.yDistance - Magic.DEFAULT_GRAVITY) * Magic.FRICTION_MEDIUM_AIR;
    }

    private double getWaterBuoyancyModel(final Player player, final PlayerMoveData thisMove,
                                         final PlayerMoveData lastMove) {
        final double velocityY = player.getVelocity().getY();
        final double lastBuoyancy = lastMove.toIsValid
                ? (lastMove.yDistance - Magic.LEGACY_LIQUID_GRAVITY) * Magic.WATER_VERTICAL_INERTIA
                        + Magic.LIQUID_SPEED_GAIN
                : Magic.LIQUID_SPEED_GAIN;
        return Math.max(thisMove.yAllowedDistance, Math.max(velocityY, lastBuoyancy)) + WATER_VERTICAL_MODEL_EPSILON;
    }

    private double getWaterSurfaceVerticalModel(final Player player, final PlayerLocation to,
                                                final PlayerMoveData thisMove) {
        final double velocityY = player.getVelocity().getY();
        final boolean exitingWater = tags.contains("v_exiting_liquid") || !to.isInWater() && !thisMove.to.inWater;
        final double surfaceModel = exitingWater ? WATER_SURFACE_EXIT_ASCEND_MODEL : WATER_SURFACE_ASCEND_MODEL;
        return Math.max(surfaceModel, velocityY > 0.0D ? velocityY : 0.0D) + WATER_VERTICAL_MODEL_EPSILON;
    }

    private boolean matchesVerticalModel(final double actual, final double expected, final double epsilon) {
        return Math.abs(actual - expected) <= epsilon;
    }

    private boolean isBedrockPlayer(final Player player, final IPlayerData pData) {
        // Prefer stored player data, then fall back to common Floodgate/Geyser identifiers used during login/session setup.
        return pData.isBedrockPlayer() || player.getName().startsWith(".") || isFloodgateUuid(player) || isFloodgatePlayer(player) || isGeyserPlayer(player);
    }

    private boolean isFloodgateUuid(final Player player) {
        return player.getUniqueId().toString().startsWith("00000000-0000-0000-0009-");
    }

    private boolean isFloodgatePlayer(final Player player) {
        try {
            final Class<?> apiClass = Class.forName("org.geysermc.floodgate.api.FloodgateApi");
            final Object api = apiClass.getMethod("getInstance").invoke(null);
            final Object result = apiClass.getMethod("isFloodgatePlayer", java.util.UUID.class).invoke(api, player.getUniqueId());
            return Boolean.TRUE.equals(result);
        }
        catch (ClassNotFoundException | NoSuchMethodException ignored) {
            return false;
        }
        catch (Throwable ignored) {
            return false;
        }
    }

    private boolean isGeyserPlayer(final Player player) {
        try {
            final Class<?> apiClass = Class.forName("org.geysermc.geyser.api.GeyserApi");
            final Object api = apiClass.getMethod("api").invoke(null);
            return api != null && apiClass.getMethod("connectionByUuid", java.util.UUID.class).invoke(api, player.getUniqueId()) != null;
        }
        catch (ClassNotFoundException | NoSuchMethodException ignored) {
            return false;
        }
        catch (Throwable ignored) {
            return false;
        }
    }
    
    
    /**
     * Estimates the player's horizontal speed based on the given data and Minecraft's movement logic.<br>
     * Order of operations is essential. Do not shuffle things around unless you know what you're doing.
     *<hr>
     * <p>Order of client-movement operations (as per MCP tool):
     * <ul>
     * <li>{@code EntityLiving.tick()}
     * <li>{@code [Entity].tick()}
     *   <ul>
     *     <li>{@code baseTick()}
     *     <li>{@code updateInWaterStateAndDoFluidPushing()}
     *   </ul>
     * <li>{@code EntityLiving.aiStep()}
     *   <ul>
     *     <li>Decrease the jump delay counter if it is active ({@code this.noJumpDelay > 0})
     *     <li>Negligible speed reset (0.003)
     *     <li>Apply liquid motion if the player is pressing the space bar (vertical axis only)
     *     <li>Multiply the input vector (= the vector containing the player's WASD impulse) by 0.98</li>
     *     <li>{@code jumpFromGround()} is called if the player is on ground and has pressed the space bar.
     *   </ul>
     * <li>Begin executing {@code EntityLiving.travel()} ({@code In EntityLiving.aiStep()})<p> <b>Note:</b> from 1.21.2 and onwards, Mojang split the travel function into different helper methods to better
     * distinguish between media (we now have {@code travelInAir()}, {@code travelInFluid()} and {@code travelFallFlying()})</p><br>
     *   <ul>
     *     <li>Invoke {@code [Entity].moveRelative()} (WASD inputs are transformed to acceleration vectors, call {@code getInputVector()})
     *     <li>If not in liquid or gliding, limit motion when on climbable via {@code handleRelativeFrictionAndCalculateMovement()}
     *     <li>Invoke {@code [Entity].move()}
     *       <ul>
     *         <li>Apply stuck speed multiplier
     *         <li>Invoke {@code EntityHuman.maybeBackOffFromEdge()}
     *         <li>Handle wall collisions via {@code Entity.collide()} (speed is cut-off and the collision flag is set) <br>
     * <em><strong>After {@code [Entity].collide()} is called, the next movement is prepared. Every subsequent operation 
     *           applies to the next move for the client.</strong></em>
     *         <li>Set the supporting block data; call {@code setGroundWithMovement()}</li>
     *         <li>Handle horizontal collisions (speed is now reset to 0 on the colliding axis); <em><strong>NCP STARTS ESTIMATING FROM HERE ON (!)</strong></em>
     *         <li>Invoke {@code checkFallDamage()} (apply fluid pushing if not previously in water)
     *         <li>Invoke {@code [Block].updateEntityAfterFallOn()} (for slime bouncing)
     *         <li>Invoke {@code [Block].stepOn()} (for slime blocks only, currently)
     *         <li>Invoke {@code tryCheckInsideBlocks()} (for honey blocks slide-down and bubble columns)
     *         <li>Invoke {@code [Entity].getBlockSpeedFactor()} (soul sand, honey blocks)
     *       </ul>
     *   </ul>
     * <li>Complete executing {@code EntityLiving.travel()}
     *   <ul>
     *     <li>{@code handleRelativeFrictionAndCalculateMovement()} (for snow climbing speed)
     *     <li>Apply gravity.
     *     <li>Apply friction/inertia.
     *     <li>Handle fluid falling function if in liquid (vertical axis only)
     *     <li>Handle jumping out of liquids (vertical axis only)
     *     <li>Handle entity pushing
     *   </ul>
     * <li>Complete {@code EntityLiving.aiStep()}
     * <li>Complete {@code EntityLiving.tick()}
     * <li> Finally, send movement to the server.
     * </ul>
     * <hr>
     * The logic is split into different sections:
     * <li>Firstly, we perform some preliminary checks to quickly catch specific ways of cheating.</li>
     * <li>If no blatant cheating is detected, the movement speed estimate is calculated starting from the horizontal collision reset, calculating the client’s actions on the next move and then processing the actions performed prior.</li>
     * <li>If needed, the player's impulse (acceleration) is brute-forced (see {@link BridgeMisc#isWASDImpulseKnown(Player)}</li>
     * @return {@code true}, if the move has been deemed to be predictable. {@code false} otherwise.
     */
    private boolean estimateNextSpeed(final Player player, float movementSpeed, final IPlayerData pData, final Collection<String> tags,
                                      final PlayerLocation to, final PlayerLocation from, final boolean debug,
                                      final boolean fromOnGround, final boolean toOnGround, final boolean onGround, boolean forceSetOffGround) {
        /*
         * TODO: This is a mess, clean-up pending / needed. Get rid of code duplication
         */
        final MovingData data = pData.getGenericInstance(MovingData.class);
        final CombinedData cData = pData.getGenericInstance(CombinedData.class);
        final PlayerMoveData thisMove = data.playerMoves.getCurrentMove();
        final PlayerMoveData lastMove = data.playerMoves.getFirstPastMove();
        final ClientVersion clientVersion = getMovementClientVersion(pData);
        
        // Reference commit of this piece of code: https://github.com/NoCheatPlus/NoCheatPlus/commit/1c024c072c9f6ebe5371c113916c6a2414e635a6
        /////////////////////////////////////////////////////
        // Horizontal push/pull is put on top priority     //
        /////////////////////////////////////////////////////
        // With the current implementation, the prediction will run for the axis even if a push/pull is detected on it.
        // We'll have to somehow skip predicting that specfic axis, but it requires some refactoring to do it.
        // This is not ideal, but it's better than flagging players for being pushed by pistons.
        final MovingConfig cc = pData.getGenericInstance(MovingConfig.class);
        boolean xPush = false;
        boolean zPush = false;
        // TODO: Get rid of this config option. Why would someone want to disable piston push detection and cause false positives?
        if (cc.trackBlockMove) {
            if (from.matchBlockChange(blockChangeTracker, data.blockChangeRef, thisMove.xDistance < 0.0 ? Direction.X_NEG : Direction.X_POS, 0.05)) {
                tags.add("blkmv_x");
                xPush = true;
            }
            if (from.matchBlockChange(blockChangeTracker, data.blockChangeRef, thisMove.zDistance < 0.0 ? Direction.Z_NEG : Direction.Z_POS, 0.05)) {
                tags.add("blkmv_z");
                zPush = true;
            }
            if (xPush && zPush) {
                thisMove.xAllowedDistance = thisMove.xDistance;
                thisMove.zAllowedDistance = thisMove.zDistance;
                // A push/pull happened on both axes, no need to continue the prediction.
                return true;
            }
        }
        
        ////////////////////////////////////////////////////////
        // Test for specific cheat implementation types first //
        ////////////////////////////////////////////////////////
        // These checks don't need specific data from the prediction, so they can be performed ex-ante and save some performance.
        if (cData.isHackingRI) {
            tags.add("noslowpacket");
            cData.isHackingRI = false;
            Improbable.check(player, (float) thisMove.hDistance, System.currentTimeMillis(), "moving.survivalfly.noslow", pData);
            data.resetHorizontalData();
            return true;
        }
        // If impulses don't need to be inferred from the prediction, illegal sprinting checks can be performed here.
        if (BridgeMisc.isWASDImpulseKnown(player) && pData.isSprinting()
            && (data.input.getForwardDir() != ForwardDirection.FORWARD && data.input.getStrafeDir() != StrafeDirection.NONE && data.input.getForwardDir() != ForwardDirection.NONE
                || player.getFoodLevel() <= 5) // must be checked here as well (besides on toggle sprinting) because players will immediately lose the ability to sprint if food level drops below 5
            ) { 
            // || inputs[i].getForward() < 0.8 // hasEnoughImpulseToStartSprinting, in LocalPlayer,java -> aiStep()
            tags.add("illegalsprint");
            Improbable.check(player, (float) thisMove.hDistance, System.currentTimeMillis(), "moving.survivalfly.illegalsprint", pData);
            data.resetHorizontalData();
            return true;
        }
        
        
        //////////////////////////////////////////
        // Setup theoretical inputs, if needed  //
        //////////////////////////////////////////
        PlayerKeyboardInput input = null; // Precise input
        PlayerKeyboardInput[] theorInputs = null; // All brute-forced inputs.
        /* Index for accessing speed combinations. If you need to perform an operation for/with each speed, set it to 0 and loop until it 8 */
        int i = 0;
        if (BridgeMisc.isWASDImpulseKnown(player)) {
            // Clone for safety as this data is consumed. 
            input = data.input.clone();
            // In EntityLiving.java -> aiStep() the game multiplies input values by 0.98 before dispatching them to the travel() function.
            input.operationToInt(0.98f, 0.98f, 1);
            // From KeyboardInput.java and LocalPlayer.java (MC-Reborn tool)
            // Sneaking and item-use aren't directly applied to the player's motion. The game reduces the force of the input instead.
            if (pData.isInCrouchingPose()) {
                // Note that this is determined by player poses, not shift key presses.
                input.operationToInt(attributeAccess.getHandle().getPlayerSneakingFactor(player), attributeAccess.getHandle().getPlayerSneakingFactor(player), 1);
                tags.add("crouching");
            }
            // From LocalPlayer.java.aiStep()
            if (BridgeMisc.isSlowedDownByUsingAnItem(player)) {
                input.operationToInt(Magic.USING_ITEM_MULTIPLIER, Magic.USING_ITEM_MULTIPLIER, 1);
                tags.add("usingitem");
            }
        }
        else {
            // The input's matrix is: NONE, LEFT, RIGHT, FORWARD, FORWARD_LEFT, FORWARD_RIGHT, BACKWARD, BACKWARD_LEFT, BACKWARD_RIGHT.
            theorInputs = new PlayerKeyboardInput[9];
            // Loop through all combinations otherwise.
            for (int strafe = -1; strafe <= 1; strafe++) {
                for (int forward = -1; forward <= 1; forward++) {
                    // Multiply all 
                    theorInputs[i] = new PlayerKeyboardInput(strafe * 0.98f, forward * 0.98f);
                    i++;
                }
            }
            if (pData.isInCrouchingPose()) {
                tags.add("crouching");
                for (i = 0; i < 9; i++) {
                    // Multiply all combinations
                    theorInputs[i].operationToInt(attributeAccess.getHandle().getPlayerSneakingFactor(player), attributeAccess.getHandle().getPlayerSneakingFactor(player), 1);
                }
            }
            // From LocalPlayer.java.aiStep()
            if (BridgeMisc.isSlowedDownByUsingAnItem(player)) {
                tags.add("usingitem");
                for (i = 0; i < 9; i++) {
                    theorInputs[i].operationToInt(Magic.USING_ITEM_MULTIPLIER, Magic.USING_ITEM_MULTIPLIER, 1);
                }
            }
        }


        //////////////////////////////////////
        // Next move for the client         //
        //////////////////////////////////////
        /*
          All moves are assumed to be predictable, unless we explicitly state otherwise. 
          A move is considered to be predictable if there aren't any particular client-side issues/limitations that prevent it.
         */
        boolean isPredictable = true;
        // Initialize the allowed distance(s) with the previous speed. (Only if we have end-point coordinates)
        // This essentially represents the momentum of the player.
        thisMove.xAllowedDistance = lastMove.toIsValid ? lastMove.xDistance : 0.0;
        thisMove.zAllowedDistance = lastMove.toIsValid ? lastMove.zDistance : 0.0;
        // !lastMove.possibleStopMotion is dummy flag to flip false on failure and retry
        // Check if there had been a hidden move on the prebious tick(s) due to 0.03 threshold.
        // If so, the initial speed is initialized by the corrected distance. xDistance/zDistance would report a wrong speed value.
        if (!lastMove.possibleStopMotion && (lastMove.xCorrectedDistancePre != 0.0 || lastMove.zCorrectedDistancePre != 0.0)) {
            thisMove.xAllowedDistance = lastMove.xCorrectedDistancePre;
            thisMove.zAllowedDistance = lastMove.zCorrectedDistancePre;
            // Distinguish source of CorrectedDistance that only come from hidden move not from stop motion
            if (lastMove.hiddenDistanceIndex != -1) {
                thisMove.possibleStopMotion = true;
            }
            tags.add("hidden");
        }
        // If the player collided with something on the previous tick, start with 0 momentum now.
        doWallCollision(lastMove, thisMove);
        // (The game calls a checkFallDamage() function, which, as you can imagine, handles fall damage. But also handles liquids' flow force, thus we need to apply this 2 times.)
        if (from.isInWater() && !lastMove.from.inWater) {
            Vector liquidFlowVector = from.getLiquidPushingVector(thisMove.xAllowedDistance, thisMove.zAllowedDistance, BlockFlags.F_WATER);
            thisMove.xAllowedDistance += liquidFlowVector.getX();
            thisMove.zAllowedDistance += liquidFlowVector.getZ();
        }
        // Slime speed
        if (from.isOnSlimeBlock() && onGround) {
            /*
             * Specific issue with slime speed: the client tries to fall down with -0.0784 gravity, and then bounce back up to 0 >=. Ground status is set to false then.
             * However, if the bounce-back is smaller than 0.0784, we don't see it on the server-side; we always see the player as being on ground with 0 dist; the multiplier can range from 0.4 to 0.45, depending on the y motion.
             * In other words, this movement is effectively hidden and cannot be predicted, likewise isVerticallyConstricted()...
             * Our solution: always assume the multiplier to be at maximum and allow speed lower than that (in other words, just set a limit). 
             * 
             * Assume it to be a bug. Mojang is never going to fix this stuff anyway.
             */
            if (Math.abs(lastMove.yDistance) < 0.1 && !pData.isShiftKeyPressed()) {
                if (thisMove.yDistance == 0.0) {
                    // Mojang... Why did you have to make the multiplier dependent on vertical motion, why...
                    isPredictable = false;
                    thisMove.xAllowedDistance *= 0.67; // From testing: 0.6 was too little, while 0.7 a bit too much
                    thisMove.zAllowedDistance *= 0.67;
                }
                else {
                    // Otherwise, do attempt to predict. Hopefully this works.
                    thisMove.xAllowedDistance *= 0.4 + Math.abs(lastMove.yDistance) * 0.2;
                    thisMove.zAllowedDistance *= 0.4 + Math.abs(lastMove.yDistance) * 0.2;
                }
                /*
                 * 
                 * For reference: this does not *always* work. Need to test it further.
                 * Bukkit's getVelocity() does actually report the hidden velocity, but it seems to be behind a tick or something.
                 * (In fact, getVelocity() seems to moreso represent the player's momentum than their current speed)
                 * 
                 * if (thisMove.yDistance == 0.0) {
                 *     Vector bukkitMomentum = player.getVelocity().clone();
                 *     thisMove.xAllowedDistance *= 0.4 + Math.abs(bukkitMomentum.getY()) * 0.2;
                 *     thisMove.zAllowedDistance *= 0.4 + Math.abs(bukkitMomentum.getY()) * 0.2;
                 * }
                 * else {
                 *    thisMove.xAllowedDistance *= 0.4 + Math.abs(lastMove.yDistance) * 0.2;
                 *    thisMove.zAllowedDistance *= 0.4 + Math.abs(lastMove.yDistance) * 0.2;
                 * }
                 * 
                 */
            }
        }
        // Sliding speed (honey block)
        if (from.isSlidingDown()) { // TODO: lastMove.from.slideDown or something?
            if (lastMove.yDistance < -Magic.SLIDE_START_AT_VERTICAL_MOTION_THRESHOLD) {
                thisMove.xAllowedDistance *= -Magic.SLIDE_SPEED_THROTTLE / lastMove.yDistance;
                thisMove.zAllowedDistance *= -Magic.SLIDE_SPEED_THROTTLE / lastMove.yDistance;
            }
        }
        // Stuck speed reset (the game resets momentum each tick the player is in a stuck-speed block)
        if (data.lastStuckInBlockHorizontal != 1.0) {
            if (TrigUtil.lengthSquared(data.lastStuckInBlockHorizontal, data.lastStuckInBlockVertical, data.lastStuckInBlockHorizontal) > 1.0E-7) { // (Vanilla check, don't ask)
                // Throttle speed if stuck in.
                thisMove.xAllowedDistance = thisMove.zAllowedDistance = 0.0;
            }
        }
        
        // Block speed
        thisMove.xAllowedDistance *= (double) data.nextBlockSpeedMultiplier;
        thisMove.zAllowedDistance *= (double) data.nextBlockSpeedMultiplier;
        // Friction next, with special case for riptide at the start of the movement tick (when the riptide move is "unified" and not split into two updates; friction of the next move is used here)
        boolean newFriction = false;
        if (lastMove.tridentRelease.decide() && lastMove.toIsValid) {
            final PlayerMoveData secondLastMove = data.playerMoves.getSecondPastMove();
            if (lastMove.from.onGround || secondLastMove.tridentRelease.decideOptimistically() 
                || (secondLastMove.toIsValid && secondLastMove.yDistance <= 0.0 && (secondLastMove.from.onGround || secondLastMove.fromLostGround))) {
                newFriction = true;
            }
        }
        thisMove.xAllowedDistance *= (double) (newFriction ? data.nextInertia : data.lastInertia);
        thisMove.zAllowedDistance *= (double) (newFriction ? data.nextInertia : data.lastInertia);
        // Apply entity-pushing speed
        // From Entity.java.push()
        // The entity's location is in the past.
        if (player.getGameMode() != BridgeMisc.GAME_MODE_SPECTATOR) { // noPhysics check in vanilla.
            Vector push = from.doPush(new Vector(thisMove.xAllowedDistance, 0.0, thisMove.zAllowedDistance));
            thisMove.xAllowedDistance = push.getX();
            thisMove.zAllowedDistance = push.getZ();
            if (data.lastCollidingEntitiesLocations != null && !data.lastCollidingEntitiesLocations.isEmpty()) {
                isPredictable = false;
            }
        }



        //////////////////////////////////
        // Last move for the client     //
        //////////////////////////////////
        // See CombinedListener.java for more details
        // This is done before liquid pushing...
        if (thisMove.hasAttackSlowDown) {
            thisMove.zAllowedDistance *= Magic.ATTACK_SLOWDOWN;
            thisMove.xAllowedDistance *= Magic.ATTACK_SLOWDOWN;
        }
        // Apply liquid pushing speed (2nd call).
        if (from.isInLiquid()) {
            Vector liquidFlowVector = from.getLiquidPushingVector(thisMove.xAllowedDistance, thisMove.zAllowedDistance, from.isInWater() ? BlockFlags.F_WATER : BlockFlags.F_LAVA);
            thisMove.xAllowedDistance += liquidFlowVector.getX();
            verticalLiquidPushComponent = liquidFlowVector.getY();
            thisMove.zAllowedDistance += liquidFlowVector.getZ();
        }
        // Before calculating the acceleration, check if momentum is below the negligible speed threshold and cancel it.
        checkNegligibleMomentum(pData, thisMove);
        // Sprint-jumping...
        // IMPORTANT NOTE: when working **exclusively** with rotations (like in the following cases), you must use the TO location, not the FROM one, as TO contains the most recent rotation. Using FROM lags behind a few ticks, causing false positives when switching looking direction.
        if (PhysicsEnvelope.isBunnyhop(from, to, pData, fromOnGround, toOnGround, player, forceSetOffGround)) {
            thisMove.xAllowedDistance += (double) (-TrigUtil.sin(to.getYaw() * TrigUtil.toRadians) * Magic.BUNNYHOP_BOOST);
            thisMove.zAllowedDistance += (double) (TrigUtil.cos(to.getYaw() * TrigUtil.toRadians) * Magic.BUNNYHOP_BOOST);
            thisMove.bunnyHop = true;
            tags.add("bunnyhop");
        }
        
        /*
         * This bit of vertical distance computation is needed for the supporting block mechanism, which needs Minecraft-calculated on-ground status.
         * Normally, these would be computed at the same time, however, since NCP handles horizontal and vertical speed estimation separately, we need to duplicate this bit of code here.
         * TODO: Unify predictions somehow.
         */
        // Current yDistance before calculation for supporting block ground state. Copy paste from vDistrel
        double yDistanceBeforeCollide = lastMove.toIsValid ? lastMove.yDistance : 0.0; 
        if (lastMove.from.inWater) {
            yDistanceBeforeCollide *= data.lastFrictionVertical;
            if (BridgeMisc.hasGravity(player)) {
                // Legacy: clients older than 1.13 have some kind of gravity effect applied to them even in liquids, if they don't press the space bar.
                // On 1.13 and above, only friction gets applied, resulting in a much slower descending speed when not pressing the space bar pressed.
                if (clientVersion.isLowerThan(ClientVersion.V_1_13)) {
                    yDistanceBeforeCollide -= Magic.LEGACY_LIQUID_GRAVITY;
                } 
                else {
                    // In 1.13 the gravity effect in liquids was removed and this function got added.
                    Vector fluidFallingAdjustMovement = from.getFluidFallingAdjustedMovement(data.lastGravity, yDistanceBeforeCollide <= 0.0, new Vector(0.0, yDistanceBeforeCollide, 0.0), cData.wasSprinting);
                    yDistanceBeforeCollide = fluidFallingAdjustMovement.getY();
                }
            }
        }
        else if (lastMove.from.inLava) {
            if (data.lastFrictionVertical != Magic.LAVA_VERTICAL_INERTIA) {
                yDistanceBeforeCollide *= data.lastFrictionVertical;
                if (BridgeMisc.hasGravity(player)) {
                    if (clientVersion.isLowerThan(ClientVersion.V_1_13)) {
                        yDistanceBeforeCollide -= Magic.LEGACY_LIQUID_GRAVITY;
                    } 
                    else {
                        // In 1.13 the gravity effect in liquids was removed and this function got added.
                        Vector fluidFallingAdjustMovement = from.getFluidFallingAdjustedMovement(data.lastGravity, yDistanceBeforeCollide <= 0.0, new Vector(0.0, yDistanceBeforeCollide, 0.0), cData.wasSprinting);
                        yDistanceBeforeCollide = fluidFallingAdjustMovement.getY();
                    }
                }
            }
            else {
                yDistanceBeforeCollide *= data.lastFrictionVertical;
            }
            if (data.lastGravity != 0.0) {
                yDistanceBeforeCollide += -data.lastGravity / 4.0;
            }
        }
        else {
            // Air motion
            if (cData.wasLevitating) {
                yDistanceBeforeCollide += (0.05 * data.lastLevitationLevel - yDistanceBeforeCollide) * 0.2;
            }
            else yDistanceBeforeCollide -= data.lastGravity;
            yDistanceBeforeCollide *= data.lastFrictionVertical;
        }
        if (from.isInLiquid() && verticalLiquidPushComponent != 0.0) {
            // Liquid vertical push component calculated in hdistrel.
            yDistanceBeforeCollide += verticalLiquidPushComponent;
        }
        //End of yDistanceBeforeCollide getter

        // *--------------------------------------------------------------------------------------------------------------------*
        // *--------- If we know the player's impulse, brute-forcing acceleration and everything after it isn't needed ---------* 
        // *--------------------------------------------------------------------------------------------------------------------*
        if (BridgeMisc.isWASDImpulseKnown(player)) {
            // Transform the input into an acceleration vector (getInputVector, entity.java)
            double inputSq = MathUtil.square((double) input.getStrafe()) + MathUtil.square((double) input.getForward()); // Cast to a double because the client does it
            if (inputSq >= 1.0E-7) {
                if (inputSq > 1.0) {
                    double inputForce = Math.sqrt(inputSq);
                    if (inputForce < 1.0E-4) {
                        // Not enough force, reset.
                        input.operationToInt(0, 0, 0);
                    }
                    // Normalize
                    else input.operationToInt(inputForce, inputForce, 2);
                }
                // Multiply the input by movement speed.
                input.operationToInt(movementSpeed, movementSpeed, 1);
                // The acceleration vector is added to the current momentum...
                thisMove.xAllowedDistance += input.getStrafe() * (double) TrigUtil.cos(to.getYaw() * TrigUtil.toRadians) - input.getForward() * (double) TrigUtil.sin(to.getYaw() * TrigUtil.toRadians);
                thisMove.zAllowedDistance += input.getForward() * (double) TrigUtil.cos(to.getYaw() * TrigUtil.toRadians) + input.getStrafe() * (double) TrigUtil.sin(to.getYaw() * TrigUtil.toRadians);
            }
            // Minecraft caps horizontal speed if on climbable, for whatever reason.
            if (from.isOnClimbable() && !from.isInLiquid()) {
                //data.clearActiveHorVel(); // Might want to clear ALL horizontal vel.
                thisMove.xAllowedDistance = MathUtil.clamp(thisMove.xAllowedDistance, -Magic.CLIMBABLE_MAX_SPEED, Magic.CLIMBABLE_MAX_SPEED);
                thisMove.zAllowedDistance = MathUtil.clamp(thisMove.zAllowedDistance, -Magic.CLIMBABLE_MAX_SPEED, Magic.CLIMBABLE_MAX_SPEED);
            }
            // Stuck-speed multiplier.
            if (TrigUtil.lengthSquared(data.nextStuckInBlockHorizontal, data.nextStuckInBlockVertical, data.nextStuckInBlockHorizontal) > 1.0E-7) {
                thisMove.xAllowedDistance *= (double) data.nextStuckInBlockHorizontal;
                thisMove.zAllowedDistance *= (double) data.nextStuckInBlockHorizontal;
            }
            // Riptide works by propelling the player after releasing the trident (the effect only pushes the player, unless is on ground)
            if (thisMove.tridentRelease.decideOptimistically()) {
                thisMove.tridentRelease = AlmostBoolean.YES;
                Vector riptideVelocity = to.getRiptideVelocity(onGround);
                thisMove.xAllowedDistance += riptideVelocity.getX();
                thisMove.zAllowedDistance += riptideVelocity.getZ();
            }
            // Lunging forward if applicable: the effect only adds the lunging motion on the current delta.
            // TODO: TOTALLY RANDOM PLACEMENT !
            // The addition is called on any left click, provided the player has a Spear in hand with Lunge enchant.
            // NOTE: Does not need to be brute forced, since lunging is supported only by clients that can send WASD inputs.
            if (thisMove.lungingForward) {
                Vector lungeVelocity = to.tryApplyLungingMotion(); // Use to as we're working with rotations here
                thisMove.xAllowedDistance += lungeVelocity.getX();
                thisMove.zAllowedDistance += lungeVelocity.getZ();
            }
            // Try to back off players from edges, if sneaking.
            // NOTE: this is after the riptiding propelling force.
            // NOTE: here the game uses isShiftKeyDown (so this is shifting not sneaking, using Bukkit's isShift is correct)
            if (!player.isFlying() && pData.isShiftKeyPressed() && from.isAboveGround() && thisMove.yDistance <= 0.0) {
                Vector backOff = from.maybeBackOffFromEdge(new Vector(thisMove.xAllowedDistance, yDistanceBeforeCollide, thisMove.zAllowedDistance));
                thisMove.xAllowedDistance = backOff.getX();
                thisMove.zAllowedDistance = backOff.getZ();
            }
            // Collision next.
            // NOTE: Passing the unchecked y-distance is fine in this case. Vertical collision is checked with vdistrel (just separately).
            // TODO: Perhaps after this use collisionVector to store onGround? Also can not restore minecraft ground state with step and jump movement(like stairs)!
            Vector collisionVector = from.collide(new Vector(thisMove.xAllowedDistance, yDistanceBeforeCollide, thisMove.zAllowedDistance), onGround, from.getBoundingBox());
            // Set the supporting block data.
            if (clientVersion.isAtLeast(ClientVersion.V_1_20)) {
                // This is called with setOnGroundWithMovement at the same time of setting the ground flag but before setting the horizontal collision flags.
                // NOTE: here the bounding box of the TO location must be used.
                pData.setSupportingBlockData(SupportingBlockUtils.checkSupportingBlock(to.getBlockCache(), player, pData.getSupportingBlockData(), new Vector(thisMove.xAllowedDistance, thisMove.yDistance, thisMove.zAllowedDistance), to.getBoundingBox(), yDistanceBeforeCollide < 0.0 && yDistanceBeforeCollide != collisionVector.getY()));
            }
            // NOTE: Collision flags must be set before setting speed in thisMove.
            thisMove.collideX = thisMove.xAllowedDistance != collisionVector.getX();
            thisMove.collideZ = thisMove.zAllowedDistance != collisionVector.getZ();
            thisMove.collidesHorizontally = thisMove.collideX || thisMove.collideZ;
            // Set speed.
            thisMove.xAllowedDistance = collisionVector.getX();
            thisMove.zAllowedDistance = collisionVector.getZ();
            // More edge data...
            thisMove.negligibleHorizontalCollision = thisMove.collidesHorizontally && CollisionUtil.isHorizontalCollisionNegligible(new Vector(thisMove.xAllowedDistance, thisMove.yDistance, thisMove.zAllowedDistance), to, input.getStrafe(), input.getForward());
            // Check for block push.
            // TODO: Unoptimized insertion point... Waste of resources to just override everything at the end. See note at the start of the method.
            if (xPush) {
               thisMove.xAllowedDistance = thisMove.xDistance;
            }
            if (zPush) {
                thisMove.zAllowedDistance = thisMove.zDistance;
            }
            //////////////
            // Set data //
            //////////////
            thisMove.hasImpulse = AlmostBoolean.match(input.getForwardDir() != ForwardDirection.NONE || input.getStrafeDir() != StrafeDirection.NONE);
            thisMove.strafeImpulse = input.getStrafeDir();
            thisMove.forwardImpulse = input.getForwardDir();
            if (debug) {
                player.sendMessage(ChatColor.YELLOW + "[SurvivalFly] (postPredict) Direction: " + input.getForwardDir() +" | "+ input.getStrafeDir());
            }
            // If-else instead of an early return... Matter of preference. This makes code slightly easier to look at, as it avoids yet another indentation
            return isPredictable;
        }
        
        
        // *----------------------------------------------------------------------------------------------------*
        // *-------Can't know / read player inputs, loop through everything after looping the acceleration------*
        // *----------------------------------------------------------------------------------------------------*
        // Transform theoretical inputs into acceleration vectors (getInputVector, entity.java)
        float sinYaw = TrigUtil.sin(to.getYaw() * TrigUtil.toRadians);
        float cosYaw = TrigUtil.cos(to.getYaw() * TrigUtil.toRadians);
        /* List of predicted X distances. Size is the number of possible inputs (left/right/backwards/forward etc...) */
        double[] xTheoreticalDistance = new double[9];
        /* To keep track which theoretical speed would result in a collision on the X axis */
        boolean[] collideX = new boolean[9];
        /* List of predicted Z distances. Size is the number of possible inputs (left/right/backwards/forward etc...) */
        double[] zTheoreticalDistance = new double[9];
        /* To keep track which theoretical speed would result in a collision on the Z axis */
        boolean[] collideZ = new boolean[9];
        /* To keep track which theoretical speed would result in a collision on the Y axis */
        boolean[] collideY = new boolean[9];
        for (i = 0; i < 9; i++) {
            // Each slot in the array is initialized with the same momentum first.
            xTheoreticalDistance[i] = thisMove.xAllowedDistance;
            zTheoreticalDistance[i] = thisMove.zAllowedDistance;
            // Then we proceed to compute all possible accelerations with all theoretical inputs and apply subsequent modifiers.
            double inputSq = MathUtil.square((double)theorInputs[i].getStrafe()) + MathUtil.square((double)theorInputs[i].getForward());
            if (inputSq >= 1.0E-7) {
                if (inputSq > 1.0) {
                    double inputForce = Math.sqrt(inputSq);
                    if (inputForce < 1.0E-4) {
                        theorInputs[i].operationToInt(0, 0, 0);
                    }
                    else {
                        theorInputs[i].operationToInt(inputForce, inputForce, 2);
                    }
                }
                theorInputs[i].operationToInt(movementSpeed, movementSpeed, 1);
                xTheoreticalDistance[i] += theorInputs[i].getStrafe() * (double)cosYaw - theorInputs[i].getForward() * (double)sinYaw;
                zTheoreticalDistance[i] += theorInputs[i].getForward() * (double)cosYaw + theorInputs[i].getStrafe() * (double)sinYaw;
            }
        }
        // All later modifiers get applied to each theoretical speed...
        if (from.isOnClimbable() && !from.isInLiquid()) {
            for (i = 0; i < 9; i++) {
                xTheoreticalDistance[i] = MathUtil.clamp(xTheoreticalDistance[i], -Magic.CLIMBABLE_MAX_SPEED, Magic.CLIMBABLE_MAX_SPEED);
                zTheoreticalDistance[i] = MathUtil.clamp(zTheoreticalDistance[i], -Magic.CLIMBABLE_MAX_SPEED, Magic.CLIMBABLE_MAX_SPEED);
            }
        }
        if (TrigUtil.lengthSquared(data.nextStuckInBlockHorizontal, data.nextStuckInBlockVertical, data.nextStuckInBlockHorizontal) > 1.0E-7) {
            for (i = 0; i < 9; i++) {
                xTheoreticalDistance[i] *= (double) data.nextStuckInBlockHorizontal;
                zTheoreticalDistance[i] *= (double) data.nextStuckInBlockHorizontal;
            }
        }
        if (thisMove.tridentRelease.decideOptimistically()) {
            thisMove.tridentRelease = AlmostBoolean.YES;
            Vector riptideVelocity = to.getRiptideVelocity(onGround);
            for (i = 0; i < 9; i++) {
                xTheoreticalDistance[i] += riptideVelocity.getX();
                zTheoreticalDistance[i] += riptideVelocity.getZ();
            }
        }
        if (!player.isFlying() && pData.isShiftKeyPressed() && from.isAboveGround() && thisMove.yDistance <= 0.0) {
            for (i = 0; i < 9; i++) {
                // TODO: Optimize. Brute forcing collisions with all 9 speed combinations will tank performance.
                Vector backOff = from.maybeBackOffFromEdge(new Vector(xTheoreticalDistance[i], yDistanceBeforeCollide, zTheoreticalDistance[i]));
                xTheoreticalDistance[i] = backOff.getX();
                zTheoreticalDistance[i] = backOff.getZ();
            }
        }
        // TODO: Optimize. Brute forcing collisions with all 9 speed combinations will tank performance.
        // TODO: If sprinting detected correctly, Might not need to loop backward, only 6 left to check
        for (i = 0; i < 9; i++) {
            // TODO: Perhaps after this use collisionVector to store onGround?
            Vector collisionVector = from.collide(new Vector(xTheoreticalDistance[i], yDistanceBeforeCollide, zTheoreticalDistance[i]), onGround, from.getBoundingBox());
            if (xTheoreticalDistance[i] != collisionVector.getX()) {
                // This theoretical speed would result in a collision. Remember it.
                collideX[i] = true;
            }
            if (yDistanceBeforeCollide != collisionVector.getY()) {
                // This theoretical speed would result in a collision. Remember it.
                // Only needed for supporting block detection.
                collideY[i] = true;
            }
            if (zTheoreticalDistance[i] != collisionVector.getZ()) {
                // This theoretical speed would result in a collision. Remember it.
                collideZ[i] = true;
            }
            xTheoreticalDistance[i] = collisionVector.getX();
            zTheoreticalDistance[i] = collisionVector.getZ();
            if (lastMove.xCorrectedDistancePost != 0 || lastMove.zCorrectedDistancePost != 0) {
                xTheoreticalDistance[i] += lastMove.xCorrectedDistancePost;
                zTheoreticalDistance[i] += lastMove.zCorrectedDistancePost;
            }
        }
        // Check for block push.
        // TODO: Unoptimized insertion point... Waste of resources to just override everything at the end. See note at the start of the method.
        if (xPush) {
            for (i = 0; i < 9; i++) {
                // Override all theoretical speeds.
                xTheoreticalDistance[i] = thisMove.xDistance;
            }
        }
        if (zPush) {
            for (i = 0; i < 9; i++) {
                zTheoreticalDistance[i] = thisMove.zDistance;
            }
        }
        /////////////////////////////////////////////////////////////////////////////
        // Determine which (and IF) theoretical speed should be set in this move   //
        /////////////////////////////////////////////////////////////////////////////
        /*
           True, if the offset between predicted and actual speed is smaller than the accuracy margin (0.0001).
        */
        boolean found = false;
        /*
           True will check if BOTH axis have an offset smaller than 0.0001 (against strafe-like cheats and anything of that sort that relies on the specific direction of the move).
           Otherwise, only the combined horizontal distance will be checked against the offset.
        */
        boolean strict = cc.survivalFlyStrictHorizontal;
        final double hiddenThreshold = clientVersion.isLowerThan(ClientVersion.V_1_18_2) ? Magic.Minecraft_minMoveSqDistance_legacy : Magic.Minecraft_minMoveSqDist_modern;
        for (i = 0; i < 9; i++) {
            // If this theoretical candidate results in a post-collision horizontal
            // displacement smaller than the client's packet-suppression threshold,
            // it means that the client may have omitted (suppressed) the intermediate
            // packet(s) for this motion. Record the WASD candidate index so it can
            // be used as a seed for the hidden-tick reconstructor.
            // Note: if multiple candidates match, the last matching index will
            // overwrite earlier ones (thisMove.hiddenDistanceIndex holds only one
            // seed candidate).
            if (!collideX[i] && !collideZ[i] && thisMove.yDistance < hiddenThreshold
                && MathUtil.dist(xTheoreticalDistance[i], zTheoreticalDistance[i]) < hiddenThreshold) {
                thisMove.hiddenDistanceIndex = i;
            }
            if (strict) {
                if (MathUtil.almostEqual(thisMove.xDistance, xTheoreticalDistance[i], Magic.PREDICTION_EPSILON) 
                    && MathUtil.almostEqual(thisMove.zDistance, zTheoreticalDistance[i], Magic.PREDICTION_EPSILON)) {
                    found = true;
                }
            }
            else {
                double theoreticalHDistance = MathUtil.dist(xTheoreticalDistance[i], zTheoreticalDistance[i]);
                if (MathUtil.almostEqual(theoreticalHDistance, thisMove.hDistance, Magic.PREDICTION_EPSILON)) {
                    found = true;
                }
            }
            
            /*
               True it will force a violation even if there's a matching theoretical speed.
             */
            boolean forceViolation = false;
            if (found) {
                // These checks must be performed ex-post because they rely on data that is set after the prediction.
                if (pData.isSprinting() 
                    && (theorInputs[i].getForwardDir() != ForwardDirection.FORWARD && theorInputs[i].getStrafeDir() != StrafeDirection.NONE && theorInputs[i].getForwardDir() != ForwardDirection.NONE
                        || player.getFoodLevel() <= 5)) { 
                    tags.add("illegalsprint");
                    Improbable.check(player, (float) thisMove.hDistance, System.currentTimeMillis(), "moving.survivalfly.illegalsprint", pData);
                    // Keep looping
                    forceViolation = true;
                }
                if (thisMove.possibleStopMotion) {
                    double[] result = HiddenMotionReconstructor.simulateStoppingMotion(sinYaw, cosYaw, theorInputs[i], data, pData, from, to, onGround, player.isFlying(), yDistanceBeforeCollide);
                    thisMove.xCorrectedDistancePre = result[0];
                    thisMove.zCorrectedDistancePre = result[1];
                }
                if (!forceViolation) {
                    // Found a candidate to set in this move; these collisions are valid.
                    // Also set the supporting block.
                    if (clientVersion.isAtLeast(ClientVersion.V_1_20)) {
                        pData.setSupportingBlockData(SupportingBlockUtils.checkSupportingBlock(to.getBlockCache(), player, pData.getSupportingBlockData(), new Vector(xTheoreticalDistance[i], thisMove.yDistance, zTheoreticalDistance[i]), to.getBoundingBox(), collideY[i] && yDistanceBeforeCollide < 0.0));
                    }
                    thisMove.collideX = collideX[i];
                    thisMove.collideZ = collideZ[i];
                    thisMove.collidesHorizontally = thisMove.collideX || thisMove.collideZ;
                    thisMove.negligibleHorizontalCollision = thisMove.collidesHorizontally && CollisionUtil.isHorizontalCollisionNegligible(new Vector(xTheoreticalDistance[i], thisMove.yDistance, zTheoreticalDistance[i]), to, theorInputs[i].getStrafe(), theorInputs[i].getForward());
                    break;
                }
            }
        }
        if (!found) {
            // If we couldn't find a direct match for the observed movement, but
            // we previously recorded a candidate that would have been suppressed
            // by the client (hiddenDistanceIndex), attempt to reconstruct the
            // missing intermediate ticks. The reconstructor is seeded with the
            // selected WASD candidate and its post-collision displacement and
            // will return cumulative displacements for the hidden ticks. Those
            // results are then folded into the theoretical distances or stored
            // in the "corrected distance" fields for use across ticks.
            if (thisMove.hiddenDistanceIndex != -1) {
                final double result[] = HiddenMotionReconstructor.findBestHiddenTickExplanation(sinYaw, cosYaw, movementSpeed, theorInputs[thisMove.hiddenDistanceIndex], xTheoreticalDistance[thisMove.hiddenDistanceIndex], zTheoreticalDistance[thisMove.hiddenDistanceIndex], data, pData, from, pData.isInCrouchingPose(), attributeAccess.getHandle().getPlayerSneakingFactor(player), BridgeMisc.isSlowedDownByUsingAnItem(player), onGround, xTheoreticalDistance[thisMove.hiddenDistanceIndex], zTheoreticalDistance[thisMove.hiddenDistanceIndex]);
                if (thisMove.xDistance == 0 && thisMove.zDistance == 0) {
                    thisMove.xCorrectedDistancePost = xTheoreticalDistance[thisMove.hiddenDistanceIndex];
                    thisMove.zCorrectedDistancePost = zTheoreticalDistance[thisMove.hiddenDistanceIndex];
                    xTheoreticalDistance[thisMove.hiddenDistanceIndex] = 0.0;
                    zTheoreticalDistance[thisMove.hiddenDistanceIndex] = 0.0;
                    tags.add("hdistzero");                  
                } 
                else {
                    xTheoreticalDistance[thisMove.hiddenDistanceIndex] += result[0];
                    zTheoreticalDistance[thisMove.hiddenDistanceIndex] += result[1];
                }
                if (strict) {
                    if (MathUtil.almostEqual(thisMove.xDistance, xTheoreticalDistance[thisMove.hiddenDistanceIndex], Magic.PREDICTION_EPSILON) 
                        && MathUtil.almostEqual(thisMove.zDistance, zTheoreticalDistance[thisMove.hiddenDistanceIndex], Magic.PREDICTION_EPSILON)) {
                        i = thisMove.hiddenDistanceIndex;
                        thisMove.xCorrectedDistancePre = result[0];
                        thisMove.zCorrectedDistancePre = result[1];
                        found = true;
                    }
                }
                else {
                    double theoreticalHDistance = MathUtil.dist(xTheoreticalDistance[thisMove.hiddenDistanceIndex], zTheoreticalDistance[thisMove.hiddenDistanceIndex]);
                    if (MathUtil.almostEqual(theoreticalHDistance, thisMove.hDistance, Magic.PREDICTION_EPSILON)) {
                        i = thisMove.hiddenDistanceIndex;
                        found = true;
                    }
                }
            } 
            else if (lastMove.hiddenDistanceIndex != -1 && (lastMove.xCorrectedDistancePre != 0 || lastMove.zCorrectedDistancePre != 0)) {
                // Hidden move was found and sum match lastMove but the component of the sum didn't right as there another direction that also make the sum correct!
                isPredictable = false;
            }
        }
        //////////////////////////////////////////////////////////////
        // Finish. Check if the move had been predictable at all    //
        //////////////////////////////////////////////////////////////
        /* The index representing the input associated with the pair of speed to set in this move. */
        int indexPair = i;
        int xIdx = -1;
        int zIdx = -1;
        if (indexPair >= 9) {
            // Cheating: prevent an index out of bounds (we couldn't find the correct pair of speed to set)
            indexPair = 4;
        }
        // If the move is unpredictable, the x/z speeds cannot be associated to a specific input, thus we set them independently.
        // TODO: How can we know the impulse if the move is uncertain? ...
        if (!isPredictable) {
            // In this case, instead of setting the predicted speed with the smallest delta from the actual speed (0.0001), we select the speed that is closest to the current one, effectively allowing for the maximum predicted speed (just limits speed then).
            xIdx = MathUtil.findClosestIndex(xTheoreticalDistance, thisMove.xDistance);
            zIdx = MathUtil.findClosestIndex(zTheoreticalDistance, thisMove.zDistance);
        }
        // Done, set in this move.
        thisMove.xAllowedDistance = xTheoreticalDistance[!isPredictable ? xIdx : indexPair];
        thisMove.zAllowedDistance = zTheoreticalDistance[!isPredictable ? zIdx : indexPair];
        thisMove.hasImpulse = !isPredictable ? AlmostBoolean.MAYBE // We don't know the direction in this case.
                              : AlmostBoolean.match(theorInputs[indexPair].getForwardDir() != ForwardDirection.NONE || theorInputs[indexPair].getStrafeDir() != StrafeDirection.NONE);
        thisMove.strafeImpulse = theorInputs[isPredictable ? indexPair : xIdx].getStrafeDir();
        thisMove.forwardImpulse = theorInputs[isPredictable ? indexPair : zIdx].getForwardDir();
        if (debug) {
            player.sendMessage(ChatColor.YELLOW + "[SurvivalFly] (postPredict) " + (!isPredictable ? "Uncertain" : "Predicted") + " direction: " + theorInputs[isPredictable ? indexPair : xIdx].getForwardDir() +" | "+ theorInputs[isPredictable ? indexPair : xIdx].getStrafeDir());
        }
        return isPredictable;
    }
    
    
    /**
     * In case we couldn't predict speed, just ensure that actual speed is below what we estimated.
     * This allows for minor deviations below the allowed speed limit, thus players/cheaters may execute movements 
     * that are technically invalid, but do not provide any [significant] gameplay advantage.
     *
     * @param thisMove
     * @param strict If true, only the combined speed (hDistance) is required to be below the allowed one.
     * @return The horizontal distance above limit.
     */
    private double handleUnpredictableMove(final PlayerMoveData thisMove, boolean strict) {
        double hDistanceAboveLimit = 0.0;
        double offset = thisMove.hDistance - thisMove.hAllowedDistance;
        if (strict) {
            // both axes must be below-allowed distance if strict.
            if (MathUtil.exceedsAllowedDistance(thisMove.xDistance, thisMove.xAllowedDistance) 
                || MathUtil.exceedsAllowedDistance(thisMove.zDistance, thisMove.zAllowedDistance)) {
                hDistanceAboveLimit = Math.max(hDistanceAboveLimit, offset);
            }
        } 
        else {
            // Otherwise, only the combined distance needs to be below the limit.
            if (thisMove.hDistance > thisMove.hAllowedDistance) {
                hDistanceAboveLimit = Math.max(hDistanceAboveLimit, offset);
            }
        }
        return hDistanceAboveLimit;
    }
    
    
    /**
     * If the move was predictable, ensure that the difference between actual and allowed speed is below the {@link Magic#PREDICTION_EPSILON}.
     * 
     * @param thisMove
     * @param strict If true, the offset of each axis (x/z) must be below the epsilon, otherwise, only the combined offset (h) will be checked.
     *               Needed against cheats that rely on the specific direction of a move.
     * @return The horizontal distance above limit.
     */
    private double handlePredictableMove(final PlayerMoveData thisMove, boolean strict) {
        double hDistanceAboveLimit = 0.0;
        double offset = thisMove.hDistance - thisMove.hAllowedDistance;
        if (strict) {
            if (!MathUtil.isOffsetWithinPredictionEpsilon(thisMove.xDistance, thisMove.xAllowedDistance) 
                || !MathUtil.isOffsetWithinPredictionEpsilon(thisMove.zDistance, thisMove.zAllowedDistance)) {
                hDistanceAboveLimit = Math.max(hDistanceAboveLimit, offset);
            }
        } 
        else {
            if (!MathUtil.isOffsetWithinPredictionEpsilon(thisMove.hDistance, thisMove.hAllowedDistance)) {
                hDistanceAboveLimit = Math.max(hDistanceAboveLimit, offset);
            }
        }
        return hDistanceAboveLimit;
    }


    /**
     * Relative (to workarounds) vertical distance checking.
     *
     * @param forceResetMomentum    Whether the check should start with 0.0 speed on applying air friction.
     * @param useBlockChangeTracker
     */
    private double[] vDistRel(final Player player, final PlayerLocation from,
                              final boolean fromOnGround, final boolean resetFrom, final PlayerLocation to,
                              final boolean toOnGround, final boolean resetTo,
                              final double yDistance, boolean isNormalOrPacketSplitMove,
                              final PlayerMoveData lastMove,
                              final MovingData data, final IPlayerData pData,
                              boolean forceResetMomentum, final boolean debug, boolean useBlockChangeTracker) {
        double yDistanceAboveLimit = 0.0;
        final PlayerMoveData thisMove = data.playerMoves.getCurrentMove();
        final boolean yDirectionSwitch = lastMove.toIsValid && lastMove.yDistance != yDistance && (yDistance <= 0.0 && lastMove.yDistance >= 0.0 || yDistance >= 0.0 && lastMove.yDistance <= 0.0);
        /* Not on ground, not on climbable, not in liquids, not in stuck-speed, no lostground (...) */
        final boolean fullyInAir = !thisMove.touchedGroundWorkaround && !resetFrom && !resetTo;
        final CombinedData cData = pData.getGenericInstance(CombinedData.class);
        final ClientVersion clientVersion = getMovementClientVersion(pData);
        final boolean onGround = from.isOnGround() || lastMove.toIsValid && lastMove.yDistance <= 0.0 && lastMove.from.onGround;
        /*
         * 1: Simulate the reset of speed that the client should have sent to the server.
         * [Client lands on the ground but does not come to a "rest" on top of the block (and thus, reset the vertical speed), instead they'll immediately descend right after, but with speed that is still based on a previous move of 0.0]
         * Can be noticed when stepping down stair of slabs or noob-towering upwards.
         * See: https://gyazo.com/0f748030296aebc0484564629abe6864
         * After interpolating the ground status, notice how the player immediately proceeds to descend with speed as if they actually landed on the ground with the previous move (-0.0784)
         */
        // After completing a "touch-down" (toOnGround), the next move should always come *from* ground
        // Thus, such cases can be generalised by checking for negative motion and last move landing on ground, but this move not *starting back* from a ground position.
        boolean touchDownIsLost = !thisMove.couldStepUp && thisMove.yDistance < 0.0 && (lastMove.toLostGround || lastMove.to.onGround) && !thisMove.from.onGround;
        
        ///////////////////////////////////////////////////
        // Vertical push/pull is put on top priority     //
        ///////////////////////////////////////////////////
        if (useBlockChangeTracker) {
            double[] res = getVerticalBlockMoveResult(thisMove.yDistance, from, to, data);
            if (res != null) {
                thisMove.yAllowedDistance = res[0];
                yDistanceAboveLimit = res[1];
                // Nothing else to do here; allow the movement as-is.
                return new double[]{thisMove.yAllowedDistance, yDistanceAboveLimit};
            }
        }
        
        
        //////////////////////////////////////////////////////////////////////////////
        // Test if this movement can fit into any pre-set envelope                  //
        //////////////////////////////////////////////////////////////////////////////
        // NOTE: order of these checks should be from most common to least common.
        if (thisMove.yDistance == 0.0 && fromOnGround) {
            // No vertical motion in this case, as the player is on ground.
            thisMove.yAllowedDistance = 0.0;
            yDistanceAboveLimit = 0.0;
            tags.add("onground_env");
            return new double[]{thisMove.yAllowedDistance, yDistanceAboveLimit};
        }
        if (isBedrockLanternCollisionMove(player, pData, from, to, thisMove)) {
            // Bedrock clients can send small 1/16-ish vertical corrections while moving through lantern collision.
            thisMove.yAllowedDistance = thisMove.yDistance;
            yDistanceAboveLimit = 0.0;
            tags.add("bedrock_lantern");
            return new double[]{thisMove.yAllowedDistance, yDistanceAboveLimit};
        }
        if (PhysicsEnvelope.isJumpMotion(from, to, player, fromOnGround, toOnGround)) {
            // After stepping, jumping comes second.
            // Players can jump anywhere through air, so this must be checked before the actual prediction.
            thisMove.yAllowedDistance = thisMove.yDistance;
            yDistanceAboveLimit = 0.0;
            thisMove.isJump = true;
            data.jumpDelay = Magic.MAX_JUMP_DELAY;
            tags.add("jump_env");
            return new double[]{thisMove.yAllowedDistance, yDistanceAboveLimit};
        }
        if (PhysicsEnvelope.isStepUpByNCPDefinition(pData, fromOnGround, toOnGround, player)) {
            // Players can step anywhere, both in liquid and in air, so this must be checked before everything else.
            thisMove.yAllowedDistance = thisMove.yDistance;
            yDistanceAboveLimit = 0.0;
            thisMove.isStepUp = true;
            tags.add("step_env");
            return new double[]{thisMove.yAllowedDistance, yDistanceAboveLimit};
        }
        if (onGround && thisMove.tridentRelease.decideOptimistically() && thisMove.multiMoveCount == 1 
            && !isNormalOrPacketSplitMove && MathUtil.isOffsetWithinPredictionEpsilon(thisMove.yDistance, Magic.RIPTIDE_ON_GROUND_MOVE)) {
            // Riptide from ground launch at the end of the movement stack; the actual riptide push will come next. Allowed this move as-is.
            // IMPORTANT NOTE: this move specifically will always cause NCP to fire a Bukkit-based split move on the first split move, no matter what.
            thisMove.yAllowedDistance = Magic.RIPTIDE_ON_GROUND_MOVE;
            // The riptide move was split into two moves: 1.2 upwards from ground and then the actual riptide push.
            // We can therefore set the flag to YES from here on. The push will be handled below.
            data.setTridentReleaseEvent(AlmostBoolean.YES);
            yDistanceAboveLimit = 0.0;
            tags.add("gnd_riptide_pre");
            return new double[]{thisMove.yAllowedDistance, yDistanceAboveLimit};
        }
        
        
        ///////////////////////////////////////////////////////////////////////////////////
        // Estimate the allowed yDistance (per-move distance check)                      //
        ///////////////////////////////////////////////////////////////////////////////////
        /* 
           0: With space bar pressed. 
           1: with space bar not pressed 
           2: swimming not applied at all
         */
        double[] yTheoreticalDistance = null;
        boolean[] collideLiquidY = null;
        // Initialize with momentum (or lack thereof)
        // TODO: Not sure block.updateEntityAfterFallOn (lastMove.yDistance < 0.0 && fromOnGround) put here is correct?
        thisMove.yAllowedDistance = forceResetMomentum || touchDownIsLost 
                                    || (lastMove.yDistance < 0.0 && (fromOnGround || thisMove.fromLostGround))
                                    || !lastMove.toIsValid ? 0.0 : lastMove.yDistance;
        if (lastMove.yCorrectedDistancePre != 0.0) thisMove.yAllowedDistance = lastMove.yCorrectedDistancePre;
        //////////////////////////////////////
        // Next client-tick/move            //
        //////////////////////////////////////
        // *----------updateEntityAfterFallOn()----------*
        // NOTE: pressing space bar on a bouncy block will override the bounce (in that case, vdistrel will fall back to the jump check above).
        // updateEntityAfterFallOn(), this function is called on the next move
        if (pData.isShiftKeyPressed() && lastMove.collideY) { 
            if (thisMove.yAllowedDistance < 0.0) { // NOTE: Must be the allowed distance, not the actual one (exploit)
                if (lastMove.to.onBouncyBlock) {
                    // The effect works by inverting the distance.
                    // Beds have a weaker bounce effect (BedBlock.java).
                    thisMove.yAllowedDistance = lastMove.to.onSlimeBlock ? -thisMove.yAllowedDistance : -thisMove.yAllowedDistance * 0.66;
                    tags.add("bounceup");
                }
            }
        }
        // *----------tryCheckInsideBlocks()----------*
        // Bubble columns are checked in the tryCheckInsideBlocks method, so it comes after updateEntityAfterFallOn()...
        Vector bubbleVector = from.tryApplyBubbleColumnMotion(new Vector(0.0, thisMove.yAllowedDistance, 0.0));
        thisMove.yAllowedDistance = bubbleVector.getY();
        // Honey block sliding mechanic...
        if (from.isSlidingDown()) {
            // Speed is static in this case
            thisMove.yAllowedDistance = -Magic.SLIDE_SPEED_THROTTLE;
        }
        // *----------stuck-speed-momentum-reset----------*
        if (TrigUtil.lengthSquared(data.lastStuckInBlockHorizontal, data.lastStuckInBlockVertical, data.lastStuckInBlockHorizontal) > 1.0E-7) {
            if (data.lastStuckInBlockVertical != 1.0) {
                thisMove.yAllowedDistance = 0.0;
            }
        }
        // *----------Finalization of handleRelativeFrictionAndCalculateMovement; this check/condition is called after having called the move() function. The former method is called only when the player is traveling in air, thus the liquid and gliding checks ----------*
        if (!lastMove.from.inLiquid && !lastMove.isGliding) {
            // TODO: We have to loop the jumping state for 1.21.1 and below... No other way to put it unfortunately. This will make the code an ugly mess than it already is.
            final boolean jumpedOrCollided = lastMove.collidesHorizontally || data.input.wasSpaceBarPressed() && BridgeMisc.isSpaceBarImpulseKnown(player);
            if (jumpedOrCollided && (lastMove.from.onClimbable || lastMove.from.touchedPowderSnow && BridgeMisc.canStandOnPowderSnow(player))) { 
                thisMove.yAllowedDistance = 0.2;
            }
        }
        // *----------Gravity, friction and other medium-dependent modifiers in LivingEntity.travel() (water first, then lava and finally air)----------*
        data.nextGravity = attributeAccess.getHandle().getGravity(player);
        if (lastMove.from.inWater) {
            if (lastMove.collidesHorizontally && lastMove.from.onClimbable && clientVersion.isAtLeast(ClientVersion.V_1_14)) {
                thisMove.yAllowedDistance = 0.2;
            }
            // Water applies friction before calling the fluidFalling function.
            thisMove.yAllowedDistance *= data.lastFrictionVertical;
            if (BridgeMisc.hasGravity(player)) {
                // Legacy: clients older than 1.13 have some kind of gravity effect applied to them even in liquids, if they don't press the space bar.
                // On 1.13 and above, only friction gets applied, resulting in a much slower descending speed when not pressing the space bar pressed.
                if (clientVersion.isLowerThan(ClientVersion.V_1_13)) {
                    thisMove.yAllowedDistance -= Magic.LEGACY_LIQUID_GRAVITY;
                } 
                else {
                    // In 1.13 the gravity effect in liquids was removed and this function got added.
                    Vector fluidFallingAdjustMovement = from.getFluidFallingAdjustedMovement(data.lastGravity, thisMove.yAllowedDistance <= 0.0, new Vector(0.0, thisMove.yAllowedDistance, 0.0), cData.wasSprinting);
                    thisMove.yAllowedDistance = fluidFallingAdjustMovement.getY();
                }
            }
            tags.add("v_water");
        }
        else if (lastMove.from.inLava) {
            // Lava friction is quite odd. Depending on specified thresholds, it can be 0.5 or 0.8
            if (data.lastFrictionVertical != Magic.LAVA_VERTICAL_INERTIA) { // Note that this condition is not vanilla. It's just a shortcut to avoid replicating the condition contained in BlockProperties.getBlockFrictionFactor.
                thisMove.yAllowedDistance *= data.lastFrictionVertical;
                if (BridgeMisc.hasGravity(player)) {
                    if (clientVersion.isLowerThan(ClientVersion.V_1_13)) {
                        thisMove.yAllowedDistance -= Magic.LEGACY_LIQUID_GRAVITY;
                    } 
                    else {
                        // getFluidFallingAdjustedMovement is only applied if friction is 0.8.
                        Vector fluidFallingAdjustMovement = from.getFluidFallingAdjustedMovement(data.lastGravity, thisMove.yAllowedDistance <= 0.0, new Vector(0.0, thisMove.yAllowedDistance, 0.0), cData.wasSprinting);
                        thisMove.yAllowedDistance = fluidFallingAdjustMovement.getY();
                    }
                }
            }
            else {
                // Otherwise, 0.5
                thisMove.yAllowedDistance *= data.lastFrictionVertical;
            }
            if (data.lastGravity != 0.0) {
                thisMove.yAllowedDistance += -data.lastGravity / 4.0;
            }
            tags.add("v_lava");
        }
        else {
            // Air motion
            if (cData.wasLevitating) {
                // Levitation forces players to ascend and does not work in liquids, so thankfully we don't have to account for that, other than stuck-speed.
                thisMove.yAllowedDistance += (0.05 * data.lastLevitationLevel - lastMove.yAllowedDistance) * 0.2;
            }
            else thisMove.yAllowedDistance -= data.lastGravity;
            thisMove.yAllowedDistance *= data.lastFrictionVertical;
            tags.add("v_air");
        }
        // *----------Finalize LivingEntity.travel; isFree() check----------*
        // Try making the player jump out of the liquid... 
        // This condition is the same for both lava and water, and is always done at the end of the travel() function.
        if (lastMove.from.inLiquid && lastMove.collidesHorizontally 
            // TODO: Somewhat work. Incorrect horizontal move. Require this function call at the time BOTH horizontal and vertical calculating at the same time. Which is not possible with current infrastructure
            && from.isUnobstructed()) {
            thisMove.yAllowedDistance = 0.3;
            tags.add("v_exiting_liquid");
        }
        
        
        //////////////////////////////////
        // Last client-tick/move        //
        //////////////////////////////////
        if (from.isInLiquid() && verticalLiquidPushComponent != 0.0) {
            // Liquid vertical push component calculated in hdistrel.
            thisMove.yAllowedDistance += verticalLiquidPushComponent;
        }
        // *----------LivingEntity.aiStep(), negligible speed----------*
        checkNegligibleMomentumVertical(pData, thisMove);
        // *----------LivingEntity.travel(), handleRelativeFrictionAndCalculateMovement() -> handleOnClimbable()----------*
        // TODO: Is it correct to put here?
        if (!from.isInLiquid() && from.isOnClimbable() && from.canClimbUp(data.liftOffEnvelope.getMaxJumpHeight(data.jumpAmplifier))) {
            thisMove.yAllowedDistance = Math.max(thisMove.yAllowedDistance, -Magic.CLIMBABLE_MAX_SPEED);
            // Should replicate the condition: !this.getInBlockState().is(Blocks.SCAFFOLDING)
            final Material typeId = from.getBlockType();
            final long theseFlags = BlockFlags.getBlockFlags(typeId);
            if (thisMove.yAllowedDistance < 0.0 && pData.isShiftKeyPressed() && from.getEntity() instanceof Player
                && (theseFlags & BlockFlags.F_SCAFFOLDING) == 0 && clientVersion.isAtLeast(ClientVersion.V_1_14)) {
                thisMove.yAllowedDistance = 0.0;
            }
            tags.add("v_climbable");
        }
        // *----------EntityLiving.aiStep(), apply liquid motion----------*
        if (from.isInLiquid()) {
            // *----------LocalPlayer.aiStep(), goDownInWater()----------*
            if (pData.isShiftKeyPressed() && from.isInWater()) {
                thisMove.yAllowedDistance -= Magic.LIQUID_SPEED_GAIN;
            }
            // *----------------------------------------------------------------------------------------------------------------------------*
            // *----- When in liquid, the game doesn't care about players being on ground, only if they press the space bar.   -------------*
            // *----- When they do press it, the game sets the jumping field to true.   ----------------------------------------------------*
            // *----- However, up until MC 1.21.2 we couldn't know this, because the player used to not send anything about it -------------*
            // *----- Solution: if the client/server does not support input reading/sending, loop the space bar impulse   ------------------*
            // *----------------------------------------------------------------------------------------------------------------------------* 
            if (BridgeMisc.isSpaceBarImpulseKnown(player)) {
                // From: EntityLiving.java -> aiStep() and KeyboardInput.java.
                if (data.input.isSpaceBarPressed()) {
                    boolean isSubmergedInWater = from.isInWater() && thisMove.submergedWaterHeight > 0.0;
                    double fluidJumpThreshold = from.getEyeHeight() < 0.4D ? 0.0D : 0.4D;
                    if (isSubmergedInWater && (!onGround || thisMove.submergedWaterHeight > fluidJumpThreshold)) {
                        thisMove.yAllowedDistance += Magic.LIQUID_SPEED_GAIN; // The game distinguishes liquid tagkeys, but the motion is the same...
                    } 
                    else if (from.isInLava() && (!onGround || thisMove.submergedLavaHeight > fluidJumpThreshold)) {
                        thisMove.yAllowedDistance += Magic.LIQUID_SPEED_GAIN;
                    } 
                    else if ((onGround || isSubmergedInWater && thisMove.submergedWaterHeight <= fluidJumpThreshold) && data.jumpDelay == 0) {
                        thisMove.yAllowedDistance = data.liftOffEnvelope.getJumpGain(data.jumpAmplifier) * attributeAccess.getHandle().getJumpGainMultiplier(player);
                        data.jumpDelay = Magic.MAX_JUMP_DELAY;
                        thisMove.hasImpulse = AlmostBoolean.YES; // Minecraft explicitly tells us that there's impulse in this case.
                        thisMove.isJump = true;
                    }
                } 
                else {
                    data.jumpDelay = 0;
                }
                //*--------Player.java, travel(). Apply swimming speed-------*
                // 1.13 swimming speed depends on the looking direction vector of the player.
                // Small note: the game here does NOT explicitly ensure that the player is also in water. Thus, this should be checked outside the from.isInLiquid() condition
                if (Bridge1_13.isSwimming(player) && from.getEntity() instanceof Player) { // inside vehicle checking would always return false, since Sf doesn't run for vehicles, but in the future, we might merge vehicle checks
                    Vector lookVector = TrigUtil.getLookingDirection(to, player);
                    double swimmingScalar = lookVector.getY() < -0.2 ? 0.085 : 0.06;
                    if (lookVector.getY() <= 0.0 || data.input.isSpaceBarPressed()
                        || BlockProperties.getLiquidHeightAt(from.getBlockCache(), Location.locToBlock(from.getX()), Location.locToBlock(from.getY() + 1.0 - 0.1), Location.locToBlock(from.getZ()), BlockFlags.F_WATER, true) != 0.0) {
                        thisMove.yAllowedDistance += (lookVector.getY() - thisMove.yAllowedDistance) * swimmingScalar;
                    }
                }
            }
            else {
                // *----------------------------------*
                // *--- Loop the space bar impulse ---*
                // *----------------------------------*
                // Initialize with the momentum that has hitherto been calculated.
                yTheoreticalDistance = new double[3];
                collideLiquidY = new boolean[3];
                // With space bar pressed
                yTheoreticalDistance[0] = thisMove.yAllowedDistance;
                // With space bar not pressed
                yTheoreticalDistance[1] = thisMove.yAllowedDistance;
                // With swimming speed not applied
                yTheoreticalDistance[2] = thisMove.yAllowedDistance;
                boolean isSubmergedInWater = from.isInWater() && thisMove.submergedWaterHeight > 0.0;
                double fluidJumpThreshold = from.getEyeHeight() < 0.4D ? 0.0D : 0.4D;
                if (isSubmergedInWater && (!onGround || thisMove.submergedWaterHeight > fluidJumpThreshold)) {
                    yTheoreticalDistance[0] += Magic.LIQUID_SPEED_GAIN;
                }
                else if (from.isInLava() && (!onGround || thisMove.submergedLavaHeight > fluidJumpThreshold)) {
                    yTheoreticalDistance[0] += Magic.LIQUID_SPEED_GAIN;
                }
                else if ((onGround || isSubmergedInWater && thisMove.submergedWaterHeight <= fluidJumpThreshold) && data.jumpDelay == 0) {
                    yTheoreticalDistance[0] = data.liftOffEnvelope.getJumpGain(data.jumpAmplifier) * attributeAccess.getHandle().getJumpGainMultiplier(player);
                    data.jumpDelay = Magic.MAX_JUMP_DELAY;
                    thisMove.hasImpulse = AlmostBoolean.YES;
                    // (Can't set thisMove.isJump yet.)
                }
                if (Bridge1_13.isSwimming(player) && !player.isInsideVehicle()) {
                    Vector lookVector = TrigUtil.getLookingDirection(to, player);
                    double swimmingScalar = lookVector.getY() < -0.2 ? 0.085 : 0.06;
                    // Note: Since thisMove.isJump is always false because not been set yet, make these conditions unusable, result in brute force
                    //if (lookVector.getY() <= 0.0 || thisMove.isJump 
                    //    || BlockProperties.getLiquidHeightAt(from.getBlockCache(), Location.locToBlock(from.getX()), Location.locToBlock(from.getY()+1.0-0.1), Location.locToBlock(from.getZ()), BlockFlags.F_WATER, true) != 0.0) {
                    yTheoreticalDistance[0] += (lookVector.getY() - yTheoreticalDistance[0]) * swimmingScalar;
                    yTheoreticalDistance[1] += (lookVector.getY() - yTheoreticalDistance[1]) * swimmingScalar;
                    //}
                }
            }
        }
        // *----------Beginning of EntityLiving.travel(); call Entity.move(); apply stuck speed multipliers----------*
        if (TrigUtil.lengthSquared(data.nextStuckInBlockHorizontal, data.nextStuckInBlockVertical, data.nextStuckInBlockHorizontal) > 1.0E-7) {
            // If we looped the space bar impulse, all later modifiers are applied to each speed.
            if (yTheoreticalDistance != null) {
                for (int i = 0; i < yTheoreticalDistance.length; i++) {
                    yTheoreticalDistance[i] *= data.nextStuckInBlockVertical;
                }
            }
            else thisMove.yAllowedDistance *= data.nextStuckInBlockVertical;
        }
        // *----------TridentItem.releaseUsing(), apply trident motion----------*
        if (thisMove.tridentRelease.decideOptimistically()) {
            thisMove.tridentRelease = AlmostBoolean.YES;
            // Riptide works by propelling the player in air after releasing the trident (the effect only pushes the player, unless is on ground)
            final Vector riptideVelocity = to.getRiptideVelocity(onGround);
            final double fric = getLastYGroundRipTide(from, lastMove, data, cData);
            if (fric != 0.0) {
                if (yTheoreticalDistance != null) {
                    for (int i = 0; i < yTheoreticalDistance.length; i++) {
                        yTheoreticalDistance[i] = riptideVelocity.getY() + fric;
                    }
                }
                else thisMove.yAllowedDistance = riptideVelocity.getY() + fric;
            } 
            else {
                if (yTheoreticalDistance != null) {
                    for (int i = 0; i < yTheoreticalDistance.length; i++) {
                        yTheoreticalDistance[i] += riptideVelocity.getY();
                    }
                }
                else thisMove.yAllowedDistance += riptideVelocity.getY();
            }
        }
        //  Special case for riptide at the start of the movement tick (when the riptide move is "unified" and not split into two updates; friction of the next move is used here)
        if (lastMove.tridentRelease.decide() && lastMove.toIsValid) {
            final PlayerMoveData secondLastMove = data.playerMoves.getSecondPastMove();
            if (lastMove.from.onGround || (secondLastMove.toIsValid && secondLastMove.yDistance <= 0.0 && (secondLastMove.from.onGround || secondLastMove.fromLostGround))) {
                if (yTheoreticalDistance != null) {
                    for (int i = 0; i < yTheoreticalDistance.length; i++) {
                        yTheoreticalDistance[i] -= Magic.RIPTIDE_ON_GROUND_MOVE * data.lastFrictionVertical;
                    }
                }
                else thisMove.yAllowedDistance -= Magic.RIPTIDE_ON_GROUND_MOVE * data.lastFrictionVertical;
            }
        }
        // *----------Entity.move(), call the collide() function----------*
        // Include horizontal motion to account for stepping: there are cases where NCP's isStep definition fails to catch it.
        // (In which case, isStep will return false and fall-back to friction here)
        // It is imperative that you pass yAllowedDistance as argument here (not the real yDistance), because if the player isn't on ground, the current motion will be used to determine it (collideY && motionY < 0.0). Passing an uncontrolled yDistance will be easily exploitable.
        if (yTheoreticalDistance == null) {
            Vector collisionVector = from.collide(new Vector(thisMove.xAllowedDistance, thisMove.yAllowedDistance, thisMove.zAllowedDistance), fromOnGround || thisMove.fromLostGround && lastMove.yDistance < 0.0, from.getBoundingBox());
            thisMove.headObstructed = thisMove.yAllowedDistance != collisionVector.getY() && thisMove.yDistance >= 0.0 && from.seekCollisionAbove() && !fromOnGround;  // New definition of head obstruction: yDistance is checked because Minecraft considers players to be on ground when motion is explicitly negative
            // If this vertical move resulted in a collision, remember it.
            thisMove.collideY = collisionVector.getY() != thisMove.yAllowedDistance;
            // Switch to descent phase after colliding above.
            if (lastMove.headObstructed && !thisMove.headObstructed && yDirectionSwitch && thisMove.yDistance <= 0.0 && fullyInAir) { // TODO: Is the gravity-reiteration fix needed for liquids?
                // Fix for clients not sending the "speed-reset move" to the server: player collides vertically with a ceiling, then proceeds to descend.
                // Normally, speed is set back to 0.0 and then gravity is applied. This movement however is never actually sent to the server: what we see on the server-side is the player immediately descending (negative motion), but with motion that is still based on a previous move of 0.0 speed.
                thisMove.yAllowedDistance = 0.0; // Simulate what the client should be doing and re-iterate gravity
                if (BridgeMisc.hasGravity(player)) {
                    thisMove.yAllowedDistance -= data.nextGravity; // This should be the current (next) gravity not the last one
                }
                thisMove.yAllowedDistance *= data.nextFrictionVertical;
                tags.add("gravity_reiterate");
            } 
            else thisMove.yAllowedDistance = collisionVector.getY();
        }
        else {
            for (int i = 0; i < yTheoreticalDistance.length; i++) {
                Vector collisionVector = from.collide(new Vector(thisMove.xAllowedDistance, yTheoreticalDistance[i], thisMove.zAllowedDistance), fromOnGround || thisMove.fromLostGround && lastMove.yDistance < 0.0, from.getBoundingBox());
                if (yTheoreticalDistance[i] != collisionVector.getY()) {
                    // This theoretical speed would result in a collision. Remember it.
                    collideLiquidY[i] = true;
                }
                yTheoreticalDistance[i] = collisionVector.getY();
                thisMove.headObstructed = yTheoreticalDistance[i] != collisionVector.getY() && thisMove.yDistance >= 0.0 && from.seekCollisionAbove() && !fromOnGround;
            }
        }
        
        
        ////////////////////////////////////////////////////////////////////////////
        // Calculate the offset: check for velocity and workarounds on violations // 
        ////////////////////////////////////////////////////////////////////////////
        if (yTheoreticalDistance != null) {
            final double hiddenThreshold = clientVersion.isLowerThan(ClientVersion.V_1_18_2) ? Magic.Minecraft_minMoveSqDistance_legacy : Magic.Minecraft_minMoveSqDist_modern;
            if (thisMove.hDistance < hiddenThreshold) {
                for (int i = 0; i < yTheoreticalDistance.length; i++) {
                    if (yTheoreticalDistance[i] < hiddenThreshold && !collideLiquidY[i]) {
                        thisMove.hiddenYDistanceIndex = i;
                        break;
                    }
                }
            }
            boolean found = false;
            for (int i = 0; i < yTheoreticalDistance.length; i++) {
                if (MathUtil.isOffsetWithinPredictionEpsilon(thisMove.yDistance, yTheoreticalDistance[i])) {
                    thisMove.yAllowedDistance = yTheoreticalDistance[i];
                    thisMove.collideY = collideLiquidY[i];
                    found = true;
                    break;
                }
            }
            if (!found && thisMove.hiddenYDistanceIndex != -1) {
                final double result[] = HiddenMotionReconstructor.findBestHiddenTickExplanation(yTheoreticalDistance[thisMove.hiddenYDistanceIndex], player, data, cData, pData, from, to, attributeAccess.getHandle().getJumpGainMultiplier(player), 
                                                                                                verticalLiquidPushComponent, onGround, yTheoreticalDistance[thisMove.hiddenYDistanceIndex]);
                yTheoreticalDistance[thisMove.hiddenYDistanceIndex] += result[0];
                if (MathUtil.isOffsetWithinPredictionEpsilon(thisMove.yDistance, yTheoreticalDistance[thisMove.hiddenYDistanceIndex])) {
                    thisMove.yAllowedDistance = yTheoreticalDistance[thisMove.hiddenYDistanceIndex];
                    thisMove.collideY = collideLiquidY[thisMove.hiddenYDistanceIndex];
                    thisMove.yCorrectedDistancePre = result[0];
                    found = true;
                    tags.add("v_hidden");
                }
            }
        }
        /* Expected difference from current to allowed */
        final double offset = thisMove.yDistance - thisMove.yAllowedDistance;
        if (Math.abs(offset) < Magic.PREDICTION_EPSILON) {
            // Accuracy margin.
        }
        else {
            // Check for workarounds at the end and override the prediction if needed (just allow the movement in this case.)
            if (MagicWorkarounds.checkPostPredictWorkaround(data, fromOnGround, toOnGround, from, to, thisMove.yAllowedDistance, player, isNormalOrPacketSplitMove)) {
                thisMove.yAllowedDistance = thisMove.yDistance;
                if (debug) {
                    player.sendMessage("[SurvivalFly] Workaround ID used: " + (!justUsedWorkarounds.isEmpty() ? StringUtil.join(justUsedWorkarounds, " , ") : ""));
                }
            }
            else if (data.getOrUseVerticalVelocity(yDistance).isEmpty()) {
                // If velocity can be used for compensation, use it.
                yDistanceAboveLimit = Math.max(yDistanceAboveLimit, Math.abs(offset));
                tags.add("vdistrel");
                if (debug) {
                    player.sendMessage(ChatColor.RED + "[SurvivalFly] vdistrel: predicted=" + StringUtil.fdec6.format(thisMove.yAllowedDistance) + ", actual=" + StringUtil.fdec6.format(thisMove.yDistance) + ", offset=" + StringUtil.fdec6.format(offset));
                }
            }
        }
        return new double[]{thisMove.yAllowedDistance, yDistanceAboveLimit};
    }

    private boolean isBedrockLanternCollisionMove(final Player player, final IPlayerData pData,
                                                  final PlayerLocation from, final PlayerLocation to,
                                                  final PlayerMoveData thisMove) {
        return isBedrockPlayer(player, pData)
            && thisMove.hDistance <= 0.35
            && thisMove.yDistance >= -0.125
            && thisMove.yDistance <= 0.625
            && (MaterialUtil.LANTERNS.contains(from.getBlockType())
                || MaterialUtil.LANTERNS.contains(to.getBlockType()));
    }

    private double getLastYGroundRipTide(PlayerLocation from, PlayerMoveData lastMove, MovingData data, CombinedData cData) {
        double tmp = 0.0;
        if (lastMove.tridentRelease == AlmostBoolean.MAYBE) {
            if (lastMove.from.inWater) {
                return from.getFluidFallingAdjustedMovement(data.lastGravity, true, new Vector(0.0, 0.0, 0.0), false).getY();
            }
            else if (lastMove.from.inLava) {
                // Lava friction is quite odd. Depending on specified thresholds, it can be 0.5 or 0.8
                if (data.lastFrictionVertical != Magic.LAVA_VERTICAL_INERTIA) {
                    tmp = from.getFluidFallingAdjustedMovement(data.lastGravity, true, new Vector(0.0, 0.0, 0.0), false).getY();
                }
                else {
                    // Otherwise, 0.5
                    tmp *= data.lastFrictionVertical;
                }
                if (data.lastGravity != 0.0) {
                    tmp += -data.lastGravity / 4.0;
                }
            }
            else {
                // Air motion
                if (cData.wasLevitating) {
                    // Levitation forces players to ascend and does not work in liquids, so thankfully we don't have to account for that, other than stuck-speed.
                    tmp += (0.05 * data.lastLevitationLevel - lastMove.yAllowedDistance) * 0.2;
                }
                else tmp -= data.lastGravity;
                tmp *= data.lastFrictionVertical;
            }
        }
        return tmp;
    }


    /**
     * After-horizontal-failure checks.
     *
     * @return hAllowedDistance, hDistanceAboveLimit, hFreedom
     */
    private double[] hDistAfterFailure(final Player player,
                                       final PlayerLocation from, final PlayerLocation to,
                                       double hAllowedDistance, double hDistanceAboveLimit,
                                       final PlayerMoveData thisMove, final PlayerMoveData lastMove, final boolean debug,
                                       final MovingData data, final MovingConfig cc, final IPlayerData pData, final int tick,
                                       boolean useBlockChangeTracker, final boolean fromOnGround, final boolean toOnGround,
                                       final boolean isNormalOrPacketSplitMove) {
        /*
         * 0: If we got a speed violation and the player is using an item, assume it to be a "noslowdown" violation.
         */
        if (cc.survivalFlyResetItem && BridgeMisc.isSlowedDownByUsingAnItem(player) && !Bridge1_9.isGliding(player)) {
            // Forcibly release the item in use.
            pData.requestItemUseResync();
            tags.add("itemresync");
            if (!BridgeMisc.isSlowedDownByUsingAnItem(player) && hDistanceAboveLimit > 0.0) {
                // Re-estimate with released item (if it still throws a VL, the player is actually cheating, if the item is still in use, then it wasn't desync'ed).
                double[] res = prepareSpeedEstimation(from, to, pData, player, data, thisMove, lastMove, fromOnGround, toOnGround, debug, isNormalOrPacketSplitMove, false, false);
                hAllowedDistance = res[0];
                hDistanceAboveLimit = res[1];
            }
        }
        /*
         *  Because this move is not sent by the client and cannot be predicted through normal means, we have to brute force it.
         */
        if (hDistanceAboveLimit > 0.0 && lastMove.possibleStopMotion) {
            lastMove.possibleStopMotion = false;
            tags.add("stop_motion");
            double[] res = prepareSpeedEstimation(from, to, pData, player, data, thisMove, lastMove, fromOnGround, toOnGround, debug, isNormalOrPacketSplitMove, false, false);
            hAllowedDistance = res[0];
            hDistanceAboveLimit = res[1];
        }
        /*
         * 2: Undetectable jump (must brute force here): player failed with the onGround flag, lets try with off-ground then.
         */
        if (PhysicsEnvelope.isVerticallyConstricted(from, to, pData) && hDistanceAboveLimit > 0.0) {
            tags.add("vert_constricted");
            double[] res = prepareSpeedEstimation(from, to, pData, player, data, thisMove, lastMove, fromOnGround, toOnGround, debug, isNormalOrPacketSplitMove, false, true);
            hAllowedDistance = res[0];
            hDistanceAboveLimit = res[1];
        }
        /*
         * 3: Above limit again? Check for past onGround states caused by block changes (i.e.: ground was pulled off from the player's feet)
         */
        if (useBlockChangeTracker && hDistanceAboveLimit > 0.0) {
            // Be sure to test this only if the player is seemingly off ground
            if (!thisMove.fromLostGround && !from.isOnGround() && from.isOnGroundOpportune(cc.yOnGround, 0L, blockChangeTracker, data.blockChangeRef, tick)) {
                tags.add("onground_tracked");
                double[] res = prepareSpeedEstimation(from, to, pData, player, data, thisMove, lastMove, fromOnGround, toOnGround, debug, isNormalOrPacketSplitMove, true, false);
                hAllowedDistance = res[0];
                hDistanceAboveLimit = res[1];
            }
        }
        /* 
         * 4: Distance is still above limit; last resort: check if the distance above limit can be covered with velocity
         */
        // TODO: Implement Asofold's fix to prevent too easy abuse:
        // See: https://github.com/NoCheatPlus/Issues/issues/374#issuecomment-296172316
        double hFreedom = 0.0; // Horizontal velocity used.
        if (hDistanceAboveLimit > 0.0) {
            final double xDemand = thisMove.xDistance - thisMove.xAllowedDistance;
            final double zDemand = thisMove.zDistance - thisMove.zAllowedDistance;
            if (!data.useHorizontalVelocityCovering(xDemand, zDemand, cc.velocityActivationCounter).isEmpty()) {
                hFreedom = MathUtil.dist(xDemand, zDemand);
                thisMove.xAllowedDistance = thisMove.xDistance;
                thisMove.zAllowedDistance = thisMove.zDistance;
                thisMove.hAllowedDistance = thisMove.hDistance;
                tags.add("hvel");
                hDistanceAboveLimit = 0.0;
                hAllowedDistance = thisMove.hAllowedDistance;
            }
            else if (currentHorizontalVelocityCovers(player, xDemand, zDemand)) {
                hFreedom = MathUtil.dist(xDemand, zDemand);
                thisMove.xAllowedDistance = thisMove.xDistance;
                thisMove.zAllowedDistance = thisMove.zDistance;
                thisMove.hAllowedDistance = thisMove.hDistance;
                tags.add("hvel_current");
                hDistanceAboveLimit = 0.0;
                hAllowedDistance = thisMove.hAllowedDistance;
            }
        }
        return new double[]{hAllowedDistance, hDistanceAboveLimit, hFreedom};
    }

    private boolean currentHorizontalVelocityCovers(final Player player, final double xDemand, final double zDemand) {
        final double demandSq = xDemand * xDemand + zDemand * zDemand;
        if (demandSq <= Magic.PREDICTION_EPSILON * Magic.PREDICTION_EPSILON) {
            return false;
        }
        final Vector velocity = player.getVelocity();
        final double vx = velocity.getX();
        final double vz = velocity.getZ();
        final double velocitySq = vx * vx + vz * vz;
        if (velocitySq <= Magic.PREDICTION_EPSILON * Magic.PREDICTION_EPSILON) {
            return false;
        }
        final double dot = vx * xDemand + vz * zDemand;
        if (dot < -Magic.PREDICTION_EPSILON) {
            return false;
        }
        final double demand = Math.sqrt(demandSq);
        final double velocityAmount = Math.sqrt(velocitySq);
        if (demand > velocityAmount + CURRENT_VELOCITY_AMOUNT_GRACE) {
            return false;
        }
        final double perpendicular = Math.abs(vx * zDemand - vz * xDemand) / velocityAmount;
        return perpendicular <= CURRENT_VELOCITY_PERPENDICULAR_GRACE;
    }

    private boolean currentGlidingVerticalVelocityMatches(final Player player, final PlayerMoveData thisMove,
                                                          final double yAllowedDistance,
                                                          final double yDistanceAboveLimit) {
        if (yDistanceAboveLimit > GLIDING_CURRENT_VELOCITY_VERTICAL_OVER_GRACE) {
            return false;
        }
        final Vector velocity = player.getVelocity();
        final double actualVelocityDiff = Math.abs(thisMove.yDistance - velocity.getY());
        if (actualVelocityDiff <= GLIDING_CURRENT_VELOCITY_VERTICAL_MATCH_GRACE) {
            return true;
        }
        final double modelVelocityDiff = Math.abs(yAllowedDistance - velocity.getY());
        return actualVelocityDiff <= GLIDING_CURRENT_VELOCITY_VERTICAL_MODEL_DIFF_GRACE
                && actualVelocityDiff + GLIDING_CURRENT_VELOCITY_BETTER_MODEL_GRACE < modelVelocityDiff;
    }

    private boolean currentGlidingVerticalVelocityEnvelopeCovers(final Player player,
                                                                final PlayerMoveData thisMove,
                                                                final double yAllowedDistance,
                                                                final double yDistanceAboveLimit) {
        if (yDistanceAboveLimit > GLIDING_CURRENT_VELOCITY_VERTICAL_OVER_GRACE
                || thisMove.yDistance + Magic.PREDICTION_EPSILON < yAllowedDistance) {
            return false;
        }
        final double velocityY = player.getVelocity().getY();
        return velocityY > yAllowedDistance + Magic.PREDICTION_EPSILON
                && thisMove.yDistance <= velocityY + GLIDING_CURRENT_VELOCITY_VERTICAL_MATCH_GRACE;
    }

    private boolean currentVelocityBudgetsNoFireworkAscent(final Player player,
                                                           final PlayerMoveData thisMove,
                                                           final double energyLimit) {
        final Vector velocity = player.getVelocity();
        final double velocityY = velocity.getY();
        final double velocityH = MathUtil.dist(velocity.getX(), velocity.getZ());
        return velocityY > energyLimit + Magic.PREDICTION_EPSILON
                && thisMove.yDistance <= velocityY + GLIDING_CURRENT_VELOCITY_VERTICAL_MATCH_GRACE
                && Math.max(thisMove.hDistance, velocityH) >= GLIDING_NO_FIREWORK_CURRENT_VELOCITY_MIN_HORIZONTAL;
    }

    private double getNoFireworkDownwardVelocityAscentDebt(final Player player,
                                                          final PlayerMoveData thisMove,
                                                          final double energyLimit) {
        final double velocityY = player.getVelocity().getY();
        final boolean lowEnergyAscent = thisMove.yDistance > Magic.PREDICTION_EPSILON
                && velocityY <= GLIDING_NO_FIREWORK_DOWNWARD_VELOCITY_Y
                && (thisMove.hDistance < GLIDING_NO_FIREWORK_ASCENT_SPEED_START
                    || energyLimit <= GLIDING_NO_FIREWORK_ASCENT_RESIDUAL + GLIDING_VERTICAL_PRECISION_GRACE);
        if (!lowEnergyAscent) {
            return 0.0D;
        }
        /*
         * Elytra no-firework energy model: a real glide cannot climb while the
         * server velocity says the player is already moving downward, unless
         * speed/dive energy pays for it. This catches packet-shaped vertical fly
         * before the broader hover timer is the only thing responding.
         */
        return Math.min(GLIDING_NO_FIREWORK_DOWNWARD_VELOCITY_MAX_DEBT,
                Math.max(0.0D, thisMove.yDistance - velocityY - GLIDING_NO_FIREWORK_ASCENT_RESIDUAL));
    }

    private double getNoFireworkGlidingDescentBudgetOver(final Player player,
                                                         final MovingData data,
                                                         final PlayerMoveData thisMove) {
        if (data.fireworksBoostDuration > 0
                || player.getVelocity().getY() > Magic.PREDICTION_EPSILON
                || data.hoverAirTicks < GLIDING_NO_FIREWORK_DESCENT_BUDGET_MIN_TICKS) {
            return 0.0D;
        }
        final double deficit = data.hoverExpectedDrop - data.hoverActualDrop;
        final boolean flatHover = data.hoverActualDrop <= GLIDING_NO_FIREWORK_FLAT_MAX_ACTUAL_DROP
                && deficit > GLIDING_NO_FIREWORK_FLAT_DROP_DEFICIT;
        final boolean broadBudgetMiss = deficit > GLIDING_NO_FIREWORK_DESCENT_DROP_DEFICIT;
        if (!flatHover && !broadBudgetMiss) {
            return 0.0D;
        }
        addTag(flatHover
                ? "glide_no_firework_flat_hover_model_miss"
                : "glide_no_firework_descent_budget_model_miss");
        return deficit - (flatHover ? GLIDING_NO_FIREWORK_FLAT_DROP_DEFICIT
                : GLIDING_NO_FIREWORK_DESCENT_DROP_DEFICIT);
    }

    private boolean updateNoFireworkGlidingAscentEnergy(final Player player, final MovingData data,
                                                        final PlayerLocation from,
                                                        final PlayerMoveData thisMove,
                                                        final PlayerMoveData lastMove) {
        if (!isNoFireworkGlidingAscentTracked(player, data, thisMove)) {
            final boolean resetStart = shouldResetNoFireworkGlidingStart(player, data);
            resetNoFireworkGlidingAscentEnergy(data, resetStart);
            if (!resetStart && data.elytraNoFireworkStart != null) {
                /*
                 * Elytra no-firework model: correction teleports and packet
                 * resync can briefly surface as queued velocity. Preserve the
                 * original glide-start anchor through that internal noise, so
                 * a vertical-fly client cannot slowly refresh the anchor upward.
                 */
                addTag("glide_no_firework_start_anchor_preserved");
            }
            return false;
        }
        recordNoFireworkGlidingStart(data, from);
        updateNoFireworkGlidingDescentCredit(data, thisMove);
        if (thisMove.yDistance <= Magic.PREDICTION_EPSILON) {
            data.elytraNoFireworkAscentTicks = 0;
            data.elytraNoFireworkAscentDebt = Math.max(0.0D,
                    data.elytraNoFireworkAscentDebt - Math.max(0.02D, -thisMove.yDistance));
            return false;
        }
        final double energyLimit = getNoFireworkGlidingAscentEnergyLimit(data, thisMove, lastMove);
        final double velocityContradictionDebt = getNoFireworkDownwardVelocityAscentDebt(player, thisMove, energyLimit);
        final double modeledExcess = thisMove.yDistance - energyLimit;
        final double excess = Math.max(modeledExcess, velocityContradictionDebt);
        data.elytraNoFireworkAscentExcess = Math.max(0.0D, excess);
        if (excess <= 0.0D) {
            data.elytraNoFireworkAscentTicks = 0;
            data.elytraNoFireworkAscentDebt = Math.max(0.0D, data.elytraNoFireworkAscentDebt - 0.02D);
            consumeNoFireworkGlidingDescentCredit(data);
            addTag("glide_no_firework_ascent_energy_model");
            return false;
        }
        if (currentVelocityBudgetsNoFireworkAscent(player, thisMove, energyLimit)) {
            /*
             * Elytra no-firework energy model: do not treat ascent as client-created
             * if the packet is still inside Bukkit's current velocity envelope and
             * there is enough horizontal glide speed to plausibly carry that energy.
             */
            data.elytraNoFireworkAscentTicks = 0;
            data.elytraNoFireworkAscentDebt = Math.max(0.0D,
                    data.elytraNoFireworkAscentDebt - Math.max(0.04D, excess * 0.5D));
            addTag("glide_no_firework_ascent_velocity_budget_model");
            return false;
        }
        if (velocityContradictionDebt > modeledExcess) {
            addTag("glide_no_firework_downward_velocity_ascent_debt");
        }
        data.elytraNoFireworkAscentTicks++;
        data.elytraNoFireworkAscentDebt += excess;
        final boolean violation = data.elytraNoFireworkAscentTicks > GLIDING_NO_FIREWORK_ASCENT_TICK_LIMIT
                || data.elytraNoFireworkAscentDebt > GLIDING_NO_FIREWORK_ASCENT_DEBT_LIMIT;
        addTag(violation ? "glide_no_firework_ascent_energy_miss" : "glide_no_firework_ascent_energy_debt");
        return violation;
    }

    private void recordNoFireworkGlidingStart(final MovingData data, final PlayerLocation from) {
        final Location start = data.elytraNoFireworkStart;
        if (start != null && start.getWorld().equals(from.getWorld())) {
            return;
        }
        /*
         * Elytra no-firework anchor: remember where this glide began. If the
         * player later climbs above this without a firework/velocity/dive budget,
         * the correction can use the launch point instead of a moving air setback.
         */
        data.elytraNoFireworkStart = from.getLocation();
        addTag("glide_no_firework_start_anchor");
    }

    private void updateNoFireworkGlidingDescentCredit(final MovingData data,
                                                      final PlayerMoveData thisMove) {
        data.elytraNoFireworkDescentCreditUsed = 0.0D;
        if (thisMove.yDistance < -Magic.PREDICTION_EPSILON) {
            /*
             * Elytra no-firework energy model: a real glide can trade altitude
             * lost during the current glide session back into a later climb. Store
             * actual descent as energy, then spend it only through the horizontal
             * speed-gated ascent envelope below.
             */
            data.elytraNoFireworkDescentCredit = Math.min(GLIDING_NO_FIREWORK_DESCENT_CREDIT_CAP,
                    data.elytraNoFireworkDescentCredit
                            + (-thisMove.yDistance * GLIDING_NO_FIREWORK_DESCENT_CREDIT_FACTOR));
            return;
        }
        data.elytraNoFireworkDescentCredit = Math.max(0.0D,
                data.elytraNoFireworkDescentCredit - GLIDING_NO_FIREWORK_DESCENT_CREDIT_DECAY);
    }

    private boolean isNoFireworkGlidingAscentTracked(final Player player, final MovingData data,
                                                     final PlayerMoveData thisMove) {
        return Bridge1_9.isGliding(player)
                && data.fireworksBoostDuration <= 0
                && Double.isInfinite(Bridge1_9.getLevitationAmplifier(player))
                && data.timeRiptiding + 1500 <= System.currentTimeMillis()
                && !data.hasQueuedVerVel()
                && thisMove.verVelUsed.isEmpty();
    }

    private boolean shouldResetNoFireworkGlidingStart(final Player player, final MovingData data) {
        return !Bridge1_9.isGliding(player)
                || data.fireworksBoostDuration > 0
                || !Double.isInfinite(Bridge1_9.getLevitationAmplifier(player))
                || data.timeRiptiding + 1500 > System.currentTimeMillis();
    }

    private double getNoFireworkGlidingAscentEnergyLimit(final MovingData data,
                                                         final PlayerMoveData thisMove,
                                                         final PlayerMoveData lastMove) {
        if (!lastMove.toIsValid) {
            return recordNoFireworkGlidingAscentBudget(data, thisMove, GLIDING_NO_FIREWORK_ASCENT_RESIDUAL, 0.0D);
        }
        final double decayedAscent = lastMove.yDistance > 0.0D
                ? Math.max(0.0D, (lastMove.yDistance - Magic.DEFAULT_GRAVITY) * Magic.FRICTION_MEDIUM_AIR) : 0.0D;
        final double diveLift = Math.max(0.0D, -lastMove.yDistance) * GLIDING_NO_FIREWORK_ASCENT_DIVE_FACTOR;
        final double tradedSpeedLift = Math.max(0.0D, lastMove.hDistance - thisMove.hDistance)
                * GLIDING_NO_FIREWORK_ASCENT_TRADE_FACTOR;
        final double descentCreditLift = getNoFireworkGlidingDescentCreditLift(data, thisMove, lastMove);
        final double rawSpeedLift = Math.min(GLIDING_NO_FIREWORK_ASCENT_SPEED_CAP,
                Math.max(0.0D, lastMove.hDistance - GLIDING_NO_FIREWORK_ASCENT_SPEED_START)
                        * GLIDING_NO_FIREWORK_ASCENT_SPEED_FACTOR);
        final boolean hasEarnedLiftSource = diveLift > Magic.PREDICTION_EPSILON
                || tradedSpeedLift > Magic.PREDICTION_EPSILON
                || descentCreditLift > Magic.PREDICTION_EPSILON;
        final double speedLift = hasEarnedLiftSource ? rawSpeedLift : 0.0D;
        if (rawSpeedLift > 0.0D && speedLift <= 0.0D && thisMove.yDistance > GLIDING_NO_FIREWORK_ASCENT_RESIDUAL) {
            addTag("glide_no_firework_unearned_speed_lift");
        }
        /*
         * Elytra no-firework energy model: climbing must be paid for by decaying
         * previous upward momentum, a prior dive, horizontal speed actually traded
         * away, or descent credit earned earlier in the same glide. Horizontal
         * speed alone is not an energy source; otherwise a flat no-firework hover
         * can create altitude just by maintaining speed.
         */
        final double energyLimit = Math.max(decayedAscent,
                Math.max(diveLift, speedLift + tradedSpeedLift + descentCreditLift))
                + GLIDING_NO_FIREWORK_ASCENT_RESIDUAL;
        return recordNoFireworkGlidingAscentBudget(data, thisMove, energyLimit,
                tradedSpeedLift + descentCreditLift);
    }

    private double getNoFireworkGlidingDescentCreditLift(final MovingData data,
                                                         final PlayerMoveData thisMove,
                                                         final PlayerMoveData lastMove) {
        data.elytraNoFireworkDescentCreditUsed = 0.0D;
        if (data.elytraNoFireworkDescentCredit <= 0.0D) {
            return 0.0D;
        }
        final double hDistance = Math.max(thisMove.hDistance, lastMove.toIsValid ? lastMove.hDistance : 0.0D);
        if (hDistance < GLIDING_NO_FIREWORK_DESCENT_CREDIT_MIN_H) {
            return 0.0D;
        }
        final double speedScale = Math.min(1.0D, Math.max(0.0D,
                (hDistance - GLIDING_NO_FIREWORK_DESCENT_CREDIT_MIN_H)
                        / (GLIDING_NO_FIREWORK_DESCENT_CREDIT_FULL_H
                        - GLIDING_NO_FIREWORK_DESCENT_CREDIT_MIN_H)));
        final double lift = Math.min(data.elytraNoFireworkDescentCredit,
                GLIDING_NO_FIREWORK_DESCENT_CREDIT_TICK_CAP * speedScale);
        data.elytraNoFireworkDescentCreditUsed = lift;
        if (lift > 0.0D) {
            addTag("glide_no_firework_descent_credit_model");
        }
        return lift;
    }

    private void consumeNoFireworkGlidingDescentCredit(final MovingData data) {
        if (data.elytraNoFireworkDescentCreditUsed <= 0.0D) {
            return;
        }
        data.elytraNoFireworkDescentCredit = Math.max(0.0D,
                data.elytraNoFireworkDescentCredit - data.elytraNoFireworkDescentCreditUsed);
    }

    private double recordNoFireworkGlidingAscentBudget(final MovingData data, final PlayerMoveData thisMove,
                                                       final double energyLimit, final double tradedSpeedLift) {
        data.elytraNoFireworkAscentBudget = energyLimit;
        data.elytraNoFireworkAscentExcess = Math.max(0.0D, thisMove.yDistance - energyLimit);
        data.elytraNoFireworkNeededH = getNoFireworkHorizontalSpeedNeededForAscent(thisMove.yDistance, tradedSpeedLift);
        return energyLimit;
    }

    private double getNoFireworkHorizontalSpeedNeededForAscent(final double yDistance, final double tradedSpeedLift) {
        final double ascent = Math.max(0.0D, yDistance);
        final double speedLiftNeeded = Math.max(0.0D,
                ascent - GLIDING_NO_FIREWORK_ASCENT_RESIDUAL - Math.max(0.0D, tradedSpeedLift));
        if (speedLiftNeeded <= 0.0D) {
            return 0.0D;
        }
        if (speedLiftNeeded > GLIDING_NO_FIREWORK_ASCENT_SPEED_CAP) {
            return Double.POSITIVE_INFINITY;
        }
        return GLIDING_NO_FIREWORK_ASCENT_SPEED_START
                + speedLiftNeeded / GLIDING_NO_FIREWORK_ASCENT_SPEED_FACTOR;
    }

    private void resetNoFireworkGlidingAscentEnergy(final MovingData data, final boolean resetStart) {
        data.elytraNoFireworkAscentTicks = 0;
        data.elytraNoFireworkAscentDebt = 0.0D;
        data.elytraNoFireworkAscentBudget = 0.0D;
        data.elytraNoFireworkAscentExcess = 0.0D;
        data.elytraNoFireworkNeededH = Double.NaN;
        data.elytraNoFireworkDescentCredit = 0.0D;
        data.elytraNoFireworkDescentCreditUsed = 0.0D;
        if (resetStart) {
            data.elytraNoFireworkStart = null;
        }
    }

    private boolean currentGlidingHorizontalVelocityCovers(final Player player, final PlayerMoveData thisMove,
                                                           final double xAllowedDistance, final double zAllowedDistance,
                                                           final double xDemand, final double zDemand) {
        final double demandSq = xDemand * xDemand + zDemand * zDemand;
        if (demandSq <= Magic.PREDICTION_EPSILON * Magic.PREDICTION_EPSILON) {
            return false;
        }
        final Vector velocity = player.getVelocity();
        final double vx = velocity.getX();
        final double vz = velocity.getZ();
        final double velocitySq = vx * vx + vz * vz;
        if (velocitySq <= Magic.PREDICTION_EPSILON * Magic.PREDICTION_EPSILON) {
            return false;
        }
        final double actualVelocityDiff = MathUtil.dist(thisMove.xDistance - vx, thisMove.zDistance - vz);
        final double modelVelocityDiff = MathUtil.dist(xAllowedDistance - vx, zAllowedDistance - vz);
        if (actualVelocityDiff <= GLIDING_CURRENT_VELOCITY_HORIZONTAL_MODEL_DIFF_GRACE
                && actualVelocityDiff + GLIDING_CURRENT_VELOCITY_BETTER_MODEL_GRACE < modelVelocityDiff) {
            return true;
        }
        final double dot = vx * xDemand + vz * zDemand;
        if (dot < -Magic.PREDICTION_EPSILON) {
            return false;
        }
        final double demand = Math.sqrt(demandSq);
        final double velocityAmount = Math.sqrt(velocitySq);
        if (demand > velocityAmount + GLIDING_CURRENT_VELOCITY_HORIZONTAL_AMOUNT_GRACE
                || thisMove.hDistance > velocityAmount + GLIDING_CURRENT_VELOCITY_HORIZONTAL_MOVE_GRACE) {
            return false;
        }
        final double perpendicular = Math.abs(vx * zDemand - vz * xDemand) / velocityAmount;
        return perpendicular <= GLIDING_CURRENT_VELOCITY_HORIZONTAL_PERPENDICULAR_GRACE;
    }

    private void addGlidingVerticalPredictionTags(final double offsetV) {
        addTag(SurvivalFlyTags.GLIDE_VERTICAL_PREDICTION_MISS);
        addTag(offsetV > 0.0
                ? SurvivalFlyTags.GLIDE_VERTICAL_ACTUAL_ABOVE_MODEL
                : SurvivalFlyTags.GLIDE_VERTICAL_ACTUAL_BELOW_MODEL);
    }

    private void addGlidingHorizontalPredictionTags(final double offsetH) {
        addTag(SurvivalFlyTags.GLIDE_HORIZONTAL_PREDICTION_MISS);
        addTag(offsetH > 0.0
                ? SurvivalFlyTags.GLIDE_HORIZONTAL_ACTUAL_ABOVE_MODEL
                : SurvivalFlyTags.GLIDE_HORIZONTAL_ACTUAL_BELOW_MODEL);
    }

    private void addGlidingLookAndFireworkTags(final MovingData data, final PlayerLocation to,
                                               final PlayerMoveData move,
                                               final double offsetV, final double offsetH) {
        final String pitchBand = getElytraPitchBand(to.getPitch());
        addTag("glide_pitch_" + pitchBand.toLowerCase(Locale.ROOT));
        if (data.fireworksBoostDuration <= 0) {
            return;
        }
        addTag("glide_firework_pitch_" + pitchBand.toLowerCase(Locale.ROOT));
        if (offsetV < -0.05D) {
            addTag("glide_firework_vertical_model_high");
        }
        else if (offsetV > 0.05D) {
            addTag("glide_firework_vertical_model_low");
        }
        if (offsetH < -0.05D || move.hDistance + 0.05D < move.hAllowedDistance) {
            addTag("glide_firework_horizontal_model_fast");
        }
        else if (offsetH > 0.05D) {
            addTag("glide_firework_horizontal_model_slow");
        }
    }

    private String getElytraPitchBand(final float pitch) {
        if (pitch <= -15.0f) {
            return "UP_STEEP";
        }
        if (pitch <= -5.0f) {
            return "UP";
        }
        if (pitch < 5.0f) {
            return "LEVEL";
        }
        if (pitch < 15.0f) {
            return "DOWN";
        }
        return "DOWN_STEEP";
    }

    private void addTag(final String tag) {
        if (!tags.contains(tag)) {
            tags.add(tag);
        }
    }

    private void logConsoleDetails(final double result,
                                   final Player player, final PlayerLocation from, final PlayerLocation to,
                                   final Location setback,
                                   final MovingData data, final IPlayerData pData,
                                   final PlayerMoveData thisMove, final PlayerMoveData lastMove,
                                   final double hAllowedDistance, final double hDistanceAboveLimit,
                                   final double yAllowedDistance, final double yDistanceAboveLimit,
                                   final boolean fromOnGround, final boolean resetFrom,
                                   final boolean toOnGround, final boolean resetTo,
                                   final int tick, final long now, final int multiMoveCount,
                                   final boolean isNormalOrPacketSplitMove) {
        final MovementModelBranch modelBranch = selectExplicitMovementModel(player, pData, data, from, to, thisMove, lastMove);
        final String movementMode = SurvivalFlyDiagnostics.formatMovementMode(player, data, from, to, thisMove,
                fromOnGround, resetFrom, toOnGround, resetTo);
        final String subcheck = getViolationSubCheck(player, from, to, data);
        final String axis = getViolationAxis(thisMove);
        final boolean partialSupport = isPartialSupportNear(from, to);
        final double partialHorizontalLimit = partialSupport ? getPartialSupportHorizontalModelLimit(from, to, thisMove, lastMove) : 0.0D;
        final double partialVerticalLimit = partialSupport ? getPartialSupportVerticalModelLimit(from, to, thisMove) : 0.0D;
        final double partialVerticalClamp = partialSupport ? getPartialSupportVerticalClampModel(from, to, thisMove.yDistance) : 0.0D;
        final boolean jumpProbe = isJumpDiagnosticProbe(data, thisMove);
        player.getServer().getLogger().info(SurvivalFlyDiagnostics.formatDetail(player, from, to, setback,
                data, pData, thisMove, lastMove, isBedrockPlayer(player, pData),
                String.valueOf(getMovementClientVersion(pData)),
                movementMode,
                subcheck,
                SurvivalFlyDiagnostics.formatReadableDebugSummary(subcheck, movementMode, modelBranch.tag, axis,
                        hDistanceAboveLimit, yDistanceAboveLimit, tags),
                result, hAllowedDistance, hDistanceAboveLimit, yAllowedDistance, yDistanceAboveLimit,
                fromOnGround, resetFrom, toOnGround, resetTo, tick, now, multiMoveCount,
                isNormalOrPacketSplitMove,
                SurvivalFlyDiagnostics.formatCompactModelProbe(modelBranch.tag, axis,
                        hDistanceAboveLimit, yDistanceAboveLimit, lastMove.toIsValid, player.getVelocity(),
                        data.getHorizontalVelocityTracker().hasQueued(), !thisMove.verVelUsed.isEmpty(),
                        partialSupport, partialSupport ? getPartialSupportTypeTag(from, to) : "none",
                        partialHorizontalLimit, partialVerticalLimit, partialVerticalClamp,
                        jumpProbe, thisMove.isJump, thisMove.isStepUp, thisMove.couldStepUp, data.sfJumpPhase),
                SurvivalFlyDiagnostics.formatElytraModel(player, from, to, data, thisMove,
                        hAllowedDistance, yAllowedDistance, getElytraPitchBand(to.getPitch()),
                        getYawDelta(from.getYaw(), to.getYaw())),
                StringUtil.join(tags, "+")));
    }

    private boolean isJumpDiagnosticProbe(final MovingData data, final PlayerMoveData move) {
        return move.isJump || move.couldStepUp || move.isStepUp || data.sfJumpPhase > 0 || tags.contains("jump_env");
    }

    private boolean isVelocityDiagnosticProbe(final MovingData data, final PlayerMoveData move) {
        return data.getHorizontalVelocityTracker().hasQueued()
                || !move.verVelUsed.isEmpty()
                || tags.contains("hvel_current") || tags.contains("hvel");
    }

    private double getYawDelta(final float fromYaw, final float toYaw) {
        double delta = toYaw - fromYaw;
        while (delta > 180.0D) {
            delta -= 360.0D;
        }
        while (delta < -180.0D) {
            delta += 360.0D;
        }
        return delta;
    }

    /**
     * Handles a violation for Survivalfly.
     * 
     * @return The Location where the player will be set backed to.
     */
    private Location handleViolation(final double result,
                                     final Player player, final PlayerLocation from, final PlayerLocation to,
                                     final MovingData data, final MovingConfig cc) {
        // Increment violation level.
        addViolationModeTag(player, from, to, data.playerMoves.getCurrentMove(), data);
        addViolationDiagnosticTags(player, from, to, data);
        if (shouldUseNoFireworkElytraDownwardSetBack(player, data)) {
            addTag(shouldUseNoFireworkElytraStartSetBack(to, data)
                    ? "glide_no_firework_start_setback"
                    : "glide_no_firework_downward_setback");
        }
        data.survivalFlyVL += result;
        data.sfVLMoveCount = data.getPlayerMoveCount();
        final ViolationData vd = new ViolationData(this, player, data.survivalFlyVL, result, cc.survivalFlyActions);
        if (vd.needsParameters()) {
            vd.setParameter(ParameterName.LOCATION_FROM, String.format(Locale.US, "%.2f, %.2f, %.2f", from.getX(), from.getY(), from.getZ()));
            vd.setParameter(ParameterName.LOCATION_TO, String.format(Locale.US, "%.2f, %.2f, %.2f", to.getX(), to.getY(), to.getZ()));
            vd.setParameter(ParameterName.DISTANCE, String.format(Locale.US, "%.2f", TrigUtil.distance(from, to)));
            vd.setParameter(ParameterName.TAGS, StringUtil.join(tags, "+"));
        }
        // Some resetting is done in MovingListener.
        if (executeActions(vd).willCancel()) {
            // Set back + view direction of to (more smooth).
            final Location fallback = MovingUtil.getApplicableSetBackLocation(player, to.getYaw(), to.getPitch(), to, data, cc);
            return getNoFireworkElytraSetBackLocation(player, to, to.getYaw(), to.getPitch(), data, fallback);
        }
        else {
            data.sfJumpPhase = 0;
            // Cancelled by other plugin, or no cancel set by configuration.
            return null;
        }
    }

    private Location getNoFireworkElytraSetBackLocation(final Player player, final PlayerLocation ref,
                                                        final float refYaw, final float refPitch,
                                                        final MovingData data, final Location fallback) {
        if (!shouldUseNoFireworkElytraDownwardSetBack(player, data)) {
            return fallback;
        }
        final Location startCorrection = getNoFireworkElytraStartSetBackLocation(ref, refYaw, refPitch, data);
        if (startCorrection != null) {
            return startCorrection;
        }
        final double drop = getNoFireworkElytraSetBackDrop(data);
        final Vector allowedDrop = ref.collide(new Vector(0.0D, -drop, 0.0D), false, ref.getBoundingBox());
        if (allowedDrop.getY() >= -Magic.PREDICTION_EPSILON) {
            return fallback;
        }
        final Location correction = ref.getLocation();
        correction.setYaw(refYaw);
        correction.setPitch(refPitch);
        correction.setY(ref.getY() + allowedDrop.getY());
        if (fallback != null && fallback.getWorld().equals(correction.getWorld())
                && fallback.getY() < correction.getY()) {
            return fallback;
        }
        /*
         * Elytra no-firework correction model: ordinary SurvivalFly setbacks are
         * updated during accepted gliding, so a hover cheat can otherwise be
         * cancelled back to an air checkpoint. For no-energy ascent/hover, choose
         * a collision-aware downward correction so repeated violations force real
         * descent instead of preserving the illegal altitude.
         */
        addTag("glide_no_firework_downward_setback");
        return correction;
    }

    private Location getNoFireworkElytraStartSetBackLocation(final PlayerLocation ref,
                                                             final float refYaw, final float refPitch,
                                                             final MovingData data) {
        if (!shouldUseNoFireworkElytraStartSetBack(ref, data)) {
            return null;
        }
        final Location start = data.elytraNoFireworkStart;
        /*
         * Elytra no-firework correction model: if the player climbed above the
         * point where the no-firework glide started, snap back to that anchor.
         * Hovering at roughly the same height still uses the downward correction
         * below, because the start anchor would not create descent.
         */
        final Location correction = start.clone();
        correction.setYaw(refYaw);
        correction.setPitch(refPitch);
        addTag("glide_no_firework_start_setback");
        return correction;
    }

    private boolean shouldUseNoFireworkElytraStartSetBack(final PlayerLocation ref, final MovingData data) {
        final Location start = data.elytraNoFireworkStart;
        if (start == null || !start.getWorld().equals(ref.getWorld())) {
            return false;
        }
        final double yGain = ref.getY() - start.getY();
        if (yGain < GLIDING_NO_FIREWORK_START_SETBACK_MIN_GAIN) {
            return false;
        }
        final double dx = ref.getX() - start.getX();
        final double dz = ref.getZ() - start.getZ();
        return dx * dx + dz * dz <= GLIDING_NO_FIREWORK_START_SETBACK_MAX_HORIZONTAL
                * GLIDING_NO_FIREWORK_START_SETBACK_MAX_HORIZONTAL;
    }

    private boolean shouldUseNoFireworkElytraDownwardSetBack(final Player player, final MovingData data) {
        if (!Bridge1_9.isGliding(player) || data.fireworksBoostDuration > 0) {
            return false;
        }
        final double deficit = data.hoverExpectedDrop - data.hoverActualDrop;
        return tags.contains("glide_no_firework_ascent_energy_enforced")
                || tags.contains("glide_no_firework_descent_budget_enforced")
                || tags.contains("glide_no_firework_flat_hover_model_miss")
                || tags.contains("glide_no_firework_descent_budget_model_miss")
                || deficit > GLIDING_NO_FIREWORK_FLAT_DROP_DEFICIT;
    }

    private double getNoFireworkElytraSetBackDrop(final MovingData data) {
        final double deficit = Math.max(0.0D, data.hoverExpectedDrop - data.hoverActualDrop);
        final double modelDrop = deficit * GLIDING_NO_FIREWORK_SETBACK_DEFICIT_FACTOR
                + Math.max(0.0D, data.elytraNoFireworkAscentDebt) * GLIDING_NO_FIREWORK_SETBACK_DEBT_FACTOR
                + Math.max(0.0D, data.elytraNoFireworkAscentExcess) * GLIDING_NO_FIREWORK_SETBACK_EXCESS_FACTOR;
        return Math.min(GLIDING_NO_FIREWORK_SETBACK_MAX_DROP,
                Math.max(GLIDING_NO_FIREWORK_SETBACK_MIN_DROP, modelDrop));
    }

    private void addViolationModeTag(final Player player, final PlayerLocation from, final PlayerLocation to,
                                     final PlayerMoveData move, final MovingData data) {
        final String mode = SurvivalFlyDiagnostics.formatMovementMode(player, data, from, to, move,
                from.isOnGround(), from.isResetCond(), to.isOnGround(), to.isResetCond());
        addTag("mode_" + mode.toLowerCase(Locale.ROOT));
    }

    private void addViolationDiagnosticTags(final Player player, final PlayerLocation from, final PlayerLocation to,
                                            final MovingData data) {
        // Diagnostic info: label the concrete SurvivalFly branch so false flags are not hidden by the umbrella check name.
        final PlayerMoveData move = data.playerMoves.getCurrentMove();
        final PlayerMoveData lastMove = data.playerMoves.getFirstPastMove();
        final double hOverRaw = move.hDistance - move.hAllowedDistance;
        final double yOverRaw = move.yDistance - move.yAllowedDistance;
        if (hOverRaw > Magic.PREDICTION_EPSILON && Math.abs(yOverRaw) > Magic.PREDICTION_EPSILON) {
            addTag("axis_hy");
        }
        else if (hOverRaw > Magic.PREDICTION_EPSILON) {
            addTag("axis_h");
        }
        else if (Math.abs(yOverRaw) > Magic.PREDICTION_EPSILON) {
            addTag("axis_y");
        }
        if (yOverRaw > Magic.PREDICTION_EPSILON) {
            addTag("y_above_model");
        }
        else if (yOverRaw < -Magic.PREDICTION_EPSILON) {
            addTag("y_below_model");
        }
        if (tags.contains("onground_env")) {
            addTag("branch_ground_env");
        }
        if (tags.contains("jump_env") || move.touchedGround || move.from.onGroundOrResetCond && !move.to.onGroundOrResetCond) {
            addTag("branch_jump_or_step");
        }
        if (tags.contains("v_air") || (!move.from.onGroundOrResetCond && !move.to.onGroundOrResetCond)) {
            addTag("branch_air_model");
        }
        if (from.isInLiquid() || to.isInLiquid() || move.from.inLiquid || move.to.inLiquid || tags.contains("v_water")) {
            addTag("branch_liquid");
        }
        if (move.collideX || move.collideY || move.collideZ || move.collidesHorizontally || move.negligibleHorizontalCollision) {
            addTag("branch_collision");
        }
        if (tags.contains("hvel") || tags.contains("hvel_current") || !move.verVelUsed.isEmpty()
                || data.getHorizontalVelocityTracker().hasQueued()) {
            addTag("branch_velocity");
        }
        if (!lastMove.toIsValid) {
            addTag("branch_last_invalid");
        }
        if (data.timeSinceSetBack < 20) {
            addTag("branch_recent_setback");
        }
        if (Bridge1_9.isGliding(player) || Bridge1_9.isWearingElytra(player) || data.fireworksBoostDuration > 0) {
            addTag("branch_elytra_state");
        }
        final MovementModelBranch modelBranch = selectExplicitMovementModel(player, DataManager.getPlayerData(player),
                data, from, to, move, lastMove);
        if (modelBranch != MovementModelBranch.NONE) {
            addTag("branch_model_" + modelBranch.tag);
        }
        addModelProbeDiagnosticTags(player, from, to, data, move, lastMove, modelBranch, hOverRaw, yOverRaw);
        if (!from.isPassable() || !to.isPassable()) {
            addTag("branch_inside_block");
        }
        addTag("subcheck_" + getViolationSubCheck(player, from, to, data).toLowerCase(Locale.ROOT));
    }

    private void addModelProbeDiagnosticTags(final Player player, final PlayerLocation from, final PlayerLocation to,
                                             final MovingData data, final PlayerMoveData move,
                                             final PlayerMoveData lastMove, final MovementModelBranch modelBranch,
                                             final double hOverRaw, final double yOverRaw) {
        // Diagnostic info: these tags do not grant movement; they mark the next model boundary to refine.
        if (modelBranch != MovementModelBranch.NONE) {
            addTag("diag_probe_" + modelBranch.tag);
        }
        if (!lastMove.toIsValid) {
            addTag("diag_last_invalid_probe");
            if (Bridge1_9.isWearingElytra(player) || Bridge1_9.isGliding(player)) {
                addTag("diag_last_invalid_elytra");
            }
            if (hOverRaw > Magic.PREDICTION_EPSILON && Math.abs(yOverRaw) > Magic.PREDICTION_EPSILON) {
                addTag("diag_last_invalid_hy");
            }
            if (isLastInvalidStandstillResyncModel(move)) {
                addTag("diag_last_invalid_standstill_resync_candidate");
            }
            if (isLastInvalidVelocityResyncCandidate(player, move)) {
                addTag("diag_last_invalid_velocity_resync_candidate");
            }
            if (isLastInvalidGroundInputCandidate(move)) {
                addTag("diag_last_invalid_ground_input_candidate");
            }
            if (isLastInvalidVelocityHandoffCandidate(player, move)) {
                addTag("diag_last_invalid_velocity_handoff_candidate");
            }
            if (isLastInvalidAirStallCandidate(player, move)) {
                addTag("diag_last_invalid_air_stall_candidate");
            }
            if (isLastInvalidJumpContinuationCandidate(move)) {
                addTag("diag_last_invalid_jump_continuation_candidate");
                if (isLastInvalidLowJumpContinuationCandidate(move)) {
                    addTag("diag_last_invalid_low_jump_continuation_candidate");
                }
            }
        }
        if (hasCollisionSignal(move)) {
            addTag("diag_collision_probe");
            if (isCollisionHorizontalSlideCandidate(move)) {
                addTag("diag_collision_horizontal_slide_candidate");
            }
            if (isCollisionVerticalTruncationCandidate(move)) {
                addTag("diag_collision_vertical_truncation_candidate");
            }
        }
        if (isItemResyncMovementContext(player, from, to, move)) {
            addTag("diag_itemresync_model_candidate");
        }
        if (isAirInertiaMovementContext(player, from, to, move, lastMove)) {
            addTag("diag_air_inertia_candidate");
        }
        if (isAirCurrentVelocityContext(player, from, to, move)) {
            addTag("diag_air_current_velocity_candidate");
        }
        if (isGroundLandingCarryContext(player, from, to, move, lastMove)) {
            addTag("diag_ground_landing_carry_candidate");
        }
        if (isGroundVelocityCarryContext(player, from, to, move)) {
            addTag("diag_ground_velocity_carry_candidate");
        }
        if (Bridge1_9.isWearingElytra(player) && !Bridge1_9.isGliding(player)) {
            addTag("diag_elytra_equipped_probe");
            if (isElytraEquippedTransitionContext(player, data, from, to, move, lastMove)) {
                addTag("diag_elytra_equipped_transition_probe");
            }
        }
        if (isPartialSupportNear(from, to)) {
            addTag("diag_partial_support_probe");
            final double horizontalLimit = getPartialSupportHorizontalModelLimit(from, to, move, lastMove);
            if (move.hDistance > horizontalLimit) {
                addTag("diag_partial_support_h_limit_miss");
            }
            if (move.yDistance < -Magic.PREDICTION_EPSILON) {
                final double verticalClamp = getPartialSupportVerticalClampModel(from, to, move.yDistance);
                if (verticalClamp > 0.0D) {
                    addTag("diag_partial_support_clamp_candidate");
                }
                else {
                    addTag("diag_partial_support_clamp_unit_miss");
                }
                if (getPartialSupportLandingClampFraction(to) >= 0.0D
                        && (to.isOnGroundOrResetCond() || move.to.onGroundOrResetCond
                                || move.touchedGround || move.touchedGroundWorkaround)) {
                    addTag("diag_partial_support_landing_clamp_candidate");
                }
            }
            if (isJumpDiagnosticProbe(data, move)) {
                addTag("diag_partial_support_jump_probe");
            }
        }
        if (isJumpDiagnosticProbe(data, move)) {
            addTag("diag_jump_probe");
            if (Math.abs(move.yDistance - BEDROCK_HALF_STEP_VERTICAL_MOVE) <= BEDROCK_HALF_STEP_VERTICAL_EPSILON) {
                addTag("diag_jump_half_block_candidate");
            }
            if (isModernHalfStepContext(player, DataManager.getPlayerData(player), from, to, move, lastMove)) {
                addTag("diag_modern_half_step_candidate");
            }
            if (isJumpCarryContext(player, from, to, move, lastMove)) {
                addTag("diag_jump_carry_candidate");
                if (isLowJumpCarryContext(move, lastMove)) {
                    addTag("diag_low_jump_carry_candidate");
                }
            }
            if (yOverRaw > Magic.PREDICTION_EPSILON) {
                addTag("diag_jump_y_above_model");
            }
        }
        if (isVelocityDiagnosticProbe(data, move)) {
            addTag("diag_velocity_probe");
        }
    }

    private String getViolationSubCheck(final Player player, final PlayerLocation from, final PlayerLocation to,
                                        final MovingData data) {
        // Diagnostic info: choose a human-readable subcheck for console output and action tags.
        final PlayerMoveData move = data.playerMoves.getCurrentMove();
        final String axis = getViolationAxis(move);
        final double yOverRaw = move.yDistance - move.yAllowedDistance;
        if (Bridge1_9.isGliding(player)) {
            if (data.fireworksBoostDuration > 0) {
                return "ELYTRA_FIREWORK_" + axis;
            }
            if (yOverRaw > Magic.PREDICTION_EPSILON) {
                return "ELYTRA_GLIDE_Y_ABOVE";
            }
            if (yOverRaw < -Magic.PREDICTION_EPSILON) {
                return "ELYTRA_GLIDE_Y_BELOW";
            }
            return "ELYTRA_GLIDE_" + axis;
        }
        if (from.isInWater() || to.isInWater() || move.from.inWater || move.to.inWater || tags.contains("v_water")) {
            return "WATER_" + axis;
        }
        if (from.isInLava() || to.isInLava() || move.from.inLava || move.to.inLava) {
            return "LAVA_" + axis;
        }
        if (from.isOnClimbable() || to.isOnClimbable() || move.from.onClimbable || move.to.onClimbable) {
            return "CLIMBABLE_" + axis;
        }
        if (Bridge1_9.isWearingElytra(player)) {
            if (data.fireworksBoostDuration > 0) {
                return "ELYTRA_EQUIPPED_FIREWORK_" + axis;
            }
            return "ELYTRA_EQUIPPED_" + axis;
        }
        if (from.isInWeb() || to.isInWeb() || move.from.inWeb || move.to.inWeb) {
            return "WEB_" + axis;
        }
        if (from.isInBerryBush() || to.isInBerryBush() || move.from.inBerryBush || move.to.inBerryBush) {
            return "BERRY_BUSH_" + axis;
        }
        if (from.isInPowderSnow() || to.isInPowderSnow() || move.from.inPowderSnow || move.to.inPowderSnow) {
            return "POWDER_SNOW_" + axis;
        }
        if (!from.isPassable() || !to.isPassable()) {
            return "INSIDE_BLOCK_" + axis;
        }
        if (move.collideX || move.collideY || move.collideZ || move.collidesHorizontally || move.negligibleHorizontalCollision) {
            return "COLLISION_" + axis;
        }
        if (tags.contains("hvel") || tags.contains("hvel_current") || !move.verVelUsed.isEmpty()
                || data.getHorizontalVelocityTracker().hasQueued()) {
            return "VELOCITY_" + axis;
        }
        if (data.timeSinceSetBack < 20) {
            return "SETBACK_RECOVERY_" + axis;
        }
        if (move.isStepUp || move.couldStepUp) {
            return "GROUND_STEP_" + axis;
        }
        if (move.isJump || data.sfJumpPhase > 0 || tags.contains("jump_env")) {
            return "GROUND_JUMP_" + axis;
        }
        if (from.isOnGround() || to.isOnGround() || from.isResetCond() || to.isResetCond()
                || move.from.onGroundOrResetCond || move.to.onGroundOrResetCond || tags.contains("onground_env")) {
            return "GROUND_" + axis;
        }
        return "AIR_" + axis;
    }

    private String getViolationAxis(final PlayerMoveData move) {
        // Diagnostic info: split movement failures by horizontal, vertical, or combined model mismatch.
        final boolean hOver = move.hDistance - move.hAllowedDistance > Magic.PREDICTION_EPSILON;
        final boolean yOver = Math.abs(move.yDistance - move.yAllowedDistance) > Magic.PREDICTION_EPSILON;
        if (hOver && yOver) {
            return "HY";
        }
        if (hOver) {
            return "H";
        }
        if (yOver) {
            return "Y";
        }
        return "MODEL";
    }


    /**
     * Hover violations have to be handled in this check, because they are handled as SurvivalFly violations (needs executeActions).
     */
    public final void handleHoverViolation(final Player player, final PlayerLocation loc, final MovingConfig cc, final MovingData data) {
        data.survivalFlyVL += cc.sfHoverViolation;
        // TODO: Extra options for set back / kick, like vl?
        data.sfVLMoveCount = data.getPlayerMoveCount();
        data.sfVLInAir = true;
        final ViolationData vd = new ViolationData(this, player, data.survivalFlyVL, cc.sfHoverViolation, cc.survivalFlyActions);
        if (vd.needsParameters()) {
            vd.setParameter(ParameterName.LOCATION_FROM, String.format(Locale.US, "%.2f, %.2f, %.2f", loc.getX(), loc.getY(), loc.getZ()));
            vd.setParameter(ParameterName.LOCATION_TO, "(HOVER)");
            vd.setParameter(ParameterName.DISTANCE, "0.0(HOVER)");
            // Diagnostic info: hover is a SurvivalFly action path, so tag it as its own subcheck.
            final boolean elytraHover = Bridge1_9.isGliding(player);
            final String hoverTags = elytraHover
                    ? "subcheck_hover+branch_elytra_model+hover+hover_elytra_budget_model+hover_descent_budget_model"
                            + (shouldUseNoFireworkElytraDownwardSetBack(player, data)
                                    ? (shouldUseNoFireworkElytraStartSetBack(loc, data)
                                            ? "+glide_no_firework_start_setback"
                                            : "+glide_no_firework_downward_setback")
                                    : "")
                    : "subcheck_hover+branch_air_model+hover+hover_air_stall_model+hover_descent_budget_model";
            vd.setParameter(ParameterName.TAGS, hoverTags);
        }
        if (executeActions(vd).willCancel()) {
            // Set back or kick.
            final Location fallback = MovingUtil.getApplicableSetBackLocation(player, loc.getYaw(), loc.getPitch(), loc, data, cc);
            final Location newTo = getNoFireworkElytraSetBackLocation(player, loc, loc.getYaw(), loc.getPitch(), data, fallback);
            if (newTo != null) {
                data.prepareSetBack(newTo);
                SchedulerHelper.teleportEntity(player, newTo, BridgeMisc.TELEPORT_CAUSE_CORRECTION_OF_POSITION);
            }
            else {
                // Solve by extra actions ? Special case (probably never happens)?
                player.kickPlayer("Hovering?");
            }
        }
        else {
            // Ignore.
        }
    }


    /**
     * Debug output.
     */
    private void outputDebug(final Player player, final PlayerLocation to, final PlayerLocation from,
                             final MovingData data,
                             final double hDistance, final double hAllowedDistance, final double hFreedom,
                             final double yDistance, final double yAllowedDistance,
                             final boolean fromOnGround, final boolean resetFrom,
                             final boolean toOnGround, final boolean resetTo,
                             final PlayerMoveData thisMove) {

        // TODO: Show player name once (!)
        final PlayerMoveData lastMove = data.playerMoves.getFirstPastMove();
        final double yDistDiffEx = yDistance - yAllowedDistance;
        final double hDistDiffEx = thisMove.hDistance - thisMove.hAllowedDistance;
        final StringBuilder builder = new StringBuilder(500);
        builder.append(CheckUtils.getLogMessagePrefix(player, type));
        final String hVelUsed = hFreedom > 0 ? " / hVelUsed: " + StringUtil.fdec3.format(hFreedom) : "";
        builder.append("\nOnGround: " + (thisMove.headObstructed ? "(head obstr.) " : from.isSlidingDown() ? "(sliding down) " : "") + (thisMove.touchedGroundWorkaround ? "(lost ground) " : "") + (fromOnGround ? "onground -> " : (resetFrom ? "resetcond -> " : "--- -> ")) + (toOnGround ? "onground" : (resetTo ? "resetcond" : "---")) + ", jumpPhase: " + data.sfJumpPhase + ", LiftOff: " + data.liftOffEnvelope.name());
        final String dHDist = lastMove.toIsValid ? "(" + StringUtil.formatDiff(hDistance, lastMove.hDistance) + ")" : "";
        final String dYDist = lastMove.toIsValid ? "(" + StringUtil.formatDiff(yDistance, lastMove.yDistance)+ ")" : "";
        builder.append("\n" + " hDist: " + StringUtil.fdec6.format(hDistance) + dHDist + " / offset: " + hDistDiffEx + " / predicted: " + StringUtil.fdec6.format(hAllowedDistance) + hVelUsed +
                "\n" + " vDist: " + StringUtil.fdec6.format(yDistance) + dYDist + " / offset: " + yDistDiffEx + " / predicted: " + StringUtil.fdec6.format(yAllowedDistance) + " , setBackY: " + (data.hasSetBack() ? (data.getSetBackY() + " (jump height: " + StringUtil.fdec3.format(to.getY() - data.getSetBackY()) + " / max jump height: " + data.liftOffEnvelope.getMaxJumpHeight(data.jumpAmplifier) + ")") : "?"));
        if (lastMove.toIsValid) {
            builder.append("\n fdsq: " + StringUtil.fdec3.format(thisMove.distanceSquared / lastMove.distanceSquared));
        }
        if (!lastMove.toIsValid) {
            builder.append("\n Invalid last move (data reset)");
        }
        if (!lastMove.valid) {
            builder.append("\n Invalid last move (missing data)");
        }
        if (!thisMove.verVelUsed.isEmpty()) {
            builder.append(" , vVelUsed: " + thisMove.verVelUsed + " ");
        }
        data.addVerticalVelocity(builder);
        data.addHorizontalVelocity(builder);
        if (player.isSleeping()) {
            tags.add("sleeping");
        }
        if (Bridge1_9.isWearingElytra(player)) {
            // Just wearing (not isGliding).
            tags.add("elytra_off");
        }
        if (!tags.isEmpty()) {
            builder.append("\n" + " Tags: " + StringUtil.join(tags, "+"));
        }
        if (!justUsedWorkarounds.isEmpty()) {
            builder.append("\n" + " Workaround ID: " + StringUtil.join(justUsedWorkarounds, " , "));
        }
        builder.append("\n");
        NCPAPIProvider.getNoCheatPlusAPI().getLogManager().debug(Streams.TRACE_FILE, builder.toString());
    }


    private void logPostViolationTags(final Player player) {
        debug(player, "SurvivalFly Post violation handling tag update:\n" + StringUtil.join(tags, "+"));
    }
}
