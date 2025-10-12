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
package fr.neatmonster.nocheatplus.checks.combined;

import org.bukkit.Input;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerInputEvent;

import fr.neatmonster.nocheatplus.checks.moving.MovingData;
import fr.neatmonster.nocheatplus.players.DataManager;
import fr.neatmonster.nocheatplus.players.IPlayerData;
/**
 * Compatibility class in case of old version without Input.
 */
public class InputChangeListener implements Listener {
    
    public InputChangeListener() {}
    
    /**
     * Sets the input data in this move.
     * Do note that: 1) this is called only when the player toggles on/of a specific input (i.e.: press/release keys);
     * 2) the input set here will be re-mapped in case of split moves.
     */
    @EventHandler(priority = EventPriority.MONITOR)
    public void onInputChange(final PlayerInputEvent event) {
        final IPlayerData pData = DataManager.getPlayerData(event.getPlayer());
        final MovingData data = pData.getGenericInstance(MovingData.class);
        Input bukkitInput = event.getInput();
        data.input.set(Boolean.compare(bukkitInput.isLeft(), bukkitInput.isRight()), Boolean.compare(bukkitInput.isForward(), bukkitInput.isBackward()), bukkitInput.isJump(), bukkitInput.isSneak(), bukkitInput.isSprint());
    }
}
