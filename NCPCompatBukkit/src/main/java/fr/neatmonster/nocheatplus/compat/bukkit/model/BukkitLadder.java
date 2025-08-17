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
import org.bukkit.block.data.Directional;
import org.bukkit.util.BoundingBox;

import fr.neatmonster.nocheatplus.utilities.map.BlockCache;

public class BukkitLadder implements BukkitShapeModel {

    @Override
    public double[] getShape(final BlockCache blockCache, final World world, final int x, final int y, final int z) {
        final Block block = world.getBlockAt(x, y, z);
        BoundingBox box = block.getBoundingBox();
        return new double[] {box.getMinX()-x, box.getMinY()-y, box.getMinZ()-z, box.getMaxX()-x, box.getMaxY()-y, box.getMaxZ()-z};
    }

    @Override
    public int getFakeData(final BlockCache blockCache, final World world, final int x, final int y, final int z) {
        final Block block = world.getBlockAt(x, y, z);
        final BlockState state = block.getState();
        final BlockData blockData = state.getBlockData();
        if (blockData instanceof Directional) {
            final Directional ladder = (Directional) blockData;
            switch(ladder.getFacing()) {
                case EAST: 
                    return 5;
                case NORTH: 
                    return 6;
                case SOUTH: 
                    return 3;
                case WEST: 
                    return 4;
                default:
                    break;
            }
        }
        return 0;
    }
}