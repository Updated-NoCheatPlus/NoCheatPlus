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
import org.bukkit.block.data.type.Snow;

import fr.neatmonster.nocheatplus.utilities.map.BlockCache;
import fr.neatmonster.nocheatplus.utilities.map.BlockCache.IBlockCacheNode;

public class BukkitSnow implements BukkitShapeModel {
    
    private static final int MAX_COLLISION_LAYER = 7;
    private static final double[][] SNOW_COLLISION_LAYERS = {
            {0.0, 0.0, 0.0, 1.0, 0.0, 1.0},
            {0.0, 0.0, 0.0, 1.0, 0.125, 1.0},
            {0.0, 0.0, 0.0, 1.0, 0.250, 1.0},
            {0.0, 0.0, 0.0, 1.0, 0.375, 1.0},
            {0.0, 0.0, 0.0, 1.0, 0.5, 1.0},
            {0.0, 0.0, 0.0, 1.0, 0.625, 1.0},
            {0.0, 0.0, 0.0, 1.0, 0.75, 1.0},
            {0.0, 0.0, 0.0, 1.0, 0.875, 1.0},
            {0.0, 0.0, 0.0, 1.0, 1.0, 1.0}
    };

    @Override
    public double[] getShape(BlockCache blockCache, World world, int x, int y, int z) {
        // Snow model: collision height is one layer lower than visual height; one-layer snow has no collision.
        return SNOW_COLLISION_LAYERS[getCollisionLayer(blockCache, world, x, y, z)];
    }

    @Override
    public double[] getVisualShape(BlockCache blockCache, World world, int x, int y, int z) {
        return getShape(blockCache, world, x, y, z);
    }

    @Override
    public boolean isCollisionSameVisual(BlockCache blockCache, World world, int x, int y, int z) {
        return true;
    }

    @Override
    public int getFakeData(BlockCache blockCache, World world, int x, int y, int z) {
        return getCollisionLayer(blockCache, world, x, y, z);
    }

    private int getCollisionLayer(final BlockCache blockCache, final World world, final int x, final int y, final int z) {
        if (world != null) {
            try {
                final Block block = world.getBlockAt(x, y, z);
                final BlockState state = block.getState();
                final BlockData blockData = state.getBlockData();
                if (blockData instanceof Snow) {
                    return clampCollisionLayer(((Snow) blockData).getLayers() - 1);
                }
            }
            catch (Throwable ignored) {
                // Folia/async fallback: use already cached legacy data if it exists, otherwise use no collision.
            }
        }
        if (blockCache != null) {
            final IBlockCacheNode node = blockCache.getBlockCacheNode(x, y, z);
            if (node != null && node.isDataFetched()) {
                return clampCollisionLayer(node.getData());
            }
        }
        return 0;
    }

    private int clampCollisionLayer(final int layer) {
        return Math.max(0, Math.min(MAX_COLLISION_LAYER, layer));
    }
}
