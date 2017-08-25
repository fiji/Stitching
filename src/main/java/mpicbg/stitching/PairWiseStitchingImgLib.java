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

import fiji.stacks.Hyperstack_rearranger;
import ij.ImagePlus;
import ij.gui.Roi;
import net.imglib2.Cursor;
import net.imglib2.RandomAccess;
import net.imglib2.exception.IncompatibleTypeException;
import net.imglib2.img.Img;
import net.imglib2.img.ImgFactory;
import net.imglib2.img.display.imagej.ImageJFunctions;
import net.imglib2.realtransform.Translation;
import net.imglib2.realtransform.Translation3D;
import net.imglib2.type.numeric.RealType;
import net.imglib2.type.numeric.integer.UnsignedByteType;
import net.imglib2.type.numeric.integer.UnsignedShortType;
import net.imglib2.type.numeric.real.FloatType;
import net.imglib2.util.Pair;
import net.preibisch.mvrecon.Threads;
import net.preibisch.mvrecon.process.deconvolution.DeconViews;
import net.preibisch.mvrecon.process.fusion.FusionTools;
import net.preibisch.mvrecon.process.fusion.ImagePortion;

import java.util.ArrayList;
import java.util.Vector;
import java.util.concurrent.Callable;

import net.preibisch.stitcher.algorithm.PairwiseStitching;
import net.preibisch.stitcher.algorithm.PairwiseStitchingParameters;
import stitching.utils.Log;


/**
 * Pairwise Stitching of two ImagePlus using ImgLib1 and PhaseCorrelation.
 * It deals with aligning two slices (2d) or stacks (3d) having an arbitrary
 * amount of channels. If the ImagePlus contains several time-points it will 
 * only consider the first time-point as this requires global optimization of 
 * many independent 2d/3d &lt;-&gt; 2d/3d alignments.
 * 
 * @author Stephan Preibisch (stephan.preibisch@gmx.de)
 *
 */
