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
package fr.neatmonster.nocheatplus.checks.net;

import org.bukkit.entity.Player;

import fr.neatmonster.nocheatplus.checks.Check;
import fr.neatmonster.nocheatplus.checks.CheckType;

/**
 * The WrongTurn check will detect players who send improbable rotations 
 */
public class WrongTurn extends Check {
    
    public WrongTurn() {
        super(CheckType.NET_WRONGTURN);
    }

    /**
     * Checks a player
     * @param player
     * @param pitch
     * @param data
     * @param cc
     * @return true if successful.
     * 
     */
    public boolean check(final Player player, final float pitch, final NetData data, final NetConfig cc) {
        boolean cancel = false;
        // Currently, only impossible pitch is detected, later, we might throw in actual pattern-based checks.
        if (pitch > 90.0 || pitch < -90.0) {
            data.wrongTurnVL++;
            cancel = executeActions(player, data.wrongTurnVL, 1, cc.wrongTurnActions).willCancel();
        }
        return cancel;
    }
}