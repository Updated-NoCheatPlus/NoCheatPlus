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

public class BukkitHopper implements BukkitShapeModel {

    @Override
    public double[] getShape(BlockCache blockCache, World world, int x, int y, int z) {
        final Block block = world.getBlockAt(x, y, z);
        final BlockData blockData = block.getBlockData();
        if (blockData instanceof Directional) {
            Directional b = (Directional) blockData;
            BlockFace face = b.getFacing();
            switch (face) {
                case NORTH:
                    return new double[] {
                        // Standing inside
                        0.0, 0.625, 0.0, 1.0, 0.6875, 1.0,
                        // Middle
                        0.25, 0.25, 0.25, 0.75, 0.625, 0.75,
                        // Bottom
                        0.375, 0.25, 0.0, 0.625, 0.5, 0.25,
                        // Top
                        0.0, 0.6875, 0.0, 1.0, 1.0, 1.0
                        // 4 sides of hopper (top)
                        //0.0, 0.6875, 0.0, 1.0, 1.0, 0.125,
                        //0.0, 0.6875, 0.875, 1.0, 1.0, 1.0,
                        //0.0, 0.6875, 0.0, 0.125, 1.0, 1.0,
                        //0.875, 0.6875, 0.0, 1.0, 1.0, 1.0,
                        };
                case SOUTH:
                    return new double[] {
                        // Standing inside
                        0.0, 0.625, 0.0, 1.0, 0.6875, 1.0,
                        // Middle
                        0.25, 0.25, 0.25, 0.75, 0.625, 0.75,
                        // Bottom
                        0.375, 0.25, 0.75, 0.625, 0.5, 1.0,
                        // Top
                        0.0, 0.6875, 0.0, 1.0, 1.0, 1.0
                        // 4 sides of hopper (top)
                        //0.0, 0.6875, 0.0, 1.0, 1.0, 0.125,
                        //0.0, 0.6875, 0.875, 1.0, 1.0, 1.0,
                        //0.0, 0.6875, 0.0, 0.125, 1.0, 1.0,
                        //0.875, 0.6875, 0.0, 1.0, 1.0, 1.0,
                        };
                case WEST:
                    return new double[] {
                        // Standing inside
                        0.0, 0.625, 0.0, 1.0, 0.6875, 1.0,
                        // Middle
                        0.25, 0.25, 0.25, 0.75, 0.625, 0.75,
                        // Bottom
                        0.0, 0.25, 0.375, 0.25, 0.5, 0.625,
                        // Top
                        0.0, 0.6875, 0.0, 1.0, 1.0, 1.0
                        // 4 sides of hopper (top)
                        //0.0, 0.6875, 0.0, 1.0, 1.0, 0.125,
                        //0.0, 0.6875, 0.875, 1.0, 1.0, 1.0,
                        //0.0, 0.6875, 0.0, 0.125, 1.0, 1.0,
                        //0.875, 0.6875, 0.0, 1.0, 1.0, 1.0,
                        };
                case EAST:
                    return new double[] {
                        // Standing inside
                        0.0, 0.625, 0.0, 1.0, 0.6875, 1.0,
                        // Middle
                        0.25, 0.25, 0.25, 0.75, 0.625, 0.75,
                        // Bottom
                        0.75, 0.25, 0.375, 1.0, 0.5, 0.625,
                        // Top
                        0.0, 0.6875, 0.0, 1.0, 1.0, 1.0
                        // 4 sides of hopper (top)
                        //0.0, 0.6875, 0.0, 1.0, 1.0, 0.125,
                        //0.0, 0.6875, 0.875, 1.0, 1.0, 1.0,
                        //0.0, 0.6875, 0.0, 0.125, 1.0, 1.0,
                        //0.875, 0.6875, 0.0, 1.0, 1.0, 1.0,
                        };
                case DOWN:
                    return new double[] {
                        // Standing inside
                        0.0, 0.625, 0.0, 1.0, 0.6875, 1.0,
                        // Middle
                        0.25, 0.25, 0.25, 0.75, 0.625, 0.75,
                        // Bottom
                        0.375, 0.0, 0.375, 0.625, 0.25, 0.625,
                        // Top
                        0.0, 0.6875, 0.0, 1.0, 1.0, 1.0
                        // 4 sides of hopper (top)
                        //0.0, 0.6875, 0.0, 1.0, 1.0, 0.125,
                        //0.0, 0.6875, 0.875, 1.0, 1.0, 1.0,
                        //0.0, 0.6875, 0.0, 0.125, 1.0, 1.0,
                        //0.875, 0.6875, 0.0, 1.0, 1.0, 1.0,
                        };
                default:
                    break;
            }
        }
        return new double[] {0.0, 0.0, 0.0, 1.0, 1.0, 1.0};
    }

    @Override
    public int getFakeData(BlockCache blockCache, World world, int x, int y, int z) {
        return 0;
    }
}