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

import java.util.Set;

import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.block.BlockState;
import org.bukkit.block.data.BlockData;
import org.bukkit.block.data.MultipleFacing;
import org.bukkit.block.data.type.Wall;
import org.bukkit.util.BoundingBox;

import fr.neatmonster.nocheatplus.compat.Bridge1_13;
import fr.neatmonster.nocheatplus.compat.versions.ClientVersion;
import fr.neatmonster.nocheatplus.players.IPlayerData;
import fr.neatmonster.nocheatplus.utilities.collision.AxisAlignedBBUtils;
import fr.neatmonster.nocheatplus.utilities.collision.ShapeUtils;
import fr.neatmonster.nocheatplus.utilities.map.BlockCache;

public class BukkitWall implements BukkitShapeModel {
    private final double[] east;
    private final double[] north;
    private final double[] west;
    private final double[] south;
    private final double[] eastB;
    private final double[] northB;
    private final double[] westB;
    private final double[] southB;
    private final double[] eastwest;
    private final double[] southnorth;
    private final double[] baseState;

    public BukkitWall(double inset, double height) {
        this(inset, height, inset);
    }

    public BukkitWall(double inset, double height, double sideInset) {
        this(inset, 1.0 - inset, height, sideInset, 1.0 - sideInset);
    }

    public BukkitWall(double minXZ, double maxXZ, double height, double minSideXZ, double maxSideXZ) {
        east = new double[] {maxXZ, 0.0, minSideXZ, 1.0, height, maxSideXZ};
        north = new double[] {minSideXZ, 0.0, 0.0, maxSideXZ, height, minXZ};
        west = new double[] {0.0, 0.0, minSideXZ, minXZ, height, maxSideXZ};
        south = new double[] {minSideXZ, 0.0, maxXZ, maxSideXZ, height, 1.0};
        eastB = new double[] {maxXZ, 0.0, minXZ, 1.0, height, maxXZ};
        northB = new double[] {minXZ, 0.0, 0.0, maxXZ, height, minXZ};
        westB = new double[] {0.0, 0.0, minXZ, minXZ, height, maxXZ};
        southB = new double[] {minXZ, 0.0, maxXZ, maxXZ, height, 1.0};
        eastwest = new double[] {0.0, 0.0, minSideXZ, 1.0, height, maxSideXZ};
        southnorth = new double[] {minSideXZ, 0.0, 0.0, maxSideXZ, height, 1.0};
        baseState = new double[] {minXZ, 0.0, minXZ, maxXZ, height, maxXZ};
    }

    @Override
    public double[] getShape(final BlockCache blockCache, final World world, final int x, final int y, final int z) {
        // Relevant: https://bugs.mojang.com/browse/MC-9565
        final Block block = world.getBlockAt(x, y, z);
        final IPlayerData data = blockCache.getPlayerData();
        final boolean legacy = data != null && data.getClientVersion().isLowerThan(ClientVersion.V_1_13);
        if (Bridge1_13.hasBuiltInRayTracing() && !legacy) {
            double[] res = {};
            for (BoundingBox box : block.getCollisionShape().getBoundingBoxes()) {
                res = ShapeUtils.add(res, AxisAlignedBBUtils.toArray(box));
            }
            if (res.length == 0) return null;
            return res;
        } else {
            final BlockState state = block.getState();
            final BlockData blockData = state.getBlockData();
            boolean east = false;
            boolean north = false;
            boolean west = false;
            boolean south = false;
            boolean up = false;

            if (blockData instanceof MultipleFacing) {
                MultipleFacing fence = (MultipleFacing) blockData;
                Set<BlockFace> faces = fence.getFaces();
                up = faces.contains(BlockFace.UP);
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
            else if (blockData instanceof Wall) {
                final Wall wall = (Wall) blockData;
                up = wall.isUp();
                if (!wall.getHeight(BlockFace.WEST).equals(Wall.Height.NONE)) {
                    west = true;
                }
                if (!wall.getHeight(BlockFace.EAST).equals(Wall.Height.NONE)) {
                    east = true;
                }
                if (!wall.getHeight(BlockFace.NORTH).equals(Wall.Height.NONE)) {
                    north = true;
                }
                if (!wall.getHeight(BlockFace.SOUTH).equals(Wall.Height.NONE)) {
                    south = true;
                }
            }

            if (legacy && east && west && south && north) {
                return new double[] {0.0, 0.0, 0.0, 1.0, 1.5, 1.0};
            }

            double tmp[] = new double[0];
            if (east && west) {
                if (legacy) {
                    if (south) {
                        return new double[] {0.0, 0.0, 0.25, 1.0, 1.5, 1.0}; 
                    }
                    if (north) {
                        return new double[] {0.0, 0.0, 0.0, 1.0, 1.5, 0.75}; 
                    }
                }
                tmp = eastwest;
                east = west = false;
            } else if (south && north) {
                if (legacy) {
                    if (west) {
                        return new double[] {0.0, 0.0, 0.0, 0.75, 1.5, 1.0}; 
                    }
                    if (east) {
                        return new double[] {0.25, 0.0, 0.0, 1.0, 1.5, 1.0}; 
                    }
                }
                tmp = southnorth;
                south = north = false;
            }

            if (legacy) {
                if (south && west) {return new double[] {0.0, 0.0, 0.25, 0.75, 1.5, 1.0};}
                if (south && east) {return new double[] {0.25, 0.0, 0.25, 1.0, 1.5, 1.0};}
                if (north && west) {return new double[] {0.0, 0.0, 0.0, 0.75, 1.5, 0.75};}
                if (north && east) {return new double[] {0.25, 0.0, 0.0, 1.0, 1.5, 0.75};}
            }

            if (south) {
                tmp = ShapeUtils.add(tmp, legacy ? this.southB : this.south);
            }
            if (north) {
                tmp = ShapeUtils.add(tmp, legacy ? this.northB : this.north);
            }
            if (east) {
                tmp = ShapeUtils.add(tmp, legacy ? this.eastB : this.east);
            }
            if (west) {
                tmp = ShapeUtils.add(tmp, legacy ? this.westB : this.west);
            }
            if (tmp.length == 0 || up) {
                tmp = ShapeUtils.add(tmp, this.baseState);
            }
            return tmp;
        }
    }

    @Override
    public double[] getVisualShape(BlockCache blockCache, World world, int x, int y, int z) {
        double a[] = getShape(blockCache, world, x, y, z);
        for (int i = 1; i < (int)a.length / 6; i++) a[i*6-2] = 1.0;
        return a;
    }
    
    @Override
    public boolean isCollisionSameVisual(BlockCache blockCache, World world, int x, int y, int z) {
        // TODO: Which one is better, return true and cap the height or return false and fetch twice?
        // NOTE: Should be no different as current implementation doesn't do exact ray trace check
        return true;
    }

    @Override
    public int getFakeData(final BlockCache blockCache, final World world, final int x, final int y, final int z) {
        return 0;
    }
}