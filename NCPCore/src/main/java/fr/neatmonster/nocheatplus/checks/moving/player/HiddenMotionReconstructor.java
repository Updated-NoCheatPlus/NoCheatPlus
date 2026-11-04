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

import java.util.Collection;

import org.bukkit.entity.Player;
import org.bukkit.util.Vector;

import fr.neatmonster.nocheatplus.checks.combined.CombinedData;
import fr.neatmonster.nocheatplus.checks.moving.MovingData;
import fr.neatmonster.nocheatplus.checks.moving.model.PlayerKeyboardInput;
import fr.neatmonster.nocheatplus.checks.moving.model.PlayerMoveData;
import fr.neatmonster.nocheatplus.checks.moving.player.order.AxisScheduler;
import fr.neatmonster.nocheatplus.checks.moving.player.order.MotionPhaseId;
import fr.neatmonster.nocheatplus.checks.moving.player.order.MotionSchedule;
import fr.neatmonster.nocheatplus.checks.moving.player.order.MotionSchedule.JumpVertical;
import fr.neatmonster.nocheatplus.checks.moving.player.order.MotionSchedule.LiquidModel;
import fr.neatmonster.nocheatplus.compat.Bridge1_13;
import fr.neatmonster.nocheatplus.compat.BridgeMisc;
import fr.neatmonster.nocheatplus.compat.versions.ClientVersion;
import fr.neatmonster.nocheatplus.players.IPlayerData;
import fr.neatmonster.nocheatplus.utilities.location.PlayerLocation;
import fr.neatmonster.nocheatplus.utilities.map.BlockFlags;
import fr.neatmonster.nocheatplus.utilities.math.MathUtil;
import fr.neatmonster.nocheatplus.utilities.math.TrigUtil;
import fr.neatmonster.nocheatplus.utilities.moving.Magic;

/**
 * A class to deal with the client's movement threshold values for sending flying packets to the server 
 * (0.03 or 0.0002 in 1.18.2. See {@link Magic#Minecraft_minMoveSqDistance_legacy}).
 * <p>
 * Clients don't send flying packets to the server when the positional delta falls below a certain threshold. 
 * Because the server never receives these movements, several ticks of accumulated motion can appear as a single jump between positions, causing false positives; these 
 * moves are, in a sense, hidden to the server.
 * </p>
 * <p>
 * To fix this, this class attempts to reconstruct the
 * hidden intermediate ticks by brute-forcing all nine plausible WASD keyboard states across up to {@link #MAX_HIDDEN_TICK_DEPTH} simulated
 * ticks. Each candidate path re-runs the client-side horizontal physics pipeline
 * (inertia, liquid push, etc...) and the closest match to the observed final position is returned.
 * Each simulated tick walks {@link fr.neatmonster.nocheatplus.checks.moving.player.order.MotionSchedule#forVersion}
 * (1.12.2 and below, 1.13 through 1.21.1, 1.21.2 and above), the same list
 * {@link fr.neatmonster.nocheatplus.checks.moving.player.SurvivalFly} uses. From 1.21.2 the input is known, so the nine-state loop is not the schedule.
 * </p>
 *
 */
public class HiddenMotionReconstructor {
    
    /**
     * Maximum number of hidden ticks this reconstructor will simulate.
     * <p>
     * A depth of 2 means up to two consecutive suppressed ticks (i.e., 9^2 = 81 candidate
     * paths in the worst case) will be explored before the search gives up. Increasing this
     * value raises accuracy for longer suppression windows at an exponential cost in CPU time.
     * </p>
     */
    private static final int MAX_HIDDEN_TICK_DEPTH = 2;
    
    /**
     * Squared position error below which a reconstructed path is considered a match.
     * <p>
     * Using the squared distance avoids a {@link Math#sqrt} call in the hot loop.
     * Corresponds to a linear tolerance of 0.001 blocks.
     * </p>
     */
    private static final double ACCEPTABLE_ERROR_SQUARED = 1.0E-6;
    
    
    
    /////////////////////////////
    // Private helpers
    /////////////////////////////
    /**
     * Generates the nine candidate {@link PlayerKeyboardInput} states that represent every
     * combination of strafe ∈ {-1, 0, +1} and forward ∈ {-1, 0, +1} a player could have
     * pressed during a hidden tick.
     *
     * <p>Each value is pre-scaled by {@code 0.98f} to match the client's analog
     * input normalisation before movement-speed multiplication. Crouching and item-use
     * multipliers are folded in immediately so that each candidate already reflects the
     * correct effective input strength for the player's current state.</p>
     *
     * @param crouching      whether the player is currently sneaking
     * @param sneakingFactor the speed multiplier applied while sneaking (< 1.0)
     * @param usingItem      whether the player is currently using an item (e.g. bow draw)
     * @return an array of exactly 9 {@link PlayerKeyboardInput} candidates
     */
    private static PlayerKeyboardInput[] generateWASDCandidates(final boolean crouching, final double sneakingFactor, final boolean usingItem) {
        PlayerKeyboardInput[] arr = new PlayerKeyboardInput[9];
        int i = 0;
        for (int strafe = -1; strafe <= 1; strafe++) {
            for (int forward = -1; forward <= 1; forward++) {
                arr[i] = new PlayerKeyboardInput(strafe * 0.98f, forward * 0.98f);
                if (crouching) arr[i].operationToInt(sneakingFactor, sneakingFactor, 1);
                if (usingItem) arr[i].operationToInt(Magic.USING_ITEM_MULTIPLIER, Magic.USING_ITEM_MULTIPLIER, 1);
                i++;
            }
        }
        return arr;
    }
    
    /**
     * Zeroes out any velocity component whose magnitude falls below the client's
     * "negligible momentum" threshold.
     *
     * <p>The threshold differs between legacy (pre-1.9) and modern clients, so the
     * client version stored in {@code pData} is consulted.</p>
     *
     * @param pData     player data providing the client version
     * @param xDistance current X velocity component
     * @param zDistance current Z velocity component
     * @return a two-element array {@code [clampedX, clampedZ]} with sub-threshold
     *         components replaced by {@code 0.0}
     */
    private static double[] doNegligibleMomentum(IPlayerData pData, double xDistance, double zDistance) {
        double resultX = xDistance;
        double resultZ = zDistance;
        
        if (pData.getClientVersion().isAtLeast(ClientVersion.V_1_9)) {
            if (Math.abs(resultX) < Magic.NEGLIGIBLE_SPEED_THRESHOLD) {
                resultX = 0.0;
            }
            if (Math.abs(resultZ) < Magic.NEGLIGIBLE_SPEED_THRESHOLD) {
                resultZ = 0.0;
            }
        }
        else {
            if (Math.abs(resultX) < Magic.NEGLIGIBLE_SPEED_THRESHOLD_LEGACY) {
                resultX = 0.0;
            }
            if (Math.abs(resultZ) < Magic.NEGLIGIBLE_SPEED_THRESHOLD_LEGACY) {
                resultZ = 0.0;
            }
        }
        return new double[] {resultX, resultZ};
    }
    
