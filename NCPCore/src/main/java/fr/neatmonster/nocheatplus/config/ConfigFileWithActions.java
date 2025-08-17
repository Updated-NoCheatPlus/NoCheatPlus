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
package fr.neatmonster.nocheatplus.config;

import fr.neatmonster.nocheatplus.actions.AbstractActionFactory;
import fr.neatmonster.nocheatplus.actions.AbstractActionList;
import fr.neatmonster.nocheatplus.actions.Action;
import fr.neatmonster.nocheatplus.actions.ActionData;
import fr.neatmonster.nocheatplus.permissions.RegisteredPermission;

public abstract class ConfigFileWithActions<D extends ActionData, L extends AbstractActionList<D, L>> extends RawConfigFile {

    /** The factory. */
    protected AbstractActionFactory<D, L> factory = null;

    /**
     * This should set (override if necessary) a default ActionFactory. NCP will use ConfigManager.getActionsFactoryFactory. <br>
     * Do this after reading new data or changing the AbstractActionFactory instance.<br>
     * This must set or override the internal factory field to enable/update ActionList getting.<br>
     * If factory is null on getting an ActionList, this will be called internally.
     */
    public abstract void setActionFactory();

    /**
     * Explicitly set the ActionFactory, also allow setting to null for lazy reset/get.
     * @param factory
     */
    public void setActionFactory(final AbstractActionFactory<D, L> factory){
        this.factory = factory;
    }

    /**
     * A convenience method to get an optimized action list from the
     * configuration.
     * 
     * @param path
     *            the path
     * @param permission
     *            The ordinary check bypass permission, which will be extended
     *            by '.silent' to obtain the log action bypass permission.
     * @return the action list
     */
    public L getOptimizedActionList(final String path, final RegisteredPermission permission)
    {
        return getDefaultActionList(path, permission).getOptimizedCopy(this);
    }

    /**
     * A convenience method to get default action lists from the configuration,
     * without applying any optimization.
     * 
     * @param path
     *            the path
     * @param permission
     *            The ordinary check bypass permission, which will be extended
     *            by '.silent' to obtain the log action bypass permission.
     * @return the action list
     */
    public L getDefaultActionList(final String path, final RegisteredPermission permission)
    {
        if (factory == null){
            setActionFactory();
        }
        final String value = this.getString(path);
        return factory.createActionList(value, permission);
    }


    /**
     * Safely store ActionLists back into the yml file.
     * 
     * @param path
     *            the path
     * @param list
     *            the list
     */
    public void set(final String path, final L list) {
        final StringBuffer string = new StringBuffer();

        for (final Integer threshold : list.getThresholds()) {
            if (threshold > 0)
                string.append(" vl>").append(threshold);
            for (final Action<D, L> action : list.getActions(threshold))
                string.append(" ").append(action);
        }

        set(path, string.toString().trim());
    }

}
