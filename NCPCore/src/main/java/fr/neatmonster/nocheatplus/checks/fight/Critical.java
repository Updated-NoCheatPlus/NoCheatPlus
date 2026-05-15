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
package fr.neatmonster.nocheatplus.checks.fight;

import java.util.ArrayList;
import java.util.List;

import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.potion.PotionEffectType;

import fr.neatmonster.nocheatplus.NCPAPIProvider;
import fr.neatmonster.nocheatplus.actions.ParameterName;
import fr.neatmonster.nocheatplus.checks.Check;
import fr.neatmonster.nocheatplus.checks.CheckType;
import fr.neatmonster.nocheatplus.checks.ViolationData;
import fr.neatmonster.nocheatplus.checks.moving.MovingConfig;
import fr.neatmonster.nocheatplus.checks.moving.MovingData;
import fr.neatmonster.nocheatplus.checks.moving.model.PlayerMoveData;
import fr.neatmonster.nocheatplus.checks.moving.model.PlayerMoveInfo;
import fr.neatmonster.nocheatplus.checks.moving.velocity.VelocityFlags;
import fr.neatmonster.nocheatplus.compat.Bridge1_13;
import fr.neatmonster.nocheatplus.compat.versions.ClientVersion;
import fr.neatmonster.nocheatplus.penalties.IPenaltyList;
import fr.neatmonster.nocheatplus.players.IPlayerData;
import fr.neatmonster.nocheatplus.utilities.CheckUtils;
import fr.neatmonster.nocheatplus.utilities.StringUtil;
import fr.neatmonster.nocheatplus.utilities.map.BlockFlags;
import fr.neatmonster.nocheatplus.utilities.moving.AuxMoving;
import fr.neatmonster.nocheatplus.utilities.moving.MovingUtil;

/**
 * A check used to verify that critical hits done by players are legit.
 */
public class Critical extends Check {

    // Modern-client model: tiny fall-distance desync can happen during valid low-fall attacks.
    private static final double MODERN_JUMP_PHASE_CRITICAL_FALL_GRACE = 0.90D;

    private final AuxMoving auxMoving = NCPAPIProvider.getNoCheatPlusAPI().getGenericInstance(AuxMoving.class);

    /**
     * Instantiates a new critical check.
     */
    public Critical() {
        super(CheckType.FIGHT_CRITICAL);
    }
    
