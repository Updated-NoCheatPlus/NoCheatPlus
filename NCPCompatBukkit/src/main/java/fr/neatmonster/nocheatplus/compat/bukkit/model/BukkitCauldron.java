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

public class BukkitCauldron implements BukkitShapeModel {
    private final static double[] new1_13_2Bounds = {
            0.0, 0.0, 0.0, 0.125, 1.0, 0.25, 
            0.0, 0.0, 0.75, 0.125, 1.0, 1.0, 
            0.125, 0.0, 0.0, 0.25, 1.0, 0.125, 
            0.125, 0.0, 0.875, 0.25, 1.0, 1.0, 
            0.75, 0.0, 0.0, 1.0, 1.0, 0.125, 
            0.75, 0.0, 0.875, 1.0, 1.0, 1.0, 
            0.875, 0.0, 0.125, 1.0, 1.0, 0.25, 
            0.875, 0.0, 0.75, 1.0, 1.0, 0.875, 
            0.0, 0.1875, 0.25, 1.0, 0.25, 0.75, 
            0.125, 0.1875, 0.125, 0.875, 0.25, 0.25, 
            0.125, 0.1875, 0.75, 0.875, 0.25, 0.875, 
            0.25, 0.1875, 0.0, 0.75, 1.0, 0.125, 
            0.25, 0.1875, 0.875, 0.75, 1.0, 1.0, 
            0.0, 0.25, 0.25, 0.125, 1.0, 0.75, 
            0.875, 0.25, 0.25, 1.0, 1.0, 0.75};
    private final static double[] new1_13Bounds = makeCauldron(0.1875, 0.125, 0.8125, 0.0625);
    private final static double[] legacyBounds = makeCauldron(0.0, 0.125, 1.0, 0.3125);

    private static double[] makeCauldron(double minY, double sideWidth, double sideHeight, double coreHeight) {
        return new double[] {
                // Core
                sideWidth, minY, sideWidth, 1 - sideWidth, minY + coreHeight, 1 - sideWidth,
                // 4 side
                0.0, minY, 0.0, 1.0, minY + sideHeight, sideWidth,
                0.0, minY, 1.0 - sideWidth, 1.0, minY + sideHeight, 1.0,
                0.0, minY, 0.0, sideWidth, minY + sideHeight, 1.0,
                1.0 - sideWidth, minY, 0.0, 1.0, minY + sideHeight, 1.0
        };
    }

    @Override
    public double[] getShape(BlockCache blockCache, World world, int x, int y, int z) {
        IPlayerData pData = blockCache.getPlayerData();
        if (pData != null) {
            if (pData.getClientVersion().isLowerThan(ClientVersion.V_1_13)) {
                return legacyBounds;
            } else if (pData.getClientVersion().isLowerThan(ClientVersion.V_1_13_2)) {
                return new1_13Bounds;
            } else return new1_13_2Bounds;
        }
        return new1_13Bounds;
    }

    @Override
    public int getFakeData(BlockCache blockCache, World world, int x, int y, int z) {
        return 0;
    }

    @Override
    public double[] getVisualShape(BlockCache blockCache, World world, int x, int y, int z) {
        return new double[] {0.0, 0.1875, 0.0, 1.0, 1.0, 1.0};
        //return getShape(blockCache, world, x, y, z);
    }

    @Override
    public boolean isCollisionSameVisual(BlockCache blockCache, World world, int x, int y, int z) {
        // Should be true , but current implementation of Visible check of NCP can't do complex ray-trace for performance wise and technical limitations
        return false;
    }
}