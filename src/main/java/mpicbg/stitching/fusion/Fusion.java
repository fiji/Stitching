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

import fiji.stacks.Hyperstack_rearranger;
import ij.CompositeImage;
import ij.IJ;
import ij.ImageJ;
import ij.ImagePlus;
import ij.ImageStack;
import ij.io.FileSaver;

import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.Stack;
import java.util.Vector;
import java.util.concurrent.atomic.AtomicInteger;

import mpicbg.imglib.cursor.LocalizableByDimCursor;
import mpicbg.models.InvertibleBoundable;
import mpicbg.models.NoninvertibleModelException;
import net.imglib2.Cursor;
import net.imglib2.RandomAccess;
import net.imglib2.RandomAccessible;
import net.imglib2.RealRandomAccess;
import net.imglib2.exception.ImgLibException;
import net.imglib2.img.Img;
import net.imglib2.img.ImgFactory;
import net.imglib2.img.array.ArrayImgFactory;
import net.imglib2.img.display.imagej.ImageJFunctions;
import net.imglib2.img.imageplus.ImagePlusImg;
import net.imglib2.img.imageplus.ImagePlusImgFactory;
import net.imglib2.interpolation.InterpolatorFactory;
import net.imglib2.interpolation.randomaccess.NLinearInterpolatorFactory;
import net.imglib2.interpolation.randomaccess.NearestNeighborInterpolatorFactory;
import net.imglib2.multithreading.Chunk;
import net.imglib2.multithreading.SimpleMultiThreading;
import net.imglib2.type.NativeType;
import net.imglib2.type.numeric.RealType;
import net.imglib2.type.numeric.integer.UnsignedByteType;
import net.imglib2.type.numeric.integer.UnsignedShortType;
import net.imglib2.type.numeric.real.FloatType;
import stitching.utils.CompositeImageFixer;
import stitching.utils.Log;

/**
 * Manages the fusion for all types except the overlayfusion
 * 
 * @author Stephan Preibisch (stephan.preibisch@gmx.de)
 *
 */
public class Fusion 
{
	public static long redrawDelay = 500;

