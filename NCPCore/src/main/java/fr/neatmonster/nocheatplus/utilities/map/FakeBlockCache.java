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
package fr.neatmonster.nocheatplus.utilities.map;

import java.util.Iterator;

import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.entity.Entity;

import fr.neatmonster.nocheatplus.logging.debug.DebugUtil;
import fr.neatmonster.nocheatplus.utilities.ds.map.CoordHashMap;
import fr.neatmonster.nocheatplus.utilities.ds.map.CoordMap;
import fr.neatmonster.nocheatplus.utilities.ds.map.CoordMap.Entry;

// TODO: Auto-generated Javadoc
/**
 * Stand-alone BlockCache for setting data by access methods, for testing purposes.
 * @author dev1mc
 *
 */
public class FakeBlockCache extends BlockCache {

    /** Cached type-ids. */
    private final CoordMap<Material> idMapStored = new CoordHashMap<Material>(23);

    /** Cached data values. */
    private final CoordMap<Integer> dataMapStored = new CoordHashMap<Integer>(23);

    /** Cached shape values. */
    private final CoordMap<double[]> boundsMapStored = new CoordHashMap<double[]>(23);

    /**
     * Convenience method to copy a cuboid region given by two endpoints without
     * any order specified.
     *
     * @param other
     *            the other
     * @param x0
     *            the x0
     * @param y0
     *            the y0
     * @param z0
     *            the z0
     * @param x1
     *            the x1
     * @param y1
     *            the y1
     * @param z1
     *            the z1
     * @param margin
     *            the margin
     */
    public void set(BlockCache other, double x0, double y0, double z0, double x1, double y1, double z1, double margin) {
        set(other, Location.locToBlock(Math.min(x0, x1) - margin), Location.locToBlock(Math.min(y0,  y1) - margin), Location.locToBlock(Math.min(z0,  z1) - margin),
                Location.locToBlock(Math.max(x0, x1) + margin), Location.locToBlock(Math.max(y0, y1) + margin), Location.locToBlock(Math.max(z0, z1) + margin));
    }

    /**
     * Copy a cuboid region from the other BlockCache instance.
     *
     * @param other
     *            the other
     * @param minX
     *            the min x
     * @param minY
     *            the min y
     * @param minZ
     *            the min z
     * @param maxX
     *            the max x
     * @param maxY
     *            the max y
     * @param maxZ
     *            the max z
     */
    public void set(BlockCache other, int minX, int minY, int minZ, int maxX, int maxY, int maxZ) {
        for (int x = minX; x <= maxX; x++) {
            for (int y = minY; y <= maxY; y ++) {
                for (int z = minZ; z <= maxZ; z ++) {
                    set(x, y, z, other.getType(x, y, z), other.getData(x, y, z), other.getBounds(x, y, z));
                }
            }
        }
    }

    /**
     * Set with data=0 and bounds=full.
     *
     * @param x
     *            the x
     * @param y
     *            the y
     * @param z
     *            the z
     * @param type
     *            the type
     */
    public void set(int x, int y, int z, Material type) {
        set(x, y, z, type, 0);
    }

    /**
     * Set with data=0-.
     *
     * @param x
     *            the x
     * @param y
     *            the y
     * @param z
     *            the z
     * @param type
     *            the type
     * @param bounds
     *            the bounds
     */
    public void set(int x, int y, int z, Material type, double[] bounds) {
        set(x, y, z, type, 0, bounds);
    }

    /**
     * Set with bounds=full.
     *
     * @param x
     *            the x
     * @param y
     *            the y
     * @param z
     *            the z
     * @param typeId
     *            the type id
     * @param data
     *            the data
     */
    public void set(int x, int y, int z, Material typeId, int data) {
        set(x, y, z, typeId, data, new double[]{0.0, 0.0, 0.0, 1.0, 1.0, 1.0});
    }

    /**
     * Set custom properties.
     *
     * @param x
     *            the x
     * @param y
     *            the y
     * @param z
     *            the z
     * @param typeId
     *            the type id
     * @param data
     *            the data
     * @param bounds
     *            Stores the given bounds directly.
     */
    public void set(int x, int y, int z, Material typeId, int data, double[] bounds) {
        idMapStored.put(x, y, z, typeId);
        dataMapStored.put(x, y, z, data);
        if (bounds == null) {
            // TODO: Might store full bounds.
            boundsMapStored.remove(x, y, z);
        } else {
            boundsMapStored.put(x, y, z, bounds);
        }
    }

