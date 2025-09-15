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
import org.bukkit.util.VoxelShape;

import fr.neatmonster.nocheatplus.utilities.collision.AxisAlignedBBUtils;
import fr.neatmonster.nocheatplus.utilities.map.BlockCache;

/**
 * Fetches the block's collision VoxelShape and returns one or more bounding boxes
 * as a concatenated double[] containing {minX,minY,minZ,maxX,maxY,maxZ} for each
 * box. Coordinates are expressed relative to the block position.
 *
 * Difference from BukkitFetchableBound: this class may return multiple boxes
 * (preserving complex/multi-part collision geometry); BukkitFetchableBound
 * returns a single BoundingBox from Block#getBoundingBox().
 */
public class BukkitFetchableBounds implements BukkitShapeModel {

    @Override
    public double[] getShape(BlockCache blockCache, World world, int x, int y, int z) {
        final Block block = world.getBlockAt(x, y, z);
        final VoxelShape blockShape = block.getCollisionShape();
        double[] res = {};
        for (BoundingBox box : blockShape.getBoundingBoxes()) {
            res = add(res, AxisAlignedBBUtils.toArray(box));
        }
        if (res.length == 0) return null;
        return res;
    }
    
    /**
     * Concatenates two {@code double[]} arrays into a single array.
     * <p>
     * The contents of {@code array1} are placed first, followed by the contents
     * of {@code array2}. The resulting array has a length equal to the sum of
     * the lengths of the two input arrays.
     * </p>
     *
     * <pre>
     * Example:
     * array1 = {1.0, 2.0}
     * array2 = {3.0, 4.0}
     * result = {1.0, 2.0, 3.0, 4.0}
     * </pre>
     *
     * @param array1 the first array, may be empty but not {@code null}
     * @param array2 the second array, may be empty but not {@code null}
     * @return a new array containing all elements of {@code array1} followed by
     *         all elements of {@code array2}
     */
    private double[] add(final double[] array1, final double[] array2) {
        final double[] newArray = new double[array1.length + array2.length];
        System.arraycopy(array1, 0, newArray, 0, array1.length);
        System.arraycopy(array2, 0, newArray, array1.length, array2.length);
        return newArray;
    }

    @Override
    public int getFakeData(BlockCache blockCache, World world, int x, int y, int z) {
        return 0;
    }
}
