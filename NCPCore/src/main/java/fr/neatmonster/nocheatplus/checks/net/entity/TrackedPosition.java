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
package fr.neatmonster.nocheatplus.checks.net.entity;

import fr.neatmonster.nocheatplus.compat.versions.ServerVersion;

/**
 * Fixed-point entity position, matching the encoding of relative move packets.
 * Before 1.9 a step is 1/32 of a block. After that it is 1/4096.
 * Before 1.16 the client floors the packed coordinate; afterwards it rounds.
 */
final class TrackedPosition {

    static final double SCALE = ServerVersion.isAtLeast("1.9") ? 4096.0D : 32.0D;
    private static final boolean ROUND = ServerVersion.isAtLeast("1.16");
    private static final boolean SNAP_LEGACY = !ServerVersion.isAtLeast("1.9");

    double x;
    double y;
    double z;

    void copyFrom(TrackedPosition other) {
        x = other.x;
        y = other.y;
        z = other.z;
    }

    void setAbsolute(double x, double y, double z) {
        if (SNAP_LEGACY) {
            this.x = ((int) (x * 32.0D)) / 32.0D;
            this.y = ((int) (y * 32.0D)) / 32.0D;
            this.z = ((int) (z * 32.0D)) / 32.0D;
            return;
        }
        this.x = x;
        this.y = y;
        this.z = z;
    }

    void addEncoded(long dx, long dy, long dz) {
        x = add(x, dx);
        y = add(y, dy);
        z = add(z, dz);
    }

    private static double add(double value, long delta) {
        if (delta == 0L) {
            return value;
        }
        if (ROUND) {
            return (Math.round(value * SCALE) + delta) / SCALE;
        }
        return (Math.floor(value * SCALE) + delta) / SCALE;
    }

}
