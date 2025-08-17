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
package fr.neatmonster.nocheatplus.checks.blockplace;

import java.util.HashSet;
import java.util.Set;

import org.bukkit.Material;

import fr.neatmonster.nocheatplus.actions.ActionList;
import fr.neatmonster.nocheatplus.checks.access.ACheckConfig;
import fr.neatmonster.nocheatplus.config.ConfPaths;
import fr.neatmonster.nocheatplus.config.ConfigFile;
import fr.neatmonster.nocheatplus.permissions.Permissions;
import fr.neatmonster.nocheatplus.worlds.IWorldData;

/**
 * Configurations specific for the block place checks. Every world gets one of these assigned to it, or if a world
 * doesn't get it's own, it will use the "global" version.
 */
public class BlockPlaceConfig extends ACheckConfig {

    public final ActionList againstActions;

    public final boolean autoSignSkipEmpty;
    public final ActionList autoSignActions;

    public final ActionList directionActions;

    public final int fastPlaceLimit;
    public final int fastPlaceShortTermTicks;
    public final int fastPlaceShortTermLimit;
    public final boolean fastPlaceImprobableFeedOnly;
    public final float fastPlaceImprobableWeight;
    public final ActionList fastPlaceActions;

    public final Set<Material> noSwingExceptions = new HashSet<Material>();
    public final ActionList noSwingActions;

    public final ActionList reachActions;

    public final ActionList scaffoldActions;
    public final boolean scaffoldAngle;
    public final boolean scaffoldTime;
    public final int scaffoldTimeAvg;
    public final boolean scaffoldSprint;
    public final boolean scaffoldRotate;
    public final int scaffoldRotateDiff;
    public final boolean scaffoldToolSwitch;
    public final boolean scaffoldImprobableFeedOnly;
    public final float   scaffoldImprobableWeight;

    public final long speedInterval;
    public final boolean speedImprobableFeedOnly;
    public final float speedImprobableWeight;
    public final ActionList speedActions;

    /** General activation flag. */
    public final boolean boatsOnWaterOnly;

    /**
     * Instantiates a new block place configuration.
     * 
     * @param config
     * 
     */
    public BlockPlaceConfig(final IWorldData worldData) {
        super(worldData);
        final ConfigFile config = worldData.getRawConfiguration();

        againstActions = config.getOptimizedActionList(ConfPaths.BLOCKPLACE_AGAINST_ACTIONS, Permissions.BLOCKPLACE_AGAINST);

        autoSignSkipEmpty = config.getBoolean(ConfPaths.BLOCKPLACE_AUTOSIGN_SKIPEMPTY);
        autoSignActions = config.getOptimizedActionList(ConfPaths.BLOCKPLACE_AUTOSIGN_ACTIONS, Permissions.BLOCKPLACE_AUTOSIGN);

        directionActions = config.getOptimizedActionList(ConfPaths.BLOCKPLACE_DIRECTION_ACTIONS, Permissions.BLOCKPLACE_DIRECTION);

        fastPlaceLimit = config.getInt(ConfPaths.BLOCKPLACE_FASTPLACE_LIMIT);
        fastPlaceShortTermTicks = config.getInt(ConfPaths.BLOCKPLACE_FASTPLACE_SHORTTERM_TICKS);
        fastPlaceShortTermLimit = config.getInt(ConfPaths.BLOCKPLACE_FASTPLACE_SHORTTERM_LIMIT);
        fastPlaceImprobableFeedOnly = config.getBoolean(ConfPaths.BLOCKPLACE_FASTPLACE_IMPROBABLE_FEEDONLY);
        fastPlaceImprobableWeight = (float) config.getDouble(ConfPaths.BLOCKPLACE_FASTPLACE_IMPROBABLE_WEIGHT);
        fastPlaceActions = config.getOptimizedActionList(ConfPaths.BLOCKPLACE_FASTPLACE_ACTIONS, Permissions.BLOCKPLACE_FASTPLACE);

        config.readMaterialFromList(ConfPaths.BLOCKPLACE_NOSWING_EXCEPTIONS, noSwingExceptions);
        noSwingActions = config.getOptimizedActionList(ConfPaths.BLOCKPLACE_NOSWING_ACTIONS, Permissions.BLOCKPLACE_NOSWING);

        reachActions = config.getOptimizedActionList(ConfPaths.BLOCKPLACE_REACH_ACTIONS, Permissions.BLOCKPLACE_REACH);

        scaffoldAngle = config.getBoolean(ConfPaths.BLOCKPLACE_SCAFFOLD_ANGLE);
        scaffoldTime = config.getBoolean(ConfPaths.BLOCKPLACE_SCAFFOLD_TIME_ACTIVE);
        scaffoldTimeAvg = config.getInt(ConfPaths.BLOCKPLACE_SCAFFOLD_TIME_AVG);
        scaffoldSprint = config.getBoolean(ConfPaths.BLOCKPLACE_SCAFFOLD_SPRINT);
        scaffoldRotate = config.getBoolean(ConfPaths.BLOCKPLACE_SCAFFOLD_ROTATE_ACTIVE);
        scaffoldRotateDiff = config.getInt(ConfPaths.BLOCKPLACE_SCAFFOLD_ROTATE_DIFFERENCE);
        scaffoldToolSwitch = config.getBoolean(ConfPaths.BLOCKPLACE_SCAFFOLD_TOOLSWITCH);
        scaffoldImprobableFeedOnly = config.getBoolean(ConfPaths.BLOCKPLACE_SCAFFOLD_IMPROBABLE_FEEDONLY);
        scaffoldImprobableWeight = (float) config.getDouble(ConfPaths.BLOCKPLACE_SCAFFOLD_IMPROBABLE_WEIGHT);
        scaffoldActions = config.getOptimizedActionList(ConfPaths.BLOCKPLACE_SCAFFOLD_ACTIONS, Permissions.BLOCKPLACE_SCAFFOLD);

        speedInterval = config.getLong(ConfPaths.BLOCKPLACE_SPEED_INTERVAL);
        speedImprobableFeedOnly = config.getBoolean(ConfPaths.BLOCKPLACE_SPEED_IMPROBABLE_FEEDONLY);
        speedImprobableWeight = (float) config.getDouble(ConfPaths.BLOCKPLACE_SPEED_IMPROBABLE_WEIGHT);
        speedActions = config.getOptimizedActionList(ConfPaths.BLOCKPLACE_SPEED_ACTIONS, Permissions.BLOCKPLACE_SPEED);

        boatsOnWaterOnly = config.getBoolean(ConfPaths.BLOCKPLACE_BOATSONWATERONLY);
        /*
         * TODO: Placing boats has been possible since 1.4.5-R1.0. Behavior
         * differs, e.g. 1.12 only places boats when clicking the top of a
         * block, while in 1.7.10 the boat is placed on top of a block you click
         * the side of. If exceptions are to be implemented at all, they must
         * contain protection against abuse.
         */
    }

}