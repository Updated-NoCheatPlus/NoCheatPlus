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

import java.util.Locale;

import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.util.Vector;

import fr.neatmonster.nocheatplus.checks.moving.MovingData;
import fr.neatmonster.nocheatplus.checks.moving.model.PlayerMoveData;
import fr.neatmonster.nocheatplus.compat.Bridge1_9;
import fr.neatmonster.nocheatplus.players.IPlayerData;
import fr.neatmonster.nocheatplus.utilities.StringUtil;
import fr.neatmonster.nocheatplus.utilities.location.PlayerLocation;
import fr.neatmonster.nocheatplus.utilities.math.MathUtil;
import fr.neatmonster.nocheatplus.utilities.math.TrigUtil;
import fr.neatmonster.nocheatplus.utilities.moving.Magic;

/**
 * Formatting-only helpers for SurvivalFly compatibility diagnostics.
 * Keep model math in SurvivalFly; this class only turns already-computed state
 * into readable console output.
 */
final class SurvivalFlyDiagnostics {

    private SurvivalFlyDiagnostics() {}

    static String formatReadableDebugSummary(final String subcheck, final String movementMode,
                                             final String modelBranch, final String axis,
                                             final double hDistanceAboveLimit,
                                             final double yDistanceAboveLimit,
                                             final Iterable<String> tags) {
        return subcheck.toLowerCase(Locale.ROOT)
                + "{mode=" + movementMode.toLowerCase(Locale.ROOT)
                + ",model=" + modelBranch
                + ",axis=" + axis.toLowerCase(Locale.ROOT)
                + ",hOver=" + StringUtil.fdec6.format(Math.max(hDistanceAboveLimit, 0.0D))
                + ",yOver=" + StringUtil.fdec6.format(Math.max(yDistanceAboveLimit, 0.0D))
                + ",tags=" + formatPrimaryReadableTags(tags)
                + "}";
    }

    private static String formatPrimaryReadableTags(final Iterable<String> tags) {
        final StringBuilder builder = new StringBuilder(120);
        int count = 0;
        for (final String tag : tags) {
            if (tag.startsWith("subcheck_") || tag.startsWith("branch_")
                    || tag.startsWith("model_") || tag.startsWith("diag_")) {
                if (count > 0) {
                    builder.append('+');
                }
                builder.append(tag);
                if (++count == 5) {
                    break;
                }
            }
        }
        return builder.length() == 0 ? "none" : builder.toString();
    }

    static String formatMovementMode(final Player player, final MovingData data,
                                     final PlayerLocation from, final PlayerLocation to,
                                     final PlayerMoveData move,
                                     final boolean fromOnGround, final boolean resetFrom,
                                     final boolean toOnGround, final boolean resetTo) {
        if (player.isInsideVehicle()) {
            return player.getVehicle() == null ? "VEHICLE" : "VEHICLE_" + player.getVehicle().getType().name();
        }
        if (Bridge1_9.isGliding(player)) {
            return data.fireworksBoostDuration > 0 ? "ELYTRA_FIREWORK" : "ELYTRA_GLIDING";
        }
        if (from.isInWater() || to.isInWater() || move.from.inWater || move.to.inWater) {
            return "WATER";
        }
        if (from.isInLava() || to.isInLava() || move.from.inLava || move.to.inLava) {
            return "LAVA";
        }
        if (from.isOnClimbable() || to.isOnClimbable() || move.from.onClimbable || move.to.onClimbable) {
            return "CLIMBABLE";
        }
        if (Bridge1_9.isWearingElytra(player)) {
            return fromOnGround || resetFrom || toOnGround || resetTo ? "ELYTRA_EQUIPPED_GROUND" : "ELYTRA_EQUIPPED_AIR";
        }
        if (from.isInWeb() || to.isInWeb() || move.from.inWeb || move.to.inWeb) {
            return "WEB";
        }
        if (from.isInBerryBush() || to.isInBerryBush() || move.from.inBerryBush || move.to.inBerryBush) {
            return "BERRY_BUSH";
        }
        if (from.isInPowderSnow() || to.isInPowderSnow() || move.from.inPowderSnow || move.to.inPowderSnow) {
            return "POWDER_SNOW";
        }
        if (fromOnGround || resetFrom || toOnGround || resetTo || move.from.onGroundOrResetCond || move.to.onGroundOrResetCond) {
            return "GROUND";
        }
        return "AIR";
    }

