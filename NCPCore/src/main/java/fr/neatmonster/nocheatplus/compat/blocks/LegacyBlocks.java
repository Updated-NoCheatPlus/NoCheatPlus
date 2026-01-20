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
package fr.neatmonster.nocheatplus.compat.blocks;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.stream.IntStream;

import org.bukkit.Material;
import org.bukkit.block.BlockFace;

import fr.neatmonster.nocheatplus.compat.bukkit.BridgeMaterial;
import fr.neatmonster.nocheatplus.compat.versions.ClientVersion;
import fr.neatmonster.nocheatplus.players.IPlayerData;
import fr.neatmonster.nocheatplus.utilities.Validate;
import fr.neatmonster.nocheatplus.utilities.collision.ShapeUtils;
import fr.neatmonster.nocheatplus.utilities.map.BlockCache;
import fr.neatmonster.nocheatplus.utilities.map.BlockFlags;
import fr.neatmonster.nocheatplus.utilities.map.BlockProperties;
import fr.neatmonster.nocheatplus.utilities.map.MaterialUtil;

public class LegacyBlocks {
    private static final BlockStairs STAIRS = new BlockStairs();
    private static final BlockTrapDoor TRAPDOOR = new BlockTrapDoor();
    private static final Map<Material, Block> blocks = init();
    private static final Map<Material, TransformationBlock> adjustingBlocks = init2();

    private static Map<Material, TransformationBlock> init2() {
        Map<Material, TransformationBlock> blocks = new HashMap<>();
        for (final Material mat : MaterialUtil.addBlocks(
                MaterialUtil.GLASS_PANES, 
                BridgeMaterial.IRON_BARS)) {
            blocks.put(mat, new BlockFence(0.4375, 1.0, true));
        }
        for (Material mat : MaterialUtil.ALL_WALLS) {
            blocks.put(mat, new BlockWall(0.25, 1.5, 0.3125));
        }
        for (final Material mat : Material.values()) {
            final long flags = BlockFlags.getBlockFlags(mat);
            if (BlockFlags.hasAnyFlag(flags, BlockFlags.F_THICK_FENCE)) {
                if (BlockFlags.hasAnyFlag(flags, BlockFlags.F_PASSABLE_X4)) {
                    // Gates
                }
                // Fences
                else blocks.put(mat, new BlockFence(0.375, 1.5, false));
            }
        }
        return blocks;
    }
    private static Map<Material, Block> init() {
        Map<Material, Block> blocks = new HashMap<>();
        for (Material mat : MaterialUtil.ALL_STAIRS) {
            blocks.put(mat, STAIRS);
        }
        for (Material mat : MaterialUtil.ALL_TRAP_DOORS) {
            blocks.put(mat, TRAPDOOR);
        }
        blocks.put(BridgeMaterial.END_PORTAL_FRAME, new BlockEndPortalFrame());
        blocks.put(BridgeMaterial.PISTON_HEAD, new BlockPistonHead());
        blocks.put(Material.BREWING_STAND, new BlockStatic(
                // Bottom rod
                0.0, 0.0, 0.0, 1.0, 0.125, 1.0,
                // Rod
                0.4375, 0.125, 0.4375, 0.5625, 0.875, 0.5625
                )
            );
        blocks.put(Material.SOUL_SAND, new BlockStatic(0.0, 0.0, 0.0, 1.0, 0.875, 1.0));
        blocks.put(Material.CACTUS, new BlockStatic(0.0625, 0.0, 0.0625, 0.9375, 0.9375, 0.9375));
        blocks.put(Material.HOPPER, new BlockHopper());
        blocks.put(Material.CAULDRON, new BlockCauldron());
        blocks.put(Material.ANVIL, new BlockAnvil());
        blocks.put(BridgeMaterial.LILY_PAD, new BlockWaterLily());
        blocks.put(BridgeMaterial.FARMLAND, new BlockFarmLand());
        if (BridgeMaterial.GRASS_PATH != null) blocks.put(BridgeMaterial.GRASS_PATH, new BlockGrassPath());
        Material tmp = BridgeMaterial.getFirst("CHORUS_PLANT");
        if (tmp != null) blocks.put(tmp, new BlockChorusPlant());
        return blocks;
    }

    /**
     * Get block visual bounding boxes for legacy version
     * 
     * @param cache the BlockCache
     * @param mat Material of the block
     * @param x Block location
     * @param y Block location
     * @param z Block location
     * @param old if server is below 1.9
     * @return bounds, can be null if that block doesn't need.
     */
    public static double[] getVisualShape(BlockCache cache, Material mat, int x, int y, int z) {
        final Block blockshape = blocks.get(mat);
        if (blockshape != null) {
            return blockshape.getVisualShape(cache, mat, x, y, z);
        }
        return null;
    }

    /**
     * Get block collision bounding boxes for legacy version
     * 
     * @param cache the BlockCache
     * @param mat Material of the block
     * @param x Block location
     * @param y Block location
     * @param z Block location
     * @param old if server is below 1.9
     * @return bounds, can be null if that block doesn't need.
     */
    public static double[] getShape(BlockCache cache, Material mat, int x, int y, int z, boolean old) {
        final Block blockshape = blocks.get(mat);
        if (blockshape != null) {
            return blockshape.getShape(cache, mat, x, y, z, old);
        }
        return null;
    }

