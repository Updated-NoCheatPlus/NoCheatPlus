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
package fr.neatmonster.nocheatplus.utilities;

import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Iterator;
import java.util.List;


/**
 * String utility methods (working with or returning strings).
 * @author asofold
 *
 */
public class StringUtil {

    /** Decimal format for "#.###" */
    public static final DecimalFormat fdec3 = new DecimalFormat();
    /** Decimal format for "#.######" */
    public static final DecimalFormat fdec1 = new DecimalFormat();
    public static final DecimalFormat fdec6 = new DecimalFormat();

    static {
        DecimalFormatSymbols sym = fdec6.getDecimalFormatSymbols();
        // 6 digits
        sym.setDecimalSeparator('.');
        fdec6.setDecimalFormatSymbols(sym);
        fdec6.setMaximumFractionDigits(6);
        fdec6.setMinimumIntegerDigits(1);
        // 3 digits.
        sym = fdec6.getDecimalFormatSymbols();
        sym.setDecimalSeparator('.');
        fdec3.setDecimalFormatSymbols(sym);
        fdec3.setMaximumFractionDigits(3);
        fdec3.setMinimumIntegerDigits(1);
        // 1 digit.
        sym = fdec1.getDecimalFormatSymbols();
        sym.setDecimalSeparator('.');
        fdec1.setDecimalFormatSymbols(sym);
        fdec1.setMaximumFractionDigits(1);
        fdec1.setMinimumIntegerDigits(1);
    }

    /**
     * List by boxing.
     * @param chars
     * @return
     */
    public static List<Character> characterList(char...chars) {
        final List<Character> res = new ArrayList<Character>(chars.length);
        for (int i = 0; i < chars.length; i++) {
            res.add(chars[i]);
        }
        return res;
    }

    /**
     * Join parts with link, starting from startIndex.
     * @param input
     * @param startIndex
     * @param link
     * @return
     */
    public static <O extends Object> String join(O[] input, int startIndex, String link) {
        return join(Arrays.copyOfRange(input, startIndex, input.length), link);
    }

    /**
     * Join parts with link.
     * 
     * @param input
     * @param link
     * @return
     */
    public static <O extends Object> String join(O[] input, String link) {
        return join(Arrays.asList(input), link);
    }

    public static String join(Iterator<? extends Object> iterator, String link) {
        final StringBuilder builder = new StringBuilder(1024);
        boolean first = true;
        while (iterator.hasNext()) {
            final Object obj = iterator.next();
            if (!first) {
                builder.append(link);
            }
            builder.append(obj.toString());
            first = false;
        }
        return builder.toString();
    }

    /**
     * Join parts with link.
     * 
     * @param input
     * @param link
     * @return
     */
    public static String join(final Collection<? extends Object> input, final String link)
    {
        return join(input, link, new StringBuilder(Math.max(300, input.size() * 10))).toString();
    }

    /**
     * Add joined parts with link.
     * 
     * @param input
     * @param link
     * @return The given StringBuilder for chaining.
     */
    public static StringBuilder join(final Collection<? extends Object> input, final String link,
            final StringBuilder builder)
    {
        boolean first = true;
        for (final Object obj : input) {
            if (!first) {
                builder.append(link);
            }
            builder.append(obj.toString());
            first = false;
        }
        return builder;
    }

    /**
     * Split input by all characters given (convenience method).
     * 
     * @param input
     * @param chars
     * @return An (Array)List with the results.
     */
    public static List<String> split(String input, Character... chars){
        return split(input, Arrays.asList(chars));
    }

    /**
     * Split input by all characters given (convenience method).
     * 
     * @param input
     * @param chars
     * @return An (Array)List with the results.
     */
    public static List<String> splitChars(String input, char... chars){
        return split(input, characterList(chars));
    }

    /**
     * Split input by all characters given (convenience method).
     * 
     * @param input
     * @param chars
     * @return An (Array)List with the results.
     */
    public static List<String> split(String input, Collection<Character> chars){
        // TODO: Construct one regular expression to do the entire job!?
        List<String> out = new ArrayList<String>();
        out.add(input);
        List<String> queue = new ArrayList<String>();
        for (final char c : chars){
            String hex = Integer.toHexString((int) c);
            switch (hex.length()){
                case 1:
                    hex = "000" + hex;
                    break;
                case 2:
                    hex = "00" + hex;
                    break;
                case 3:
                    hex = "0" + hex;
            }
            for (final String s : out){
                final String[] split = s.split("\\u" + hex);
                for (final String _s : split){
                    queue.add(_s);
                }
            }
            List<String> temp = out;
            out = queue;
            queue = temp;
            queue.clear();
        }
        return out;
    }

    /**
     * Add non empty strings to an output (Array)List.
     * 
     * @param input
     * @param trim
     * @return
     */
    public static List<String> getNonEmpty(final List<String> input, boolean trim) {
        final List<String> output = new ArrayList<String>();
        for (String x : input) {
            if (trim) {
                x = x.trim();
            }
            if (!x.isEmpty()) {
                output.add(x);
            }
        }
        return output;
    }


