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

import java.util.List;

import fr.neatmonster.nocheatplus.checks.chat.analysis.MessageLetterCount;
import fr.neatmonster.nocheatplus.config.ConfigFile;
import fr.neatmonster.nocheatplus.utilities.ds.prefixtree.SimpleTimedCharPrefixTree;
import fr.neatmonster.nocheatplus.utilities.ds.prefixtree.SimpleTimedCharPrefixTree.SimpleTimedCharLookupEntry;

public class WordPrefixes extends DigestedWords {
    
    public static class WordPrefixesSettings extends DigestedWordsSettings {
        public int maxAdd = 1000;
        public long durExpire = 30000;
        /**
         * split and compress by default.
         */
        public WordPrefixesSettings(){
            split = true;
            compress = true;
        }
        public WordPrefixesSettings applyConfig(ConfigFile config, String prefix) {
            super.applyConfig(config, prefix);
            this.maxAdd = config.getInt(prefix + "size", this.maxAdd);
            this.durExpire = (long) (config.getDouble(prefix + "time", (float) this.durExpire / 1000f) * 1000f);
            return this;
        }
    }

    protected final SimpleTimedCharPrefixTree tree = new SimpleTimedCharPrefixTree(true);
    
    protected final int maxAdd;
    
    protected int added = 0;

    protected final long durExpire;
    
    protected long lastAdd = System.currentTimeMillis();
    
    public WordPrefixes(String name, WordPrefixesSettings settings) {
        super(name, settings);
        this.durExpire = settings.durExpire;
        this.maxAdd = settings.maxAdd;
    }
    
    @Override
    public void start(final MessageLetterCount message) {
        // This allows adding up to maximum messge length more characters, but also allows to set size of nodes exactly.
        // TODO: Some better method  than blunt clear (extra LinkedHashSet/LRU?).
        if (added > maxAdd || System.currentTimeMillis() - lastAdd > durExpire){
            tree.clear();
            added = 0;
        }
    }
    
    @Override
    public void clear() {
        super.clear();
        tree.clear();
        added = 0;
    }

    protected float getScore(final List<Character> chars, final long ts) {
        lastAdd = ts;
        final int len = chars.size();
        final SimpleTimedCharLookupEntry entry = tree.lookup(chars, true);
        final int depth = entry.depth;
        float score = 0f;
        for (int i = 0; i < depth ; i++){
            final long age = ts - entry.timeInsertion[i];
            if (age < durExpire)
                score += 1f / (float) (depth - i) * (float) (durExpire - age) / (float) durExpire;
        }
        if (depth == len){
            score += 0.2;
            if (entry.insertion.isEnd) score += 0.2;
        }
        if (len != depth) added += len - depth;
        return score;
    }
}
