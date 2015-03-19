package mpicbg.stitching.fusion;

public class MinPixelFusionIgnoreZero extends MinPixelFusion 
{
	public MinPixelFusionIgnoreZero() { super(); }
	
	@Override
	public void addValue( final double value, final int imageId, final double[] localPosition )
	{
		if ( value != 0.0 )
		{
			if ( set )
			{
				min = Math.min( value, min );
			}
			else
			{
				min = value;
				set = true;
			}
		}
	}

	@Override
	public PixelFusion copy() { return new MinPixelFusionIgnoreZero(); }
}
