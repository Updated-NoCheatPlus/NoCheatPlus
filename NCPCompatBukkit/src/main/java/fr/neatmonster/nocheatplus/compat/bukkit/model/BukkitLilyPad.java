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
package fr.neatmonster.nocheatplus.compat.bukkit.model;

import org.bukkit.World;

import fr.neatmonster.nocheatplus.compat.versions.ClientVersion;
import fr.neatmonster.nocheatplus.utilities.map.BlockCache;

/**
 * Water Lily shape model.
 */
public class BukkitLilyPad implements BukkitShapeModel {
    @Override
    public double[] getShape(BlockCache blockCache, World world, int x, int y, int z) {
        if (blockCache.getPlayerData().getClientVersion().isLowerThan(ClientVersion.V_1_13)) {
            return new double[] {0.0625, 0.0, 0.0625, 0.9375, 0.125, 0.9375};
        }
        return new double[] {0.0625, 0.0, 0.0625, 0.9375, 0.09375, 0.9375};
    }

    @Override
    public int getFakeData(BlockCache blockCache, World world, int x, int y, int z) {
        return 0;
    }
}
