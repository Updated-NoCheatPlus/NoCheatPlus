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
package fr.neatmonster.nocheatplus.command.testing.stopwatch.start;


import org.bukkit.ChatColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import fr.neatmonster.nocheatplus.command.AbstractCommand;
import fr.neatmonster.nocheatplus.command.testing.stopwatch.StopWatchRegistry;

public class StartCommand extends AbstractCommand<StopWatchRegistry> {

    public static final String TAG = ChatColor.GRAY +""+ ChatColor.BOLD + "[" + ChatColor.RED + "NC+" + ChatColor.GRAY +""+ ChatColor.BOLD + "] " + ChatColor.GRAY;

    public StartCommand(StopWatchRegistry access) {
        super(access, "start", null);
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String alias, String[] args) {
        access.setClock((Player) sender, new SimpleStopWatch((Player) sender));
        sender.sendMessage(TAG + "New stopwatch started.");
        return true;
    }

}