    private static double doNegligibleMomentumVertical(IPlayerData pData, double yDistance) {
        double resultY = yDistance;
        
        if (pData.getClientVersion().isAtLeast(ClientVersion.V_1_9)) {
            if (Math.abs(resultY) < Magic.NEGLIGIBLE_SPEED_THRESHOLD) {
                resultY = 0.0;
            }
        }
        else {
            if (Math.abs(resultY) < Magic.NEGLIGIBLE_SPEED_THRESHOLD_LEGACY) {
                resultY = 0.0;
            }
        }
        return resultY;
    }

    /** Slime step and honey slide. Shared by the hidden horizontal paths. */
    private static double[] scaleSlimeAndHoney(final PlayerLocation from, final PlayerMoveData lastMove, final PlayerMoveData thisMove,
            final IPlayerData pData, final double baseX, final double baseZ) {
        double x = baseX;
        double z = baseZ;
        if (from.isOnSlimeBlock() && thisMove.hasClientFromOnGround && thisMove.clientFromOnGround && Math.abs(lastMove.motionY) < 0.1 && !pData.isShiftKeyPressed()) {
            final double slimeMul = 0.4 + Math.abs(lastMove.motionY) * 0.2;
            x *= slimeMul;
            z *= slimeMul;
        }
        if (from.isSlidingDown() && lastMove.yDistance < -Magic.SLIDE_START_AT_VERTICAL_MOTION_THRESHOLD) {
            x *= -Magic.SLIDE_SPEED_THROTTLE / lastMove.yDistance;
            z *= -Magic.SLIDE_SPEED_THROTTLE / lastMove.yDistance;
        }
        return new double[] { x, z };
    }

    private static double zeroStuckHorizontal(final MovingData data, final double component) {
        if (data.lastStuckInBlockHorizontal != 1.0
                && TrigUtil.lengthSquared(data.lastStuckInBlockHorizontal, data.lastStuckInBlockVertical, data.lastStuckInBlockHorizontal) > 1.0E-7) {
            return 0.0;
        }
        return component;
    }

    /**
     * Horizontal phases the hidden-tick search shares with {@link SurvivalFly}.
     * Input, edge, and collide stay in the candidate loop.
     */
    private static void applyHiddenHorizontalPhase(final MotionPhaseId id, final double[] hz, final PlayerLocation from, final PlayerMoveData lastMove,
            final PlayerMoveData thisMove, final MovingData data, final IPlayerData pData) {
        switch (id) {
        case SLIME_STEP:
            if (from.isOnSlimeBlock() && thisMove.hasClientFromOnGround && thisMove.clientFromOnGround && Math.abs(lastMove.motionY) < 0.1 && !pData.isShiftKeyPressed()) {
                final double slimeMul = 0.4 + Math.abs(lastMove.motionY) * 0.2;
                hz[0] *= slimeMul;
                hz[1] *= slimeMul;
            }
            break;
        case INSIDE_BLOCKS:
            if (from.isSlidingDown() && lastMove.yDistance < -Magic.SLIDE_START_AT_VERTICAL_MOTION_THRESHOLD) {
                hz[0] *= -Magic.SLIDE_SPEED_THROTTLE / lastMove.yDistance;
                hz[1] *= -Magic.SLIDE_SPEED_THROTTLE / lastMove.yDistance;
            }
            break;
        case BLOCK_SPEED:
            hz[0] *= (double) data.nextBlockSpeedMultiplier;
            hz[1] *= (double) data.nextBlockSpeedMultiplier;
            break;
        case MEDIUM_GRAVITY_FRICTION:
            hz[0] *= (double) data.lastInertia;
            hz[1] *= (double) data.lastInertia;
            break;
        case FLUID_CURRENT:
            if (from.isInLiquid()) {
                Vector liquidFlowVector = from.getLiquidPushingVector(hz[0], hz[1], from.isInWater() ? BlockFlags.F_WATER : BlockFlags.F_LAVA);
                hz[0] += liquidFlowVector.getX();
                hz[1] += liquidFlowVector.getZ();
            }
            break;
        case NEGLIGIBLE_SPEED: {
            final double[] negligible = doNegligibleMomentum(pData, hz[0], hz[1]);
            hz[0] = negligible[0];
            hz[1] = negligible[1];
            break;
        }
        default:
            break;
        }
    }

