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
import org.bukkit.block.data.type.Chest;
import org.bukkit.block.data.type.Chest.Type;

import fr.neatmonster.nocheatplus.utilities.map.BlockCache;

public class BukkitChest implements BukkitShapeModel {
    //TODO: With/without this, NCP still throw false when nearby doubled chest. Something else was wrong. Do note flags are correct too: (CHEST / SOLID+GROUND) BlockProps(2.5 / ToolProps(AXE/WOOD) / [3750, 1900, 950, 750, 600, 450, 400])
    private final double[] NORTH_AABB = new double[] {0.0625, 0.0, 0.0, 0.9375, 0.875, 0.9375};
    private final double[] SOUTH_AABB = new double[] {0.0625, 0.0, 0.0625, 0.9375, 0.875, 1.0};
    private final double[] WEST_AABB  = new double[] {0.0, 0.0, 0.0625, 0.9375, 0.875, 0.9375};
    private final double[] EAST_AABB  = new double[] {0.0625, 0.0, 0.0625, 1.0, 0.875, 0.9375};
    private final double[] SINGLE     = new double[] {0.0625, 0.0, 0.0625, 0.9375, 0.875, 0.9375};

    public static BlockFace getConnectedDirection(BlockFace facing, Type chestType) {
        return chestType == Type.LEFT ? getClockWise(facing) : getCounterClockWise(facing);
    }
    public static BlockFace getCounterClockWise(BlockFace dir) {
        switch (dir) {
            case NORTH: return BlockFace.WEST;
            case SOUTH: return BlockFace.EAST;
            case WEST: return BlockFace.SOUTH;
            case EAST: return BlockFace.NORTH;
            default: throw new IllegalStateException("Unable to get CCW facing of " + dir);
        }
    }

    public static BlockFace getClockWise(BlockFace dir) {
        switch (dir) {
            case NORTH: return BlockFace.EAST;
            case SOUTH: return BlockFace.WEST;
            case WEST: return BlockFace.NORTH;
            case EAST: return BlockFace.SOUTH;
            default: throw new IllegalStateException("Unable to get Y-rotated facing of " + dir);
        }
    }
    
    @Override
    public double[] getShape(BlockCache blockCache, World world, int x, int y, int z) {
        final Block block = world.getBlockAt(x, y, z);
        final BlockState state = block.getState();
        final BlockData blockData = state.getBlockData();
        if (blockData instanceof Chest) {
            final Chest chest = (Chest) blockData;
            final Type chestType = chest.getType();
            if (chestType == Type.SINGLE) {
                return SINGLE;
            }
            switch (getConnectedDirection(chest.getFacing(), chestType)) {
            case NORTH:
            default:
                return NORTH_AABB;
            case SOUTH:
                return SOUTH_AABB;
            case WEST:
                return WEST_AABB;
            case EAST:
                return EAST_AABB;
        }
        }
        return SINGLE;
    }

    @Override
    public boolean isCollisionSameVisual(BlockCache blockCache, World world, int x, int y, int z) {
        return true;
    }

    @Override
    public double[] getVisualShape(BlockCache blockCache, World world, int x, int y, int z) {
        return getShape(blockCache, world, x, y, z);
    }

    @Override
    public int getFakeData(BlockCache blockCache, World world, int x, int y, int z) {
        return 0;
    }

}
