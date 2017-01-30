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

import java.util.ArrayList;

public class BlendingPixelFusionIgnoreZero extends BlendingPixelFusion
{	
	/**
	 * Instantiates the per-pixel blending
	 * 
	 * @param images - all input images (the position in the list has to be the same as Id provided by addValue!)
	 */
	public BlendingPixelFusionIgnoreZero( final ArrayList< ? extends ImageInterpolation< ? > > images )
	{
		super( images );
	}	

	@Override
	public void addValue( final double value, final int imageId, final double[] localPosition ) 
	{
		if ( value != 0.0 )
		{
			// we are always inside the image, so we do not want 0.0
			final double weight = Math.max( 0.00001, computeWeight( localPosition, dimensions[ imageId ], border, percentScaling ) );
			
			weightSum += weight;
			valueSum += value * weight;
		}
	}

	@Override
	public PixelFusion copy() { return new BlendingPixelFusionIgnoreZero( images ); }
}
