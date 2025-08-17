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
import org.bukkit.block.data.BlockData;
import org.bukkit.block.data.type.Lantern;

import fr.neatmonster.nocheatplus.utilities.map.BlockCache;

public class BukkitLantern implements BukkitShapeModel {

	private double xz = 0.3125;

    @Override
    public double[] getShape(final BlockCache blockCache, final World world, final int x, final int y, final int z) {

        final Block block = world.getBlockAt(x, y, z);
        final BlockData blockData = block.getBlockData();
        if (blockData instanceof Lantern) {
        	Lantern lantern = (Lantern) blockData;
        	if (lantern.isHanging()) 
                return new double[] {xz, 0.0625, xz, 1-xz, 0.5, 1-xz, 0.375, 0.5, 0.375, 0.625, 0.625, 0.625};
        }
        return new double[] {xz, 0.0, xz, 1.0-xz, 0.4375, 1.0-xz, 0.375, 0.4375, 0.375, 0.625, 0.5625, 0.625};
    }

    @Override
    public int getFakeData(final BlockCache blockCache, final World world, final int x, final int y, final int z) {
        return 0;
    }
}