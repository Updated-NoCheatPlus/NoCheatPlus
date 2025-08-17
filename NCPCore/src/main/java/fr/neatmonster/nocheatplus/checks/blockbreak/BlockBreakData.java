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
import org.bukkit.inventory.ItemStack;

import fr.neatmonster.nocheatplus.checks.access.ACheckData;
import fr.neatmonster.nocheatplus.components.data.IDataOnReload;
import fr.neatmonster.nocheatplus.components.registry.IGetGenericInstance;
import fr.neatmonster.nocheatplus.stats.Timings;
import fr.neatmonster.nocheatplus.utilities.ds.count.ActionFrequency;
import fr.neatmonster.nocheatplus.utilities.map.BlockProperties;

/**
 * Player specific data for the block break checks.
 */
public class BlockBreakData extends ACheckData implements IDataOnReload {

    // Violation levels.
    public double directionVL;
    public double fastBreakVL;
    public double frequencyVL;
    public double noSwingVL;
    public double reachVL;
    public final ActionFrequency wrongBlockVL;

    // Shared data.
    public int clickedX = Integer.MAX_VALUE;
    public int clickedY;
    public int clickedZ;
    public int clickedTick;
    /** Tool that the block was clicked with, null for the case of air. */
    public Material clickedTool = null;
    // TODO: use tick here too  ?
    public long wasInstaBreak;
    public Timings stats;

    // Data of the fast break check.
    public final ActionFrequency fastBreakPenalties;
    public long  fastBreakBreakTime  = System.currentTimeMillis() - 1000L;
    /** First time interaction with a block. */
    public long fastBreakfirstDamage = System.currentTimeMillis();
    
    // Data of the frequency check
    public final ActionFrequency frequencyBuckets;
    public int frequencyShortTermCount;
    public int frequencyShortTermTick;

    // Data of the no swing check.
    public int noSwingCount = 0;

    public BlockBreakData(final BlockBreakConfig cc) {
        setStats();
        fastBreakPenalties = new ActionFrequency(cc.fastBreakBuckets, cc.fastBreakBucketDur);
        frequencyBuckets = new ActionFrequency(cc.frequencyBuckets, cc.frequencyBucketDur);
        wrongBlockVL = new ActionFrequency(6, 20000);
    }

    void setStats() {
        if (stats == null) {
            stats = new Timings("NCP/FASTBREAK");
        }
    }

    /**
     * Meant to record the first click/damage on a block (not subsequent clicking), forces internals update.
     * @param block
     * @param tick
     * @param now
     * @param mat 
     */
    public void setClickedBlock(Block block, int tick, long now, Material tool) {
        fastBreakfirstDamage = now;
        // Also set last clicked blocks position.
        clickedX = block.getX();
        clickedY = block.getY();
        clickedZ = block.getZ();
        clickedTick = tick;
        clickedTool = BlockProperties.isAir(tool) ? null : tool;
    }

    /**
     * Reset clicked block (as if not clicked anything before).
     */
    public void resetClickedBlock() {
        clickedX = Integer.MAX_VALUE;
        clickedTick = 0;
        fastBreakfirstDamage = 0;
        clickedTool = null;
    }

    public boolean toolChanged(ItemStack stack) {
        return toolChanged(stack == null ? null: stack.getType());
    }

    public boolean toolChanged(Material mat) {
        if (BlockProperties.isAir(mat)) {
            return !BlockProperties.isAir(clickedTool);
        }
        return clickedTool != mat;
    }

    @Override
    public boolean dataOnReload(IGetGenericInstance dataAccess) {
        // Remove on reload for now.
        return true;
    }
}