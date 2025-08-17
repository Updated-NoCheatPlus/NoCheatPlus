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

import org.bukkit.block.BlockFace;
import org.bukkit.block.data.BlockData;
import org.bukkit.block.data.Directional;

public class BukkitDirectionalCentered extends AbstractBukkitCentered {

    public BukkitDirectionalCentered(double inset, double length,
            boolean invertFace) {
        super(inset, length, invertFace);
    }

    @Override
    protected BlockFace getFacing(final BlockData blockData) {
        if (blockData instanceof Directional) {
            return ((Directional) blockData).getFacing();
        }
        return BlockFace.SELF;
    }
}