	/**
	 * 
	 * @param targetType
	 * @param images
	 * @param models
	 * @param dimensionality
	 * @param subpixelResolution - if there is no subpixel resolution, we do not need to convert to float as no interpolation is necessary, we can compute everything with RealType
	 */
	public static < T extends RealType< T > & NativeType< T > > ImagePlus fuse( final T targetType, final ArrayList< ImagePlus > images, final ArrayList< InvertibleBoundable > models, 
			final int dimensionality, final boolean subpixelResolution, final int fusionType, final String outputDirectory, final boolean noOverlap, final boolean ignoreZeroValues, final boolean displayImages )
	{
		// first we need to estimate the boundaries of the new image
		final double[] offset = new double[ dimensionality ];
		final int[] size = new int[ dimensionality ];
		final int numTimePoints = images.get( 0 ).getNFrames();
		final int numChannels = images.get( 0 ).getNChannels();
		
		estimateBounds( offset, size, images, models, dimensionality );
		
		if ( subpixelResolution )
			for ( int d = 0; d < size.length; ++d )
				++size[ d ];
		
		// for output
		final ImgFactory<T> f = new ImagePlusImgFactory<T>();
		
		// the final composite
		final ImageStack stack;
		
		// there is no output if we write to disk
		if ( outputDirectory == null )
			stack = new ImageStack( size[ 0 ], size[ 1 ] );
		else
			stack = null;

		//"Overlay into composite image"
		for ( int t = 1; t <= numTimePoints; ++t )
		{
			for ( int c = 1; c <= numChannels; ++c )
			{
				IJ.showStatus("Fusing time point: " + t + " of " + numTimePoints + ", " +
					"channel: " + c + " of " + numChannels + "...");
				// create the 2d/3d target image for the current channel and timepoint 
				final Img< T > out;
				
				// we just create one slice if we write to disk
				if ( outputDirectory == null )
					out = f.create( size, targetType );
				else
					out = f.create( new int[] { size[ 0 ], size[ 1 ] }, targetType ); // just create a slice

				// init the fusion
				PixelFusion fusion = null;
				
				if ( fusionType == 1 )
				{
					if ( ignoreZeroValues )
						fusion = new AveragePixelFusionIgnoreZero();
					else
						fusion = new AveragePixelFusion();
				}
				else if ( fusionType == 2 )
				{
					if ( ignoreZeroValues )
						fusion = new MedianPixelFusionIgnoreZero();
					else
						fusion = new MedianPixelFusion();
				}
				else if ( fusionType == 3 )
				{
					if ( ignoreZeroValues )
						fusion = new MaxPixelFusionIgnoreZero();
					else
						fusion = new MaxPixelFusion();
				}
				else if ( fusionType == 4 )
				{
					if ( ignoreZeroValues )
						fusion = new MinPixelFusionIgnoreZero();
					else
						fusion = new MinPixelFusion();	
				}
				else if ( fusionType == 5 )
				{
					fusion = new OverlapFusion();
				}
				
				// extract the complete blockdata
				if ( subpixelResolution )
				{
					final ArrayList< ImageInterpolation< FloatType > > blockData = new ArrayList< ImageInterpolation< FloatType > >();

					// for linear interpolation we want to mirror, otherwise we get black areas at the first and last pixel of each image
					final InterpolatorFactory< FloatType, RandomAccessible< FloatType > > interpolatorFactory = new NLinearInterpolatorFactory< FloatType >();// new OutOfBoundsStrategyMirrorFactory<FloatType>() );
					
					for ( final ImagePlus imp : images )
						blockData.add( new ImageInterpolation<FloatType>( ImageJFunctions.convertFloat( Hyperstack_rearranger.getImageChunk( imp, c, t ) ), interpolatorFactory, true ) );
					
					// init blending with the images
					if ( fusionType == 0 )
					{
						if ( ignoreZeroValues )
							fusion = new BlendingPixelFusionIgnoreZero( blockData );
						else
							fusion = new BlendingPixelFusion( blockData );
					}
					
					if ( outputDirectory == null )
					{
						fuseBlock( out, blockData, offset, models, fusion, displayImages );
					}
					else
					{
						final int numSlices;
						
						if ( dimensionality == 2 )
							numSlices = 1;
						else
							numSlices = size[ 2 ];
						
						writeBlock( out, numSlices, t, numTimePoints, c, numChannels, blockData, offset, models, fusion, outputDirectory );
					}
				}
				else
				{
					// can be a mixture of different RealTypes
					final ArrayList< ImageInterpolation< ? extends RealType< ? > > > blockData = new ArrayList< ImageInterpolation< ? extends RealType< ? > > >();

					final InterpolatorFactory< FloatType, RandomAccessible< FloatType > > interpolatorFactoryFloat = new NearestNeighborInterpolatorFactory< FloatType >();// new OutOfBoundsStrategyValueFactory<FloatType>() );
					final InterpolatorFactory< UnsignedShortType, RandomAccessible< UnsignedShortType > > interpolatorFactoryShort = new NearestNeighborInterpolatorFactory< UnsignedShortType >();// new OutOfBoundsStrategyValueFactory<UnsignedShortType>() );
					final InterpolatorFactory< UnsignedByteType, RandomAccessible< UnsignedByteType > > interpolatorFactoryByte = new NearestNeighborInterpolatorFactory< UnsignedByteType >();// new OutOfBoundsStrategyValueFactory<UnsignedByteType>() );

					for ( final ImagePlus imp : images )
					{
						if ( imp.getType() == ImagePlus.GRAY32 )
							blockData.add( new ImageInterpolation<FloatType>( ImageJFunctions.wrapFloat( Hyperstack_rearranger.getImageChunk( imp, c, t ) ), interpolatorFactoryFloat, false ) );
						else if ( imp.getType() == ImagePlus.GRAY16 )
							blockData.add( new ImageInterpolation<UnsignedShortType>( ImageJFunctions.wrapShort( Hyperstack_rearranger.getImageChunk( imp, c, t ) ), interpolatorFactoryShort, false ) );
						else
							blockData.add( new ImageInterpolation<UnsignedByteType>( ImageJFunctions.wrapByte( Hyperstack_rearranger.getImageChunk( imp, c, t ) ), interpolatorFactoryByte, false ) );
					}
					
					// init blending with the images
					if ( fusionType == 0 )
					{
						if ( ignoreZeroValues )
							fusion = new BlendingPixelFusionIgnoreZero( blockData );
						else
							fusion = new BlendingPixelFusion( blockData );					
					}
					
					if ( outputDirectory == null )
					{
						if ( noOverlap )
							fuseBlockNoOverlap( out, blockData, offset, models, displayImages );
						else
							fuseBlock( out, blockData, offset, models, fusion, displayImages );
					}
					else
					{
						final int numSlices;
						
						if ( dimensionality == 2 )
							numSlices = 1;
						else
							numSlices = size[ 2 ];
						
						writeBlock( out, numSlices, t, numTimePoints, c, numChannels, blockData, offset, models, fusion, outputDirectory );
					}
				}
				
				// add to stack
				try 
				{
					if ( stack != null )
					{
						final ImagePlus outImp = ((ImagePlusImg<?, ?>)out).getImagePlus();
						for ( int z = 1; z <= out.dimension( 2 ); ++z )
							stack.addSlice( "", outImp.getStack().getProcessor( z ) );
					}
				} 
				catch (ImgLibException e) 
				{
					Log.error( "Output image has no ImageJ type: " + e );
				}				
			}
		}

		IJ.showStatus( "Fusion complete." );
		
		// reset the progress bar
		IJ.showProgress( 1.01 );

		// has been written to disk ...
		if ( stack == null )
			return null;
		
		//convertXYZCT ...
		ImagePlus result = new ImagePlus( "", stack );
		
		// numchannels, z-slices, timepoints (but right now the order is still XYZCT)
		if ( dimensionality == 3 )
		{
			result.setDimensions( size[ 2 ], numChannels, numTimePoints );
			result = OverlayFusion.switchZCinXYCZT( result );
			return CompositeImageFixer.makeComposite( result, CompositeImage.COMPOSITE );
		}
		//Log.info( "ch: " + imp.getNChannels() );
		//Log.info( "slices: " + imp.getNSlices() );
		//Log.info( "frames: " + imp.getNFrames() );
		result.setDimensions( numChannels, 1, numTimePoints );
		
		if ( numChannels > 1 || numTimePoints > 1 )
			return CompositeImageFixer.makeComposite( result, CompositeImage.COMPOSITE );
		return result;
	}
	
