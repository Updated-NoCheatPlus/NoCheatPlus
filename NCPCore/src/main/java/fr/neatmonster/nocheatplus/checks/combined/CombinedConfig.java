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

import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.event.entity.EntityDamageEvent.DamageCause;

import fr.neatmonster.nocheatplus.actions.ActionList;
import fr.neatmonster.nocheatplus.checks.access.ACheckConfig;
import fr.neatmonster.nocheatplus.config.ConfPaths;
import fr.neatmonster.nocheatplus.config.ConfigFile;
import fr.neatmonster.nocheatplus.logging.StaticLog;
import fr.neatmonster.nocheatplus.permissions.Permissions;
import fr.neatmonster.nocheatplus.utilities.StringUtil;
import fr.neatmonster.nocheatplus.worlds.IWorldData;

public class CombinedConfig extends ACheckConfig {

    // Ender pearl
    public final boolean enderPearlCheck;
    public final boolean enderPearlPreventClickBlock;

    // Improbable check
    /** Do mind that this flag is not used by all components. */
    public final float          improbableLevel;
    public final ActionList     improbableActions;

    // Invulnerable management.
    public final boolean                    invulnerableCheck;
    public final int                        invulnerableInitialTicksJoin;
    public final Set<DamageCause>           invulnerableIgnore = new HashSet<DamageCause>();
    public final Map<DamageCause, Integer>  invulnerableModifiers = new HashMap<DamageCause, Integer>();
    public final int                        invulnerableModifierDefault;
    public final boolean                    invulnerableTriggerAlways;
    public final boolean                    invulnerableTriggerFallDistance;
    
    // Last yaw tracking 
    public final float      yawRate;
    public final boolean    yawRateImprobableFeedOnly;
    public final float      yawRateImprobableWeight;
    public final float      yawRatePenaltyFactor;
    public final int        yawRatePenaltyMin;
    public final int        yawRatePenaltyMax;

    public CombinedConfig(final IWorldData worldData) {
        super(worldData);
        final ConfigFile config = worldData.getRawConfiguration();

        enderPearlCheck = config.getBoolean(ConfPaths.COMBINED_ENDERPEARL_CHECK);
        enderPearlPreventClickBlock = config.getBoolean(ConfPaths.COMBINED_ENDERPEARL_PREVENTCLICKBLOCK);

        improbableLevel = (float) config.getDouble(ConfPaths.COMBINED_IMPROBABLE_LEVEL);
        improbableActions = config.getOptimizedActionList(ConfPaths.COMBINED_IMPROBABLE_ACTIONS, Permissions.COMBINED_IMPROBABLE);

        invulnerableCheck = config.getBoolean(ConfPaths.COMBINED_INVULNERABLE_CHECK);
        invulnerableInitialTicksJoin = config.getInt(ConfPaths.COMBINED_INVULNERABLE_INITIALTICKS_JOIN);
        boolean error = false;
        // Read ignored causes.
        for (final String input : config.getStringList(ConfPaths.COMBINED_INVULNERABLE_IGNORE)){
            final String normInput = input.trim().toUpperCase();
            try{
                invulnerableIgnore.add(DamageCause.valueOf(normInput.replace(' ', '_').replace('-', '_')));
            }
            catch (final Exception e){
                error = true;
                StaticLog.logWarning("Bad damage cause (combined.invulnerable.ignore): " + input);
            }
        }
        // Read modifiers for causes.
        Integer defaultMod = 0;
        final ConfigurationSection sec = config.getConfigurationSection(ConfPaths.COMBINED_INVULNERABLE_MODIFIERS);
        for (final String input : sec.getKeys(false)){
            final int modifier = sec.getInt(input, 0);
            final String normInput = input.trim().toUpperCase();
            if (normInput.equals("ALL")){
                defaultMod = modifier;
                continue;
            }
            try{
                invulnerableModifiers.put(DamageCause.valueOf(normInput.replace(' ', '_').replace('-', '_')), modifier);
            }
            catch (final Exception e){
                error = true;
                StaticLog.logWarning("Bad damage cause (combined.invulnerable.modifiers): " + input);
            }
        }
        invulnerableModifierDefault = defaultMod;
        if (error) StaticLog.logInfo("Damage causes can be: " + StringUtil.join(Arrays.asList(DamageCause.values()), ", "));
        invulnerableTriggerAlways = config.getBoolean(ConfPaths.COMBINED_INVULNERABLE_TRIGGERS_ALWAYS);
        invulnerableTriggerFallDistance = config.getBoolean(ConfPaths.COMBINED_INVULNERABLE_TRIGGERS_FALLDISTANCE);
        
        yawRate = config.getInt(ConfPaths.COMBINED_YAWRATE_RATE);
        yawRateImprobableFeedOnly = config.getBoolean(ConfPaths.COMBINED_YAWRATE_IMPROBABLE_FEEDONLY);
        yawRateImprobableWeight = (float) config.getDouble(ConfPaths.COMBINED_YAWRATE_IMPROBABLE_WEIGHT);
        yawRatePenaltyFactor = (float) config.getDouble(ConfPaths.COMBINED_YAWRATE_PENALTY_FACTOR);
        yawRatePenaltyMin = config.getInt(ConfPaths.COMBINED_YAWRATE_PENALTY_MIN);
        yawRatePenaltyMax = config.getInt(ConfPaths.COMBINED_YAWRATE_PENALTY_MAX);
    }

}
