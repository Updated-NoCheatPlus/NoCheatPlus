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
package fr.neatmonster.nocheatplus.checks.blockbreak;

import fr.neatmonster.nocheatplus.actions.ActionList;
import fr.neatmonster.nocheatplus.checks.access.ACheckConfig;
import fr.neatmonster.nocheatplus.config.ConfPaths;
import fr.neatmonster.nocheatplus.config.ConfigFile;
import fr.neatmonster.nocheatplus.permissions.Permissions;
import fr.neatmonster.nocheatplus.worlds.IWorldData;

/**
 * Configurations specific for the block break checks. Every world gets one of these assigned to it, or if a world
 * doesn't get it's own, it will use the "global" version.
 */
public class BlockBreakConfig extends ACheckConfig {

    public final ActionList directionActions;

    public final boolean fastBreakStrict;
    public final int fastBreakBuckets;
    public final long fastBreakBucketDur;
    public final float fastBreakBucketFactor;
    public final long fastBreakGrace;
    public final long fastBreakDelay;	
    public final int fastBreakModSurvival;
    public final ActionList fastBreakActions;

    public final int frequencyBuckets;
    public final long frequencyBucketDur;
    public final float frequencyBucketFactor;  
    public final int frequencyIntervalCreative;
    public final int frequencyIntervalSurvival;
    public final int frequencyShortTermLimit;
    public final int frequencyShortTermTicks;
    public final ActionList frequencyActions;

    public final ActionList noSwingActions;

    public final ActionList reachActions;

    public final float wrongBLockLevel;
    public final boolean wrongBlockImprobableFeedOnly;
    public final float wrongBlockImprobableWeight;
    public final ActionList wrongBlockActions;

    /**
     * Instantiates a new block break configuration.
     * 
     * @param data
     *            the data
     */
    public BlockBreakConfig(final IWorldData worldData) {
        super(worldData);
        final ConfigFile config = worldData.getRawConfiguration();
        directionActions = config.getOptimizedActionList(ConfPaths.BLOCKBREAK_DIRECTION_ACTIONS, Permissions.BLOCKBREAK_DIRECTION);

        // Fastbreak.
        fastBreakStrict = config.getBoolean(ConfPaths.BLOCKBREAK_FASTBREAK_STRICT);
        fastBreakDelay = config.getLong(ConfPaths.BLOCKBREAK_FASTBREAK_DELAY);
        fastBreakGrace = config.getLong(ConfPaths.BLOCKBREAK_FASTBREAK_BUCKETS_CONTENTION, config.getLong(ConfPaths.BLOCKBREAK_FASTBREAK_GRACE, 2000));
        fastBreakBucketDur = config.getInt(ConfPaths.BLOCKBREAK_FASTBREAK_BUCKETS_DUR, 4000);
        fastBreakBucketFactor = (float) config.getDouble(ConfPaths.BLOCKBREAK_FASTBREAK_BUCKETS_FACTOR, 0.99);
        fastBreakBuckets = config.getInt(ConfPaths.BLOCKBREAK_FASTBREAK_BUCKETS_N, 30);
        fastBreakModSurvival = config.getInt(ConfPaths.BLOCKBREAK_FASTBREAK_MOD_SURVIVAL);
        // Fastbreak  actions, shared.
        fastBreakActions = config.getOptimizedActionList(ConfPaths.BLOCKBREAK_FASTBREAK_ACTIONS, Permissions.BLOCKBREAK_FASTBREAK);

        frequencyBuckets = config.getInt(ConfPaths.BLOCKBREAK_FREQUENCY_BUCKETS_N, 2);
        frequencyBucketDur = config.getLong(ConfPaths.BLOCKBREAK_FREQUENCY_BUCKETS_DUR, 1000);
        frequencyBucketFactor = (float) config.getDouble(ConfPaths.BLOCKBREAK_FREQUENCY_BUCKETS_FACTOR, 1f);
        frequencyIntervalCreative = config.getInt(ConfPaths.BLOCKBREAK_FREQUENCY_MOD_CREATIVE);
        frequencyIntervalSurvival = config.getInt(ConfPaths.BLOCKBREAK_FREQUENCY_MOD_SURVIVAL);
        frequencyShortTermLimit = config.getInt(ConfPaths.BLOCKBREAK_FREQUENCY_SHORTTERM_LIMIT);
        frequencyShortTermTicks = config.getInt(ConfPaths.BLOCKBREAK_FREQUENCY_SHORTTERM_TICKS);
        frequencyActions = config.getOptimizedActionList(ConfPaths.BLOCKBREAK_FREQUENCY_ACTIONS, Permissions.BLOCKBREAK_FREQUENCY);

        noSwingActions = config.getOptimizedActionList(ConfPaths.BLOCKBREAK_NOSWING_ACTIONS, Permissions.BLOCKBREAK_NOSWING);

        reachActions = config.getOptimizedActionList(ConfPaths.BLOCKBREAK_REACH_ACTIONS, Permissions.BLOCKBREAK_REACH);
        wrongBlockImprobableFeedOnly = config.getBoolean(ConfPaths.BLOCKBREAK_WRONGBLOCK_IMPROBABLE_FEEDONLY);
        wrongBlockImprobableWeight = (float) config.getDouble(ConfPaths.BLOCKBREAK_WRONGBLOCK_IMPROBABLE_WEIGHT);

        wrongBLockLevel = config.getInt(ConfPaths.BLOCKBREAK_WRONGBLOCK_LEVEL);
        wrongBlockActions = config.getOptimizedActionList(ConfPaths.BLOCKBREAK_WRONGBLOCK_ACTIONS, Permissions.BLOCKBREAK_WRONGBLOCK);
    }
}