    /**
     * Vertical phases the hidden-tick search shares with {@link SurvivalFly}.
     * Candidates are created in {@link MotionPhaseId#JUMP} when the player is in liquid.
     */
    private static void applyHiddenVerticalPhase(final MotionPhaseId id, final MotionSchedule schedule, final double[] yBox, final double[][] candidatesOut,
            final Player player, final MovingData data, final CombinedData cData, final IPlayerData pData, final PlayerLocation from, final PlayerLocation to,
            final PlayerMoveData lastMove, final boolean onGround, final int depthIndex, final double incomingY, final double jumpGain,
            final double verticalLiquidPushComponent, final double yBeforeMove) {
        switch (id) {
        case SLIME_BOUNCE:
            if (!pData.isShiftKeyPressed() && yBox[0] < 0.0 && from.isOnBouncyBlock()) {
                yBox[0] = from.isOnSlimeBlock() ? -yBox[0] : -yBox[0] * 0.66;
            }
            break;
        case INSIDE_BLOCKS:
            if (from.isSlidingDown()) {
                yBox[0] = -Magic.SLIDE_SPEED_THROTTLE;
            }
            break;
        case CLIMBABLE_POST_MOVE:
            if (!lastMove.from.inLiquid && !lastMove.isGliding) {
                if (lastMove.collidesHorizontally && (lastMove.from.onClimbable || lastMove.from.touchedPowderSnow && BridgeMisc.canStandOnPowderSnow(player))) {
                    yBox[0] = 0.2;
                }
            }
            break;
        case MEDIUM_GRAVITY_FRICTION:
            if (lastMove.from.inWater) {
                if (lastMove.collidesHorizontally && lastMove.from.onClimbable && pData.getClientVersion().isAtLeast(ClientVersion.V_1_14)) {
                    yBox[0] = 0.2;
                }
                yBox[0] *= data.lastFrictionVertical;
                if (BridgeMisc.hasGravity(player)) {
                    if (schedule.liquidModel() == LiquidModel.FIXED_GRAVITY) {
                        yBox[0] -= Magic.LEGACY_LIQUID_GRAVITY;
                    }
                    else {
                        final boolean falling = schedule.gravitySample() == MotionSchedule.GravitySample.BEFORE_MOVE ? yBeforeMove <= 0.0 : yBox[0] <= 0.0;
                        yBox[0] = from.getFluidFallingAdjustedMovement(data.lastGravity, falling, new Vector(0.0, yBox[0], 0.0), cData.wasSprinting).getY();
                    }
                }
            }
            else if (lastMove.from.inLava) {
                if (data.lastFrictionVertical != Magic.LAVA_VERTICAL_INERTIA) {
                    yBox[0] *= data.lastFrictionVertical;
                    if (BridgeMisc.hasGravity(player)) {
                        if (schedule.liquidModel() == LiquidModel.FIXED_GRAVITY) {
                            yBox[0] -= Magic.LEGACY_LIQUID_GRAVITY;
                        }
                        else {
                            final boolean falling = schedule.gravitySample() == MotionSchedule.GravitySample.BEFORE_MOVE ? yBeforeMove <= 0.0 : yBox[0] <= 0.0;
                            yBox[0] = from.getFluidFallingAdjustedMovement(data.lastGravity, falling, new Vector(0.0, yBox[0], 0.0), cData.wasSprinting).getY();
                        }
                    }
                }
                else {
                    yBox[0] *= data.lastFrictionVertical;
                }
                if (schedule.liquidModel() == LiquidModel.FLUID_FALLING && data.lastGravity != 0.0) {
                    yBox[0] += -data.lastGravity / 4.0;
                }
            }
            else {
                if (cData.wasLevitating) {
                    yBox[0] += (0.05 * data.lastLevitationLevel - (depthIndex == 0 ? lastMove.yAllowedDistance : incomingY)) * 0.2;
                }
                else yBox[0] -= data.lastGravity;
                yBox[0] *= data.lastFrictionVertical;
            }
            break;
        case LIQUID_EXIT:
            if (lastMove.from.inLiquid && lastMove.collidesHorizontally && from.isUnobstructed(0.0)) {
                yBox[0] = 0.3;
            }
            break;
        case FLUID_CURRENT:
            if (from.isInLiquid() && verticalLiquidPushComponent != 0.0) {
                yBox[0] += verticalLiquidPushComponent;
            }
            break;
        case DESCEND_IN_WATER:
            if (pData.isShiftKeyPressed() && from.isInWater()) {
                yBox[0] -= Magic.LIQUID_SPEED_GAIN;
            }
            break;
        case NEGLIGIBLE_SPEED:
            yBox[0] = doNegligibleMomentumVertical(pData, yBox[0]);
            break;
        case JUMP:
            if (from.isInLiquid()) {
                final double[] yTheoreticalDistance = new double[] { yBox[0], yBox[0], yBox[0] };
                final boolean isSubmergedInWater = from.isInWater() && data.playerMoves.getCurrentMove().submergedWaterHeight > 0.0;
                final double fluidJumpThreshold = from.getEyeHeight() < 0.4D ? 0.0D : 0.4D;
                final PlayerMoveData thisMove = data.playerMoves.getCurrentMove();
                if (isSubmergedInWater && (!onGround || thisMove.submergedWaterHeight > fluidJumpThreshold)) {
                    yTheoreticalDistance[0] += Magic.LIQUID_SPEED_GAIN;
                }
                else if (from.isInLava() && (!onGround || thisMove.submergedLavaHeight > fluidJumpThreshold)) {
                    yTheoreticalDistance[0] += Magic.LIQUID_SPEED_GAIN;
                }
                else if ((onGround || isSubmergedInWater && thisMove.submergedWaterHeight <= fluidJumpThreshold) && data.jumpDelay == 0) {
                    final double jump = data.liftOffEnvelope.getJumpGain(data.jumpAmplifier) * jumpGain;
                    yTheoreticalDistance[0] = schedule.jumpVertical() == JumpVertical.KEEP_IF_HIGHER ? Math.max(jump, yTheoreticalDistance[0]) : jump;
                }
                candidatesOut[0] = yTheoreticalDistance;
            }
            break;
        case SWIM_PITCH:
            if (schedule.hasSwimming() && candidatesOut[0] != null && Bridge1_13.isSwimming(player) && !player.isInsideVehicle()) {
                final Vector lookVector = TrigUtil.getLookingDirection(to, player);
                final double swimmingScalar = lookVector.getY() < -0.2 ? 0.085 : 0.06;
                candidatesOut[0][0] += (lookVector.getY() - candidatesOut[0][0]) * swimmingScalar;
                candidatesOut[0][1] += (lookVector.getY() - candidatesOut[0][1]) * swimmingScalar;
            }
            break;
        case CLIMBABLE_CLAMP:
            if (!from.isInLiquid() && from.isOnClimbable() && from.canClimbUp(data.liftOffEnvelope.getMaxJumpHeight(data.jumpAmplifier))) {
                yBox[0] = Math.max(yBox[0], -Magic.CLIMBABLE_MAX_SPEED);
                final long theseFlags = BlockFlags.getBlockFlags(from.getBlockType());
                if (yBox[0] < 0.0 && pData.isShiftKeyPressed()
                    && (theseFlags & BlockFlags.F_SCAFFOLDING) == 0
                    && pData.getClientVersion().isAtLeast(ClientVersion.V_1_14)) {
                    yBox[0] = 0.0;
                }
            }
            break;
        case STUCK_SPEED:
            if (TrigUtil.lengthSquared(data.nextStuckInBlockHorizontal, data.nextStuckInBlockVertical, data.nextStuckInBlockHorizontal) > 1.0E-7) {
                if (candidatesOut[0] != null) {
                    for (int i = 0; i < candidatesOut[0].length; i++) {
                        candidatesOut[0][i] *= data.nextStuckInBlockVertical;
                    }
                }
                else yBox[0] *= data.nextStuckInBlockVertical;
            }
            break;
        default:
            break;
        }
    }

