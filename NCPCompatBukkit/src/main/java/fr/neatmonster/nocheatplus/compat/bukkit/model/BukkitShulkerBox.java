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
import org.bukkit.block.ShulkerBox;

import fr.neatmonster.nocheatplus.utilities.map.BlockCache;

public class BukkitShulkerBox implements BukkitShapeModel {

    @Override
    public double[] getShape(final BlockCache blockCache, final World world, final int x, final int y, final int z) {
        
        final Block block = world.getBlockAt(x, y, z);
        final BlockState state = block.getState();
        //final BlockData blockData = state.getBlockData();
        
        if (state instanceof ShulkerBox) {
            if (!((ShulkerBox) state).getInventory().getViewers().isEmpty()) {
                return new double[]{0.0, 0.0, 0.0, 1.0, 1.5, 1.0};
            }
        }
        return new double[] {0.0, 0.0, 0.0, 1.0, 1.0, 1.0};
//        if (!(state instanceof ShulkerBox)) {
//            return new double[]{0.0, 0.0, 0.0, 1.0, 1.0, 1.0};
//        }
//        
//        // Base collision box (same as vanilla)
//        double min = 1.0 / 16.0;
//        double max = 15.0 / 16.0;
//        double height = 14.0 / 16.0;
//        
//        double minX = min, minY = 0.0, minZ = min;
//        double maxX = max, maxY = height, maxZ = max;
//        
//        // Adjust for facing direction
//        if (state.getBlockData() instanceof Directional) {
//            switch (((ShulkerBox)state).getFacing()) {
//                case DOWN -> {
//                    minY = 1.0 - height;
//                    maxY = 1.0;
//                }
//                case NORTH -> {
//                    minZ = 1.0 - height;
//                    maxZ = 1.0;
//                }
//                case SOUTH -> {
//                    // default orientation: do nothing
//                }
//                case EAST -> {
//                    minX = 1.0 - height;
//                    maxX = 1.0;
//                }
//                case WEST -> {
//                    // mirror default: do nothing
//                }
//                default -> {
//                    // UP or unknown: do nothing
//                }
//            }
//        }
//        
//        // If open, extend the lid height
//        if (!shulker.getInventory().getViewers().isEmpty()) {
//            maxY = Math.min(1.5, maxY + 0.5);
//        }
//    }
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