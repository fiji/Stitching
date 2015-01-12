package mpicbg.stitching;

import ij.IJ;
import ij.gui.Roi;

import java.awt.Rectangle;
import java.util.ArrayList;
import java.util.Vector;
import java.util.concurrent.atomic.AtomicInteger;

import mpicbg.imglib.multithreading.SimpleMultiThreading;
import mpicbg.imglib.util.Util;
import mpicbg.models.TranslationModel2D;
import mpicbg.models.TranslationModel3D;

import java.util.Collections;

public class CollectionStitchingImgLib 
{

	public static ArrayList< ImagePlusTimePoint > stitchCollection( final ArrayList< ImageCollectionElement > elements, final StitchingParameters params )
	{
		// the result
		//final 
		ArrayList< ImagePlusTimePoint > optimized = new ArrayList< ImagePlusTimePoint >();

		
		if ( params.computeOverlap )
		{
			// find overlapping tiles
			final Vector< ComparePair > pairs = findOverlappingTiles( elements, params );
				
			if ( pairs == null || pairs.size() == 0 )
			{
				IJ.log( "No overlapping tiles could be found given the approximate layout." );
				return null;
			}
			
			// compute all compare pairs
			// compute all matchings
			final AtomicInteger ai = new AtomicInteger(0);
			
			final int numThreads;
			
			if ( params.cpuMemChoice == 0 )
				numThreads = 1;
			else
				numThreads = Runtime.getRuntime().availableProcessors();
			
	        final Thread[] threads = SimpleMultiThreading.newThreads( numThreads );
	    	
	        for ( int ithread = 0; ithread < threads.length; ++ithread )
	            threads[ ithread ] = new Thread(new Runnable()
	            {
	                @Override
	                public void run()
	                {		
	                   	final int myNumber = ai.getAndIncrement();
	                    
	                    for ( int i = 0; i < pairs.size(); i++ )
	                    {
	                    	if ( i % numThreads == myNumber )
	                    	{
	                    		final ComparePair pair = pairs.get( i );
	                    		
	                    		long start = System.currentTimeMillis();			
	                			
	                    		// where do we approximately overlap?
	                			final Roi roi1 = getROI( pair.getTile1().getElement(), pair.getTile2().getElement() );
	                			final Roi roi2 = getROI( pair.getTile2().getElement(), pair.getTile1().getElement() );
	                			
	            				final PairWiseStitchingResult result = PairWiseStitchingImgLib.stitchPairwise( pair.getImagePlus1(), pair.getImagePlus2(), roi1, roi2, pair.getTimePoint1(), pair.getTimePoint2(), params );			
	            				if ( result == null )
	            				{
	            					IJ.log( "Collection stitching failed" );
	            					return;
	            				}
	
	            				if ( params.dimensionality == 2 )
	            					pair.setRelativeShift( new float[]{ result.getOffset( 0 ), result.getOffset( 1 ) } );
	            				else
	            					pair.setRelativeShift( new float[]{ result.getOffset( 0 ), result.getOffset( 1 ), result.getOffset( 2 ) } );
	            				
	            				pair.setCrossCorrelation( result.getCrossCorrelation() );
	
	            				IJ.log( pair.getImagePlus1().getTitle() + "[" + pair.getTimePoint1() + "]" + " <- " + pair.getImagePlus2().getTitle() + "[" + pair.getTimePoint2() + "]" + ": " + 
	            						Util.printCoordinates( result.getOffset() ) + " correlation (R)=" + result.getCrossCorrelation() + " (" + (System.currentTimeMillis() - start) + " ms)");
	                    	}
	                    }
	                }
	            });
	        
	        final long time = System.currentTimeMillis();
	        SimpleMultiThreading.startAndJoin( threads );
	        
	        // get the final positions of all tiles
	        //optimized = GlobalOptimization.optimize( pairs, pairs.get( 0 ).getTile1(), params );
	        
	        //::dip Change (12.01.2015)
	        // find islets
	        // global optimization for all islets
	        // return values of global optimization in original coordinates
	        // add optimized islets to final list
			
	        Vector< ComparePair > tmpPairs = ( Vector< ComparePair > ) pairs.clone();
	        tmpPairs = cleanList(tmpPairs, params);
	        
	        while (tmpPairs.size() > 0){
	        	final Vector< ComparePair > isletPairs = findIsletList(tmpPairs, params);
	        	tmpPairs = removeFromTileList(tmpPairs, isletPairs);
				optimized.addAll( GlobalOptimization.optimize( isletPairs, isletPairs.get( 0 ).getTile1(), params ) );
	        }
			
			Collections.sort( optimized );
			//::dip end of Change (12.01.2015)			
			
			IJ.log( "Finished registration process (" + (System.currentTimeMillis() - time) + " ms)." );
		}
		else
		{
			// all ImagePlusTimePoints, each of them needs its own model
			optimized = new ArrayList< ImagePlusTimePoint >();
			
			for ( final ImageCollectionElement element : elements )
			{
				final ImagePlusTimePoint imt = new ImagePlusTimePoint( element.open( params.virtual ), element.getIndex(), 1, element.getModel(), element );
				
				// set the models to the offset
				if ( params.dimensionality == 2 )
				{
					final TranslationModel2D model = (TranslationModel2D)imt.getModel();
					model.set( element.getOffset( 0 ), element.getOffset( 1 ) );
				}
				else
				{
					final TranslationModel3D model = (TranslationModel3D)imt.getModel();
					model.set( element.getOffset( 0 ), element.getOffset( 1 ), element.getOffset( 2 ) );					
				}
				
				optimized.add( imt );
			}
			
		}
		
		return optimized;
	}

