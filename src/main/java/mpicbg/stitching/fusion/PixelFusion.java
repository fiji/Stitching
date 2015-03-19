package mpicbg.stitching.fusion;

public interface PixelFusion 
{
	/**
	 *  reset for the next output pixel
	 */
	void clear();
	
	/**
	 * add a new value for the current output pixel
	 * 
	 * @param value - the image intensity
	 * @param imageId - from which input image as defined by the id
	 * @param localPosition - the position inside the input image in local coordinates of the input image
	 */
	void addValue( double value, int imageId, double[] localPosition );
	
	/**
	 *  return the result for the current pixel
	 *  
	 * @return - the value for the output image
	 */
	double getValue();
	
	/**
	 * Convinience method for multi-threading
	 * 
	 * @return - a {@link PixelFusion} with the same properties
	 */
	PixelFusion copy();
}
