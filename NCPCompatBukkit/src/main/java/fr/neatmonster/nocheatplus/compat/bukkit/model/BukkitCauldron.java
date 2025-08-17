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

public class BukkitCauldron implements BukkitShapeModel {
    private final double[] bounds;

    public BukkitCauldron(double minY, double sideWidth, double sideHeight, double coreHeight) {
        bounds = new double[] {
                // Core
                sideWidth, minY, sideWidth, 1 - sideWidth, minY + coreHeight, 1 - sideWidth,
                // 4 side
                0.0, minY, 0.0, 1.0, minY + sideHeight, sideWidth,
                0.0, minY, 1.0 - sideWidth, 1.0, minY + sideHeight, 1.0,
                0.0, minY, 0.0, sideWidth, minY + sideHeight, 1.0,
                1.0 - sideWidth, minY, 0.0, 1.0, minY + sideHeight, 1.0
        };
    }

    @Override
    public double[] getShape(BlockCache blockCache, World world, int x, int y, int z) {
        return bounds;
    }

    @Override
    public int getFakeData(BlockCache blockCache, World world, int x, int y, int z) {
        return 0;
    }
}