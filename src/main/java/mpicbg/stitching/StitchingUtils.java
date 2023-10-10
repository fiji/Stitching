/*-
 * #%L
 * Fiji distribution of ImageJ for the life sciences.
 * %%
 * Copyright (C) 2007 - 2020 Fiji developers.
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

package mpicbg.stitching;

import java.util.ArrayList;

import ij.ImagePlus;
import mpicbg.models.InvertibleBoundable;
import mpicbg.models.TranslationModel2D;
import mpicbg.models.TranslationModel3D;
import mpicbg.stitching.fusion.Fusion;
import net.imglib2.type.numeric.integer.UnsignedByteType;
import net.imglib2.type.numeric.integer.UnsignedShortType;
import net.imglib2.type.numeric.real.FloatType;

/**
 * 
 * @author Jan Eglinger
 *
 */
public class StitchingUtils {

	public static final int BLENDING_FUSION = 0;
	public static final int AVERAGE_FUSION = 1;
	public static final int MEDIAN_FUSION = 2;
	public static final int MAX_FUSION = 3;
	public static final int MIN_FUSION = 4;
	public static final int OVERLAP_FUSION = 5;

	private StitchingUtils() {
		// prevent instantiation of static utility class
	}

	/**
	 * Compute optimal tile positions from a list of known initial positions
	 * 
	 * @param images List of tiles
	 * @param positions List of known positions
	 * @param dimensionality 2 or 3
	 * @param computeOverlap whether to compute the exact tile overlap, or to trust the known coordinates
	 * @return List of transformation models
	 */
	public static ArrayList<InvertibleBoundable> computeStitching(ArrayList<ImagePlus> images, ArrayList<float[]> positions, int dimensionality, boolean computeOverlap) {
		if (images.size() != positions.size()) {
			throw new RuntimeException("number of images != number of positions");
		}

		// Create tile list
		ArrayList<ImageCollectionElement> elements = new ArrayList<>();
		float[] pos;
		for (int i = 0; i < images.size(); i++) {
			ImageCollectionElement element = new ImageCollectionElement(null, i);
			element.setDimensionality( dimensionality );
			element.setModel(dimensionality == 2 ? new TranslationModel2D() : new TranslationModel3D());
			element.setImagePlus(images.get(i));
			if (dimensionality == 2) {
				element.setOffset(positions.get(i));
			} else {
				pos = positions.get(i);
				element.setOffset(new float[] {pos[0], pos[1], 0});
			}
			elements.add(element);
		}
		
		// Create parameters
		StitchingParameters params = new StitchingParameters();
		params.cpuMemChoice = 1; // faster, use more RAM
		params.dimensionality = dimensionality;
		params.computeOverlap = computeOverlap;
		params.checkPeaks = 5;
		params.subpixelAccuracy = true;

		// Compute stitching
		ArrayList<ImagePlusTimePoint> tiles = CollectionStitchingImgLib.stitchCollection(elements, params);

		// Extract models
		ArrayList<InvertibleBoundable> models = new ArrayList<>();
		for (ImagePlusTimePoint tile : tiles) {
			models.add((InvertibleBoundable) tile.getModel());
		}
		return models;
	}

	/**
	 * Fuse a set of tiles, given a set of transformation models and a fusion type
	 * 
	 * @param images List of tiles
	 * @param models List of transformation models
	 * @param dimensionality 2 or 3
	 * @param fusionType Type of fusion, one of the following:
	 * <ul>
	 *   <li>{@code BLENDING_FUSION}</li>
	 *   <li>{@code AVERAGE_FUSION}</li>
	 *   <li>{@code MEDIAN_FUSION}</li>
	 *   <li>{@code MAX_FUSION}</li>
	 *   <li>{@code MIN_FUSION}</li>
	 *   <li>{@code OVERLAP_FUSION}</li>
	 * </ul>
	 * @return fused image
	 */
	public static ImagePlus fuseTiles(ArrayList<ImagePlus> images, ArrayList<InvertibleBoundable> models, int dimensionality, int fusionType) {
		switch (images.get(0).getType()) {
			case ImagePlus.GRAY8:
				return Fusion.fuse(new UnsignedByteType(), images, models, dimensionality, true, fusionType, null, false, false, false);
			case ImagePlus.GRAY16:
				return Fusion.fuse(new UnsignedShortType(), images, models, dimensionality, true, fusionType, null, false, false, false);
			case ImagePlus.GRAY32:
				return Fusion.fuse(new FloatType(), images, models, dimensionality, true, fusionType, null, false, false, false);
			default:
				throw new RuntimeException("Unknown image type for fusion");
		}
	}

	/**
	 * Fuse a set of tiles, given a set of transformation models
	 * 
	 * @param images List of tiles
	 * @param models List of transformation models
	 * @param dimensionality 2 or 3
	 * @return fused image
	 */
	public static ImagePlus fuseTiles(ArrayList<ImagePlus> images, ArrayList<InvertibleBoundable> models, int dimensionality) {
		return fuseTiles(images, models, dimensionality, BLENDING_FUSION);
	}
}
