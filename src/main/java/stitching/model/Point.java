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

/**
 * An n-dimensional point.
 * 
 * {@link #l Local coordinates} are thought to be immutable, application
 * of a model changes the {@link #w world coordinates} of the point.
 *
 * @author Stephan Saalfeld
 */
public class Point
{
	/**
	 * World coordinates
	 */
	final private float[] w;
	final public float[] getW() { return w; }
	
	/**
	 * Local coordinates
	 */
	final private float[] l;
	final public float[] getL() { return l; }
	
	/**
	 * Constructor
	 *          
	 * Sets {@link #l} to the given float[] reference.
	 * 
	 * @param l reference to the local coordinates of the {@link Point}
	 */
	public Point( float[] l )
	{
		this.l = l;
		w = l.clone();		
	}
	
	/**
	 * Apply a {@link Model} to the {@link Point}.
	 * 
	 * Transfers the {@link #l local coordinates} to new
	 * {@link #w world coordinates}.
	 * 
	 * @param model
	 */
	final public void apply( Model model )
	{
		System.arraycopy( l, 0, w, 0, l.length );
		model.applyInPlace( w );
	}
	
	/**
	 * Apply a {@link Model} to the {@link Point} by a given amount.
	 * 
	 * Transfers the {@link #l local coordinates} to new
	 * {@link #w world coordinates}.
	 * 
	 * @param model
	 * @param amount 0.0 -&gt; no application, 1.0 -&gt; full application
	 */
	final public void apply( Model model, float amount )
	{
		float[] a = model.apply( l );
		for ( int i = 0; i < a.length; ++i )
			w[ i ] += amount * ( a[ i ] - w[ i ] );
	}
	
	/**
	 * Apply the inverse of a {@link InvertibleModel} to the {@link Point}.
	 * 
	 * Transfers the {@link #l local coordinates} to new
	 * {@link #w world coordinates}.
	 * 
	 * @param model
	 */
	final public void applyInverse( InvertibleModel model ) throws NoninvertibleModelException
	{
		System.arraycopy( l, 0, w, 0, l.length );
		model.applyInverseInPlace( w );
	}
	
	/**
	 * Estimate the square distance of two {@link Point Points} in the world.
	 *  
	 * @param p1
	 * @param p2
	 * @return square distance
	 */
	final static public float squareDistance( Point p1, Point p2 )
	{
		assert
		p1.l.length == p2.l.length :
			"Both points have to have the same dimensionality.";
		
		double sum = 0.0;
		for ( int i = 0; i < p1.w.length; ++i )
		{
			double d = p1.w[ i ] - p2.w[ i ];
			sum += d * d;
		}
		return ( float )sum;
	}
	
	/**
	 * Estimate the Euclidean distance of two {@link Point Points} in the world.
	 *  
	 * @param p1
	 * @param p2
	 * @return Euclidean distance
	 */
	final static public float distance( Point p1, Point p2 )
	{
		assert
			p1.l.length == p2.l.length :
				"Both points have to have the same dimensionality.";
		
		return ( float )Math.sqrt( squareDistance( p1, p2 ) );
	}
	
	/**
	 * Clone this {@link Point} instance.
	 */
	@Override
	public Point clone()
	{
		Point p = new Point( l.clone() );
		for ( int i = 0; i < w.length; ++i )
			p.w[ i ] = w[ i ];
		return p;
	}
}
