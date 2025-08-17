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

import java.util.LinkedList;
import java.util.List;
import java.util.UUID;

import org.bukkit.ChatColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

import fr.neatmonster.nocheatplus.checks.CheckType;
import fr.neatmonster.nocheatplus.command.BaseCommand;
import fr.neatmonster.nocheatplus.hooks.NCPExemptionManager;
import fr.neatmonster.nocheatplus.permissions.Permissions;
import fr.neatmonster.nocheatplus.players.DataManager;
import fr.neatmonster.nocheatplus.utilities.StringUtil;

public class ExemptionsCommand extends BaseCommand {

    public ExemptionsCommand(JavaPlugin plugin) {
        super(plugin, "exemptions", Permissions.COMMAND_EXEMPTIONS, new String[]{"exe"});
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

        if (args.length != 2) {
            sender.sendMessage((sender instanceof Player ? TAG : CTAG) + "Please specify a player.");
            return true;
        } 
        String playerName = args[1].trim();
        Player player = DataManager.getPlayer(playerName);
        UUID id;
        if (player != null) {
            playerName = player.getName();
            id = player.getUniqueId();
        } else {
            id = DataManager.getUUID(playerName);
        }
        final List<String> entries = new LinkedList<String>();
        if (id == null) {
            sender.sendMessage((sender instanceof Player ? TAG : CTAG) + "Not online nor a UUID: " + c3 +""+ playerName + c1 + " .");
            return true;
        } else {
            for (CheckType type : CheckType.values()){
                if (NCPExemptionManager.isExempted(id, type)) {
                    entries.add(type.toString());
                }
            }
        }
        if (entries.isEmpty()) {
            sender.sendMessage((sender instanceof Player ? TAG : CTAG) + "No exemption entries available for " + c3 +""+ playerName + c1 + " .");
        }
        else {
            // TODO: Compress entries ?
            sender.sendMessage((sender instanceof Player ? TAG : CTAG) + "Exemptions for " + c3 +""+  playerName + c1 + ": " + c3 +""+ StringUtil.join(entries, ", "));
        }
        return true;
    }

    /* (non-Javadoc)
     * @see fr.neatmonster.nocheatplus.command.AbstractCommand#onTabComplete(org.bukkit.command.CommandSender, org.bukkit.command.Command, java.lang.String, java.lang.String[])
     */
    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        // Fill in players.
        return null;
    }

}
