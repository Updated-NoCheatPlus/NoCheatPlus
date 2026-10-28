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
package fr.neatmonster.nocheatplus.compat.blocks.init.vanilla;

import org.bukkit.Material;

import fr.neatmonster.nocheatplus.compat.blocks.BlockPropertiesSetup;
import fr.neatmonster.nocheatplus.compat.blocks.init.BlockInit;
import fr.neatmonster.nocheatplus.compat.versions.ServerVersion;
import fr.neatmonster.nocheatplus.config.ConfPaths;
import fr.neatmonster.nocheatplus.config.ConfigFile;
import fr.neatmonster.nocheatplus.config.ConfigManager;
import fr.neatmonster.nocheatplus.config.WorldConfigProvider;
import fr.neatmonster.nocheatplus.logging.StaticLog;
import fr.neatmonster.nocheatplus.utilities.map.BlockFlags;
import fr.neatmonster.nocheatplus.utilities.map.BlockProperties;
import fr.neatmonster.nocheatplus.utilities.map.MaterialUtil;


public class BlocksMC26 implements BlockPropertiesSetup {
    public BlocksMC26() {
        BlockInit.assertMaterialExists("GOLDEN_DANDELION");
    }
    
    @Override
    public void setupBlockProperties(WorldConfigProvider<?> worldConfigProvider) {
        ConfigFile config = ConfigManager.getConfigFile();
        // GOLDEN_DANDELION is set already set within MaterialUtil#Instant_Plants.
        if (ServerVersion.isAtLeast("26.2")) {
            for (Material mat : MaterialUtil.CINNABAR_BLOCKS) {
                BlockProperties.setBlockProps(mat, new BlockProperties.BlockProps(BlockProperties.woodPickaxe, 1.5f, true));
                BlockFlags.addFlags(mat, BlockFlags.SOLID_GROUND);
            }
            for (Material mat : MaterialUtil.SULFUR_BLOCKS) {
                BlockProperties.setBlockProps(mat, new BlockProperties.BlockProps(BlockProperties.woodPickaxe, 1.5f, true));
                BlockFlags.addFlags(mat, BlockFlags.SOLID_GROUND);
            }
            BlockFlags.addFlags("SULFUR_SPIKE", BlockFlags.SOLID_GROUND | BlockFlags.F_VARIABLE);
            BlockProperties.setBlockProps("SULFUR_SPIKE", new BlockProperties.BlockProps(BlockProperties.woodPickaxe, 1.5f));
        }
        if (ServerVersion.isAtLeast("26.3")) {
            for (final Material material : MaterialUtil.WOOL_STAIRS) {
                BlockInit.setPropsAs(material, Material.WHITE_WOOL);
            }
            for (final Material material : MaterialUtil.WOOL_SLABS) {
                BlockInit.setPropsAs(material, Material.WHITE_WOOL);
            }
            for (final Material material : MaterialUtil.CONCRETE_STAIRS) {
                BlockInit.setPropsAs(material, Material.WHITE_CONCRETE);
            }
            for (final Material material : MaterialUtil.CONCRETE_SLABS) {
                BlockInit.setPropsAs(material, Material.WHITE_CONCRETE);
            }
            // Not bouncy!
            BlockProperties.setBlockProps("STRAW_BED", new BlockProperties.BlockProps(BlockProperties.woodHoe, 0.2f));
            BlockFlags.setBlockFlags("STRAW_BED", BlockFlags.SOLID_GROUND);
            
            BlockProperties.setBlockProps("SHELF_MUSHROOM", BlockProperties.instantType);
            BlockFlags.addFlags("SHELF_MUSHROOM", BlockFlags.F_VARIABLE | BlockFlags.F_BOUNCE25);
            
            // red shrub added in Instant_plants
            
        }
        if (config.getBoolean(ConfPaths.BLOCKBREAK_DEBUG, config.getBoolean(ConfPaths.CHECKS_DEBUG, false)))
            StaticLog.logInfo("Added block-info for Minecraft 26 blocks.");
    }
}