    static String formatCompactModelProbe(final String branchTag, final String axis,
                                          final double hDistanceAboveLimit,
                                          final double yDistanceAboveLimit,
                                          final boolean lastMoveValid,
                                          final Vector velocity,
                                          final boolean queuedHorizontalVelocity,
                                          final boolean usedVerticalVelocity,
                                          final boolean partialSupport,
                                          final String partialSupportTag,
                                          final double partialHorizontalLimit,
                                          final double partialVerticalLimit,
                                          final double partialVerticalClamp,
                                          final boolean jumpProbe,
                                          final boolean isJump,
                                          final boolean isStepUp,
                                          final boolean couldStepUp,
                                          final int jumpPhase) {
        // Diagnostic info: compact branch context for future false-positive reports without the old full environment dump.
        final StringBuilder builder = new StringBuilder(260);
        builder.append("branch=").append(branchTag)
                .append(",axis=").append(axis)
                .append(",hOver=").append(StringUtil.fdec6.format(Math.max(hDistanceAboveLimit, 0.0D)))
                .append(",yOver=").append(StringUtil.fdec6.format(Math.max(yDistanceAboveLimit, 0.0D)))
                .append(",last=").append(lastMoveValid ? "valid" : "invalid")
                .append(",vel=").append(formatVector(velocity))
                .append(",queuedH=").append(queuedHorizontalVelocity)
                .append(",usedY=").append(usedVerticalVelocity);
        if (partialSupport) {
            builder.append(",partial=").append(partialSupportTag)
                    .append(':').append(StringUtil.fdec6.format(partialHorizontalLimit))
                    .append('/').append(StringUtil.fdec6.format(partialVerticalLimit))
                    .append("/clamp=").append(StringUtil.fdec6.format(partialVerticalClamp));
        }
        if (jumpProbe) {
            builder.append(",jump=").append(isJump)
                    .append('/').append(isStepUp)
                    .append('/').append(couldStepUp)
                    .append(",phase=").append(jumpPhase);
        }
        if (!lastMoveValid) {
            builder.append(",resync=last-invalid");
        }
        return builder.toString();
    }