    /**
     * Get block bounding boxes for legacy version by guessing from given bound from NMS for performance wise, currently for walls and fences
     * 
     * @param cache the BlockCache
     * @param mat Material of the block
     * @param x Block location
     * @param y Block location
     * @param z Block location
     * @param visualBB given NMS bounding box
     * @return bounds
     */
    public static double[] adjustBounds(BlockCache cache, Material mat, int x, int y, int z, double[] visualBB) {
        final TransformationBlock blockshape = adjustingBlocks.get(mat);
        if (blockshape != null) {
            return blockshape.adjustToCollisionBox(cache, mat, x, y, z, visualBB);
        }
        return visualBB;
    }

    public static boolean isCollisionSameVisual(BlockCache cache, Material mat, int x, int y, int z) {
        final Block blockshape = blocks.get(mat);
        if (blockshape != null) {
            return blockshape.isCollisionSameVisual(cache, mat, x, y, z);
        }
        final TransformationBlock blockshape2 = adjustingBlocks.get(mat);
        if (blockshape2 != null) {
            return blockshape2.isCollisionSameVisual(cache, mat, x, y, z);
        }
        return true;
    }

    public static interface Block {
        public double[] getShape(BlockCache cache, Material mat, int x, int y, int z, boolean old);
        
        public double[] getVisualShape(BlockCache cache, Material mat, int x, int y, int z);
        
        public boolean isCollisionSameVisual(BlockCache cache, Material mat, int x, int y, int z);
    }
    
    public static interface TransformationBlock {
        public double[] adjustToCollisionBox(BlockCache cache, Material mat, int x, int y, int z, double[] visualBB);
        
        public boolean isCollisionSameVisual(BlockCache cache, Material mat, int x, int y, int z);
    }

    public static class BlockStatic implements Block{
        private final double[] bounds;

        public BlockStatic(double ...bounds) {
            if (bounds.length == 0) {
                this.bounds = null;
                return;
            }
            Validate.validateAABB(bounds);
            this.bounds = bounds;
        }

        @Override
        public double[] getShape(BlockCache cache, Material mat, int x, int y, int z, boolean old) {
            return bounds;
        }

        @Override
        public boolean isCollisionSameVisual(BlockCache cache, Material mat, int x, int y, int z) {
            return true;
        }

        @Override
        public double[] getVisualShape(BlockCache cache, Material mat, int x, int y, int z) {
            return getShape(cache, mat, x, y, z, false);
        }    
    }

    public static class BlockWaterLily implements Block {
        public BlockWaterLily() {}
        @Override
        public double[] getShape(BlockCache cache, Material mat, int x, int y, int z, boolean old) {
            final IPlayerData data = cache.getPlayerData();
            if (data != null && data.getClientVersion().isAtLeast(ClientVersion.V_1_9)) {
                return new double[] {0.0625, 0.0, 0.0625, 0.9375, 0.09375, 0.9375};
            }
            return new double[] {0.0625, 0.0, 0.0625, 0.9375, 0.125, 0.9375};
        }
        @Override
        public boolean isCollisionSameVisual(BlockCache cache, Material mat, int x, int y, int z) {
            return true;
        }
        @Override
        public double[] getVisualShape(BlockCache cache, Material mat, int x, int y, int z) {
            return getShape(cache, mat, x, y, z, false);
        } 
    }

    public static class BlockFarmLand implements Block {
        public BlockFarmLand() {}
        @Override
        public double[] getShape(BlockCache cache, Material mat, int x, int y, int z, boolean old) {
            final IPlayerData data = cache.getPlayerData();
            if (data != null && data.getClientVersion().isLowerThan(ClientVersion.V_1_10)) {
                return new double[] {0.0, 0.0, 0.0, 1.0, 1.0, 1.0};
            }
            return new double[] {0.0, 0.0, 0.0, 1.0, 0.9375, 1.0};
        }
        @Override
        public boolean isCollisionSameVisual(BlockCache cache, Material mat, int x, int y, int z) {
            return true;
        }
        @Override
        public double[] getVisualShape(BlockCache cache, Material mat, int x, int y, int z) {
            return getShape(cache, mat, x, y, z, false);
        } 
    }

    public static class BlockGrassPath implements Block {
        public BlockGrassPath() {}
        @Override
        public double[] getShape(BlockCache cache, Material mat, int x, int y, int z, boolean old) {
            final IPlayerData data = cache.getPlayerData();
            if (data != null && data.getClientVersion().isLowerThan(ClientVersion.V_1_9)) {
                return new double[] {0.0, 0.0, 0.0, 1.0, 1.0, 1.0};
            }
            return new double[] {0.0, 0.0, 0.0, 1.0, 0.9375, 1.0};
        }
        @Override
        public boolean isCollisionSameVisual(BlockCache cache, Material mat, int x, int y, int z) {
            return true;
        }
        @Override
        public double[] getVisualShape(BlockCache cache, Material mat, int x, int y, int z) {
            return getShape(cache, mat, x, y, z, false);
        } 
    }

    public static class BlockTrapDoor implements Block {

        private static final double closedHeight = 0.1875;
        private static final double openWidth = 0.1875;
        public BlockTrapDoor() {}

