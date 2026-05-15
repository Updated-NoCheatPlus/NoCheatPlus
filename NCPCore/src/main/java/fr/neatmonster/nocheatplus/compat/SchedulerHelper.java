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
package fr.neatmonster.nocheatplus.compat;

import java.lang.reflect.Method;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executors;
import java.util.function.Consumer;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Server;
import org.bukkit.World;
import org.bukkit.entity.Entity;
import org.bukkit.event.player.PlayerTeleportEvent.TeleportCause;
import org.bukkit.plugin.Plugin;

import fr.neatmonster.nocheatplus.utilities.ReflectionUtil;

/**
 * Utility class to provide compatibility with Paper's regionized, multi-threaded server implementation (a.k.a.: Folia), using reflection.
 * If the server is not running Folia, use Bukkit's scheduler.
 * Keep Folia-specific calls here so the rest of NCP can stay source-compatible with regular Bukkit/Paper APIs.
 */
public class SchedulerHelper {

    private static final boolean RegionizedServer = ReflectionUtil.getClass("io.papermc.paper.threadedregions.RegionizedServer") != null;
    // private static final Class<?> AsyncScheduler = ReflectionUtil.getClass("io.papermc.paper.threadedregions.scheduler.AsyncScheduler");
    private static final Class<?> GlobalRegionScheduler = ReflectionUtil.getClass("io.papermc.paper.threadedregions.scheduler.GlobalRegionScheduler");
    private static final Class<?> EntityScheduler = ReflectionUtil.getClass("io.papermc.paper.threadedregions.scheduler.EntityScheduler");
    private static final boolean isFoliaServer = RegionizedServer && GlobalRegionScheduler != null && EntityScheduler != null; // && AsyncScheduler != null
    private static final Method Bukkit_isOwnedByCurrentRegionLocation = ReflectionUtil.getMethod(Bukkit.class, "isOwnedByCurrentRegion", Location.class);
    private static final Method Bukkit_isOwnedByCurrentRegionLocationRadius = ReflectionUtil.getMethod(Bukkit.class, "isOwnedByCurrentRegion", Location.class, int.class);
    private static final Method Bukkit_isOwnedByCurrentRegionWorldChunk = ReflectionUtil.getMethod(Bukkit.class, "isOwnedByCurrentRegion", World.class, int.class, int.class);
    private static final Method Bukkit_isOwnedByCurrentRegionWorldChunkRadius = ReflectionUtil.getMethod(Bukkit.class, "isOwnedByCurrentRegion", World.class, int.class, int.class, int.class);
    private static final Method Bukkit_isOwnedByCurrentRegionEntity = ReflectionUtil.getMethod(Bukkit.class, "isOwnedByCurrentRegion", Entity.class);
    
    /**
     * @return Whether the server is running Folia
     */
    public static boolean isFoliaServer() {
        return isFoliaServer;
    }

    /**
     * Check whether the current Folia region owns a Bukkit location before
     * touching world/block state. Non-Folia servers keep the old behavior.
     *
     * @param location The block/world location to check.
     * @return True if the location is safe to access on the current thread.
     */
    public static boolean isOwnedByCurrentRegion(final Location location) {
        return isOwnedByCurrentRegion(location, 0);
    }

    /**
     * Check whether the current Folia region owns a location and a square chunk
     * radius around it. Use this before code that scans neighboring blocks.
     *
     * @param location The block/world location to check.
     * @param squareRadiusChunks Radius in chunks, not squared distance.
     * @return True if the area is safe to access on the current thread.
     */
    public static boolean isOwnedByCurrentRegion(final Location location, final int squareRadiusChunks) {
        if (!isFoliaServer) {
            return true;
        }
        if (location == null || location.getWorld() == null) {
            return false;
        }
        if (squareRadiusChunks > 0 && Bukkit_isOwnedByCurrentRegionLocationRadius != null) {
            return invokeOwnershipCheck(Bukkit_isOwnedByCurrentRegionLocationRadius, location, squareRadiusChunks);
        }
        if (squareRadiusChunks == 0 && Bukkit_isOwnedByCurrentRegionLocation != null) {
            return invokeOwnershipCheck(Bukkit_isOwnedByCurrentRegionLocation, location);
        }
        return isOwnedByCurrentRegion(location.getWorld(), location.getBlockX(), location.getBlockZ(), squareRadiusChunks);
    }

    /**
     * Check whether the current Folia region owns the chunk containing a block.
     *
     * @param world The world to check.
     * @param blockX The block x coordinate.
     * @param blockZ The block z coordinate.
     * @return True if the block is safe to access on the current thread.
     */
    public static boolean isOwnedByCurrentRegion(final World world, final int blockX, final int blockZ) {
        return isOwnedByCurrentRegion(world, blockX, blockZ, 0);
    }

