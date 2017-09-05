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
package mpicbg.stitching;

import ij.ImagePlus;
import mpicbg.models.Model;
import mpicbg.models.Tile;
import mpicbg.models.TranslationModel2D;

public class ImagePlusTimePoint extends Tile implements Comparable< ImagePlusTimePoint >
{
	final ImagePlus imp;
	final int impId;
	final int timePoint, dimensionality;
	
	// might have one if called from grid/collection stitching
	final ImageCollectionElement element;
	
	public ImagePlusTimePoint( final ImagePlus imp, final int impId, final int timepoint, final Model model, final ImageCollectionElement element )
	{
		super( model );
		this.imp = imp;
		this.impId = impId;
		this.timePoint = timepoint;
		this.element = element;
		
		if ( TranslationModel2D.class.isInstance( model ) )
			dimensionality = 2;
		else
			dimensionality = 3;
	}
	
	public int getImpId() { return impId; }
	public ImagePlus getImagePlus() { return imp; }
	public int getTimePoint() { return timePoint; }
	public ImageCollectionElement getElement() { return element; }

	@Override
	public int compareTo( final ImagePlusTimePoint o ) 
	{
		if ( timePoint < o.timePoint )
			return -1;
		else if ( timePoint > o.timePoint )
			return 1;
		else
		{
			if ( impId < o.impId )
				return -1;
			else if ( impId > o.impId )
				return 1;
			else 
				return 0;
		}			
	}
}
