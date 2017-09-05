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
package mpicbg.stitching.fusion;

public class MinPixelFusion implements PixelFusion 
{
	double min;
	boolean set;
	
	public MinPixelFusion() { clear(); }
	
	@Override
	public void clear() 
	{ 
		min = 0;
		set = false;
	}

	@Override
	public void addValue( final double value, final int imageId, final double[] localPosition )
	{
		if ( set )
		{
			min = Math.min( value, min );
		}
		else
		{
			min = value;
			set = true;
		}
	}

	@Override
	public double getValue() { return min; }

	@Override
	public PixelFusion copy() { return new MinPixelFusion(); }
}