    private static double[] reconstructHiddenTicksRecursive(final double yDistance, final Player player, final MovingData data, final CombinedData cData, final IPlayerData pData, final PlayerLocation from, final PlayerLocation to, final double jumpGain, final double verticalLiquidPushComponent, final boolean onGround, final int depthRemaining, final double totalY,
            final double targetY, final int depthIndex) {
        // ------------------------------------------------------------------
        // Early exit 1: accumulated displacement already matches the target.
        // ------------------------------------------------------------------
        if (MathUtil.isOffsetWithinPredictionEpsilon(totalY, targetY)) {
            return new double[]{0.0, targetY - totalY};
        }
        
        // ------------------------------------------------------------------
        // Early exit 2: depth cap reached — return the residual as-is.
        // SurvivalFly will decide whether the error is acceptable.
        // ------------------------------------------------------------------
        if (depthRemaining == 0) {
            return new double[]{0.0, targetY - totalY};
        }
        
        // ------------------------------------------------------------------
        // Early exit 3: the incoming momentum is already above the suppression
        // threshold, so this tick cannot be a hidden tick — stop recursing.
        // The threshold differs between old and new protocol versions.
        // ------------------------------------------------------------------
        final double suppressionThreshold = pData.getClientVersion().isLowerThan(ClientVersion.V_1_18_2) ? Magic.Minecraft_minMoveSqDistance_legacy : Magic.Minecraft_minMoveSqDist_modern;
        if (yDistance > suppressionThreshold) {
            return new double[]{0.0, targetY - totalY};
        }
        
        final PlayerMoveData lastMove = data.playerMoves.getFirstPastMove();
        double baseY = yDistance;
        // Last stuck multiplier is a seed, same as SurvivalFly. The schedule then orders bounce, medium, and block effects.
        if (TrigUtil.lengthSquared(data.lastStuckInBlockHorizontal, data.lastStuckInBlockVertical, data.lastStuckInBlockHorizontal) > 1.0E-7) {
            if (data.lastStuckInBlockVertical != 1.0) {
                baseY = 0.0;
            }
        }
        final double[] yBox = new double[] { baseY };
        final double[][] hiddenCandidates = new double[1][];
        final MotionSchedule verticalSchedule = MotionSchedule.forVersion(pData.getClientVersion());
        final double yBeforeMove = yBox[0];
        AxisScheduler.carryThenPre(verticalSchedule, id -> applyHiddenVerticalPhase(id, verticalSchedule, yBox, hiddenCandidates, player, data, cData, pData, from, to,
                lastMove, onGround, depthIndex, yDistance, jumpGain, verticalLiquidPushComponent, yBeforeMove), null);
        baseY = yBox[0];
        final double[] yTheoreticalDistance = hiddenCandidates[0];
        double[] best = null;
        double bestErrSq = Double.MAX_VALUE;
        if (yTheoreticalDistance != null) {
            for (int i = 0; i < yTheoreticalDistance.length; i++) {
                // Check whether adding this tick's displacement gets us within epsilon of the target.
                final double nextTotalY = totalY + yTheoreticalDistance[i];
                //System.out.println(resultX + " | " + resultZ);
                if (MathUtil.isOffsetWithinPredictionEpsilon(nextTotalY, targetY)) {
                    // Perfect match, short-circuit immediately, no need to recurse.
                    return new double[] {yTheoreticalDistance[i], targetY - nextTotalY};
                }
                // Recurse one level deeper with this candidate's resulting velocity.
                double[] nextRes = reconstructHiddenTicksRecursive(yTheoreticalDistance[i], player, data, cData, pData, from, to, 
                                                                   jumpGain, verticalLiquidPushComponent, onGround, 
                                                                   depthRemaining - 1, nextTotalY, targetY, depthIndex + 1);
            
                // Fold the sub-result back: accumulated displacement = this tick + child ticks.
                double[] candidate = new double[] {
                        nextRes[0] + yTheoreticalDistance[i],
                        nextRes[1]
                };
            
                // Keep the candidate with the smallest residual error.
                double candidateErrSq = candidate[1] * candidate[1];
                if (best == null || candidateErrSq < bestErrSq) {
                    best = candidate;
                    bestErrSq = candidateErrSq;
                }
            
                // Early exit: residual is already within acceptable tolerance — no point
                // testing the remaining candidates.
                if (candidateErrSq <= ACCEPTABLE_ERROR_SQUARED) {
                    return candidate;
                }
            }
        } else {
            final double nextTotalY = totalY + baseY;
            if (MathUtil.isOffsetWithinPredictionEpsilon(nextTotalY, targetY)) {
                // Perfect match, short-circuit immediately, no need to recurse.
                return new double[] {baseY, targetY - nextTotalY};
            }
        
            // Recurse one level deeper with this candidate's resulting velocity.
            double[] nextRes = reconstructHiddenTicksRecursive(baseY, player, data, cData, pData, from, to, 
                                                               jumpGain, verticalLiquidPushComponent, onGround, 
                                                               depthRemaining - 1, nextTotalY, targetY, depthIndex + 1);
        
            // Fold the sub-result back: accumulated displacement = this tick + child ticks.
            double[] candidate = new double[] {
                    nextRes[0] + baseY,
                    nextRes[1]
            };
        
            // Keep the candidate with the smallest residual error.
            double candidateErrSq = candidate[1];
            if (best == null || candidateErrSq < bestErrSq) {
                best = candidate;
                bestErrSq = candidateErrSq;
            }
        
            // Early exit: residual is already within acceptable tolerance — no point
            // testing the remaining candidates.
            if (candidateErrSq <= ACCEPTABLE_ERROR_SQUARED) {
                return candidate;
            }
        }
        // Return the best candidate found, or a zero-displacement fallback if somehow
        // the candidate list was empty (should never happen with 1 fixed candidates).
        return best != null ? best : new double[] {0.0, targetY - totalY};
    }
    
