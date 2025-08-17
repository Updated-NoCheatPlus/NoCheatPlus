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

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;

import fr.neatmonster.nocheatplus.checks.chat.analysis.WordLetterCount;
import fr.neatmonster.nocheatplus.config.ConfigFile;

public abstract class DigestedWords extends AbstractWordProcessor{
	
	/**
	 * Doubling code a little for the sake of flexibility with config reading.
	 * @author asofold
	 *
	 */
	public static class DigestedWordsSettings {
		public boolean sort = false;
		public boolean compress = false;
		public boolean split = false;
		public float weight = 1f;
		public int minWordSize = 0;
		public int maxWordSize = 0;
		public DigestedWordsSettings(){}
		
		/**
		 * Returns this object.
		 * @param config
		 * @param prefix Prefix for direct addition of config path.
		 * @return This object.
		 */
		public DigestedWordsSettings applyConfig(ConfigFile config, String prefix) {
			this.sort = config.getBoolean(prefix + "sort", this.sort);
			this.compress = config.getBoolean(prefix + "compress", this.compress);
			this.split = config.getBoolean(prefix + "split", this.split);
			this.weight = (float) config.getDouble(prefix + "weight", this.weight);
			this.minWordSize = config.getInt(prefix + "minwordsize", this.minWordSize);
			this.maxWordSize = config.getInt(prefix + "maxwordsize", this.maxWordSize);
			return this;
		}
	}

	protected boolean sort = false;
	protected boolean compress = false;
	protected boolean split = false;
	
	protected int minWordSize = 0;
	protected int maxWordSize = 0;
	
	protected final List<Character> letters = new ArrayList<Character>(10);
	protected final List<Character> digits = new ArrayList<Character>(10);
	protected final List<Character> other = new ArrayList<Character>(10);
	
	/**
	 * Constructor for a given settings instance.
	 * @param name
	 * @param settings
	 */
	public DigestedWords(String name, DigestedWordsSettings settings) {
		this(name);
		this.weight = settings.weight;
		this.minWordSize = settings.minWordSize;
		this.maxWordSize = settings.maxWordSize;
		this.sort = settings.sort;
		this.compress = settings.compress;
		this.split = settings.split;
	}
	
	/**
	 * 
	 * @param durExpire
	 * @param maxAdd
	 * @param sort Sort letters.
	 * @param compress Only use every letter once.
	 * @param split Check for letters, digits, other individually (!).
	 */
	public DigestedWords(String name) {
		super(name);
	}

	@Override
	public float loop(final long ts, final int index, final String key, final WordLetterCount word) {
		letters.clear();
		digits.clear();
		other.clear();
		Collection<Character> chars;
		if (compress) chars = word.counts.keySet();
		else{
			// Add all.
			chars = new ArrayList<Character>(word.word.length());
			for (int i = 0; i < word.word.length(); i++){
				char c = word.word.charAt(i);
				if (Character.isUpperCase(c)) c = Character.toLowerCase(c);
				chars.add(c); 
				// hmm. Maybe provide all the lists in the WordLetterCount already.
			}
		}
		final int len = chars.size();
		for (Character c : chars){
			if (!split || Character.isLetter(c)) {
				letters.add(c);
			}
			else if (Character.isDigit(c)) {
				digits.add(c);
			}
			else other.add(c);
		}

		float score = 0;
		if (prepare(letters)) score += getScore(letters, ts) * (float) letters.size();
		if (prepare(digits)) score += getScore(digits, ts) * (float) digits.size();
		if (prepare(other)) score += getScore(other, ts) * (float) other.size();
		return len == 0 ? 0f :(score / (float) len);
	}
	
	protected boolean prepare(final List<Character> chars) {
		if (chars.isEmpty()) return false;
		final int size = chars.size();
		if (size < minWordSize) return false;
		if (maxWordSize > 0 && size > maxWordSize) return false;
		if (sort) Collections.sort(chars);
		return true;
	}

	@Override
	public void clear() {
		letters.clear();
		digits.clear();
		other.clear();
		super.clear(); // Just for completeness.
	}
	
	public static final char[] toArray(final Collection<Character> chars) {
		final char[] a = new char[chars.size()];
		int i = 0;
		for (final Character c : chars){
			a[i] = c;
			i ++;
			// TODO: lol, horrible.
		}
		return a;
	}

	/**
	 * 
	 * @param chars List of characters, should be lower case, could be split / compressed.
	 * @param ts common timestamp for processing the whole message.
	 * @return
	 */
	protected abstract float getScore(final List<Character> chars, final long ts);
}