    /**
     * Checks a player.
     * 
     * @param player
     * @param loc
     * @param data
     * @param cc
     * @param pData
     * @param penaltyList
     * @return true, if successful
     */
    public boolean check(final Player player, final Location loc, final FightData data, final FightConfig cc, 
                         final IPlayerData pData, final IPenaltyList penaltyList) {
        boolean cancel = false;
        final List<String> tags = new ArrayList<String>();
        final MovingData mData = pData.getGenericInstance(MovingData.class);
        final MovingConfig mCC = pData.getGenericInstance(MovingConfig.class);
        final PlayerMoveData thisMove = mData.playerMoves.getCurrentMove();
        final PlayerMoveData lastMove = mData.playerMoves.getFirstPastMove();
        final double mcFallDistance = (double) player.getFallDistance();
        final double ncpFallDistance = mData.noFallFallDistance;
        final double realisticFallDistance = MovingUtil.getRealisticFallDistance(player, thisMove.from.getY(), thisMove.to.getY(), mData, pData);
        final PlayerMoveInfo moveInfo = auxMoving.usePlayerMoveInfo();
        moveInfo.set(player, loc, null, mCC.yOnGround);
        
        // Check if the hit was a critical hit (fall distance is present, player is not on a ladder, not in vehicle, and without blindness effect).
        if (mcFallDistance > 0.0 && !player.isInsideVehicle() && !player.hasPotionEffect(PotionEffectType.BLINDNESS)) {

            if (pData.isDebugActive(type)) {
                debug(player, 
                    "Fall distances: MC(" + StringUtil.fdec3.format(mcFallDistance) +") | NCP("+ StringUtil.fdec3.format(ncpFallDistance) +") | R("+ StringUtil.fdec3.format(realisticFallDistance) +")"
                    + "\nfD diff: " + StringUtil.fdec3.format(Math.abs(ncpFallDistance - mcFallDistance))
                    + "\nJumpPhase: " + mData.sfJumpPhase + " | NCP onGround: " + (thisMove.from.onGround ? "ground -> " : "--- -> ") + (thisMove.to.onGround ? "ground" : "---") + " | MC onGround: " + player.isOnGround()
                ); // + ", packet onGround: " + packet.onGround); 
            }
            
            // Check for skipping conditions first.
            moveInfo.from.collectBlockFlags(0.4);
            // False positives with medium counts reset all nofall data when nearby boat
            // TODO: Fix isOnGroundDueToStandingOnAnEntity() to work on entity not nearby
            if (moveInfo.from.isOnGroundDueToStandingOnAnEntity()
                // Edge case with slime blocks
                || (moveInfo.from.getBlockFlags() & BlockFlags.F_BOUNCE25) != 0 && !moveInfo.from.isOnGround() && !moveInfo.to.isOnGround()) {
                auxMoving.returnPlayerMoveInfo(moveInfo);
                return false;
            }
            
            final boolean resetCond = moveInfo.from.isResetCond();
            final boolean slowFalling = !Double.isInfinite(Bridge1_13.getSlowfallingAmplifier(player));
            final boolean jumpPhaseCritical = mData.sfJumpPhase > 0
                    && mData.sfJumpPhase <= mData.liftOffEnvelope.getMaxJumpPhase(mData.jumpAmplifier)
                    && !moveInfo.from.seekCollisionAbove(0.2)
                    && (lastMove.verVelUsed.isEmpty() || !lastMove.verVelUsed.get(0).hasFlag(VelocityFlags.ORIGIN_BLOCK_BOUNCE))
                    && !isModernJumpPhaseCriticalGrace(player, pData, mcFallDistance, realisticFallDistance,
                            mData, thisMove, lastMove, moveInfo);
            final boolean groundMismatch = Math.abs(ncpFallDistance - mcFallDistance) > 1e-5
                    && (moveInfo.from.isOnGround() || lastMove.touchedGroundWorkaround);
            boolean isIllegal =
                       // 0: Don't allow players to perform critical hits in blocks where the game would reset fall distance (water, powder snow, bushes, webs, climbables)
                       resetCond
                       // 0: Same as above. The game resets fall distance with slowfall
                       || slowFalling
                       // 0: A full jump from ground requires more than 6 phases/events.
                       || jumpPhaseCritical
                       // 0: Always invalidate critical hits if we judge the player to be on ground (given enough fall distance)
                       || groundMismatch
                       // (Let SurvivalFly catch low-jumps).
            ;

            // Handle violations
            if (isIllegal) {
                addCriticalTags(tags, resetCond, slowFalling, jumpPhaseCritical, groundMismatch, lastMove, moveInfo);
                data.criticalVL += 1.0;
                // Execute whatever actions are associated with this check and
                //  the violation level and find out if we should cancel the event.
                final ViolationData vd = new ViolationData(this, player, data.criticalVL, 1.0, cc.criticalActions);
                if (vd.needsParameters()) vd.setParameter(ParameterName.TAGS, StringUtil.join(tags, "+"));
                if (CheckUtils.shouldLogDebugToConsole()) {
                    logCriticalDetail(player, loc, data.criticalVL, tags, mcFallDistance, ncpFallDistance,
                            realisticFallDistance, mData, thisMove, lastMove, moveInfo);
                }
                cancel = executeActions(vd).willCancel();
                // TODO: Introduce penalty instead of cancel.
            }
            // Crit was legit, reward the player.
            else data.criticalVL *= 0.96D;
        }
        auxMoving.returnPlayerMoveInfo(moveInfo);
        return cancel;
    }

    private void addCriticalTags(final List<String> tags, final boolean resetCond, final boolean slowFalling,
                                 final boolean jumpPhaseCritical, final boolean groundMismatch,
                                 final PlayerMoveData lastMove, final PlayerMoveInfo moveInfo) {
        // Diagnostic info: name the exact Critical branch instead of only reporting FIGHT_CRITICAL.
        tags.add("subcheck_" + getCriticalSubCheck(resetCond, slowFalling, jumpPhaseCritical, groundMismatch).toLowerCase());
        if (resetCond) {
            tags.add("branch_resetcond");
        }
        if (slowFalling) {
            tags.add("branch_slowfall");
        }
        if (jumpPhaseCritical) {
            tags.add("branch_jump_phase");
        }
        if (groundMismatch) {
            tags.add("branch_ground_mismatch");
        }
        if (lastMove.touchedGroundWorkaround) {
            tags.add("branch_lostground");
        }
        if (!lastMove.verVelUsed.isEmpty()) {
            tags.add("branch_velocity");
        }
        if (moveInfo.from.isInLiquid() || moveInfo.to.isInLiquid()) {
            tags.add("branch_liquid");
        }
    }

    private String getCriticalSubCheck(final boolean resetCond, final boolean slowFalling,
                                       final boolean jumpPhaseCritical, final boolean groundMismatch) {
        if (resetCond) {
            return "CRITICAL_RESETCOND";
        }
        if (slowFalling) {
            return "CRITICAL_SLOWFALL";
        }
        if (jumpPhaseCritical) {
            return "CRITICAL_JUMP_PHASE";
        }
        if (groundMismatch) {
            return "CRITICAL_GROUND_MISMATCH";
        }
        return "CRITICAL_UNKNOWN";
    }

