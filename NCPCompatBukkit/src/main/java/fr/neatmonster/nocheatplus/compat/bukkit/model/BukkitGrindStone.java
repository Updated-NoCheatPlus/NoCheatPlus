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
import org.bukkit.block.data.type.Grindstone;

import fr.neatmonster.nocheatplus.utilities.map.BlockCache;

public class BukkitGrindStone implements BukkitShapeModel {

    @Override
    public double[] getShape(BlockCache blockCache, World world, int x, int y, int z) {
        final Block block = world.getBlockAt(x, y, z);
        final BlockData blockData = block.getState().getBlockData();
        if (blockData instanceof Grindstone) {
            final Grindstone grindstone = (Grindstone) blockData;
            final BlockFace facing = grindstone.getFacing();
            switch (grindstone.getAttachedFace()) {
                case CEILING:
                    switch (facing) {
                        case NORTH:
                        case SOUTH:
                            return new double[] {0.125, 0.5625, 0.375, 0.25, 1.0, 0.625,
                                                0.75, 0.5625, 0.375, 0.875, 1.0, 0.625,
                                                0.125, 0.1875, 0.3125, 0.25, 0.5625, 0.6875,
                                                0.75, 0.1875, 0.3125, 0.875, 0.5625, 0.6875,
                                                0.25, 0.0, 0.125, 0.75, 0.75, 0.875};
                        case EAST:
                        case WEST:
                            return new double[] {0.375, 0.5625, 0.125, 0.625, 1.0, 0.25,
                                                0.375, 0.5625, 0.75, 0.625, 1.0, 0.875,
                                                0.3125, 0.1875, 0.125, 0.6875, 0.5625, 0.25,
                                                0.3125, 0.1875, 0.75, 0.6875, 0.5625, 0.875,
                                                0.125, 0.0, 0.25, 0.875, 0.75, 0.75};

                        default: 
                            return new double[] {0.0, 0.0, 0.0, 1.0, 1.0, 1.0};
                    }
                case FLOOR:
                    switch (facing) {
                        case NORTH:
                        case SOUTH:
                            return new double[] {0.125, 0.0, 0.375, 0.25, 0.4375, 0.625,
                                                0.75, 0.0, 0.375, 0.875, 0.4375, 0.625,
                                                0.125, 0.4375, 0.3125, 0.25, 0.8125, 0.6875,
                                                0.75, 0.4375, 0.3125, 0.875, 0.8125, 0.6875,
                                                0.25, 0.25, 0.125, 0.75, 1.0, 0.875};
                        case EAST:
                        case WEST:
                            return new double[] {0.375, 0.0, 0.125, 0.625, 0.4375, 0.25,
                                                0.375, 0.0, 0.75, 0.625, 0.4375, 0.875,
                                                0.3125, 0.4375, 0.125, 0.6875, 0.8125, 0.25,
                                                0.3125, 0.4375, 0.75, 0.6875, 0.8125, 0.875,
                                                0.125, 0.25, 0.25, 0.875, 1.0, 0.75};

                        default: 
                            return new double[] {0.0, 0.0, 0.0, 1.0, 1.0, 1.0};
                    }
                case WALL:
                    switch (facing) {
                        case NORTH:
                            return new double[] {0.125, 0.375, 0.4375, 0.25, 0.625, 1.0,
                                            0.75, 0.375, 0.4375, 0.875, 0.625, 1.0,
                                            0.125, 0.3125, 0.1875, 0.25, 0.6875, 0.5625,
                                            0.75, 0.3125, 0.1875, 0.875, 0.6875, 0.5625,
                                            0.25, 0.125, 0.0, 0.75, 0.875, 0.75};
                        case SOUTH:
                            return new double[] {0.125, 0.375, 0.0, 0.25, 0.625, 0.4375,
                                            0.75, 0.375, 0.0, 0.875, 0.625, 0.4375,
                                            0.125, 0.3125, 0.4375, 0.25, 0.6875, 0.8125,
                                            0.75, 0.3125, 0.4375, 0.875, 0.6875, 0.8125,
                                            0.25, 0.125, 0.25, 0.75, 0.875, 1.0};
                        case EAST:
                            return new double[] {0.0, 0.375, 0.125, 0.5625, 0.625, 0.25,
                                            0.0, 0.375, 0.75, 0.5625, 0.625, 0.875,
                                            0.4375, 0.3125, 0.125, 0.8125, 0.6875, 0.25,
                                            0.4375, 0.3125, 0.75, 0.8125, 0.6875, 0.875,
                                            0.25, 0.125, 0.25, 1.0, 0.875, 0.75};
                        case WEST:
                            return new double[] {0.4375, 0.375, 0.125, 1.0, 0.625, 0.25,
                                            0.4375, 0.375, 0.75, 1.0, 0.625, 0.875,
                                            0.1875, 0.3125, 0.125, 0.5625, 0.6875, 0.25,
                                            0.1875, 0.3125, 0.75, 0.5625, 0.6875, 0.875,
                                            0.0, 0.125, 0.25, 0.75, 0.875, 0.75};
                        default: 
                            break;
                    }
            }
        }
        return new double[] {0.0, 0.0, 0.0, 1.0, 1.0, 1.0};
    }

    @Override
    public int getFakeData(BlockCache blockCache, World world, int x, int y, int z) {
        return 0;
    }
}