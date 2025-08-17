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
package fr.neatmonster.nocheatplus.command.admin.exemption;

import java.util.Arrays;
import java.util.List;
import java.util.UUID;

import org.bukkit.ChatColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

import fr.neatmonster.nocheatplus.checks.CheckType;
import fr.neatmonster.nocheatplus.command.BaseCommand;
import fr.neatmonster.nocheatplus.command.CommandUtil;
import fr.neatmonster.nocheatplus.hooks.NCPExemptionManager;
import fr.neatmonster.nocheatplus.permissions.Permissions;
import fr.neatmonster.nocheatplus.players.DataManager;
import fr.neatmonster.nocheatplus.utilities.StringUtil;

public class UnexemptCommand extends BaseCommand {

    public UnexemptCommand(JavaPlugin plugin) {
        super(plugin, "unexempt", Permissions.COMMAND_UNEXEMPT);
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {

        final String c1, c2, c3, c4, c5, c6, c7;
        if (sender instanceof Player) {
            c1 = ChatColor.GRAY.toString();
            c2 = ChatColor.BOLD.toString();
            c3 = ChatColor.RED.toString();
            c4 = ChatColor.ITALIC.toString();
            c5 = ChatColor.GOLD.toString();
            c6 = ChatColor.WHITE.toString();
            c7 = ChatColor.YELLOW.toString();
        } else {
            c1 = c2 = c3 = c4 = c5 = c6 = c7 = "";
        }

        // TODO: Reduce copy and paste by introducing some super class.
        if (args.length < 2) {
            sender.sendMessage((sender instanceof Player ? TAG : CTAG) + "Please specify a player to unexempt.");
            return true;
        }
        else if (args.length > 3) {
            sender.sendMessage((sender instanceof Player ? TAG : CTAG) + "Too many arguments. Command usage: /ncp unexempt (playername) (checktype).");
            return true;
        }
        String playerName = args[1];
        final CheckType checkType;
        if (args.length == 3){
            try{
                checkType = CheckType.valueOf(args[2].toUpperCase().replace('-', '_').replace('.', '_'));
            } catch (Exception e){
                sender.sendMessage((sender instanceof Player ? TAG : CTAG) + "Could not interpret: " + c3 +""+ args[2]);
                sender.sendMessage((sender instanceof Player ? TAG : CTAG) + "Check type should be one of: " + c3 +""+ StringUtil.join(Arrays.asList(CheckType.values()), c6 + ", " + c3));
                return true;
            }
        }
        else checkType = CheckType.ALL;
        if (playerName.equals("*")){
            // Unexempt all.
            // TODO: might care to find players only ?
            NCPExemptionManager.clear();
            sender.sendMessage((sender instanceof Player ? TAG : CTAG) + "Removed exemptions for all players for checks: " + c3 +""+ checkType);
            return true;
        }
        // Find player.
        final Player player = DataManager.getPlayer(playerName);
        final UUID id;
        if (player != null) {
            playerName = player.getName();
            id = player.getUniqueId();
        } else {
            id = DataManager.getUUID(playerName);
        }
        if (id == null) {
            sender.sendMessage((sender instanceof Player ? TAG : CTAG) + "Not an online player nor a UUID: " + c3 +""+ playerName);
        } else {
            NCPExemptionManager.unexempt(id, checkType);
            sender.sendMessage((sender instanceof Player ? TAG : CTAG) + "Remove exemptions for " + c3 +""+ playerName + c1 + " for checks: " + c3 +""+ checkType);
        }
        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args)
    {
        // At least complete CheckType
        if (args.length == 3) return CommandUtil.getCheckTypeTabMatches(args[2]);
        return null;
    }

    /* (non-Javadoc)
     * @see fr.neatmonster.nocheatplus.command.AbstractCommand#testPermission(org.bukkit.command.CommandSender, org.bukkit.command.Command, java.lang.String, java.lang.String[])
     */
    @Override
    public boolean testPermission(CommandSender sender, Command command, String alias, String[] args) {
        return super.testPermission(sender, command, alias, args) 
                || args.length >= 2 && args[1].trim().equalsIgnoreCase(sender.getName()) 
                && sender.hasPermission(Permissions.COMMAND_UNEXEMPT_SELF.getBukkitPermission());
    }

}
