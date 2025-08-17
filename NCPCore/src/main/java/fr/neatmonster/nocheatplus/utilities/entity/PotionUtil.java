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
package fr.neatmonster.nocheatplus.utilities.entity;

import java.util.Collection;

import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;

// TODO: Auto-generated Javadoc
/**
 * Could not help it.
 * @author mc_dev
 *
 */
public class PotionUtil {

    /**
     * Get amplifier for a potion effect.
     *
     * @param player
     *            the player
     * @param type
     *            the type
     * @return Double.NEGATIVE_INFINITY if not present, otherwise the maximal
     *         amplifier.
     */
    public static final double getPotionEffectAmplifier(final Player player, final PotionEffectType type) {
        if (!player.hasPotionEffect(type)) {
            return Double.NEGATIVE_INFINITY; // TODO: Might not win anything.
        }
        final Collection<PotionEffect> effects = player.getActivePotionEffects();
        double max = Double.NEGATIVE_INFINITY;
        for (final PotionEffect effect : effects){
            if (effect.getType().equals(type)){
                max = Math.max(max, effect.getAmplifier());
            }
        }
        return max;
    }
    
    /**
     * Get amplifier for a potion effect.
     *
     * @param entity
     *            the LivingEntity
     * @param type
     *            the type
     * @return Double.NEGATIVE_INFINITY if not present, otherwise the maximal
     *         amplifier.
     */
    public static final double getPotionEffectAmplifier(final LivingEntity entity, final PotionEffectType type) {
        if (!entity.hasPotionEffect(type)) {
            return Double.NEGATIVE_INFINITY; // TODO: Might not win anything.
        }
        final Collection<PotionEffect> effects = entity.getActivePotionEffects();
        double max = Double.NEGATIVE_INFINITY;
        for (final PotionEffect effect : effects){
            if (effect.getType().equals(type)){
                max = Math.max(max, effect.getAmplifier());
            }
        }
        return max;
    }

}