	/**
	 * Fuse one slice/volume (one channel)
	 * 
	 * @param output - same the type of the ImagePlus input
	 * @param input - FloatType, because of Interpolation that needs to be done
	 * @param transform - the transformation
	 */
	protected static <T extends RealType<T>> void fuseBlock( final Img<T> output, final ArrayList< ? extends ImageInterpolation< ? extends RealType< ? > > > input, final double[] offset, 
			final ArrayList< InvertibleBoundable > transform, final PixelFusion fusion, final boolean displayFusion )
	{
		final int numDimensions = output.numDimensions();
		final int numImages = input.size();
		long size = output.dimension( 0 );

		for (int d = 1; d < output.numDimensions(); ++d) {
			size *= output.dimension(d);
		}

		final List<ClassifiedRegion> tiles =
			buildTileList(numImages, numDimensions, transform, input, offset);

		IJ.showProgress( 0 );

		final ImagePlus[] fusionImp = new ImagePlus[1];

		if (displayFusion) {
			try {
				fusionImp[0] = ((ImagePlusImg<?, ?>) output).getImagePlus();
				fusionImp[0].setTitle("fusing...");
				fusionImp[0].show();
			}
			catch (ImgLibException e) {
				Log.error("Output image has no ImageJ type: " + e);
			}
		}

    final Thread[] threads = SimpleMultiThreading.newThreads();
		final TileProcessor<T>[] processors = new TileProcessor[threads.length];
		final List<ArrayList<RealRandomAccess<? extends RealType<?>>>> interpolators =
				new ArrayList<ArrayList<RealRandomAccess<? extends RealType<?>>>>();
		final long positionsPerThread = size / threads.length;

		// These fields need to be used within each thread, but modified from
    // the outer loop. Thus the use of arrays/vectors.
		final int[] count = new int[1]; // positions processed
		final ClassifiedRegion[] currentTile = new ClassifiedRegion[1]; // current region being processed
		final Vector<Chunk> threadChunks = new Vector<Chunk>(); // work divisions for current region
		final int[] loopDim = new int[1]; // Dimension to split up work on

		// Initialize the TileProcessors. These will be reused by each thread.
		for (int i = 0; i < threads.length; ++i) {
			processors[i] =
				new TileProcessor<T>(i, interpolators, input, threadChunks, numImages,
					output, fusion, currentTile, transform, fusionImp, count,
					positionsPerThread, offset, loopDim);
		}

		// Process each tile
		for (int tileIndex = 0; tileIndex < tiles.size(); tileIndex++) {
			currentTile[0] = tiles.get(tileIndex);

			// Initialize each thread for this tile
			for (int threadID = 0; threadID < threads.length; ++threadID) {
				threads[threadID] = new Thread(processors[threadID]);
			}

			// Decide which dimension to use to split up work for each thread.
			// We want to pick the largest dimension as this gives us the best chance
			// of evenly dividing work when using multiple threads
			int dimensionSize = -1;
			for (int d=0; d<currentTile[0].size(); d++) {
				int tmpSize = currentTile[0].get(d).max() - currentTile[0].get(d).min() + 1;
				if (tmpSize > dimensionSize) {
					dimensionSize = tmpSize;
					loopDim[0] = d;
				}
			}

			// Specify the loop ranges for each thread
			threadChunks.clear();
			threadChunks.addAll(SimpleMultiThreading.divideIntoChunks(dimensionSize,
				threads.length));

			// Start the work for this tile
			SimpleMultiThreading.startAndJoin(threads);
		}

		if (fusionImp[0] != null) fusionImp[0].hide();
	}

	/**
	 * Helper method to generate a list of all non-overlapping tiles. The
	 * dimensions and position of each tile are based on the input and offset
	 * parameters. From these, all overlaps are computed, and ultimately a list of
	 * {@link ClassifiedRegion}s is created such that no region overlaps. Each
	 * region is classified based on what source images overlapped with it.
	 */
	private static List<ClassifiedRegion> buildTileList(int numImages,
		int numDimensions, ArrayList<InvertibleBoundable> transform,
		ArrayList<? extends ImageInterpolation<? extends RealType<?>>> input, double[] offset)
	{
		Stack<ClassifiedRegion> rawTiles = new Stack<ClassifiedRegion>();

		for ( int i = 0; i < numImages; ++i ){
				final double[] min = new double[ numDimensions ];
				transform.get(i).applyInPlace(min);
				ClassifiedRegion shape = new ClassifiedRegion(numDimensions);
				shape.addClass(i);
			for ( int d = 0; d < numDimensions; ++d ) {
				min[d] -= offset[d];
				// Sets each interval to the smallest possible, by rounding the min up and the max down
				Interval ival =
					new Interval((int) Math.ceil(min[d]), (int) Math.floor(min[d] +
						input.get(i).getImg().dimension(d) - 1));
				// Build our list of positions
				shape.set(ival, d);
			}
			rawTiles.push(shape);
		}

		// Set of placed tiles. Goal is to move all of the known positions
		// to this set, creating new regions as needed such that there is no
		// overlap between regions. Then use this set to drive iteration and
		// fusion.
		final Set<ClassifiedRegion> placedTiles = new HashSet<ClassifiedRegion>();

		// Process each position. We need to look through all placed regions and
		// if we find an intersection, create a new set of regions, add them
		// to the appropriate sets, and continue
		while (!rawTiles.isEmpty()) {
			// Get the next tile to process
			ClassifiedRegion queryTile = rawTiles.pop();
			ClassifiedRegion toRemove = null;
			//TODO instead of iterating we really should use a look up tree structure, as this
			// is still quite slow with larger datasets.
			// Iterate over all placed shapes.
			for (ClassifiedRegion placedTile : placedTiles) {
				if (queryTile.intersects(placedTile)) {
					// can't remove while iterating
					toRemove = placedTile;
					// The first time we find an overlapping between tiles, we split the
					// two tiles into tile components and place them in the appropriate
					// lists. Then quit this loop (as our query shape is no longer valid)
					// and continue the process
					splitOverlappingRegions(placedTiles, rawTiles, queryTile, placedTile);
					break;
				}
			}
			// If we found an intersection, the query tile and placed tile were broken
			// down and should be discarded
			if (toRemove != null) {
				placedTiles.remove(toRemove);
			}
			// No intersections found, so just place the tile and continue.
			else {
				placedTiles.add(queryTile);
			}
		}
		return new ArrayList<ClassifiedRegion>(placedTiles);
	}

