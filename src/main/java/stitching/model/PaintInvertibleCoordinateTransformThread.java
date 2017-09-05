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

import ij.ImagePlus;
import ij.process.ImageProcessor;

import java.util.concurrent.atomic.AtomicBoolean;

import stitching.utils.Log;

/**
 * TODO
 *
 * @author Stephan Saalfeld
 */
public class PaintInvertibleCoordinateTransformThread extends Thread
{
	final protected ImagePlus imp;
	final protected ImageProcessor source;
	final protected ImageProcessor target;
	final protected AtomicBoolean pleaseRepaint;
	final protected InvertibleCoordinateTransform transform;
	
	public PaintInvertibleCoordinateTransformThread(
			ImagePlus imp,
			ImageProcessor source,
			ImageProcessor target,
			AtomicBoolean pleaseRepaint,
			InvertibleCoordinateTransform transform )
	{
		this.imp = imp;
		this.source = source;
		this.target = target;
		this.pleaseRepaint = pleaseRepaint;
		this.transform = transform;
		this.setName( "PaintInvertibleCoordinateTransformThread" );
	}
	
	@Override
	public void run()
	{
		while ( !isInterrupted() )
		{
			try
			{
				if ( pleaseRepaint.compareAndSet( true, false ) )
				{
					for ( int y = 0; y < target.getHeight(); ++y )
					{
						for ( int x = 0; x < target.getWidth(); ++x )
						{
							float[] t = new float[]{ x, y };
							try
							{
								transform.applyInverseInPlace( t );
								target.putPixel( x, y, source.getPixel( ( int )t[ 0 ], ( int )t[ 1 ] ) );
							}
							catch ( NoninvertibleModelException e ){ Log.error( e ); }
						}
						imp.updateAndDraw();
					}
				}
				else
					synchronized ( this ){ wait(); }
			}
			catch ( InterruptedException e){ Thread.currentThread().interrupt(); }
		}
	}
}
