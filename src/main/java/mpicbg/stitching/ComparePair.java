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

import ij.ImagePlus;
import mpicbg.models.Model;

public class ComparePair 
{
	final ImagePlusTimePoint impA, impB;
	float crossCorrelation;
	boolean validOverlap;
	
	// the local shift of impB relative to impA 
	float[] relativeShift;
	
	public ComparePair( final ImagePlusTimePoint impA, final ImagePlusTimePoint impB )
	{
		this.impA = impA;
		this.impB = impB;
		this.validOverlap = true;
	}
	
	public ImagePlusTimePoint getTile1() { return impA; }
	public ImagePlusTimePoint getTile2() { return impB; }
	
	public ImagePlus getImagePlus1() { return impA.getImagePlus(); }
	public ImagePlus getImagePlus2() { return impB.getImagePlus(); }
	
	public int getTimePoint1() { return impA.getTimePoint(); }
	public int getTimePoint2() { return impB.getTimePoint(); }
	
	public Model< ? > getModel1() { return impA.getModel(); }
	public Model< ? > getModel2() { return impB.getModel(); }
	
	public void setCrossCorrelation( final float r ) { this.crossCorrelation = r; }
	public float getCrossCorrelation() { return crossCorrelation; }

	public void setRelativeShift( final float[] relativeShift ) { this.relativeShift = relativeShift; }
	public float[] getRelativeShift() { return relativeShift; }
	
	public void setIsValidOverlap( final boolean state ) { this.validOverlap = state; }
	public boolean getIsValidOverlap() { return validOverlap; }
}