        @Override
        public double[] getShape(BlockCache cache, Material mat, int x, int y, int z, boolean old) {
            return getShapeLegacy(cache.getData(x, y, z));
        }

        public double[] getShapeLegacy(int data) {
            BlockFace face = dataToDirection(data);
            if (face == null) return null;
            return getShape(face, (data & 4) != 0, (data & 8) == 0);
        }

        public BlockFace dataToDirection(int data) {
            switch (data & 3) {
            case 0:
                return BlockFace.NORTH;
            case 1:
                return BlockFace.SOUTH;
            case 2:
                return BlockFace.WEST;
            case 3:
                return BlockFace.EAST;
            }
            return null;
        }

        private double[] getShape(BlockFace face, boolean open, boolean bottom) {
            if (open) {
                switch(face) {
                    case NORTH:
                        return new double[] {0.0, 0.0, 1.0 - openWidth, 1.0, 1.0, 1.0};
                    case SOUTH:
                        return new double[] {0.0, 0.0, 0.0, 1.0, 1.0, openWidth};
                    case EAST:
                        return new double[] {0.0, 0.0, 0.0, openWidth, 1.0, 1.0};
                    case WEST:
                        return new double[] {1.0 - openWidth, 0.0, 0.0, 1.0, 1.0, 1.0};
                    default:
                        break;
                    }
                }
                else {
                    return bottom
                            ? new double[] {0.0, 0.0, 0.0, 1.0, closedHeight, 1.0}
                    : new double[] {0.0, 1.0 - closedHeight, 0.0, 1.0, 1.0, 1.0};

                }
            return new double[] {0.0, 0.0, 0.0, 1.0, 1.0, 1.0};
        }

        @Override
        public boolean isCollisionSameVisual(BlockCache cache, Material mat, int x, int y, int z) {
            return true;
        }

        @Override
        public double[] getVisualShape(BlockCache cache, Material mat, int x, int y, int z) {
            return getShape(cache, mat, x, y, z, false);
        }
    }
    
    public static class BlockAnvil implements Block {
        @Override
        public double[] getShape(BlockCache cache, Material mat, int x, int y, int z, boolean old) {
            final IPlayerData data = cache.getPlayerData();
            final boolean legacy = data != null && data.getClientVersion().isLowerThan(ClientVersion.V_1_13);
            BlockFace face = dataToDirection(cache.getData(x, y, z));
            if (face == null) return null;
            switch (face) {
                case NORTH:
                case SOUTH:
                    if (legacy) return new double[] {0.125, 0.0, 0.0, 0.875, 1.0, 1.0};
                    return new double[] {
                            // Bottom
                            0.125, 0.0, 0.125, 0.875, 0.25, 0.875,
                            // Top
                            0.1875, 0.625, 0.0, 0.8125, 1.0, 1.0
                            // Body... maybe not need
                        };
                case WEST:
                case EAST:
                    if (legacy) return new double[] {0.0, 0.0, 0.125, 1.0, 1.0, 0.875};
                    return new double[] {
                            // Bottom
                            0.125, 0.0, 0.125, 0.875, 0.25, 0.875,
                            // Top
                            0.0, 0.625, 0.1875, 1.0, 1.0, 0.8125
                            // Body... maybe not need
                    };
                default:
                    break;
            }
            return new double[] {0.0, 0.0, 0.0, 1.0, 1.0, 1.0};
        }

        public BlockFace dataToDirection(int data) {
            switch (data & 3) {
            case 0:
                return BlockFace.NORTH;
            case 1:
                return BlockFace.SOUTH;
            case 2:
                return BlockFace.WEST;
            case 3:
                return BlockFace.EAST;
            }
            return null;
        }

        @Override
        public boolean isCollisionSameVisual(BlockCache cache, Material mat, int x, int y, int z) {
            return true;
        }

        @Override
        public double[] getVisualShape(BlockCache cache, Material mat, int x, int y, int z) {
            return getShape(cache, mat, x, y, z, false);
        }
    }

    public static class BlockEndPortalFrame implements Block {

        public BlockEndPortalFrame() {}

        public double[] getShapeLegacy(boolean hasEye) {
            return hasEye ? new double[] {0.0, 0.0, 0.0, 1.0, 0.8125, 1.0,
                                          0.3125, 0.8125, 0.3125, 0.6875, 1.0, 0.6875} 
                          : new double[] {0.0, 0.0, 0.0, 1.0, 0.8125, 1.0};
        }

        public double[] getShapeLegacy(int data) {
            return getShapeLegacy((data & 0x4) != 0);
        }

        @Override
        public double[] getShape(BlockCache cache, Material mat, int x, int y, int z, boolean old) {
            return getShapeLegacy(cache.getData(x, y, z));
        }

        @Override
        public boolean isCollisionSameVisual(BlockCache cache, Material mat, int x, int y, int z) {
            return true;
        }

        @Override
        public double[] getVisualShape(BlockCache cache, Material mat, int x, int y, int z) {
            return getShape(cache, mat, x, y, z, false);
        }
    }

    public static class BlockPistonHead implements Block {

        public BlockPistonHead() {}

