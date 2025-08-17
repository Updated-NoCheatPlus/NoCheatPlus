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
package fr.neatmonster.nocheatplus.command.testing.stopwatch.distance;

import org.bukkit.Location;
import org.bukkit.entity.Player;

import fr.neatmonster.nocheatplus.command.testing.stopwatch.LocationBasedStopWatchData;
import fr.neatmonster.nocheatplus.utilities.math.TrigUtil;

/**
 * Stops at the distance of the player.
 * @author mc_dev
 *
 */
public class DistanceStopWatch extends LocationBasedStopWatchData{

    protected final  double distance;
    protected final  double distanceSq;

    public DistanceStopWatch(Player player, double distance) {
        super(player);
        this.distance = distance;
        this.distanceSq = distance * distance;
        clockDetails = "(distance " + distance + " meters)";
    }

    @Override
    public boolean checkStop() {
        final Location loc = player.getLocation(useLoc);
        if (!worldName.equals(loc.getWorld().getName()) || TrigUtil.distanceSquared(x, y, z, loc.getX(), loc.getY(), loc.getZ()) >= distanceSq) {
            stop();
            useLoc.setWorld(null);
            return true;
        } else {
            useLoc.setWorld(null);
            return false;
        }
    }

    @Override
    public boolean needsTick() {
        return true;
    }

}
