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

public class BukkitPistonHead implements BukkitShapeModel {

    @Override
    public double[] getShape(BlockCache blockCache, World world, int x, int y, int z) {
        final Block block = world.getBlockAt(x, y, z);
        final BlockData blockData = block.getBlockData();
        if (blockData instanceof Directional) {
            Directional b = (Directional) blockData;
            BlockFace face = b.getFacing();
            switch (face) {
                case UP: 
                    return new double[] {
                        // Shaft
                        0.375, 0.0, 0.375, 0.625, 1.0, 0.625,
                        // Plank
                        0.0, 0.75, 0.0, 1.0, 1.0, 1.0
                        };
                case DOWN: 
                    return new double[] {
                        // Shaft
                        0.375, 0.0, 0.375, 0.625, 1.0, 0.625,
                        // Plank
                        0.0, 0.0, 0.0, 1.0, 0.25, 1.0
                        };
                case NORTH: 
                    return new double[] {
                        // Shaft
                        0.375, 0.375, 0.0, 0.625, 0.625, 1.0,
                        // Plank
                        0.0, 0.0, 0.0, 1.0, 1.0, 0.25
                        };
                case SOUTH: 
                    return new double[] {
                        // Shaft
                        0.375, 0.375, 0.0, 0.625, 0.625, 1.0,
                        // Plank
                        0.0, 0.0, 0.75, 1.0, 1.0, 1.0
                        };
                case WEST: 
                    return new double[] {
                        // Shaft
                        0.0, 0.375, 0.375, 1.0, 0.625, 0.625,
                        // Plank
                        0.0, 0.0, 0.0, 0.25, 1.0, 1.0
                        };
                case EAST: 
                    return new double[] {
                        // Shaft
                        0.0, 0.375, 0.375, 1.0, 0.625, 0.625,
                        // Plank
                        0.75, 0.0, 0.0, 1.0, 1.0, 1.0
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