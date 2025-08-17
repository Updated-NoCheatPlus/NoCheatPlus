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
import org.bukkit.block.data.type.Bell;

import fr.neatmonster.nocheatplus.utilities.map.BlockCache;

public class BukkitBell implements BukkitShapeModel {

    @Override
    public double[] getShape(final BlockCache blockCache, final World world, final int x, final int y, final int z) {
        final Block block = world.getBlockAt(x, y, z);
        final BlockData blockData = block.getBlockData();
        if (blockData instanceof Bell) {
            Bell b = (Bell) blockData;
            BlockFace face = b.getFacing();
            switch (b.getAttachment()) {
            case CEILING: 
                return new double[] {0.25, 1 - 0.75, 0.25, 0.75, 1.0, 0.75};
            case DOUBLE_WALL:
                switch (face) {
                    case NORTH:
                    case SOUTH:
                        return new double[] {
                            // Bottom bell
                            0.25, 0.25, 0.25, 0.75, 0.375, 0.75,
                            // Body
                            0.3125, 0.375, 0.3125, 0.6875, 0.8125, 0.6875,
                            // Stick holding the bell
                            0.4375, 0.8125, 0.0, 0.5625, 0.9375, 1.0
                            //0.0, 0.8125, 0.4375, 1.0, 0.9375, 0.5625
                            };
                    case WEST:
                    case EAST:
                        return new double[] {
                            // Bottom bell
                            0.25, 0.25, 0.25, 0.75, 0.375, 0.75,
                            // Body
                            0.3125, 0.375, 0.3125, 0.6875, 0.8125, 0.6875,
                            // Stick holding the bell
                            0.0, 0.8125, 0.4375, 1.0, 0.9375, 0.5625
                            };
                    default:
                        break;
                }
                break;
            case FLOOR:
                switch (face) {
                    case NORTH:
                    case SOUTH:
                        return new double[] {0.0, 0.0, 0.25, 1.0, 1.0, 0.75};
                    case WEST:
                    case EAST:
                        return new double[] {0.25, 0.0, 0.0, 0.75, 1.0, 1.0};
                    default:
                        break;
                }
                break;
            case SINGLE_WALL:
                switch (face) {
                    case NORTH: 
                        return new double[] {
                            // Bottom bell
                            0.25, 0.25, 0.25, 0.75, 0.375, 0.75,
                            // Body
                            0.3125, 0.375, 0.3125, 0.6875, 0.8125, 0.6875,
                            // Stick holding the bell
                            0.4375, 0.8125, 0.0, 0.5625, 0.9375, 0.8125
                            };
                    case SOUTH: 
                        return new double[] {
                            // Bottom bell
                            0.25, 0.25, 0.25, 0.75, 0.375, 0.75,
                            // Body
                            0.3125, 0.375, 0.3125, 0.6875, 0.8125, 0.6875,
                            // Stick holding the bell
                            0.4375, 0.8125, 0.1875, 0.5625, 0.9375, 1.0
                            };
                    case WEST: 
                        return new double[] {
                            // Bottom bell
                            0.25, 0.25, 0.25, 0.75, 0.375, 0.75,
                            // Body
                            0.3125, 0.375, 0.3125, 0.6875, 0.8125, 0.6875,
                            // Stick holding the bell
                            0.0, 0.8125, 0.4375, 0.8125, 0.9375, 0.5625
                            };
                    case EAST: 
                        return new double[] {
                            // Bottom bell
                            0.25, 0.25, 0.25, 0.75, 0.375, 0.75,
                            // Body
                            0.3125, 0.375, 0.3125, 0.6875, 0.8125, 0.6875,
                            // Stick holding the bell
                            0.1875, 0.8125, 0.4375, 1.0, 0.9375, 0.5625
                            };
                    default:
                        break;
                }
            }
        }
        return new double[] {0.0, 0.0, 0.0, 1.0, 1.0, 1.0};
    }

    @Override
    public int getFakeData(final BlockCache blockCache, final World world, final int x, final int y, final int z) {
        return 0;
    }
}