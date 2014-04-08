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

import mpicbg.imglib.container.array.ArrayContainerFactory;
import mpicbg.imglib.container.imageplus.ImagePlusContainer;
import mpicbg.imglib.container.imageplus.ImagePlusContainerFactory;
import mpicbg.imglib.cursor.LocalizableByDimCursor;
import mpicbg.imglib.cursor.LocalizableCursor;
import mpicbg.imglib.exception.ImgLibException;
import mpicbg.imglib.image.Image;
import mpicbg.imglib.image.ImageFactory;
import mpicbg.imglib.image.display.imagej.ImageJFunctions;
import mpicbg.imglib.interpolation.Interpolator;
import mpicbg.imglib.interpolation.InterpolatorFactory;
import mpicbg.imglib.interpolation.linear.LinearInterpolatorFactory;
import mpicbg.imglib.interpolation.nearestneighbor.NearestNeighborInterpolatorFactory;
import mpicbg.imglib.multithreading.Chunk;
import mpicbg.imglib.multithreading.SimpleMultiThreading;
import mpicbg.imglib.outofbounds.OutOfBoundsStrategyMirrorFactory;
import mpicbg.imglib.outofbounds.OutOfBoundsStrategyValueFactory;
import mpicbg.imglib.type.numeric.RealType;
import mpicbg.imglib.type.numeric.integer.UnsignedByteType;
import mpicbg.imglib.type.numeric.integer.UnsignedShortType;
import mpicbg.imglib.type.numeric.real.FloatType;
import mpicbg.models.InvertibleBoundable;
import mpicbg.models.NoninvertibleModelException;
import stitching.Interval;
import stitching.ClassifiedRegion;
import stitching.utils.CompositeImageFixer;

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
	public static < T extends RealType< T > > ImagePlus fuse( final T targetType, final ArrayList< ImagePlus > images, final ArrayList< InvertibleBoundable > models, 
			final int dimensionality, final boolean subpixelResolution, final int fusionType, final String outputDirectory, final boolean noOverlap, final boolean ignoreZeroValues, final boolean displayImages )
	{
		// first we need to estimate the boundaries of the new image
		final float[] offset = new float[ dimensionality ];
		final int[] size = new int[ dimensionality ];
		final int numTimePoints = images.get( 0 ).getNFrames();
		final int numChannels = images.get( 0 ).getNChannels();
		
		estimateBounds( offset, size, images, models, dimensionality );
		
		if ( subpixelResolution )
			for ( int d = 0; d < size.length; ++d )
				++size[ d ];
		
		// for output
		final ImageFactory<T> f = new ImageFactory<T>( targetType, new ImagePlusContainerFactory() );
		
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
				final Image< T > out;
				
				// we just create one slice if we write to disk
				if ( outputDirectory == null )
					out = f.createImage( size );
				else
					out = f.createImage( new int[] { size[ 0 ], size[ 1 ] } ); // just create a slice

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
				
				// extract the complete blockdata
				if ( subpixelResolution )
				{
					final ArrayList< ImageInterpolation< FloatType > > blockData = new ArrayList< ImageInterpolation< FloatType > >();

					// for linear interpolation we want to mirror, otherwise we get black areas at the first and last pixel of each image
					final InterpolatorFactory< FloatType > interpolatorFactory = new LinearInterpolatorFactory<FloatType>( new OutOfBoundsStrategyMirrorFactory<FloatType>() );
					
					for ( final ImagePlus imp : images )
						blockData.add( new ImageInterpolation<FloatType>( ImageJFunctions.convertFloat( Hyperstack_rearranger.getImageChunk( imp, c, t ) ), interpolatorFactory ) );
					
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
						out.close();
					}
				}
				else
				{
					// can be a mixture of different RealTypes
					final ArrayList< ImageInterpolation< ? extends RealType< ? > > > blockData = new ArrayList< ImageInterpolation< ? extends RealType< ? > > >();

					final InterpolatorFactory< FloatType > interpolatorFactoryFloat = new NearestNeighborInterpolatorFactory< FloatType >( new OutOfBoundsStrategyValueFactory<FloatType>() );
					final InterpolatorFactory< UnsignedShortType > interpolatorFactoryShort = new NearestNeighborInterpolatorFactory< UnsignedShortType >( new OutOfBoundsStrategyValueFactory<UnsignedShortType>() );
					final InterpolatorFactory< UnsignedByteType > interpolatorFactoryByte = new NearestNeighborInterpolatorFactory< UnsignedByteType >( new OutOfBoundsStrategyValueFactory<UnsignedByteType>() );

					for ( final ImagePlus imp : images )
					{
						if ( imp.getType() == ImagePlus.GRAY32 )
							blockData.add( new ImageInterpolation<FloatType>( ImageJFunctions.wrapFloat( Hyperstack_rearranger.getImageChunk( imp, c, t ) ), interpolatorFactoryFloat ) );
						else if ( imp.getType() == ImagePlus.GRAY16 )
							blockData.add( new ImageInterpolation<UnsignedShortType>( ImageJFunctions.wrapShort( Hyperstack_rearranger.getImageChunk( imp, c, t ) ), interpolatorFactoryShort ) );
						else
							blockData.add( new ImageInterpolation<UnsignedByteType>( ImageJFunctions.wrapByte( Hyperstack_rearranger.getImageChunk( imp, c, t ) ), interpolatorFactoryByte ) );
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
						out.close();
					}
				}
				
				// add to stack
				try 
				{
					if ( stack != null )
					{
						final ImagePlus outImp = ((ImagePlusContainer<?,?>)out.getContainer()).getImagePlus();
						for ( int z = 1; z <= out.getDimension( 2 ); ++z )
							stack.addSlice( "", outImp.getStack().getProcessor( z ) );
					}
				} 
				catch (ImgLibException e) 
				{
					IJ.log( "Output image has no ImageJ type: " + e );
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
		//IJ.log( "ch: " + imp.getNChannels() );
		//IJ.log( "slices: " + imp.getNSlices() );
		//IJ.log( "frames: " + imp.getNFrames() );
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
	protected static <T extends RealType<T>> void fuseBlock( final Image<T> output, final ArrayList< ? extends ImageInterpolation< ? extends RealType< ? > > > input, final float[] offset, 
			final ArrayList< InvertibleBoundable > transform, final PixelFusion fusion, final boolean displayFusion )
	{
		final int numDimensions = output.getNumDimensions();
		final int numImages = input.size();
		long size = output.getDimension( 0 );

		for (int d = 1; d < output.getNumDimensions(); ++d) {
			size *= output.getDimension(d);
		}

		Stack<ClassifiedRegion> rawTiles = new Stack<ClassifiedRegion>();

		for ( int i = 0; i < numImages; ++i ){
				final float[] min = new float[ numDimensions ];
				transform.get(i).applyInPlace(min);
				ClassifiedRegion shape = new ClassifiedRegion(numDimensions);
				shape.addClass(i);
			for ( int d = 0; d < numDimensions; ++d ) {
				Interval ival = new Interval((int) min[d], (int) (min[d] + input.get(i).getImage().getDimension(d)));
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

		final List<ClassifiedRegion> tiles = new ArrayList<ClassifiedRegion>(placedTiles);

		IJ.showProgress( 0 );

		
		// run multithreaded
		final AtomicInteger ai = new AtomicInteger(0);					
        final Thread[] threads = SimpleMultiThreading.newThreads();

        final Vector<Chunk> threadChunks = SimpleMultiThreading.divideIntoChunks( tiles.size(), threads.length );
        final long positionsPerThread = size / threadChunks.size();
        
        for (int ithread = 0; ithread < threads.length; ++ithread)
            threads[ithread] = new Thread(new Runnable()
            {
                @Override
                public void run()
                {
                	// Thread ID
                	final int threadNumber = ai.getAndIncrement();
        
                	// only the first thread does preview and update the status bar
                	// this requires no synchronized stuff
            		long[] lastDraw = new long[1];
            		ImagePlus fusionImp = null;

                	if ( displayFusion && threadNumber == 0 )
                	{
                		try
            			{
            				fusionImp = ((ImagePlusContainer<?, ?>) output.getContainer()).getImagePlus();
            				fusionImp.setTitle( "fusing..." );
            				fusionImp.show();
            			}
            			catch ( ImgLibException e )
            			{
            				IJ.log( "Output image has no ImageJ type: " + e );
            			}                		
                	}

                	// get chunk of pixels to process
                	final Chunk myChunk = threadChunks.get( threadNumber );
                	final long startPos = myChunk.getStartPosition();
                	final long loopSize = myChunk.getLoopSize();
                	
            		final ArrayList<Interpolator<? extends RealType<?>>> in = new ArrayList<Interpolator<? extends RealType<?>>>();
            		
            		for ( int i = 0; i < numImages; ++i )
            			in.add( input.get( i ).createInterpolator() );
            		
            		final int[] outPos = new int[ output.getNumDimensions() ];
            		final float[] inPos = new float[ output.getNumDimensions() ];
            		final int[] count = new int[1];
            		final PixelFusion myFusion = fusion.copy();
            		LocalizableByDimCursor<T> out = output.createLocalizableByDimCursor();
            		
            		try 
            		{
						// Process each tile index assigned to this thread
						for (long tileIndex = startPos; tileIndex < startPos + loopSize; tileIndex++)
						{
							//TODO we're using integer indices... might need to use floats + step values for subpixel accuracy?
							// Current tile
							ClassifiedRegion tile = tiles.get((int) tileIndex);
							// For each position in this tile, fuse its pixels across the appropriate images
							// NB: recursion is necessary because there are an arbitrary number of dimensions in the tile
							processTile(tile, 0, myFusion, transform, in, out, inPos, outPos,
								threadNumber, count, lastDraw, fusionImp);

						}
            		} 
            		catch ( NoninvertibleModelException e ) 
            		{
            			IJ.log( "Cannot invert model, qutting." );
            			return;
            		}

                    if ( fusionImp != null )
                    	fusionImp.hide();
                }

				/**
				 * Helper method to fuse all the positions of a given
				 * {@link ClassifiedRegion}. Since we do not know the dimensionality of
				 * the region, we recurse over each position of each dimension. The tail
				 * step of each descent iterates over all the images (classes) of the
				 * given region, fusing the pixel values at the current position of each
				 * associated image. This final value is then set in the output.
				 */
				private void processTile(ClassifiedRegion r, int depth, PixelFusion myFusion,
					ArrayList<InvertibleBoundable> transform,
					ArrayList<Interpolator<? extends RealType<?>>> in,
					LocalizableByDimCursor<T> out, float[] inPos, int[] outPos,
					int threadNumber, int[] count, long[] lastDraw,
					ImagePlus fusionImp) throws NoninvertibleModelException
				{
					if (depth < r.size()) {
						Interval d = r.get(depth);
						// The intervals of the given region define the bounds of iteration
						// So we are recursively defining a nested iteration order to cover
						// each position of the region
						for (int i=d.start(); i<d.end(); i++) {
							// The position array will be used to set the in and out positions.
							// It specifies where we are in the output image
							outPos[depth] = i;
							// Recurse to the next depth (dimension)
							processTile(r, depth+1, myFusion, transform, in, out, inPos,
								outPos, threadNumber, count, lastDraw, fusionImp);
						}
						return;
					}

					// compute fusion for this position
					myFusion.clear();
					// These are the image indices associated with this region
					final int[] indices = r.classArray();

					// Loop over the images in this region
					for (int index=0; index<indices.length; index++) {
						// Get the positions for the current image
						for (int d=0; d<r.size(); d++) {
							inPos[d] = outPos[d] + offset[d];
						}
						// Transform to get input position
						transform.get(indices[index]).applyInverseInPlace(inPos);
						in.get(indices[index]).setPosition(inPos);
						// fuse
						myFusion.addValue(in.get(indices[index]).getType().getRealFloat(),
							indices[index], inPos);
					}

					// set value
					out.setPosition(outPos);
					out.getType().setReal(myFusion.getValue());
					count[0]++;

					// Display progress if on thread 0
  				if ( threadNumber == 0 )
  				{
  					// just every 10000'th pixel
  					if ( count[0] % 10000 == 0 )
  					{
          				lastDraw[0] = drawFusion( lastDraw[0], fusionImp );
  						IJ.showProgress( (double)count[0] / (double)positionsPerThread );
  					}
  				}
				}

			});
        
        SimpleMultiThreading.startAndJoin( threads );        
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
		// First we do an explicit simple check to see if the tiles are the same:
		if (queryTile.equalsRegion(placedTile)) {
			// NB: the original place tile will be removed from the list, so we create a new tile
			// and add all indices.
			ClassifiedRegion newTile = new ClassifiedRegion(queryTile);
			newTile.addAllClasses(placedTile);
			placedTiles.add(newTile);
			return;
		}

		// Tiles are different but overlapping. So we need to identify the start and end points of
		// all potential sub tiles, for each dimension.
		List<Interval>[] allIntervals = new List[queryTile.size()];
		for (int i=0; i<allIntervals.length; i++) {
			List<Integer> points = new ArrayList<Integer>();
			List<Interval> intervals = new ArrayList<Interval>();
			points.add(queryTile.get(i).start());
			points.add(queryTile.get(i).end());
			points.add(placedTile.get(i).start());
			points.add(placedTile.get(i).end());
			// Which tiles the points belong to doesn't matter. We just need to make intervals of the adjacent points
			Collections.sort(points);
			for (int j=0; j<points.size() - 1; j++) {
				if (!points.get(j).equals(points.get(j+1))) {
					intervals.add(new Interval(points.get(j), points.get(j+1)));
				}
			}
			allIntervals[i] = intervals;
		}

		// Now that we know our potential intervals, we need to enumerate a list of all regions constructable from those intervals
		List<ClassifiedRegion> allRegions = new ArrayList<ClassifiedRegion>();
		int[] pos = new int[allIntervals.length];
		buildAllRegions(allRegions, allIntervals, pos, 0);

		ClassifiedRegion overlappedRegion = null;

		// Update the classification of each sub-region. Possibilities include: 1 parent, both parents, or no parents (in which case
		// the region is discarded)
		for (ClassifiedRegion newRegion : allRegions) {
			boolean matchedQueried = false;
			boolean matchedPlaced = false;
			// If there's an intersection in either case here, it's because the new region
			// is a subset of one of the two parent tiles. Thus it should inherit all the
			// index labels of the parent(s).
			if (queryTile.intersects(newRegion, true)) {
				newRegion.addAllClasses(queryTile);
				matchedQueried = true;
			}
			if (placedTile.intersects(newRegion, true)) {
				newRegion.addAllClasses(placedTile);
				matchedPlaced = true;
			}

			if (!matchedQueried && matchedPlaced) {
				// This is a sub-tile of a placed tile only. Thus we know it does not intersect
				// with any other placed tile, so we can add it back to the placed set.
				placedTiles.add(newRegion);
			}
			else if (matchedQueried) {
				// This is either a) a new overlapping sub-tile or b) a pure queried sub-tile.
				// In either case, since we short-circuited the query after finding our first overlap,
				// the new tile needs to be tested against other placed tiles. So add it
				// back to the raw list.
				rawTiles.push(newRegion);

				// Only one region will be created that is overlapped by both original regions
				// It can then be used to reduce the area of the other regions
				if (matchedPlaced) {
					overlappedRegion = newRegion;
				}

				// Regions that didn't match either parent are discarded
			}
		}

		// Now we need to shrink all the intervals that were not overlapped by both
		// the query and placed regions. This ensures that there will be no potential
		// overlap between the new sub-regions (so each position is claimed by a single region)
		// and that the maximum number of classes will be applied when possible.
		for (ClassifiedRegion region : allRegions) {
			if (overlappedRegion != null && region != overlappedRegion) {
				for (int i=0; i<region.size(); i++) {
					Interval primeInterval = overlappedRegion.get(i);
					Interval secondaryInterval = region.get(i);
					// Test the start point to ensure the two are not the same interval
					if (primeInterval.start() != secondaryInterval.start()) {
						if (primeInterval.start() == secondaryInterval.end()) {
							// Move the secondary interval's end down 1, so that their border
							// becomes owned exclusively by the prime interval
							secondaryInterval.setEnd(secondaryInterval.end() - 1);
						}
						else if (primeInterval.end() == secondaryInterval.start()) {
							// Move the secondary interval's start up 1, so that their border
							// becomes owned exclusively by the prime interval
							secondaryInterval.setStart(secondaryInterval.start() + 1);
						}
					}
				}
			}
		}
	}

	/**
	 * Recursive method to construct all possible {@link ClassifiedRegion}
	 * combinations from an arbitrary number of {@link Interval} lists. One list
	 * is required for each dimension of the final regions. The {@code allRegions}
	 * list is then populated with these combinations.
	 */
	private static void buildAllRegions(List<ClassifiedRegion> allRegions,
		List<Interval>[] allIntervals, int[] ivalIndices, int depth)
	{
		if (depth != ivalIndices.length) {
			for (int i = 0; i < allIntervals[depth].size(); i++) {
				buildAllRegions(allRegions, allIntervals, ivalIndices, depth + 1);
				// increment the index at this position in the intervals array
				ivalIndices[depth]++;
			}
			ivalIndices[depth] = 0;
			return;
		}

		// Build a new region using the current specified indices
		ClassifiedRegion region = new ClassifiedRegion(allIntervals.length);

		for (int i=0; i<allIntervals.length; i++) {
			region.set(allIntervals[i].get(ivalIndices[i]), i);
		}

		allRegions.add(region);
	}

	/**
	 * Fuse one slice/volume (one channel)
	 * 
	 * @param output - same the type of the ImagePlus input
	 * @param input - FloatType, because of Interpolation that needs to be done
	 * @param transform - the transformation
	 */
	protected static <T extends RealType<T>> void fuseBlockNoOverlap( final Image<T> output, final ArrayList< ? extends ImageInterpolation< ? extends RealType< ? > > > input, final float[] offset, 
			final ArrayList< InvertibleBoundable > transform, final boolean displayFusion )
	{
		final int numDimensions = output.getNumDimensions();
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
            				fusionImp = ((ImagePlusContainer<?, ?>) output.getContainer()).getImagePlus();
            				fusionImp.setTitle( "fusing..." );
            				fusionImp.show();
            			}
            			catch ( ImgLibException e )
            			{
            				IJ.log( "Output image has no ImageJ type: " + e );
            			}                		
                	}

                	final Image< ? extends RealType<?> > image = input.get( myImage ).getImage();
                	final int[] translation = new int[ numDimensions ];
                	
                	final InvertibleBoundable t = transform.get( myImage );
            		final float[] tmp = new float[ numDimensions ];
            		t.applyInPlace( tmp );
 
            		for ( int d = 0; d < numDimensions; ++d )
            			translation[ d ] = Math.round( tmp[ d ] );

            		final LocalizableCursor< ? extends RealType<?> > cursor = image.createLocalizableCursor();
            		final LocalizableByDimCursor< ? extends RealType<?> > randomAccess = output.createLocalizableByDimCursor();
            		final int[] pos = new int[ numDimensions ];
            		
            		while ( cursor.hasNext() )
            		{
            			cursor.fwd();
            			cursor.getPosition( pos );
            			
        				// just thread 0
        				if ( myImage == 0 )
        				{
        					// just every 10000'th pixel
        					final int j = cursor.getArrayIndex(); 
        					if ( j % 10000 == 0 )
        					{
                				lastDraw = drawFusion( lastDraw, fusionImp );
        						IJ.showProgress( (double)j / (double)image.getNumPixels() );
        					}
        				}
          				
                		for ( int d = 0; d < numDimensions; ++d )
                		{
                			pos[ d ] += translation[ d ];
                			pos[ d ] -= offset[ d ];
                		}
                		
                		randomAccess.setPosition( pos );
                		randomAccess.getType().setReal( cursor.getType().getRealFloat() );
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
	protected static <T extends RealType<T>> void writeBlock( final Image<T> outputSlice, final int numSlices, final int t, final int numTimePoints, final int c, final int numChannels, 
			final ArrayList< ? extends ImageInterpolation< ? extends RealType< ? > > > input, final float[] offset, 
			final ArrayList< InvertibleBoundable > transform, final PixelFusion fusion, final String outputDirectory )
	{
		final int numImages = input.size();
		final int numDimensions = offset.length;

		// the maximal dimensions of each image
		final int[][] max = new int[ numImages ][ numDimensions ];
		for ( int i = 0; i < numImages; ++i )
			for ( int d = 0; d < numDimensions; ++d )
				max[ i ][ d ] = input.get( i ).getImage().getDimension( d ) - 1; 
		
		final LocalizableCursor<T> out = outputSlice.createLocalizableCursor();
		final ArrayList<Interpolator<? extends RealType<?>>> in = new ArrayList<Interpolator<? extends RealType<?>>>();
		
		for ( int i = 0; i < numImages; ++i )
			in.add( input.get( i ).createInterpolator() );
		
		final float[][] tmp = new float[ numImages ][ numDimensions ];
		final PixelFusion myFusion = fusion.copy();
		
		try 
		{
			final int sliceSize = outputSlice.getNumPixels();
			
			for ( int slice = 0; slice < numSlices; ++slice )
			{
				IJ.showStatus("Fusing time point: " + t + " of " + numTimePoints + ", " +
						"channel: " + c + " of " + numChannels + ", slice: " + (slice + 1) + " of " +
						numSlices + "...");
				out.reset();

				IJ.showProgress(0);
				
				// fill all pixels of the current slice
				while ( out.hasNext() )
				{
					out.fwd();
  					
					// just every 10000'th pixel
					final long j = out.getArrayIndex(); 
					if ( j % 10000 == 0 )
						IJ.showProgress( (double)( j + ( slice * sliceSize ) ) / (double)( numSlices * sliceSize ) );
					
					// get the current position in the output image
					for ( int d = 0; d < 2; ++d )
					{
						final float value = out.getPosition( d ) + offset[ d ];
						
						for ( int i = 0; i < numImages; ++i )
							tmp[ i ][ d ] = value;
					}
					
					// if there is a third dimension, use the slice index
					if ( numDimensions == 3 )
					{
						final float value = slice + offset[ 2 ];
						
						for ( int i = 0; i < numImages; ++i )
							tmp[ i ][ 2 ] = value;						
					}
					
					// transform and compute output value
					myFusion.clear();
					
					// loop over all images for this output location
A:		        	for ( int i = 0; i < numImages; ++i )
		        	{
		        		transform.get( i ).applyInverseInPlace( tmp[ i ] );
		            	
		        		// test if inside
						for ( int d = 0; d < numDimensions; ++d )
							if ( tmp[ i ][ d ] < 0 || tmp[ i ][ d ] > max[ i ][ d ] )
								continue A;
						
						in.get( i ).setPosition( tmp[ i ] );			
						myFusion.addValue( in.get( i ).getType().getRealFloat(), i, tmp[ i ] );
					}
					
					// set value
					out.getType().setReal( myFusion.getValue() );
				}
				
				// write the slice
				final ImagePlus outImp = ((ImagePlusContainer<?,?>)outputSlice.getContainer()).getImagePlus();
				final FileSaver fs = new FileSaver( outImp );
				fs.saveAsTiff( new File( outputDirectory, "img_t" + lz( t, numTimePoints ) + "_z" + lz( slice+1, numSlices ) + "_c" + lz( c, numChannels ) ).getAbsolutePath() );
			}
		} 
		catch ( NoninvertibleModelException e ) 
		{
			IJ.log( "Cannot invert model, qutting." );
			return;
		} 
		catch ( ImgLibException e ) 
		{
			IJ.log( "Output image has no ImageJ type: " + e );
			return;
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
	public static void estimateBounds( final float[] offset, final int[] size, final List<ImagePlus> images, final ArrayList<InvertibleBoundable> models, final int dimensionality )
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
	public static void estimateBounds( final float[] offset, final int[] size, final int[][]imgSizes, final ArrayList<InvertibleBoundable> models, final int dimensionality )
	{
		final int numImages = imgSizes.length;
		final int numTimePoints = models.size() / numImages;
		
		// estimate the bounaries of the output image
		final float[][] max = new float[ numImages * numTimePoints ][];
		final float[][] min = new float[ numImages * numTimePoints ][ dimensionality ];
		
		if ( dimensionality == 2 )
		{
			for ( int i = 0; i < numImages * numTimePoints; ++i )
				max[ i ] = new float[] { imgSizes[ i % numImages ][ 0 ], imgSizes[ i % numImages ][ 1 ] };
		}
		else
		{
			for ( int i = 0; i < numImages * numTimePoints; ++i )
				max[ i ] = new float[] { imgSizes[ i % numImages ][ 0 ], imgSizes[ i % numImages ][ 1 ], imgSizes[ i % numImages ][ 2 ] };
		}
		
		//IJ.log( "1: " + Util.printCoordinates( min[ 0 ] ) + " -> " + Util.printCoordinates( max[ 0 ] ) );
		//IJ.log( "2: " + Util.printCoordinates( min[ 1 ] ) + " -> " + Util.printCoordinates( max[ 1 ] ) );

		// casts of the models
		final ArrayList<InvertibleBoundable> boundables = new ArrayList<InvertibleBoundable>();
		
		for ( int i = 0; i < numImages * numTimePoints; ++i )
		{
			final InvertibleBoundable boundable = models.get( i ); 
			boundables.add( boundable );
			
			//IJ.log( "i: " + boundable );
			
			boundable.estimateBounds( min[ i ], max[ i ] );
		}
		//IJ.log( "1: " + Util.printCoordinates( min[ 0 ] ) + " -> " + Util.printCoordinates( max[ 0 ] ) );
		//IJ.log( "2: " + Util.printCoordinates( min[ 1 ] ) + " -> " + Util.printCoordinates( max[ 1 ] ) );
		
		// dimensions of the final image
		final float[] minImg = new float[ dimensionality ];
		final float[] maxImg = new float[ dimensionality ];

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
		//IJ.log( "output: " + Util.printCoordinates( minImg ) + " -> " + Util.printCoordinates( maxImg ) );

		// the size of the new image
		//final int[] size = new int[ dimensionality ];
		// the offset relative to the output image which starts with its local coordinates (0,0,0)
		//final float[] offset = new float[ dimensionality ];
		
		for ( int d = 0; d < dimensionality; ++d )
		{
			size[ d ] = Math.round( maxImg[ d ] - minImg[ d ] );
			offset[ d ] = minImg[ d ];			
		}
		
		//IJ.log( "size: " + Util.printCoordinates( size ) );
		//IJ.log( "offset: " + Util.printCoordinates( offset ) );		
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
		ImageFactory< FloatType > f = new ImageFactory<FloatType>( new FloatType(), new ArrayContainerFactory() );
		Image< FloatType > img = f.createImage( new int[] { 400, 400 } ); 
		
		LocalizableCursor< FloatType > c = img.createLocalizableCursor();
		final int numDimensions = img.getNumDimensions();
		final float[] tmp = new float[ numDimensions ];
		
		// for blending
		final int[] dimensions = img.getDimensions();
		final float percentScaling = 0.2f;
		final float[] border = new float[ numDimensions ];
					
		while ( c.hasNext() )
		{
			c.fwd();
			
			for ( int d = 0; d < numDimensions; ++d )
				tmp[ d ] = c.getPosition( d );
			
			c.getType().set( (float)BlendingPixelFusion.computeWeight( tmp, dimensions, border, percentScaling ) );
		}
		
		ImageJFunctions.show( img );
		System.out.println( "done" );
	}
}
