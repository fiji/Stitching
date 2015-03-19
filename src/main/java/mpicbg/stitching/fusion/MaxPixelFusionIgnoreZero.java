package mpicbg.stitching.fusion;

public class MaxPixelFusionIgnoreZero extends MaxPixelFusion 
{
	public MaxPixelFusionIgnoreZero() { super(); }
	
	@Override
	public void addValue( final double value, final int imageId, final double[] localPosition ) 
	{
		if ( value != 0.0 )
		{
			if ( set )
			{
				max = Math.max( value, max );
			}
			else
			{
				max = value;
				set = true;
			}
		}
	}

	@Override
	public PixelFusion copy() { return new MaxPixelFusionIgnoreZero(); }
}
