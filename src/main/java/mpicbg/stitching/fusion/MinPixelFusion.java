package mpicbg.stitching.fusion;

public class MinPixelFusion implements PixelFusion 
{
	double min;
	boolean set;
	
	public MinPixelFusion() { clear(); }
	
	@Override
	public void clear() 
	{ 
		min = 0;
		set = false;
	}

	@Override
	public void addValue( final double value, final int imageId, final double[] localPosition )
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

	@Override
	public double getValue() { return min; }

	@Override
	public PixelFusion copy() { return new MinPixelFusion(); }
}
