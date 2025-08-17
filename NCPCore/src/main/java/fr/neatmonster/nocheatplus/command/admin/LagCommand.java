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

import org.bukkit.ChatColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

import fr.neatmonster.nocheatplus.command.BaseCommand;
import fr.neatmonster.nocheatplus.permissions.Permissions;
import fr.neatmonster.nocheatplus.utilities.StringUtil;
import fr.neatmonster.nocheatplus.utilities.TickTask;

public class LagCommand extends BaseCommand {

    public LagCommand(JavaPlugin plugin) {
        super(plugin, "lag", Permissions.COMMAND_LAG);
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {

        final String cGO, cG, cR, bO;
        if (sender instanceof Player) {
            cGO = ChatColor.GOLD.toString();
            cR = ChatColor.RED.toString();
            cG = ChatColor.GRAY.toString();
            bO = ChatColor.BOLD.toString();
        }
        else cGO = cR = cG = bO = "";

        StringBuilder builder = new StringBuilder(300);
        builder.append((sender instanceof Player ? TAG : CTAG) + "Displaying lag information...\n");

        // Lag spikes.
        long[] spikeDurations = TickTask.getLagSpikeDurations();
        int[] spikes = TickTask.getLagSpikes();
        builder.append(cR +""+ bO + "»Lag Spikes«\n");  

        if (spikes[0] == 0){
            builder.append("No lag spike(s) greater than "+ cGO +""+ spikeDurations[0] + cG +" ms within the last 40 to 60 minutes.");
        }
        else if (spikes[0] > 0){

            builder.append(cG + "Total spikes: " + cGO +""+ spikes[0] + cG + "\nThere have been some spikes greater than " + cGO +""+ spikeDurations[0] + cG + " ms within the last 40 to 60 minutes.");
            builder.append("\n" + "Result(s):");

            for (int i = 0; i < spikeDurations.length; i++){
                if (i < spikeDurations.length - 1 && spikes[i] == spikes[i + 1]){
                    // Ignore these, get printed later.
                    continue;
                }
                if (spikes[i] == 0){
                    continue; // Could be break.
                }
                else if (i < spikeDurations.length - 1){
                    builder.append(cG + "\n• " + cGO +""+ (spikes[i] - spikes[i + 1]) + cG + "spike(s) x " + cGO +""+ cGO +""+ spikeDurations[i] + cG + "ms -> " + cGO +""+ spikeDurations[i + 1] + cG + ". ");
                }
                else{
                    builder.append(cG + "\n• " + cGO +""+ spikes[i] + cG + "spike(s) x " + cGO +""+ cGO +""+ spikeDurations[i] +"ms"+ cG + ".");
                }
            }
        }
        builder.append("\n");
        // TPS lag.
        long max = 50L * (1L + TickTask.lagMaxTicks) * TickTask.lagMaxTicks;
        long medium = 50L * TickTask.lagMaxTicks;
        long second = 1200L;
        builder.append(cR +""+ bO + "»TPS Lag«" + cG + "\n[Perc.][time tracked], 0% = 20 TPS");
        for (long ms : new long[]{second, medium, max}){
            double lag = TickTask.getLag(ms, true);
            int p = Math.max(0, (int) ((lag - 1.0) * 100.0));
            builder.append(cG + "\n• " + cG + "" + cGO + p + cG + "% tps lag in the last " + cGO +""+ StringUtil.fdec1.format((double) ms / 1200.0) + cG + " second(s). " );
        }
        // Send message.
        sender.sendMessage(builder.toString());
        return true;
    }

}
