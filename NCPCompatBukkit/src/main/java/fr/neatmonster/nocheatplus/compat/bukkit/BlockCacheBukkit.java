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
package fr.neatmonster.nocheatplus.compat.bukkit;

import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;

import fr.neatmonster.nocheatplus.compat.Bridge1_13;
import fr.neatmonster.nocheatplus.compat.SchedulerHelper;
import fr.neatmonster.nocheatplus.utilities.CheckUtils;
import fr.neatmonster.nocheatplus.utilities.map.BlockCache;
import fr.neatmonster.nocheatplus.utilities.map.BlockFlags;
import fr.neatmonster.nocheatplus.utilities.map.BlockProperties;
import fr.neatmonster.nocheatplus.utilities.map.MaterialUtil;

public class BlockCacheBukkit extends BlockCache {

    private static final long FOLIA_FALLBACK_LOG_INTERVAL_MS = 10000L;
    private static final int FOLIA_FALLBACK_CONSOLE_LOG_MIN_COUNT = 2;
    private static final AtomicInteger foliaFallbackCount = new AtomicInteger();
    private static final AtomicInteger foliaFallbackResolvedCount = new AtomicInteger();
    private static final AtomicLong nextFoliaFallbackLog = new AtomicLong();

    protected World world;

    /** Temporary use. Use LocUtil.clone before passing on. Call setWorld(null) after use. */
    protected final Location useLoc = new Location(null, 0, 0, 0);

    public BlockCacheBukkit(World world) {
        setAccess(world);
    }

    @Override
    public BlockCache setAccess(World world) {
        this.world = world;
        if (world != null) {
            this.maxBlockY = world.getMaxHeight() - 1;
            this.minBlockY = BlockProperties.getMinWorldY();
        }
        return this;
    }

    @Override
    public Material fetchTypeId(final int x, final int y, final int z) {
        // TODO: consider setting type id and data at once.
        if (world == null) {
            return Material.AIR;
        }
        if (SchedulerHelper.isOwnedByCurrentRegion(world, x, z)) {
            return world.getBlockAt(x, y, z).getType();
        }
        final Material fallbackType = fetchTypeIdWithLocationFallback(x, y, z);
        return fallbackType == null ? Material.AIR : fallbackType;
    }

    @SuppressWarnings("deprecation")
    @Override
    public int fetchData(final int x, final int y, final int z) {
        // TODO: consider setting type id and data at once.
        if (world == null) {
            return 0;
        }
        if (SchedulerHelper.isOwnedByCurrentRegion(world, x, z)) {
            return Bridge1_13.hasIsSwimming() ? 0 : world.getBlockAt(x, y, z).getData();
        }
        final Integer fallbackData = fetchDataWithLocationFallback(x, y, z);
        return fallbackData == null ? 0 : fallbackData.intValue();
    }

    private Material fetchTypeIdWithLocationFallback(final int x, final int y, final int z) {
        // Folia compatibility: if the chunk-level ownership check cannot prove safety, retry the exact location.
        // If that also fails, keep AIR as the safe final fallback instead of touching another region's block state.
        boolean resolved = false;
        try {
            if (SchedulerHelper.isOwnedByCurrentRegion(new Location(world, x, y, z))) {
                resolved = true;
                return world.getBlockAt(x, y, z).getType();
            }
        }
        catch (Throwable t) {
            // Keep the final AIR fallback for servers that throw while checking ownership or block state.
        }
        finally {
            logFoliaBlockFallback("type", resolved);
        }
        return null;
    }

    @SuppressWarnings("deprecation")
    private Integer fetchDataWithLocationFallback(final int x, final int y, final int z) {
        // Folia compatibility: retry exact-location ownership before returning legacy data 0 as the final fallback.
        boolean resolved = false;
        try {
            if (SchedulerHelper.isOwnedByCurrentRegion(new Location(world, x, y, z))) {
                resolved = true;
                return Integer.valueOf(Bridge1_13.hasIsSwimming() ? 0 : world.getBlockAt(x, y, z).getData());
            }
        }
        catch (Throwable t) {
            // Keep the final data 0 fallback for servers that throw while checking ownership or block state.
        }
        finally {
            logFoliaBlockFallback("data", resolved);
        }
        return null;
    }

    private static void logFoliaBlockFallback(final String kind, final boolean resolved) {
        if (!CheckUtils.shouldLogDebugToConsole()) {
            return;
        }
        final int count = foliaFallbackCount.incrementAndGet();
        if (resolved) {
            foliaFallbackResolvedCount.incrementAndGet();
        }
        final long now = System.currentTimeMillis();
        final long next = nextFoliaFallbackLog.get();
        if (now < next || !nextFoliaFallbackLog.compareAndSet(next, now + FOLIA_FALLBACK_LOG_INTERVAL_MS)) {
            return;
        }
        final int resolvedCount = foliaFallbackResolvedCount.getAndSet(0);
        final int periodCount = foliaFallbackCount.getAndSet(0);
        // Folia diagnostic: a single safe fallback during teleport/chunk handoff is expected and not useful console noise.
        if (periodCount < FOLIA_FALLBACK_CONSOLE_LOG_MIN_COUNT) {
            return;
        }
        Bukkit.getLogger().info("[NCP][Folia][BlockCache] safe " + kind + " block fallback used "
                                + periodCount + " times in the last 10s, resolved=" + resolvedCount
                                + ", finalSafeFallback=" + (periodCount - resolvedCount) + ".");
    }

    @Override
    public double[] fetchBounds(final int x, final int y, final int z){
        Material mat = getType(x, y, z);
        long flags = BlockFlags.getBlockFlags(mat);
        if (flags == BlockFlags.F_IGN_PASSABLE_CHECK) {
            return null;
        }
        // TODO: Want to maintain a list with manual entries or at least half / full blocks ?
        // Always return full bounds, needs extra adaption to BlockProperties (!).
        return new double[]{0D, 0D, 0D, 1D, 1D, 1D};
    }

    @Override
    public boolean standsOnEntity(final Entity entity, final double minX, final double minY, final double minZ, final double maxX, final double maxY, final double maxZ){
        if (!SchedulerHelper.isOwnedByCurrentRegion(entity)) {
            return false;
        }
        try{
            // TODO: Probably check other ids too before doing this ?
            for (final Entity other : entity.getNearbyEntities(2.0, 2.0, 2.0)){
                if (!SchedulerHelper.isOwnedByCurrentRegion(other)) {
                    continue;
                }
                final EntityType type = other.getType();
                if (!MaterialUtil.isBoat(type) && type != EntityType.SHULKER){ //  && !(other instanceof Minecart))
                    continue;
                }
                final double locY = entity.getLocation(useLoc).getY();
                useLoc.setWorld(null);
                // TODO: A "better" estimate is possible, though some more tolerance would be good. 
                return Math.abs(locY - minY) < 0.7;
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
    }

    @Override
    public double[] fetchVisualBounds(int x, int y, int z) {
        Material mat = getType(x, y, z);
        long flags = BlockFlags.getBlockFlags(mat);
        if (flags == BlockFlags.F_IGN_PASSABLE_CHECK) {
            return null;
        }
        // TODO: Want to maintain a list with manual entries or at least half / full blocks ?
        // Always return full bounds, needs extra adaption to BlockProperties (!).
        return new double[]{0D, 0D, 0D, 1D, 1D, 1D};
    }

    @Override
    public boolean isCollisionSameVisual(int x, int y, int z) {
        return true;
    }

    @Override
    public long fetchExtendedData(int x, int y, int z) {
        return 0;
    }
}
