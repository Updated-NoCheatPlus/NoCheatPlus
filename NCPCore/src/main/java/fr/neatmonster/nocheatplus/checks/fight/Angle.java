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

import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.UUID;

import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;

import fr.neatmonster.nocheatplus.actions.ParameterName;
import fr.neatmonster.nocheatplus.checks.Check;
import fr.neatmonster.nocheatplus.checks.CheckType;
import fr.neatmonster.nocheatplus.checks.ViolationData;
import fr.neatmonster.nocheatplus.permissions.Permissions;
import fr.neatmonster.nocheatplus.players.IPlayerData;
import fr.neatmonster.nocheatplus.utilities.StringUtil;
import fr.neatmonster.nocheatplus.utilities.TickTask;
import fr.neatmonster.nocheatplus.utilities.math.TrigUtil;

/**
 * A check used to verify if the player isn't using a forcefield in order to attack multiple entities at the same time.
 * 
 * Thanks @asofold for the original idea!
 */
public class Angle extends Check {
  

  private final List<String> tags = new LinkedList<String>();


    public static class AttackLocation {
        public final double x, y, z;
        /** Yaw of the attacker. */
        public final float yaw;
        public long time;
        public final UUID damagedId;
        /** Squared distance to the last location (0 if none given). */
        public final double distSqLast;
        /** Difference in yaw to the last location (0 if none given). */
        public final double yawDiffLast;
        /** Time difference to the last location (0 if none given). */
        public final long timeDiff;
        /** If the id differs from the last damaged entity (true if no lastLoc is given). */
        public final boolean idDiffLast;
        public AttackLocation(final Location loc, final UUID damagedId, final long time, final AttackLocation lastLoc) {
            x = loc.getX();
            y = loc.getY();
            z = loc.getZ();
            yaw = loc.getYaw();
            this.time = time;
            this.damagedId = damagedId;

            if (lastLoc != null) {
                distSqLast = TrigUtil.distanceSquared(x, y, z, lastLoc.x, lastLoc.y, lastLoc.z);
                yawDiffLast = TrigUtil.yawDiff(yaw, lastLoc.yaw);
                timeDiff = Math.max(0L, time - lastLoc.time);
                idDiffLast = !damagedId.equals(lastLoc.damagedId);
            } 
            else {
                distSqLast = 0.0;
                yawDiffLast = 0f;
                timeDiff = 0L;
                idDiffLast = true;
            }
        }
    }
    
    /** Used to also monitor lag-time */
    public static long maxTimeDiff = 1000L;


    /**
     * Instantiates a new angle check.
     */
    public Angle() {
        super(CheckType.FIGHT_ANGLE);
    }


