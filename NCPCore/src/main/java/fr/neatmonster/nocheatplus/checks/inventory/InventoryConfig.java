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
package fr.neatmonster.nocheatplus.checks.inventory;

import java.util.HashSet;
import java.util.Set;

import org.bukkit.Material;

import fr.neatmonster.nocheatplus.NCPAPIProvider;
import fr.neatmonster.nocheatplus.actions.ActionList;
import fr.neatmonster.nocheatplus.checks.CheckType;
import fr.neatmonster.nocheatplus.checks.access.ACheckConfig;
import fr.neatmonster.nocheatplus.compat.AlmostBoolean;
import fr.neatmonster.nocheatplus.compat.versions.ServerVersion;
import fr.neatmonster.nocheatplus.components.config.value.OverrideType;
import fr.neatmonster.nocheatplus.config.ConfPaths;
import fr.neatmonster.nocheatplus.config.ConfigFile;
import fr.neatmonster.nocheatplus.permissions.Permissions;
import fr.neatmonster.nocheatplus.worlds.IWorldData;

/**
 * Configurations specific for the "inventory" checks. Every world gets one of
 * these assigned to it, or if a world doesn't get it's own, it will use the
 * "global" version.
 */
public class InventoryConfig extends ACheckConfig {

    public final boolean fastClickSpareCreative;
    public final float fastClickShortTermLimit;
    public final float fastClickNormalLimit;
    public final int chestOpenLimit;
    public final Set<String> inventoryExemptions = new HashSet<String>();
    public final float fastClickImprobableWeight;
    public final ActionList fastClickActions;

    public final long fastConsumeDuration;
    public final boolean fastConsumeWhitelist;
    public final Set<Material> fastConsumeItems = new HashSet<Material>();
    public final ActionList fastConsumeActions;

    public final int gutenbergPageLimit;
    public final ActionList gutenbergActions;

    public final boolean instantBowStrict;
    public final long instantBowDelay;
    public final boolean instantBowImprobableFeedOnly;
    public final float instantBowImprobableWeight;
    public final ActionList instantBowActions;

    public final boolean openClose;
    public final boolean openCancelOnMove;
    public final float openImprobableWeight;
    public final boolean openDisableCreative;


    // Hot fixes.
    public final boolean hotFixFallingBlockEndPortalActive;

    /**
     * Instantiates a new inventory configuration.
     * 
     * @param data
     *            the data
     */
    public InventoryConfig(final IWorldData worldData) {
        super(worldData);
        final ConfigFile data = worldData.getRawConfiguration();
        fastClickSpareCreative = data.getBoolean(ConfPaths.INVENTORY_FASTCLICK_SPARECREATIVE);
        fastClickShortTermLimit = (float) data.getDouble(ConfPaths.INVENTORY_FASTCLICK_LIMIT_SHORTTERM);
        fastClickNormalLimit = (float) data.getDouble(ConfPaths.INVENTORY_FASTCLICK_LIMIT_NORMAL);
        chestOpenLimit = data.getInt(ConfPaths.INVENTORY_FASTCLICK_MIN_INTERACT_TIME);
        data.readStringlFromList(ConfPaths.INVENTORY_FASTCLICK_EXCLUDE, inventoryExemptions);
        fastClickImprobableWeight = (float) data.getDouble(ConfPaths.INVENTORY_FASTCLICK_IMPROBABLE_WEIGHT);
        fastClickActions = data.getOptimizedActionList(ConfPaths.INVENTORY_FASTCLICK_ACTIONS, Permissions.INVENTORY_FASTCLICK);

        if (ServerVersion.compareMinecraftVersion("1.9") >= 0) {
            /** Note: Disable check should use
             *    NCPAPIProvider#getNoCheatPlusAPI()#getWorldDataManager()#overrideCheckActivation()
             *  to actually disable from all worlds. 
             *  Using worldData from config will only affect
             *  on world they are staying on join; And on other worlds still remain unchanged.
             */
            NCPAPIProvider.getNoCheatPlusAPI().getWorldDataManager().overrideCheckActivation(
                    CheckType.INVENTORY_FASTCONSUME, AlmostBoolean.NO, 
                    OverrideType.PERMANENT, true);
            NCPAPIProvider.getNoCheatPlusAPI().getWorldDataManager().overrideCheckActivation(
                    CheckType.INVENTORY_INSTANTBOW, AlmostBoolean.NO, 
                    OverrideType.PERMANENT, true);
        }
        
        fastConsumeDuration = (long) (1000.0 * data.getDouble(ConfPaths.INVENTORY_FASTCONSUME_DURATION));
        fastConsumeWhitelist = data.getBoolean(ConfPaths.INVENTORY_FASTCONSUME_WHITELIST);
        data.readMaterialFromList(ConfPaths.INVENTORY_FASTCONSUME_ITEMS, fastConsumeItems);
        fastConsumeActions = data.getOptimizedActionList(ConfPaths.INVENTORY_FASTCONSUME_ACTIONS, Permissions.INVENTORY_FASTCONSUME);

        gutenbergPageLimit = data.getInt(ConfPaths.INVENTORY_GUTENBERG_PAGELIMIT);
        gutenbergActions = data.getOptimizedActionList(ConfPaths.INVENTORY_GUTENBERG_ACTIONS, Permissions.INVENTORY_GUTENBERG);

        instantBowStrict = data.getBoolean(ConfPaths.INVENTORY_INSTANTBOW_STRICT);
        instantBowDelay = data.getInt(ConfPaths.INVENTORY_INSTANTBOW_DELAY);
        instantBowImprobableFeedOnly = data.getBoolean(ConfPaths.INVENTORY_INSTANTBOW_IMPROBABLE_FEEDONLY);
        instantBowImprobableWeight = (float) data.getDouble(ConfPaths.INVENTORY_INSTANTBOW_IMPROBABLE_WEIGHT);
        instantBowActions = data.getOptimizedActionList(ConfPaths.INVENTORY_INSTANTBOW_ACTIONS, Permissions.INVENTORY_INSTANTBOW);

        openClose = data.getBoolean(ConfPaths.INVENTORY_OPEN_CLOSE);
        openCancelOnMove = data.getBoolean(ConfPaths.INVENTORY_OPEN_CLOSE_ON_MOVE);
        openDisableCreative = data.getBoolean(ConfPaths.INVENTORY_OPEN_DISABLE_CREATIVE);
        openImprobableWeight = (float) data.getDouble(ConfPaths.INVENTORY_OPEN_IMPROBABLE_WEIGHT);

        hotFixFallingBlockEndPortalActive = data.getBoolean(ConfPaths.INVENTORY_HOTFIX_DUPE_FALLINGBLOCKENDPORTAL);
    }
}