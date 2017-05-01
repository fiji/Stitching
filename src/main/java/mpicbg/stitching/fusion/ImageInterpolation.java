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

import net.imglib2.RandomAccessible;
import net.imglib2.RealRandomAccess;
import net.imglib2.RealRandomAccessible;
import net.imglib2.img.Img;
import net.imglib2.interpolation.InterpolatorFactory;
import net.imglib2.type.numeric.RealType;
import net.imglib2.view.Views;

/**
 * This class is necessary as it can create an {@link InterpolatorFactory} for
 * an {@link Img} even if hold it as {@code < ? extends RealType< ? > >}.
 * 
 * @author Stephan Preibisch
 * @param <T>
 */
public class ImageInterpolation< T extends RealType< T > > 
{
	final Img< T > image;
	final RealRandomAccessible< T > interpolated;
	final InterpolatorFactory< T, RandomAccessible< T > > interpolatorFactory;
	
	public ImageInterpolation( final Img< T > image, final InterpolatorFactory< T, RandomAccessible< T > > interpolatorFactory, final boolean mirror )
	{
		this.image = image;
		this.interpolatorFactory = interpolatorFactory;
		if ( mirror )
			this.interpolated = Views.interpolate( Views.extendMirrorSingle( image ), interpolatorFactory );
		else
			this.interpolated = Views.interpolate( Views.extendZero( image ), interpolatorFactory );
	}
	
	public Img< T > getImg() { return image; }
	public RealRandomAccess< T > createInterpolator() { return interpolated.realRandomAccess(); }
}
