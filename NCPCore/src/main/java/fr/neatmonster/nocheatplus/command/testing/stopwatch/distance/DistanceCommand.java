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


import org.bukkit.ChatColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import fr.neatmonster.nocheatplus.command.AbstractCommand;
import fr.neatmonster.nocheatplus.command.testing.stopwatch.StopWatch;
import fr.neatmonster.nocheatplus.command.testing.stopwatch.StopWatchRegistry;

public class DistanceCommand  extends AbstractCommand<StopWatchRegistry> {

    public static final String TAG = ChatColor.GRAY +""+ ChatColor.BOLD + "[" + ChatColor.RED + "NC+" + ChatColor.GRAY +""+ ChatColor.BOLD + "] " + ChatColor.GRAY;

    public DistanceCommand(StopWatchRegistry access) {
        super(access, "distance", null);
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String alias, String[] args) {
        Double distance = null;
        if (args.length != 3) {
            sender.sendMessage(TAG + "Not enough arguments. Command usage: /ncp stopwatch distance (distance in blocks).");
            return true;
        }
        try {
            distance = Double.parseDouble(args[2]);
        } catch (NumberFormatException e) {}
        if (distance == null || distance.isNaN() || distance.isInfinite() || distance.doubleValue() < 0.0) {
            sender.sendMessage(TAG + "Bad distance: " + ChatColor.RED +""+ args[2] + ChatColor.GRAY);
            return true;
        }
        StopWatch clock = new DistanceStopWatch((Player) sender, distance.doubleValue());
        access.setClock((Player) sender, clock);
        sender.sendMessage(TAG + "New stopwatch started: " + ChatColor.GREEN +""+ clock.getClockDetails() + ChatColor.GRAY + ".");
        return true;
    }

}
