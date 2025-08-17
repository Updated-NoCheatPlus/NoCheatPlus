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
package fr.neatmonster.nocheatplus.checks.chat.analysis.engine;

import java.util.Collection;

import fr.neatmonster.nocheatplus.checks.chat.ChatConfig;
import fr.neatmonster.nocheatplus.checks.chat.analysis.engine.processors.WordProcessor;
import fr.neatmonster.nocheatplus.utilities.ds.map.ManagedMap;

/**
 * Store EnginePlayerData. Expire data on get(String, Chatonfig).
 * @author asofold
 *
 */
public class EnginePlayerDataMap extends ManagedMap<String, EnginePlayerData> {

    protected long durExpire;
    
    /** Timestamp of last time get was called.*/
    protected long lastAccess = System.currentTimeMillis();
    
    /** Timestamp of last time an entry was removed. */
    protected long lastExpired = lastAccess;

    public EnginePlayerDataMap(long durExpire, int defaultCapacity, float loadFactor) {
        super(defaultCapacity, loadFactor);
        this.durExpire = durExpire;
    }

    /**
     * 
     * @param key
     * @param cc
     * @return
     */
    public EnginePlayerData get(final String key, final ChatConfig cc) {
        EnginePlayerData data = super.get(key);
        if (data == null){
            data = new EnginePlayerData(cc);
            put(key, data);
        }
        final long ts = System.currentTimeMillis();
        if (ts < lastExpired){
            lastExpired = ts;
            // might clear map or update all entries.
        }
        else if (ts - lastExpired > durExpire) expire(ts - durExpire);
        lastAccess = ts;
        return data;
    }
    
    public void clear() { 
        final long time = System.currentTimeMillis();
        for (final ValueWrap wrap : map.values()){
            for (final WordProcessor processor : wrap.value.processors) {
                processor.clear();
            }
        }
        super.clear();
        lastAccess = lastExpired = time;
    }

    /* (non-Javadoc)
     * @see fr.neatmonster.nocheatplus.utilities.ds.ManagedMap#expire(long)
     */
    @Override
    public Collection<String> expire(long ts) {
        final Collection<String> rem = super.expire(ts);
        if (!rem.isEmpty()) lastExpired = System.currentTimeMillis();
        return rem;
    }
}