        @Override
        public double[] getShape(BlockCache cache, Material mat, int x, int y, int z, boolean old) {
            return getShapeLegacy(cache.getData(x, y, z), old);
        }

        public double[] getShapeLegacy(int data, boolean bugged) {
            BlockFace face = dataToDirection(data);
            if (face == null) return null;
            return getShape(face, bugged);
        }

        private double[] getShape(BlockFace face, boolean bugged) {
            final double bug = bugged ? 0.125 : 0.0;
            switch (face) {
            case UP: return new double[] {
                    // Shaft
                    0.375, 0.0, 0.375, 0.625, 1.0, 0.625,
                    // Plank
                    0.0, 0.75, 0.0, 1.0, 1.0, 1.0
                    };
            case DOWN: return new double[] {
                    // Shaft
                    0.375, 0.0, 0.375, 0.625, 1.0, 0.625,
                    // Plank
                    0.0, 0.0, 0.0, 1.0, 0.25, 1.0
                    };
            case NORTH: return new double[] {
                    // Shaft
                    0.375 - bug, 0.375, 0.0, 0.625 + bug, 0.625, 1.0,
                    // Plank
                    0.0, 0.0, 0.0, 1.0, 1.0, 0.25
                    };
            case SOUTH: return new double[] {
                    // Shaft
                    0.375 - bug, 0.375, 0.0, 0.625 + bug, 0.625, 1.0,
                    // Plank
                    0.0, 0.0, 0.75, 1.0, 1.0, 1.0
                    };
            case WEST: 
                return bugged ? new double[] {
                       // Shaft ???
                       0.0, 0.375, 0.25, 0.625, 0.75, 1.0,
                       // Plank
                       0.0, 0.0, 0.0, 0.25, 1.0, 1.0
                       } : 
                    new double[] {
                    // Shaft
                    0.0, 0.375, 0.375, 1.0, 0.625, 0.625,
                    // Plank
                    0.0, 0.0, 0.0, 0.25, 1.0, 1.0
                    };
            case EAST: return new double[] {
                    // Shaft
                    0.0, 0.375, 0.375 - bug, 1.0, 0.625, 0.625 + bug,
                    // Plank
                    0.75, 0.0, 0.0, 1.0, 1.0, 1.0
                    };
            default:
                return new double[] {0.0, 0.0, 0.0, 1.0, 1.0, 1.0};
            }
        }

        private BlockFace dataToDirection(int data) {
            switch (data & 7) {
            case 0:
                return BlockFace.DOWN;
            case 1:
                return BlockFace.UP;
            case 2:
                return BlockFace.NORTH;
            case 3:
                return BlockFace.SOUTH;
            case 4:
                return BlockFace.WEST;
            case 5:
                return BlockFace.EAST;
            }
            return null;
        }

        @Override
        public boolean isCollisionSameVisual(BlockCache cache, Material mat, int x, int y, int z) {
            return false;
        }

        @Override
        public double[] getVisualShape(BlockCache cache, Material mat, int x, int y, int z) {
            return getShape(cache, mat, x, y, z, false);
        }
    }

    // Mostly taken from NMS - StairBlock.java
    public static class BlockStairs implements Block {
        private final double[] topslabs = new double[] {0.0, 0.5, 0.0, 1.0, 1.0, 1.0};
        private final double[] bottomslabs = new double[] {0.0, 0.0, 0.0, 1.0, 0.5, 1.0};

        private final double[] octet_nnn = new double[] {0.0, 0.0, 0.0, 0.5, 0.5, 0.5};
        private final double[] octet_nnp = new double[] {0.0, 0.0, 0.5, 0.5, 0.5, 1.0};
        private final double[] octet_pnn = new double[] {0.5, 0.0, 0.0, 1.0, 0.5, 0.5};
        private final double[] octet_pnp = new double[] {0.5, 0.0, 0.5, 1.0, 0.5, 1.0};

        private final double[] octet_npn = new double[] {0.0, 0.5, 0.0, 0.5, 1.0, 0.5};
        private final double[] octet_npp = new double[] {0.0, 0.5, 0.5, 0.5, 1.0, 1.0};
        private final double[] octet_ppn = new double[] {0.5, 0.5, 0.0, 1.0, 1.0, 0.5};
        private final double[] octet_ppp = new double[] {0.5, 0.5, 0.5, 1.0, 1.0, 1.0};

        private final double[][] top_stairs = makeshape(topslabs, octet_nnn, octet_pnn, octet_nnp, octet_pnp);
        private final double[][] bottom_stairs = makeshape(bottomslabs, octet_npn, octet_ppn, octet_npp, octet_ppp);
        private final int[] shape_by_state = new int[]{12, 5, 3, 10, 14, 13, 7, 11, 13, 7, 11, 14, 8, 4, 1, 2, 4, 1, 2, 8};

        public BlockStairs() {}

        @Override
        public double[] getShape(BlockCache cache, Material mat, int x, int y, int z, boolean old) {
            return getShapeLegacy(cache, cache.getData(x, y, z), x, y, z);
        }