    /**
     * Recursively searches for a sequence of hidden tick inputs that best explains
     * the observed position delta, using a depth-limited brute-force over all nine
     * possible WASD keyboard states.
     *
     * <p>At each recursion level the method:</p>
     * <ol>
     *   <li>Checks early-exit conditions (epsilon match, depth exhausted, move above threshold).</li>
     *   <li>Re-simulates the client's horizontal physics pipeline for the current tick's
     *       momentum ({@code xDistance}, {@code zDistance}).</li>
     *   <li>Iterates over all nine {@link #generateWASDCandidates WASD candidates}, applying
     *       the input acceleration formula for each.</li>
     *   <li>For each candidate, recurses one level deeper with the candidate's resulting
     *       velocity as the new "momentum" and an updated accumulated displacement.</li>
     *   <li>Tracks the best candidate using squared residual error and short-circuits if an
     *       exact match (within {@link #ACCEPTABLE_ERROR_SQUARED}) is found.</li>
     * </ol>
     *
     * <h3>Return value layout</h3>
     * <pre>
     *   index 0 — accumulated X displacement across all simulated hidden ticks
     *   index 1 — accumulated Z displacement across all simulated hidden ticks
     *   index 2 — residual X error  (targetX − (totalX + accumulatedX))
     *   index 3 — residual Z error  (targetZ − (totalZ + accumulatedZ))
     * </pre>
     *
     * @param sinYaw          sine of the player's yaw
     * @param cosYaw          cosine of the player's yaw
     * @param movementSpeed   the player's movement speed scalar for this tick
     * @param inputs          the candidate inputs from the calling level (unused after depth 0;
     *                        all levels expand to the full 9-candidate set internally)
     * @param xDistance       X momentum entering this simulated tick
     * @param zDistance       Z momentum entering this simulated tick
     * @param data            moving data
     * @param pData           player data
     * @param from            player's start-of-move location
     * @param onGround        ground state for physics calculations
     * @param depthRemaining  how many more hidden ticks may be simulated; 0 terminates recursion
     * @param totalX          X displacement accumulated by all previously simulated ticks
     * @param totalZ          Z displacement accumulated by all previously simulated ticks
     * @param crouching       sneaking state
     * @param sneakingFactor  speed multiplier while sneaking
     * @param usingItem       item-use state
     * @param targetX         the observed X position the reconstruction is trying to reach
     * @param targetZ         the observed Z position the reconstruction is trying to reach
     * @param depthIndex      current recursion depth (0 = first hidden tick below the observed move)
     * @return four-element array as described above
     */
    private static double[] reconstructHiddenTicksRecursive(float sinYaw, float cosYaw, float movementSpeed, PlayerKeyboardInput[] inputs,
                                                            final double xDistance, final double zDistance, final MovingData data, final IPlayerData pData,
                                                            final PlayerLocation from, final boolean onGround, final int depthRemaining, final double totalX,
                                                            final double totalZ, final boolean crouching, final double sneakingFactor, final boolean usingItem,
                                                            final double targetX, final double targetZ, final int depthIndex,
                                                            final double yDistanceBeforeCollide, final boolean flying) {
        
        final PlayerMoveData thisMove = data.playerMoves.getCurrentMove();
        
        // ------------------------------------------------------------------
        // Early exit 1: accumulated displacement already matches the target.
        // ------------------------------------------------------------------
        if (MathUtil.isOffsetWithinPredictionEpsilon(totalX, targetX) && MathUtil.isOffsetWithinPredictionEpsilon(totalZ, targetZ)) {
            return new double[]{0.0, 0.0, targetX - totalX, targetZ - totalZ};
        }
        
        // ------------------------------------------------------------------
        // Early exit 2: depth cap reached — return the residual as-is.
        // SurvivalFly will decide whether the error is acceptable.
        // ------------------------------------------------------------------
        if (depthRemaining == 0) {
            return new double[]{0.0, 0.0, targetX - totalX, targetZ - totalZ};
        }
        
        // ------------------------------------------------------------------
        // Early exit 3: the incoming momentum is already above the suppression
        // threshold, so this tick cannot be a hidden tick — stop recursing.
        // The threshold differs between old and new protocol versions.
        // ------------------------------------------------------------------
        final double suppressionThresholdSq = pData.getClientVersion().isLowerThan(ClientVersion.V_1_18_2)
                ? Magic.Minecraft_minMoveSqDistance_legacy : Magic.Minecraft_minMoveSqDist_modern;
        final double incomingHSq = MathUtil.square(xDistance) + MathUtil.square(zDistance);
        final boolean incomingSuppressed = pData.getClientVersion().isLowerThan(ClientVersion.V_1_18_2)
                ? MathUtil.dist(xDistance, zDistance) < suppressionThresholdSq
                : incomingHSq < suppressionThresholdSq;
        final double residualX = targetX - totalX;
        final double residualZ = targetZ - totalZ;
        final double residualSq = MathUtil.square(residualX) + MathUtil.square(residualZ);
        final boolean residualNeedsHidden = pData.getClientVersion().isLowerThan(ClientVersion.V_1_18_2)
                ? MathUtil.dist(residualX, residualZ) >= suppressionThresholdSq
                : residualSq >= suppressionThresholdSq;
        if (!incomingSuppressed && !residualNeedsHidden) {
            return new double[]{0.0, 0.0, residualX, residualZ};
        }
        
        // ------------------------------------------------------------------
        // Physics pipeline: propagate the incoming momentum through one tick.
        // The resulting baseX/baseZ is the momentum *before* any new WASD input
        // is applied for this hidden tick.
        // ------------------------------------------------------------------
        
        final PlayerMoveData lastMove = data.playerMoves.getFirstPastMove();
        double baseX = xDistance;
        double baseZ = zDistance;

        if (data.lastStuckInBlockHorizontal != 1.0) {
            if (TrigUtil.lengthSquared(data.lastStuckInBlockHorizontal, data.lastStuckInBlockVertical, data.lastStuckInBlockHorizontal) > 1.0E-7) {
                baseX = 0.0;
                baseZ = 0.0;
            }
        }
        // Same carry-then-pre list as SurvivalFly. Input, edge, and collide stay in the candidate loop below.
        final double[] hz = new double[] { baseX, baseZ };
        final MotionSchedule horizontalSchedule = MotionSchedule.forVersion(pData.getClientVersion());
        AxisScheduler.carryThenPre(horizontalSchedule, id -> applyHiddenHorizontalPhase(id, hz, from, lastMove, thisMove, data, pData), () -> {
            if (thisMove.hasAttackSlowDown) {
                hz[0] *= Magic.ATTACK_SLOWDOWN;
                hz[1] *= Magic.ATTACK_SLOWDOWN;
            }
        });
        baseX = hz[0];
        baseZ = hz[1];
        
        double[] best = null;
        double bestErrSq = Double.MAX_VALUE;
        
        // ------------------------------------------------------------------
        // Brute-force search: try every WASD candidate for this hidden tick.
        // ------------------------------------------------------------------
        PlayerKeyboardInput[] candidates;
        //if (depthIndex == 0) {
        //    candidates = new PlayerKeyboardInput[] { inputs[0] };
        //} else {
        candidates = generateWASDCandidates(crouching, sneakingFactor, usingItem);
        //}
        for (PlayerKeyboardInput input : candidates) {
            double resultX = baseX;
            double resultZ = baseZ;
            double inputSq = MathUtil.square((double) input.getStrafe()) + MathUtil.square((double) input.getForward()); // Cast to a double because the client does it
            //if (depthIndex == 0) {
            //    resultX += input.getStrafe() * (double) cosYaw - input.getForward() * (double) sinYaw;
            //    resultZ += input.getForward() * (double) cosYaw + input.getStrafe() * (double) sinYaw;
            //} else
            if (inputSq >= 1.0E-7) {
                if (inputSq > 1.0) {
                    double inputForce = Math.sqrt(inputSq);
                    if (inputForce < 1.0E-4) {
                        input.operationToInt(0, 0, 0);
                    }
                    else input.operationToInt(inputForce, inputForce, 2);
                }
                input.operationToInt(movementSpeed, movementSpeed, 1);
                resultX += input.getStrafe() * (double) cosYaw - input.getForward() * (double) sinYaw;
                resultZ += input.getForward() * (double) cosYaw + input.getStrafe() * (double) sinYaw;
            }
            if (from.isOnClimbable() && !from.isInLiquid()) {
                resultX = MathUtil.clamp(resultX, -Magic.CLIMBABLE_MAX_SPEED, Magic.CLIMBABLE_MAX_SPEED);
                resultZ = MathUtil.clamp(resultZ, -Magic.CLIMBABLE_MAX_SPEED, Magic.CLIMBABLE_MAX_SPEED);
            }
            if (TrigUtil.lengthSquared(data.nextStuckInBlockHorizontal, data.nextStuckInBlockVertical, data.nextStuckInBlockHorizontal) > 1.0E-7) {
                resultX *= (double) data.nextStuckInBlockHorizontal;
                resultZ *= (double) data.nextStuckInBlockHorizontal;
            }
            final double[] afterEdge = { resultX, resultZ };
            applyMaybeBackOffFromEdge(from, pData, flying, yDistanceBeforeCollide, afterEdge);
            resultX = afterEdge[0];
            resultZ = afterEdge[1];
            
            // Check whether adding this tick's displacement gets us within epsilon of the target.
            final double nextTotalX = totalX + resultX;
            final double nextTotalZ = totalZ + resultZ;
            //System.out.println(resultX + " | " + resultZ);
            if (MathUtil.isOffsetWithinPredictionEpsilon(nextTotalX, targetX) && MathUtil.isOffsetWithinPredictionEpsilon(nextTotalZ, targetZ)) {
                // Perfect match, short-circuit immediately, no need to recurse.
                return new double[] {resultX, resultZ, targetX - nextTotalX, targetZ - nextTotalZ};
            }
            
            // Recurse one level deeper with this candidate's resulting velocity.
            double[] nextRes = reconstructHiddenTicksRecursive(sinYaw, cosYaw, movementSpeed, inputs, resultX, resultZ,
                                                               data, pData, from, onGround, depthRemaining - 1, nextTotalX, nextTotalZ,
                                                               crouching, sneakingFactor, usingItem, targetX, targetZ, depthIndex + 1,
                                                               yDistanceBeforeCollide, flying);
            
            // Fold the sub-result back: accumulated displacement = this tick + child ticks.
            double[] candidate = new double[] {
                    nextRes[0] + resultX,
                    nextRes[1] + resultZ,
                    nextRes[2],
                    nextRes[3]
            };
            
            // Keep the candidate with the smallest residual error.
            double candidateErrSq = candidate[2] * candidate[2] + candidate[3] * candidate[3];
            if (best == null || candidateErrSq < bestErrSq) {
                best = candidate;
                bestErrSq = candidateErrSq;
            }
            
            // Early exit: residual is already within acceptable tolerance — no point
            // testing the remaining candidates.
            if (candidateErrSq <= ACCEPTABLE_ERROR_SQUARED) {
                return candidate;
            }
        }
        
        // Return the best candidate found, or a zero-displacement fallback if somehow
        // the candidate list was empty (should never happen with 9 fixed candidates).
        return best != null ? best : new double[] {0.0, 0.0, targetX - totalX, targetZ - totalZ};
    }

