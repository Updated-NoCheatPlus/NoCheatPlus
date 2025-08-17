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
package fr.neatmonster.nocheatplus.utilities.moving;

/**
 * Magic for vehicles.
 * 
 * @author asofold
 *
 */
public class MagicVehicle {

    // Vertical distances //

    /** Maximum descend distance. */
    public static final double maxDescend = 5.0;
    /** Maximum ascend distance (overall, no special effects counted in). */
    // TODO: Likely needs adjustments, many thinkable edge cases, also pistons (!).
    // TODO: Does trigger on vehicle enter somehow some time.
    public static final double maxAscend = 0.27;
    
    public static final double maxRailsVertical = 0.5;

    public static final double boatGravityMin = Magic.GRAVITY_MIN / 3.0;
    /** Allow lower gravity when falling this fast. */
    public static final double boatLowGravitySpeed = 0.5;
    /** Simplistic approach for falling speed more than 0.5. */
    public static final double boatGravityMinAtSpeed = Magic.GRAVITY_MIN / 12.0;
    public static final double boatGravityMax = (Magic.GRAVITY_MAX + Magic.GRAVITY_MIN) / 2.0;
    /** The speed up to which gravity mechanics roughly work. */
    public static final double boatVerticalFallTarget = 3.7;
    public static final double boatMaxBackToSurfaceAscend = 3.25;

    /** Max ascending in-air jump phase. */
    public static final int maxJumpPhaseAscend = 8;

    /** Absolute max. speed. */
    public static final double climbSpeed = 0.1625;

}
