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
package fr.neatmonster.nocheatplus.utilities.collision.supportingblock;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.util.Vector;

import fr.neatmonster.nocheatplus.checks.moving.MovingData;
import fr.neatmonster.nocheatplus.checks.moving.model.PlayerMoveData;
import fr.neatmonster.nocheatplus.compat.bukkit.BridgeMaterial;
import fr.neatmonster.nocheatplus.players.DataManager;
import fr.neatmonster.nocheatplus.utilities.collision.AxisAlignedBBUtils;
import fr.neatmonster.nocheatplus.utilities.collision.CollisionUtil;
import fr.neatmonster.nocheatplus.utilities.ds.map.BlockCoord;
import fr.neatmonster.nocheatplus.utilities.map.BlockCache;
import fr.neatmonster.nocheatplus.utilities.map.BlockFlags;
import fr.neatmonster.nocheatplus.utilities.map.BlockProperties;
import fr.neatmonster.nocheatplus.utilities.map.MaterialUtil;
import fr.neatmonster.nocheatplus.utilities.math.MathUtil;
/**
 * Utility class for handling Mojang's logic introduced in Minecraft 1.20 to fix a <a href="https://bugs.mojang.com/browse/MC-262690">long-standing bug</a>.
 * <p>
 * Prior to Minecraft 1.19.4, block properties would only apply if the player stood at the very center of the block.
 * In Minecraft 1.20+, block properties are now determined based on the closest block to the player's position with a tie-break system
 */
public class SupportingBlockUtils {
    
    /**
     * From: {@code VoxelShapeSpliterator.java / BlockCollision.java}<br>
     * Mostly a copy of {@link CollisionUtil#getCollisionBoxes(BlockCache, Entity, double[], List, boolean)}.<br>
     * This one however checks for intersection rather than an actual collision.
     * Retrieves a list of block positions that intersect with the given entity playerAABB.
     *
     * @param blockCache The block cache used for retrieving block data.
     * @param eAABB The entity's bounding box represented as an array.
     * @return A list of {@link Vector} positions where collisions occur.
     */
    public static List<BlockCoord> getCollisionsLoc(BlockCache blockCache, double[] eAABB) {
        List<BlockCoord> collisionsLoc = new ArrayList<>();
        int minBlockX = (int) Math.floor(eAABB[0] - CollisionUtil.COLLISION_EPSILON) - 1;
        int maxBlockX = (int) Math.floor(eAABB[3] + CollisionUtil.COLLISION_EPSILON) + 1;
        int minBlockY = (int) Math.max(Math.floor(eAABB[1] - CollisionUtil.COLLISION_EPSILON) - 1, blockCache.getMinBlockY());
        int maxBlockY = (int) Math.min(Math.floor(eAABB[4] + CollisionUtil.COLLISION_EPSILON) + 1, blockCache.getMaxBlockY());
        int minBlockZ = (int) Math.floor(eAABB[2] - CollisionUtil.COLLISION_EPSILON) - 1;
        int maxBlockZ = (int) Math.floor(eAABB[5] + CollisionUtil.COLLISION_EPSILON) + 1;
        for (int y = minBlockY; y < maxBlockY; y++) {
            for (int x = minBlockX; x <= maxBlockX; x++) {
                for (int z = minBlockZ; z <= maxBlockZ; z++) {
                    Material mat = blockCache.getType(x, y, z);
                    // The piece of code below is found in BlockCollision.java -> computeNext() using MCB reborn tool.
                    if (BlockProperties.isAir(mat) || BlockProperties.isPassable(mat)) {
                        continue;
                    }
                    // how many of the current block’s coordinates (x, y, z) lie on the edges of the search region defined by the entity’s playerAABB
                    int edgeCount = ((x == minBlockX || x == maxBlockX) ? 1 : 0) + 
                                    ((y == minBlockY || y == maxBlockY) ? 1 : 0) +  
                                    ((z == minBlockZ || z == maxBlockZ) ? 1 : 0);
                    if (edgeCount != 3 && (edgeCount != 1 || (BlockFlags.getBlockFlags(mat) & BlockFlags.F_HEIGHT150) != 0) // isShapeExceedsCube...
                        && (edgeCount != 2 || mat == BridgeMaterial.MOVING_PISTON)) {
                        final double[] originAABB = blockCache.fetchBounds(x, y, z);
                        if (originAABB != null) {
                            if (AxisAlignedBBUtils.isIntersected(x, y, z, originAABB, eAABB)) {
                                collisionsLoc.add(new BlockCoord(x, y, z));
                            }
                        }
                    }
                }
            }
        }
        return collisionsLoc;
    }
    
    /**
     * From: {@code Entity.java} -> {@code getOnPos()}.<br>
     * Ask Mojang about the logic of this method.
     * 
     * @param access The block cache.
     * @param eLoc Location of the entity. This should already be corrected to account for split moves.
     * @param data Supporting block data
     * @param yBelow Y parameter for searching beneath the player.
     * @return A {@link Vector} containing the position of the block.
     */
    public static BlockCoord getOnPos(BlockCache access, Location eLoc, SupportingBlockData data, float yBelow) {
        BlockCoord blockPos = data == null ? null : data.getBlockPos();
        if (blockPos != null) {
            final BlockCache.IBlockCacheNode node = access.getOrCreateBlockCacheNode(blockPos.getX(), blockPos.getY(), blockPos.getZ(), false);
            final long flags = BlockFlags.getBlockFlags(node.getType());
            final Material mat = node.getType();
            boolean isSpecialCond = (!(yBelow <= 0.5D) || (flags & BlockFlags.F_HEIGHT150) == 0)
                                     && !MaterialUtil.ALL_WALLS.contains(mat)
                                     && !MaterialUtil.WOODEN_FENCE_GATES.contains(mat);
            if (isSpecialCond) {
                return new BlockCoord(blockPos.getX(), MathUtil.floor(eLoc.getY()-yBelow), blockPos.getZ()); // location of the block
            }
            return blockPos;
        }
        return new BlockCoord(MathUtil.floor(eLoc.getX()), MathUtil.floor(eLoc.getY()-yBelow), MathUtil.floor(eLoc.getZ())); // location of the entity
    }
    
