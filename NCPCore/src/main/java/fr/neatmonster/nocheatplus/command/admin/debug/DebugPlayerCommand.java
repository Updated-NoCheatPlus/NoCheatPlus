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
package fr.neatmonster.nocheatplus.command.admin.debug;

import java.util.Arrays;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import org.bukkit.ChatColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

import fr.neatmonster.nocheatplus.checks.CheckType;
import fr.neatmonster.nocheatplus.command.BaseCommand;
import fr.neatmonster.nocheatplus.command.CommandUtil;
import fr.neatmonster.nocheatplus.compat.AlmostBoolean;
import fr.neatmonster.nocheatplus.components.config.value.OverrideType;
import fr.neatmonster.nocheatplus.players.DataManager;
import fr.neatmonster.nocheatplus.players.IPlayerData;
import fr.neatmonster.nocheatplus.utilities.IdUtil;
import fr.neatmonster.nocheatplus.utilities.StringUtil;

public class DebugPlayerCommand extends BaseCommand {

    static class DebugEntry {
        public AlmostBoolean active = AlmostBoolean.YES;
        public final Set<CheckType> checkTypes = new LinkedHashSet<CheckType>();

        /**
         * AlmostBoolean[:CheckType1[:CheckType2[...]]]
         * @param input
         * @return
         */
        public static DebugEntry parseEntry(String input) {
            String[] split = input.split(":");
            DebugEntry entry = new DebugEntry();
            entry.active = AlmostBoolean.match(split[0]);
            if (entry.active == null) {
                return null;
            }
            for (int i = 1; i < split.length; i++) {
                try {
                    CheckType checkType = CheckType.valueOf(split[i].toUpperCase().replace('.', '_'));
                    if (checkType == null) {
                        // TODO: Possible !?
                        return entry;
                    }
                    entry.checkTypes.add(checkType);
                }
                catch (Exception e){
                    return entry;
                }
            }
            return entry;
        }

    }

    public DebugPlayerCommand(JavaPlugin plugin) {
        super(plugin, "player", null);
        usage = TAG + "/ncp debug player (playername/UUID) (yes|no|default)[:CheckType[:Check]]";
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (args.length == 4) {
            String[] parts = args[3].split(":");
            if (parts.length == 1) {
                return Arrays.asList("no", "yes", "default");
            } else {
                StringBuilder builder = new StringBuilder();
                for (int i = 0; i < parts.length - 1; i++) {
                    builder.append(parts[i]);
                    builder.append(":");
                }
                return CommandUtil.getCheckTypeTabMatches2(parts[parts.length-1], builder.toString());
            }
        }
        return null; // Tab-complete player names. 
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String alias, String[] args) {

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

        if (args.length <= 2) {
            sender.sendMessage((sender instanceof Player ? TAG : CTAG) + "Bad setup. Command usage: /ncp debug player (playername) yes/no:(checktype).");
            return true;
        }
        // TODO: Wild cards (all players)?
        // TODO: (Allow to specify OverrideType ?)

        // Note that MAYBE means to reset here, it's not the same as direct PlayerData API access.
        DebugEntry entry = new DebugEntry();
        Player player = null;
        if (args.length > 2) {
            final String input = args[2];
            if (IdUtil.isValidMinecraftUserName(input)) {
                player = DataManager.getPlayer(input);
            }
            else {
                UUID id = IdUtil.UUIDFromStringSafe(input);
                if (id == null) {
                    sender.sendMessage((sender instanceof Player ? TAG : CTAG) + "Bad name or UUID: " + c3 + input);
                    return true;
                }
                else {
                    player = DataManager.getPlayer(id);
                }
            }
            if (player == null) {
                sender.sendMessage((sender instanceof Player ? TAG : CTAG) + "Not online: " + c3 + input);
                return true;
            }
        }
        

        if (args.length > 3) {
            String input = args[3];
            entry = DebugEntry.parseEntry(input);
            if (entry == null) {
                sender.sendMessage((sender instanceof Player ? TAG : CTAG) + "Bad setup: " + c3 + input);
                // Can't continue.
                return true;
            }
        }

        // Execute for online player.
        final Collection<CheckType> checkTypes;
        if (entry.checkTypes.isEmpty()) {
            // CheckType.ALL
            checkTypes = Arrays.asList(CheckType.ALL);
        }
        else {
            checkTypes = entry.checkTypes;
        }
        final IPlayerData data = DataManager.getPlayerData(player);
        for (final CheckType checkType : checkTypes) {
            if (entry.active == AlmostBoolean.MAYBE) {
                data.resetDebug(checkType);
            }
            else {
                data.overrideDebug(checkType, entry.active, OverrideType.CUSTOM, true);
            }
        }
        sender.sendMessage(TAG + "Set debug: " +c3+ entry.active +c1+ " for player " + c3 + player.getName() +c1+ " for checks: " +c3+ StringUtil.join(checkTypes, ","));
        return true;
    }

}
