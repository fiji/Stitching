package mpicbg.stitching.fusion;

/**
 * This class implements a fusion where only the pixel value
 * of one of the images is used. In fact, there is no fusion
 * but a simple overlap of one image over the others. 
 * 
 * @author Ignacio Arganda Carreras (iarganda at mit.edu)
 *
 */
public class OverlapFusion implements PixelFusion {

	double lastValue;
	
	public OverlapFusion() { clear(); }
	
	@Override
	public void clear() {
		lastValue = 0;
	}

	@Override
	public void addValue(double value, int imageId, double[] localPosition) {
		lastValue = value;	
	}

	@Override
	public double getValue() {		
		return lastValue;
	}

	@Override
	public PixelFusion copy() {		
		return new OverlapFusion();
	}

}
