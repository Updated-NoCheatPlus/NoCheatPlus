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
package fr.neatmonster.nocheatplus.checks.blockbreak;

import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import fr.neatmonster.nocheatplus.actions.ParameterName;
import fr.neatmonster.nocheatplus.checks.Check;
import fr.neatmonster.nocheatplus.checks.CheckType;
import fr.neatmonster.nocheatplus.checks.ViolationData;
import fr.neatmonster.nocheatplus.compat.AlmostBoolean;
import fr.neatmonster.nocheatplus.compat.Bridge1_9;
import fr.neatmonster.nocheatplus.compat.bukkit.BridgePotionEffect;
import fr.neatmonster.nocheatplus.permissions.Permissions;
import fr.neatmonster.nocheatplus.players.IPlayerData;
import fr.neatmonster.nocheatplus.utilities.CheckUtils;
import fr.neatmonster.nocheatplus.utilities.StringUtil;
import fr.neatmonster.nocheatplus.utilities.TickTask;
import fr.neatmonster.nocheatplus.utilities.entity.PotionUtil;
import fr.neatmonster.nocheatplus.utilities.map.BlockProperties;

/**
 * A check used to verify if the player is breaking blocks faster than possible.
 */
public class FastBreak extends Check {

    /**
     * Instantiates a new fast break check.
     */
    public FastBreak() {
        super(CheckType.BLOCKBREAK_FASTBREAK);
    }

    /**
     * Checks a player for fastbreak. This is NOT for creative mode.
     * @return true, if successful
     */
    public boolean check(final Player player, final Block block, final AlmostBoolean isInstaBreak, final BlockBreakConfig cc, final BlockBreakData data, final IPlayerData pData) {
        final long now = System.currentTimeMillis();
        boolean cancel = false;
        // Determine expected breaking time by block type.
        final Material blockType = block.getType();
        final long expectedBreakingTime = Math.max(0, Math.round((double) BlockProperties.getBreakingDuration(blockType, player) * (double) cc.fastBreakModSurvival / 100D));
        final long elapsedTime;
        // Counting interact...break.
        // TODO: Concept for unbreakable blocks? Context: extreme VL.
        // TODO: Should it be breakingTime instead of 0 for inconsistencies?
        elapsedTime = cc.fastBreakStrict ? (data.fastBreakBreakTime > data.fastBreakfirstDamage) ? 0 : now - data.fastBreakfirstDamage
                                         // Counting break...break.
                                         : (data.fastBreakBreakTime > now) ? 0 : now - data.fastBreakBreakTime;

        /*
         * FastBreak compatibility: keep the grace threshold configurable.
         * The safer modern default belongs in config, while this check only
         * compares measured block/tool timing against that configured budget.
         */
        // Check if the time spent is lower than expected.
        if (isInstaBreak.decideOptimistically()) {
            // Ignore these for now.
            // TODO: Find out why this was commented out long ago a) did not fix mcMMO b) exploits.
            // TODO: Maybe adjust time to min(time, SOMETHING) for MAYBE/YES.
        }
        else if (elapsedTime < 0) {
            // Ignore it. TODO: ?
        }
        else if (elapsedTime + cc.fastBreakDelay < expectedBreakingTime) {
            // Lag or cheat or Minecraft.
            // Count in server side lag, if desired.
            final float serverLagFactor = pData.getCurrentWorldDataSafe().shouldAdjustToLag(type) ? TickTask.getLag(expectedBreakingTime, true) : 1f;
            final long missingTime = expectedBreakingTime - (long)(serverLagFactor * elapsedTime);
            if (missingTime > 0) {
                // Add as penalty
                data.fastBreakPenalties.add(now, (float) missingTime);
                // Only raise a violation, if the total penalty score exceeds the contention duration (for lag, delay).
                final float penaltyScore = data.fastBreakPenalties.score(cc.fastBreakBucketFactor);
                if (penaltyScore > cc.fastBreakGrace) {
                    // TODO: maybe add one absolute penalty time for big amounts to stop breaking until then
                    final double violation = (double) missingTime / 1000.0;
                    data.fastBreakVL += violation;
                    final ItemStack stack = Bridge1_9.getItemInMainHand(player);
                    final Material toolType = stack == null ? Material.AIR : stack.getType();
                    final boolean isValidTool = BlockProperties.isValidTool(blockType, stack);
                    final double haste = PotionUtil.getPotionEffectAmplifier(player, BridgePotionEffect.HASTE);
                    // Diagnostic info: tag the timing branch so false FastBreak reports show the block/tool context.
                    final String tags = getFastBreakTags(isInstaBreak, cc, isValidTool, haste, elapsedTime, missingTime);
                    final ViolationData vd = new ViolationData(this, player, data.fastBreakVL, violation, cc.fastBreakActions);
                    if (vd.needsParameters()) {
                        vd.setParameter(ParameterName.BLOCK_TYPE, blockType.toString());
                        vd.setParameter(ParameterName.TAGS, tags);
                    }
                    if (CheckUtils.shouldLogDebugToConsole()) {
                        logFastBreakDetail(player, blockType, toolType, isInstaBreak, isValidTool, haste,
                                elapsedTime, expectedBreakingTime, missingTime, serverLagFactor,
                                penaltyScore, data.fastBreakVL, violation, cc, tags);
                    }
                    cancel = executeActions(vd).willCancel();
                }
                // else: still within contention limits.
            }
        }
        else if (expectedBreakingTime > cc.fastBreakDelay) {
            // Fast breaking does not decrease violation level.
            data.fastBreakVL *= 0.9D;
        }

        // TODO: Rework to use (then hopefully completed) BlockBreakKey.
        if (pData.isDebugActive(type)) {
            detailDebugStats(player, isInstaBreak, blockType, elapsedTime, expectedBreakingTime, data, pData);
        }
        else {
            data.stats = null;
        }
        // (The break time is set in the listener).
        return cancel;
    }

