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
package fr.neatmonster.nocheatplus.compat.registry;

public class BukkitAPIAccessFactory {
    private static final IBukkitAccess access = init();
    
    public static IBukkitAccess getBukkitAccess() {
        return access;
    }

    private static IBukkitAccess init() {
        return getFirstAvailable(new String[]{
                "fr.neatmonster.nocheatplus.compat.bukkit.BridgeBukkitAPI",
                "fr.neatmonster.nocheatplus.compat.bukkit.BridgeBukkitAPIModern"
        }, IBukkitAccess.class);
    }
    
    @SuppressWarnings({ "deprecation", "unchecked" })
    public static <T> T getFirstAvailable(String[] classNames, Class<T> registerFor) {
        T res = null;
        for (String name : classNames) {
            try {
                res = (T) Class.forName(name).newInstance();
                if (res != null) {
                    return res;
                }
            }
            catch (Throwable t) {
                // Skip.
            }
        }
        return null;
    }
}