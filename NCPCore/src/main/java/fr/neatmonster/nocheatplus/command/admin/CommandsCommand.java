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
package fr.neatmonster.nocheatplus.command.admin;

import java.util.Arrays;

import org.bukkit.ChatColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.plugin.java.JavaPlugin;

import fr.neatmonster.nocheatplus.command.BaseCommand;
import fr.neatmonster.nocheatplus.permissions.Permissions;
import fr.neatmonster.nocheatplus.utilities.StringUtil;

/**
 * This command just shows a list of all commands.
 * @author mc_dev
 *
 */
public class CommandsCommand extends BaseCommand {

    final String[] moreCommands = new String[]{
            // TODO: Mmmmh, spaghetti.
            ChatColor.GOLD +""+ ChatColor.BOLD + "Console commands:",
            ChatColor.GRAY +""+ ChatColor.BOLD +"• "+ChatColor.RED +""+ ChatColor.ITALIC + "/<command> ban (playername) (reason)"+ChatColor.GRAY+" - Ban player.",
            ChatColor.GRAY +""+ ChatColor.BOLD +"• "+ChatColor.RED +""+ ChatColor.ITALIC +"/<command> kick (playername) (reason)"+ChatColor.GRAY+" - Kick player.",
            ChatColor.GRAY +""+ ChatColor.BOLD +"• "+ChatColor.RED +""+ ChatColor.ITALIC +"/<command> tell (playername) (message)"+ChatColor.GRAY+" - Tell a private message to the player.",
            ChatColor.GRAY +""+ ChatColor.BOLD +"• "+ChatColor.RED +""+ ChatColor.ITALIC +"/<command> delay (delay=ticks) (command to delay)"+ChatColor.GRAY+" - Delay a command execution. Time is in ticks.",
            ChatColor.GRAY +""+ ChatColor.BOLD +"• "+ChatColor.RED +""+ ChatColor.ITALIC +"/<command> denylogin (playername) (minutes) (reason)"+ChatColor.GRAY+" - Deny log-in for a player.",
            "",
            ChatColor.GOLD +""+ ChatColor.BOLD + "Auxiliary commands:",
            ChatColor.GRAY +""+ ChatColor.BOLD +"• "+ChatColor.RED +""+ ChatColor.ITALIC + "/<command> log counters"+ChatColor.GRAY+" - Show some stats/debug counters summary.",
            ChatColor.GRAY +""+ ChatColor.BOLD +"• "+ChatColor.RED +""+ ChatColor.ITALIC +"/<command> reset counters" +ChatColor.GRAY+ " - Reset some stats/debug counters",
            ChatColor.GRAY +""+ ChatColor.BOLD +"• "+ChatColor.RED +""+ ChatColor.ITALIC +"/<command> debug player (playername) yes/no:(checktype)"+ChatColor.GRAY+" - Start/End a debug session for a specific check.",
            ChatColor.GRAY +""+ ChatColor.BOLD +"• "+ChatColor.RED +""+ ChatColor.ITALIC +"/<command> denylist"+ChatColor.GRAY+"- Lists players that have been denied to log-in.",
            ChatColor.GRAY +""+ ChatColor.BOLD +"• "+ChatColor.RED +""+ ChatColor.ITALIC +"/<command> allowlogin (playername)"+ChatColor.GRAY+" - Allow a player to login again.",
            ChatColor.GRAY +""+ ChatColor.BOLD +"• "+ChatColor.RED +""+ ChatColor.ITALIC +"/<command> exemptions (playername)"+ChatColor.GRAY+" - Lists all exemptions for a player.",
            ChatColor.GRAY +""+ ChatColor.BOLD +"• "+ChatColor.RED +""+ ChatColor.ITALIC +"/<command> exempt (playername) (checktype)"+ChatColor.GRAY+" - Exempt a player from a check. * will exempt from all checks.",
            ChatColor.GRAY +""+ ChatColor.BOLD +"• "+ChatColor.RED +""+ ChatColor.ITALIC +"/<command> unexempt (playername) (checktype)"+ChatColor.GRAY+" - Unexempt a player from a check. * will unexempt from all checks.",
    };
    
    final String allCommands;

    public CommandsCommand(JavaPlugin plugin) {
        super(plugin, "commands", Permissions.COMMAND_COMMANDS, new String[]{"cmds"});
        for (int i = 0; i < moreCommands.length; i++){
            moreCommands[i] = moreCommands[i].replace("<command>", "ncp");
        }
        String all = TAG + ChatColor.GOLD + "All commands info:\n";
        Command cmd = plugin.getCommand("nocheatplus");
        if (cmd != null){
            all += cmd.getUsage().replace("<command>", "ncp");
        }
        all += StringUtil.join(Arrays.asList(moreCommands), "\n");
        allCommands = all;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        sender.sendMessage(allCommands);
        return true;
    }

}
