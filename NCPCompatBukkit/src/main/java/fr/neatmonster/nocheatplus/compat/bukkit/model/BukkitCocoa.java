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
import org.bukkit.block.BlockState;
import org.bukkit.block.data.BlockData;
import org.bukkit.block.data.Directional;
import org.bukkit.block.data.type.Cocoa;
import org.bukkit.util.BoundingBox;

import fr.neatmonster.nocheatplus.compat.Bridge1_13;
import fr.neatmonster.nocheatplus.utilities.map.BlockCache;

public class BukkitCocoa implements BukkitShapeModel {

    @Override
    public double[] getShape(final BlockCache blockCache, final World world, final int x, final int y, final int z) {
        final Block block = world.getBlockAt(x, y, z);
        if (Bridge1_13.hasBoundingBox()) {
            BoundingBox bd = block.getBoundingBox();
            return new double[] {bd.getMinX()-x, bd.getMinY()-y, bd.getMinZ()-z, bd.getMaxX()-x, bd.getMaxY()-y, bd.getMaxZ()-z};
        }

        final BlockState state = block.getState();
        final BlockData blockData = state.getBlockData();
        if (blockData instanceof Cocoa && blockData instanceof Directional) {
        	BlockFace face = ((Directional) blockData).getFacing();
            final Cocoa cocoa = (Cocoa) blockData;
            switch (cocoa.getAge()) {
                case 0: // .625 .4375 .0625 max: .375 .75 .3125
                	switch (face) {
                	    case NORTH:
                		    return new double[] {0.375, 0.4375, 0.0, 0.625, 0.75, 0.315};
                	    case SOUTH:
                		    return new double[] {0.375, 0.4375, 0.685, 0.625, 0.75, 1.0};
                	    case WEST:                		
                		    return new double[] {0.0, 0.4375, 0.375, 0.315, 0.75, 0.625};
                	    case EAST:
                		    return new double[] {0.685, 0.4375, 0.375, 1.0, 0.75, 0.625};
					    default:
						    break;
                	}
                    break;
                case 1: // .6875 .3125 .0625 max: .3125 .75 .4375
                	switch (face) {
                	    case NORTH:
                		    return new double[] {0.3125, 0.3125, 0.0, 0.6875, 0.75, 0.4375};
                	    case SOUTH:
                		    return new double[] {0.3125, 0.3125, 0.5625, 0.6875, 0.75, 1.0};
                	    case WEST:                		
                		    return new double[] {0.0, 0.3125, 0.3125, 0.4375, 0.75, 0.6875};
                	    case EAST:
                		    return new double[] {0.5625, 0.3125, 0.3125, 1.0, 0.75, 0.6875};
					    default:
						    break;
                	}
                	break;
                case 2: // .75 .1875 .0625 max: .25 .75 .5625
                	switch (face) {
                	    case NORTH:
                		    return new double[] {0.25, 0.1875, 0.0, 0.75, 0.75, 0.5625};
                	    case SOUTH:
                		    return new double[] {0.25, 0.1875, 0.4375, 0.75, 0.75, 1.0};
                	    case WEST:                		
                		    return new double[] {0.0, 0.1875, 0.25, 0.5625, 0.75, 0.75};
                	    case EAST:
                		    return new double[] {0.4375, 0.1875, 0.25, 1.0, 0.75, 0.75};
					    default:
						    break;
                	}
                    //break; ?
            }
        }
        return new double[] {0.0, 0.0, 0.0, 1.0, 1.0, 1.0};
    }

    @Override
    public int getFakeData(final BlockCache blockCache, final World world, final int x, final int y, final int z) {
        return 0;
    }
}