    /**
     * Return if the two Strings are similar based on the given threshold.
     * 
     * @param s
     *            the first String, must not be null
     * @param t
     *            the second String, must not be null
     * @param threshold
     *            the minimum value of the correlation coefficient
     * @return result true if the two Strings are similar, false otherwise
     */
    public static boolean isSimilar(final String s, final String t, final float threshold)
    {
        return 1.0f - (float) levenshteinDistance(s, t) / Math.max(1.0, Math.max(s.length(), t.length())) > threshold;
    }

    /**
     * Find the Levenshtein distance between two Strings.
     * 
     * This is the number of changes needed to change one String into another,
     * where each change is a single character modification (deletion, insertion or substitution).
     * 
     * @param s
     *            the first String, must not be null
     * @param t
     *            the second String, must not be null
     * @return result distance
     */
    public static int levenshteinDistance(CharSequence s, CharSequence t) {
        if (s == null || t == null) throw new IllegalArgumentException("Strings must not be null");

        int n = s.length();
        int m = t.length();

        if (n == 0) return m;
        else if (m == 0) return n;

        if (n > m) {
            final CharSequence tmp = s;
            s = t;
            t = tmp;
            n = m;
            m = t.length();
        }

        int p[] = new int[n + 1];
        int d[] = new int[n + 1];
        int _d[];

        int i;
        int j;

        char t_j;

        int cost;

        for (i = 0; i <= n; i++)
            p[i] = i;

        for (j = 1; j <= m; j++) {
            t_j = t.charAt(j - 1);
            d[0] = j;

            for (i = 1; i <= n; i++) {
                cost = s.charAt(i - 1) == t_j ? 0 : 1;
                d[i] = Math.min(Math.min(d[i - 1] + 1, p[i] + 1), p[i - 1] + cost);
            }

            _d = p;
            p = d;
            d = _d;
        }

        return p[n];
    }

    /**
     * Just return the stack trace as new-line-separated string. 
     * @param t
     * @param header Add a header about the exception itself, if set to true.
     * @param trim If to make the output more compact in case of repetitions.
     * @return
     */
    public static final String stackTraceToString(final Throwable t, final boolean header, final boolean trim) {
        // TODO: Consider to use System.getProperty("line.separator").
        // TODO: Consider to add a trimDepth argument, for repetition of a sequence of elements.
        final StringBuilder b = new StringBuilder(325);
        if (header) {
            b.append(t.toString()); // TODO: Check.
            b.append("\n");
        }
        final StackTraceElement[] elements = t.getStackTrace();
        StackTraceElement last = null; // Assume this is faster than operating on the strings.
        int repetition = 0;
        for (int i = 0; i < elements.length; i++) {
            final StackTraceElement element = elements[i];
            if (trim) {
                if (element.equals(last)) {
                    repetition += 1;
                    continue;
                } else {
                    if (repetition > 0) {
                        if (header) {
                            b.append("\t");
                        }
                        b.append("(... repeated " + repetition + " times.)\n");
                        repetition = 0;
                    }
                    last = element;
                }
            }
            if (header) {
                b.append("\t");
            }
            b.append(element);
            b.append("\n");
        }
        if (repetition > 0) {
            if (header) {
                b.append("\t");
            }
            b.append("(... repeated " + repetition + " times.)\n");
        }
        return b.toString();
    }

    /**
     * Convenience method for stackTraceToString(t).
     * @param t
     * @return
     */
    public static final String throwableToString(final Throwable t) {
        return stackTraceToString(t, true, true);
    }

    /**
     * Count number of needles left in a dart board.
     * @param dartBoard
     * @param needles
     * @return
     */
    public static final int count(final String dartBoard, final char needles) {
        int n = 0;
        int index = 0;
        while (index != -1) {
            index = dartBoard.indexOf(needles, index);
            if (index != -1) {
                n ++;
                index ++;
            }
        }
        return n;
    }

    /**
     * Get a version of a String with all leading whitespace removed.
     * 
     * @param input
     * @return String with leading whitespace removed. Returns the original
     *         reference, if there is no leading whitespace.
     */
    public static final String leftTrim(final String input) {
        if (input == null) {
            return null;
        }
        final int len = input.length();
        int beginIndex = 0;
        for (int i = 0; i < len; i++) {
            if (Character.isWhitespace(input.charAt(i))) {
                ++beginIndex;
            }
            else {
                break;
            }
        }
        if (beginIndex > 0) {
            if (beginIndex >= len) {
                return "";
            } else {
                return input.substring(beginIndex);
            }
        }
        else {
            return input;
        }
    }

    /**
     * Format to maximally 6 digits after the comma, always show the sign,
     * unless equal.
     * 
     * @param current
     * @param previous
     * @return
     */
    public static String formatDiff(final double current, final double previous) {
        return current == previous ? "0" : ((current > previous ? "+" : "-") + fdec6.format(Math.abs(current - previous)));
    }

    public static boolean startsWithAnyOf(final String input, final String... startsWith) {
        for (int i = 0; i < startsWith.length; i++) {
            if (input.startsWith(startsWith[i])) {
                return true;
            }
        }
        return false;
    }

    public static boolean endsWithAnyOf(final String input, final String... endsWith) {
        for (int i = 0; i < endsWith.length; i++) {
            if (input.endsWith(endsWith[i])) {
                return true;
            }
        }
        return false;
    }

    public static String reverse(final String input) {
        return new StringBuilder(input).reverse().toString();
    }

}
