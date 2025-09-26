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
package fr.neatmonster.nocheatplus.checks.combined;

import org.bukkit.entity.Player;

import fr.neatmonster.nocheatplus.actions.ParameterName;
import fr.neatmonster.nocheatplus.checks.Check;
import fr.neatmonster.nocheatplus.checks.CheckType;
import fr.neatmonster.nocheatplus.checks.ViolationData;
import fr.neatmonster.nocheatplus.components.registry.feature.IDisableListener;
import fr.neatmonster.nocheatplus.players.DataManager;
import fr.neatmonster.nocheatplus.players.IPlayerData;
import fr.neatmonster.nocheatplus.utilities.TickTask;

/**
 * Meta-check that aggregates suspicion weights from multiple checks over time,
 * and evaluates if the overall behavior is statistically unlikely
 * ("improbable") for a legitimate player.
 *
 * Intended for static access by other checks, which can feed weights into this
 * aggregator.
 *
 * Buckets are used internally to group suspicion scores into time slices.
 */
public class Improbable extends Check implements IDisableListener {

    private static Improbable instance = null;

    /**
     * Run the Improbable check for a given player with a new weight.
     *
     * @param player The player being checked.
     * @param weight The suspicion weight to add (severity of the detected behavior).
     * @param now    Current system time in ms.
     * @param tags   Optional tags for additional context.
     * @param pData 
     * @return true if the action should be cancelled, false otherwise.
     */
    public static final boolean check(final Player player, final float weight, final long now, final String tags, final IPlayerData pData) {
        return instance.checkImprobable(player, weight, now, tags, pData);
    }

    /**
     * Advance the internal time window without adding new suspicion.
     * Useful for keeping buckets aligned to the current time without
     * incrementing scores or triggering violations.
     *
     * @param now   Current system time in ms.
     * @param pData Player data container.
     */
    public static final void update(final long now, final IPlayerData pData) {
        pData.getGenericInstance(CombinedData.class).improbableCount.update(now);
    }

    /**
     * Advance the internal time window for a given player without
     * adding new suspicion.
     *
     * @param player The player.
     * @param now    Current system time in ms.
     */
    public static final void update(final Player player, final long now) {
        update(now, DataManager.getPlayerData(player));
    }

    /**
     * Add suspicion weight to the player's current bucket without a violation.
     *
     * @param player The player.
     * @param weight The suspicion weight to add.
     * @param now    Current system time in ms.
     * @param pData 
     */
    public static final void feed(final Player player, final float weight, final long now, final IPlayerData pData) {
        pData.getGenericInstance(CombinedData.class).improbableCount.add(now, weight);
    }

    /**
     * Add suspicion weight to the player's current bucket without a violation.
     *
     * @param player The player.
     * @param weight The suspicion weight to add.
     * @param now    Current system time in ms.
     */
    public static void feed(final Player player, final float weight, long now) {
        feed(player, weight, now, DataManager.getPlayerData(player));
    }

    ////////////////////////////////////
    // Instance methods.
    ///////////////////////////////////

    public Improbable() {
        super(CheckType.COMBINED_IMPROBABLE);
        instance = this;
    }

    /**
     * Perform the full Improbable check for a player by adding a new weight
     * and evaluating both short-term and long-term suspicion levels.
     *
     * @param player The player being checked.
     * @param weight The suspicion weight to add.
     * @param now    Current system time in ms.
     * @param tags   Optional tags with context about the source of suspicion.
     * @param pData
     * @return true if the suspicious action should be cancelled, false otherwise.
     */
    private boolean checkImprobable(final Player player, final float weight, final long now, final String tags, final IPlayerData pData) {
        if (!pData.isCheckActive(type, player)) {
            return false;
        }
        final CombinedData data = pData.getGenericInstance(CombinedData.class);
        final CombinedConfig cc = pData.getGenericInstance(CombinedConfig.class);
        data.improbableCount.add(now, weight);
        // Score of the first bucket (covers ~3 seconds).
        final float shortTerm = data.improbableCount.bucketScore(0);
        double violation = 0.0;
        boolean violated = false;
        if (shortTerm * 0.8f > cc.improbableLevel / 20.0) {
            // Lag adjustment factor for the short-term window (~3 seconds).
            final float lagFactor = pData.getCurrentWorldData().shouldAdjustToLag(type) ? TickTask.getLag(data.improbableCount.bucketDuration(), true) : 1f;
            // Re-check with lag adaptation.
            if (shortTerm / lagFactor > cc.improbableLevel / 20.0) {
                violation += shortTerm * 2D / lagFactor;
                violated = true;
            }
        }
        // Total score across all buckets (~1 minute span).
        final double fullTerm = data.improbableCount.score(1.0f);
        if (fullTerm > cc.improbableLevel) {
            // Lag adjustment factor for the full window (~1 minute).
            final float lagFactor = pData.getCurrentWorldData().shouldAdjustToLag(type) ? TickTask.getLag(data.improbableCount.bucketDuration() * data.improbableCount.numberOfBuckets(), true) : 1f;
            // Re-check with lag adaptation.
            if (fullTerm / lagFactor > cc.improbableLevel) {
                violation += fullTerm / lagFactor;
                violated = true;
            }
        }
        boolean cancel = false;
        if (violated) {
            // Execute actions
            data.improbableVL += violation / 10.0;
            final ViolationData vd = new ViolationData(this, player, data.improbableVL, violation, cc.improbableActions);
            if (tags != null && !tags.isEmpty()) vd.setParameter(ParameterName.TAGS, tags);
            cancel = executeActions(vd).willCancel();
        }
        else data.improbableVL *= 0.8;
        return cancel;
    }

    @Override
    public void onDisable() {
        instance = null;
    }
}
