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
package fr.neatmonster.nocheatplus.logging.details;

import org.bukkit.Bukkit;
import org.bukkit.plugin.IllegalPluginAccessException;
import org.bukkit.plugin.Plugin;

import fr.neatmonster.nocheatplus.compat.SchedulerHelper;
import fr.neatmonster.nocheatplus.components.registry.feature.TickListener;
import fr.neatmonster.nocheatplus.utilities.TickTask;

public class BukkitLogNodeDispatcher extends AbstractLogNodeDispatcher { // TODO: Name.


    /**
     * Permanent TickListener for logging [TODO: on-demand scheduling, but thread-safe. With extra lock.]
     */
    private final TickListener taskPrimary = new TickListener() {

        @Override
        public void onTick(final int tick, final long timeLast) {
            if (runLogsPrimary()) {
                // TODO: Here or within runLogsPrimary, handle rescheduling.
            }
        }

    };

    /**
     * Needed for scheduling.
     */
    private final Plugin plugin;

    public BukkitLogNodeDispatcher(Plugin plugin) {
        this.plugin = plugin;
    }

    /**
     * Necessary logging to a primary thread task (TickTask) or asynchronously.
     * This can be called multiple times without causing damage.
     */
    public void startTasks() {
        // TODO: This is a temporary solution. Needs on-demand scheduling [or a wrapper task].
        TickTask.addTickListener(taskPrimary);
        scheduleAsynchronous(); // Just in case.
    }

    @Override
    protected void scheduleAsynchronous() {
        synchronized (queueAsynchronous) {
            if (!SchedulerHelper.isTaskScheduled(taskAsynchronousID)) {
                // Deadlocking should not be possible.
                try {
                    taskAsynchronousID = SchedulerHelper.runTaskAsync(plugin, (arg) -> taskAsynchronous.run());
                } catch (IllegalPluginAccessException ex) {
                    // (Should be during onDisable, ignore for now.)
                }
                // TODO: Re-check task id here.
            }
        }
    }

    @Override
    protected final boolean isPrimaryThread() {
        return Bukkit.isPrimaryThread();
    }

    @Override
    protected void cancelTask(Object taskInfo) {
        SchedulerHelper.cancelTask(taskInfo);
    }

    @Override
    protected boolean isTaskScheduled(Object taskInfo) {
        return SchedulerHelper.isTaskScheduled(taskInfo);
    }

}
