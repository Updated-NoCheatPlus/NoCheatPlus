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
import org.bukkit.block.BlockState;
import org.bukkit.block.data.BlockData;
import org.bukkit.block.data.type.Door;
import org.bukkit.block.data.type.Door.Hinge;

import fr.neatmonster.nocheatplus.utilities.map.BlockCache;

public class BukkitDoor implements BukkitShapeModel {

    /**
     * 
     * @param facing
     * @param hinge
     * @param isOpen
     * @return The block face of the block the door is standing on, indicating
     *         where the door is.
     */
    private static final BlockFace getWhereTheDoorIsFace(final BlockFace facing, final Hinge hinge, final boolean isOpen) {
        // For idiots:
        /*
         * Facing: Door is closing the opposite side of the block, so the
         * "outside" is south.
         */
        /*
         * Hinge: Looking into the facing direction, hinge is left or right.
         */
        // Let's play north and south: 
        switch (facing) {
            case NORTH:
                if (isOpen) {
                    if (hinge == Hinge.LEFT) {
                        return BlockFace.WEST;
                    }
                    return BlockFace.EAST;
                }
                return BlockFace.SOUTH;
            case SOUTH:
                if (isOpen) {
                    if (hinge == Hinge.LEFT) {
                        return BlockFace.EAST;
                    }
                    return BlockFace.WEST;
                }
                return BlockFace.NORTH;
            case EAST:
                if (isOpen) {
                    if (hinge == Hinge.LEFT) {
                        return BlockFace.NORTH;
                    }
                    return BlockFace.SOUTH;
                }
                return BlockFace.WEST;
            case WEST:
                if (isOpen) {
                    if (hinge == Hinge.LEFT) {
                        return BlockFace.SOUTH;
                    }
                    return BlockFace.NORTH;
                }
                return BlockFace.EAST;
            default:
                // Invalid
                return BlockFace.SELF;
        }
    }

    //private static final double doorWidthClosed = 0.2125;
    private static final double doorWidthOpen = 0.1875;

    @Override
    public double[] getShape(final BlockCache blockCache, final World world, final int x, final int y, final int z) {
        final Block block = world.getBlockAt(x, y, z);
        final BlockState state = block.getState();
        final BlockData blockData = state.getBlockData();
        if (blockData instanceof Door) {
            final Door door = (Door) blockData;
            final boolean isOpen = door.isOpen();
            final double doorWidth = doorWidthOpen; // isOpen ? doorWidthOpen : doorWidthClosed;
            switch (getWhereTheDoorIsFace(door.getFacing(), door.getHinge(), isOpen)) {
                case NORTH:
                    return new double[] {0.0, 0.0, 0.0, 1.0, 1.0, doorWidth};
                case SOUTH:
                    return new double[] {0.0, 0.0, 1.0 - doorWidth, 1.0, 1.0, 1.0};
                case EAST:
                    return new double[] {1.0 - doorWidth, 0.0, 0.0, 1.0, 1.0, 1.0};
                case WEST:
                    return new double[] {0.0, 0.0, 0.0, doorWidth, 1.0, 1.0};
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
