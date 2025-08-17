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
package fr.neatmonster.nocheatplus.compat.bukkit.model;

import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.BlockState;
import org.bukkit.block.data.BlockData;
import org.bukkit.block.data.Levelled;
import org.bukkit.block.data.Waterlogged;

import fr.neatmonster.nocheatplus.utilities.map.BlockCache;
import fr.neatmonster.nocheatplus.utilities.map.BlockFlags;

public class BukkitLevelled implements BukkitShapeModel {

    @Override
    public double[] getShape(BlockCache blockCache, World world, int x, int y, int z) {
        final Block blockAbove = world.getBlockAt(x, y + 1, z);
        final BlockData blockDataAbove = blockAbove.getBlockData();
        if ((BlockFlags.getBlockFlags(world.getBlockAt(x, y, z).getType()) & BlockFlags.F_WATER) != 0 
            && (blockDataAbove instanceof Waterlogged && ((Waterlogged)blockDataAbove).isWaterlogged())) {
            return new double[] {0.0, 0.0, 0.0, 1.0, 1.0, 1.0};
        }
        return new double[] {0.0, 0.0, 0.0, 1.0, 8 / 9f, 1.0};
    }

    @Override
    public int getFakeData(BlockCache blockCache, World world, int x, int y, int z) {
        final Block block = world.getBlockAt(x, y, z);
        final BlockState state = block.getState();
        final BlockData blockData = state.getBlockData();
        if (blockData instanceof Levelled) {
            return ((Levelled)blockData).getLevel();
        }
        return 0;
    }
}