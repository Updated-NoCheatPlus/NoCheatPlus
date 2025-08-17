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
package fr.neatmonster.nocheatplus.compat.bukkit;

import org.bukkit.potion.PotionEffectType;

import fr.neatmonster.nocheatplus.utilities.StringUtil;

public class BridgePotionEffect {
    
    /**
     * Parses a potion effect type from its name.
     *
     * @param name The name of the potion effect.
     * @return The corresponding PotionEffectType, or null if not found.
     */
    @SuppressWarnings("deprecation")
    private static final PotionEffectType parsePotionEffect(final String name) {
        try {
            return PotionEffectType.getByName(name);
        } catch (Exception e) {
            return null;
        }
    }
    
    /**
     * Retrieves the first valid PotionEffectType from a list of names.
     *
     * @param names The names to search for.
     * @return The first matching PotionEffectType, or null if none are found.
     */
    public static PotionEffectType getFirst(String... names) {
        for (String name : names) {
            final PotionEffectType type = parsePotionEffect(name);
            if (type != null) {
                return type;
            }
        }
        return null;
    }
    
    /**
     * Retrieves the first valid PotionEffectType from a list of names, throwing an exception if none are found.
     *
     * @param names The names to search for.
     * @return The first matching PotionEffectType.
     * @throws NullPointerException if no valid PotionEffectType is found.
     */
    public static PotionEffectType getFirstNotNull(String... names) {
        final PotionEffectType type = getFirst(names);
        if (type == null) {
            throw new NullPointerException("PotionEffect not present: " + StringUtil.join(names, ", "));
        }
        else {
            return type;
        }
    }

    public final static PotionEffectType SLOWNESS = getFirstNotNull("SLOWNESS", "SLOW");
    public final static PotionEffectType HASTE = getFirstNotNull("HASTE", "FAST_DIGGING");
    public final static PotionEffectType MINING_FATIGUE = getFirstNotNull("MINING_FATIGUE", "SLOW_DIGGING");
    public final static PotionEffectType JUMP_BOOST = getFirstNotNull("JUMP_BOOST", "JUMP");
    public final static PotionEffectType WEAVING = getFirst("WEAVING");
}
