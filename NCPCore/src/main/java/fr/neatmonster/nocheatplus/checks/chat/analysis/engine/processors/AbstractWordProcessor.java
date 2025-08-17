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

import java.util.LinkedList;
import java.util.List;
import java.util.Map;

import fr.neatmonster.nocheatplus.checks.chat.analysis.MessageLetterCount;
import fr.neatmonster.nocheatplus.checks.chat.analysis.WordLetterCount;

public abstract class AbstractWordProcessor implements WordProcessor{
    
    /**
     * Remove a number of entries from a map, in order of iteration over a key set.
     * @param map
     * @param number Number of entries to remove.
     */
    public static final <K> void releaseMap(final Map<K, ?> map, int number) {
        final List<K> rem = new LinkedList<K>();
        int i = 0;
        for (final K key : map.keySet()) {
            rem.add(key);
            i++;
            if (i > number) break;
        }
        for (final K key : rem) {
            map.remove(key);
        }
    }
    
    protected String name;
    /** Not set by constructor. */
    protected float weight = 1f;
    
    public AbstractWordProcessor(String name) {
        this.name = name;
    }
    
    @Override
    public String getProcessorName() {
        return name;
    }
    
    @Override
    public float getWeight() {
        return weight;
    }
    
    public void setWeight(float weight) {
        this.weight = weight;
    }

    @Override
    public float process(final MessageLetterCount message) {
        // Does the looping, scores are summed up and divided by number of words.
        start(message);
        final long ts = System.currentTimeMillis();
        float score = 0;
        for (int index = 0; index < message.words.length; index++) {
            final WordLetterCount word = message.words[index];
            final String key = word.word.toLowerCase();
            score += loop(ts, index, key, word) * (float) (word.word.length() + 1);
        }
        score /= (float) (message.message.length() + message.words.length);
        return score;
    }
    
    public void start(final MessageLetterCount message){
        // Override if needed.
    }
    
    @Override
    public void clear() {
        // Override if needed.
    }
    
    /**
     * Process one word.
     * @param index
     * @param message
     * @return Score, suggested to be within [0 .. 1].
     */
    public abstract float loop(final long ts, final int index, final String key, final WordLetterCount word);   
}
