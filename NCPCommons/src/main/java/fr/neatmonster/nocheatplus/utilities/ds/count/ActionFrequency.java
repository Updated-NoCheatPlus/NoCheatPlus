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
package fr.neatmonster.nocheatplus.utilities.ds.count;

/**
 * Keep track of frequency of some action, 
 * put weights into buckets, which represent intervals of time. 
 * @author mc_dev
 *
 */
public class ActionFrequency {

    /** Reference time for the transition from the first to the second bucket. */
    private long time = 0;
    /**
     * Time of last update (add). Should be the "time of the last event" for the
     * usual case.
     */
    private long lastUpdate = 0;
    private final boolean noAutoReset;

    /**
     * Buckets to fill weights in, each represents an interval of durBucket duration, 
     * index 0 is the latest, highest index is the oldest. 
     * Weights will get filled into the next buckets with time passed. 
     */
    private final float[] buckets;

    /** Duration in milliseconds that one bucket covers. */
    private final long durBucket;

    /**
     * This constructor will set noAutoReset to false, optimized for short term
     * accounting.
     * 
     * @param nBuckets
     * @param durBucket
     */
    public ActionFrequency(final int nBuckets, final long durBucket) {
        this(nBuckets, durBucket, false);
    }

    /**
     * 
     * @param nBuckets
     * @param durBucket
     * @param noAutoReset
     *            Set to true, to prevent auto-resetting with
     *            "time ran backwards". Setting this to true is recommended if
     *            larger time frames are monitored, to prevent data loss.
     */
    public ActionFrequency(final int nBuckets, final long durBucket, final boolean noAutoReset) {
        this.buckets = new float[nBuckets];
        this.durBucket = durBucket;
        this.noAutoReset = noAutoReset;
    }

    /**
     * Update and add (updates reference and update time).
     * @param now
     * @param amount
     */
    public final void add(final long now, final float amount) {
        update(now);
        buckets[0] += amount;
    }

    /**
     * Unchecked addition of amount to the first bucket.
     * @param amount
     */
    public final void add(final float amount) {
        buckets[0] += amount;
    }

    /**
     * Update without adding, also updates reference and update time. Detects
     * time running backwards.
     * 
     * @param now
     */
    public final void update(final long now) {
        final long diff = now - time;
        if (now < lastUpdate) {
            // Time ran backwards.
            if (noAutoReset) {
                // Only update time and lastUpdate.
                time = lastUpdate = now;
            } else {
                // Clear all.
                clear(now);
                return;
            }
        }
        else if (diff >= durBucket * buckets.length) {
            // Clear (beyond range).
            clear(now);
            return;
        }
        else if (diff < durBucket) {
            // No changes (first bucket).
        }
        else {
            final int shift = (int) ((float) diff / (float) durBucket);
            // Update buckets.
            for (int i = 0; i < buckets.length - shift; i++) {
                buckets[buckets.length - (i + 1)] = buckets[buckets.length - (i + 1 + shift)];
            }
            for (int i = 0; i < shift; i++) {
                buckets[i] = 0;
            }
            // Set time according to bucket duration (!).
            time += durBucket * shift;
        }
        // Ensure lastUpdate is set.
        lastUpdate = now;
    }

    /**
     * Clear all counts, reset reference and update time.
     * @param now
     */
    public final void clear(final long now) {
        for (int i = 0; i < buckets.length; i++) {
            buckets[i] = 0f;
        }
        time = lastUpdate = now;
    }

    /**
     * Get a weighted sum score, weight for bucket i: w(i) = factor^i. 
     * @param factor
     * @return
     */
    public final float score(final float factor) {
        return sliceScore(0, buckets.length, factor);
    }

    /**
     * Get score of a certain bucket. At own risk.
     * @param bucket
     * @return
     */
    public final float bucketScore(final int bucket) {
        return buckets[bucket];
    }

    /**
     * Get score of first end buckets, with factor.
     * @param end Number of buckets including start. The end is not included.
     * @param factor
     * @return
     */
    public final float leadingScore(final int end, float factor) {
        return sliceScore(0, end, factor);
    }

