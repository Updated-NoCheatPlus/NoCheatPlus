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
 * Quick dirty handle hidden under 0.03. Might rename and move somewhere else
 */
public class MiniCalculator {
    private static final int MAX_BRUTE_DEPTH = 2;
    private static final double MATCH_EPSILON_SQ = 1.0E-6;
    
    private static PlayerKeyboardInput[] createBruteforceInputs(final boolean crouching, final double sneakingFactor, final boolean usingItem) {
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
    
    public static double[] motionFromHiddenStopMotion(float sinYaw, float cosYaw, final PlayerKeyboardInput input, final MovingData data, final IPlayerData pData, final PlayerLocation from, final PlayerLocation to, final boolean onGround, final boolean flying, final double yDistanceBeforeCollide) {
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
                    } else {
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
                if (TrigUtil.lengthSquared(
                        data.lastStuckInBlockHorizontal,
                        data.lastStuckInBlockVertical,
                        data.lastStuckInBlockHorizontal) > 1.0E-7) {
                    baseX = 0.0;
                    baseZ = 0.0;
                }
            }
            // ???-Next stage after ground riptide(integrated, no gnd_riptide_pre)...

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
            Vector liquidFlowVector = from.getLiquidPushingVector(
                    baseX, baseZ, from.isInWater() ? BlockFlags.F_WATER : BlockFlags.F_LAVA);
            baseX += liquidFlowVector.getX();
            baseZ += liquidFlowVector.getZ();
        }

        double[] negligible = negligibleMomentum(pData, baseX, baseZ);
        baseX = negligible[0];
        baseZ = negligible[1];
        
        baseX += input.getStrafe() * (double) cosYaw - input.getForward() * (double) sinYaw;
        baseZ += input.getForward() * (double) cosYaw + input.getStrafe() * (double) sinYaw;
        
        if (from.isOnClimbable() && !from.isInLiquid()) {
            //data.clearActiveHorVel(); // Might want to clear ALL horizontal vel.
            baseX = MathUtil.clamp(baseX, -Magic.CLIMBABLE_MAX_SPEED, Magic.CLIMBABLE_MAX_SPEED);
            baseZ = MathUtil.clamp(baseZ, -Magic.CLIMBABLE_MAX_SPEED, Magic.CLIMBABLE_MAX_SPEED);
        }
        
        // Stuck-speed multiplier.
        if (TrigUtil.lengthSquared(data.nextStuckInBlockHorizontal, data.nextStuckInBlockVertical, data.nextStuckInBlockHorizontal) > 1.0E-7) {
            baseX *= (double) data.nextStuckInBlockHorizontal;
            baseZ *= (double) data.nextStuckInBlockHorizontal;
        }
        