        public double[] getShapeLegacy(BlockCache cache, int data, int x, int y, int z) {
            final boolean isTop = (data & 4) !=0;
            final BlockFace face = dataToDirection(data);
            if (face == null) return null;
            final int shapeindex = getStairShapeIndexLegacy(cache, face, isTop, data, x, y, z);
            if (isTop) return top_stairs[shape_by_state[getShapeStateIndex(shapeindex, face)]];
            return bottom_stairs[shape_by_state[getShapeStateIndex(shapeindex, face)]]; 
        }

        private int getStairShapeIndexLegacy(BlockCache cache, BlockFace face, boolean isTop, int data, int x, int y, int z) {
            final BlockFace oppositeface = face.getOppositeFace();

            final Material testType1 = cache.getType(x + face.getModX(), y, z + face.getModZ());
            final int testData1 = cache.getData(x + face.getModX(), y, z + face.getModZ());

            final Material testType2 = cache.getType(x + oppositeface.getModX(), y, z + oppositeface.getModZ());
            final int testData2 = cache.getData(x + oppositeface.getModX(), y, z + oppositeface.getModZ());

            if (BlockProperties.isStairs(testType1) && isTop == ((testData1 & 4) !=0)) {
                final BlockFace testFace = dataToDirection(testData1);
                if (testFace != null && hasDifferentAscAxis(face, testFace) && 
                    canTakeShape(cache, isTop, face, x + testFace.getOppositeFace().getModX(), y, z + testFace.getOppositeFace().getModZ())) {
                    if (testFace == getCounterClockWise(face)) {
                        return 3; // OUTER_LEFT
                    }
                    return 4; // OUTER_RIGHT
                }
            }

            if (BlockProperties.isStairs(testType2) && isTop == ((testData2 & 4) !=0)) {
                final BlockFace testFace = dataToDirection(testData2);
                if (testFace != null && hasDifferentAscAxis(face, testFace) && 
                        canTakeShape(cache, isTop, face, x + testFace.getModX(), y, z + testFace.getModZ())) {
                        if (testFace == getCounterClockWise(face)) {
                            return 1; // INNER_LEFT
                        }
                        return 2; // INNER_RIGHT
                    }
            }
            return 0; // STRAIGHT
        }

        private boolean hasDifferentAscAxis(BlockFace testFace1, BlockFace testFace2) {
            return testFace1.getOppositeFace() != testFace2;
        }

        private boolean canTakeShape(BlockCache cache, boolean orginStairTop, BlockFace orginStairFace, int x, int y, int z) {
            final Material testType = cache.getType(x, y, z);
            final int testData = cache.getData(x, y, z);
            final boolean testTop = (testData & 4) !=0;
            final BlockFace testFace = dataToDirection(testData);
            return !BlockProperties.isStairs(testType) || orginStairFace != testFace || orginStairTop != testTop;
        }

        private BlockFace getCounterClockWise(BlockFace face) {
            switch (face) {
                case NORTH:
                    return BlockFace.WEST;
                case EAST:
                    return BlockFace.NORTH;
                case SOUTH:
                    return BlockFace.EAST;
                case WEST:
                    return BlockFace.SOUTH;
                default: return null;
            }
        }

        private double[][] makeshape(double[] slab, 
                double[] octet_nn, double[] octet_pn, double[] octet_np, double[] octet_pp) {
            return IntStream
                    .range(0, 16)
                    .mapToObj((flags) -> makeStairShape(flags, slab, octet_nn, octet_pn, octet_np, octet_pp))
                    .toArray(double[][]::new);
        }

        private double[] makeStairShape(int flags, double[] slab,
                double[] octet_nn, double[] octet_pn, double[] octet_np, double[] octet_pp) {
            double[] res = slab;
            if ((flags & 1) != 0) {
                res = ShapeUtils.merge(res, octet_nn);
            }
            if ((flags & 2) != 0) {
                res = ShapeUtils.merge(res, octet_pn);
            }
            if ((flags & 4) != 0) {
                res = ShapeUtils.merge(res, octet_np);
            }
            if ((flags & 8) != 0) {
                res = ShapeUtils.merge(res, octet_pp);
            }
            return res;
        }

        private int getShapeStateIndex(int shapeIndex, BlockFace face) {
            return shapeIndex * 4 + directionToValue(face);
        }

        private int directionToValue(BlockFace face) {
            switch (face) {
                default:
                case UP:
                case DOWN:
                    return -1;
                case NORTH:
                    return 2;
                case SOUTH:
                    return 0;
                case WEST:
                    return 1;
                case EAST:
                    return 3;
            }
        }

        private BlockFace dataToDirection(int data) {
            switch (data & 3) {
            case 0:
                return BlockFace.EAST;
            case 1:
                return BlockFace.WEST;
            case 2:
                return BlockFace.SOUTH;
            case 3:
                return BlockFace.NORTH;
            }
            return null;
        }

        @Override
        public boolean isCollisionSameVisual(BlockCache cache, Material mat, int x, int y, int z) {
            return true;
        }

        @Override
        public double[] getVisualShape(BlockCache cache, Material mat, int x, int y, int z) {
            return getShape(cache, mat, x, y, z, false);
        }
    }
    
