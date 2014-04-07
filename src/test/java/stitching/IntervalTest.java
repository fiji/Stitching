
package stitching;

import static org.junit.Assert.*;
import org.junit.Test;

/**
 * Tests for the {@link Interval} class.
 * 
 * @author Mark Hiner
 */
public class IntervalTest {

	/**
	 * Test that when constructing Intervals, the start and end points are picked
	 * out appropriately. When these values are manually altered, verify the
	 * changes are properly reflected.
	 */
	@Test
	public void testIntervalCreation() {
		Interval i = new Interval(1, 2, 3, 4, 5, 6, 7);
		// Constructor should pick out the smallest and largest values to build the
		// interval
		testEnds(i, 1, 7);

		// Again, pick out the smallest and largest values when values are not well
		// ordered
		i = new Interval(3, 0, 10, 100, 25, -9);
		testEnds(i, -9, 100);

		// Test extremes
		i = new Interval(Integer.MAX_VALUE, Integer.MIN_VALUE);
		testEnds(i, Integer.MIN_VALUE, Integer.MAX_VALUE);

		// Test setting to the same point
		i.setEnd(0);
		i.setStart(0);
		testEnds(i, 0, 0);

		// Test adjusting one point manually
		i.setEnd(25);
		testEnds(i, 0, 25);

		// If start > end, the interval should be adjusted automatically
		i.setStart(30);
		testEnds(i, 30, 30);

		// Same for if end < start
		i.setEnd(-7);
		testEnds(i, -7, -7);
	}

	/**
	 * Basic testing of the {@link Interval#contains(int)} method. This is an
	 * inclusive contains test.
	 */
	@Test
	public void testIntervalContains() {
		Interval i = new Interval(-5, 25);

		// Interval is to the right of point
		assertEquals(i.contains(-6), 1);

		// Interval contains the start point
		assertEquals(i.contains(-5), 0);

		// Test a point in the range
		assertEquals(i.contains(10), 0);

		// Interval contains end point
		assertEquals(i.contains(25), 0);

		// Interval is to the left of point
		assertEquals(i.contains(26), -1);
	}

	/**
	 * Basic testing of the {@link Interval#contains(int, boolean)} method. The
	 * {@code ignoreOverlap} flag is set to true, so this is an exclusive contains
	 * test.
	 */
	@Test
	public void testIntervalContainsExclusive() {
		Interval i = new Interval(-5, 25);

		// Interval is to the right of point
		assertEquals(i.contains(-6, true), 1);

		// Interval does not contain the start point
		assertEquals(i.contains(-5, true), 1);

		// Test a point in the range
		assertEquals(i.contains(10, true), 0);

		// Interval does not contain end point
		assertEquals(i.contains(25, true), -1);

		// Interval is to the left of point
		assertEquals(i.contains(26, true), -1);
	}

	/**
	 * Basic testing of the {@link Interval#intersects(Interval)} method. This is
	 * an inclusive intersection test.
	 */
	@Test
	public void testIntervalIntersects() {
		Interval base = new Interval(0, 100);
		Interval test = new Interval(-5, -1);

		testIntersect(base, test, false, false);

		test.setEnd(0);
		testIntersect(base, test, false, true);

		test = new Interval(-100, 42);
		testIntersect(base, test, false, true);

		test.setEnd(101);
		testIntersect(base, test, false, true);

		test.setStart(100);
		testIntersect(base, test, false, true);

		test.setStart(101);
		testIntersect(base, test, false, false);
	}

	/**
	 * Basic testing of the {@link Interval#intersects(Interval, boolean)} method.
	 * The {@code ignoreOverlap} flag is set to true, so this is an exclusive
	 * intersection test.
	 */
	@Test
	public void testIntervalIntersectsExclusive() {
		Interval base = new Interval(0, 100);
		Interval test = new Interval(-5, -1);

		testIntersect(base, test, true, false);

		test.setEnd(0);
		testIntersect(base, test, true, false);

		test = new Interval(-100, 42);
		testIntersect(base, test, true, true);

		test.setEnd(101);
		testIntersect(base, test, true, true);

		test.setStart(100);
		testIntersect(base, test, true, false);

		test.setStart(101);
		testIntersect(base, test, true, false);
	}

	/**
	 * Simple test for {@link Interval#equalsInterval(Interval)}
	 */
	@Test
	public void testEquality() {
		Interval i1 = new Interval(-5, 5);
		Interval i2 = new Interval(-5, 5);
		Interval i3 = new Interval(100, 100);

		assertTrue(i1.equalsInterval(i2));
		assertFalse(i1.equals(i3));
		i3.setStart(-5);
		i3.setEnd(5);
		assertTrue(i2.equalsInterval(i3));
	}

	// Helper methods

	/**
	 * Verify the start and end points of an interval
	 */
	private void testEnds(Interval i, int start, int end) {
		assertTrue(i.start() == start);
		assertTrue(i.end() == end);
	}

	/**
	 * Helper method to test {@link Interval#intersects(Interval, boolean)}. Tests
	 * both directions of intersection between the intervals, to avoid bias.
	 */
	private void testIntersect(Interval base, Interval test,
		boolean ignoreOverlap, boolean expected)
	{
		if (expected) {
			assertTrue(base.intersects(test, ignoreOverlap));
			assertTrue(test.intersects(base, ignoreOverlap));
		}
		else {
			assertFalse(base.intersects(test, ignoreOverlap));
			assertFalse(test.intersects(base, ignoreOverlap));
		}
	}
}