    /**
     * Get score from start on, with factor.
     * @param start This is included.
     * @param factor
     * @return
     */
    public final float trailingScore(final int start, float factor) {
        return sliceScore(start, buckets.length, factor);
    }

    /**
     * Get score from start on, until before end, with factor.
     * @param start This is included.
     * @param end This is not included.
     * @param factor
     * @return
     */
    public final float sliceScore(final int start, final int end, float factor) {
        float score = buckets[start];
        float cf = factor;
        for (int i = start + 1; i < end; i++) {
            score += buckets[i] * cf;
            cf *= factor;
        }
        return score;
    }

    /**
     * Set the value for a buckt.
     * @param n
     * @param value
     */
    public final void setBucket(final int n, final float value) {
        buckets[n] = value;
    }

    /**
     * Set the reference time and last update time.
     * @param time
     */
    public final void setTime(final long time) {
        this.time = time;
        this.lastUpdate = time;
    }

    /**
     * Get the reference time for the transition from the first to the second bucket.
     * @return
     */
    public final long lastAccess() { // TODO: Should rename this.
        return time;
    }

    /**
     * Get the last time when update was called (adding).
     * @return
     */
    public final long lastUpdate() {
        return lastUpdate;
    }

    /**
     * Get the number of buckets.
     * @return
     */
    public final int numberOfBuckets() {
        return buckets.length;
    }

    /**
     * Get the duration of a bucket in milliseconds.
     * @return
     */
    public final long bucketDuration() {
        return durBucket;
    }

    /**
     * Serialize to a String line.
     * @return
     */
    public final String toLine() {
        // TODO: Backwards-compatible lastUpdate ?
        final StringBuilder buffer = new StringBuilder(50);
        buffer.append(buckets.length + ","+durBucket+","+time);
        for (int i = 0; i < buckets.length; i++) {
            buffer.append("," + buckets[i]);
        }
        return buffer.toString();
    }

    /**
     * Update and then reduce all given ActionFrequency instances by the given
     * amount, capped at a maximum of 0 for the resulting first bucket score.
     * 
     * @param amount
     *            The amount to subtract.
     * @param freqs
     */
    public static void reduce(final long time, final float amount, final ActionFrequency... freqs) {
        for (int i = 0; i < freqs.length; i++) {
            final ActionFrequency freq = freqs[i];
            freq.update(time);
            freq.setBucket(0, Math.max(0f, freq.bucketScore(0) - amount));
        }
    }

    /**
     * Update and then reduce all given ActionFrequency instances by the given
     * amount, without capping the result.
     * 
     * @param amount
     *            The amount to subtract.
     * @param freqs
     */
    public static void subtract(final long time, final float amount, final ActionFrequency... freqs) {
        for (int i = 0; i < freqs.length; i++) {
            final ActionFrequency freq = freqs[i];
            freq.update(time);
            freq.setBucket(0, freq.bucketScore(0) - amount);
        }
    }

    /**
     * Deserialize from a string.
     * @param line
     * @return
     */
    public static ActionFrequency fromLine(final String line) {
        // TODO: Backwards-compatible lastUpdate ?
        String[] split = line.split(",");
        if (split.length < 3) throw new RuntimeException("Bad argument length."); // TODO
        final int n = Integer.parseInt(split[0]);
        final long durBucket = Long.parseLong(split[1]);
        final long time = Long.parseLong(split[2]);
        final float[] buckets = new float[split.length -3];
        if (split.length - 3 != buckets.length) throw new RuntimeException("Bad argument length."); // TODO
        for (int i = 3; i < split.length; i ++) {
            buckets[i - 3] = Float.parseFloat(split[i]);
        }
        ActionFrequency freq = new ActionFrequency(n, durBucket);
        freq.setTime(time);
        for (int i = 0; i < buckets.length; i ++) {
            freq.setBucket(i, buckets[i]);
        }
        return freq;
    }
}
