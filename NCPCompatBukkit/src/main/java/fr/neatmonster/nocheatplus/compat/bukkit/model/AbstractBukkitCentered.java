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

import fr.neatmonster.nocheatplus.utilities.map.BlockCache;

/**
 * Somehow attached to a block face, centered cuboid.
 * 
 * @author asofold
 *
 */
public abstract class AbstractBukkitCentered implements BukkitShapeModel {

    private final double minDist;
    private final double maxDist;
    private final double length;
    protected final boolean invertFace;

    public AbstractBukkitCentered(double inset, double length, boolean invertFace) {
        // TODO: Might add a signature to specify minY and maxY (attach NWSE only).
        this.minDist = inset;
        this.maxDist = 1.0 - inset;
        this.length = length;
        this.invertFace = invertFace;
    }

    protected abstract BlockFace getFacing(BlockData blockData);

    @Override
    public double[] getShape(final BlockCache blockCache, final World world, final int x, final int y, final int z) {
        final Block block = world.getBlockAt(x, y, z);
        final BlockState state = block.getState();
        final BlockData blockData = state.getBlockData();
        final BlockFace facing = invertFace ? getFacing(blockData).getOppositeFace() : getFacing(blockData);
        // TODO: Evaluate if (some) faces need to be inverted.
        // End rod facing: the direction it points to.
        switch (facing) {
            case EAST:
                return new double[] {0.0, minDist, minDist, length, maxDist, maxDist};
            case WEST:
                return new double[] {1.0 - length, minDist, minDist, 1.0, maxDist, maxDist};
            case SOUTH:
                return new double[] {minDist, minDist, 0.0, maxDist, maxDist, length};
            case NORTH:
                return new double[] {minDist, minDist, 1.0 - length, maxDist, maxDist, 1.0};
            case DOWN:
                return new double[] {minDist, 1.0 - length, minDist, maxDist, 1.0, maxDist};
            case UP:
                return new double[] {minDist, 0.0, minDist, maxDist, length, maxDist};
            default:
                break;
        }
        return new double[] {0.0, 0.0, 0.0, 1.0, 1.0, 1.0};
    }

    @Override
    public int getFakeData(final BlockCache blockCache, final World world, final int x, final int y, final int z) {
        return 0;
    }
}