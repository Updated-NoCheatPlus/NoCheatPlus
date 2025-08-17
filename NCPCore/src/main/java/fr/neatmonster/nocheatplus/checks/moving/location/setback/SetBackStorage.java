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
package fr.neatmonster.nocheatplus.checks.moving.location.setback;

import org.bukkit.Location;
import org.bukkit.World;

import fr.neatmonster.nocheatplus.components.location.IGetLocationWithLook;
import fr.neatmonster.nocheatplus.time.monotonic.Monotonic;
import fr.neatmonster.nocheatplus.utilities.math.TrigUtil;

/**
 * Represent a set back location storage, allowing for more or less efficient
 * keeping track of multiple set backs for use within one overall checking
 * context (e.g. player moving, or vehicle moving). For timing values, a counter
 * is used as time and a monotonic clock for msTime (static primary thread
 * clock: Monotonic.millis()).
 * 
 * @author asofold
 *
 */
public class SetBackStorage {

    // TODO: Support a hash for locations (LocUtil.hashCode).
    // TODO: Support for last-used set back (on retrieving a Location instance)?

    final SetBackEntry[] entries;
    final int defaultIndex;
    /** Count times setting a set back. */
    int time; // TODO: Switch to a counter/clock implementation, in order to allow the same provider for player + vehicle?

    /**
     * 
     * @param size
     * @param defaultIndex < 0 if not meant to be used.
     */
    public SetBackStorage(final int size, final int defaultIndex) {
        entries = new SetBackEntry[size];
        for (int i = 0; i < size; i++) {
            entries[i] = new SetBackEntry();
        }
        this.defaultIndex = defaultIndex;
    }

    /**
     * Maximum number of stored locations, disregarding validity.
     * 
     * @return
     */
    public int size() {
        return entries.length;
    }

    /**
     * Oldest by time value.
     * 
     * @return
     */
    public SetBackEntry getOldestValidEntry() {
        int oldestTime = Integer.MAX_VALUE;
        SetBackEntry oldestEntry = null;
        for (int i = 0; i < entries.length; i++) {
            final SetBackEntry entry = entries[i];
            if (entry.isValid() && entry.getTime() < oldestTime) {
                oldestTime = entry.getTime();
                oldestEntry = entry;
            }
        }
        return oldestEntry;
    }

    /**
     * Latest by time value.
     * 
     * @return
     */
    public SetBackEntry getLatestValidEntry() {
        int latestTime = 0;
        SetBackEntry latestEntry = null;
        for (int i = 0; i < entries.length; i++) {
            final SetBackEntry entry = entries[i];
            if (entry.isValid() && entry.getTime() > latestTime) {
                latestTime = entry.getTime();
                latestEntry = entry;
            }
        }
        return latestEntry;
    }

    /**
     * Retrieve the entry at a given index, only if valid. Set fallBack to true,
     * in order to fall back to the default entry, if a default index is set,
     * and if that entry is valid
     * 
     * @param index
     * @param fallBack
     *            If to allow falling back to the default entry.
     * @return
     */
    public SetBackEntry getValidEntry(final int index, final boolean fallBack) {
        if (entries[index].isValid()) {
            return entries[index];
        }
        if (fallBack && defaultIndex >= 0 && entries[defaultIndex].isValid()) {
            return entries[defaultIndex];
        }
        return null;
    }

    /**
     * Get the first entry that is valid and matches the location (position +
     * look, ignore world name).
     * 
     * @param location
     * @return
     */
    public SetBackEntry getFirstValidEntry(final Location location) {
        for (int i = 0; i < entries.length; i++) {
            final SetBackEntry entry = entries[i];
            if (entry.isValid() && TrigUtil.isSamePosAndLook(entry, location)) {
                return entry;
            }
        }
        return null;
    }

    public void invalidateAll() {
        for (int i = 0; i < entries.length; i++) {
            entries[i].setValid(false);
        }
    }

    /**
     * Hard-reset all entries to the given location. All will have the same time
     * value.
     * 
     * @param loc
     */
    public void resetAll(final Location loc) {
        time ++;
        for (int i = 0; i < entries.length; i++) {
            entries[i].set(loc, time, Monotonic.millis());
        }
    }

    /**
     * Hard-reset all entries to the given location. All will have the same time
     * value.
     * 
     * @param loc
     */
    public void resetAll(final IGetLocationWithLook loc) {
        time ++;
        for (int i = 0; i < entries.length; i++) {
            entries[i].set(loc, time, Monotonic.millis());
        }
    }

    /**
     * Invalidate all entries and reset the default entry to the given location.
     * Internal time is increased. If no default is set, resetAll is called.
     * 
     * @param loc
     */
    public void resetAllLazily(final Location loc) {
        if (defaultIndex < 0) {
            resetAll(loc);
        }
        else {
            invalidateAll();
            entries[defaultIndex].set(loc, ++time, Monotonic.millis());
        }
    }

    /**
     * Invalidate all entries and reset the default entry to the given location.
     * Internal time is increased. If no default is set, resetAll is called.
     * 
     * @param loc
     */
    public void resetAllLazily(final IGetLocationWithLook loc) {
        if (defaultIndex < 0) {
            resetAll(loc);
        }
        else {
            invalidateAll();
            entries[defaultIndex].set(loc, ++time, Monotonic.millis());
        }
    }

    public void resetByWorldName(final String worldName) {
        // TODO: Not needed for memory leaks, possibly tie resetAll to a global world?
        for (int i = 0; i < entries.length; i++) {
            if (worldName.equals(entries[i].getWorldName())) {
                entries[i].setValid(false);
            }
        }
    }

    /**
     * Test if any of the stored set back location is valid.
     * 
     * @return
     */
    public boolean isAnyEntryValid() {
        for (int i = 0; i < entries.length; i++) {
            if (entries[i].isValid()) {
                return true;
            }
        }
        return false;
    }

    /**
     * Test if the location stored at index is valid.
     * 
     * @param index
     * @return
     */
    public boolean isEntryValid(final int index) {
        return entries[index].isValid();
    }

    public boolean isDefaultEntryValid() {
        return defaultIndex >= 0 && entries[defaultIndex].isValid();
    }

    /**
     * Get the default set back entry, disregarding validity.
     * 
     * @return In case no default is set, null is returned. Otherwise the
     *         default entry is returned disregarding validity.
     */
    public SetBackEntry getDefaultEntry() {
        return defaultIndex < 0 ? null : entries[defaultIndex];
    }

    /**
     * Update the location for the default entry in-place. Time values are set
     * as well.
     * 
     * @param loc
     */
    public void setDefaultEntry(final Location loc) {
        getDefaultEntry().set(loc, ++time, Monotonic.millis());
    }

    /**
     * Update the location for the default entry in-place. Time values are set
     * as well.
     * 
     * @param loc
     */
    public void setDefaultEntry(final IGetLocationWithLook loc) {
        getDefaultEntry().set(loc, ++time, Monotonic.millis());
    }

    /**
     * Retrieve the default location as a Location instance, if valid. If not
     * valid, null is returned.
     * 
     * @param world
     * @return
     */
    public Location getValidDefaultLocation(final World world) {
        final SetBackEntry entry = getDefaultEntry();
        return entry.isValid() ? entry.getLocation(world) : null;
    }

    // TODO: Method to reset only newer entries to a certain location [a) pass a set back entry b) find it first, then compare age].

}
