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

import fr.neatmonster.nocheatplus.utilities.map.BlockCache;

public interface ShapeModel<W> {

    // TODO: Rather fill in all into node directly (data as well), avoid redundant casting etc.
    // TODO: Refine +- might have BukkitBlockCacheNode etc.
    
    /**
     * Get the <i>collision</i> shape of the block at the given position. <br>
     * <strong> Do note that not all blocks have a collision shape, like all plants/farmable blocks, which do have a visual shape that can be interacted with, but don't have a collision one;
     * other have a collision shape that exceeds their visual one, like fences, which have a height of 1.0 block visually, but 1.5 collision-wise and such. </strong> <br>
     * Most of the time, the collision shape will be the same as its visual shape, but not always. <br>
     * To check for any divergence, use {@link #isCollisionSameVisual(BlockCache, W, int, int, int)}. <br>
     * 
     * @param blockCache The block cache.
     * @param world The world.
     * @param x The x coordinate of the block.
     * @param y The y coordinate of the block.
     * @param z The z coordinate of the block.
     * @return An array of doubles representing the collision shape, in the format
     *         {minX, minY, minZ, maxX, maxY, maxZ, ...} for each box in the shape.
     *         Coordinates are in the range [0.0, 1.0].
     */
    public double[] getShape(BlockCache blockCache, W world, int x, int y, int z);
    
    /**
     * Tell if collision shape is the same as visual shape <br>
     * 
     * @param blockCache
     * @param world
     * @param x
     * @param y
     * @param z
     * @return if true
     */
    public boolean isCollisionSameVisual(BlockCache blockCache, W world, int x, int y, int z);
    
    /**
     * Get the visual shape of the block at the given position. <br>
     * Call this method if dealing with visual interactions, like ray tracing for line of sight, block targeting, etc. <br>
     * @See {@link #getShape(BlockCache, W, int, int, int)} for collision shape. <br>
     * 
     * @param blockCache
     * @param world
     * @param x
     * @param y
     * @param z
     * @return
     */
    public double[] getVisualShape(BlockCache blockCache, W world, int x, int y, int z);

    /**
     * Allow faking data.
     * 
     * @return Integer.MAX_VALUE, in case fake data is not supported, and the
     *         Bukkit method is used (as long as possible). 0 may be returned
     *         for performance.
     */
    public int getFakeData(BlockCache blockCache, W world, int x, int y, int z);

}
