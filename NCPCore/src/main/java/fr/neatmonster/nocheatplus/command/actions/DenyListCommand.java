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
package fr.neatmonster.nocheatplus.command.actions;

import java.util.Arrays;

import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

import fr.neatmonster.nocheatplus.NCPAPIProvider;
import fr.neatmonster.nocheatplus.command.BaseCommand;
import fr.neatmonster.nocheatplus.permissions.Permissions;
import fr.neatmonster.nocheatplus.utilities.StringUtil;

public class DenyListCommand extends BaseCommand {

	public DenyListCommand(JavaPlugin plugin) {
		super(plugin, "denylist", Permissions.COMMAND_KICKLIST,
		    new String[]{"kicklist", "tempbanned", "deniedlist", "denyloginlist", "deniedlogin"});
		}

	@Override
	public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
		final String[] kicked = NCPAPIProvider.getNoCheatPlusAPI().getLoginDeniedPlayers();
		if (kicked.length < 100) Arrays.sort(kicked);
		sender.sendMessage((sender instanceof Player ? TAG : CTAG) + "Players denied to login (temporarily):");
		sender.sendMessage(StringUtil.join(Arrays.asList(kicked), " "));
		return true;
	}

}
