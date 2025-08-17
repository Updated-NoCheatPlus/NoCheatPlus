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
package fr.neatmonster.nocheatplus.checks.inventory;

import org.bukkit.ChatColor;
import org.bukkit.entity.Player;

import fr.neatmonster.nocheatplus.checks.Check;
import fr.neatmonster.nocheatplus.checks.CheckType;
import fr.neatmonster.nocheatplus.permissions.Permissions;
import fr.neatmonster.nocheatplus.players.DataManager;
import fr.neatmonster.nocheatplus.players.IPlayerData;
import fr.neatmonster.nocheatplus.utilities.TickTask;

/**
 * Legacy check. It will find out if a player pulled the string of their bow too fast. <br>
 * Will be disabled on 1.8 and later.
 */
public class InstantBow extends Check {

    private static final float maxTime = 800f;

    /**
     * Instantiates a new instant bow check.
     */
    public InstantBow() {
        super(CheckType.INVENTORY_INSTANTBOW);
    }

    /**
     * Checks a player.
     * 
     * @param player
     *            the player
     * @param force
     *            the force
     * @param now
     * @return true, if successful
     */
    public boolean check(final Player player, final float force, final long now) {
        final IPlayerData pData = DataManager.getPlayerData(player);
        final InventoryData data = pData.getGenericInstance(InventoryData.class);
        final InventoryConfig cc = pData.getGenericInstance(InventoryConfig.class);
        boolean cancel = false;
        /** Rough estimation of how long pulling the string should've taken */
        final long expectedPullDuration = (long) (maxTime - maxTime * (1f - force) * (1f - force)) - cc.instantBowDelay;
        /** Time taken to pull the string */
        final long pullDuration;
        final boolean valid;
        // The interact time is invalid, if set to 0.
        valid = cc.instantBowStrict ? data.instantBowInteract != 0 : true;
        pullDuration = cc.instantBowStrict ? (valid ? (now - data.instantBowInteract) : 0L) : (now - data.instantBowShoot);

        if (valid && (!cc.instantBowStrict || data.instantBowInteract > 0L) && pullDuration >= expectedPullDuration) {
            // The player was slow enough, reward them by lowering their violation level.
            data.instantBowVL *= 0.9D;
        }
        else if (valid && data.instantBowInteract > now) {
            // Security check if time ran backwards.
            // TODO: Maybe this can be removed, though TickTask does not reset at the exact moment.
        }
        else {
            // Account for server side lag.
            // (Do not apply correction to invalid pulling.)
            final long correctedPullduration = valid ? 
                                               (pData.getCurrentWorldData().shouldAdjustToLag(type) ? (long) (TickTask.getLag(expectedPullDuration, true) * pullDuration) : pullDuration) 
                                                : 0;
            if (correctedPullduration < expectedPullDuration) {
                // TODO: Consider: Allow one time but set yawrate penalty time ?
                final double difference = (expectedPullDuration - pullDuration) / 100D;
                // Player was too fast, increase their violation level.
                data.instantBowVL += difference;
                // Execute whatever actions are associated with this check and the violation level and find out if we should cancel the event
                cancel = executeActions(player, data.instantBowVL, difference, cc.instantBowActions).willCancel();
            }
        }

        if (pData.isDebugActive(type) && pData.hasPermission(Permissions.ADMINISTRATION_DEBUG, player)) {
            player.sendMessage(ChatColor.YELLOW + "NCP: " + ChatColor.GRAY + "Bow shot - force: " + force +", " + (cc.instantBowStrict || pullDuration < 2 * expectedPullDuration ? ("pull time: " + pullDuration) : "") + "(" + expectedPullDuration +")");
        }

        // Reset data here.
        data.instantBowInteract = 0;
        data.instantBowShoot = now;
        return cancel;
    }
}
