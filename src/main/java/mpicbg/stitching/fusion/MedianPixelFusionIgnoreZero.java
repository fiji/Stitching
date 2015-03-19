package mpicbg.stitching.fusion;


public class MedianPixelFusionIgnoreZero extends MedianPixelFusion
{
	public MedianPixelFusionIgnoreZero() { super(); }
	
	@Override
	public void addValue( final double value, final int imageId, final double[] localPosition ) 
	{
		if ( value != 0.0 )
			list.add( value );
	}

	@Override
	public PixelFusion copy() { return new MedianPixelFusionIgnoreZero(); }
}
