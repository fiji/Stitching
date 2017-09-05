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

import mpicbg.models.PointMatch;
import mpicbg.models.Tile;
import mpicbg.models.TileConfiguration;

public class TileConfigurationStitching extends TileConfiguration
{
	protected Tile< ? > worstTile = null;

	final public Tile getWorstTile() {	return worstTile; }

	/**
	 * Estimate min/max/average displacement of all
	 * {@link PointMatch PointMatches} in all {@link Tile Tiles}.
	 */
	@Override
	protected void updateErrors()
	{
		double cd = 0.0;
		minError = Double.MAX_VALUE;
		maxError = 0.0;
		for ( Tile< ? > t : tiles )
		{
			t.updateCost();
			double d = t.getDistance();
			if ( d < minError ) minError = d;
			
			// >= because if they are all 0.0, worstTile would be null
			if ( d >= maxError )
			{
				maxError = d;
				worstTile = t;
			}
			cd += d;
		}
		cd /= tiles.size();
		error = cd;

	}
	
}
