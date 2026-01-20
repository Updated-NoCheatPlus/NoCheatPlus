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
import org.bukkit.block.data.Directional;

import fr.neatmonster.nocheatplus.utilities.collision.ShapeUtils;
import fr.neatmonster.nocheatplus.utilities.map.BlockCache;

/**
 * Model for wooden shelves.
 */
public class BukkitWoodenShelf implements BukkitShapeModel {
    
    // Shelf thickness and depths
    // These constants match vanilla ShelfBlock voxel boxes (normalized 0..1):
    // Top slab: y 12/16..16/16 (0.75..1.0)
    // Bottom slab: y 0/16..4/16 (0.0..0.25)
    // Shelf (slab) depth: 2/16 = 0.125
    // Back plate depth: 3/16 = 0.1875 (full-height)
    private static final double TOP_MIN_Y = 12.0 / 16.0; // 0.75
    private static final double TOP_MAX_Y = 16.0 / 16.0; // 1.0
    private static final double BOTTOM_MIN_Y = 0.0;
    private static final double BOTTOM_MAX_Y = 4.0 / 16.0; // 0.25
    private static final double SHELF_DEPTH = 2.0 / 16.0; // 0.125
    private static final double BACK_DEPTH = 3.0 / 16.0; // 0.1875
    
    @Override
    public double[] getShape(final BlockCache blockCache, final World world, final int x, final int y, final int z) {
        final Block block = world.getBlockAt(x, y, z);
        
        // Shelf shape: two slabs + back plate attached to facing side
        final BlockState state = block.getState();
        final BlockData blockData = state.getBlockData();
        BlockFace facing = BlockFace.SELF;
        if (blockData instanceof Directional) {
            facing = ((Directional) blockData).getFacing();
        }
        
        double[] res = {};
        
        // Top slab
        double[] topBox;
        switch (facing) {
            case NORTH:
                topBox = new double[] {0.0, TOP_MIN_Y, 0.0, 1.0, TOP_MAX_Y, SHELF_DEPTH};
                break;
            case SOUTH:
                topBox = new double[] {0.0, TOP_MIN_Y, 1.0 - SHELF_DEPTH, 1.0, TOP_MAX_Y, 1.0};
                break;
            case EAST:
                topBox = new double[] {1.0 - SHELF_DEPTH, TOP_MIN_Y, 0.0, 1.0, TOP_MAX_Y, 1.0};
                break;
            case WEST:
                topBox = new double[] {0.0, TOP_MIN_Y, 0.0, SHELF_DEPTH, TOP_MAX_Y, 1.0};
                break;
            case UP:
            case DOWN:
            default:
                topBox = new double[] {0.0, TOP_MIN_Y, 0.0, 1.0, TOP_MAX_Y, 1.0};
                break;
        }
        res = ShapeUtils.add(res, topBox);
        
        // Bottom slab
        double[] bottomBox;
        switch (facing) {
            case NORTH:
                bottomBox = new double[] {0.0, BOTTOM_MIN_Y, 0.0, 1.0, BOTTOM_MAX_Y, SHELF_DEPTH};
                break;
            case SOUTH:
                bottomBox = new double[] {0.0, BOTTOM_MIN_Y, 1.0 - SHELF_DEPTH, 1.0, BOTTOM_MAX_Y, 1.0};
                break;
            case EAST:
                bottomBox = new double[] {1.0 - SHELF_DEPTH, BOTTOM_MIN_Y, 0.0, 1.0, BOTTOM_MAX_Y, 1.0};
                break;
            case WEST:
                bottomBox = new double[] {0.0, BOTTOM_MIN_Y, 0.0, SHELF_DEPTH, BOTTOM_MAX_Y, 1.0};
                break;
            case UP:
            case DOWN:
            default:
                bottomBox = new double[] {0.0, BOTTOM_MIN_Y, 0.0, 1.0, BOTTOM_MAX_Y, 1.0};
                break;
        }
        res = ShapeUtils.add(res, bottomBox);
        
        // Back plate (full height)
        double[] backBox;
        switch (facing) {
            case NORTH:
                backBox = new double[] {0.0, 0.0, 0.0, 1.0, 1.0, BACK_DEPTH};
                break;
            case SOUTH:
                backBox = new double[] {0.0, 0.0, 1.0 - BACK_DEPTH, 1.0, 1.0, 1.0};
                break;
            case EAST:
                backBox = new double[] {1.0 - BACK_DEPTH, 0.0, 0.0, 1.0, 1.0, 1.0};
                break;
            case WEST:
                backBox = new double[] {0.0, 0.0, 0.0, BACK_DEPTH, 1.0, 1.0};
                break;
            case UP:
            case DOWN:
            default:
                backBox = new double[] {0.0, 0.0, 0.0, 1.0, 1.0, 1.0};
                break;
        }
        res = ShapeUtils.add(res, backBox);
        
        return res.length == 0.0 ? new double[]{0.0,0.0,0.0,1.0,1.0,1.0} : res; // Defensive
    }

    @Override
    public double[] getVisualShape(BlockCache blockCache, World world, int x, int y, int z) {
        return getShape(blockCache, world, x, y, z);
    }

    @Override
    public boolean isCollisionSameVisual(BlockCache blockCache, World world, int x, int y, int z) {
        return true;
    }

    @Override
    public int getFakeData(final BlockCache blockCache, final World world, final int x, final int y, final int z) {
        return 0;
    }
}
