package mpicbg.stitching.fusion;

import java.util.ArrayList;
import java.util.Collections;

public class MedianPixelFusion implements PixelFusion
{
	final ArrayList< Double > list;
	
	public MedianPixelFusion() 
	{
		list = new ArrayList< Double >();
		clear(); 
	}
	
	@Override
	public void clear() { list.clear();	}

	@Override
	public void addValue( final double value, final int imageId, final double[] localPosition ) 
	{
		list.add( value );
	}

	@Override
	public double getValue() 
	{ 
		if ( list.size() == 0 )
			return 0;
		Collections.sort( list );
		final int size = list.size();
		
		if ( size % 2 == 1 )
			return list.get( size/2 );
		return (list.get( size/2 - 1) + list.get( size/2 ))/2.0f ;
	}
	
	@Override
	public PixelFusion copy() { return new MedianPixelFusion(); }
}