public class PairWiseStitchingImgLib
{
	public static Pair< Translation, Double > stitchPairwise( final ImagePlus imp1, final ImagePlus imp2, Roi roi1, Roi roi2, final int timepoint1, final int timepoint2, final StitchingParameters params )
	{
		Pair< Translation, Double > result = null;
		roi1 = getOnlyRectangularRoi( roi1 );
		roi2 = getOnlyRectangularRoi( roi2 );

		// can both images be wrapped into imglib without copying
		final boolean canWrap = !StitchingParameters.alwaysCopy && canWrapIntoImgLib( imp1, roi1, params.channel1 ) && canWrapIntoImgLib( imp2, roi2, params.channel2 );

		//
		// the ugly but correct way into generic programming...
		//
		if ( canWrap )
		{
			if ( imp1.getType() == ImagePlus.GRAY32 )
			{
				final Img<FloatType> image1 = getWrappedImageFloat( imp1, params.channel1, timepoint1 );
				
				if ( imp2.getType() == ImagePlus.GRAY32 )
					result = performStitching( image1, getWrappedImageFloat( imp2, params.channel2, timepoint2 ), params );
				else if ( imp2.getType() == ImagePlus.GRAY16 )
					result = performStitching( image1, getWrappedImageUnsignedShort( imp2, params.channel2, timepoint2 ), params );
				else if ( imp2.getType() == ImagePlus.GRAY8 )
					result = performStitching( image1, getWrappedImageUnsignedByte( imp2, params.channel2, timepoint2 ), params );
				else
					Log.error( "Unknown image type: " + imp2.getType() );
			}
			else if ( imp1.getType() == ImagePlus.GRAY16 )
			{
				final Img<UnsignedShortType> image1 = getWrappedImageUnsignedShort( imp1, params.channel1, timepoint1 );
				
				if ( imp2.getType() == ImagePlus.GRAY32 )
					result = performStitching( image1, getWrappedImageFloat( imp2, params.channel2, timepoint2 ), params );
				else if ( imp2.getType() == ImagePlus.GRAY16 )
					result = performStitching( image1, getWrappedImageUnsignedShort( imp2, params.channel2, timepoint2 ), params );
				else if ( imp2.getType() == ImagePlus.GRAY8 )
					result = performStitching( image1, getWrappedImageUnsignedByte( imp2, params.channel2, timepoint2 ), params );
				else
					Log.error( "Unknown image type: " + imp2.getType() );
			} 
			else if ( imp1.getType() == ImagePlus.GRAY8 )
			{
				final Img<UnsignedByteType> image1 = getWrappedImageUnsignedByte( imp1, params.channel1, timepoint1 );
				
				if ( imp2.getType() == ImagePlus.GRAY32 )
					result = performStitching( image1, getWrappedImageFloat( imp2, params.channel2, timepoint2 ), params );
				else if ( imp2.getType() == ImagePlus.GRAY16 )
					result = performStitching( image1, getWrappedImageUnsignedShort( imp2, params.channel2, timepoint2 ), params );
				else if ( imp2.getType() == ImagePlus.GRAY8 )
					result = performStitching( image1, getWrappedImageUnsignedByte( imp2, params.channel2, timepoint2 ), params );
				else
					Log.error( "Unknown image type: " + imp2.getType() );
			}
			else
			{
				Log.error( "Unknown image type: " + imp1.getType() );
			}
		}
		else
		{
			ImgFactory< UnsignedByteType > imgFactoryByte = null;
			ImgFactory<UnsignedShortType> imgFactoryShort = null;
			ImgFactory<FloatType> imgFactoryFloat = null;

			try
			{
				imgFactoryByte = StitchingParameters.phaseCorrelationFactory.imgFactory( new UnsignedByteType() );
				imgFactoryShort = StitchingParameters.phaseCorrelationFactory.imgFactory( new UnsignedShortType() );
				imgFactoryFloat = StitchingParameters.phaseCorrelationFactory.imgFactory( new FloatType() );
			}
			catch ( IncompatibleTypeException e )
			{
				Log.error( "Could not instantiate ImgFactory: " + e );
				e.printStackTrace();
				return null;
			}

			if ( imp1.getType() == ImagePlus.GRAY32 )
			{
				final Img< FloatType > image1 = getImage( imp1, roi1, imgFactoryFloat, new FloatType(), params.channel1, timepoint1 );

				if ( imp2.getType() == ImagePlus.GRAY32 )
					result = performStitching( image1, getImage( imp2, roi2, imgFactoryFloat, new FloatType(), params.channel2, timepoint2 ), params );
				else if ( imp2.getType() == ImagePlus.GRAY16 )
					result = performStitching( image1, getImage( imp2, roi2, imgFactoryShort, new UnsignedShortType(), params.channel2, timepoint2 ), params );
				else if ( imp2.getType() == ImagePlus.GRAY8 )
					result = performStitching( image1, getImage( imp2, roi2, imgFactoryByte, new UnsignedByteType(), params.channel2, timepoint2 ), params );
				else
					Log.error( "Unknown image type: " + imp2.getType() );
			}
			else if ( imp1.getType() == ImagePlus.GRAY16 )
			{
				final Img< UnsignedShortType > image1 = getImage( imp1, roi1, imgFactoryShort, new UnsignedShortType(), params.channel1, timepoint1 );

				if ( imp2.getType() == ImagePlus.GRAY32 )
					result = performStitching( image1, getImage( imp2, roi2, imgFactoryFloat, new FloatType(), params.channel2, timepoint2 ), params );
				else if ( imp2.getType() == ImagePlus.GRAY16 )
					result = performStitching( image1, getImage( imp2, roi2, imgFactoryShort, new UnsignedShortType(), params.channel2, timepoint2 ), params );
				else if ( imp2.getType() == ImagePlus.GRAY8 )
					result = performStitching( image1, getImage( imp2, roi2, imgFactoryByte, new UnsignedByteType(), params.channel2, timepoint2 ), params );
				else
					Log.error( "Unknown image type: " + imp2.getType() );
			}
			else if ( imp1.getType() == ImagePlus.GRAY8 )
			{
				final Img< UnsignedByteType > image1 = getImage( imp1, roi1, imgFactoryByte, new UnsignedByteType(), params.channel1, timepoint1 );

				if ( imp2.getType() == ImagePlus.GRAY32 )
					result = performStitching( image1, getImage( imp2, roi2, imgFactoryFloat, new FloatType(), params.channel2, timepoint2 ), params );
				else if ( imp2.getType() == ImagePlus.GRAY16 )
					result = performStitching( image1, getImage( imp2, roi2, imgFactoryShort, new UnsignedShortType(), params.channel2, timepoint2 ), params );
				else if ( imp2.getType() == ImagePlus.GRAY8 )
					result = performStitching( image1, getImage( imp2, roi2, imgFactoryByte, new UnsignedByteType(), params.channel2, timepoint2 ), params );
				else
					Log.error( "Unknown image type: " + imp2.getType() );
			}
			else
			{
				Log.error( "Unknown image type: " + imp1.getType() );			
			}
		}
		if ( result == null )
		{
			Log.error( "Pairwise stitching failed." );
			return null;
		}
		
		// add the offset to the shift
		if ( roi2 != null )
		{
			final Translation t = result.getA();

			t.set( t.getTranslation( 0 ) - roi2.getBounds().x, 0 );
			t.set( t.getTranslation( 1 ) - roi2.getBounds().y, 1 );
		}

		if ( roi1 != null )
		{
			final Translation t = result.getA();

			t.set( t.getTranslation( 0 ) + roi1.getBounds().x, 0 );
			t.set( t.getTranslation( 1 ) + roi1.getBounds().y, 1 );
		}

		return result;
	}

