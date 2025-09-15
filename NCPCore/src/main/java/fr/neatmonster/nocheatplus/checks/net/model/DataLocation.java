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
package fr.neatmonster.nocheatplus.checks.net.model;

import org.bukkit.Location;

import fr.neatmonster.nocheatplus.components.location.IGetPositionWithLook;
import fr.neatmonster.nocheatplus.utilities.location.LocUtil;

/**
 * An immutable location data object with coordinates and pitch and yaw.
 * 
 * @author asofold
 *
 */
public class DataLocation implements IGetPositionWithLook {

    // TODO: equals.
    // TODO: hashCode won't help much if sub classes will override it anyway.

    private final double x;

    private final double y;

    private final double z;

    private final float yaw;

    private final float pitch;

    public DataLocation(double x, double y, double z, float yaw, float pitch) {
        this.x = x;
        this.y = y;
        this.z = z;
        this.yaw = yaw;
        this.pitch = pitch;
    }

    /**
     * Quick test for same coordinates and looking direction.
     * 
     * @param other
     * @return
     */
    public boolean isSamePosAndLook(final DataLocation other) {
        return getY() == other.getY() && getX() == other.getX() && getZ() == other.getZ() && getPitch() == other.getPitch() && getYaw() == other.getYaw();
    }
    
    /**
     * Quick test for same coordinates and looking direction.
     *
     * @param x
     * @param y
     * @param z
     * @param yaw
     * @param pitch
     * @return
     */
    public boolean isSamePosAndLook(final double x, final double y, final double z, final float yaw, final float pitch) {
        return x == this.getX() && y == this.getY() && z == this.getZ() && yaw == this.getYaw() && pitch == this.getPitch();
    }

    /**
     * Quick test for same coordinates only.
     * 
     * @param other
     * @return
     */
    public boolean isSamePos(final DataLocation other) {
        return getY() == other.getY() && getX() == other.getX() && getZ() == other.getZ();
    }
    
    /**
     * Quick test for match between packet and bukkit event (coordinates only)
     * 
     * @param bukkitLoc
     * @return
     */
    public boolean isSameBukkitPos(final Location bukkitLoc) {
        return getX() == bukkitLoc.getX() && getY() == bukkitLoc.getY() && getZ() == bukkitLoc.getZ();
    }

    @Override
    public String toString() {
        final StringBuilder builder = new StringBuilder(256);
        builder.append("Location(");
        builder.append("x=");
        builder.append(getX());
        builder.append(",y=");
        builder.append(getY());
        builder.append(",z=");
        builder.append(getZ());
        builder.append(",pitch=");
        builder.append(getPitch());
        builder.append(",yaw=");
        builder.append(getYaw());
        builder.append(")");
        return builder.toString();
    }

    @Override
    public double getX() {
        return x;
    }

    @Override
    public double getY() {
        return y;
    }

    @Override
    public double getZ() {
        return z;
    }

    @Override
    public float getYaw() {
        return yaw;
    }

    @Override
    public float getPitch() {
        return pitch;
    }

    @Override
    public int hashCode() {
        return LocUtil.hashCode(this);
    }

}