	protected static Roi getROI( final ImageCollectionElement e1, final ImageCollectionElement e2 )
	{
		final int start[] = new int[ 2 ], end[] = new int[ 2 ];
		
		for ( int dim = 0; dim < 2; ++dim )
		{			
			// begin of 2 lies inside 1
			if ( e2.offset[ dim ] >= e1.offset[ dim ] && e2.offset[ dim ] <= e1.offset[ dim ] + e1.size[ dim ] )
			{
				start[ dim ] = Math.round( e2.offset[ dim ] - e1.offset[ dim ] );
				
				// end of 2 lies inside 1
				if ( e2.offset[ dim ] + e2.size[ dim ] <= e1.offset[ dim ] + e1.size[ dim ] )
					end[ dim ] = Math.round( e2.offset[ dim ] + e2.size[ dim ] - e1.offset[ dim ] );
				else
					end[ dim ] = Math.round( e1.size[ dim ] );
			}
			else if ( e2.offset[ dim ] + e2.size[ dim ] <= e1.offset[ dim ] + e1.size[ dim ] ) // end of 2 lies inside 1
			{
				start[ dim ] = 0;
				end[ dim ] = Math.round( e2.offset[ dim ] + e2.size[ dim ] - e1.offset[ dim ] );
			}
			else // if both outside then the whole image 
			{
				start[ dim ] = -1;
				end[ dim ] = -1;
			}
		}
		
		return new Roi( new Rectangle( start[ 0 ], start[ 1 ], end[ 0 ] - start[ 0 ], end[ 1 ] - start[ 1 ] ) );
	}