    private boolean isModernJumpPhaseCriticalGrace(final Player player, final IPlayerData pData,
                                                   final double mcFallDistance, final double realisticFallDistance,
                                                   final MovingData mData, final PlayerMoveData thisMove,
                                                   final PlayerMoveData lastMove,
                                                   final PlayerMoveInfo moveInfo) {
        // False-positive model: 1.21+ clients can report valid low-fall attacks inside the old jump-phase window.
        // This stays a bounded desync model because Bukkit fall distance and NCP movement history update separately.
        if (pData.getClientVersion() != ClientVersion.HIGHER_THAN_KNOWN_VERSIONS
                && !pData.getClientVersion().isAtLeast(ClientVersion.V_1_21)) {
            return false;
        }
        if (mcFallDistance > MODERN_JUMP_PHASE_CRITICAL_FALL_GRACE
                || moveInfo.from.isResetCond()
                || moveInfo.to.isResetCond()
                || !lastMove.verVelUsed.isEmpty() && lastMove.verVelUsed.get(0).hasFlag(VelocityFlags.ORIGIN_BLOCK_BOUNCE)) {
            return false;
        }
        final boolean lowFallDesync = player.isOnGround()
                || thisMove.from.onGround
                || thisMove.to.onGround
                || realisticFallDistance > 0.0D
                || thisMove.yDistance < 0.0D
                || lastMove.yDistance < 0.0D;
        return lowFallDesync && mData.sfJumpPhase > 0
                && mData.sfJumpPhase <= mData.liftOffEnvelope.getMaxJumpPhase(mData.jumpAmplifier);
    }

    private void logCriticalDetail(final Player player, final Location loc, final double totalVL,
                                   final List<String> tags, final double mcFallDistance,
                                   final double ncpFallDistance, final double realisticFallDistance,
                                   final MovingData mData, final PlayerMoveData thisMove,
                                   final PlayerMoveData lastMove, final PlayerMoveInfo moveInfo) {
        try {
            // Diagnostic info: console-only Critical context for fall distance, ground mismatch, and liquid/reset false flags.
            player.getServer().getLogger().info(new StringBuilder(420)
                    .append("[NCP][FightCritical][detail] player=").append(player.getName())
                    .append(" uuid=").append(player.getUniqueId())
                    .append(" subcheck=").append(tags.isEmpty() ? "CRITICAL_UNKNOWN" : tags.get(0).substring("subcheck_".length()).toUpperCase())
                    .append(" summary=").append(tags.isEmpty() ? "critical_unknown" : tags.get(0).substring("subcheck_".length()))
                    .append("{mcFall=").append(StringUtil.fdec3.format(mcFallDistance))
                    .append(",ncpFall=").append(StringUtil.fdec3.format(ncpFallDistance))
                    .append(",realistic=").append(StringUtil.fdec3.format(realisticFallDistance))
                    .append(",jumpPhase=").append(mData.sfJumpPhase)
                    .append('}')
                    .append(" totalVL=").append(StringUtil.fdec3.format(totalVL))
                    .append(" mcFall=").append(StringUtil.fdec3.format(mcFallDistance))
                    .append(" ncpFall=").append(StringUtil.fdec3.format(ncpFallDistance))
                    .append(" realisticFall=").append(StringUtil.fdec3.format(realisticFallDistance))
                    .append(" jumpPhase=").append(mData.sfJumpPhase)
                    .append(" liftOff=").append(mData.liftOffEnvelope.name())
                    .append(" playerGround=").append(player.isOnGround())
                    .append(" moveGround=").append(thisMove.from.onGround).append("->").append(thisMove.to.onGround)
                    .append(" infoGround=").append(moveInfo.from.isOnGround()).append("->").append(moveInfo.to.isOnGround())
                    .append(" reset=").append(moveInfo.from.isResetCond()).append("->").append(moveInfo.to.isResetCond())
                    .append(" lastTouchedGround=").append(lastMove.touchedGroundWorkaround)
                    .append(" vVelUsed=").append(lastMove.verVelUsed)
                    .append(" loc=").append(formatLocation(loc))
                    .append(" tags=").append(StringUtil.join(tags, "+"))
                    .toString());
        }
        catch (Throwable ignored) {}
    }

    private String formatLocation(final Location location) {
        return location == null ? "none"
                : StringUtil.fdec3.format(location.getX()) + ","
                + StringUtil.fdec3.format(location.getY()) + ","
                + StringUtil.fdec3.format(location.getZ()) + "/"
                + StringUtil.fdec3.format(location.getYaw()) + ","
                + StringUtil.fdec3.format(location.getPitch());
    }
}
