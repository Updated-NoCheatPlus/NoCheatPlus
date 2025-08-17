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
import org.bukkit.block.BlockState;
import org.bukkit.block.data.BlockData;
import org.bukkit.block.data.type.SeaPickle;
import org.bukkit.util.BoundingBox;

import fr.neatmonster.nocheatplus.compat.Bridge1_13;
import fr.neatmonster.nocheatplus.utilities.map.BlockCache;

public class BukkitSeaPickle implements BukkitShapeModel {

    @Override
    public double[] getShape(final BlockCache blockCache, final World world, final int x, final int y, final int z) {
        final Block block = world.getBlockAt(x, y, z);
        if (Bridge1_13.hasBoundingBox()) {
            BoundingBox bd = block.getBoundingBox();
            return new double[] {bd.getMinX()-x, bd.getMinY()-y, bd.getMinZ()-z, bd.getMaxX()-x, bd.getMaxY()-y, bd.getMaxZ()-z};
        }
        final BlockState state = block.getState();
        final BlockData blockData = state.getBlockData();

        if (blockData instanceof SeaPickle) {
            final SeaPickle pick = (SeaPickle) blockData;
            switch (pick.getPickles()) {
                case 1: //  .375 , 0 , .375 max: .625 , 375 , .625
                    return new double[] {0.375, 0.0, 0.375, 0.625, 0.375, 0.625};
                case 2: // .1875 , 0 , .1875 max: .8125 , .375 , .8125
                    return new double[] {0.1875, 0.0, 0.1875, 0.8125, 0.375, 0.8125};
                case 3: // .125 0 .125 max: .875 .375 .875
                	return new double[] {0.125, 0.0, 0.125, 0.875, 0.375, 0.875};
                case 4: // .125 0 .125 max: .875 .4375 .875
                	return new double[] {0.125, 0.0, 0.125, 0.875, 0.4375, 0.875};
                default:
                    break;
            }
        }
        return new double[] {0.0, 0.0, 0.0, 1.0, 1.0, 1.0};
    }

    @Override
    public int getFakeData(final BlockCache blockCache, final World world, final int x, final int y, final int z) {
        return 0;
    }
}