        // Riptide works by propelling the player after releasing the trident (the effect only pushes the player, unless is on ground)
        if (thisMove.tridentRelease.decideOptimistically()) {
            Vector riptideVelocity = to.getRiptideVelocity(onGround);
            baseX += riptideVelocity.getX();
            baseZ += riptideVelocity.getZ();
        }
        // Lunging forward if applicable: the effect only adds the lunging motion on the current delta.
        // TODO: TOTALLY RANDOM PLACEMENT !
        // The addition is called on any left click, provided the player has a Spear in hand with Lunge enchant.
        // NOTE: Does not need to be brute forced, since lunging is supported only by clients that can send WASD inputs.
        if (thisMove.lungingForward) {
            Vector lungeVelocity = to.tryApplyLungingMotion(); // Use to as we're working with rotations here
            baseX += lungeVelocity.getX();
            baseZ += lungeVelocity.getZ();
        }
        // Try to back off players from edges, if sneaking.
        // NOTE: this is after the riptiding propelling force.
        // NOTE: here the game uses isShiftKeyDown (so this is shifting not sneaking, using Bukkit's isShift is correct)
        if (!flying && pData.isShiftKeyPressed() && from.isAboveGround() && thisMove.yDistance <= 0.0) {
            Vector backOff = from.maybeBackOffFromEdge(new Vector(thisMove.xAllowedDistance, yDistanceBeforeCollide, thisMove.zAllowedDistance));
            baseX = backOff.getX();
            baseZ = backOff.getZ();
        }
        // Collision next.
        // NOTE: Passing the unchecked y-distance is fine in this case. Vertical collision is checked with vdistrel (just separately).
        // TODO: Perhaps after this use collisionVector to store onGround? Also can not restore minecraft ground state with step and jump movement(like stairs)!
        Vector collisionVector = from.collide(new Vector(baseX, yDistanceBeforeCollide, baseZ), onGround, from.getBoundingBox());
        return new double[] {collisionVector.getX(), collisionVector.getZ()}; 
    }
    
    public static double[] hiddenDistanceHorizontal(
            float sinYaw, float cosYaw, float movementspeed, PlayerKeyboardInput input,
            final double xDistance, final double zDistance,
            final MovingData data, final IPlayerData pData, final PlayerLocation from,
            final boolean crouching, final double sneakingFactor, final boolean usingItem,
            final boolean onGround, final double totalX, final double totalZ) {

        final PlayerMoveData thisMove = data.playerMoves.getCurrentMove();

        return hiddenDistanceHorizontalInternal(
                sinYaw, cosYaw, movementspeed, new PlayerKeyboardInput[] {input},
                xDistance, zDistance,
                data, pData, from, onGround,
                MAX_BRUTE_DEPTH,
                totalX, totalZ,
                crouching, sneakingFactor, usingItem,
                thisMove.xDistance, thisMove.zDistance,
                0
        );
    }

    private static double[] hiddenDistanceHorizontalInternal(
            float sinYaw, float cosYaw, float movementSpeed, PlayerKeyboardInput[] inputs,
            final double xDistance, final double zDistance,
            final MovingData data, final IPlayerData pData, final PlayerLocation from,
            final boolean onGround, final int depthRemaining,
            final double totalX, final double totalZ,
            final boolean crouching, final double sneakingFactor, final boolean usingItem,
            final double targetX, final double targetZ,
            final int depthIndex) {

        final PlayerMoveData thisMove = data.playerMoves.getCurrentMove();

        if (MathUtil.isOffsetWithinPredictionEpsilon(totalX, targetX)
                && MathUtil.isOffsetWithinPredictionEpsilon(totalZ, targetZ)) {
            return new double[] {0.0, 0.0, targetX - totalX, targetZ - totalZ};
        }

        if (depthRemaining == 0) {
            return new double[] {0.0, 0.0, targetX - totalX, targetZ - totalZ};
        }

        if (MathUtil.dist(xDistance, zDistance) > (pData.getClientVersion().isLowerThan(ClientVersion.V_1_18_2) ? 0.03 : 0.0002)) {
            return new double[] {0.0, 0.0, targetX - totalX, targetZ - totalZ};
        }

        final PlayerMoveData lastMove = data.playerMoves.getFirstPastMove();

        double baseX = xDistance;
        double baseZ = zDistance;

        if (from.isOnSlimeBlock() && onGround) {
            if (Math.abs(lastMove.yDistance) < 0.1 && !pData.isShiftKeyPressed()) {
                if (thisMove.yDistance == 0.0) {
                    baseX *= 0.67;
                    baseZ *= 0.67;
                } else {
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
            if (TrigUtil.lengthSquared(
                    data.lastStuckInBlockHorizontal,
                    data.lastStuckInBlockVertical,
                    data.lastStuckInBlockHorizontal) > 1.0E-7) {
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
            Vector liquidFlowVector = from.getLiquidPushingVector(
                    baseX, baseZ, from.isInWater() ? BlockFlags.F_WATER : BlockFlags.F_LAVA);
            baseX += liquidFlowVector.getX();
            baseZ += liquidFlowVector.getZ();
        }

        double[] negligible = negligibleMomentum(pData, baseX, baseZ);
        baseX = negligible[0];
        baseZ = negligible[1];

        double[] best = null;
        double bestErrSq = Double.MAX_VALUE;

        PlayerKeyboardInput[] candidates;
        //if (depthIndex == 0) {
        //    candidates = new PlayerKeyboardInput[] { inputs[0] };
        //} else {
            candidates = createBruteforceInputs(crouching, sneakingFactor, usingItem);
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
                        // Not enough force, reset.
                        input.operationToInt(0, 0, 0);
                    }
                    // Normalize
                    else input.operationToInt(inputForce, inputForce, 2);
                }
                // Multiply the input by movement speed.
                input.operationToInt(movementSpeed, movementSpeed, 1);
                // The acceleration vector is added to the current momentum...
                resultX += input.getStrafe() * (double) cosYaw - input.getForward() * (double) sinYaw;
                resultZ += input.getForward() * (double) cosYaw + input.getStrafe() * (double) sinYaw;
            }

            if (from.isOnClimbable() && !from.isInLiquid()) {
                resultX = MathUtil.clamp(resultX, -Magic.CLIMBABLE_MAX_SPEED, Magic.CLIMBABLE_MAX_SPEED);
                resultZ = MathUtil.clamp(resultZ, -Magic.CLIMBABLE_MAX_SPEED, Magic.CLIMBABLE_MAX_SPEED);
            }

            if (TrigUtil.lengthSquared(
                    data.nextStuckInBlockHorizontal,
                    data.nextStuckInBlockVertical,
                    data.nextStuckInBlockHorizontal) > 1.0E-7) {
                resultX *= (double) data.nextStuckInBlockHorizontal;
                resultZ *= (double) data.nextStuckInBlockHorizontal;
            }

            final double nextTotalX = totalX + resultX;
            final double nextTotalZ = totalZ + resultZ;
            //System.out.println(resultX + " | " + resultZ);
            if (MathUtil.isOffsetWithinPredictionEpsilon(nextTotalX, targetX)
                    && MathUtil.isOffsetWithinPredictionEpsilon(nextTotalZ, targetZ)) {
                return new double[] {resultX, resultZ, targetX - nextTotalX, targetZ - nextTotalZ};
            }
            double[] nextRes = hiddenDistanceHorizontalInternal(
                    sinYaw, cosYaw, movementSpeed, inputs,
                    resultX, resultZ,
                    data, pData, from, onGround,
                    depthRemaining - 1,
                    nextTotalX, nextTotalZ,
                    crouching, sneakingFactor, usingItem,
                    targetX, targetZ,
                    depthIndex + 1
            );


            double[] candidate = new double[] {
                    nextRes[0] + resultX,
                    nextRes[1] + resultZ,
                    nextRes[2],
                    nextRes[3]
            };

            double candidateErrSq = candidate[2] * candidate[2] + candidate[3] * candidate[3];
            if (best == null || candidateErrSq < bestErrSq) {
                best = candidate;
                bestErrSq = candidateErrSq;
            }

            if (candidateErrSq <= MATCH_EPSILON_SQ) {
                return candidate;
            }
        }

        return best != null ? best : new double[] {0.0, 0.0, targetX - totalX, targetZ - totalZ};
    }

    private static double[] negligibleMomentum(IPlayerData pData, double xDistance, double zDistance) {
        double resultX = xDistance;
        double resultZ = zDistance;

        if (pData.getClientVersion().isAtLeast(ClientVersion.V_1_9)) {
            if (Math.abs(resultX) < Magic.NEGLIGIBLE_SPEED_THRESHOLD) {
                resultX = 0.0;
            }
            if (Math.abs(resultZ) < Magic.NEGLIGIBLE_SPEED_THRESHOLD) {
                resultZ = 0.0;
            }
        } else {
            if (Math.abs(resultX) < Magic.NEGLIGIBLE_SPEED_THRESHOLD_LEGACY) {
                resultX = 0.0;
            }
            if (Math.abs(resultZ) < Magic.NEGLIGIBLE_SPEED_THRESHOLD_LEGACY) {
                resultZ = 0.0;
            }
        }

        return new double[] {resultX, resultZ};
    }
}