    static String formatDetail(final Player player, final PlayerLocation from, final PlayerLocation to,
                               final Location setback, final MovingData data,
                               final IPlayerData pData, final PlayerMoveData move, final PlayerMoveData lastMove,
                               final boolean bedrock, final String movementClient, final String movementMode,
                               final String subcheck, final String summary, final double result,
                               final double hAllowedDistance, final double hDistanceAboveLimit,
                               final double yAllowedDistance, final double yDistanceAboveLimit, final boolean fromOnGround,
                               final boolean resetFrom, final boolean toOnGround, final boolean resetTo,
                               final int tick, final long now, final int multiMoveCount,
                               final boolean packetSplit, final String modelProbe,
                               final String elytraModel, final String tags) {
        final StringBuilder builder = new StringBuilder(1000);
        builder.append("[NCP][SurvivalFly][detail] player=").append(player.getName())
                .append(" bedrock=").append(bedrock)
                .append(" bedrockData=").append(pData.isBedrockPlayer())
                .append(" uuid=").append(player.getUniqueId())
                .append(" client=").append(pData.getClientVersion())
                .append(" movementClient=").append(movementClient)
                .append(" tick=").append(tick)
                .append(" now=").append(now)
                .append(" moveCount=").append(data.getPlayerMoveCount())
                .append(" multiMove=").append(multiMoveCount)
                .append(" packetSplit=").append(packetSplit)
                .append(" movementMode=").append(movementMode)
                .append(" subcheck=").append(subcheck)
                .append(" summary=").append(summary)
                .append(tags.contains(SurvivalFlyTags.ELYTRA_MODEL_DATA_ONLY) ? " dataOnlyVL=" : " addVL=")
                .append(StringUtil.fdec3.format(result))
                .append(" totalVL=").append(StringUtil.fdec3.format(data.survivalFlyVL))
                .append(" setback=").append(formatBukkitLocation(setback))
                .append(" from=").append(formatLocation(from))
                .append(" to=").append(formatLocation(to))
                .append(" ground=").append(fromOnGround ? "on" : resetFrom ? "reset" : "air")
                .append("->").append(toOnGround ? "on" : resetTo ? "reset" : "air")
                .append(" playerState=sprint:").append(player.isSprinting())
                .append(",sneak:").append(player.isSneaking())
                .append(",fly:").append(player.isFlying())
                .append(",allowFlight:").append(player.getAllowFlight())
                .append(",vehicle:").append(player.isInsideVehicle())
                .append(",gliding:").append(Bridge1_9.isGliding(player))
                .append(",elytra:").append(Bridge1_9.isWearingElytra(player))
                .append(",walkSpeed:").append(StringUtil.fdec3.format(player.getWalkSpeed()))
                .append(" medium=water:").append(StringUtil.fdec3.format(move.submergedWaterHeight))
                .append(",lava:").append(StringUtil.fdec3.format(move.submergedLavaHeight))
                .append(",friction:").append(StringUtil.fdec3.format(data.lastFrictionHorizontal)).append("->").append(StringUtil.fdec3.format(data.nextFrictionHorizontal))
                .append(",stuckH:").append(StringUtil.fdec3.format(data.lastStuckInBlockHorizontal)).append("->").append(StringUtil.fdec3.format(data.nextStuckInBlockHorizontal))
                .append(" speedData=walk:").append(StringUtil.fdec3.format(data.walkSpeed)).append("->").append(StringUtil.fdec3.format(data.nextWalkSpeed))
                .append(",speedTick:").append(data.speedTick)
                .append(",jumpDelay:").append(data.jumpDelay)
                .append(",fireworkBoost:").append(data.fireworksBoostDuration)
                .append(",setbackAge:").append(data.timeSinceSetBack)
                .append(" jumpPhase=").append(data.sfJumpPhase)
                .append(" liftOff=").append(data.liftOffEnvelope.name())
                .append(" lastValid=").append(lastMove.toIsValid)
                .append(" movementModel=").append(formatMovementModel(move, hAllowedDistance, hDistanceAboveLimit, yAllowedDistance, yDistanceAboveLimit))
                .append(" physicsModel=").append(formatPhysicsModel(player, data, move, lastMove, hAllowedDistance, yAllowedDistance))
                .append(" flightTrace=").append(formatFlightTrace(player, data, move, lastMove, movementMode, from, to))
                .append(" modelProbe=").append(modelProbe)
                .append(" elytraModel=").append(elytraModel)
                .append(" tags=").append(tags);
        return builder.toString();
    }