	public static < T extends RealType<T>, S extends RealType<S> > Pair< Translation, Double > performStitching( final Img<T> img1, final Img<S> img2, final StitchingParameters oldParams )
	{
		if ( img1 == null )
		{
			Log.error( "Image 1 could not be wrapped." );
			return null;
		}
		else if ( img2 == null )
		{
			Log.error( "Image 2 could not be wrapped." );
			return null;
		}
		else if ( oldParams == null )
		{
			Log.error( "Parameters are null." );
			return null;
		}

		final PairwiseStitchingParameters params = new PairwiseStitchingParameters( 0.01, oldParams.checkPeaks, oldParams.subpixelAccuracy, false );

		final Translation3D t1 = new Translation3D( 0, 0, 0 );
		final Translation3D t2 = new Translation3D( 0, 0, 0 );

		return PairwiseStitching.getShift( img1, img2, t1, t2, params, DeconViews.createExecutorService() );
	}

	/**
	 * return an {@code Image<T>} as input for the PhaseCorrelation.
	 * 
	 * @param imp - the {@link ImagePlus}
	 * @param imgFactory - the {@link ImageFactory} defining wher to put it into
	 * @param type - an instance of the type to use
	 * @param channel - which channel (if channel=0 means average all channels)
	 * @param timepoint - which timepoint
	 * 
	 * @return - the {@link Image} or null if it was not an ImagePlus.GRAY8, ImagePlus.GRAY16 or ImagePlus.GRAY32
	 */
	public static < T extends RealType<T> > Img<T> getImage( final ImagePlus imp, Roi roi, final ImgFactory<T> imgFactory, final T type, final int channel, final int timepoint )
	{
		// first test the roi
		roi = getOnlyRectangularRoi( roi );

		// how many dimensions?
		final int numDimensions;
		if ( imp.getNSlices() > 1 )
			numDimensions = 3;
		else
			numDimensions = 2;
		
		// the size of the image
		final long[] size = new long[ numDimensions ];
		final long[] offset = new long[ numDimensions ];
		
		if ( roi == null )
		{
			size[ 0 ] = imp.getWidth();
			size[ 1 ] = imp.getHeight();
			
			if ( numDimensions == 3 )
				size[ 2 ] = imp.getNSlices();
		}
		else
		{
			size[ 0 ] = roi.getBounds().width;
			size[ 1 ] = roi.getBounds().height;

			offset[ 0 ] = roi.getBounds().x;
			offset[ 1 ] = roi.getBounds().y;
			
			if ( numDimensions == 3 )
				size[ 2 ] = imp.getNSlices();
		}
		
		// create the Image
		final Img<T> img = imgFactory.create( size, type );
		final boolean success;
		
		// copy the content
		if ( channel == 0 )
		{
			// we need to average all channels
			success = averageAllChannels( img, offset, imp, timepoint );
		}
		else
		{
			// otherwise only copy one channel
			success = fillInChannel( img, offset, imp, channel, timepoint );
		}

		if ( success )
			return img;
		else
			return null;
	}
	
