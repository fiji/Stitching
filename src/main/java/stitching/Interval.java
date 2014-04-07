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

package stitching;

/**
 * A range on the number line with a {@link #start()} and {@link #end()} point.
 * 
 * @author Mark Hiner hinerm at gmail.com
 */
public class Interval {

	private int start;
	private int end;

	/**
	 * Constructs a new Interval using the smallest and largest values in the
	 * provided points.
	 */
	public Interval(final int... points) {
		start = Integer.MAX_VALUE;
		end = Integer.MIN_VALUE;
		for (final int p : points) {
			start = Math.min(start, p);
			end = Math.max(end, p);
		}
	}

	/**
	 * Update the start position of this interval. If after this setting,
	 * {@code start > end}, sets {@code end = start}.
	 */
	public void setStart(final int start) {
		this.start = start;
		if (start > end) end = start;
	}

	/**
	 * Update the end position of this interval. If after this setting,
	 * {@code end < start}, sets {@code start = end}.
	 */
	public void setEnd(final int end) {
		this.end = end;
		if (end < start) start = end;
	}

	/**
	 * @return Start position for this interval
	 */
	public int start() {
		return start;
	}

	/**
	 * @return End position for this interval
	 */
	public int end() {
		return end;
	}

	/**
	 * As {@link #contains(int, boolean)} with {@code exclusive = false}.
	 * 
	 * @return -1 if this interval is completely to the left of the point. 1 if
	 *         this interval is completely to the right of the point. 0 if the
	 *         interval intersects (contains) the point.
	 */
	public int contains(final int point) {
		return contains(point, false);
	}

	/**
	 * Determines if this interval contains the given point. If
	 * {@code exclusive} is true, then this interval is considered exclusive
	 * of its start and end points.
	 * 
	 * @return -1 if this interval is completely to the left of the point. 1 if
	 *         this interval is completely to the right of the point. 0 if the
	 *         interval intersects (contains) the point.
	 */
	public int contains(final int point, final boolean exclusive) {
		if (end < point || (exclusive && end == point)) {
			return -1;
		}
		else if (start > point || (exclusive && start == point)) {
			return 1;
		}
		return 0;
	}

	/**
	 * As {@link #intersects(Interval, boolean)} with
	 * {@code exclusive = false}.
	 */
	public boolean intersects(final Interval other) {
		return intersects(other, false);
	}

	/**
	 * Determines if this interval has an intersection with another interval. This
	 * is true if one {@link #contains} the other's start or end point. If
	 * {@code exclusive} is true, the intervals are treated as exclusive
	 * intervals.
	 */
	public boolean intersects(final Interval other, final boolean exclusive) {
		// always return true if the two intervals are the same
		final boolean intersects = start() == other.start() && end() == other.end();
		// For two finite, non-identical intervals to overlap, one needs to contain
		// the start or end point of the other.
		return intersects || contains(other.start(), exclusive) == 0 ||
			other.contains(start(), exclusive) == 0 ||
			contains(other.end(), exclusive) == 0 ||
			other.contains(end(), exclusive) == 0;
	}

	/**
	 * Check to see if another interval is the same as this interval.
	 */
	public boolean equalsInterval(final Interval other) {
		return start() == other.start() && end == other.end();
	}

	@Override
	public String toString() {
		return "[" + start + ", " + end + "]";
	}
}