	/**
	 * Takes two overlapping regions and deconstructs them into a set of non-overlapping regions.
	 * Each resulting region gains the classification(s) of its parent(s). Parents are differentiated
	 * by "query" and "placed" - where "placed" is assumed to come from the {@code placedTiles} set,
	 * and thus will not overlap with any other tile in that set. The "query" tile is what we are
	 * currently investigating for overlap, so any children of the query tile must be added back to
	 * the set of tiles that will be used for further overlap checks.
	 */
	private static void splitOverlappingRegions(Set<ClassifiedRegion> placedTiles,
		Stack<ClassifiedRegion> rawTiles, ClassifiedRegion queryTile, ClassifiedRegion placedTile)
	{
		// Tiles are different but overlapping. So we need to identify the start and end points of
		// all potential sub tiles, for each dimension.
		List<Interval>[] allIntervals = new List[queryTile.size()];
		for (int i=0; i<allIntervals.length; i++) {
			List<Interval> intervals = new ArrayList<Interval>();
			List<Integer> points = new ArrayList<Integer>();
			points.add(queryTile.get(i).min());
			points.add(queryTile.get(i).max());
			points.add(placedTile.get(i).min());
			points.add(placedTile.get(i).max());
			// Which tiles the points belong to doesn't matter. We just need to make intervals of the adjacent points
			Collections.sort(points);

			if (points.get(0).equals(points.get(1))) {
				// First two points are equal
				if (points.get(2).equals(points.get(3))) {
					// Last two points are equal. Intervals overlap exactly
					intervals.add(new Interval(points.get(0), points.get(2)));
				}
				else if (points.get(1).equals(points.get(2))) {
					// First three points are equal, but last one is different. Single point shares edge
					intervals.add(new Interval(points.get(0)));
					intervals.add(new Interval(points.get(0) + 1, points.get(3)));
				}
				else {
					// Overlap at first point, other two are unique (partial overlap with shared edge)
					intervals.add(new Interval(points.get(0), points.get(2)));
					intervals.add(new Interval(points.get(2) + 1, points.get(3)));
				}
			}
			else if (points.get(1).equals(points.get(2))) {
				if (points.get(2).equals(points.get(3))) {
					// Last three points are equal, first is different. Single point shares edge
					intervals.add(new Interval(points.get(3)));
					intervals.add(new Interval(points.get(0), points.get(3) - 1));
				}
				else {
					// Mid 2 points are equal. Two distinct tiles share an edge.
					intervals.add(new Interval(points.get(0), points.get(1)  - 1));
					intervals.add(new Interval(points.get(1)));
					intervals.add(new Interval(points.get(1) + 1, points.get(3)));
				}
			}
			else if (points.get(2).equals(points.get(3))) {
				// Last 2 points are equal, other two are unique (partial overlap with shared edge)
				intervals.add(new Interval(points.get(0), points.get(1) - 1));
				intervals.add(new Interval(points.get(1), points.get(3)));
			}
			else {
				// Overlap, zero shared edges
				intervals.add(new Interval(points.get(0), points.get(1) - 1));
				intervals.add(new Interval(points.get(1), points.get(2)));
				intervals.add(new Interval(points.get(2) + 1, points.get(3)));
			}

			allIntervals[i] = intervals;
		}

		// Now that we know our potential intervals, we need to enumerate a list of all regions constructable from those intervals
		int[] pos = new int[allIntervals.length];

		// Build and place all regions
		buildAllRegions(allIntervals, pos, 0, queryTile, placedTile,
			placedTiles, rawTiles);
	}

	/**
	 * Helper class to perform tile processing (iteration through a region, fusion
	 * of input pixels, and population of output pixels). Array fields allow one
	 * to be created per thread, then have values modified externally.
	 */
	private static class TileProcessor<T extends RealType<T>> implements Runnable {

			private int loopOffset;
			private int loopSize;
			private final int[] loopDim;

			private Vector<Chunk> threadChunks;
			private final int threadNumber; // thread id
			private ClassifiedRegion[] currentTile;
			private ArrayList<InvertibleBoundable> transform;
			private ImagePlus[] fusionImp;
			private int[] count;
			private double positionsPerThread;
			private double[] offset;
			private long[] lastDraw = new long[1];
			private final ArrayList<RealRandomAccess<? extends RealType<?>>> in;
			private final double[][] inPos;
			private final PixelFusion myFusion;
			private final RandomAccess<T> out;

		public TileProcessor(int threadNumber,
			List<ArrayList<RealRandomAccess<? extends RealType<?>>>> interpolators,
			ArrayList<? extends ImageInterpolation<? extends RealType<?>>> input,
			Vector<Chunk> threadChunks, int numImages, Img<T> output,
			PixelFusion fusion, ClassifiedRegion[] currentTile,
			ArrayList<InvertibleBoundable> transform, ImagePlus[] fusionImp,
			int[] count, double positionsPerThread, double[] offset, int[] loopDim)
		{
			this.threadNumber = threadNumber;
			this.threadChunks = threadChunks;
			this.currentTile = currentTile;
			this.transform = transform;
			this.fusionImp = fusionImp;
			this.count = count;
			this.positionsPerThread = positionsPerThread;
			this.offset = offset;
			this.loopDim = loopDim;

			in = getThreadInterpolators(interpolators, threadNumber, input, numImages);
			inPos = new double[numImages][output.numDimensions()];
			myFusion = fusion.copy();
			out = output.randomAccess();
		}

			@Override
			public void run() {

				// get chunk of pixels to process
				final Chunk myChunk = threadChunks.get(threadNumber);
				loopOffset = (int) myChunk.getStartPosition();
				loopSize = (int) myChunk.getLoopSize();

				try {
					// Process each tile index assigned to this thread
					// For each position in this tile, fuse its pixels across the
					// appropriate images
					// NB: recursion is necessary because there are an arbitrary
					// number of dimensions in the tile
					processTile(currentTile[0], 0, myFusion, transform, in, out, inPos,
						threadNumber, count, lastDraw, fusionImp[0]);
				}
				catch (NoninvertibleModelException e) {
					Log.error("Cannot invert model, qutting.");
					return;
				}

			}

			/**
		 * Helper method to lazily initialize the input image interpolators,
		 * creating one list per thread.
		 */
			private
				ArrayList<RealRandomAccess<? extends RealType<?>>>
				getThreadInterpolators(
					List<ArrayList<RealRandomAccess<? extends RealType<?>>>> interpolators,
					int threadNumber,
					ArrayList<? extends ImageInterpolation<? extends RealType<?>>> input,
					int numImages)
			{
				ArrayList<RealRandomAccess<? extends RealType<?>>> in = null;
				if (threadNumber >= interpolators.size()) {
					in = new ArrayList<RealRandomAccess<? extends RealType<?>>>();
					for (int i = 0; i < numImages; ++i) {
						in.add(input.get(i).createInterpolator());
					}
					interpolators.add(in);
				}
				else {
					in = interpolators.get(threadNumber);
				}
				return in;
			}