    public static class BlockHopper implements Block {
        @Override
        public double[] getShape(BlockCache cache, Material mat, int x, int y, int z, boolean old) {
            IPlayerData pData = cache.getPlayerData();
            if (pData != null && pData.getClientVersion().isHigherThan(ClientVersion.V_1_12_2)) {
                BlockFace face = dataToDirection(cache.getData(x, y, z));
                switch (face) {
                    case NORTH:
                        return new double[] {
                            // Standing inside
                            //0.0, 0.625, 0.0, 1.0, 0.6875, 1.0,
                            // Middle
                            0.25, 0.25, 0.25, 0.75, 0.625, 0.75,
                            // Bottom
                            0.375, 0.25, 0.0, 0.625, 0.5, 0.25,
                            // Top
                            0.0, 0.6875, 0.0, 1.0, 1.0, 1.0,
                            // 4 sides of hopper (top)
                            0.0, 0.6875, 0.0, 1.0, 1.0, 0.125,
                            0.0, 0.6875, 0.875, 1.0, 1.0, 1.0,
                            0.0, 0.6875, 0.0, 0.125, 1.0, 1.0,
                            0.875, 0.6875, 0.0, 1.0, 1.0, 1.0,
                            };
                    case SOUTH:
                        return new double[] {
                            // Standing inside
                            //0.0, 0.625, 0.0, 1.0, 0.6875, 1.0,
                            // Middle
                            0.25, 0.25, 0.25, 0.75, 0.625, 0.75,
                            // Bottom
                            0.375, 0.25, 0.75, 0.625, 0.5, 1.0,
                            // Top
                            0.0, 0.6875, 0.0, 1.0, 1.0, 1.0,
                            // 4 sides of hopper (top)
                            0.0, 0.6875, 0.0, 1.0, 1.0, 0.125,
                            0.0, 0.6875, 0.875, 1.0, 1.0, 1.0,
                            0.0, 0.6875, 0.0, 0.125, 1.0, 1.0,
                            0.875, 0.6875, 0.0, 1.0, 1.0, 1.0,
                            };
                    case WEST:
                        return new double[] {
                            // Standing inside
                            //0.0, 0.625, 0.0, 1.0, 0.6875, 1.0,
                            // Middle
                            0.25, 0.25, 0.25, 0.75, 0.625, 0.75,
                            // Bottom
                            0.0, 0.25, 0.375, 0.25, 0.5, 0.625,
                            // Top
                            0.0, 0.6875, 0.0, 1.0, 1.0, 1.0,
                            // 4 sides of hopper (top)
                            0.0, 0.6875, 0.0, 1.0, 1.0, 0.125,
                            0.0, 0.6875, 0.875, 1.0, 1.0, 1.0,
                            0.0, 0.6875, 0.0, 0.125, 1.0, 1.0,
                            0.875, 0.6875, 0.0, 1.0, 1.0, 1.0,
                            };
                    case EAST:
                        return new double[] {
                            // Standing inside
                            //0.0, 0.625, 0.0, 1.0, 0.6875, 1.0,
                            // Middle
                            0.25, 0.25, 0.25, 0.75, 0.625, 0.75,
                            // Bottom
                            0.75, 0.25, 0.375, 1.0, 0.5, 0.625,
                            // Top
                            0.0, 0.6875, 0.0, 1.0, 1.0, 1.0,
                            // 4 sides of hopper (top)
                            0.0, 0.6875, 0.0, 1.0, 1.0, 0.125,
                            0.0, 0.6875, 0.875, 1.0, 1.0, 1.0,
                            0.0, 0.6875, 0.0, 0.125, 1.0, 1.0,
                            0.875, 0.6875, 0.0, 1.0, 1.0, 1.0,
                            };
                    default:  // DOWN
                        return new double[] {
                            // Standing inside
                            //0.0, 0.625, 0.0, 1.0, 0.6875, 1.0,
                            // Middle
                            0.25, 0.25, 0.25, 0.75, 0.625, 0.75,
                            // Bottom
                            0.375, 0.0, 0.375, 0.625, 0.25, 0.625,
                            // Top
                            0.0, 0.6875, 0.0, 1.0, 1.0, 1.0,
                            // 4 sides of hopper (top)
                            0.0, 0.6875, 0.0, 1.0, 1.0, 0.125,
                            0.0, 0.6875, 0.875, 1.0, 1.0, 1.0,
                            0.0, 0.6875, 0.0, 0.125, 1.0, 1.0,
                            0.875, 0.6875, 0.0, 1.0, 1.0, 1.0,
                            };
                }
            }
            return new double[] {
                    0, 0, 0, 1, 0.625, 1,
                    0, 0.625, 0, 0.125, 1, 1,
                    0.875, 0.625, 0, 1, 1, 1,
                    0, 0.625, 0, 1, 1, 0.125,
                    0, 0.625, 0.875, 1, 1, 1
            };
        }
        private BlockFace dataToDirection(int data) {
            switch (data & 7) {
            case 0:
                return BlockFace.DOWN;
            case 1:
                return BlockFace.UP;
            case 2:
                return BlockFace.NORTH;
            case 3:
                return BlockFace.SOUTH;
            case 4:
                return BlockFace.WEST;
            case 5:
                return BlockFace.EAST;
            }
            return null;
        }
        @Override
        public boolean isCollisionSameVisual(BlockCache cache, Material mat, int x, int y, int z) {
            return false;
        }
        @Override
        public double[] getVisualShape(BlockCache cache, Material mat, int x, int y, int z) {
            IPlayerData pData = cache.getPlayerData();
            if (pData != null && pData.getClientVersion().isLowerThan(ClientVersion.V_1_13)) {
                return new double[] {0, 0, 0, 1, 1, 1};
            }
            return new double[] {0.0, 0.625, 0.0, 1.0, 1.0, 1.0, 0.25, 0.25, 0.25, 0.75, 0.625, 0.75}; 
        }
    }
    
