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
import org.bukkit.util.BoundingBox;

import fr.neatmonster.nocheatplus.utilities.map.BlockCache;

/**
 * Fetches a single BoundingBox from Bukkit's Block#getBoundingBox().
 * Returns a six-element double[] {minX, minY, minZ, maxX, maxY, maxZ}
 * relative to the block position (world coords minus the block's x,y,z).
 *
 * This class returns one bounding box; BukkitFetchableBounds reads the VoxelShape and may return multiple boxes for complex
 * collision shapes.
 */
public class BukkitFetchableBound implements BukkitShapeModel {

    @Override
    public double[] getShape(BlockCache blockCache, World world, int x, int y, int z) {
        final Block block = world.getBlockAt(x, y, z);
        BoundingBox box = block.getBoundingBox();
        return new double[] {box.getMinX()-x, box.getMinY()-y, box.getMinZ()-z, box.getMaxX()-x, box.getMaxY()-y, box.getMaxZ()-z};
    }

    @Override
    public int getFakeData(BlockCache blockCache, World world, int x, int y, int z) {
        return 0;
    }
}