			/**
			 * Intermediate helper method to delegate to
			 * {@link #processTile(ClassifiedRegion, int[], int, PixelFusion, ArrayList, ArrayList, LocalizableByDimCursor, float[], int[], int, int[], long[], ImagePlus)}
			 */
		private void processTile(ClassifiedRegion r, int depth,
			PixelFusion myFusion, ArrayList<InvertibleBoundable> transform,
			ArrayList<RealRandomAccess<? extends RealType<?>>> in,
			RandomAccess<T> out, double[][] inPos,
			int threadNumber, int[] count, long[] lastDraw, ImagePlus fusionImp)
			throws NoninvertibleModelException
		{
			processTile(r, r.classArray(), depth, myFusion, transform, in, out,
				inPos, threadNumber, count, lastDraw, fusionImp);
		}

		/**
		 * Helper method to fuse all the positions of a given
		 * {@link ClassifiedRegion}. Since we do not know the dimensionality of the
		 * region, we recurse over each position of each dimension. The tail step of
		 * each descent iterates over all the images (classes) of the given region,
		 * fusing the pixel values at the current position of each associated image.
		 * This final value is then set in the output.
		 */
		private void processTile(ClassifiedRegion r, int[] images, int depth,
			PixelFusion myFusion, ArrayList<InvertibleBoundable> transform,
			ArrayList<RealRandomAccess<? extends RealType<?>>> in,
			RandomAccess<T> out, double[][] inPos,
			int threadNumber, int[] count, long[] lastDraw, ImagePlus fusionImp)
			throws NoninvertibleModelException
		{
			// NB: there are two process tile methods, one for in-memory fusion
			// and one for writing to disk. They are slightly different, but
			// if one is updated the other should be as well!
			if (depth < r.size()) {
				Interval d = r.get(depth);

				// The intervals of the given region define the bounds of
				// iteration
				// So we are recursively defining a nested iteration order to
				// cover each position of the region
				int start = d.min();
				int end = d.max();

				// If this is the dimension being split up for multi-threading we
				// need to update the iteration bounds.
				if (depth == loopDim[0]) {
					start += loopOffset;
					end = start + loopSize - 1;
				}

				out.setPosition(start, depth);

				// NB: can't make this loop inclusive since we don't want the out.fwd
				// call to go out of bounds, and we can't setPosition (-1) or we
				// get AIOOB exceptions. So we loop 1 time less than desired, then
				// do a straight read after the loop.
				for (int i = start; i < end; i++) {
					// Recurse to the next depth (dimension)
					processTile(r, images, depth + 1, myFusion, transform, in, out,
						inPos, threadNumber, count, lastDraw, fusionImp);
					// move forward
					out.fwd(depth);
				}

				// Need to read the final position.
				processTile(r, images, depth + 1, myFusion, transform, in, out,
					inPos, threadNumber, count, lastDraw, fusionImp);
				return;
			}

			// compute fusion for this position
			myFusion.clear();

			// Loop over the images in this region
			for (int d = 0; d < r.size(); d++) {
				final double value = out.getDoublePosition(d) + offset[d];

				for (int index = 0; index < images.length; index++) {
					// Get the positions for the current image
					inPos[images[index]][d] = value;
				}
			}

			// Get the value at each input position
			for (int index = 0; index < images.length; index++) {
				final int image = images[index];
				// Transform to get input position
				transform.get(image).applyInverseInPlace(inPos[image]);
				in.get(image).setPosition(inPos[image]);
				// fuse
				myFusion.addValue(in.get(image).get().getRealFloat(), image, inPos[image]);
			}

			// set value
			out.get().setReal(myFusion.getValue());

			// Display progress if on thread 0
			if (threadNumber == 0) {
				count[0]++;
				// just every 10000'th pixel
				if (count[0] % 10000 == 0) {
					lastDraw[0] = drawFusion(lastDraw[0], fusionImp);
					IJ.showProgress(count[0] / positionsPerThread);
				}
			}
		}
	}
	
	/**
	 * Recursive method to construct all possible {@link ClassifiedRegion}
	 * combinations from an arbitrary number of {@link Interval} lists. One list
	 * is required for each dimension of the final regions. The {@code allRegions}
	 * list is then populated with these combinations.
	 * @param rawTiles 
	 * @param placedTiles 
	 */
	private static void buildAllRegions(List<Interval>[] allIntervals,
		int[] ivalIndices, int depth, ClassifiedRegion queryTile,
		ClassifiedRegion placedTile, Set<ClassifiedRegion> placedTiles,
		Stack<ClassifiedRegion> rawTiles)
	{
		if (depth != ivalIndices.length) {
			for (int i = 0; i < allIntervals[depth].size(); i++) {
				buildAllRegions(allIntervals, ivalIndices, depth + 1, queryTile,
					placedTile, placedTiles, rawTiles);
				// increment the index at this position in the intervals array
				ivalIndices[depth]++;
			}
			ivalIndices[depth] = 0;
			return;
		}

		// Build a new region using the current specified indices
		ClassifiedRegion region = new ClassifiedRegion(allIntervals.length);

		boolean inQuery = true;
		boolean inPlaced = true;
		boolean validIval = true;
		for (int i = 0; validIval && i < allIntervals.length; i++)
		{
			Interval newIval = new Interval(allIntervals[i].get(ivalIndices[i]));
			Interval queryIval = queryTile.get(i);
			Interval placedIval = placedTile.get(i);
			region.set(newIval, i);
			// Each sub-region must be fully contained in at least one parent,
			// otherwise it is invalid.
			inQuery =
				inQuery && queryIval.contains(newIval.min()) == 0 &&
					queryIval.contains(newIval.max()) == 0;
			inPlaced =
				inPlaced && placedIval.contains(newIval.min()) == 0 &&
					placedIval.contains(newIval.max()) == 0;
			validIval = inQuery || inPlaced;
		}

		if (validIval) {
			if (inQuery) {
				region.addAllClasses(queryTile);
				rawTiles.push(region);
			}
			if (inPlaced) {
				region.addAllClasses(placedTile);
				if (!inQuery) {
					placedTiles.add(region);
				}
			}
		}
	}

