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
package fr.neatmonster.nocheatplus.command.actions.delay;

import org.bukkit.Bukkit;
import org.bukkit.Server;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.plugin.java.JavaPlugin;

import fr.neatmonster.nocheatplus.command.AbstractCommand;
import fr.neatmonster.nocheatplus.logging.StaticLog;
import fr.neatmonster.nocheatplus.permissions.Permissions;

/**
 * Delay an arbitrary command, the command is always delayed, unless for bad delay input.
 * @author mc_dev
 *
 */
public class DelayCommand extends DelayableCommand {

	public DelayCommand(JavaPlugin plugin){
		super(plugin, "delay", Permissions.COMMAND_DELAY, 1, 0, true);
		demandConsoleCommandSender = true;
	}
	
	@Override
	public boolean execute(CommandSender sender, Command command, String label, String[] alteredArgs, long delay) {
		if (alteredArgs.length <= 2) {
			StaticLog.logInfo("Bad setup. Command usage /ncp delay delay=(ticks) (command to delay).");
			return true;
		}
		final String cmd = AbstractCommand.join(alteredArgs, 1);
		schedule(new Runnable() {
			@Override
			public void run() {
				Server server = Bukkit.getServer();
				server.dispatchCommand(server.getConsoleSender(), cmd);
			}
		}, delay);
		return true;
	}

}