    public static class BlockCauldron implements Block {
        private final static double[] new1_13_2Bounds = {
                0.0, 0.0, 0.0, 0.125, 1.0, 0.25, 
                0.0, 0.0, 0.75, 0.125, 1.0, 1.0, 
                0.125, 0.0, 0.0, 0.25, 1.0, 0.125, 
                0.125, 0.0, 0.875, 0.25, 1.0, 1.0, 
                0.75, 0.0, 0.0, 1.0, 1.0, 0.125, 
                0.75, 0.0, 0.875, 1.0, 1.0, 1.0, 
                0.875, 0.0, 0.125, 1.0, 1.0, 0.25, 
                0.875, 0.0, 0.75, 1.0, 1.0, 0.875, 
                0.0, 0.1875, 0.25, 1.0, 0.25, 0.75, 
                0.125, 0.1875, 0.125, 0.875, 0.25, 0.25, 
                0.125, 0.1875, 0.75, 0.875, 0.25, 0.875, 
                0.25, 0.1875, 0.0, 0.75, 1.0, 0.125, 
                0.25, 0.1875, 0.875, 0.75, 1.0, 1.0, 
                0.0, 0.25, 0.25, 0.125, 1.0, 0.75, 
                0.875, 0.25, 0.25, 1.0, 1.0, 0.75};
        private final static double[] new1_13Bounds = makeCauldron(0.1875, 0.125, 0.8125, 0.0625);
        private final static double[] legacyBounds = makeCauldron(0.0, 0.125, 1.0, 0.3125);