	/**
	 * Fuse one slice/volume (one channel)
	 * 
	 * @param output - same the type of the ImagePlus input
	 * @param input - FloatType, because of Interpolation that needs to be done
	 * @param transform - the transformation
	 */
	protected static <T extends RealType<T>> void fuseBlockNoOverlap( final Img<T> output, final ArrayList< ? extends ImageInterpolation< ? extends RealType< ? > > > input, final double[] offset, 
			final ArrayList< InvertibleBoundable > transform, final boolean displayFusion )
	{
		final int numDimensions = output.numDimensions();
		final int numImages = input.size();
				
		// run multithreaded
		final AtomicInteger ai = new AtomicInteger(0);					
        final Thread[] threads = SimpleMultiThreading.newThreads( numImages );
        
        for (int ithread = 0; ithread < threads.length; ++ithread)
            threads[ithread] = new Thread( new Runnable()
            {
                @Override
                public void run()
                {
                	// Thread ID
                	final int myImage = ai.getAndIncrement();
                	
                	// only the first thread does preview and update the status bar
                	// this requires no synchronized stuff
            		long lastDraw = 0;
            		ImagePlus fusionImp = null;

                	if ( displayFusion && myImage == 0 )
                	{
                		try
            			{
            				fusionImp = ((ImagePlusImg<?, ?>) output).getImagePlus();
            				fusionImp.setTitle( "fusing..." );
            				fusionImp.show();
            			}
            			catch ( ImgLibException e )
            			{
            				Log.error( "Output image has no ImageJ type: " + e );
            			}                		
                	}

                	final Img< ? extends RealType<?> > image = input.get( myImage ).getImg();
                	final int[] translation = new int[ numDimensions ];
                	
                	final InvertibleBoundable t = transform.get( myImage );
            		final double[] tmp = new double[ numDimensions ];
            		t.applyInPlace( tmp );
 
            		for ( int d = 0; d < numDimensions; ++d )
            			translation[ d ] = (int) Math.round( tmp[ d ] );

            		final Cursor< ? extends RealType<?> > cursor = image.localizingCursor();
            		final RandomAccess< ? extends RealType<?> > randomAccess = output.randomAccess();
            		final int[] pos = new int[ numDimensions ];
            		
            		int j = 0;
            		while ( cursor.hasNext() )
            		{
            			cursor.fwd();
            			cursor.localize( pos );
            			
        				// just thread 0
        				if ( myImage == 0 )
        				{
        					// just every 10000'th pixel
        					if ( j++ % 10000 == 0 )
        					{
                				lastDraw = drawFusion( lastDraw, fusionImp );
        						IJ.showProgress( (double)j / (double)image.size() );
        					}
        				}
          				
                		for ( int d = 0; d < numDimensions; ++d )
                		{
                			pos[ d ] += translation[ d ];
                			pos[ d ] -= offset[ d ];
                		}
                		
                		randomAccess.setPosition( pos );
                		randomAccess.get().setReal( cursor.get().getRealFloat() );
            		}
            		
    				if ( fusionImp != null )
    					fusionImp.hide();
                 }
            });
        
        SimpleMultiThreading.startAndJoin( threads );        
	}

	/**
	 * Fuse one slice/volume (one channel)
	 * 
	 * @param outputSlice - same the type of the ImagePlus input, just one slice which will be written to the output directory
	 * @param input - FloatType, because of Interpolation that needs to be done
	 * @param transform - the transformation
	 */
	protected static <T extends RealType<T>> void writeBlock( final Img<T> outputSlice, final int numSlices, final int t, final int numTimePoints, final int c, final int numChannels, 
			final ArrayList< ? extends ImageInterpolation< ? extends RealType< ? > > > input, final double[] offset, 
			final ArrayList< InvertibleBoundable > transform, final PixelFusion fusion, final String outputDirectory )
	{
		final int numImages = input.size();
		final int numDimensions = offset.length;

		final List<ClassifiedRegion> tiles =
				buildTileList(numImages, numDimensions, transform, input, offset);
		
		final ArrayList<RealRandomAccess<? extends RealType<?>>> in = new ArrayList<RealRandomAccess<? extends RealType<?>>>();
		
		for ( int i = 0; i < numImages; ++i )
			in.add( input.get( i ).createInterpolator() );
		
		final PixelFusion myFusion = fusion.copy();
		
		try 
		{
			final long sliceSize = outputSlice.size();
			for ( int slice = 0; slice < numSlices; ++slice )
			{
				IJ.showStatus("Fusing time point: " + t + " of " + numTimePoints + ", " +
						"channel: " + c + " of " + numChannels + ", slice: " + (slice + 1) + " of " +
						numSlices + "...");

				final RandomAccess<T> out = outputSlice.randomAccess();
				final double[][] inPos = new double[ numImages ][ numDimensions ];
				final int[] count = new int[1];

				IJ.showProgress(0);
				
				// just like fuseBlock but pin to the current slice #
				for (long tileIndex=0; tileIndex<tiles.size(); tileIndex++) {
					ClassifiedRegion currentTile = tiles.get((int) tileIndex);
					writeTile(currentTile, 0, slice, myFusion, transform, offset, in, out, inPos, count, sliceSize, numSlices);
				}
				
				
				// write the slice
				final ImagePlus outImp = ((ImagePlusImg<?,?>)outputSlice).getImagePlus();
				final FileSaver fs = new FileSaver( outImp );
				fs.saveAsTiff( new File( outputDirectory, "img_t" + lz( t, numTimePoints ) + "_z" + lz( slice+1, numSlices ) + "_c" + lz( c, numChannels ) ).getAbsolutePath() );
				}
		} 
		catch ( NoninvertibleModelException e ) 
		{
			Log.error( "Cannot invert model, qutting." );
			return;
		} 
		catch ( ImgLibException e ) 
		{
			Log.error( "Output image has no ImageJ type: " + e );
			return;
		}
	}