    /**
     * The Angle check.
     * @param player
     * @param loc Location of the player.
     * @param worldChanged
     * @param data
     * @param cc
     * @return
     */
    public boolean check(final Player player, final Location loc, 
                         final Entity damagedEntity, final boolean worldChanged, 
                         final FightData data, final FightConfig cc, final IPlayerData pData) {

        if (worldChanged) {
            data.angleHits.clear();
        }

        boolean cancel = false;
        tags.clear();

        // Quick check for expiration of all entries.
        final long time = System.currentTimeMillis();
        AttackLocation lastLoc = data.angleHits.isEmpty() ? null : data.angleHits.getLast();
        if (lastLoc != null && time - lastLoc.time > maxTimeDiff) {
            data.angleHits.clear();
            lastLoc = null;
        }

        // Add the new location.
        data.angleHits.add(new AttackLocation(loc, damagedEntity.getUniqueId(), System.currentTimeMillis(), lastLoc));

        // Calculate the sums of differences.
        double deltaMove = 0D;
        long deltaTime = 0L;
        float deltaYaw = 0f;
        int deltaSwitchTarget = 0;
        final Iterator<AttackLocation> it = data.angleHits.iterator();
        while (it.hasNext()) {
            final AttackLocation refLoc = it.next();
            if (time - refLoc.time > maxTimeDiff) {
                it.remove();
                continue;
            }
            deltaMove += refLoc.distSqLast;
            final double yawDiff = Math.abs(refLoc.yawDiffLast);
            deltaYaw += yawDiff;
            deltaTime += refLoc.timeDiff;
            if (refLoc.idDiffLast && yawDiff > 30.0) {
                // TODO: Configurable sensitivity ? Scale with yawDiff?
                deltaSwitchTarget += 1;
            }
        }

        // Check if there is enough data present.
        if (data.angleHits.size() < 2) {
            return false;
        }

        final double n = (double) (data.angleHits.size() - 1);

        // Let's calculate the average move.
        double averageMove = deltaMove / n;
        // Adjust to server-sided lag
        averageMove *= TickTask.getLag(maxTimeDiff, true);

        // And the average time elapsed.
        double averageTime = (double) deltaTime / n;
        // Adjust to server-sided lag
        averageTime *= TickTask.getLag(maxTimeDiff, true);

        // And the average yaw delta.
        double averageYaw = (double) deltaYaw / n;
        // Adjust to server-sided lag
        averageYaw /= TickTask.getLag(maxTimeDiff, true);

        // Average target switching.
        double averageSwitching = (double) deltaSwitchTarget / n;
        // Adjust to server-sided lag
        averageSwitching /= TickTask.getLag(maxTimeDiff, true);

        // Declare the variables.
        double violation = 0.0;
        double violationMove = 0.0;
        double violationTime = 0.0;
        double violationYaw = 0.0;
        double violationSwitchSpeed = 0.0;

        // If the average move is between 0 and 0.2 block(s), add it to the violation.
        if (averageMove >= 0.0 && averageMove < 0.2D) {
            violationMove += 20.0 * (0.2 - averageMove) / 0.2;
            tags.add("avgmove");
            if (pData.isDebugActive(type) && pData.hasPermission(Permissions.ADMINISTRATION_DEBUG, player)) {
                player.sendMessage(ChatColor.RED + "NC+ Debug: " + ChatColor.RESET + "avgMove: " + averageMove + " avgMove VL: " + violationMove + "/" + cc.angleMove);
            }
        }

        // If the average time elapsed is between 0 and 150 millisecond(s), add it to the violation.
        if (averageTime >= 0.0 && averageTime < 150.0) {
            violationTime += 30.0 * (150.0 - averageTime) / 150.0;
            tags.add("avgtime");
            if (pData.isDebugActive(type) && pData.hasPermission(Permissions.ADMINISTRATION_DEBUG, player)) {
                player.sendMessage(ChatColor.RED + "NC+ Debug: " + ChatColor.RESET + "avgTime: " + averageTime + " avgTime VL: " + violationTime + "/" + cc.angleTime);
            }
        }

        // If the average difference of yaw is superior to 50 degrees, add it to the violation.
        if (averageYaw > 50.0) {
            violationYaw += 30.0 * averageYaw / 180.0;
            tags.add("avgyaw");
            if (pData.isDebugActive(type) && pData.hasPermission(Permissions.ADMINISTRATION_DEBUG, player)) {
                player.sendMessage(ChatColor.RED + "NC+ Debug: " + ChatColor.RESET + "avgYaw: " + averageYaw + " avgYaw VL: " + violationYaw + "/" + cc.angleYaw);
            }
        }
        
        // Check for too quick target switch speed
        if (averageSwitching > 0.0) {
            violationSwitchSpeed += 20.0 * averageSwitching;
            tags.add("switchspeed");
            if (pData.isDebugActive(type) && pData.hasPermission(Permissions.ADMINISTRATION_DEBUG, player)) {
                player.sendMessage(ChatColor.RED + "NC+ Debug: " + ChatColor.RESET + "avgSwitch: " + averageSwitching + " avgSwitch VL: " + violationSwitchSpeed + "/" + cc.angleSwitch);
            }
        }


        if (violationMove > cc.angleMove) {
            violation = violationMove;
            data.angleVL += violation;
            final ViolationData vd = new ViolationData(this, player, data.angleVL, violation, cc.angleActions);
            if (vd.needsParameters()) vd.setParameter(ParameterName.TAGS, StringUtil.join(tags, "+"));
            cancel = executeActions(vd).willCancel();
        }
        else if (violationTime > cc.angleTime) {
            violation = violationTime;
            data.angleVL += violation;
            final ViolationData vd = new ViolationData(this, player, data.angleVL, violation, cc.angleActions);
            if (vd.needsParameters()) vd.setParameter(ParameterName.TAGS, StringUtil.join(tags, "+"));
            cancel = executeActions(vd).willCancel();
        }
        else if (violationYaw > cc.angleYaw) {
            violation = violationYaw;
            data.angleVL += violation;
            final ViolationData vd = new ViolationData(this, player, data.angleVL, violation, cc.angleActions);
            if (vd.needsParameters()) vd.setParameter(ParameterName.TAGS, StringUtil.join(tags, "+"));
            cancel = executeActions(vd).willCancel();
        } 
        else if (violationSwitchSpeed > cc.angleSwitch) {
            violation = violationSwitchSpeed;
            data.angleVL += violation;
            final ViolationData vd = new ViolationData(this, player, data.angleVL, violation, cc.angleActions);
            if (vd.needsParameters()) vd.setParameter(ParameterName.TAGS, StringUtil.join(tags, "+"));
            cancel = executeActions(vd).willCancel();
        } 
        else {
            // Reward the player by lowering their violation level.
            data.angleVL *= 0.98D;  
        }
        return cancel;
    }
}