    private static void applyMaybeBackOffFromEdge(final PlayerLocation from, final IPlayerData pData, final boolean flying,
            final double yBeforeCollide, final double[] horizontal) {
        if (!flying && pData.isShiftKeyPressed() && from.isAboveGround() && yBeforeCollide <= 0.0) {
            final Vector backOff = from.maybeBackOffFromEdge(new Vector(horizontal[0], yBeforeCollide, horizontal[1]));
            horizontal[0] = backOff.getX();
            horizontal[1] = backOff.getZ();
        }
    }



    /////////////////////////////
    // Public API
    /////////////////////////////
    /**
     * Simulates the single tick in which a player re-accelerates after a coast-to-stop
     * sequence, whose final deceleration frames were suppressed by the client's threshold. <p>
     * Refers to the scenario where a player releases all keys while moving: friction will reduce their speed across
     * several ticks and once speed falls below the suppression threshold, those packets are
     * dropped, including the final {@code 0.0}-distance packet. When the player presses a
     * key again, the server perceives a jump from the last visible non-zero speed directly
     * to the new accelerating speed; in other words, the complete stop is invisible. For example:
     * </p>
     * <pre>
     *   ... 0.2 → 0.1 → 0.01 → [0.001 suppressed due to 0.03] → [0.0 suppressed due to 0.03]
     *       → (key pressed) 0.1 visible
     *
     *   Server sees: ... 0.2 → 0.1 → 0.01 → 0.1 
     * </pre>
     *
     * @param sinYaw                  sine of the player's current yaw angle
     * @param cosYaw                  cosine of the player's current yaw angle
     * @param input                   the keyboard input to simulate
     * @param data                    moving data for this player (inertia, stuck multipliers, etc.)
     * @param pData                   player data (client version, shift key state, etc.)
     * @param from                    the player's position at the start of the hidden tick
     * @param to                      the player's position at the end of the move (used for
     *                                riptide / lunge velocity queries, which need look direction)
     * @param onGround                whether the player was on the ground during this tick
     * @param flying                  whether the player is in a flying state (creative/spectator)
     * @param yDistanceBeforeCollide  the unchecked vertical distance, passed through to the
     *                                collision resolver 
     * @return a two-element array {@code [postCollisionX, postCollisionZ]} representing the
     *         horizontal displacement that would result from this tick, after AABB resolution
     */
    public static double[] simulateStoppingMotion(float sinYaw, float cosYaw, final PlayerKeyboardInput input, final MovingData data,
                                                  final IPlayerData pData, final PlayerLocation from, final PlayerLocation to,
                                                  final boolean onGround, final boolean flying, final double yDistanceBeforeCollide) {
        final PlayerMoveData thisMove = data.playerMoves.getCurrentMove();
        final PlayerMoveData lastMove = data.playerMoves.getFirstPastMove();
        double baseX = 0.0;
        double baseZ = 0.0;
        if (from.isInWater() && !lastMove.from.inWater) {
            Vector liquidFlowVector = from.getLiquidPushingVector(baseX, baseZ, BlockFlags.F_WATER);
            baseX += liquidFlowVector.getX();
            baseZ += liquidFlowVector.getZ();
            // This shortcut only runs on a fresh water entry. Block effects after friction follow the 1.21.2 list.
            final MotionSchedule stopSchedule = MotionSchedule.forVersion(pData.getClientVersion());
            if (stopSchedule.areBlockEffectsAfterFriction()) {
                baseX = zeroStuckHorizontal(data, baseX);
                baseZ = zeroStuckHorizontal(data, baseZ);
                baseX *= (double) data.nextBlockSpeedMultiplier;
                baseZ *= (double) data.nextBlockSpeedMultiplier;
                baseX *= (double) data.lastInertia;
                baseZ *= (double) data.lastInertia;
                final double[] scaled = scaleSlimeAndHoney(from, lastMove, thisMove, pData, baseX, baseZ);
                baseX = scaled[0];
                baseZ = scaled[1];
            }
            else {
                final double[] scaled = scaleSlimeAndHoney(from, lastMove, thisMove, pData, baseX, baseZ);
                baseX = scaled[0];
                baseZ = scaled[1];
                baseX = zeroStuckHorizontal(data, baseX);
                baseZ = zeroStuckHorizontal(data, baseZ);
                baseX *= (double) data.nextBlockSpeedMultiplier;
                baseZ *= (double) data.nextBlockSpeedMultiplier;
                baseX *= (double) data.lastInertia;
                baseZ *= (double) data.lastInertia;
            }
            if (thisMove.hasAttackSlowDown) {
                baseX *= Magic.ATTACK_SLOWDOWN;
                baseZ *= Magic.ATTACK_SLOWDOWN;
            }
        }
        if (from.isInLiquid()) {
            Vector liquidFlowVector = from.getLiquidPushingVector(baseX, baseZ, from.isInWater() ? BlockFlags.F_WATER : BlockFlags.F_LAVA);
            baseX += liquidFlowVector.getX();
            baseZ += liquidFlowVector.getZ();
        }

        double[] negligible = doNegligibleMomentum(pData, baseX, baseZ);
        baseX = negligible[0];
        baseZ = negligible[1];
        baseX += input.getStrafe() * (double) cosYaw - input.getForward() * (double) sinYaw;
        baseZ += input.getForward() * (double) cosYaw + input.getStrafe() * (double) sinYaw;
        
        if (from.isOnClimbable() && !from.isInLiquid()) {
            baseX = MathUtil.clamp(baseX, -Magic.CLIMBABLE_MAX_SPEED, Magic.CLIMBABLE_MAX_SPEED);
            baseZ = MathUtil.clamp(baseZ, -Magic.CLIMBABLE_MAX_SPEED, Magic.CLIMBABLE_MAX_SPEED);
        }
        if (TrigUtil.lengthSquared(data.nextStuckInBlockHorizontal, data.nextStuckInBlockVertical, data.nextStuckInBlockHorizontal) > 1.0E-7) {
            baseX *= (double) data.nextStuckInBlockHorizontal;
            baseZ *= (double) data.nextStuckInBlockHorizontal;
        }
        if (thisMove.tridentRelease.decideOptimistically()) {
            Vector riptideVelocity = to.getRiptideVelocity(onGround);
            baseX += riptideVelocity.getX();
            baseZ += riptideVelocity.getZ();
        }
        if (thisMove.lungingForward) {
            Vector lungeVelocity = to.tryApplyLungingMotion();
            baseX += lungeVelocity.getX();
            baseZ += lungeVelocity.getZ();
        }
        final double[] stopHoriz = { baseX, baseZ };
        applyMaybeBackOffFromEdge(from, pData, flying, yDistanceBeforeCollide, stopHoriz);
        baseX = stopHoriz[0];
        baseZ = stopHoriz[1];
        Vector collisionVector = from.collide(new Vector(baseX, yDistanceBeforeCollide, baseZ), onGround, from.getBoundingBox());
        return new double[] {collisionVector.getX(), collisionVector.getZ()};
    }
    
    
    /**
     * Entry point for the hidden-tick brute-force reconstruction.
     *
     * <p>Called by {@link SurvivalFly}, this method initialises the recursive
     * search with the original observed {@code input} as the depth-0 candidate and
     * delegates to {@link #reconstructHiddenTicksRecursive} for the actual tree traversal.</p>
     * <p>The returned array contains the cumulative X and Z displacement across all simulated hidden ticks, 
     * as well as the final residual error compared to the observed position. 
     * If the error is within an acceptable tolerance, the reconstructed path is considered a valid explanation for the hidden movement.</p>
     *
     * @param sinYaw          sine of the player's current yaw angle
     * @param cosYaw          cosine of the player's current yaw angle
     * @param movementspeed   the player's current movement speed scalar (sprint, slow, etc.)
     * @param input           the keyboard input inferred from the observed move direction
     * @param xDistance       the observed X displacement for the current (possibly hidden) tick
     * @param zDistance       the observed Z displacement for the current (possibly hidden) tick
     * @param data            moving data for this player
     * @param pData           player data (client version, shift-key state, etc.)
     * @param from            the player's start-of-move position
     * @param crouching       whether the player is sneaking
     * @param sneakingFactor  horizontal speed multiplier while sneaking
     * @param usingItem       whether the player is using an item
     * @param onGround        whether the player is on the ground
     * @param totalX          accumulated reconstructed X displacement so far (usually 0 at entry)
     * @param totalZ          accumulated reconstructed Z displacement so far (usually 0 at entry)
     * @return a four-element array {@code [cumulativeDeltaX, cumulativeDeltaZ, residualErrX, residualErrZ]}
     *
     * <p>Called by {@code SurvivalFly} when a WASD candidate (selected via
     * {@code hiddenDistanceIndex}) produced a post-collision displacement small
     * enough to be suppressed by the client. The selected candidate is passed
     * as the depth-0 seed and the reconstructor searches for sequences of
     * hidden ticks that, when simulated, explain the observed movement. The
     * returned array contains cumulative delta X/Z and the residual error
     * compared to the observed position.</p>
     *
     */
    public static double[] findBestHiddenTickExplanation(float sinYaw, float cosYaw, float movementspeed, PlayerKeyboardInput input,
                                                         final double xDistance, final double zDistance, final MovingData data, final IPlayerData pData, final PlayerLocation from,
                                                         final boolean crouching, final double sneakingFactor, final boolean usingItem,
                                                         final boolean onGround, final double totalX, final double totalZ,
                                                         final double yDistanceBeforeCollide, final boolean flying) {
        final PlayerMoveData thisMove = data.playerMoves.getCurrentMove();
        return reconstructHiddenTicksRecursive(sinYaw, cosYaw, movementspeed, new PlayerKeyboardInput[] {input},
                                               xDistance, zDistance, data, pData, from, onGround, MAX_HIDDEN_TICK_DEPTH,
                                               totalX, totalZ, crouching, sneakingFactor, usingItem,
                                               thisMove.xDistance, thisMove.zDistance, 0,
                                               yDistanceBeforeCollide, flying);
    }
    public static double[] findBestHiddenTickExplanation(final double yDistance, final Player player, final MovingData data, final CombinedData cData, final IPlayerData pData, 
                                                         final PlayerLocation from, final PlayerLocation to, final double jumpGain, 
                                                         final double verticalLiquidPushComponent, final boolean onGround, final double totalY) {
        final PlayerMoveData thisMove = data.playerMoves.getCurrentMove();
        return reconstructHiddenTicksRecursive(yDistance, player, data, cData, pData, from, to, jumpGain, verticalLiquidPushComponent, onGround, MAX_HIDDEN_TICK_DEPTH, totalY, thisMove.yDistance, 0);
    }

