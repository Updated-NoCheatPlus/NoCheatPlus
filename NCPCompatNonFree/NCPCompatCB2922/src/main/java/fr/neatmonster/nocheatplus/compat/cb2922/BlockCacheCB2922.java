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
package fr.neatmonster.nocheatplus.compat.cb2922;

import java.util.Iterator;
import java.util.List;

import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.craftbukkit.v1_7_R1.CraftWorld;
import org.bukkit.craftbukkit.v1_7_R1.entity.CraftEntity;
import org.bukkit.entity.Entity;

import fr.neatmonster.nocheatplus.compat.blocks.LegacyBlocks;
import fr.neatmonster.nocheatplus.utilities.map.BlockCache;
import net.minecraft.server.v1_7_R1.AxisAlignedBB;
import net.minecraft.server.v1_7_R1.Block;
import net.minecraft.server.v1_7_R1.EntityBoat;
import net.minecraft.server.v1_7_R1.IBlockAccess;
import net.minecraft.server.v1_7_R1.TileEntity;
import net.minecraft.server.v1_7_R1.Vec3DPool;

public class BlockCacheCB2922 extends BlockCache {

    /** Box for one time use, no nesting, no extra storing this(!). */
    protected static final AxisAlignedBB useBox = AxisAlignedBB.a(0, 0, 0, 0, 0, 0);

    protected net.minecraft.server.v1_7_R1.WorldServer world;

    protected World bukkitWorld;

    private final IBlockAccess iBlockAccess = new IBlockAccess() {

        @Override
        public int getData(int x, int y, int z) {
            return BlockCacheCB2922.this.getData(x, y, z);
        }

        @Override
        public TileEntity getTileEntity(final int x, final int y, final int z) {
            return world.getTileEntity(x, y, z);
        }

        @Override
        public int getBlockPower(final int arg0, final int arg1, final int arg2, final int arg3) {
            return world.getBlockPower(arg0, arg1, arg2, arg3);
        }

        @Override
        public Block getType(int x, int y, int z) {
            return world.getType(x, y, z);
        }

        @Override
        public Vec3DPool getVec3DPool() {
            return world.getVec3DPool();
        }

    };

    public BlockCacheCB2922(World world) {
        setAccess(world);
    }

    @Override
    public BlockCache setAccess(World world) {
        this.bukkitWorld = world;
        if (world != null) {
            this.maxBlockY = world.getMaxHeight() - 1;
            this.world = ((CraftWorld) world).getHandle();
        } else {
            this.world = null;
        }
        return this;
    }

    @Override
    public Material fetchTypeId(final int x, final int y, final int z) {
        return bukkitWorld.getBlockAt(x, y, z).getType();
    }

    @Override
    public int fetchData(final int x, final int y, final int z) {
        return world.getData(x, y, z);
    }

    @Override
    public double[] fetchBounds(final int x, final int y, final int z){
        // TODO: change api for this / use nodes (!)
        final Material mat = getType(x, y, z);
        @SuppressWarnings("deprecation")
        final int id = mat.getId();		
        final net.minecraft.server.v1_7_R1.Block block = net.minecraft.server.v1_7_R1.Block.e(id);
        if (block == null) return null;
        final double[] shape = LegacyBlocks.getShape(this, mat, x, y, z, true);
        if (shape != null) return shape;
        block.updateShape(iBlockAccess, x, y, z); // data from cache.

        // minX, minY, minZ, maxX, maxY, maxZ
        return LegacyBlocks.adjustBounds(this, mat, x, y, z, new double[]{block.x(), block.z(), block.B(), block.y(), block.A(), block.C()});
    }

    @Override
    public boolean isCollisionSameVisual(int x, int y, int z) {
        final Material mat = getType(x, y, z);
        return LegacyBlocks.isCollisionSameVisual(this, mat, x, y, z);
    }

    @Override
    public double[] fetchVisualBounds(final int x, final int y, final int z){
        // TODO: change api for this / use nodes (!)
        final Material mat = getType(x, y, z);
        @SuppressWarnings("deprecation")
        final int id = mat.getId();		
        final net.minecraft.server.v1_7_R1.Block block = net.minecraft.server.v1_7_R1.Block.e(id);
        if (block == null) return null;
        final double[] shape = LegacyBlocks.getVisualShape(this, mat, x, y, z);
        if (shape != null) return shape;
        block.updateShape(iBlockAccess, x, y, z); // data from cache.

        // minX, minY, minZ, maxX, maxY, maxZ
        return LegacyBlocks.adjustBounds(this, mat, x, y, z, new double[]{block.x(), block.z(), block.B(), block.y(), block.A(), block.C()});
    }

    @Override
    public boolean standsOnEntity(final Entity entity, final double minX, final double minY, final double minZ, final double maxX, final double maxY, final double maxZ){
        try{
            // TODO: Probably check other ids too before doing this ?

            final net.minecraft.server.v1_7_R1.Entity mcEntity  = ((CraftEntity) entity).getHandle();

            final AxisAlignedBB box = useBox.b(minX, minY, minZ, maxX, maxY, maxZ);
            @SuppressWarnings("rawtypes")
            final List list = world.getEntities(mcEntity, box);
            @SuppressWarnings("rawtypes")
            final Iterator iterator = list.iterator();
            while (iterator.hasNext()) {
                final net.minecraft.server.v1_7_R1.Entity other = (net.minecraft.server.v1_7_R1.Entity) iterator.next();
                if (!(other instanceof EntityBoat)){ // && !(other instanceof EntityMinecart)) continue;
                    continue;
                }
                if (minY >= other.locY && minY - other.locY <= 0.7){
                    return true;
                }
                // Still check this for some reason.
                final AxisAlignedBB otherBox = other.boundingBox;
                if (box.a > otherBox.d || box.d < otherBox.a || box.b > otherBox.e || box.e < otherBox.b || box.c > otherBox.f || box.f < otherBox.c) continue;
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
