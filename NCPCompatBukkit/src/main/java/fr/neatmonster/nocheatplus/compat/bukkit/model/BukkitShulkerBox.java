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
import org.bukkit.block.ShulkerBox;
import org.bukkit.block.data.BlockData;
import org.bukkit.block.data.Directional;

import fr.neatmonster.nocheatplus.utilities.map.BlockCache;

public class BukkitShulkerBox implements BukkitShapeModel {

    double FULL_BLOCK[] = {0.0, 0.0, 0.0, 1.0, 1.0, 1.0};
    
    @Override
    public double[] getShape(final BlockCache blockCache, final World world, final int x, final int y, final int z) {
        
        final Block block = world.getBlockAt(x, y, z);
        final BlockState state = block.getState();
        
        // Base full block
        double minX = 0.0, minY = 0.0, minZ = 0.0;
        double maxX = 1.0, maxY = 1.0, maxZ = 1.0;
        
        if ((state instanceof ShulkerBox)) {
            final ShulkerBox shulker = (ShulkerBox) state;
            
            // Closed shulker. Return full block
            if (shulker.getInventory().getViewers().isEmpty()) {
                return FULL_BLOCK;
            }
            
            // Facing is stored in BlockData, not in ShulkerBox
            BlockFace face = BlockFace.UP;
            BlockData data = block.getBlockData();
            
            if (data instanceof Directional) {
                face = ((Directional) data).getFacing();
            }
            
            // Open shulker: extend 0.5 blocks in facing direction
            switch (face) {
                case UP:
                    maxY = 1.5;
                    break;
                case DOWN:
                    minY = -0.5;
                    break;
                case NORTH:
                    minZ = -0.5;
                    break;
                case SOUTH:
                    maxZ = 1.5;
                    break;
                case WEST:
                    minX = -0.5;
                    break;
                case EAST:
                    maxX = 1.5;
                    break;
                default:
                    // Should not happen, but just in case... Treat as UP
                    maxY = 1.5;
                    break;
            }
        
        }

        return new double[]{minX, minY, minZ, maxX, maxY, maxZ};
    }
    
    @Override
    public double[] getVisualShape(BlockCache blockCache, World world, int x, int y, int z) {
        return getShape(blockCache, world, x, y, z);
    }

    @Override
    public int getFakeData(final BlockCache blockCache, final World world, final int x, final int y, final int z) {
        return 0;
    }
    
    @Override
    public boolean isCollisionSameVisual(BlockCache blockCache, World world, int x, int y, int z) {
        return true;
    }
}