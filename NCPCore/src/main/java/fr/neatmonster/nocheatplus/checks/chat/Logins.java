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
package fr.neatmonster.nocheatplus.checks.chat;

import java.util.HashMap;
import java.util.Map;

import org.bukkit.entity.Player;

import fr.neatmonster.nocheatplus.checks.Check;
import fr.neatmonster.nocheatplus.checks.CheckType;
import fr.neatmonster.nocheatplus.components.data.IData;
import fr.neatmonster.nocheatplus.components.registry.feature.IRemoveData;
import fr.neatmonster.nocheatplus.utilities.TickTask;
import fr.neatmonster.nocheatplus.utilities.ds.count.ActionFrequency;

public class Logins extends Check implements IRemoveData{
    
    /** Per world count (if set in the config, only "" is used). */
    private final Map<String, ActionFrequency> counts = new HashMap<String, ActionFrequency>();

	public Logins() {
		super(CheckType.CHAT_LOGINS);
	}
	
	private ActionFrequency getActionFrequency(String worldName, int buckets, long durBucket, boolean perWorldCount) {
	    if (!perWorldCount) worldName = "";
	    ActionFrequency freq = counts.get(worldName);
	    if (freq == null) freq = new ActionFrequency(buckets, durBucket);
	    counts.put(worldName, freq);
	    return freq;
	}

    public boolean check(final Player player, final ChatConfig cc, final ChatData data) {
        final long now = System.currentTimeMillis();
        // Skip if is too close to the startup time.
        if (now - TickTask.getTimeStart() < cc.loginsStartupDelay) return false;
        // Split into 6 buckets always.
        final long durBucket = 1000L * cc.loginsSeconds / 6;
        final ActionFrequency freq = getActionFrequency(player.getWorld().getName(), 6, durBucket, cc.loginsPerWorldCount);
        freq.update(now);
        final boolean cancel = freq.score(1f) > cc.loginsLimit; // TODO: >= ...  This will be 1 after the first login (!).
        if (!cancel) freq.add(1f);
        return cancel;
    }

	/**
	 * Called by ChatListener
	 */
	public void onReload() {
		counts.clear();
	}

	@Override
	public IData removeData(final String playerName) {
		// Ignore
		return null;
	}

	@Override
	public void removeAllData() {
		counts.clear();
	}

}