	/**
	 * Averages all channels into the target image. The size is given by the dimensions of the target image,
	 * the offset (if applicable) is given by an extra field
	 * 
	 * @param target - the target Image
	 * @param offset - the offset of the area (might be [0,0] or [0,0,0])
	 * @param imp - the input ImagePlus
	 * @param timepoint - for which timepoint
	 * 
	 * @return true if successful, false if the ImagePlus type was unknow
	 */
	public static < T extends RealType< T > > boolean averageAllChannels( final Img< T > target, final long[] offset, final ImagePlus imp, final int timepoint )
	{
		final int numChannels = imp.getNChannels();
		
		if ( imp.getType() == ImagePlus.GRAY8 )
		{
			final ArrayList< Img< UnsignedByteType > > images = new ArrayList<>();

			// first get wrapped instances of all channels
			for ( int c = 1; c <= numChannels; ++c )
				images.add( getWrappedImageUnsignedByte( imp, c, timepoint ) );

			averageAllChannels( target, images, offset );
			return true;
		}
		else if ( imp.getType() == ImagePlus.GRAY16 )
		{
			final ArrayList< Img< UnsignedShortType > > images = new ArrayList<>();

			// first get wrapped instances of all channels
			for ( int c = 1; c <= numChannels; ++c )
				images.add( getWrappedImageUnsignedShort( imp, c, timepoint ) );

			averageAllChannels( target, images, offset );
			return true;
		}
		else if ( imp.getType() == ImagePlus.GRAY32 )
		{
			final ArrayList< Img< FloatType > > images = new ArrayList<>();

			// first get wrapped instances of all channels
			for ( int c = 1; c <= numChannels; ++c )
				images.add( getWrappedImageFloat( imp, c, timepoint ) );
			
			averageAllChannels( target, images, offset );
			return true;
		}
		else
		{
			Log.error( "Unknow image type: " + imp.getType() );
			return false;
		}
	}

	/**
	 * Averages all channels into the target image. The size is given by the dimensions of the target image,
	 * the offset (if applicable) is given by an extra field
	 * 
	 * @param target - the target Image
	 * @param offset - the offset of the area (might be [0,0] or [0,0,0])
	 * @param imp - the input ImagePlus
	 * @param timepoint - for which timepoint
	 * 
	 * @return true if successful, false if the ImagePlus type was unknow
	 */
	public static < T extends RealType< T > > boolean fillInChannel( final Img< T > target, final long[] offset, final ImagePlus imp, final int channel, final int timepoint )
	{
		if ( imp.getType() == ImagePlus.GRAY8 )
		{
			final ArrayList< Img< UnsignedByteType > > images = new ArrayList<>();

			// first get wrapped instances of all channels
			images.add( getWrappedImageUnsignedByte( imp, channel, timepoint ) );

			averageAllChannels( target, images, offset );
			return true;
		}
		else if ( imp.getType() == ImagePlus.GRAY16 )
		{
			final ArrayList< Img< UnsignedShortType > > images = new ArrayList<>();

			// first get wrapped instances of all channels
			images.add( getWrappedImageUnsignedShort( imp, channel, timepoint ) );

			averageAllChannels( target, images, offset );
			return true;
		}
		else if ( imp.getType() == ImagePlus.GRAY32 )
		{
			final ArrayList< Img< FloatType > > images = new ArrayList<>();

			// first get wrapped instances of all channels
			images.add( getWrappedImageFloat( imp, channel, timepoint ) );
			
			averageAllChannels( target, images, offset );
			return true;
		}
		else
		{
			Log.error( "Unknow image type: " + imp.getType() );
			return false;
		}
	}

	/**
	 * Averages all channels into the target image. The size is given by the dimensions of the target image,
	 * the offset (if applicable) is given by an extra field
	 * 
	 * @param target - the target Image
	 * @param offset - the offset of the area (might be [0,0] or [0,0,0])
	 * @param sources - a list of input Images
	 */
	protected static < T extends RealType< T >, S extends RealType< S > > void averageAllChannels( final Img< T > target, final ArrayList< Img< S > > sources, final long[] offset )
	{
		// get the major numbers
		final int numDimensions = target.numDimensions();
		final float numImages = sources.size();
		long imageSize = target.dimension( 0 );
		
		for ( int d = 1; d < target.numDimensions(); ++d )
			imageSize *= target.dimension( d );

		// run multithreaded
		final Vector< ImagePortion > portions = FusionTools.divideIntoPortions( imageSize );
		final ArrayList< Callable< Void > > tasks = new ArrayList< Callable< Void > >();

		for ( final ImagePortion portion : portions )
		{
			tasks.add( new Callable< Void >()
			{
				@Override
				public Void call() throws Exception
				{
					// get chunk of pixels to process
					final long startPos = portion.getStartPosition();
					final long loopSize = portion.getLoopSize();

					// the cursor for the output
					final Cursor< T > targetCursor =  target.localizingCursor();

					// the input cursors
					final ArrayList< RandomAccess< S > > sourceCursors = new ArrayList<> ();

					for ( final Img< S > source : sources )
						sourceCursors.add( source.randomAccess() );

					// temporary array
					final int[] location = new int[ numDimensions ]; 

					// move to the starting position of the current thread
					targetCursor.jumpFwd( startPos );

					// do as many pixels as wanted by this thread
					for ( long j = 0; j < loopSize; ++j )
					{
						targetCursor.fwd();
						targetCursor.localize( location );

						for ( int d = 0; d < numDimensions; ++d )
							location[ d ] += offset[ d ];

						float sum = 0;

						for ( final RandomAccess< S > sourceCursor : sourceCursors )
						{
							sourceCursor.setPosition( location );
							sum += sourceCursor.get().getRealFloat();
						}

						targetCursor.get().setReal( sum / numImages );
					}
					return null;
				}
			});
		}

		FusionTools.execTasks( tasks, Threads.numThreads(), "average image" );
	}

