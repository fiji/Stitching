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
package mpicbg.stitching;

import mpicbg.imglib.container.ContainerFactory;
import mpicbg.imglib.container.array.ArrayContainerFactory;

public class StitchingParameters 
{
	/**
	 * If we cannot wrap, which factory do we use for computing the phase correlation
	 */
	public static ContainerFactory phaseCorrelationFactory = new ArrayContainerFactory();
	
	/**
	 * If you want to force that the {@link ContainerFactory} above is always used set this to true
	 */
	public static boolean alwaysCopy = false;
	
	public int dimensionality;
	public int fusionMethod;
	public String fusedName;
	public int checkPeaks;
	public boolean addTilesAsRois;
	public boolean computeOverlap, subpixelAccuracy, ignoreZeroValuesFusion = false, downSample = false, displayFusion = false;
	public boolean invertX, invertY;
	public boolean ignoreZStage;
	public double xOffset;
	public double yOffset;
	public double zOffset;

	public boolean virtual = false;
	public int channel1;
	public int channel2;

	public int timeSelect;
	
	public int cpuMemChoice = 0;
	// 0 == fuse&display, 1 == writeToDisk
	public int outputVariant = 0;
	public String outputDirectory = null;
	
	public double regThreshold = -2;
	public double relativeThreshold = 2.5;
	public double absoluteThreshold = 3.5;
	
	//added by John Lapage: allows storage of a sequential comparison range
	public boolean sequential = false;
	public int seqRange = 1;

}
