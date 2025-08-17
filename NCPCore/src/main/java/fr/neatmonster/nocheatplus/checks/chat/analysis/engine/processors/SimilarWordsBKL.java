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
import fr.neatmonster.nocheatplus.utilities.ds.bktree.SimpleTimedBKLevenshtein;
import fr.neatmonster.nocheatplus.utilities.ds.bktree.SimpleTimedBKLevenshtein.STBKLResult;
import fr.neatmonster.nocheatplus.utilities.ds.bktree.TimedBKLevenshtein.SimpleTimedLevenNode;

public class SimilarWordsBKL extends DigestedWords {
    
    public static class SimilarWordsBKLSettings extends DigestedWordsSettings {
        public int maxSize = 1000;
        public int range = 2;
        public long durExpire = 30000;
        public int maxSeek = 0;
        /**
         * split + compress by default.
         */
        public SimilarWordsBKLSettings() {
            split = true;
            compress = true;
        }
        public SimilarWordsBKLSettings applyConfig(ConfigFile config, String  prefix) {
            super.applyConfig(config, prefix);
            this.range = config.getInt(prefix + "range", this.range);
            this.maxSize = config.getInt(prefix + "size", this.maxSize);
            this.maxSeek= config.getInt(prefix + "seek", this.maxSeek);
            this.durExpire = (long) (config.getDouble(prefix + "time", (float) this.durExpire / 1000f) * 1000f);
            return this;
        }
    }

    protected final SimpleTimedBKLevenshtein tree = new SimpleTimedBKLevenshtein();
    
    protected int added = 0;

    protected final int maxSize;

    protected final int range;

    protected final long durExpire;
    
    protected final int maxSeek;
    
    protected long lastAdd = System.currentTimeMillis();
    
    public SimilarWordsBKL(String name, SimilarWordsBKLSettings settings) {
        super(name, settings);
        this.maxSize = settings.maxSize;
        this.range = settings.range;
        this.durExpire = settings.durExpire;
        this.maxSeek = settings.maxSeek;
    }
    
    @Override
    public void clear() {
        super.clear();
        tree.clear();
        added = 0;
    }

    @Override
    public void start(final MessageLetterCount message) {
        if (added + message.words.length > maxSize || System.currentTimeMillis() - lastAdd > durExpire) tree.clear();
        
    }

    @Override
    protected float getScore(final List<Character> chars, final long ts) {
        // TODO: very short words, very long words.
        lastAdd = ts;
        final char[] a = DigestedWords.toArray(chars);
        final STBKLResult result = tree.lookup(a, range, maxSeek, true);
        if (result.isNew) added ++;
        // Calculate time score.
        float score = 0f;
        if (!result.isNew && result.match != null){
            final long age = ts - result.match.ts;
            result.match.ts = ts;
            if (age < durExpire)
                score = Math.max(score, (float) (durExpire - age) / (float) durExpire);
        }
        for (final SimpleTimedLevenNode node : result.nodes) {
            final long age = ts - node.ts;
            node.ts = ts;
            if (age < durExpire)
                score = Math.max(score, (float) (durExpire - age) / (float) durExpire);
        }
        return score;
    }
}
