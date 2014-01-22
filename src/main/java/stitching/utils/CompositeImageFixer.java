package stitching.utils;

import ij.CompositeImage;
import ij.ImagePlus;

/**
 * Utility class to work around the {@link CompositeImage} nature of converting
 * stacks to channels.
 *
 * @author Mark Hiner
 */
public final class CompositeImageFixer {

	/**
	 * see {@link #makeComposite(ImagePlus, int)}
	 */
	public static CompositeImage makeComposite(ImagePlus imp) {
		return makeComposite(imp, CompositeImage.COLOR);
	}

	/**
	 * -- HACK --
	 * <p>
	 * Workaround for the fact that if there are less than 7 slices in an
	 * ImagePlus, the CompositeImage constructor will convert those slices
	 * to channels.
	 * </p>
	 * <p>
	 * This method will always construct a CompositeImage with the same
	 * dimensions as the input ImagePlus.
	 * </p>
	 */
	public static CompositeImage makeComposite(ImagePlus imp, int mode) {
		// cache the (correct) channel, frame and slice counts
		final int channels = imp.getNChannels();
		final int frames = imp.getNFrames();
		final int slices = imp.getNSlices();

		// construct the composite image
		final CompositeImage cmp = new CompositeImage(imp, mode);

		// reset the correct dimension counts
		cmp.setDimensions(channels, slices, frames);

		return cmp;
	}
}
