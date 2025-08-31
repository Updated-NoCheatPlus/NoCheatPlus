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

import fr.neatmonster.nocheatplus.utilities.map.BlockCache;

public class BukkitStatic implements BukkitShapeModel {

    private final double[] bounds;

    /**
     * Constructs a {@code BukkitStatic} block model with full width (no inset) and the specified height.
     * This is typically used for regular blocks that occupy the entire block space in the X and Z axes.
     *
     * @param height the height of the block, corresponding to the Y axis
     */
    public BukkitStatic(double height) {
        this(0.0, height);
    }

    /**
     * Constructs a {@code BukkitStatic} block model with a specified inset on the X and Z axes and the given height.
     * This is useful for blocks that do not occupy the full width and height of a block.
     *
     * @param xzInset the inset on the X and Z axes, reducing the block's width and height
     * @param height the height of the block, corresponding to the Y axis
     */
    public BukkitStatic(double xzInset, double height) {
        this(xzInset, 0.0, xzInset, 1.0 - xzInset, height, 1.0 - xzInset);
    }

    /**
     * Constructs a {@code BukkitStatic} block model using explicit bounding box values.
     * The {@code bounds} array must have a length that is a multiple of six, where each group of six values
     * represents the minimum and maximum coordinates for X, Y, and Z ({@code minX, minY, minZ, maxX, maxY, maxZ}).
     * This constructor allows for the definition of complex or custom-shaped blocks.
     *
     * @param bounds the bounding box values, in groups of six for each box: minX, minY, minZ, maxX, maxY, maxZ
     * @throws IllegalArgumentException if the length of {@code bounds} is not a multiple of six
     */
    public BukkitStatic(double ...bounds) {
        if (bounds.length % 6 != 0) {
            throw new IllegalArgumentException("The length must be a multiple of 6");
        }
        this.bounds = bounds;

    }

    @Override
    public double[] getShape(final BlockCache blockCache, final World world, final int x, final int y, final int z) {
        return bounds;
    }

    @Override
    public int getFakeData(final BlockCache blockCache, final World world, final int x, final int y, final int z) {
        return 0;
    }
}