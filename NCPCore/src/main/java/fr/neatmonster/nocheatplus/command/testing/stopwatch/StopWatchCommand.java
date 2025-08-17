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
package fr.neatmonster.nocheatplus.command.testing.stopwatch;

import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

import fr.neatmonster.nocheatplus.command.BaseCommand;
import fr.neatmonster.nocheatplus.command.testing.stopwatch.distance.DistanceCommand;
import fr.neatmonster.nocheatplus.command.testing.stopwatch.returnmargin.ReturnCommand;
import fr.neatmonster.nocheatplus.command.testing.stopwatch.start.StartCommand;
import fr.neatmonster.nocheatplus.command.testing.stopwatch.stop.StopCommand;
import fr.neatmonster.nocheatplus.compat.SchedulerHelper;
import fr.neatmonster.nocheatplus.permissions.Permissions;


/**
 * Root command. <br>
 * Intended features: current time with /stopwatch, sub commands: start+stop, distance, return to location<br>
 * TODO: countdown
 * @author asofold
 *
 */
public class StopWatchCommand extends BaseCommand {

    private final StopWatchRegistry registry = new StopWatchRegistry();

    public StopWatchCommand(final JavaPlugin access) {
        super(access, "stopwatch", Permissions.COMMAND_STOPWATCH, new String[]{"sw"});

        SchedulerHelper.runSyncTask(access, (arg) -> registry.setup(access));

        // Register sub commands.
        addSubCommands(
                new StopCommand(registry),
                new StartCommand(registry),
                new DistanceCommand(registry),
                new ReturnCommand(registry)
                );

    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String alias, String[] args) {
        if (!(sender instanceof Player)) {
            // TODO: Implement checking others clocks!
            sender.sendMessage(CTAG + "Stopwatch functionality is only available to players.");
            return true;
        }
        if (args.length == 1) {
            StopWatch clock = registry.getClock((Player) sender);
            sender.sendMessage(TAG + "Use tab completion for stopwatch options.");
            if (clock != null) {
                clock.sendStatus();
            }
            return true;
        } else {
            return super.onCommand(sender, command, alias, args);
        }
    }



}
