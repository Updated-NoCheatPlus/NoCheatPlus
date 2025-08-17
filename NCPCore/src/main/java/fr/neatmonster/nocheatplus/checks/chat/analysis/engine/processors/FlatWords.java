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
package fr.neatmonster.nocheatplus.checks.chat.analysis.engine.processors;

import java.util.LinkedHashMap;
import java.util.List;

import fr.neatmonster.nocheatplus.checks.chat.analysis.MessageLetterCount;
import fr.neatmonster.nocheatplus.config.ConfigFile;
import fr.neatmonster.nocheatplus.utilities.ds.count.ActionFrequency;

/**
 * Words mapped to ActionFrequency queues. 
 * @author asofold
 *
 */
public class FlatWords extends DigestedWords{
    
    public static class FlatWordsSettings extends DigestedWordsSettings {
        public int maxSize = 1000;
        public long durBucket = 1500;
        public int nBuckets = 4;
        public float factor = 0.9f;
        /**
         * split by default.
         */
        public FlatWordsSettings() {
            this.split = true;
        }
        public FlatWordsSettings applyConfig(ConfigFile config, String prefix) {
            super.applyConfig(config, prefix);
            this.maxSize = config.getInt(prefix + "size", this.maxSize);
            this.nBuckets = config.getInt(prefix + "buckets", this.nBuckets);
            // In seconds.
            this.durBucket = (long) (config.getDouble(prefix + "time", (float) this.durBucket / 1000f) * 1000f);
            this.factor = (float) config.getDouble(prefix + "factor", this.factor);
            return this;
        }
    }
    
    protected final int maxSize;
    protected final LinkedHashMap<String, ActionFrequency> entries;
    protected final long durBucket;
    protected final int nBuckets;
    protected final float factor;
    protected long lastAdd = System.currentTimeMillis();
    
    public FlatWords(String name, FlatWordsSettings settings) {
        super(name, settings);
        this.maxSize = settings.maxSize;
        entries = new LinkedHashMap<String, ActionFrequency>(maxSize);
        this.nBuckets = settings.nBuckets;
        this.durBucket = settings.durBucket;
        this.factor = settings.factor;
    }
    
    @Override
    public void start(final MessageLetterCount message) {
        if (System.currentTimeMillis() - lastAdd > nBuckets * durBucket) {
            entries.clear();
        }
        else if (entries.size() + message.words.length > maxSize) {
            releaseMap(entries, Math.max(message.words.length, maxSize / 10));
        }
    }

    @Override
    public void clear() {
        super.clear();
        entries.clear();
    }

    @Override
    protected float getScore(List<Character> chars, long ts) {
        lastAdd = ts;
        final char[] a = DigestedWords.toArray(chars);
        final String key = new String(a);
        ActionFrequency freq = entries.get(key);
        if (freq == null){
            freq = new ActionFrequency(nBuckets, durBucket);
            entries.put(key, freq);
            return 0.0f;
        }
        freq.update(ts);
        float score = Math.min(1.0f, freq.score(factor));
        freq.add(ts, 1.0f);
        return score;
    }
}
