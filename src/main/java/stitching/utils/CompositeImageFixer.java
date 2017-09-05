/*
 * #%L
 * Fiji distribution of ImageJ for the life sciences.
 * %%
 * Copyright (C) 2007 - 2017 Fiji
 * %%
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as
 * published by the Free Software Foundation, either version 2 of the
 * License, or (at your option) any later version.
 * 
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 * 
 * You should have received a copy of the GNU General Public
 * License along with this program.  If not, see
 * <http://www.gnu.org/licenses/gpl-2.0.html>.
 * #L%
 */
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