    static String formatMovementModel(final PlayerMoveData move,
                                      final double hAllowedDistance, final double hDistanceAboveLimit,
                                      final double yAllowedDistance, final double yDistanceAboveLimit) {
        final double xOver = move.xDistance - move.xAllowedDistance;
        final double yOver = move.yDistance - yAllowedDistance;
        final double zOver = move.zDistance - move.zAllowedDistance;
        final double hOverRaw = move.hDistance - hAllowedDistance;
        final StringBuilder builder = new StringBuilder(320);
        builder.append("actual=").append(formatVector(move.xDistance, move.yDistance, move.zDistance))
                .append(",allowed=").append(formatVector(move.xAllowedDistance, yAllowedDistance, move.zAllowedDistance))
                .append(",overVector=").append(formatVector(xOver, yOver, zOver))
                .append(",hOverRaw=").append(StringUtil.fdec6.format(hOverRaw))
                .append(",hOverApplied=").append(StringUtil.fdec6.format(Math.max(hDistanceAboveLimit, 0.0)))
                .append(",yOverRaw=").append(StringUtil.fdec6.format(yOver))
                .append(",yOverApplied=").append(StringUtil.fdec6.format(Math.max(yDistanceAboveLimit, 0.0)))
                .append(",ratio=h:").append(formatRatio(move.hDistance, hAllowedDistance))
                .append(",x:").append(formatRatio(Math.abs(move.xDistance), Math.abs(move.xAllowedDistance)))
                .append(",y:").append(formatRatio(Math.abs(move.yDistance), Math.abs(yAllowedDistance)))
                .append(",z:").append(formatRatio(Math.abs(move.zDistance), Math.abs(move.zAllowedDistance)))
                .append(",distSq=").append(StringUtil.fdec6.format(move.distanceSquared))
                .append(",modelFlying=").append(move.modelFlying == null ? "none" : move.modelFlying.getId())
                .append(",flyCheck=").append(move.flyCheck == null ? "none" : move.flyCheck.name());
        return builder.toString();
    }

    static String formatPhysicsModel(final Player player, final MovingData data,
                                     final PlayerMoveData move, final PlayerMoveData lastMove,
                                     final double hAllowedDistance, final double yAllowedDistance) {
        final Vector velocity = player.getVelocity();
        final double velocityH = MathUtil.dist(velocity.getX(), velocity.getZ());
        final double actualMinusVelocityH = MathUtil.dist(move.xDistance - velocity.getX(), move.zDistance - velocity.getZ());
        final double allowedMinusVelocityH = MathUtil.dist(move.xAllowedDistance - velocity.getX(), move.zAllowedDistance - velocity.getZ());
        final boolean air = !move.from.onGroundOrResetCond && !move.to.onGroundOrResetCond;
        final boolean liquid = move.from.inLiquid || move.to.inLiquid || move.submergedWaterHeight > 0.0D || move.submergedLavaHeight > 0.0D;
        final boolean climbable = move.from.onClimbable || move.to.onClimbable;
        final double gravity = data.lastGravity > 0.0D ? data.lastGravity : Magic.DEFAULT_GRAVITY;
        final double frictionY = data.lastFrictionVertical != 0.0D ? data.lastFrictionVertical : Magic.FRICTION_MEDIUM_AIR;
        final double gravityNextY = lastMove.toIsValid ? (lastMove.yDistance - gravity) * frictionY : Double.NaN;
        final double actualAccelY = lastMove.toIsValid ? move.yDistance - lastMove.yDistance : Double.NaN;
        final double gravityAccelY = lastMove.toIsValid ? gravityNextY - lastMove.yDistance : Double.NaN;
        final double actualMinusGravityY = lastMove.toIsValid ? move.yDistance - gravityNextY : Double.NaN;
        final StringBuilder builder = new StringBuilder(420);
        builder.append("state=").append(liquid ? "liquid" : climbable ? "climbable" : air ? "air" : "ground")
                .append(",gravity=").append(StringUtil.fdec6.format(gravity))
                .append(",frictionY=").append(StringUtil.fdec6.format(frictionY))
                .append(",lastY=").append(formatNumber(lastMove.toIsValid ? lastMove.yDistance : Double.NaN))
                .append(",gravityNextY=").append(formatNumber(gravityNextY))
                .append(",actualAccelY=").append(formatNumber(actualAccelY))
                .append(",gravityAccelY=").append(formatNumber(gravityAccelY))
                .append(",actualMinusGravityY=").append(formatNumber(actualMinusGravityY))
                .append(",velocityH=").append(StringUtil.fdec6.format(velocityH))
                .append(",actualHOverVelocity=").append(formatRatio(move.hDistance, velocityH))
                .append(",allowedHOverVelocity=").append(formatRatio(hAllowedDistance, velocityH))
                .append(",actualMinusVelocityH=").append(StringUtil.fdec6.format(actualMinusVelocityH))
                .append(",allowedMinusVelocityH=").append(StringUtil.fdec6.format(allowedMinusVelocityH))
                .append(",actualYMinusVelocity=").append(StringUtil.fdec6.format(move.yDistance - velocity.getY()))
                .append(",allowedYMinusVelocity=").append(StringUtil.fdec6.format(yAllowedDistance - velocity.getY()))
                .append(",glideRatio=actual:").append(formatRatio(move.hDistance, Math.abs(move.yDistance)))
                .append(",allowed:").append(formatRatio(hAllowedDistance, Math.abs(yAllowedDistance)));
        return builder.toString();
    }