	protected static Vector< ComparePair > findOverlappingTiles( final ArrayList< ImageCollectionElement > elements, final StitchingParameters params )
	{		
		for ( final ImageCollectionElement element : elements )
		{
			if ( element.open( params.virtual ) == null )
				return null;
		}
		
		// all ImagePlusTimePoints, each of them needs its own model
		final ArrayList< ImagePlusTimePoint > listImp = new ArrayList< ImagePlusTimePoint >();
		for ( final ImageCollectionElement element : elements )
			listImp.add( new ImagePlusTimePoint( element.open( params.virtual ), element.getIndex(), 1, element.getModel(), element ) );
	
		// get the connecting tiles
		final Vector< ComparePair > overlappingTiles = new Vector< ComparePair >();
		
		// Added by John Lapage: if the sequential option has been chosen, pair up each image 
		// with the images within the specified range, and return.
		if ( params.sequential )
		{
			for ( int i = 0; i < elements.size(); i++ )
			{
				for ( int j = 1 ; j <= params.seqRange ; j++ )
				{
					if ( ( i + j ) >= elements.size() ) 
						break;
					
					overlappingTiles.add( new ComparePair( listImp.get( i ), listImp.get( i+j ) ) );
				}
			}
			return overlappingTiles;
		}
		// end of addition

		for ( int i = 0; i < elements.size() - 1; i++ )
			for ( int j = i + 1; j < elements.size(); j++ )
			{
				final ImageCollectionElement e1 = elements.get( i );
				final ImageCollectionElement e2 = elements.get( j );
				
				boolean overlapping = true;
				
				for ( int d = 0; d < params.dimensionality; ++d )
				{
				    //::dip
				    // Is this correct / make sense ?
				    // Following line .... can never be true !!
				    // ( e2.offset[ d ] <= e1.offset[ d ] && e2.offset[ d ] >= e1.offset[ d ] + e1.size[ d ] ) 
				    // What is the meaning ?
				    // Shouldn't it be
				    // ( e2.offset[ d ] <= e1.offset[ d ] && (e2.offset[ d ] + e2.size[ d ]) >= (e1.offset[ d ] + e1.size[ d ]) ) 
					if ( !( ( e2.offset[ d ] >= e1.offset[ d ] && e2.offset[ d ] <= e1.offset[ d ] + e1.size[ d ] ) || 
						    ( e2.offset[ d ] + e2.size[ d ] >= e1.offset[ d ] && e2.offset[ d ] + e2.size[ d ] <= e1.offset[ d ] + e1.size[ d ] ) ||
						    ( e2.offset[ d ] <= e1.offset[ d ] && e2.offset[ d ] >= e1.offset[ d ] + e1.size[ d ] ) 
					   )  )
									overlapping = false;
				}
				
				if ( overlapping )
				{
					//final ImagePlusTimePoint impA = new ImagePlusTimePoint( e1.open(), e1.getIndex(), 1, e1.getModel().copy(), e1 );
					//final ImagePlusTimePoint impB = new ImagePlusTimePoint( e2.open(), e2.getIndex(), 1, e2.getModel().copy(), e2 );
					overlappingTiles.add( new ComparePair( listImp.get( i ), listImp.get( j ) ) );
				}
			}
		
		return overlappingTiles;
	}
	
	//::dip Change (12.01.2015): New function
	protected static Vector< ComparePair > cleanList( final Vector< ComparePair > pairs, final StitchingParameters params )
	{		
		final Vector< ComparePair > pairsOut = new Vector< ComparePair >();
		
		for ( int i = 0; i < pairs.size(); i++ ){
			if ( pairs.get(i).getCrossCorrelation() >= params.regThreshold )
				pairsOut.add( pairs.get( i ) );				
		}	
		return pairsOut;
	}
	
	//::dip Change (12.01.2015): New function
	protected static Vector< ComparePair > removeFromTileList( final Vector< ComparePair > pairsIn, final Vector< ComparePair > isletPairs)
	{		
		boolean inList = false;
		final Vector< ComparePair > pairsOut = new Vector< ComparePair >();
		
		for ( int i = 0; i < pairsIn.size(); i++ ){
			inList = false;
			for ( int j = 0; j < isletPairs.size(); j++ ){
				if ( pairsIn.get(i).equals(isletPairs.get(j)) )
					inList = true;	
			}
			if ( !inList )
				pairsOut.add( pairsIn.get( i ) );				
		}	
		return pairsOut;
	}

