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
package fr.neatmonster.nocheatplus.actions.types;

import java.util.logging.Level;

import org.bukkit.Bukkit;
import org.bukkit.plugin.Plugin;

import fr.neatmonster.nocheatplus.actions.AbstractActionList;
import fr.neatmonster.nocheatplus.actions.ParameterHolder;
import fr.neatmonster.nocheatplus.checks.ViolationData;
import fr.neatmonster.nocheatplus.compat.SchedulerHelper;
import fr.neatmonster.nocheatplus.config.ConfPaths;
import fr.neatmonster.nocheatplus.config.ConfigManager;
import fr.neatmonster.nocheatplus.logging.StaticLog;
import fr.neatmonster.nocheatplus.utilities.CheckUtils;
import fr.neatmonster.nocheatplus.utilities.StringUtil;

/**
 * Execute a command by imitating an administrator typing the command directly into the console.
 */
public class CommandAction<D extends ParameterHolder, L extends AbstractActionList<D, L>> extends ActionWithParameters<D, L> {

    private final boolean logDebug;
    /** The instance of NoCheatPlus. */
    private final Plugin plugin;

    /**
     * Instantiates a new command action.
     * 
     * @param name
     *            the name
     * @param delay
     *            the delay
     * @param repeat
     *            the repeat
     * @param command
     *            the command
     */
    public CommandAction(final String name, final int delay, final int repeat, final String command) {
        // Log messages may have color codes now.
        super(name, delay, repeat, command);
        logDebug = ConfigManager.getConfigFile().getBoolean(ConfPaths.LOGGING_EXTENDED_COMMANDS_ACTIONS);
        plugin = Bukkit.getPluginManager().getPlugin("NoCheatPlus");
    }

    /* (non-Javadoc)
     * @see fr.neatmonster.nocheatplus.actions.Action#execute(fr.neatmonster.nocheatplus.checks.ViolationData)
     */
    @Override
    public void execute(final D violationData) {
        final String command = getMessage(violationData);
        SchedulerHelper.runSyncTask(plugin, (arg) -> {
        	try {
                Bukkit.getServer().dispatchCommand(Bukkit.getConsoleSender(), command);
                if (logDebug) {
                    debug(violationData, command);
                }
            }
            catch (final Exception e) {
                StaticLog.logOnce(Level.WARNING, "Failed to execute the command '" + command + "': " + e.getMessage()
                + ", please check if everything is setup correct.", StringUtil.throwableToString(e));
            }
        });
    }

    private void debug(final D violationData, String command) {
        final String prefix;
        if (violationData instanceof ViolationData) {
            ViolationData vd = (ViolationData) violationData;
            prefix = CheckUtils.getLogMessagePrefix(vd.player, vd.check.getType());
        }
        else {
            prefix = "";
        }
        StaticLog.logDebug(prefix + "Execute command action: " + command);
    }

    /**
     * Convert the commands data into a string that can be used in the configuration files.
     * 
     * @return the string
     */
    @Override
    public String toString() {
        return "cmd:" + name + ":" + delay + ":" + repeat;
    }

}