    static String formatFlightTrace(final Player player, final MovingData data,
                                    final PlayerMoveData move, final PlayerMoveData lastMove,
                                    final String movementMode, final PlayerLocation from,
                                    final PlayerLocation to) {
        /*
         * Diagnostic info: this compact trace is meant for comparing labeled
         * legit/hacked test windows in the server log without adding more model
         * logic to SurvivalFly itself.
         */
        final Vector velocity = player.getVelocity();
        final double velocityH = MathUtil.dist(velocity.getX(), velocity.getZ());
        final double packetVelocityDx = move.xDistance - velocity.getX();
        final double packetVelocityDy = move.yDistance - velocity.getY();
        final double packetVelocityDz = move.zDistance - velocity.getZ();
        final double lastY = lastMove.toIsValid ? lastMove.yDistance : Double.NaN;
        final double lastH = lastMove.toIsValid ? lastMove.hDistance : Double.NaN;
        final double gravity = data.lastGravity > 0.0D ? data.lastGravity : Magic.DEFAULT_GRAVITY;
        final double frictionY = data.lastFrictionVertical != 0.0D ? data.lastFrictionVertical : Magic.FRICTION_MEDIUM_AIR;
        final double gravityNextY = lastMove.toIsValid ? (lastMove.yDistance - gravity) * frictionY : Double.NaN;
        final double yAccel = lastMove.toIsValid ? move.yDistance - lastMove.yDistance : Double.NaN;
        final double hAccel = lastMove.toIsValid ? move.hDistance - lastMove.hDistance : Double.NaN;
        final double gravityMiss = lastMove.toIsValid ? move.yDistance - gravityNextY : Double.NaN;
        final double liftExtraY = lastMove.toIsValid ? Math.max(0.0D, gravityMiss) : Double.NaN;
        final double hoverDropDeficit = Math.max(0.0D, data.hoverExpectedDrop - data.hoverActualDrop);
        final Vector look = TrigUtil.getLookingDirection(to, player);
        final double yawDelta = getYawDelta(from.getYaw(), to.getYaw());
        final StringBuilder builder = new StringBuilder(420);
        builder.append("mode=").append(movementMode)
                .append(",trend=").append(formatTrend(move.yDistance))
                .append(",lastTrend=").append(formatTrend(lastY))
                .append(",pitch=").append(StringUtil.fdec3.format(from.getPitch())).append("->").append(StringUtil.fdec3.format(to.getPitch()))
                .append(",pitchDelta=").append(StringUtil.fdec3.format(to.getPitch() - from.getPitch()))
                .append(",pitchBand=").append(formatPitchBand(to.getPitch()))
                .append(",yaw=").append(StringUtil.fdec3.format(from.getYaw())).append("->").append(StringUtil.fdec3.format(to.getYaw()))
                .append(",yawDelta=").append(StringUtil.fdec3.format(yawDelta))
                .append(",lookVector=").append(formatVector(look))
                .append(",lookH=").append(StringUtil.fdec6.format(MathUtil.dist(look.getX(), look.getZ())))
                .append(",lookY=").append(StringUtil.fdec6.format(look.getY()))
                .append(",h=").append(StringUtil.fdec6.format(move.hDistance))
                .append(",lastH=").append(formatNumber(lastH))
                .append(",hDelta=").append(formatNumber(hAccel))
                .append(",y=").append(StringUtil.fdec6.format(move.yDistance))
                .append(",lastY=").append(formatNumber(lastY))
                .append(",yDelta=").append(formatNumber(yAccel))
                .append(",gravityNextY=").append(formatNumber(gravityNextY))
                .append(",gravityMiss=").append(formatNumber(gravityMiss))
                .append(",velocity=").append(formatVector(velocity))
                .append(",velocityH=").append(StringUtil.fdec6.format(velocityH))
                .append(",packetMinusVelocity=").append(formatVector(packetVelocityDx, packetVelocityDy, packetVelocityDz))
                .append(",lift=extraY:").append(formatNumber(liftExtraY))
                .append(",perVelocityH:").append(formatRatio(liftExtraY, velocityH))
                .append(",glideRatio=").append(formatRatio(move.hDistance, Math.abs(move.yDistance)))
                .append(",firework=").append(data.fireworksBoostDuration)
                .append(",noFireworkAscent=ticks:").append(data.elytraNoFireworkAscentTicks)
                .append(",debt:").append(StringUtil.fdec6.format(data.elytraNoFireworkAscentDebt))
                .append(",budget:").append(formatNumber(data.elytraNoFireworkAscentBudget))
                .append(",excess:").append(formatNumber(data.elytraNoFireworkAscentExcess))
                .append(",neededH:").append(formatNumber(data.elytraNoFireworkNeededH))
                .append(",startY:").append(data.elytraNoFireworkStart == null
                        ? "na" : formatNumber(data.elytraNoFireworkStart.getY()))
                .append(",descentCredit:").append(formatNumber(data.elytraNoFireworkDescentCredit))
                .append(",creditUsed:").append(formatNumber(data.elytraNoFireworkDescentCreditUsed))
                .append(",hover=ticks:").append(data.hoverAirTicks)
                .append(",expectedDrop:").append(StringUtil.fdec6.format(data.hoverExpectedDrop))
                .append(",actualDrop:").append(StringUtil.fdec6.format(data.hoverActualDrop))
                .append(",dropDeficit:").append(StringUtil.fdec6.format(hoverDropDeficit))
                .append(",lastYVel:").append(StringUtil.fdec6.format(data.hoverLastYVelocity));
        return builder.toString();
    }

