/*-
 * #%L
 * ImageJ plugins for processing VisiView datasets
 * %%
 * Copyright (C) 2019 Friedrich Miescher Institute for Biomedical
 * 			Research, Basel
 * %%
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions are met:
 * 
 * 1. Redistributions of source code must retain the above copyright notice,
 *    this list of conditions and the following disclaimer.
 * 2. Redistributions in binary form must reproduce the above copyright notice,
 *    this list of conditions and the following disclaimer in the documentation
 *    and/or other materials provided with the distribution.
 * 
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS"
 * AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE
 * IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE
 * ARE DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT HOLDERS OR CONTRIBUTORS BE
 * LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR
 * CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF
 * SUBSTITUTE GOODS OR SERVICES; LOSS OF USE, DATA, OR PROFITS; OR BUSINESS
 * INTERRUPTION) HOWEVER CAUSED AND ON ANY THEORY OF LIABILITY, WHETHER IN
 * CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE)
 * ARISING IN ANY WAY OUT OF THE USE OF THIS SOFTWARE, EVEN IF ADVISED OF THE
 * POSSIBILITY OF SUCH DAMAGE.
 * #L%
 */

package ch.fmi.visiview;

import ij.ImagePlus;
import java.awt.Color;
import java.awt.Graphics;
import java.awt.image.BufferedImage;
import java.util.ArrayList;
import mpicbg.models.InvertibleBoundable;
import mpicbg.models.TranslationModel2D;
import mpicbg.models.TranslationModel3D;
import mpicbg.stitching.CollectionStitchingImgLib;
import mpicbg.stitching.ImageCollectionElement;
import mpicbg.stitching.ImagePlusTimePoint;
import mpicbg.stitching.StitchingParameters;
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
	 * Fuse a set of tiles, given a set of transformation models
	 * 
	 * @param images List of tiles
	 * @param models List of transformation models
	 * @param dimensionality 2 or 3
	 * @return fused image
	 */
	public static ImagePlus fuseTiles(ArrayList<ImagePlus> images, ArrayList<InvertibleBoundable> models, int dimensionality) {
		switch (images.get(0).getType()) {
			case ImagePlus.GRAY8:
				return Fusion.fuse(new UnsignedByteType(), images, models, dimensionality, true, 0, null, false, false, false);
			case ImagePlus.GRAY16:
				return Fusion.fuse(new UnsignedShortType(), images, models, dimensionality, true, 0, null, false, false, false);
			case ImagePlus.GRAY32:
				return Fusion.fuse(new FloatType(), images, models, dimensionality, true, 0, null, false, false, false);
			default:
				throw new RuntimeException("Unknown image type for fusion");
		}
	}

	/**
	 * Create a preview image of a tile layout
	 * 
	 * @param image {@code BufferedImage} to draw into
	 * @param pixelPositions List of positions (2D pixel coordinates)
	 * @param xSize Width of a single tile
	 * @param ySize Height of a single tile
	 */
	public static void drawPositions(BufferedImage image, ArrayList<float[]> pixelPositions, long xSize, long ySize)
	{
		Float xMin = Float.POSITIVE_INFINITY;
		Float yMin = Float.POSITIVE_INFINITY;
		Float xMax = Float.NEGATIVE_INFINITY;
		Float yMax = Float.NEGATIVE_INFINITY;

		for (float[] p : pixelPositions) {
			if (p[0] < xMin) xMin = p[0];
			if (p[1] < yMin) yMin = p[1];
			if (p[0] > xMax) xMax = p[0];
			if (p[1] > yMax) yMax = p[1];
		}

		long tileWidth = xSize;
		long tileHeight = ySize;

		xMax += tileWidth;
		yMax += tileHeight;

		int width = image.getWidth();
		int height = image.getHeight();
		double scaledWidth = xMax - xMin;
		double scaledHeight = yMax - yMin;
		Float offsetX = xMin;
		Float offsetY = yMin;
		double factor = Math.max(scaledWidth, scaledHeight) / Math.min(width,
			height);

		Graphics g = image.getGraphics();
		g.clearRect(0, 0, width, height);
		int rgb = 0;
		for (float[] p : pixelPositions) {
			rgb = nextColor(rgb);
			g.setXORMode(new Color(rgb)); // TODO replace by HSB color directly?
			g.fillRect((int) ((p[0] - offsetX) / factor), (int) ((p[1] - offsetY) /
				factor), (int) (tileWidth / factor), (int) (tileHeight / factor));
		}
		g.dispose();
	}

	private static int nextColor(int rgb) {
		// Create a "golden angle" color sequence for best contrast, see:
		// https://github.com/ijpb/MorphoLibJ/blob/c6c688f/src/main/java/inra/ijpb/util/ColorMaps.java#L315

		float[] hsb = Color.RGBtoHSB((rgb >> 16) & 0xFF, (rgb >> 8) & 0xFF, rgb &
			0xFF, null);
		hsb[0] += 0.38197;
		if (hsb[0] > 1) hsb[0] -= 1;
		hsb[1] += 0.38197;
		if (hsb[1] > 1) hsb[1] -= 1;
		hsb[1] = 0.5f * hsb[1] + 0.5f;
		return Color.HSBtoRGB(hsb[0], hsb[1], 1);
	}
}
