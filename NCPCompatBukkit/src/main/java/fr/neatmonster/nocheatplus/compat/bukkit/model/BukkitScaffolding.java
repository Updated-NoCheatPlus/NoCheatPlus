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
import org.bukkit.block.data.BlockData;
import org.bukkit.block.data.type.Scaffolding;

import fr.neatmonster.nocheatplus.checks.moving.MovingData;
import fr.neatmonster.nocheatplus.players.IPlayerData;
import fr.neatmonster.nocheatplus.utilities.map.BlockCache;

/**
 * Scaffolding shape model.
 */
public class BukkitScaffolding implements BukkitShapeModel {

    // Stable visual shape (top slab + four posts)
    private static final double[] STABLE_SHAPE = new double[] {
        // top slab: 0,14,0 -> 16,16,16
        0.0, 0.875, 0.0, 1.0, 1.0, 1.0,
        // post (0..2 x, 0..2 z)
        0.0, 0.0, 0.0, 0.125, 1.0, 0.125,
        // post (14..16 x, 0..2 z)
        0.875, 0.0, 0.0, 1.0, 1.0, 0.125,
        // post (0..2 x, 14..16 z)
        0.0, 0.0, 0.875, 0.125, 1.0, 1.0,
        // post (14..16 x, 14..16 z)
        0.875, 0.0, 0.875, 1.0, 1.0, 1.0
    };

    // Unstable visual shape includes a thin bottom plate plus the stable parts
    private static final double[] UNSTABLE_SHAPE = new double[] {
        // bottom full thin plate: 0,0,0 -> 16,2,16
        0.0, 0.0, 0.0, 1.0, 0.125, 1.0,
        // include the stable shape boxes as well
        0.0, 0.875, 0.0, 1.0, 1.0, 1.0,
        0.0, 0.0, 0.0, 0.125, 1.0, 0.125,
        0.875, 0.0, 0.0, 1.0, 1.0, 0.125,
        0.0, 0.0, 0.875, 0.125, 1.0, 1.0,
        0.875, 0.0, 0.875, 1.0, 1.0, 1.0
    };
    // Just define ground plate, no need full boxes as Minecraft handle climbable if player within 0.3125 inset to the center, and was handled by NCP somewhere else 
    private static final double[] BOTTOM_PLATE = new double[] {0.0, 0.0, 0.0, 1.0, 0.125, 1.0};
    private static final double[] UPPER_PLATE = new double[] {0.0, 0.875, 0.0, 1.0, 1.0, 1.0};
    private static final double[] ALL_PLATE = new double[] {0.0, 0.875, 0.0, 1.0, 1.0, 1.0, 0.0, 0.0, 0.0, 1.0, 0.125, 1.0};
    // (empty box)
    private static final double[] NO_COLLISION = null;

    @Override
    public double[] getShape(BlockCache blockCache, World world, int x, int y, int z) {
        final Block block = world.getBlockAt(x, y, z);
        final BlockData data = block.getBlockData();
        // If the player is above the block and not sneaking, return the stable full shape.
        IPlayerData pData = blockCache.getPlayerData();
        if (data instanceof Scaffolding) {
            Scaffolding scaff = (Scaffolding) data;
            if (pData != null) {
                MovingData mData = pData.getGenericInstance(MovingData.class);
                boolean hasbottom = false;
                // If scaffolding has distance != 0 and is bottom, and player is just above or within tolerance return bottom plate collision
                if (scaff.getDistance() != 0 && scaff.isBottom()) {
                    hasbottom = true;
                }
                // If player was above the block and not sneaking -> stable collision 
                // Vanilla: return full stable collision only when the entity is above the block AND not descending.
                // 'isDescending' in vanilla is actually the entity's "isShiftKeyDown" (sneak) flag, not a vertical-velocity test.
                //System.out.println("scf 1 " + mData.lastY + ">" + (y + 1 - 1e-5) + " " + !pData.isShiftKeyPressed() + " " + y);
                if (mData.lastY > (y + 1 - 1e-5) // p_56071_.isAbove(Shapes.block(), p_56070_, true)
                    && !pData.isShiftKeyPressed()) {
                    return hasbottom ? ALL_PLATE : UPPER_PLATE;
                }
                if (hasbottom && mData.lastY > y + 0.125 - 1e-5) {
                    //System.out.println("scf 2");
                    return BOTTOM_PLATE;
                }
                //System.out.println("scf 3");
                return NO_COLLISION;
                // TODO: This should be the VISUAL shape (aka: hitbox), not the COLLISION shape.
                // We need to distinguish shapes from collision shapes in the API.
                //  return scaff.isBottom() ? UNSTABLE_SHAPE : STABLE_SHAPE;
            }
            // Shortcut Visible haven't set pData...
            return (scaff.getDistance() != 0 && scaff.isBottom()) ? UNSTABLE_SHAPE : STABLE_SHAPE;
        }
        // Fallback to a full block. Should never happen.
        return new double[] {0.0, 0.0, 0.0, 1.0, 1.0, 1.0};
    }

    @Override
    public int getFakeData(BlockCache blockCache, World world, int x, int y, int z) {
        return 0;
    }

    @Override
    public double[] getVisualShape(BlockCache blockCache, World world, int x, int y, int z) {
        final Block block = world.getBlockAt(x, y, z);
        final BlockData data = block.getBlockData();
        if (data instanceof Scaffolding) {
            Scaffolding scaff = (Scaffolding) data;
            return (scaff.getDistance() != 0 && scaff.isBottom()) ? UNSTABLE_SHAPE : STABLE_SHAPE;
        }
        return null;
    }
    
    @Override
    public boolean isCollisionSameVisual(BlockCache blockCache, World world, int x, int y, int z) {
        return false;
    }
}