    /**
     * Check whether the current Folia region owns the chunk containing a block,
     * plus an optional square chunk radius.
     *
     * @param world The world to check.
     * @param blockX The block x coordinate.
     * @param blockZ The block z coordinate.
     * @param squareRadiusChunks Radius in chunks, not squared distance.
     * @return True if the block area is safe to access on the current thread.
     */
    public static boolean isOwnedByCurrentRegion(final World world, final int blockX, final int blockZ, final int squareRadiusChunks) {
        if (!isFoliaServer) {
            return true;
        }
        if (world == null) {
            return false;
        }
        final int chunkX = blockX >> 4;
        final int chunkZ = blockZ >> 4;
        if (squareRadiusChunks > 0 && Bukkit_isOwnedByCurrentRegionWorldChunkRadius != null) {
            return invokeOwnershipCheck(Bukkit_isOwnedByCurrentRegionWorldChunkRadius, world, chunkX, chunkZ, squareRadiusChunks);
        }
        if (Bukkit_isOwnedByCurrentRegionWorldChunk != null) {
            return invokeOwnershipCheck(Bukkit_isOwnedByCurrentRegionWorldChunk, world, chunkX, chunkZ);
        }
        return false;
    }

    /**
     * Check whether the current Folia region owns an entity before reading
     * entity state or nearby entities.
     *
     * @param entity The entity to check.
     * @return True if the entity is safe to access on the current thread.
     */
    public static boolean isOwnedByCurrentRegion(final Entity entity) {
        if (!isFoliaServer) {
            return true;
        }
        if (entity == null || Bukkit_isOwnedByCurrentRegionEntity == null) {
            return false;
        }
        return invokeOwnershipCheck(Bukkit_isOwnedByCurrentRegionEntity, entity);
    }

    private static boolean invokeOwnershipCheck(final Method method, final Object... arguments) {
        try {
            return Boolean.TRUE.equals(method.invoke(null, arguments));
        }
        catch (Throwable t) {
            return false;
        }
    }
    
    /**
     * Executes an asynchronous task, either using Bukkit's scheduler or Java's thread execution if the server is running Folia.
     *
     * @param plugin The plugin that owns the task.
     * @param run    The task to execute, represented as a consumer that accepts an object (or {@code null} for Paper/Spigot).
     * @return An integer task ID for Paper/Spigot, or a {@code Thread} object for Folia (returns {@code null} if scheduling fails).
     */
    public static Object runTaskAsync(Plugin plugin, Consumer<Object> run) {
        if (!isFoliaServer) {
            return Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> run.accept(null)).getTaskId();
        }
        // Folia async scheduler signatures have moved across builds; these callers already do async-safe work.
        //try {
        //    Method getSchedulerMethod = ReflectionUtil.getMethodNoArgs(Server.class, "getAsyncScheduler", AsyncScheduler);
        //    Object asyncScheduler = getSchedulerMethod.invoke(Bukkit.getServer());

        //    Class<?> schedulerClass = asyncScheduler.getClass();
        //    Method executeMethod = schedulerClass.getMethod("runNow", Plugin.class, Consumer.class);