	//::dip Change (12.01.2015): New function
	protected static Vector< ComparePair > findIsletList( final Vector< ComparePair > pairs, final StitchingParameters params )
	{		
		boolean connected = false;
		final Vector< ComparePair > isletPairs = new Vector< ComparePair >();
		
		isletPairs.add( pairs.get( 0 ) );
		
		for ( int i = 1; i < pairs.size(); i++ ){
			connected = false;
			for ( int n = 0; n < isletPairs.size(); n++ ){
				connected |= isPairConnected(pairs.get(i), isletPairs.get(n));
				if (connected)
					break;
			}
			if ( connected )
				isletPairs.add( pairs.get( i ) );
		}	
		return isletPairs;
	}

	//::dip Change (12.01.2015): New function
	protected static boolean isPairConnected(final ComparePair pair, final ComparePair isletPair)
	{
		if (   (pair.getTile1().element.equals(isletPair.getTile1().element))
			|| (pair.getTile1().element.equals(isletPair.getTile2().element))
			|| (pair.getTile2().element.equals(isletPair.getTile1().element))
			|| (pair.getTile2().element.equals(isletPair.getTile2().element)) )
			return true;
		return false;
	}

	//::dip Change (12.01.2015): New function
	protected static boolean isConnected(final ImageCollectionElement e1, final ImageCollectionElement e2, final StitchingParameters params )
	{
		for ( int d = 0; d < params.dimensionality; ++d )
		{
			if ( !( ( e2.offset[ d ] >= e1.offset[ d ] && e2.offset[ d ] <= e1.offset[ d ] + e1.size[ d ] ) || 
				    ( e2.offset[ d ] + e2.size[ d ] >= e1.offset[ d ] && e2.offset[ d ] + e2.size[ d ] <= e1.offset[ d ] + e1.size[ d ] ) ||
				    //::dip
				    // Is this correct ?
				    // e2.offset < e1.offset && e2.offset > e1.offset+size   .... never be true !!
				    // What is the meaning ?
				    ( e2.offset[ d ] <= e1.offset[ d ] && e2.offset[ d ] >= e1.offset[ d ] + e1.size[ d ] ) 
				    // Should it be
				    // ( e2.offset[ d ] <= e1.offset[ d ] && (e2.offset[ d ] + e2.size[ d ]) >= (e1.offset[ d ] + e1.size[ d ]) ) 
			   )  )
				return false;
		}
		return true;
	}
	
	
	//::dip Change (12.01.2015): New function
	protected static Vector< ComparePair > findIsletTiles( final ArrayList< ImageCollectionElement > elements, final StitchingParameters params )
	{		
		boolean connected;
		
		if (elements.size() == 0)
			return null;
		
		for ( final ImageCollectionElement element : elements )
		{
			if ( element.open( params.virtual ) == null )
				return null;
		}
		
		// all ImagePlusTimePoints, each of them needs its own model
		final ArrayList< ImagePlusTimePoint > listImp = new ArrayList< ImagePlusTimePoint >();
		for ( final ImageCollectionElement element : elements )
			listImp.add( new ImagePlusTimePoint( element.open( params.virtual ), element.getIndex(), 1, element.getModel(), element ) );
		
		// get the connected tiles
		final Vector< ComparePair > isletTiles = new Vector< ComparePair >();
		
		for ( int i = 0; i < elements.size() - 1; i++ )
			for ( int j = i + 1; j < elements.size(); j++ )
			{
				final ImageCollectionElement e1 = elements.get( i );
				final ImageCollectionElement e2 = elements.get( j );
				
				connected = isConnected(e1, e2, params);
				
				if (connected && isletTiles.size() != 0){
					connected = false;
					for ( int n = 0; n < isletTiles.size(); n++ ){
						connected |= isConnected(e1, isletTiles.get(n).getTile1().getElement(), params);
						connected |= isConnected(e2, isletTiles.get(n).getTile1().getElement(), params);
						connected |= isConnected(e1, isletTiles.get(n).getTile2().getElement(), params);
						connected |= isConnected(e2, isletTiles.get(n).getTile2().getElement(), params);
						if (connected)
							break;
					}
				}
				
				if ( connected )
				{
					isletTiles.add( new ComparePair( listImp.get( i ), listImp.get( j ) ) );
				}
			}
		return isletTiles;
	}
	
}
