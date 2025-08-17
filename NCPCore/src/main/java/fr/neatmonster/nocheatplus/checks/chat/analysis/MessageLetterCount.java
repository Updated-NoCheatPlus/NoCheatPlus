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
package fr.neatmonster.nocheatplus.checks.chat.analysis;

import java.util.HashMap;
import java.util.Map;

/**
 * Letter count for a message and sub words, including upper case count.<br>
 * NOTE: this is a pretty heavy implementation for testing purposes.
 * @author mc_dev
 *
 */
public class MessageLetterCount {
    
    public final String message;
    
    public final String split;
    
    public final WordLetterCount[] words;
    
    public final WordLetterCount fullCount;
    
    /**
     * Constructor for splitting by a space. 
     * @param message
     */
    public MessageLetterCount(final String message){
        this(message, " ");
    }
    
    /**
     * 
     * @param message
     * @param split
     */
    public MessageLetterCount(final String message, final String split){
        this.message = message;
        this.split = split;
        
        final String[] parts = message.split(split);
        words = new WordLetterCount[parts.length];
        
        fullCount = new WordLetterCount(message);
        // (Do not store 60 times "a".)
        final Map<String, WordLetterCount> done = new HashMap<String, WordLetterCount>(words.length);
        for (int i = 0; i < parts.length; i++){
            final String word = parts[i];
            if (done.containsKey(word)){
                words[i] = done.get(word);
                continue;
            }
            done.put(word, words[i] = new WordLetterCount(word));
        }
        done.clear();
    }

}
