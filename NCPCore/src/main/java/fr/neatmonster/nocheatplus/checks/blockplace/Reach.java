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
package fr.neatmonster.nocheatplus.checks.blockplace;

import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;

import fr.neatmonster.nocheatplus.actions.ParameterName;
import fr.neatmonster.nocheatplus.checks.Check;
import fr.neatmonster.nocheatplus.checks.CheckType;
import fr.neatmonster.nocheatplus.checks.ViolationData;
import fr.neatmonster.nocheatplus.utilities.StringUtil;
import fr.neatmonster.nocheatplus.utilities.math.TrigUtil;

/**
 * The Reach check will find out if a player places a block too far away.
 */
public class Reach extends Check {

    /** The maximum distance allowed to interact with a block in creative mode. */
    public static final double CREATIVE_DISTANCE = 5.6D;

    /** The maximum distance allowed to interact with a block in survival mode. */
    public static final double SURVIVAL_DISTANCE = 5.2D;

    /** For temporary use: LocUtil.clone before passing deeply, call setWorld(null) after use. */
    private final Location useLoc = new Location(null, 0, 0, 0);

    /**
     * Instantiates a new reach check.
     */
    public Reach() {
        super(CheckType.BLOCKPLACE_REACH);
    }

    /**
     * Checks a player.
     * 
     * @param player
     *            the player
     * @param eyeHeight 
     * @param block 
     * @param data
     * @param cc
     * @return true, if successful
     */
    public boolean check(final Player player, final double eyeHeight, final Block block, final BlockPlaceData data, final BlockPlaceConfig cc) {
    	// TODO: Ray tracing-based?
        boolean cancel = false;
        final double distanceLimit = player.getGameMode() == GameMode.CREATIVE ? CREATIVE_DISTANCE : SURVIVAL_DISTANCE;
        // Distance is calculated from eye location to center of targeted block.
        final Location eyeLoc = player.getLocation(useLoc);
        eyeLoc.setY(eyeLoc.getY() + eyeHeight);
        final double distance = TrigUtil.distance(eyeLoc, block) - distanceLimit;

        if (distance > distanceLimit) {
            // They failed, increment violation level.
            data.reachVL += distance - distanceLimit;
            // Execute whatever actions are associated with this check and the violation level and find out if we should
            // cancel the event.
            final ViolationData vd = new ViolationData(this, player, data.reachVL, distance, cc.reachActions);
            vd.setParameter(ParameterName.REACH_DISTANCE, StringUtil.fdec3.format(distance));
            cancel = executeActions(vd).willCancel();
        } 
        // Player passed the check, reward them
        else data.reachVL *= 0.9D;
        // Cleanup.
        useLoc.setWorld(null);
        return cancel;
    }
}