	/**
	 * return an {@link Image} of {@link UnsignedByteType} as input for the PhaseCorrelation. If no rectangular roi
	 * is selected, it will only wrap the existing ImagePlus!
	 * 
	 * @param imp - the {@link ImagePlus}
	 * @param channel - which channel (if channel=0 means average all channels)
	 * @param timepoint - which timepoint
	 * 
	 * @return - the {@link Image} or null if it was not an ImagePlus.GRAY8 or if channel = 0
	 */
	public static Img<UnsignedByteType> getWrappedImageUnsignedByte( final ImagePlus imp, final int channel, final int timepoint )
	{
		if ( channel == 0 || imp.getType() != ImagePlus.GRAY8 )
			return null;
		return ImageJFunctions.wrapByte( Hyperstack_rearranger.getImageChunk( imp, channel, timepoint ) );
	}

	/**
	 * return an {@link Image} of {@link UnsignedShortType} as input for the PhaseCorrelation. If no rectangular roi
	 * is selected, it will only wrap the existing ImagePlus!
	 * 
	 * @param imp - the {@link ImagePlus}
	 * @param channel - which channel (if channel=0 means average all channels)
	 * @param timepoint - which timepoint
	 * 
	 * @return - the {@link Image} or null if it was not an ImagePlus.GRAY16 or if channel = 0
	 */
	public static Img<UnsignedShortType> getWrappedImageUnsignedShort( final ImagePlus imp, final int channel, final int timepoint )
	{
		if ( channel == 0 || imp.getType() != ImagePlus.GRAY16 )
			return null;
		return ImageJFunctions.wrapShort( Hyperstack_rearranger.getImageChunk( imp, channel, timepoint ) );
	}

	/**
	 * return an {@link Image} of {@link FloatType} as input for the PhaseCorrelation. If no rectangular roi
	 * is selected, it will only wrap the existing ImagePlus!
	 * 
	 * @param imp - the {@link ImagePlus}
	 * @param channel - which channel (if channel=0 means average all channels)
	 * @param timepoint - which timepoint
	 * 
	 * @return - the {@link Image} or null if it was not an ImagePlus.GRAY32 or if channel = 0
	 */
	public static Img<FloatType> getWrappedImageFloat( final ImagePlus imp, final int channel, final int timepoint )
	{
		if ( channel == 0 || imp.getType() != ImagePlus.GRAY32 )
			return null;
		return ImageJFunctions.wrapFloat( Hyperstack_rearranger.getImageChunk( imp, channel, timepoint ) );
	}

	/**
	 * Determines if this imageplus with these parameters can be wrapped directly into an {@code Image<T>}.
	 * This is important, because if we would wrap the first but not the second image, they would
	 * have different {@link ImageFactory}s
	 * 
	 * @param imp - the ImagePlus
	 * @param roi
	 * @param channel - which channel (if channel=0 means average all channels)
	 * 
	 * @return true if it can be wrapped, otherwise false
	 */
	public static boolean canWrapIntoImgLib( final ImagePlus imp, Roi roi, final int channel )
	{
		// first test the roi
		roi = getOnlyRectangularRoi( roi );
		
		return roi == null && channel > 0;
	}

	protected static Roi getOnlyRectangularRoi( Roi roi )
	{
		// we can only do rectangular rois
		if ( roi != null && roi.getType() != Roi.RECTANGLE )
			return null;
		return roi;
	}

	/*
	protected static Roi getOnlyRectangularRoi( final ImagePlus imp )
	{
		Roi roi = imp.getRoi();
		
		// we can only do rectangular rois
		if ( roi != null && roi.getType() != Roi.RECTANGLE )
		{
			Log.warn( "roi for " + imp.getTitle() + " is not a rectangle, we have to ignore it." );
			roi = null;
		}

		return roi;
	}
	*/
}
