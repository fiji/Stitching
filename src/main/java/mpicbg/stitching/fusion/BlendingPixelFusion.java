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

public class BlendingPixelFusion implements PixelFusion
{
	public static double fractionBlended = 0.2;
	
	final int numDimensions;
	final int numImages;
	final long[][] dimensions;
	final double percentScaling;
	final double[] border;
	
	final ArrayList< ? extends ImageInterpolation< ? > > images;

	double valueSum, weightSum;
	
	/**
	 * Instantiates the per-pixel blending
	 * 
	 * @param images - all input images (the position in the list has to be the same as Id provided by addValue!)
	 */
	public BlendingPixelFusion( final ArrayList< ? extends ImageInterpolation< ? > > images )
	{
		this( images, fractionBlended );
	}	
	/**
	 * Instantiates the per-pixel blending
	 * 
	 * @param images - all input images (the position in the list has to be the same as Id provided by addValue!)
	 * @param percentScaling - which percentage of the image should be blended ( e.g. 0,3 means 15% on the left and 15% on the right)
	 */
	private BlendingPixelFusion( final ArrayList< ? extends ImageInterpolation< ? > > images, final double fractionBlended )
	{
		this.images = images;
		this.percentScaling = fractionBlended;
		
		this.numDimensions = images.get( 0 ).getImg().numDimensions();
		this.numImages = images.size();
		this.dimensions = new long[ numImages ][ numDimensions ];
		
		for ( int i = 0; i < numImages; ++i )
			for ( int d = 0; d < numDimensions; ++d )
				dimensions[ i ][ d ] = images.get( i ).getImg().dimension( d ) - 1; 

		this.border = new double[ numDimensions ];

		// reset
		clear();
	}
	
	@Override
	public void clear() { valueSum = weightSum = 0;	}

	@Override
	public void addValue( final double value, final int imageId, final double[] localPosition ) 
	{
		// we are always inside the image, so we do not want 0.0
		final double weight = Math.max( 0.00001, computeWeight( localPosition, dimensions[ imageId ], border, percentScaling ) );
		
		weightSum += weight;
		valueSum += value * weight;
	}

	@Override
	public double getValue()
	{ 
		if ( weightSum == 0 )
			return 0;
		return ( valueSum / weightSum );
	}

	@Override
	public PixelFusion copy() { return new BlendingPixelFusion( images ); }

	/**
	 * From SPIM Registration
	 * 
	 * @param location
	 * @param dimensions
	 * @param border
	 * @param percentScaling
	 * @return
	 */
	final public static double computeWeight( final double[] location, final long[] dimensions, final double[] border, final double percentScaling )
	{		
		// compute multiplicative distance to the respective borders [0...1]
		double minDistance = 1;
		
		for ( int dim = 0; dim < location.length; ++dim )
		{
			// the position in the image
			final double localImgPos = location[ dim ];
			
			// the distance to the border that is closer
			double value = Math.max( 1, Math.min( localImgPos - border[ dim ] + 1, (dimensions[ dim ] - 1) - localImgPos - border[ dim ] + 1 ) );
						
			final float imgAreaBlend = Math.round( percentScaling * 0.5f * dimensions[ dim ] );
			
			if ( value < imgAreaBlend )
				value = value / imgAreaBlend;
			else
				value = 1;
			
			minDistance *= value;
		}
		
		if ( minDistance == 1 )
			return 1;
		else if ( minDistance <= 0 )
			return 0.0000001;
		else
			return ( Math.cos( (1 - minDistance) * Math.PI ) + 1 ) / 2;				
	}

}
