package fr.neatmonster.nocheatplus.compat.blocks.init.vanilla;

import fr.neatmonster.nocheatplus.compat.blocks.BlockPropertiesSetup;
import fr.neatmonster.nocheatplus.compat.blocks.init.BlockInit;
import fr.neatmonster.nocheatplus.config.ConfPaths;
import fr.neatmonster.nocheatplus.config.ConfigFile;
import fr.neatmonster.nocheatplus.config.ConfigManager;
import fr.neatmonster.nocheatplus.config.WorldConfigProvider;
import fr.neatmonster.nocheatplus.logging.StaticLog;
import fr.neatmonster.nocheatplus.utilities.map.BlockFlags;
import fr.neatmonster.nocheatplus.utilities.map.BlockProperties;

public class BlocksMC1_15 implements BlockPropertiesSetup{
	public BlocksMC1_15() {
        BlockInit.assertMaterialExists("BEEHIVE");
        BlockInit.assertMaterialExists("BEE_NEST");
        BlockInit.assertMaterialExists("HONEY_BLOCK");
        BlockInit.assertMaterialExists("HONEYCOMB_BLOCK");
    }
	@SuppressWarnings("deprecation")
	@Override
	public void setupBlockProperties(WorldConfigProvider<?> worldConfigProvider) {
        BlockFlags.addFlags("BEEHIVE", BlockFlags.FULLY_SOLID_BOUNDS);
        BlockFlags.addFlags("BEE_NEST", BlockFlags.FULLY_SOLID_BOUNDS);
        BlockFlags.addFlags("HONEYCOMB_BLOCK", BlockFlags.FULLY_SOLID_BOUNDS);
        // TODO: Is this a good idea to just allow low jump to fix honey block elevator?
        BlockFlags.addFlags("HONEY_BLOCK", BlockFlags.SOLID_GROUND | BlockFlags.F_STICKY);
        
        BlockProperties.setBlockProps("BEEHIVE", new BlockProperties.BlockProps(BlockProperties.woodAxe, 0.6f));
        BlockProperties.setBlockProps("BEE_NEST", new BlockProperties.BlockProps(BlockProperties.woodAxe, 0.3f));
        BlockProperties.setBlockProps("HONEYCOMB_BLOCK", new BlockProperties.BlockProps(BlockProperties.noTool, 0.6f));
        BlockProperties.setBlockProps("HONEY_BLOCK", BlockProperties.instantType);
        ConfigFile config = ConfigManager.getConfigFile();
        if (config.getBoolean(ConfPaths.BLOCKBREAK_DEBUG, config.getBoolean(ConfPaths.CHECKS_DEBUG, false)))
        StaticLog.logInfo("Added block-info for Minecraft 1.15 blocks.");
	}
}