    /**
     * Fill the entire cuboid.
     *
     * @param x1
     *            the x1
     * @param y1
     *            the y1
     * @param z1
     *            the z1
     * @param x2
     *            the x2
     * @param y2
     *            the y2
     * @param z2
     *            the z2
     * @param type
     *            the type
     */
    public void fill(int x1, int y1, int z1, int x2, int y2, int z2, Material type) {
        fill(x1, y1, z1, x2, y2, z2, type, 0, new double[]{0.0, 0.0, 0.0, 1.0, 1.0, 1.0});
    }

    /**
     * Fill the entire cuboid.
     *
     * @param x1
     *            the x1
     * @param y1
     *            the y1
     * @param z1
     *            the z1
     * @param x2
     *            the x2
     * @param y2
     *            the y2
     * @param z2
     *            the z2
     * @param typeId
     *            the type id
     * @param data
     *            the data
     * @param bounds
     *            the bounds
     */
    public void fill(int x1, int y1, int z1, int x2, int y2, int z2, Material typeId, int data, double[] bounds) {
        for (int x = x1; x <= x2; x++) {
            for (int y = y1; y <= y2; y ++) {
                for (int z = z1; z <= z2; z++) {
                    set(x, y, z, typeId, data, bounds);
                }
            }
        }
    }

    /**
     * Horizontal walls.
     *
     * @param x1
     *            the x1
     * @param y1
     *            the y1
     * @param z1
     *            the z1
     * @param x2
     *            the x2
     * @param y2
     *            the y2
     * @param z2
     *            the z2
     * @param type
     *            the type
     */
    public void walls(int x1, int y1, int z1, int x2, int y2, int z2, Material type) {
        walls(x1, y1, z1, x2, y2, z2, type, 0, new double[]{0.0, 0.0, 0.0, 1.0, 1.0, 1.0});
    }

    /**
     * Horizontal walls.
     *
     * @param x1
     *            the x1
     * @param y1
     *            the y1
     * @param z1
     *            the z1
     * @param x2
     *            the x2
     * @param y2
     *            the y2
     * @param z2
     *            the z2
     * @param typeId
     *            the type id
     * @param data
     *            the data
     * @param bounds
     *            the bounds
     */
    public void walls(int x1, int y1, int z1, int x2, int y2, int z2, Material typeId, int data, double[] bounds) {
        for (int x = x1; x <= x2; x++) {
            for (int y = y1; y <= y2; y ++) {
                for (int z = z1; z <= z2; z++) {
                    if (x == x1 || x == x2 || z == z1 || z == z2) {
                        set(x, y, z, typeId, data, bounds);
                    }
                }
            }
        }
    }

    /**
     * Walls, floor, ceiling.
     *
     * @param x1
     *            the x1
     * @param y1
     *            the y1
     * @param z1
     *            the z1
     * @param x2
     *            the x2
     * @param y2
     *            the y2
     * @param z2
     *            the z2
     * @param type
     *            the type
     */
    public void room(int x1, int y1, int z1, int x2, int y2, int z2, Material type) {
        room(x1, y1, z1, x2, y2, z2, type, 0, new double[]{0.0, 0.0, 0.0, 1.0, 1.0, 1.0});
    }

    /**
     * Walls, floor, ceiling.
     *
     * @param x1
     *            the x1
     * @param y1
     *            the y1
     * @param z1
     *            the z1
     * @param x2
     *            the x2
     * @param y2
     *            the y2
     * @param z2
     *            the z2
     * @param typeId
     *            the type id
     * @param data
     *            the data
     * @param bounds
     *            the bounds
     */
    public void room(int x1, int y1, int z1, int x2, int y2, int z2, Material typeId, int data, double[] bounds) {
        for (int x = x1; x <= x2; x++) {
            for (int y = y1; y <= y2; y ++) {
                for (int z = z1; z <= z2; z++) {
                    if (x == x1 || x == x2 || z == z1 || z == z2 || y == y1 || y == y2) {
                        set(x, y, z, typeId, data, bounds);
                    }
                }
            }
        }
    }

    /**
     * Test if any an id is set for this block position.
     *
     * @param x
     *            the x
     * @param y
     *            the y
     * @param z
     *            the z
     * @return true, if successful
     */
    public boolean hasIdEntry(int x, int y, int z) {
        return idMapStored.contains(x, y, z);
    }

