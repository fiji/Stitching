/*
 * #%L
 * Fiji distribution of ImageJ for the life sciences.
 * %%
 * Copyright (C) 2007 - 2017 Fiji
 * %%
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as
 * published by the Free Software Foundation, either version 2 of the
 * License, or (at your option) any later version.
 * 
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 * 
 * You should have received a copy of the GNU General Public
 * License along with this program.  If not, see
 * <http://www.gnu.org/licenses/gpl-2.0.html>.
 * #L%
 */

package stitching;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import mpicbg.stitching.fusion.ClassifiedRegion;
import mpicbg.stitching.fusion.Interval;

import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

/**
 * Tests for the {@link ClassifiedRegion} class;
 * 
 * @author Mark Hiner
 */
public class ClassifiedRegionTest {

	private ClassifiedRegion region1;

	private Interval i1 = new Interval(0, 256);
	private Interval i2 = new Interval(0, 128);
	private Interval i3 = new Interval(0, 10);

	/**
	 * Initializes a region using the {@link Interval} constructor.
	 */
	@Before
	public void setUp() {
		region1 = new ClassifiedRegion(i1, i2, i3);

	}

	@After
	public void tearDown() {
		region1 = null;
	}

	/**
	 * Verify that regions constructed using different constructors end up the
	 * same.
	 */
	@Test
	public void testRegionCreation() {
		// Use the empty constructor with subsequent set... calls (in mixed order).
		ClassifiedRegion region2 = new ClassifiedRegion(3);
		region2.set(new Interval(0, 128), 1);
		region2.set(new Interval(0, 10), 2);
		region2.set(new Interval(0, 256), 0);

		// Test for equality
		assertTrue(equalRegions(region1, region2));

		// Use copying constructor and test that equality is preserved
		ClassifiedRegion region3 = new ClassifiedRegion(region1);
		assertTrue(equalRegions(region2, region3));
	}

	/**
	 * Test the inclusive {@link ClassifiedRegion#intersects(ClassifiedRegion)}
	 * method.
	 */
	@Test
	public void testIntersect() {
		// test overlap
		ClassifiedRegion region2 =
			new ClassifiedRegion(new Interval(-10, 255), i2, i3);
		assertTrue(intersectingRegions(region1, region2));

		// verify no overlap
		region2.get(0).setMax(-10);
		assertFalse(intersectingRegions(region1, region2));

		// Test with a region that borders the extents of the first region
		region2 =
			new ClassifiedRegion(new Interval(256, 260), new Interval(128, 142),
				new Interval(10, 11));
		assertTrue(intersectingRegions(region1, region2));
	}

	/**
	 * Tests the special case of intersection when one region is completely
	 * contained within another.
	 */
	@Test
	public void testContainedIntersection() {
		ClassifiedRegion region2 =
			new ClassifiedRegion(new Interval(1, 120), new Interval(50, 75),
				new Interval(3, 6));
		assertTrue(intersectingRegions(region1, region2));
	}

	/**
	 * Simple test of the {@link ClassifiedRegion#equalsRegion(ClassifiedRegion)}
	 * method.
	 */
	@Test
	public void testEqualRegions() {
		ClassifiedRegion region2 =
			new ClassifiedRegion(new Interval(0, 256), new Interval(0, 128),
				new Interval(0, 10));

		assertTrue(equalRegions(region1, region2));

		region2.get(0).setMin(1);
		assertFalse(equalRegions(region1, region2));
	}

	/**
	 * Test with bad calls to the {@link ClassifiedRegion#set(Interval, int)}
	 * method.
	 */
	@Test
	public void testBadSets() {
		// Test a negative value
		try {
			region1.set(null, -7);
			Assert.fail();
		}
		catch (ArrayIndexOutOfBoundsException e) {

		}
		catch (Exception e) {
			Assert.fail();
		}

		// Test a value > the last position (0-indexed)
		try {
			region1.set(null, region1.size());
			Assert.fail();
		}
		catch (ArrayIndexOutOfBoundsException e) {

		}
		catch (Exception e) {
			Assert.fail();
		}
	}

	// Helper methods

	/**
	 * Test the {@link ClassifiedRegion#intersects(ClassifiedRegion, boolean)}
	 * method in both direction, to avoid bias.
	 */
	private boolean intersectingRegions(ClassifiedRegion region1,
		ClassifiedRegion region2)
	{
		return region1.intersects(region2) && region2.intersects(region1);
	}

	/**
	 * Test the {@link ClassifiedRegion#equalsRegion(ClassifiedRegion)} method in
	 * both directions, to avoid bias.
	 */
	private boolean equalRegions(ClassifiedRegion region1,
		ClassifiedRegion region2)
	{
		return region1.equalsRegion(region2) && region2.equalsRegion(region1);
	}
}
