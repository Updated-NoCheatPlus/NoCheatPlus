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
import org.bukkit.block.data.Bisected.Half;
import org.bukkit.block.data.BlockData;
import org.bukkit.block.data.type.TrapDoor;

import fr.neatmonster.nocheatplus.utilities.map.BlockCache;

public class BukkitTrapDoor implements BukkitShapeModel {

    private static final double closedHeight = 0.1875;
    private static final double openWidth = 0.1875;

    @Override
    public double[] getShape(final BlockCache blockCache, final World world, final int x, final int y, final int z) {
        final Block block = world.getBlockAt(x, y, z);
        final BlockState state = block.getState();
        final BlockData blockData = state.getBlockData();
        if (blockData instanceof TrapDoor) {
            final TrapDoor trapDoor = (TrapDoor) blockData;
            if (trapDoor.isOpen()) {
                switch (trapDoor.getFacing()) {
                    case NORTH:
                        return new double[] {0.0, 0.0, 1.0 - openWidth, 1.0, 1.0, 1.0};
                    case SOUTH:
                        return new double[] {0.0, 0.0, 0.0, 1.0, 1.0, openWidth};
                    case EAST:
                        return new double[] {0.0, 0.0, 0.0, openWidth, 1.0, 1.0};
                    case WEST:
                        return new double[] {1.0 - openWidth, 0.0, 0.0, 1.0, 1.0, 1.0};
                    default:
                        break;
                }
            }
            return trapDoor.getHalf() == Half.BOTTOM ? new double[] {0.0, 0.0, 0.0, 1.0, closedHeight, 1.0}
                                                     : new double[] {0.0, 1.0 - closedHeight, 0.0, 1.0, 1.0, 1.0};
        }
        return new double[] {0.0, 0.0, 0.0, 1.0, 1.0, 1.0};
    }

    @Override
    public int getFakeData(final BlockCache blockCache, final World world, final int x, final int y, final int z) {
        final Block block = world.getBlockAt(x, y, z);
        final BlockState state = block.getState();
        final BlockData blockData = state.getBlockData();
        if (blockData instanceof TrapDoor) {
            final TrapDoor trapDoor = (TrapDoor) blockData;
            if (trapDoor.isOpen()) {
        	    switch(trapDoor.getFacing()) {
                case EAST: 
                    return 3;
                case NORTH: 
                    return 4;
                case SOUTH: 
                    return 5;
                case WEST: 
                    return 6;
                default:
                    break;
                }
            }
        }
        return 0;
    }
}