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

import java.util.HashSet;
import java.util.Set;

import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.block.data.BlockData;
import org.bukkit.block.data.MultipleFacing;
import org.bukkit.util.BoundingBox;

import fr.neatmonster.nocheatplus.compat.Bridge1_13;
import fr.neatmonster.nocheatplus.utilities.collision.AxisAlignedBBUtils;
import fr.neatmonster.nocheatplus.utilities.collision.ShapeUtils;
import fr.neatmonster.nocheatplus.utilities.map.BlockCache;

public class BukkitChorusPlant implements BukkitShapeModel {
    
    private static final BlockFace[] directions = new BlockFace[]{BlockFace.NORTH, BlockFace.EAST, BlockFace.SOUTH, BlockFace.WEST, BlockFace.UP, BlockFace.DOWN};
    private static final double[][] modernShapes = makeShapes();

    @Override
    public double[] getShape(final BlockCache blockCache, final World world, final int x, final int y, final int z) {
        final Block block = world.getBlockAt(x, y, z);
        // Server is 1.13.2 higher
        if (Bridge1_13.hasBuiltInRayTracing()) {
            double[] res = {};
            for (BoundingBox box : block.getCollisionShape().getBoundingBoxes()) {
                res = ShapeUtils.add(res, AxisAlignedBBUtils.toArray(box));
            }
            if (res.length == 0) return null;
            return res;
        } else {
        // Server is 1.13
            final BlockData blockData = block.getBlockData();
            if (blockData instanceof MultipleFacing) {
                final MultipleFacing chorusplant = (MultipleFacing) blockData;
                return modernShapes[getAABBIndex(chorusplant.getFaces())];
            }
        }
        
        return new double[] {0,0,0,1.0,1.0,1.0};
    }
    
    private static double[][] makeShapes() {
        float min = 0.5F - (float) 0.3125;
        float max = 0.5F + (float) 0.3125;
        double[] base = {min, min, min, max, max, max};
        double[][] cs = new double[directions.length][];
        
        for (int i = 0; i < directions.length; i++) {
            BlockFace dir = directions[i];
            cs[i] = new double[] {0.5D + Math.min(-(float) 0.3125, (double) dir.getModX() * 0.5D), 0.5D + Math.min(-(float) 0.3125, (double) dir.getModY() * 0.5D), 0.5D + Math.min(-(float) 0.3125, (double) dir.getModZ() * 0.5D), 
                                  0.5D + Math.max((float) 0.3125, (double) dir.getModX() * 0.5D), 0.5D + Math.max((float) 0.3125, (double) dir.getModY() * 0.5D), 0.5D + Math.max((float) 0.3125, (double) dir.getModZ() * 0.5D)
                                  }; 
        }
        
        double[][] cs2 = new double[64][];
        for (int k = 0; k < 64; k++) {
            double[] tmp = base;
            
            for (int j = 0; j < directions.length; j++) {
                if ((k & 1 << j) != 0) {
                    tmp = ShapeUtils.merge(tmp, cs[j]);
                }
            }
            cs2[k] = tmp;
        }
        return cs2;
    }

    private Set<BlockFace> getLegacyFaces(BlockCache blockCache, World world, int x, int y, int z) {
        Set<BlockFace> faces = new HashSet<>();
        Material upBlock = blockCache.getType(x, y+1, z);
        Material downBlock = blockCache.getType(x, y-1, z);
        Material northBlock = blockCache.getType(x, y, z-1);
        Material southBlock = blockCache.getType(x, y, z+1);
        Material westBlock = blockCache.getType(x-1, y, z);
        Material eastBlock = blockCache.getType(x+1, y, z);
        if (downBlock == Material.CHORUS_PLANT || downBlock == Material.END_STONE || downBlock == Material.CHORUS_FLOWER) {
            faces.add(BlockFace.DOWN);
        }
        if (upBlock == Material.CHORUS_PLANT || upBlock == Material.CHORUS_FLOWER) {
            faces.add(BlockFace.UP);
        }
        if (northBlock == Material.CHORUS_PLANT || northBlock == Material.CHORUS_FLOWER) {
            faces.add(BlockFace.NORTH);
        }
        if (southBlock == Material.CHORUS_PLANT || southBlock == Material.CHORUS_FLOWER) {
            faces.add(BlockFace.SOUTH);
        }
        if (westBlock == Material.CHORUS_PLANT || westBlock == Material.CHORUS_FLOWER) {
            faces.add(BlockFace.WEST);
        }
        if (eastBlock == Material.CHORUS_PLANT || eastBlock == Material.CHORUS_FLOWER) {
            faces.add(BlockFace.EAST);
        }
        return faces;
    }
    
    private int getAABBIndex(Set<BlockFace> faces) {
        int i = 0;

        for (int j = 0; j < directions.length; ++j) {
            if (faces.contains(directions[j])) {
                i |= 1 << j;
            }
        }
        return i;
    }

    @Override
    public int getFakeData(final BlockCache blockCache, final World world, final int x, final int y, final int z) {
        return 0;
    }

    @Override
    public double[] getVisualShape(BlockCache blockCache, World world, int x, int y, int z) {
        return getShape(blockCache, world, x, y, z);
    }

    @Override
    public boolean isCollisionSameVisual(BlockCache blockCache, World world, int x, int y, int z) {
        return true;
    }
}