    static String formatElytraModel(final Player player, final PlayerLocation from, final PlayerLocation to,
                                    final MovingData data, final PlayerMoveData move,
                                    final double hAllowedDistance, final double yAllowedDistance,
                                    final String pitchBand, final double yawDelta) {
        if (!Bridge1_9.isGliding(player) && !Bridge1_9.isWearingElytra(player)) {
            return "none";
        }
        final Vector look = TrigUtil.getLookingDirection(to, player);
        final Vector velocity = player.getVelocity();
        final double actualVelocityDx = move.xDistance - velocity.getX();
        final double actualVelocityDy = move.yDistance - velocity.getY();
        final double actualVelocityDz = move.zDistance - velocity.getZ();
        final double allowedVelocityDx = move.xAllowedDistance - velocity.getX();
        final double allowedVelocityDy = yAllowedDistance - velocity.getY();
        final double allowedVelocityDz = move.zAllowedDistance - velocity.getZ();
        final StringBuilder builder = new StringBuilder(420);
        builder.append("pitch=").append(StringUtil.fdec3.format(from.getPitch())).append("->").append(StringUtil.fdec3.format(to.getPitch()))
                .append(",pitchDelta=").append(StringUtil.fdec3.format(to.getPitch() - from.getPitch()))
                .append(",pitchBand=").append(pitchBand)
                .append(",yaw=").append(StringUtil.fdec3.format(from.getYaw())).append("->").append(StringUtil.fdec3.format(to.getYaw()))
                .append(",yawDelta=").append(StringUtil.fdec3.format(yawDelta))
                .append(",look=").append(formatVector(look))
                .append(",lookH=").append(StringUtil.fdec6.format(MathUtil.dist(look.getX(), look.getZ())))
                .append(",lookY=").append(StringUtil.fdec6.format(look.getY()))
                .append(",firework=").append(data.fireworksBoostDuration)
                .append(",actualH=").append(StringUtil.fdec6.format(move.hDistance))
                .append(",allowedH=").append(StringUtil.fdec6.format(hAllowedDistance))
                .append(",actualY=").append(StringUtil.fdec6.format(move.yDistance))
                .append(",allowedY=").append(StringUtil.fdec6.format(yAllowedDistance))
                .append(",velocity=").append(formatVector(velocity))
                .append(",actualMinusVelocity=").append(formatVector(actualVelocityDx, actualVelocityDy, actualVelocityDz))
                .append(",allowedMinusVelocity=").append(formatVector(allowedVelocityDx, allowedVelocityDy, allowedVelocityDz));
        return builder.toString();
    }