    /**
     * Return a line of java code to construct a new FakeBlockCache with the
     * same content (no newlines).
     *
     * @param builder
     *            the builder
     * @param fbcName
     *            Variable name of the FakeBlockCache instance.
     * @param boundsPrefix
     *            A prefix for bounds variables for the case of repeated
     *            content. If set to null, no optimization will be performed.
     */
    public void toJava(final StringBuilder builder, final String fbcName, final String boundsPrefix) {
        builder.append("FakeBlockCache " + fbcName + " = new FakeBlockCache();");
        final String fullBounds;
        if (boundsPrefix != null) {
            fullBounds = boundsPrefix + "_fb";
            builder.append(" double[] " + fullBounds + " = new double[]{0.0, 0.0, 0.0, 1.0, 1.0, 1.0};" );
        } else {
            fullBounds = null;
        }
        // Assume id is always set.
        final Iterator<Entry<Material>> it = idMapStored.iterator();
        while (it.hasNext()) {
            Entry<Material> entry = it.next();
            final int x = entry.getX();
            final int y = entry.getY();
            final int z = entry.getZ();
            final Material id = entry.getValue();
            if (BlockProperties.isAir(id)) {
                builder.append(fbcName + ".set(" + x + ", " + y + ", " + z + ", " + id + ");");
            }
            else {
                final Integer data = dataMapStored.get(x, y, z);
                final double[] bounds = boundsMapStored.get(x, y, z);
                if (bounds == null) {
                    if (data == null) { // Consider 0 too.
                        builder.append(fbcName + ".set(" + x + ", " + y + ", " + z + ", " + id + ");");
                    }
                    else {
                        builder.append(fbcName + ".set(" + x + ", " + y + ", " + z + ", " + id + ", " + data + ");");
                    }
                }
                else if (boundsPrefix != null && MapUtil.isFullBounds(bounds)) {
                    builder.append(fbcName + ".set(" + x + ", " + y + ", " + z + ", " + id + ", " + data + ", " + fullBounds + ");");;
                }
                else {
                    builder.append(fbcName + ".set(" + x + ", " + y + ", " + z + ", " + id + ", " + data + ", ");
                    DebugUtil.toJava(bounds, builder);
                    builder.append(");");
                }
            }
        }
    }

    /* (non-Javadoc)
     * @see fr.neatmonster.nocheatplus.utilities.BlockCache#setAccess(org.bukkit.World)
     */
    @Override
    public BlockCache setAccess(World world) {
        // Ignore.
        return this;
    }

    /* (non-Javadoc)
     * @see fr.neatmonster.nocheatplus.utilities.BlockCache#fetchTypeId(int, int, int)
     */
    @Override
    public Material fetchTypeId(int x, int y, int z) {
        final Material id = idMapStored.get(x, y, z);
        if (id == null) {
            return Material.AIR;
        } else {
            return id;
        }
    }

    /* (non-Javadoc)
     * @see fr.neatmonster.nocheatplus.utilities.BlockCache#fetchData(int, int, int)
     */
    @Override
    public int fetchData(int x, int y, int z) {
        final Integer data = dataMapStored.get(x,  y,  z);
        if (data == null) {
            return 0;
        } else {
            return data;
        }
    }

    /* (non-Javadoc)
     * @see fr.neatmonster.nocheatplus.utilities.BlockCache#fetchBounds(int, int, int)
     */
    @Override
    public double[] fetchBounds(int x, int y, int z) {
        final double[] bounds = boundsMapStored.get(x, y, z);
        //return new double[]{0.0, 0.0, 0.0, 1.0, 1.0, 1.0};
        return bounds;
    }

    /* (non-Javadoc)
     * @see fr.neatmonster.nocheatplus.utilities.BlockCache#standsOnEntity(org.bukkit.entity.Entity, double, double, double, double, double, double)
     */
    @Override
    public boolean standsOnEntity(Entity entity, double minX, double minY,
            double minZ, double maxX, double maxY, double maxZ) {
        // TODO: Consider adding cuboids which mean "ground" if the foot location is inside.
        return false;
    }

    /* (non-Javadoc)
     * @see fr.neatmonster.nocheatplus.utilities.BlockCache#cleanup()
     */
    @Override
    public void cleanup() {
        super.cleanup();
        idMapStored.clear();
        dataMapStored.clear();
        boundsMapStored.clear();
    }

    @Override
    public double[] fetchVisualBounds(int x, int y, int z) {
        final double[] bounds = boundsMapStored.get(x, y, z);
        //return new double[]{0.0, 0.0, 0.0, 1.0, 1.0, 1.0};
        return bounds;
    }

    @Override
    public boolean isCollisionSameVisual(int x, int y, int z) {
        return true;
    }

}
