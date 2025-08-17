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
package fr.neatmonster.nocheatplus.checks.moving.model;

import org.bukkit.Location;
import org.bukkit.entity.Player;

import fr.neatmonster.nocheatplus.compat.MCAccess;
import fr.neatmonster.nocheatplus.components.registry.event.IHandle;
import fr.neatmonster.nocheatplus.utilities.location.PlayerLocation;

/**
 * Player-specific MoveInfo.
 * 
 * @author asofold
 *
 */
public class PlayerMoveInfo extends MoveInfo<PlayerLocation, Player> {

    public PlayerMoveInfo(final IHandle<MCAccess> mcAccess){
        super(mcAccess, new PlayerLocation(mcAccess, null), new PlayerLocation(mcAccess, null));
    }

    @Override
    protected void set(PlayerLocation rLoc, Location loc, Player player, double yOnGround) {
        rLoc.set(loc, player, yOnGround);
    }

}
