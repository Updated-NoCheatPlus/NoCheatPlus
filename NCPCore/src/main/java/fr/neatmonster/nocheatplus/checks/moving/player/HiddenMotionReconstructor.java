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

import org.bukkit.util.Vector;

import fr.neatmonster.nocheatplus.checks.moving.MovingData;
import fr.neatmonster.nocheatplus.checks.moving.model.PlayerKeyboardInput;
import fr.neatmonster.nocheatplus.checks.moving.model.PlayerMoveData;
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
                                                            final double targetX, final double targetZ, final int depthIndex) {
        
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
        final double suppressionThreshold = pData.getClientVersion().isLowerThan(ClientVersion.V_1_18_2) ? 0.03 : 0.0002;
        if (MathUtil.dist(xDistance, zDistance) > suppressionThreshold) {
            return new double[]{0.0, 0.0, targetX - totalX, targetZ - totalZ};
        }
        
        // ------------------------------------------------------------------
        // Physics pipeline: propagate the incoming momentum through one tick.
        // The resulting baseX/baseZ is the momentum *before* any new WASD input
        // is applied for this hidden tick.
        // ------------------------------------------------------------------
        
        final PlayerMoveData lastMove = data.playerMoves.getFirstPastMove();
        double baseX = xDistance;
        double baseZ = zDistance;
        
        if (from.isOnSlimeBlock() && onGround) {
            if (Math.abs(lastMove.yDistance) < 0.1 && !pData.isShiftKeyPressed()) {
                if (thisMove.yDistance == 0.0) {
                    baseX *= 0.67;
                    baseZ *= 0.67;
                }
                else {
                    final double mul = 0.4 + Math.abs(lastMove.yDistance) * 0.2;
                    baseX *= mul;
                    baseZ *= mul;
                }
            }
        }
        
        if (from.isSlidingDown()) {
            if (lastMove.yDistance < -Magic.SLIDE_START_AT_VERTICAL_MOTION_THRESHOLD) {
                baseX *= -Magic.SLIDE_SPEED_THROTTLE / lastMove.yDistance;
                baseZ *= -Magic.SLIDE_SPEED_THROTTLE / lastMove.yDistance;
            }
        }
        if (data.lastStuckInBlockHorizontal != 1.0) {
            if (TrigUtil.lengthSquared(data.lastStuckInBlockHorizontal, data.lastStuckInBlockVertical, data.lastStuckInBlockHorizontal) > 1.0E-7) {
                baseX = 0.0;
                baseZ = 0.0;
            }
        }
        
        baseX *= (double) data.nextBlockSpeedMultiplier;
        baseZ *= (double) data.nextBlockSpeedMultiplier;
        
        baseX *= (double) data.lastInertia;
        baseZ *= (double) data.lastInertia;
        
        if (thisMove.hasAttackSlowDown) {
            baseX *= Magic.ATTACK_SLOWDOWN;
            baseZ *= Magic.ATTACK_SLOWDOWN;
        }
        if (from.isInLiquid()) {
            Vector liquidFlowVector = from.getLiquidPushingVector(baseX, baseZ, from.isInWater() ? BlockFlags.F_WATER : BlockFlags.F_LAVA);
            baseX += liquidFlowVector.getX();
            baseZ += liquidFlowVector.getZ();
        }
        
        double[] negligible = doNegligibleMomentum(pData, baseX, baseZ);
        baseX = negligible[0];
        baseZ = negligible[1];
        
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
                                                               crouching, sneakingFactor, usingItem, targetX, targetZ, depthIndex + 1);
            
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
            // Shortcut: only do when non zero
            if (from.isOnSlimeBlock() && onGround) {
                if (Math.abs(lastMove.yDistance) < 0.1 && !pData.isShiftKeyPressed()) {
                    if (thisMove.yDistance == 0.0) {
                        baseX *= 0.67;
                        baseZ *= 0.67;
                    } 
                    else {
                        final double mul = 0.4 + Math.abs(lastMove.yDistance) * 0.2;
                        baseX *= mul;
                        baseZ *= mul;
                    }
                }
            }
            if (from.isSlidingDown()) { 
                if (lastMove.yDistance < -Magic.SLIDE_START_AT_VERTICAL_MOTION_THRESHOLD) {
                    baseX *= -Magic.SLIDE_SPEED_THROTTLE / lastMove.yDistance;
                    baseZ *= -Magic.SLIDE_SPEED_THROTTLE / lastMove.yDistance;
                }
            }
            if (data.lastStuckInBlockHorizontal != 1.0) {
                if (TrigUtil.lengthSquared(data.lastStuckInBlockHorizontal, data.lastStuckInBlockVertical, data.lastStuckInBlockHorizontal) > 1.0E-7) {
                    baseX = 0.0;
                    baseZ = 0.0;
                }
            }
            baseX *= (double) data.nextBlockSpeedMultiplier;
            baseZ *= (double) data.nextBlockSpeedMultiplier;
            
            baseX *= (double) data.lastInertia;
            baseZ *= (double) data.lastInertia;
            
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
        if (!flying && pData.isShiftKeyPressed() && from.isAboveGround() && thisMove.yDistance <= 0.0) {
            Vector backOff = from.maybeBackOffFromEdge(new Vector(thisMove.xAllowedDistance, yDistanceBeforeCollide, thisMove.zAllowedDistance));
            baseX = backOff.getX();
            baseZ = backOff.getZ();
        }
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
     */
    public static double[] findBestHiddenTickExplanation(float sinYaw, float cosYaw, float movementspeed, PlayerKeyboardInput input,
                                                         final double xDistance, final double zDistance, final MovingData data, final IPlayerData pData, final PlayerLocation from,
                                                         final boolean crouching, final double sneakingFactor, final boolean usingItem,
                                                         final boolean onGround, final double totalX, final double totalZ) {
        final PlayerMoveData thisMove = data.playerMoves.getCurrentMove();
        return reconstructHiddenTicksRecursive(sinYaw, cosYaw, movementspeed, new PlayerKeyboardInput[] {input},
                                               xDistance, zDistance, data, pData, from, onGround, MAX_HIDDEN_TICK_DEPTH,
                                               totalX, totalZ, crouching, sneakingFactor, usingItem,
                                               thisMove.xDistance, thisMove.zDistance, 0);
    }
}