    /**
     * Vanilla keeps {@code deltaMovement} on axes reduced by {@code maybeBackOffFromEdge} while move packets only
     * carry cutoff displacement on those axes. Store pre-edge components for next-tick momentum ({@code motionX}/{@code motionZ}).
     */
    static void storeEdgeHiddenMotion(final PlayerMoveData move, final double preEdgeX, final double preEdgeZ,
            final double backOffX, final double backOffZ, final double packetSuppressH) {
        final boolean clippedX = preEdgeX != backOffX;
        final boolean clippedZ = preEdgeZ != backOffZ;
        final boolean suppressedPktX = Math.abs(move.xDistance) < packetSuppressH && Math.abs(preEdgeX) >= packetSuppressH;
        final boolean suppressedPktZ = Math.abs(move.zDistance) < packetSuppressH && Math.abs(preEdgeZ) >= packetSuppressH;
        move.motionX = (clippedX || suppressedPktX) ? preEdgeX : move.xDistance;
        move.motionZ = (clippedZ || suppressedPktZ) ? preEdgeZ : move.zDistance;
        if (clippedX || clippedZ || suppressedPktX || suppressedPktZ) {
            move.edgeAxisClamped = true;
        }
        if (clippedX && clippedZ) {
            move.edgeCornerClamp = true;
        }
    }

    /**
     * Per-input candidate variant for the brute-force path in {@link SurvivalFly}.
     */
    static void storeEdgeHiddenMotionCandidate(final PlayerMoveData move, final double[] xMotion, final double[] zMotion, final int index,
            final double preEdgeX, final double preEdgeZ, final double backOffX, final double backOffZ,
            final double packetX, final double packetZ, final double packetSuppressH) {
        final boolean clippedX = preEdgeX != backOffX;
        final boolean clippedZ = preEdgeZ != backOffZ;
        final boolean suppressedPktX = Math.abs(packetX) < packetSuppressH && Math.abs(preEdgeX) >= packetSuppressH;
        final boolean suppressedPktZ = Math.abs(packetZ) < packetSuppressH && Math.abs(preEdgeZ) >= packetSuppressH;
        xMotion[index] = (clippedX || suppressedPktX) ? preEdgeX : packetX;
        zMotion[index] = (clippedZ || suppressedPktZ) ? preEdgeZ : packetZ;
    }

