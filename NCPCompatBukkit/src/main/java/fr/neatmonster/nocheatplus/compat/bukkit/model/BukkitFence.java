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
import org.bukkit.block.data.MultipleFacing;

import fr.neatmonster.nocheatplus.utilities.map.BlockCache;

public class BukkitFence implements BukkitShapeModel {

    private final double minXZ;
    private final double maxXZ;
    private final double height;

    public BukkitFence(double inset, double height) {
        this(inset, 1.0 - inset, height);
    }

    public BukkitFence(double minXZ, double maxXZ, double height) {
        this.minXZ = minXZ;
        this.maxXZ = maxXZ;
        this.height = height;
    }

    @Override
    public double[] getShape(final BlockCache blockCache, final World world, final int x, final int y, final int z) {
        // 13749998807909
        // 86250001192093
        // 0.1375, 0.8625
        final Block block = world.getBlockAt(x, y, z);
        final BlockState state = block.getState();
        final BlockData blockData = state.getBlockData();

        if (blockData instanceof MultipleFacing) {
            // Note isPassableWorkaround for these (no voxel shapes / multi cuboid yet).
            final MultipleFacing fence = (MultipleFacing) blockData;
            // TODO: If height > 1.0, check if it needs to be capped, provided relevant.
            double[] res = new double[] {minXZ, 0.0, minXZ, maxXZ, height, maxXZ};
            for (final BlockFace face : fence.getFaces()) {
                switch (face) {
                    case EAST:
                        res[3] = 1.0;
                        break;
                    case NORTH:
                        res[2] = 0.0;
                        break;
                    case WEST:
                        res[0] = 0.0;
                        break;
                    case SOUTH:
                        res[5] = 1.0;
                        break;
                    default:
                        break;

                }
            }
            return res;
        }
        return new double[] {0.0, 0.0, 0.0, 1.0, 1.0, 1.0};
    }

    @Override
    public int getFakeData(final BlockCache blockCache, final World world, final int x, final int y, final int z) {
        return 0;
    }
}