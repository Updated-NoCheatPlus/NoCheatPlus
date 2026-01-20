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
import org.bukkit.util.BoundingBox;

import fr.neatmonster.nocheatplus.compat.Bridge1_13;
import fr.neatmonster.nocheatplus.compat.versions.ClientVersion;
import fr.neatmonster.nocheatplus.players.IPlayerData;
import fr.neatmonster.nocheatplus.utilities.collision.AxisAlignedBBUtils;
import fr.neatmonster.nocheatplus.utilities.collision.ShapeUtils;
import fr.neatmonster.nocheatplus.utilities.map.BlockCache;

public class BukkitFence implements BukkitShapeModel {
    private final double[] northplane;
    private final double[] southplane;
    private final double[] westplane;
    private final double[] eastplane;
    private final double[] northplaneB;
    private final double[] southplaneB;
    private final double[] westplaneB;
    private final double[] eastplaneB;
    private final double[] fullplane;
    private final double[] eastwest;
    private final double[] southnorth;
    private final double[] baseState;
    private final boolean cutEdgeOnLowVersions;

    public BukkitFence(double inset, double height, boolean cutEdgeOnLowVersions) {
        this(inset, 1.0 - inset, height, cutEdgeOnLowVersions);
    }

    public BukkitFence(double minXZ, double maxXZ, double height, boolean cutEdgeOnLowVersions) {
        this.cutEdgeOnLowVersions = cutEdgeOnLowVersions;
        this.northplane = new double[] {minXZ, 0.0, 0.0, maxXZ, height, maxXZ};
        this.southplane = new double[] {minXZ, 0.0, minXZ, maxXZ, height, 1.0};
        this.westplane = new double[] {0.0, 0.0, minXZ, maxXZ, height, maxXZ};
        this.eastplane = new double[] {minXZ, 0.0, minXZ, 1.0, height, maxXZ};
        this.northplaneB = new double[] {minXZ, 0.0, 0.0, maxXZ, height, maxXZ - 0.0625};
        this.southplaneB = new double[] {minXZ, 0.0, minXZ + 0.0625, maxXZ, height, 1.0};
        this.westplaneB = new double[] {0.0, 0.0, minXZ, maxXZ - 0.0625, height, maxXZ};
        this.eastplaneB = new double[] {minXZ + 0.0625, 0.0, minXZ, 1.0, height, maxXZ};
        this.eastwest = ShapeUtils.merge(westplane, eastplane);
        this.southnorth = ShapeUtils.merge(southplane, northplane);
        this.fullplane = ShapeUtils.add(eastwest, southnorth);
        this.baseState = new double[] {minXZ, 0.0, minXZ, maxXZ, height, maxXZ};
    }

    @Override
    public double[] getShape(final BlockCache blockCache, final World world, final int x, final int y, final int z) {
        final Block block = world.getBlockAt(x, y, z);
        final IPlayerData data = blockCache.getPlayerData();
        final boolean cutEdge = data != null && cutEdgeOnLowVersions && data.getClientVersion().isLowerThan(ClientVersion.V_1_9);
        // Server is 1.13.2 higher and player not on 1.8
        if (Bridge1_13.hasBuiltInRayTracing() && !cutEdge) {
            double[] res = {};
            for (BoundingBox box : block.getCollisionShape().getBoundingBoxes()) {
                res = ShapeUtils.add(res, AxisAlignedBBUtils.toArray(box));
            }
            if (res.length == 0) return null;
            return res;
        } else {
            boolean east = false;
            boolean north = false;
            boolean west = false;
            boolean south = false;
            final BlockState state = block.getState();
            final BlockData blockData = state.getBlockData();
            if (blockData instanceof MultipleFacing) {
                final MultipleFacing fence = (MultipleFacing) blockData;
                for (final BlockFace face : fence.getFaces()) {
                    switch (face) {
                        case EAST:
                            east = true;
                            break;
                        case NORTH:
                            north = true;
                            break;
                        case WEST:
                            west = true;
                            break;
                        case SOUTH:
                            south = true;
                            break;
                        default:
                            break;

                    }
                }
            }
            if (east && north && west && south) {
                return fullplane;
            }
            double tmp[] = new double[0];
            if (east && west) {
                tmp = eastwest;
                east = west = false;
            } else if (south && north) {
                tmp = southnorth;
                south = north = false;
            }
            if (south) {
                tmp = ShapeUtils.add(tmp, cutEdge && isNextDir(east, north, west, south) ? southplaneB : southplane);
            }
            if (north) {
                tmp = ShapeUtils.add(tmp, cutEdge && isNextDir(east, north, west, south) ? northplaneB : northplane);
            }
            if (east) {
                tmp = ShapeUtils.add(tmp, cutEdge && isNextDir(east, north, west, south) ? eastplaneB : eastplane);
            }
            if (west) {
                tmp = ShapeUtils.add(tmp, cutEdge && isNextDir(east, north, west, south) ? westplaneB : westplane);
            }
            if (tmp.length == 0) {
                tmp = ShapeUtils.add(tmp, cutEdge ? fullplane : baseState);
            }
            return tmp;
        }
    }
    
    private boolean isNextDir(boolean east, boolean north, boolean west, boolean south) {
        return (north || south) && (west || east);
    }

    @Override
    public double[] getVisualShape(BlockCache blockCache, World world, int x, int y, int z) {
        double a[] = getShape(blockCache, world, x, y, z);
        for (int i = 1; i < (int)a.length / 6; i++) a[i*6-2] = 1.0;
        return a;
    }

    @Override
    public int getFakeData(final BlockCache blockCache, final World world, final int x, final int y, final int z) {
        return 0;
    }

    @Override
    public boolean isCollisionSameVisual(BlockCache blockCache, World world, int x, int y, int z) {
        // TODO: Which one is better, return true and cap the height or return false and fetch twice?
        // NOTE: Should be no different as current implementation doesn't do exact ray trace check
        return true;
    }
}