    /**
     * Pick the WASD index whose pre-edge vector is still clamped at the edge (for momentum when prediction is uncertain).
     * Uses the back-off arrays already populated by the brute loop — no extra {@code maybeBackOffFromEdge} calls.
     */
    static int findBestEdgeMomentumIndex(final double[] xPre, final double[] zPre,
            final double[] xBackOff, final double[] zBackOff, final int preferredIdx) {
        if (preferredIdx >= 0 && preferredIdx < 9
                && edgeStillClampsCached(xPre[preferredIdx], zPre[preferredIdx], xBackOff[preferredIdx], zBackOff[preferredIdx])) {
            return preferredIdx;
        }
        int bestIdx = -1;
        double bestH = -1.0;
        for (int i = 0; i < 9; i++) {
            if (!edgeStillClampsCached(xPre[i], zPre[i], xBackOff[i], zBackOff[i])) {
                continue;
            }
            final double h = MathUtil.dist(xPre[i], zPre[i]);
            if (h > bestH) {
                bestH = h;
                bestIdx = i;
            }
        }
        return bestIdx;
    }

    /**
     * True if {@code maybeBackOffFromEdge} would change this horizontal vector (still on an edge).
     */
    static boolean edgeStillClamps(final PlayerLocation from, final double yBeforeCollide,
            final double vecX, final double vecZ) {
        final Vector backOff = from.maybeBackOffFromEdge(new Vector(vecX, yBeforeCollide, vecZ));
        return backOff.getX() != vecX || backOff.getZ() != vecZ;
    }

    /** No-call variant: reuse a known back-off result instead of recomputing. */
    static boolean edgeStillClampsCached(final double vecX, final double vecZ,
            final double backOffX, final double backOffZ) {
        return backOffX != vecX || backOffZ != vecZ;
    }

    /** No-call variant: reuse a known back-off result instead of recomputing. */
    static boolean edgeStillClampsCornerCached(final double vecX, final double vecZ,
            final double backOffX, final double backOffZ) {
        return backOffX != vecX && backOffZ != vecZ;
    }

    /**
     * Carry hidden momentum only while shift-sneaking at an edge and backoff still clamps the prior hidden vector.
     */
    static boolean shouldUseEdgeHiddenCarry(final boolean edgeSneakNow, final PlayerLocation from,
            final double yDistanceBeforeCollide, final PlayerMoveData lastMove) {
        if (!edgeSneakNow || !lastMove.toIsValid || !lastMove.edgeBackoffApplied || !lastMove.edgeAxisClamped) {
            return false;
        }
        return edgeStillClamps(from, yDistanceBeforeCollide, lastMove.motionX, lastMove.motionZ);
    }

    /**
     * Match tolerance scales with sneak/speed so swift sneak and speed potions stay within legit bounds.
     */
    static double edgeCornerMatchTolerance(final double referenceHorizontalSpeed) {
        final double scaled = referenceHorizontalSpeed * Magic.EDGE_SNEAK_CORNER_MATCH_SPEED_RATIO + Magic.PREDICTION_EPSILON;
        return Math.min(Magic.EDGE_SNEAK_CORNER_MATCH_TOLERANCE_MAX,
                Math.max(Magic.EDGE_SNEAK_CORNER_MATCH_TOLERANCE_MIN, scaled));
    }

    static double packetSuppressThresholdH(final IPlayerData pData) {
        if (pData.getClientVersion().isLowerThan(ClientVersion.V_1_18_2)) {
            return Magic.Minecraft_minMoveSqDistance_legacy;
        }
        return Math.sqrt(Magic.Minecraft_minMoveSqDist_modern);
    }

    static double edgeSneakBypassMaxH(final MovingData data) {
        final float walkSpeed = data.walkSpeed > 0.0f ? data.walkSpeed : 0.1f;
        return Math.min(Magic.EDGE_SNEAK_BYPASS_MAX_HDIST_HARD,
                Math.max(Magic.EDGE_SNEAK_BYPASS_MAX_HDIST, walkSpeed * Magic.EDGE_SNEAK_BYPASS_WALK_SPEED_FACTOR));
    }

    /**
     * Shift-sneak at a block edge: if horizontal prediction disagrees with the packet, trust the packet
     * (within a walk-speed-scaled cap). Cheap alternative to hidden-tick folding on 1.18+.
     */
    static double applyEdgeSneakBypass(final Player player, final IPlayerData pData, final MovingData data,
            final PlayerLocation from, final PlayerMoveData lastMove, final PlayerMoveData thisMove,
            final double hDistanceAboveLimit, final Collection<String> tags) {
        final boolean edgeTick = thisMove.edgeBackoffApplied || (lastMove.toIsValid && lastMove.edgeBackoffApplied);
        if (!edgeTick) {
            return hDistanceAboveLimit;
        }
        final double maxH = edgeSneakBypassMaxH(data);
        final double refH = Math.max(thisMove.hDistance, thisMove.hAllowedDistance);
        if (thisMove.hDistance > maxH && hDistanceAboveLimit > edgeCornerMatchTolerance(refH)) {
            return hDistanceAboveLimit;
        }
        thisMove.xAllowedDistance = thisMove.xDistance;
        thisMove.zAllowedDistance = thisMove.zDistance;
        thisMove.hAllowedDistance = thisMove.hDistance;
        thisMove.motionX = thisMove.xDistance;
        thisMove.motionZ = thisMove.zDistance;
        tags.add("edge_bypass");
        return 0.0;
    }

    /**
     * After edge brute-force, at least one candidate was clipped by {@code maybeBackOffFromEdge}.
     */
    static boolean anyEdgeCandidateClipped(final double[] xMotionBeforeEdge, final double[] zMotionBeforeEdge,
            final double[] xBackOffAfterEdge, final double[] zBackOffAfterEdge) {
        for (int j = 0; j < 9; j++) {
            if (xBackOffAfterEdge[j] != xMotionBeforeEdge[j] || zBackOffAfterEdge[j] != zMotionBeforeEdge[j]) {
                return true;
            }
        }
        return false;
    }

    /**
     * At a block corner both axes may be clipped while packets show single-axis micro-steps.
     * Pick the WASD candidate whose post-edge or post-collide displacement is closest to the packet.
     *
     * @return Candidate index, or {@code -1} if none is within {@link #edgeCornerMatchTolerance(double)}.
     */
    static int findBestEdgeCornerCandidate(final double packetX, final double packetZ, final double packetH,
            final double[] xPost, final double[] zPost, final double[] xBackOff, final double[] zBackOff,
            final boolean strict, final double matchTolerance) {
        int bestIdx = -1;
        double bestScore = Double.MAX_VALUE;
        for (int i = 0; i < 9; i++) {
            final double postScore = strict
                    ? Math.abs(packetX - xPost[i]) + Math.abs(packetZ - zPost[i])
                    : Math.abs(packetH - MathUtil.dist(xPost[i], zPost[i]));
            final double backScore = strict
                    ? Math.abs(packetX - xBackOff[i]) + Math.abs(packetZ - zBackOff[i])
                    : Math.abs(packetH - MathUtil.dist(xBackOff[i], zBackOff[i]));
            final double score = Math.min(postScore, backScore);
            if (score < bestScore) {
                bestScore = score;
                bestIdx = i;
            }
        }
        if (bestIdx >= 0 && bestScore <= matchTolerance) {
            return bestIdx;
        }
        return -1;
    }
}
