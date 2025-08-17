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
package fr.neatmonster.nocheatplus.command.testing.stopwatch;

import org.bukkit.ChatColor;
import org.bukkit.entity.Player;

import fr.neatmonster.nocheatplus.utilities.Misc;

public abstract class StopWatch {	

    public static final String TAG = ChatColor.GRAY +""+ ChatColor.BOLD + "[" + ChatColor.RED + "NC+" + ChatColor.GRAY +""+ ChatColor.BOLD + "] " + ChatColor.GRAY;

    public final long start;

    protected boolean finished = false;

    private long duration = 0;

    public final Player player;

    /** Added as clock description, mind to add spaces/brackets. */
    protected String clockDetails = "";

    public StopWatch(Player player) {
        this.start = System.currentTimeMillis(); // TODO: Monotonic ?
        this.player = player;
    }

    public String getClockDetails() {
        return clockDetails;
    }

    /**
     * 
     * @return Duration in milliseconds.
     * @throws RuntimeException if time ran backwards.
     */
    public long getTime() {
        if (isFinished()) {
            return duration;
        }
        final long time = System.currentTimeMillis();
        if (time < start) {
            throw new RuntimeException("Time ran backwards.");
        }
        return time - start;
    }

    /**
     * 
     * @return Duration in milliseconds.
     * @throws RuntimeException if time ran backwards.
     */
    public long stop() {
        if (!finished) {
            duration = getTime();
            finished = true;
        }
        return duration;
    }

    public boolean isFinished() {
        return finished;
    }

    /**
     * Override for more functionality.
     * @throws RuntimeException if time ran backwards.
     */
    public void sendStatus() {
        final long duration = getTime();
        final long tenths = (duration % 1000) / 100;
        player.sendMessage(TAG + "Stopwatch " + ChatColor.GOLD + clockDetails + ChatColor.GRAY + ": " + (finished ? ChatColor.RED : ChatColor.GREEN) + Misc.millisToShortDHMS(duration) + "." + tenths);
    }

    /**
     * Check if and indicate if the conditions for stopping have been reached + do stop the clock if reached. isFinished should be checked before calling this.
     * @return If stopped.
     */
    public abstract boolean checkStop();

    /**
     * Does checkStop need to be called every tick?
     * @return
     */
    public abstract boolean needsTick();

}
