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
package stitching.model;

import java.util.Collection;

/**
 * TODO
 *
 * @author Stephan Saalfeld
 */
public class TranslationModel3D extends InvertibleModel
{
	static final protected int MIN_SET_SIZE = 1;
	final protected float[] translation = new float[ 3 ];
	public float[] getTranslation(){ return translation; }
	
	@Override
	final public int getMinSetSize(){ return MIN_SET_SIZE; }

	//@Override
	@Override
	public float[] apply( float[] point )
	{
		assert point.length == 3 : "3d translations can be applied to 3d points only.";
		
		return new float[]{
			point[ 0 ] + translation[ 0 ],
			point[ 1 ] + translation[ 1 ],
			point[ 2 ] + translation[ 2 ] };
	}
	
	//@Override
	@Override
	public void applyInPlace( float[] point )
	{
		assert point.length == 3 : "3d translations can be applied to 3d points only.";
		
		point[ 0 ] += translation[ 0 ];
		point[ 1 ] += translation[ 1 ];
		point[ 2 ] += translation[ 2 ];
	}
	
	//@Override
	@Override
	public float[] applyInverse( float[] point )
	{
		assert point.length == 3 : "3d translations can be applied to 3d points only.";
		
		return new float[]{
				point[ 0 ] - translation[ 0 ],
				point[ 1 ] - translation[ 1 ],
				point[ 2 ] - translation[ 2 ] };
	}

	//@Override
	@Override
	public void applyInverseInPlace( float[] point )
	{
		assert point.length == 3 : "3d translations can be applied to 3d points only.";
		
		point[ 0 ] -= translation[ 0 ];
		point[ 1 ] -= translation[ 1 ];
		point[ 2 ] -= translation[ 2 ];
	}

	
	@Override
	public String toString()
	{
		return ( "[1,3](" + translation[ 0 ] + "," + translation[ 1 ] + "," + translation[ 2 ] + ") " + cost );
	}

	@Override
	final public void fit( Collection< PointMatch > matches ) throws NotEnoughDataPointsException
	{
		if ( matches.size() < MIN_SET_SIZE ) throw new NotEnoughDataPointsException( matches.size() + " data points are not enough to estimate a 3d translation model, at least " + MIN_SET_SIZE + " data points required." );
		
		// center of mass:
		float pcx = 0, pcy = 0, pcz = 0;
		float qcx = 0, qcy = 0, qcz = 0;
		
		double ws = 0.0;
		
		for ( PointMatch m : matches )
		{
			float[] p = m.getP1().getL(); 
			float[] q = m.getP2().getW(); 
			
			float w = m.getWeight();
			ws += w;
			
			pcx += w * p[ 0 ];
			pcy += w * p[ 1 ];
			pcz += w * p[ 2 ];
			qcx += w * q[ 0 ];
			qcy += w * q[ 1 ];
			qcz += w * q[ 2 ];
		}
		pcx /= ws;
		pcy /= ws;
		pcz /= ws;
		qcx /= ws;
		qcy /= ws;
		qcz /= ws;

		translation[ 0 ] = qcx - pcx;
		translation[ 1 ] = qcy - pcy;
		translation[ 2 ] = qcz - pcz;
	}
	
	/**
	 * change the model a bit
	 * 
	 * estimates the necessary amount of shaking for each single dimensional
	 * distance in the set of matches
	 * 
	 * @param matches point matches
	 * @param scale gives a multiplicative factor to each dimensional distance (scales the amount of shaking)
	 * @param center local pivot point for centered shakes (e.g. rotation)
	 */
	@Override
	final public void shake(
			Collection< PointMatch > matches,
			float scale,
			float[] center )
	{
		// TODO If you ever need it, please implement it...
	}

	@Override
	public TranslationModel3D clone()
	{
		TranslationModel3D tm = new TranslationModel3D();
		tm.translation[ 0 ] = translation[ 0 ];
		tm.translation[ 1 ] = translation[ 1 ];
		tm.translation[ 2 ] = translation[ 2 ];
		tm.cost = cost;
		return tm;
	}
}
