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
     * Initialize with the given height and with full xz-bounds.
     * 
     * @param height
     */
    public BukkitStatic(double height) {
        this(0.0, height);
    }

    /**
     * Initialize with the given height and xz-inset.
     * 
     * @param xzInset
     * @param height
     */
    public BukkitStatic(double xzInset, double height) {
        this(xzInset, 0.0, xzInset, 1.0 - xzInset, height, 1.0 - xzInset);
    }

    /**
     * Initialize with the given bounds 
     * 
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