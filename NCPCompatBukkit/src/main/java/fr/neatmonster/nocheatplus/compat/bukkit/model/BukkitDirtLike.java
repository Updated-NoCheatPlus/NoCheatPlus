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
import fr.neatmonster.nocheatplus.players.IPlayerData;
import fr.neatmonster.nocheatplus.utilities.map.BlockCache;

/**
 * Grass path and farm land shape model.
 */
public class BukkitDirtLike implements BukkitShapeModel {
    @Override
    public double[] getShape(BlockCache blockCache, World world, int x, int y, int z) {
        // Farmland changed the height from 1.10 but too lazy and not many clients on this version. Taking shortcut!
        final IPlayerData data = blockCache.getPlayerData();
        if (data != null && data.getClientVersion().isLowerThan(ClientVersion.V_1_9)) {
            return new double[] {0.0, 0.0, 0.0, 1.0, 1.0, 1.0};
        }
        return new double[] {0.0, 0.0, 0.0, 1.0, 0.9375, 1.0};
    }

    @Override
    public int getFakeData(BlockCache blockCache, World world, int x, int y, int z) {
        return 0;
    }

    @Override
    public double[] getVisualShape(BlockCache blockCache, World world, int x, int y, int z) {
        return getShape(blockCache, world, x, y, z);
    }

    @Override
    public boolean isCollisionSameVisual(BlockCache blockCache, World world, int x, int y, int z) {
        return true;
    }
}