        private static double[] makeCauldron(double minY, double sideWidth, double sideHeight, double coreHeight) {
            return new double[] {
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
        public double[] getShape(BlockCache cache, Material mat, int x, int y, int z, boolean old) {
            IPlayerData pData = cache.getPlayerData();
            if (pData != null) {
                if (pData.getClientVersion().isLowerThan(ClientVersion.V_1_13)) {
                    return legacyBounds;
                } else if (pData.getClientVersion().isLowerThan(ClientVersion.V_1_13_2)) {
                    return new1_13Bounds;
                } else return new1_13_2Bounds;
            }
            return legacyBounds;
        }
        @Override
        public boolean isCollisionSameVisual(BlockCache cache, Material mat, int x, int y, int z) {
            return false;
        }
        @Override
        public double[] getVisualShape(BlockCache cache, Material mat, int x, int y, int z) {
            return new double[] {0.0, 0.1875, 0.0, 1.0, 1.0, 1.0};
        }
        
    }
    
    public static class BlockChorusPlant implements Block {
        private static final BlockFace[] directions = new BlockFace[]{BlockFace.NORTH, BlockFace.EAST, BlockFace.SOUTH, BlockFace.WEST, BlockFace.UP, BlockFace.DOWN};
        private static final double[][] modernShapes = makeShapes();
        private static double[][] makeShapes() {
            float min = 0.5F - (float) 0.3125;
            float max = 0.5F + (float) 0.3125;
            double[] base = {min, min, min, max, max, max};
            double[][] cs = new double[directions.length][];
            
            for (int i = 0; i < directions.length; i++) {
                BlockFace dir = directions[i];
                cs[i] = new double[] {0.5D + Math.min(-(float) 0.3125, (double) dir.getModX() * 0.5D), 0.5D + Math.min(-(float) 0.3125, (double) dir.getModY() * 0.5D), 0.5D + Math.min(-(float) 0.3125, (double) dir.getModZ() * 0.5D), 
                                      0.5D + Math.max((float) 0.3125, (double) dir.getModX() * 0.5D), 0.5D + Math.max((float) 0.3125, (double) dir.getModY() * 0.5D), 0.5D + Math.max((float) 0.3125, (double) dir.getModZ() * 0.5D)
                                      }; 
            }
            
            double[][] cs2 = new double[64][];
            for (int k = 0; k < 64; k++) {
                double[] tmp = base;
                
                for (int j = 0; j < directions.length; j++) {
                    if ((k & 1 << j) != 0) {
                        tmp = ShapeUtils.merge(tmp, cs[j]);
                    }
                }
                cs2[k] = tmp;
            }
            return cs2;
        }

        private Set<BlockFace> getLegacyFaces(BlockCache blockCache, int x, int y, int z) {
            Set<BlockFace> faces = new HashSet<>();
            Material upBlock = blockCache.getType(x, y+1, z);
            Material downBlock = blockCache.getType(x, y-1, z);
            Material northBlock = blockCache.getType(x, y, z-1);
            Material southBlock = blockCache.getType(x, y, z+1);
            Material westBlock = blockCache.getType(x-1, y, z);
            Material eastBlock = blockCache.getType(x+1, y, z);
            if (downBlock == Material.CHORUS_PLANT || downBlock == BridgeMaterial.END_STONE) {
                faces.add(BlockFace.DOWN);
            }
            if (upBlock == Material.CHORUS_PLANT) {
                faces.add(BlockFace.UP);
            }
            if (northBlock == Material.CHORUS_PLANT) {
                faces.add(BlockFace.NORTH);
            }
            if (southBlock == Material.CHORUS_PLANT) {
                faces.add(BlockFace.SOUTH);
            }
            if (westBlock == Material.CHORUS_PLANT) {
                faces.add(BlockFace.WEST);
            }
            if (eastBlock == Material.CHORUS_PLANT) {
                faces.add(BlockFace.EAST);
            }
            return faces;
        }
        
        private int getAABBIndex(Set<BlockFace> faces) {
            int i = 0;

            for (int j = 0; j < directions.length; ++j) {
                if (faces.contains(directions[j])) {
                    i |= 1 << j;
                }
            }
            return i;
        }

        @Override
        public double[] getShape(BlockCache cache, Material mat, int x, int y, int z, boolean old) {
            Set<BlockFace> directions = getLegacyFaces(cache, x, y, z);
            return modernShapes[getAABBIndex(directions)];
        }

        @Override
        public boolean isCollisionSameVisual(BlockCache cache, Material mat, int x, int y, int z) {
            return true;
        }

        @Override
        public double[] getVisualShape(BlockCache cache, Material mat, int x, int y, int z) {
            return getShape(cache, mat, x, y, z, false);
        }
    }
    public static class BlockFence implements TransformationBlock {
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

        public BlockFence(double inset, double height, boolean cutEdgeOnLowVersions) {
            this(inset, 1.0 - inset, height, cutEdgeOnLowVersions);
        }

        public BlockFence(double minXZ, double maxXZ, double height, boolean cutEdgeOnLowVersions) {
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
        public double[] adjustToCollisionBox(BlockCache cache, Material mat, int x, int y, int z, double[] visualBB) {
            boolean east = visualBB[3] == 1.0;
            boolean north = visualBB[2] == 0.0;
            boolean west = visualBB[0] == 0.0;
            boolean south = visualBB[5] == 1.0;
            if (east && north && west && south) {
                return fullplane;
            }
            final IPlayerData data = cache.getPlayerData();
            final boolean cutEdge = data != null && cutEdgeOnLowVersions && data.getClientVersion().isLowerThan(ClientVersion.V_1_9);
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
        
        private boolean isNextDir(boolean east, boolean north, boolean west, boolean south) {
            return (north || south) && (west || east);
        }

        @Override
        public boolean isCollisionSameVisual(BlockCache cache, Material mat, int x, int y, int z) {
            // Should be no different as current implementation doesn't do exact ray trace check
            return true;
        }
    }
    public static class BlockWall implements TransformationBlock {
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

        public BlockWall(double inset, double height) {
            this(inset, height, inset);
        }

        public BlockWall(double inset, double height, double sideInset) {
            this(inset, 1.0 - inset, height, sideInset, 1.0 - sideInset);
        }

        public BlockWall(double minXZ, double maxXZ, double height, double minSideXZ, double maxSideXZ) {
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
        public double[] adjustToCollisionBox(BlockCache cache, Material mat, int x, int y, int z, double[] visualBB) {
            boolean east = visualBB[3] == 1.0;
            boolean north = visualBB[2] == 0.0;
            boolean west = visualBB[0] == 0.0;
            boolean south = visualBB[5] == 1.0;
            boolean up = visualBB[0] == 0.25 || visualBB[2] == 0.25 || visualBB[3] == 0.75 || visualBB[5] == 0.75;
            double tmp[] = new double[0];
            final IPlayerData data = cache.getPlayerData();
            final boolean legacy = data != null && data.getClientVersion().isLowerThan(ClientVersion.V_1_13);
            if (legacy && isNextDir(east, north, west, south)) {
                return new double[] {visualBB[0], 0.0, visualBB[2], visualBB[3], 1.5, visualBB[5]};
            }
            
            //if (legacy && east && west && south && north) {
            //    return new double[] {0.0, 0.0, 0.0, 1.0, 1.5, 1.0};
            //}

            if (east && west) {
                //if (legacy) {
                //    if (south) {
                //        return new double[] {0.0, 0.0, 0.25, 1.0, 1.5, 1.0}; 
                //    }
                //    if (north) {
                //        return new double[] {0.0, 0.0, 0.0, 1.0, 1.5, 0.75}; 
                //    }
                //}
                tmp = eastwest;
                east = west = false;
            } else if (south && north) {
                //if (legacy) {
                //    if (west) {
                //        return new double[] {0.0, 0.0, 0.0, 0.75, 1.5, 1.0}; 
                //    }
                //    if (east) {
                //        return new double[] {0.25, 0.0, 0.0, 1.0, 1.5, 1.0}; 
                //    }
                //}
                tmp = southnorth;
                south = north = false;
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

        private boolean isNextDir(boolean east, boolean north, boolean west, boolean south) {
            return (north || south) && (west || east);
        }

        @Override
        public boolean isCollisionSameVisual(BlockCache cache, Material mat, int x, int y, int z) {
            // Should be no different as current implementation doesn't do exact ray trace check
            return true;
        }
    }
}