	/**
	 * Helper method to fuse all the positions of a given
	 * {@link ClassifiedRegion}. Since we do not know the dimensionality of
	 * the region, we recurse over each position of each dimension. The tail
	 * step of each descent iterates over all the images (classes) of the
	 * given region, fusing the pixel values at the current position of each
	 * associated image. This final value is then set in the output.
	 */
	private static <T extends RealType<T>> void writeTile(ClassifiedRegion r,
		int depth, final int slice, PixelFusion myFusion,
		ArrayList<InvertibleBoundable> transform, double[] offset,
		ArrayList<RealRandomAccess<? extends RealType<?>>> in,
		RandomAccess<T> out, double[][] inPos, int[] count,
		final long sliceSize, final int numSlices)
		throws NoninvertibleModelException
	{
		//NB: there are two process tile methods, one for in-memory fusion
		// and one for writing to disk. They are slightly different, but
		// if one is updated the other should be as well!
		// For 3D datasets, we fix the last dimension to the given slice #
		if (depth < out.numDimensions()) {
			Interval d = r.get(depth);
			// The intervals of the given region define the bounds of iteration
			// So we are recursively defining a nested iteration order to cover
			// each position of the region
			int start = d.min();
			int end = d.max();

			out.setPosition(start, depth);

			for (int i=start; i < end; i++) {
				// The position array will be used to set the in and out positions.
				// It specifies where we are in the output image
				// Recurse to the next depth (dimension)
				writeTile(r, depth+1, slice, myFusion, transform, offset, in, out, inPos,
					count, sliceSize, numSlices );
				out.fwd(depth);
			}

			writeTile(r, depth+1, slice, myFusion, transform, offset, in, out, inPos,
				count, sliceSize, numSlices );
			return;
		}

		// compute fusion for this position
		myFusion.clear();

		final int[] images = r.classArray();

		// Loop over the images in this region
		for (int d = 0; d < out.numDimensions(); d++) {
			final double value = out.getDoublePosition(d) + offset[d];

			for (int index = 0; index < images.length; index++) {
				// Get the positions for the current image
				inPos[images[index]][d] = value;
			}
		}

		if (r.size() > out.numDimensions()) {
			final int dim = r.size() - 1;
			final double value = slice + offset[dim];
			for (int index = 0; index < images.length; index++) {
				// Get the positions for the current image
				inPos[images[index]][dim] = value;
			}
		}

		// Get the value at each input position
		for (int index = 0; index < images.length; index++) {
			final int image = images[index];
			// Transform to get input position
			transform.get(image).applyInverseInPlace(inPos[image]);
			in.get(image).setPosition(inPos[image]);
			// fuse
			myFusion.addValue(in.get(image).get().getRealFloat(), image, inPos[image]);
		}

		// set value
		out.get().setReal(myFusion.getValue());

		// set value
		out.get().setReal(myFusion.getValue());

		// Update progress if necessary
		count[0]++;
		if (count[0] % 10000 == 0) {
			IJ.showProgress( (double)( count[0] + ( slice * sliceSize ) ) / (double)( numSlices * sliceSize ) );
		}

	}

	private static final String lz( final int num, final int max )
	{
		String out = "" + num;
		String outMax = "" + max;
		
		while ( out.length() < outMax.length() )
			out = "0" + out;
		
		return out;
	}

	/**
	 * Estimate the bounds of the output image. If there are more models than images, we assume that this encodes for more timepoints.
	 * E.g. 2 Images and 10 models would mean 5 timepoints. The arrangement of the models should be as follows:
	 * 
	 * image1 timepoint1
	 * image2 timepoint1
	 * image1 timepoint2
	 * ...
	 * image2 timepoint5
	 * 
	 * @param offset - the offset, will be computed
	 * @param size - the size, will be computed
	 * @param images - all imageplus in a list
	 * @param models - all models
	 * @param dimensionality - which dimensionality (2 or 3)
	 */
	public static void estimateBounds( final double[] offset, final int[] size, final List<ImagePlus> images, final ArrayList<InvertibleBoundable> models, final int dimensionality )
	{
		final int[][] imgSizes = new int[ images.size() ][ dimensionality ];
		
		for ( int i = 0; i < images.size(); ++i )
		{
			imgSizes[ i ][ 0 ] = images.get( i ).getWidth();
			imgSizes[ i ][ 1 ] = images.get( i ).getHeight();
			if ( dimensionality == 3 )
				imgSizes[ i ][ 2 ] = images.get( i ).getNSlices();
		}
		
		estimateBounds( offset, size, imgSizes, models, dimensionality );
	}
	
