package mpicbg.stitching.fusion;

public class MaxPixelFusion implements PixelFusion 
{
	double max;
	boolean set;
	
	public MaxPixelFusion() { clear(); }
	
	@Override
	public void clear()
	{
		set = false;
		max = 0; 
	}

	@Override
	public void addValue( final double value, final int imageId, final double[] localPosition ) 
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

	@Override
	public double getValue() { return max; }

	@Override
	public PixelFusion copy() { return new MaxPixelFusion(); }
}