    /**
     * From: {@code Entity.java} -> {@code checkSupportingBlock()}.<br>
     * Checks for the supporting block beneath a player, considering their movement.
     *
     * @param cache The block cache used for retrieving block data.
     * @param player The player whose supporting block is being determined.
     * @param lastSupportingBlock The last known supporting block.
     * @param movementVector The player's movement vector.
     * @param AABB The player's bounding box.
     * @param isOnGround Whether the player is currently on ground.
     * @return A {@link SupportingBlockData} object containing the supporting block data.
     */
    public static SupportingBlockData checkSupportingBlock(BlockCache cache, Player player, SupportingBlockData lastSupportingBlock, Vector movementVector, double[] AABB, boolean isOnGround) {
        // Match vanilla: when not on ground, clear supporting block and onGround flag
        if (!isOnGround) {
            return new SupportingBlockData(null, false);
        }

        // Lower the box by a very tiny margin and set the top to minY (vanilla creates a new AABB)
        double[] searchAABB = AxisAlignedBBUtils.move(AABB, 0.0, -1.0E-6D, 0.0);
        searchAABB[4] = searchAABB[1]; // set maxY = minY

        Optional<BlockCoord> optional = findSupportingBlock(cache, player, searchAABB);

        // If no block found and we were not previously onGround with no blocks, try using the movement vector
        boolean prevOnGroundNoBlocks = lastSupportingBlock != null && lastSupportingBlock.lastOnGroundAndNoBlock();
        if (!optional.isPresent() && !prevOnGroundNoBlocks) {
            if (movementVector != null) {
                double[] movedAABB = AxisAlignedBBUtils.move(searchAABB, -movementVector.getX(), 0.0, -movementVector.getZ());
                optional = findSupportingBlock(cache, player, movedAABB);
            }
        }

        // Vanilla sets its onGroundNoBlocks = optional.isEmpty();
        // We represent supporting block position as optional.orElse(null) and the onGround flag as true (since we entered the on-ground branch).
        return new SupportingBlockData(optional.orElse(null), true);
    }
    
    /**
     * Search for the block that's currently supporting the player. May be empty, if it can't be found. 
     * From: {@code ICollisionAccess.java} -> {@code findSupportingBlock()}.
     *
     * @param cache The block cache used for retrieving block data.
     * @param player The player whose supporting block is being searched.
     * @param AABB   The axis-aligned bounding box representing the entity's collision area used for the search.
     * @return An {@link Optional} containing the supporting block's position as a {@link Vector}, 
     *         or empty if no valid supporting block is found.   
     */
    private static Optional<BlockCoord> findSupportingBlock(BlockCache cache, Player player, double[] AABB) {
        // Compose the current Location of the player as a Vector by using our movement data (corrected by the split move mechanic)
        // Do not trust player#getLocation() as there will be mismatches with moving events, due to Bukkit sometimes skipping them.
        final PlayerMoveData thisMove = DataManager.getPlayerData(player).getGenericInstance(MovingData.class).playerMoves.getCurrentMove();
        final Vector correctedPlayerLoc = new Vector(thisMove.to.getX(), thisMove.to.getY(), thisMove.to.getZ());
        BlockCoord lastBlockLocation = null;
        double lastDistance = Double.MAX_VALUE;
        
        for (BlockCoord blockLocation : getCollisionsLoc(cache, AABB)) {
            // + 0.5 is to get the center of the block (distanceToCenterSq)
            Vector blockLocAsVector3d = new Vector(blockLocation.getX() + 0.5, blockLocation.getY() + 0.5, blockLocation.getZ() + 0.5);
            double currentDistance = correctedPlayerLoc.distanceSquared(blockLocAsVector3d);
            if (currentDistance < lastDistance 
                || currentDistance == lastDistance && (lastBlockLocation == null || winsTieBreakAgainst(blockLocation, lastBlockLocation))) {
                lastBlockLocation = blockLocation;
                lastDistance = currentDistance;
            }
        }
        return Optional.ofNullable(lastBlockLocation);
    }
    
    
    /**
     * Determines if the first location should win the tie-break against the second one.
     * Mirrors the tie-break behavior used when multiple blocks are at the same distance from a point in vanilla
     * From {@code Vector3i.java} -> {@code compareTo()} (semantics have been adapted to a boolean predicate; the NMS method would return an int)
     * 
     * @param first The first location to compare (candidate that may win).
     * @param second The second location to compare (current best).
     * @return {@code true} if the first location has priority (wins the tie-break) over the second, otherwise {@code false}.
     */
    private static boolean winsTieBreakAgainst(BlockCoord first, BlockCoord second) {
        if (first.getY() < second.getY()) {
            return true; // Lowest Y has priority
        }
        // If Y are equal or first Y is not lower, then compare Z, then X
        return first.getZ() == second.getZ() ? first.getX() < second.getX() : first.getZ() < second.getZ(); // Lowest Z then X have priority
    }
}