	/**
	 * Estimate the bounds of the output image. If there are more models than images, we assume that this encodes for more timepoints.
	 * E.g. 2 Images and 10 models would mean 5 timepoints. The arrangement of the models should be as follows:
	 * 
	 * image1 timepoint1
	 * image2 timepoint1
	 * image1 timepoint2
	 * ...
	 * image2 timepoint5
	 * 
	 * @param offset - the offset, will be computed
	 * @param size - the size, will be computed
	 * @param imgSizes - the dimensions of all input images imgSizes[ image ][ x, y, (z) ]
	 * @param models - all models
	 * @param dimensionality - which dimensionality (2 or 3)
	 */
	public static void estimateBounds( final double[] offset, final int[] size, final int[][]imgSizes, final ArrayList<InvertibleBoundable> models, final int dimensionality )
	{
		final int numImages = imgSizes.length;
		final int numTimePoints = models.size() / numImages;
		
		// estimate the bounaries of the output image
		final double[][] max = new double[ numImages * numTimePoints ][];
		final double[][] min = new double[ numImages * numTimePoints ][ dimensionality ];
		
		if ( dimensionality == 2 )
		{
			for ( int i = 0; i < numImages * numTimePoints; ++i )
				max[ i ] = new double[] { imgSizes[ i % numImages ][ 0 ], imgSizes[ i % numImages ][ 1 ] };
		}
		else
		{
			for ( int i = 0; i < numImages * numTimePoints; ++i )
				max[ i ] = new double[] { imgSizes[ i % numImages ][ 0 ], imgSizes[ i % numImages ][ 1 ], imgSizes[ i % numImages ][ 2 ] };
		}
		
		//Log.info( "1: " + Util.printCoordinates( min[ 0 ] ) + " -> " + Util.printCoordinates( max[ 0 ] ) );
		//Log.info( "2: " + Util.printCoordinates( min[ 1 ] ) + " -> " + Util.printCoordinates( max[ 1 ] ) );

		// casts of the models
		final ArrayList<InvertibleBoundable> boundables = new ArrayList<InvertibleBoundable>();
		
		for ( int i = 0; i < numImages * numTimePoints; ++i )
		{
			final InvertibleBoundable boundable = models.get( i ); 
			boundables.add( boundable );
			
			//Log.info( "i: " + boundable );
			
			boundable.estimateBounds( min[ i ], max[ i ] );
		}
		//Log.info( "1: " + Util.printCoordinates( min[ 0 ] ) + " -> " + Util.printCoordinates( max[ 0 ] ) );
		//Log.info( "2: " + Util.printCoordinates( min[ 1 ] ) + " -> " + Util.printCoordinates( max[ 1 ] ) );
		
		// dimensions of the final image
		final double[] minImg = new double[ dimensionality ];
		final double[] maxImg = new double[ dimensionality ];

		if ( max.length == 1 )
		{
			// just one image
			for ( int d = 0; d < dimensionality; ++d )
			{
				maxImg[ d ] = Math.max( max[ 0 ][ d ], min[ 0 ][ d ] );
				minImg[ d ] = Math.min( max[ 0 ][ d ], min[ 0 ][ d ] );				
			}
		}
		else
		{
			for ( int d = 0; d < dimensionality; ++d )
			{
				// the image might be rotated so that min is actually max
				maxImg[ d ] = Math.max( Math.max( max[ 0 ][ d ], max[ 1 ][ d ] ), Math.max( min[ 0 ][ d ], min[ 1 ][ d ]) );
				minImg[ d ] = Math.min( Math.min( max[ 0 ][ d ], max[ 1 ][ d ] ), Math.min( min[ 0 ][ d ], min[ 1 ][ d ]) );
				
				for ( int i = 2; i < numImages * numTimePoints; ++i )
				{
					maxImg[ d ] = Math.max( maxImg[ d ], Math.max( min[ i ][ d ], max[ i ][ d ]) );
					minImg[ d ] = Math.min( minImg[ d ], Math.min( min[ i ][ d ], max[ i ][ d ]) );	
				}
			}
		}
		//Log.info( "output: " + Util.printCoordinates( minImg ) + " -> " + Util.printCoordinates( maxImg ) );

		// the size of the new image
		//final int[] size = new int[ dimensionality ];
		// the offset relative to the output image which starts with its local coordinates (0,0,0)
		//final float[] offset = new float[ dimensionality ];
		
		for ( int d = 0; d < dimensionality; ++d )
		{
			size[ d ] = (int)Math.round( maxImg[ d ] - minImg[ d ] );
			offset[ d ] = minImg[ d ];			
		}
		
		//Log.info( "size: " + Util.printCoordinates( size ) );
		//Log.info( "offset: " + Util.printCoordinates( offset ) );		
	}

	/**
	 * Helper method to redraw a given ImagePlus. If the current system time -
	 * lastDraw is past the {@link #redrawDelay},
	 * {@link ImagePlus#updateAndDraw()} will be invoked on the given image. The
	 * return value can be used to keep track of when the image was last drawn.
	 * 
	 * @param lastDraw - Time the ImagePlus was last drawn
	 * @param fusion - Intermediate fusion (some but not all pixels fused) to
	 *          potentially redraw
	 * @return The time of the most recent draw of the provided fusion.
	 */
	private static long drawFusion( final long lastDraw, final ImagePlus fusion )
	{
		final long t = System.currentTimeMillis();
		
		// If enough time has passed, redraw the image and return the time of the latest draw
		if ( fusion != null && t - lastDraw > redrawDelay ) {
			fusion.updateAndDraw();
			return t;
		}
		
		// As the image was not drawn, we just should still return the time of the last draw
		return lastDraw;
	}

	public static void main( String[] args )
	{
		new ImageJ();
		
		// test blending
		ImgFactory< FloatType > f = new ArrayImgFactory< FloatType >();
		Img< FloatType > img = f.create( new int[] { 400, 400 }, new FloatType() ); 
		
		Cursor< FloatType > c = img.localizingCursor();
		final int numDimensions = img.numDimensions();
		final double[] tmp = new double[ numDimensions ];
		
		// for blending
		final long[] dimensions = new long[ numDimensions ];
		img.dimensions( dimensions );
		final float percentScaling = 0.2f;
		final double[] border = new double[ numDimensions ];
					
		while ( c.hasNext() )
		{
			c.fwd();
			
			for ( int d = 0; d < numDimensions; ++d )
				tmp[ d ] = c.getFloatPosition( d );
			
			c.get().set( (float)BlendingPixelFusion.computeWeight( tmp, dimensions, border, percentScaling ) );
		}
		
		ImageJFunctions.show( img );
		Log.debug( "done" );
	}
}