    static String formatRatio(final double actual, final double allowed) {
        if (Math.abs(allowed) < 1.0E-9) {
            return Math.abs(actual) < 1.0E-9 ? "1.000" : "inf";
        }
        return StringUtil.fdec3.format(actual / allowed);
    }

    private static String formatNumber(final double value) {
        if (Double.isNaN(value)) {
            return "na";
        }
        return Double.isInfinite(value) ? "inf" : StringUtil.fdec6.format(value);
    }

    private static String formatTrend(final double yDistance) {
        if (Double.isNaN(yDistance)) {
            return "na";
        }
        if (yDistance > Magic.PREDICTION_EPSILON) {
            return "ascend";
        }
        if (yDistance < -Magic.PREDICTION_EPSILON) {
            return "descend";
        }
        return "level";
    }

    private static String formatPitchBand(final float pitch) {
        if (pitch <= -15.0f) {
            return "UP_STEEP";
        }
        if (pitch <= -5.0f) {
            return "UP";
        }
        if (pitch < 5.0f) {
            return "LEVEL";
        }
        if (pitch < 30.0f) {
            return "DOWN";
        }
        return "DOWN_STEEP";
    }

    private static double getYawDelta(final float fromYaw, final float toYaw) {
        double delta = toYaw - fromYaw;
        while (delta > 180.0D) {
            delta -= 360.0D;
        }
        while (delta < -180.0D) {
            delta += 360.0D;
        }
        return delta;
    }

    static String formatVector(final Vector vector) {
        return formatVector(vector.getX(), vector.getY(), vector.getZ());
    }

    static String formatVector(final double x, final double y, final double z) {
        return StringUtil.fdec6.format(x) + "," + StringUtil.fdec6.format(y) + "," + StringUtil.fdec6.format(z);
    }

    static String formatLocation(final PlayerLocation location) {
        return String.format(Locale.US, "%.3f,%.3f,%.3f", location.getX(), location.getY(), location.getZ());
    }

    static String formatBukkitLocation(final Location location) {
        if (location == null) {
            return "none";
        }
        return String.format(Locale.US, "%s@%.3f,%.3f,%.3f/%.3f,%.3f",
                location.getWorld() == null ? "null" : location.getWorld().getName(),
                location.getX(), location.getY(), location.getZ(), location.getYaw(), location.getPitch());
    }

}