        //    Object taskInfo = executeMethod.invoke(asyncScheduler, plugin, run);
        //    return taskInfo;
        //}
        //catch (Exception e) {
            // Second attempt, should be happening during onDisable calling from BukkitLogNodeDispatcher
            Thread thread = Executors.defaultThreadFactory().newThread(() -> run.accept(null));
            thread.start();
            return thread;
        //}
    }
    
    /**
     * Schedules a synchronous task to run as soon as possible, using either Bukkit's scheduler or Folia's global region scheduler.
     *
     * @param plugin The plugin that owns the task.
     * @param run    The task to execute, represented as a consumer that accepts an object (or {@code null} for Paper/Spigot).
     * @return An integer task ID for Paper/Spigot, or a {@code ScheduledTask} object for Folia (returns {@code null} if scheduling fails).
     */
    public static Object runSyncTask(Plugin plugin, Consumer<Object> run) {
        if (!isFoliaServer) {
            return Bukkit.getScheduler().scheduleSyncDelayedTask(plugin, () -> run.accept(null));
        }
        try {
            Method getSchedulerMethod = ReflectionUtil.getMethodNoArgs(Server.class, "getGlobalRegionScheduler", GlobalRegionScheduler);
            Object syncScheduler = getSchedulerMethod.invoke(Bukkit.getServer());

            Class<?> schedulerClass = syncScheduler.getClass();
            Method executeMethod = schedulerClass.getMethod("run", Plugin.class, Consumer.class);

            Object taskInfo = executeMethod.invoke(syncScheduler, plugin, run);
            return taskInfo;
        } catch (Exception e) {
            e.printStackTrace();
        }
        return null;
    }
    
    /**
     * Schedules a repeating task, either using Bukkit's scheduler or Folia's global region scheduler.
     *
     * @param plugin The plugin that owns the task.
     * @param run    The task to execute, represented as a consumer that accepts an object (or {@code null} for Paper/Spigot).
     * @param delay  The delay (in server ticks) before the first execution.
     * @param period The period (in server ticks) between consecutive executions.
     * @return An integer task ID for Paper/Spigot, or a {@code ScheduledTask} object for Folia (returns {@code null} if scheduling fails).
     */
    public static Object runSyncRepeatingTask(Plugin plugin, Consumer<Object> run, long delay, long period) {
        if (!isFoliaServer) {
            return Bukkit.getScheduler().scheduleSyncRepeatingTask(plugin, () -> run.accept(null), delay, period);
        }
        try {
            Method getSchedulerMethod = ReflectionUtil.getMethodNoArgs(Server.class, "getGlobalRegionScheduler", GlobalRegionScheduler);
            //ReflectionUtil.invokeMethod(getSchedulerMethod, Bukkit.getServer());
            Object syncScheduler = getSchedulerMethod.invoke(Bukkit.getServer());

            Class<?> schedulerClass = syncScheduler.getClass();
            //ReflectionUtil.getMethod(schedulerClass, "runAtFixedRate", Plugin.class, Consumer.class, long.class, long.class);
            Method executeMethod = schedulerClass.getMethod("runAtFixedRate", Plugin.class, Consumer.class, long.class, long.class);

            //ReflectionUtil.invokeMethod(executeMethod, syncScheduler, plugin, run, delay, period);
            Object taskInfo = executeMethod.invoke(syncScheduler, plugin, run, delay, period);
            return taskInfo;
        } catch (Exception e) {
            e.printStackTrace();
        }
        return null;
    }
    
    /**
     * Schedules a synchronous delayed task, either using Bukkit's scheduler or Folia's global region scheduler.
     *
     * @param plugin The plugin that owns the task.
     * @param run    The task to execute, represented as a consumer that accepts an object (or {@code null} for Paper/Spigot).
     * @param delay  The delay (in server ticks) before the task is executed.
     * @return An integer task ID for Paper/Spigot, or a {@code ScheduledTask} object for Folia (returns {@code null} if scheduling fails).
     */
    public static Object runSyncDelayedTask(Plugin plugin, Consumer<Object> run, long delay) {
        if (!isFoliaServer) {
            return Bukkit.getScheduler().scheduleSyncDelayedTask(plugin, () -> run.accept(null), delay);
        }
        try {
            Method getSchedulerMethod = ReflectionUtil.getMethodNoArgs(Server.class, "getGlobalRegionScheduler", GlobalRegionScheduler);
            Object syncScheduler = getSchedulerMethod.invoke(Bukkit.getServer());

            Class<?> schedulerClass = syncScheduler.getClass();
            Method executeMethod = schedulerClass.getMethod("runDelayed", Plugin.class, Consumer.class, long.class);

            Object taskInfo = executeMethod.invoke(syncScheduler, plugin, run, delay);
            return taskInfo;
        } catch (Exception e) {
            e.printStackTrace();
        }
        return null;
    }
    
    /**
     * Schedules a synchronous task for an entity to run on the next tick, either using Bukkit's scheduler or Folia's entity scheduler.
     *
     * @param entity  The entity associated with the task.
     * @param plugin  The plugin that owns the task.
     * @param run     The task to execute, represented as a consumer that accepts an object (or {@code null} for Paper/Spigot).
     * @param retired A fallback task to execute if the entity is retired before the scheduled task runs.
     * @return An integer task ID for Paper/Spigot, or a {@code ScheduledTask} object for Folia (returns {@code null} if scheduling fails).
     */
    public static Object runSyncTaskForEntity(Entity entity, Plugin plugin, Consumer<Object> run, Runnable retired) {
        return runSyncDelayedTaskForEntity(entity, plugin, run, retired, 1L);
    }
    
    /**
     * Schedules a synchronous delayed task for an entity, using either Bukkit's scheduler or Folia's entity scheduler.
     *
     * @param entity  The entity associated with the task.
     * @param plugin  The plugin that owns the task.
     * @param run     The task to execute, represented as a consumer that accepts an object (or {@code null} for Paper/Spigot).
     * @param retired A fallback task to execute if the entity is retired before the scheduled task runs.
     * @param delay   The delay (in server ticks) before the task is executed.
     * @return An integer task ID for Paper/Spigot, or a {@code ScheduledTask} object for Folia (returns {@code null} if scheduling fails).
     */
    public static Object runSyncDelayedTaskForEntity(Entity entity, Plugin plugin, Consumer<Object> run, Runnable retired, long delay) {
        if (!isFoliaServer) {
            return Bukkit.getScheduler().scheduleSyncDelayedTask(plugin, () -> run.accept(null), delay);
        }
        try {
            Method getSchedulerMethod = ReflectionUtil.getMethodNoArgs(Entity.class, "getScheduler", EntityScheduler);
            Object syncEntityScheduler = getSchedulerMethod.invoke(entity);

            Class<?> schedulerClass = syncEntityScheduler.getClass();
            Method executeMethod = schedulerClass.getMethod("runDelayed", Plugin.class, Consumer.class, Runnable.class, long.class);

            Object taskInfo = executeMethod.invoke(syncEntityScheduler, plugin, run, retired, delay);
            return taskInfo;
        } catch (Exception e) {
            e.printStackTrace();
        }
        return null;
    }
    
    /**
     * Cancels a scheduled task.
     *
     * @param task The task to cancel. This can be an integer task ID (for Paper/Spigot), a {@code ScheduledTask} object (for Folia), or a {@code Thread} object.
     *             Does nothing if the task is {@code null} or a {@code Thread}.
     */
    public static void cancelTask(Object task) {
        if (task == null) {
            return;
        }
        if (task instanceof Thread) {
            return; // task = null ?
        }
        if (task instanceof Integer) {
            int taskId = (int)task;
            Bukkit.getScheduler().cancelTask(taskId);
        } 
        else {
            Method cancelMethod = ReflectionUtil.getMethodNoArgs(task.getClass(), "cancel");
            ReflectionUtil.invokeMethodNoArgs(cancelMethod, task);
        }
    }
    
    /**
     * Cancels all scheduled tasks for the given plugin.
     *
     * @param plugin The plugin whose tasks should be canceled.
     */
    public static void cancelTasks(Plugin plugin) {
        if (!isFoliaServer) {
            Bukkit.getScheduler().cancelTasks(plugin);
        } 
        else {
            try {
                Method getGlobalRegionSchedulerMethod = ReflectionUtil.getMethodNoArgs(Server.class, "getGlobalRegionScheduler", GlobalRegionScheduler);
                //Method getAsyncSchedulerMethod = ReflectionUtil.getMethodNoArgs(Server.class, "getAsyncScheduler", AsyncScheduler);
                
                Object syncScheduler = getGlobalRegionSchedulerMethod.invoke(Bukkit.getServer());
                //Object asyncScheduler = getAsyncSchedulerMethod.invoke(Bukkit.getServer());

                Class<?> schedulerClass = syncScheduler.getClass();
                Method executeMethod = schedulerClass.getMethod("cancelTasks", Plugin.class);
                executeMethod.invoke(syncScheduler, plugin);
                
                //schedulerClass = asyncScheduler.getClass();
                //executeMethod = schedulerClass.getMethod("cancelTasks", Plugin.class);
                //executeMethod.invoke(asyncScheduler, plugin);
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
    }
    
    
    /**
     * Teleports an entity asynchronously if the server is running Folia.
     *
     * @param entity The entity to teleport.
     * @param loc    The target location.
     * @param cause  The cause of the teleport (from the {@code TeleportCause} enum).
     * @return {@code true} if the teleportation was successful, {@code false} otherwise.
     */
    @SuppressWarnings("unchecked")
    public static boolean teleportEntity(Entity entity, Location loc, TeleportCause cause) {
        if (!isFoliaServer) {
            return entity.teleport(loc, cause);
        }
        try {
            Method teleportAsyncMethod = ReflectionUtil.getMethod(Entity.class, "teleportAsync", Location.class, TeleportCause.class);
            Object result = ReflectionUtil.invokeMethod(teleportAsyncMethod, entity, loc, cause);
            CompletableFuture<Boolean> res = (CompletableFuture<Boolean>) result;
            return res.get();
        } catch (Exception e) {
            e.printStackTrace();
        }
        return false;
    }
    
    /**
     * Checks if a given task is scheduled.
     *
     * @param task The task to check. This can be an integer task ID (for Paper/Spigot), a {@code ScheduledTask} object (for Folia), or a {@code Thread} object.
     * @return {@code true} if the task is scheduled, {@code false} otherwise.
     */
    public static boolean isTaskScheduled(Object task) {
        if (task == null) {
            return false;
        }
        if (task instanceof Integer) {
            return (int)task != -1;
        }
        return true;
    } 
}