    private void detailDebugStats(final Player player, final AlmostBoolean isInstaBreak,
                                  final Material blockType, final long elapsedTime, final long expectedBreakingTime,
                                  final BlockBreakData data, final IPlayerData pData) {
        if (pData.hasPermission(Permissions.ADMINISTRATION_DEBUG, player)) {
            // General stats:
            // TODO: Replace stats by new system (BlockBreakKey once complete), commands to inspect / auto-config.
            data.setStats();
            data.stats.addStats(data.stats.getId(blockType+ "/u", true), elapsedTime);
            data.stats.addStats(data.stats.getId(blockType + "/r", true), expectedBreakingTime);
            player.sendMessage(data.stats.getStatsStr(true));
            // Send info about current break:
            final ItemStack stack = Bridge1_9.getItemInMainHand(player);
            final boolean isValidTool = BlockProperties.isValidTool(blockType, stack);
            final double haste = PotionUtil.getPotionEffectAmplifier(player, BridgePotionEffect.HASTE);
            String msg = (isInstaBreak.decideOptimistically() ? ("[Insta=" + isInstaBreak + "]") : "[Normal]") + "[" + blockType + "] "+ elapsedTime + "u / " + expectedBreakingTime +"r (" + (isValidTool?"tool":"no-tool") + ")" + (Double.isInfinite(haste) ? "" : " haste=" + ((int) haste + 1));
            player.sendMessage(msg);
            //          net.minecraft.server.Item mcItem = net.minecraft.server.Item.byId[stack.getTypeId()];
            //          if (mcItem != null) {
            //              double x = mcItem.getDestroySpeed(((CraftItemStack) stack).getHandle(), net.minecraft.server.Block.byId[blockId]);
            //              player.sendMessage("mc speed: " + x);
            //          }
        }
    }

    private String getFastBreakTags(final AlmostBoolean isInstaBreak, final BlockBreakConfig cc,
                                    final boolean isValidTool, final double haste,
                                    final long elapsedTime, final long missingTime) {
        // Diagnostic info: these tags describe why the umbrella FastBreak check added VL.
        final StringBuilder builder = new StringBuilder(120);
        builder.append("subcheck_fastbreak_timing")
                .append("+branch_expected_duration")
                .append(cc.fastBreakStrict ? "+strict_interact_break" : "+break_break")
                .append("+insta_").append(isInstaBreak.name().toLowerCase())
                .append(isValidTool ? "+valid_tool" : "+invalid_tool");
        if (Double.isInfinite(haste)) {
            builder.append("+no_haste");
        }
        else {
            builder.append("+haste_").append((int) haste + 1);
        }
        if (elapsedTime == 0L) {
            builder.append("+zero_elapsed");
        }
        if (missingTime >= 1000L) {
            builder.append("+large_missing_time");
        }
        return builder.toString();
    }

    private void logFastBreakDetail(final Player player, final Material blockType, final Material toolType,
                                    final AlmostBoolean isInstaBreak, final boolean isValidTool,
                                    final double haste, final long elapsedTime,
                                    final long expectedBreakingTime, final long missingTime,
                                    final float serverLagFactor, final float penaltyScore,
                                    final double totalVL, final double addedVL,
                                    final BlockBreakConfig cc, final String tags) {
        try {
            // Diagnostic info: console-only detail for tuning FastBreak grace without changing check behavior.
            player.getServer().getLogger().info(new StringBuilder(380)
                    .append("[NCP][FastBreak][detail] player=").append(player.getName())
                    .append(" uuid=").append(player.getUniqueId())
                    .append(" subcheck=FASTBREAK_TIMING")
                    .append(" summary=block_timing{missingMs=").append(missingTime)
                    .append(",graceMs=").append(StringUtil.fdec3.format(cc.fastBreakGrace))
                    .append(",tool=").append(isValidTool ? "valid" : "invalid")
                    .append(",insta=").append(isInstaBreak.name().toLowerCase())
                    .append('}')
                    .append(" block=").append(blockType)
                    .append(" tool=").append(toolType)
                    .append(" validTool=").append(isValidTool)
                    .append(" insta=").append(isInstaBreak.name())
                    .append(" strict=").append(cc.fastBreakStrict)
                    .append(" elapsedMs=").append(elapsedTime)
                    .append(" expectedMs=").append(expectedBreakingTime)
                    .append(" missingMs=").append(missingTime)
                    .append(" lagFactor=").append(StringUtil.fdec3.format(serverLagFactor))
                    .append(" penaltyScore=").append(StringUtil.fdec3.format(penaltyScore))
                    .append(" graceMs=").append(StringUtil.fdec3.format(cc.fastBreakGrace))
                    .append(" delayMs=").append(cc.fastBreakDelay)
                    .append(" modSurvival=").append(cc.fastBreakModSurvival)
                    .append(" haste=").append(Double.isInfinite(haste) ? "none" : Integer.toString((int) haste + 1))
                    .append(" addVL=").append(StringUtil.fdec3.format(addedVL))
                    .append(" totalVL=").append(StringUtil.fdec3.format(totalVL))
                    .append(" tags=").append(tags)
                    .toString());
        }
        catch (Throwable ignored) {}
    }
}
