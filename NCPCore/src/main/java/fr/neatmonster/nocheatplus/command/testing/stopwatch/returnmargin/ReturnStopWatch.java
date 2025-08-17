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
package fr.neatmonster.nocheatplus.command.testing.stopwatch.returnmargin;

import org.bukkit.Location;
import org.bukkit.entity.Player;

import fr.neatmonster.nocheatplus.command.testing.stopwatch.LocationBasedStopWatchData;
import fr.neatmonster.nocheatplus.utilities.math.TrigUtil;

public class ReturnStopWatch extends LocationBasedStopWatchData{

    protected final double marginDistance;
    protected final double marginDistanceSq;
    protected boolean started = false;

    public ReturnStopWatch(Player player, double marginDistance) {
        super(player);
        this.marginDistance = marginDistance;
        this.marginDistanceSq = marginDistance * marginDistance;
        clockDetails = "(return to " + Location.locToBlock(x) + "," +Location.locToBlock(y) + "," + Location.locToBlock(z) + "/+-" + marginDistance + ")";
    }

    @Override
    public boolean checkStop() {
        final Location loc = player.getLocation(useLoc);
        if (!worldName.equals(loc.getWorld().getName())) {
            stop();
            useLoc.setWorld(null);
            return true;
        } 
        final double distSq = TrigUtil.distanceSquared(x, y, z, loc.getX(), loc.getY(), loc.getZ());
        useLoc.setWorld(null);
        if (!started) {
            // Skip until the player made it out of the margin.
            if (distSq > marginDistanceSq) {
                started = true;
            }
            return false;
        } else {
            // Check if the player returned to within the margin.
            if (distSq < marginDistanceSq) {
                stop();
                return true;
            } else {
                return false;
            }
        }
    }

    @Override
    public boolean needsTick() {
        return true;
    }

}
