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

/**
 * Bounce preparation state.
 * @author asofold
 *
 */
public enum BounceType {
    /** No bounce happened. */
    NO_BOUNCE,
    /** Ordinary bounce off a static block underneath. */
    STATIC,
    /**
     * Ordinary bounce, due to a slime block having been underneath in the
     * past. Rather for logging.
     */
    STATIC_PAST,
    /**
     * A slime block has been underneath, pushing up into the player.
     */
    STATIC_PAST_AND_PUSH,
    // WEAK_PUSH <- TBD: with edge on slime, or with falling inside of the new slime block position?
}