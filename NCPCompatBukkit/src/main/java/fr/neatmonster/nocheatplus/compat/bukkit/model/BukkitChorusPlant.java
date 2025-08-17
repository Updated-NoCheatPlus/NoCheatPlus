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
import org.bukkit.block.data.BlockData;
import org.bukkit.block.data.MultipleFacing;
import org.bukkit.util.BoundingBox;

import fr.neatmonster.nocheatplus.compat.Bridge1_13;
import fr.neatmonster.nocheatplus.utilities.map.BlockCache;

public class BukkitChorusPlant implements BukkitShapeModel {

    @Override
    public double[] getShape(final BlockCache blockCache, final World world, final int x, final int y, final int z) {
        final Block block = world.getBlockAt(x, y, z);
        if (Bridge1_13.hasBoundingBox()) {
            BoundingBox bd = block.getBoundingBox();
            return new double[] {bd.getMinX()-x, 0.1875, bd.getMinZ()-z, bd.getMaxX()-x, bd.getMaxY()-y, bd.getMaxZ()-z};
        }

        final BlockData blockData = block.getBlockData();
        double[] res = new double[] {0.187, 0.188, 0.187, 1.0 - 0.187, 0.8125, 1.0 - 0.187};
        if (blockData instanceof MultipleFacing) {
        	final MultipleFacing chorusplant = (MultipleFacing) blockData;
        	for (final BlockFace face : chorusplant.getFaces()) {
        		switch (face) {
        		    //case DOWN:
        		    //	res[1]=0.0;
        		    //	break;
        		    case UP:
        			    res[4]=1.0;
        			    break;
        		    case SOUTH:
        			    res[5]=1.0;
        			    break;
        		    case NORTH:
        			    res[2]=0.0;
        			    break;
        		    case WEST:
        			    res[0]=0.0;
        			    break;
        		    case EAST:
        			    res[3]=1.0;
        			    break;
				    default:
					    break;
        		}
        	}
        }
        return res;
    }

    @Override
    public int getFakeData(final BlockCache blockCache, final World world, final int x, final int y, final int z) {
        return 0;
    }
}