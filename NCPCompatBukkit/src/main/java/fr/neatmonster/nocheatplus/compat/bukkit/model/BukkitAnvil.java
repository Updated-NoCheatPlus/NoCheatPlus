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
import org.bukkit.block.BlockFace;
import org.bukkit.block.data.BlockData;
import org.bukkit.block.data.Directional;

import fr.neatmonster.nocheatplus.utilities.map.BlockCache;

public class BukkitAnvil implements BukkitShapeModel {

    @Override
    public double[] getShape(final BlockCache blockCache, final World world, final int x, final int y, final int z) {
        final Block block = world.getBlockAt(x, y, z);
        final BlockData blockData = block.getBlockData();
        if (blockData instanceof Directional) {
            BlockFace face = ((Directional) blockData).getFacing();
            switch (face) {
                case NORTH:
                case SOUTH:
                    return new double[] {
                        // Bottom
                        0.125, 0.0, 0.125, 0.875, 0.25, 0.875,
                        // Top
                        0.1875, 0.625, 0.0, 0.8125, 1.0, 1.0
                        // Body... maybe not need
                        };
                case WEST:
                case EAST:
                    return new double[] {
                        // Bottom
                        0.125, 0.0, 0.125, 0.875, 0.25, 0.875,
                        // Top
                        0.0, 0.625, 0.1875, 1.0, 1.0, 0.8125
                        // Body... maybe not need
                        };
                default:
                    break;
            }
        }
        return new double[] {0.0, 0.0, 0.0, 1.0, 1.0, 1.0};
    }

    @Override
    public int getFakeData(final BlockCache blockCache, final World world, final int x, final int y, final int z) {
        return 0;
    }   
}
