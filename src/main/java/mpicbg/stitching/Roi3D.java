package mpicbg.stitching;

import ij.gui.Roi;

public class Roi3D extends Roi {

	/**
	 *
	 */
	private static final long serialVersionUID = 2925653019163885354L;
	int startZ = 0;
	int depth = 0;

	public Roi3D(
			int x,
			int y,
			int z,
			int width,
			int height,
			int depth )
	{
		super( x, y, width, height );
		this.startZ = z;
		this.depth = depth;
	}

}
