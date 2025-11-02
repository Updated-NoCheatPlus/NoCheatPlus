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
package fr.neatmonster.nocheatplus.utilities.ds.map;

import fr.neatmonster.nocheatplus.utilities.Misc;

/**
 * Integer block coordinate with support for in-place mutation.
 */
public class BlockCoord implements Cloneable {
    /*
     * NOTE: This class is now mutable to allow in-place addition/subtraction
     * operations that do not create new instances. Because instances are
     * mutable they are NOT safe to use as keys in hash-based collections if
     * their coordinates may change while stored; use {@link #copy()} to get an
     * immutable snapshot when needed.
     */
    private int x;
    private int y;
    private int z;
    
    /**
     * Constructs a BlockCoord with integer coordinates.
     *
     * @param x The x-coordinate.
     * @param y The y-coordinate.
     * @param z The z-coordinate.
     */
    public BlockCoord(int x, int y, int z) {
        this.x = x;
        this.y = y;
        this.z = z;
    }
    
    /**
     * Constructs a BlockCoord from double coordinates by converting them
     * to block integer coordinates using the same method as Bukkit's Location#locToBlock().
     *
     * @param x The x-coordinate.
     * @param y The y-coordinate.
     * @param z The z-coordinate.
     */
    public BlockCoord(double x, double y, double z) {
        this.x = Misc.floor(x);
        this.y = Misc.floor(y);
        this.z = Misc.floor(z);
    }
    
    public int getX() {
        return x;
    }
    
    public int getY() {
        return y;
    }
    
    public int getZ() {
        return z;
    }
    
    @Override
    public int hashCode() {
        return CoordHash.hashCode3DPrimes(x, y, z);
    }
    
    @Override
    public boolean equals(Object obj) {
        if (this == obj) return true;
        if (obj == null || getClass() != obj.getClass()) return false;
        BlockCoord bc = (BlockCoord) obj;
        return bc.getX() == x && bc.getY() == y && bc.getZ() == z;
    }

    /**
     * Add the given BlockCoord to this one. 
     * 
     * @param other the other BlockCoord to add to this one
     * @return this (after modification)
     */
    public BlockCoord add(BlockCoord other) {
        return add(other.x, other.y, other.z);
    }

    /**
     * Add the given integers to this BlockCoord.
     * 
     * @param dx delta x
     * @param dy delta y
     * @param dz delta z
     * @return this (after modification)
     */
    public BlockCoord add(int dx, int dy, int dz) {
        this.x += dx;
        this.y += dy;
        this.z += dz;
        return this;
    }

    /**
     * Subtract the given BlockCoord from this one.
     * 
     * @param other the other BlockCoord to subtract
     * @return this (after modification)
     */
    public BlockCoord subtract(BlockCoord other) {
        return subtract(other.x, other.y, other.z);
    }
    
    /**
     * Subtract the given integers from this BlockCoord.
     * 
     * @param dx delta x
     * @param dy delta y
     * @param dz delta z
     * @return this (after modification)
     */
    public BlockCoord subtract(int dx, int dy, int dz) {
        this.x -= dx;
        this.y -= dy;
        this.z -= dz;
        return this;
    }

    /**
     * Returns the squared Euclidean distance between this block coordinate
     * and another BlockCoord (integer arithmetic). Returns a double to match
     * common expectations and avoid overflow for large distances.
     *
     * @param other the other block coordinate
     * @return squared distance as a double
     */
    public double distanceSquared(BlockCoord other) {
        long dx = this.x - other.x;
        long dy = this.y - other.y;
        long dz = this.z - other.z;
        return  dx * dx + dy * dy + dz * dz;
    }

    /**
     * Returns the squared Euclidean distance between this block coordinate
     * and the specified integer coordinates.
     *
     * @param x x coordinate to compare to
     * @param y y coordinate to compare to
     * @param z z coordinate to compare to
     * @return squared distance as a double
     */
    public double distanceSquared(int x, int y, int z) {
        long dx = this.x - x;
        long dy = this.y - y;
        long dz = this.z - z;
        return  dx * dx + dy * dy + dz * dz;
    }

    /**
     * Create a copy of this BlockCoord. Returns a new independent instance
     * with the same coordinates (useful when you need an immutable snapshot
     * while mutating the original).
     *
     * @return a new BlockCoord with identical coordinates
     */
    public BlockCoord copy() {
        return new BlockCoord(x, y, z);
    }

    /**
     * Clone this BlockCoord.
     * <p>
     * This implementation attempts to use Object#clone() (via
     * super.clone()). If, for some reason,
     * cloning via super is not supported (CloneNotSupportedException), we
     * fall back to creating a manual copy via {@link #copy()}.
     *
     * @return a new BlockCoord with identical coordinates
     */
    @Override
    public BlockCoord clone() {
        try {
            return (BlockCoord) super.clone();
        } catch (CloneNotSupportedException e) {
            return copy();
        }
    }
}