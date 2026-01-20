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
package fr.neatmonster.nocheatplus.compat.spigotcb1_11_R1;

import java.util.Iterator;
import java.util.List;

import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.craftbukkit.v1_11_R1.CraftWorld;
import org.bukkit.craftbukkit.v1_11_R1.entity.CraftEntity;
import org.bukkit.entity.Entity;

import fr.neatmonster.nocheatplus.compat.blocks.LegacyBlocks;
import fr.neatmonster.nocheatplus.utilities.map.BlockCache;
import net.minecraft.server.v1_11_R1.AxisAlignedBB;
import net.minecraft.server.v1_11_R1.BlockPosition;
import net.minecraft.server.v1_11_R1.EntityBoat;
import net.minecraft.server.v1_11_R1.EntityShulker;
import net.minecraft.server.v1_11_R1.EnumDirection;
import net.minecraft.server.v1_11_R1.IBlockAccess;
import net.minecraft.server.v1_11_R1.IBlockData;
import net.minecraft.server.v1_11_R1.TileEntity;

public class BlockCacheSpigotCB1_11_R1 extends BlockCache {

    protected net.minecraft.server.v1_11_R1.World world;

    protected World bukkitWorld;

    private final IBlockAccess iBlockAccess = new IBlockAccess() {

        @Override
        public int getBlockPower(BlockPosition pos, EnumDirection dir) {
            return world.getBlockPower(pos, dir);
        }

        @Override
        public TileEntity getTileEntity(BlockPosition pos) {
            return world.getTileEntity(pos);
        }

        @Override
        public IBlockData getType(BlockPosition pos) {
            // TODO: Can this be cached ?
            return world.getType(pos);
        }

        @Override
        public boolean isEmpty(BlockPosition pos) {
            // TODO: Can (and should) this be cached ?
            return world.isEmpty(pos);
        }

    };

    public BlockCacheSpigotCB1_11_R1(World world) {
        setAccess(world);
    }

    @Override
    public BlockCache setAccess(World world) {
        if (world != null) {
            this.maxBlockY = world.getMaxHeight() - 1;
            this.world = ((CraftWorld) world).getHandle();
            this.bukkitWorld = world;
        } else {
            this.world = null;
            this.bukkitWorld = null;
        }
        return this;
    }

    @Override
    public Material fetchTypeId(final int x, final int y, final int z) {
        return bukkitWorld.getBlockAt(x, y, z).getType();
    }

    @SuppressWarnings("deprecation")
    @Override
    public int fetchData(final int x, final int y, final int z) {
        return bukkitWorld.getBlockAt(x, y, z).getData();
    }

    @Override
    public double[] fetchBounds(final int x, final int y, final int z){
        final Material mat = getType(x, y, z);
        @SuppressWarnings("deprecation")
        final int id = mat.getId();
        final net.minecraft.server.v1_11_R1.Block block = net.minecraft.server.v1_11_R1.Block.getById(id);
        if (block == null) {
            // TODO: Convention for null blocks -> full ?
            return null;
        }
        final double[] shape = LegacyBlocks.getShape(this, mat, x, y, z, false);
        if (shape != null) return shape;
        final BlockPosition pos = new BlockPosition(x, y, z);
        // TODO: Deprecation warning below (reason / substitute?).
        @SuppressWarnings("deprecation")
        final AxisAlignedBB bb = block.b(iBlockAccess.getType(pos), iBlockAccess, pos);
        if (bb == null) {
            return new double[] {0.0, 0.0, 0.0, 1.0, 1.0, 1.0}; // Special case.
            //return null;
        }
        // minX, minY, minZ, maxX, maxY, maxZ
        return LegacyBlocks.adjustBounds(this, mat, x, y, z, new double[]{bb.a, bb.b, bb.c, bb.d, bb.e, bb.f});
    }

    @Override
    public boolean isCollisionSameVisual(int x, int y, int z) {
        final Material mat = getType(x, y, z);
        return LegacyBlocks.isCollisionSameVisual(this, mat, x, y, z);
    }

    @Override
    public double[] fetchVisualBounds(final int x, final int y, final int z){
        final Material mat = getType(x, y, z);
        @SuppressWarnings("deprecation")
        final int id = mat.getId();
        final net.minecraft.server.v1_11_R1.Block block = net.minecraft.server.v1_11_R1.Block.getById(id);
        if (block == null) {
            // TODO: Convention for null blocks -> full ?
            return null;
        }
        final double[] shape = LegacyBlocks.getVisualShape(this, mat, x, y, z);
        if (shape != null) return shape;
        final BlockPosition pos = new BlockPosition(x, y, z);
        // TODO: Deprecation warning below (reason / substitute?).
        @SuppressWarnings("deprecation")
        final AxisAlignedBB bb = block.b(iBlockAccess.getType(pos), iBlockAccess, pos);
        if (bb == null) {
            return new double[] {0.0, 0.0, 0.0, 1.0, 1.0, 1.0}; // Special case.
            //return null;
        }
        // minX, minY, minZ, maxX, maxY, maxZ
        return LegacyBlocks.adjustBounds(this, mat, x, y, z, new double[]{bb.a, bb.b, bb.c, bb.d, bb.e, bb.f});
    }

    @Override
    public boolean standsOnEntity(final Entity entity, final double minX, final double minY, final double minZ, final double maxX, final double maxY, final double maxZ){
        try{
            // TODO: Find some simplification!

            final net.minecraft.server.v1_11_R1.Entity mcEntity  = ((CraftEntity) entity).getHandle();

            final AxisAlignedBB box = new AxisAlignedBB(minX, minY, minZ, maxX, maxY, maxZ);
            @SuppressWarnings("rawtypes")
            final List list = world.getEntities(mcEntity, box);
            @SuppressWarnings("rawtypes")
            final Iterator iterator = list.iterator();
            while (iterator.hasNext()) {
                final net.minecraft.server.v1_11_R1.Entity other = (net.minecraft.server.v1_11_R1.Entity) iterator.next();
                if (mcEntity == other || !(other instanceof EntityBoat) && !(other instanceof EntityShulker)) { // && !(other instanceof EntityMinecart)) continue;
                    continue;
                }
                if (minY >= other.locY && minY - other.locY <= 0.7){
                    return true;
                }
                // Still check this for some reason.
                final AxisAlignedBB otherBox = other.getBoundingBox();
                if (box.a > otherBox.d || box.d < otherBox.a || box.b > otherBox.e || box.e < otherBox.b || box.c > otherBox.f || box.f < otherBox.c) {
                    continue;
                }
                else {
                    return true;
                }
            }
        }
        catch (Throwable t){
            // Ignore exceptions (Context: DisguiseCraft).
        }
        return false;
    }

    /* (non-Javadoc)
     * @see fr.neatmonster.nocheatplus.utilities.BlockCache#cleanup()
     */
    @Override
    public void cleanup() {
        super.cleanup();
        world = null;
        bukkitWorld = null;
    }

}
