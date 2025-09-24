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
package fr.neatmonster.nocheatplus.compat.blocks.init.vanilla.special;

import java.util.LinkedList;
import java.util.List;

import org.bukkit.Material;

import fr.neatmonster.nocheatplus.compat.bukkit.BridgeMaterial;
import fr.neatmonster.nocheatplus.compat.activation.ActivationUtil;
import fr.neatmonster.nocheatplus.compat.blocks.AbstractBlockPropertiesPatch;
import fr.neatmonster.nocheatplus.config.WorldConfigProvider;
import fr.neatmonster.nocheatplus.logging.StaticLog;
import fr.neatmonster.nocheatplus.utilities.StringUtil;
import fr.neatmonster.nocheatplus.utilities.map.BlockFlags;
import fr.neatmonster.nocheatplus.utilities.map.MaterialUtil;

/**
 * Multi client protocol support since 1.7, roughly.
 *
 * @author asofold
 *
 */
public class MultiClientProtocolBlockShapePatch extends AbstractBlockPropertiesPatch {
    // TODO: Later just dump these into the generic registry (on activation), let BlockProperties fetch.

    public MultiClientProtocolBlockShapePatch() {
        activation
        .neutralDescription("Block shape patch for multi client protocol support.")
        .advertise(true)
        .setConditionsAND()
        .notUnitTest()
        .condition(ActivationUtil.getMultiProtocolSupportPluginActivation())
        // TODO: Other/More ?
        ;
    }

    @Override
    public void setupBlockProperties(WorldConfigProvider<?> worldConfigProvider) {
        final List<String> done = new LinkedList<String>();
        done.add("water_lily");
        done.add("farmland");
        if (BridgeMaterial.GRASS_PATH != null) done.add("grass_path");
        done.add("honey_block");

        try {
            for (Material mat : MaterialUtil.SHULKER_BOXES) {
                BlockFlags.addFlags(mat, BlockFlags.F_SOLID | BlockFlags.F_GROUND_HEIGHT | BlockFlags.F_GROUND);
            }
            done.add("shulker_box");
        }
        catch (Throwable t) {}

        StaticLog.logInfo("Applied block patches for multi client protocol support: " + StringUtil.join(done, ", "));
    }

}
