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
package fr.neatmonster.nocheatplus.hooks;

import org.bukkit.entity.Player;

import fr.neatmonster.nocheatplus.checks.CheckType;
import fr.neatmonster.nocheatplus.checks.access.IViolationInfo;

/**
 * Compatibility hooks have to implement this.<br>
 * NOTES: 
 * Some checks run asynchronously, the hooks using these also have to support processing in an extra thread, check with fr.neatmonster.utilities.CheckTypeUtil.needsSynchronization(CheckType).
 * Hooks that can be called asynchronously must not register new hooks that might run asynchronously during processing (...).
 * 
 * @author asofold
 */
public interface NCPHook {

    /**
     * For logging purposes.
     * 
     * @return the hook name
     */
    public String getHookName();

    /**
     * For logging purposes.
     * 
     * @return the hook version
     */
    public String getHookVersion();

    /**
     * This is called on failure of a check.<br>
     * This is the minimal interface, it might later be extended by specific information like (target) locations and VL,
     * but with this a lot is possible already (see CNCP).<br>
     * See AbstractNCPHook for future compatibility questions.
     * 
     * @param checkType
     *            the check that failed
     * @param player
     *            the player that failed the check
     * @param info 
     *            Extended information about the violations.
     * @return if we need to cancel the check failure processing
     */
    public boolean onCheckFailure(CheckType checkType, Player player, IViolationInfo info);
}
