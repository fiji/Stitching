/**
 * This program is free software; you can redistribute it and/or
 * modify it under the terms of the GNU General Public License 2
 * as published by the Free Software Foundation.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program; if not, write to the Free Software
 * Foundation, Inc., 59 Temple Place - Suite 330, Boston, MA  02111-1307, USA.
 * 
 * An execption is the FFT implementation of Dave Hale which we use as a library,
 * wich is released under the terms of the Common Public License - v1.0, which is 
 * available at http://www.eclipse.org/legal/cpl-v10.html  
 *
 * @author Mark Hiner, Stephan Preibisch
 */

package mpicbg.stitching.fusion;

/**
 * A range on the number line with a {@link #min()} and {@link #max()} point.
 * 
 * @author Mark Hiner hinerm at gmail.com
 */
public class Interval {

	private float min;
	private float max;

	/**
	 * Constructs a new Interval using the smallest and largest values in the
	 * provided points.
	 */
	public Interval(final float... points) {
		min = Integer.MAX_VALUE;
		max = Integer.MIN_VALUE;
		for (final float p : points) {
			min = Math.min(min, p);
			max = Math.max(max, p);
		}
	}

	/**
	 * Copying constructor
	 */
	public Interval(final Interval interval) {
		min = interval.min();
		max = interval.max();
	}

	/**
	 * Update the start position of this interval. If after this setting,
	 * {@code min > max}, sets {@code max = min}.
	 * 
	 * @return true if the max was also modified during this call
	 */
	public boolean setMin(final float min) {
		this.min = min;
		if (Float.compare(min, max) > 0) {
			max = min;
			return true;
		}
		return false;
	}

	/**
	 * Update the end position of this interval. If after this setting,
	 * {@code max < min}, sets {@code min = max}.
	 * 
	 * @return true if the min was also modified during this call
	 */
	public boolean setMax(final float max) {
		this.max = max;
		if (Float.compare(max, min) < 0) {
			min = max;
			return true;
		}
		return false;
	}

	/**
	 * @return Start position for this interval
	 */
	public float min() {
		return min;
	}

	/**
	 * @return End position for this interval
	 */
	public float max() {
		return max;
	}

	/**
	 * @return -1 if this interval is completely to the left of the point. 1 if
	 *         this interval is completely to the right of the point. 0 if the
	 *         interval intersects (contains) the point.
	 */
	public int contains(final float point) {
		if (Float.compare(max, point) <= 0) {
			return -1;
		}
		else if (Float.compare(min, point) >= 0) {
			return 1;
		}
		return 0;
	}

	/**
	 * Determines if this interval has an intersection with another interval. 
	 */
	public boolean intersects(final Interval other) {
		// always return true if the two intervals are the same
		final boolean intersects = equalsInterval(other);
		// For two finite, non-identical intervals to overlap, one needs to contain
		// the start or end point of the other.
		return intersects || contains(other.min()) == 0 ||
			other.contains(min()) == 0 ||
			contains(other.max()) == 0 ||
			other.contains(max()) == 0;
	}

	/**
	 * Check to see if another interval is the same as this interval.
	 */
	public boolean equalsInterval(final Interval other) {
		return Float.compare(min(), other.min()) == 0 &&
			Float.compare(max(), other.max()) == 0;
	}

	@Override
	public String toString() {
		return "[" + min + ", " + max + "]";
	}
}
