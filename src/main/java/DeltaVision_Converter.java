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
import static stitching.CommonFunctions.addHyperLinkListener;
import fiji.util.gui.GenericDialogPlus;
import ij.IJ;
import ij.gui.MultiLineLabel;
import ij.plugin.PlugIn;

import java.io.BufferedReader;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintWriter;

import stitching.utils.Log;


public class DeltaVision_Converter implements PlugIn
{
	final private String myURL = "http://fly.mpi-cbg.de/~preibisch/contact.html";
	protected String dvLogFileStatic = "";//"D:/Development/eclipse/Fiji/test03_R3D.dv.log";
		
	@Override
	public void run( String args )
	{
		
		GenericDialogPlus gd = new GenericDialogPlus("Delta Vision Log File Converter");
		gd.addFileField( "DeltaVision log file", dvLogFileStatic, 50 );		
		gd.addMessage("");
		gd.addMessage("This Plugin is developed by Stephan Preibisch\n" + myURL);

		MultiLineLabel text = (MultiLineLabel) gd.getMessage();
		addHyperLinkListener(text, myURL);
		
		gd.showDialog();
		if (gd.wasCanceled()) 
			return;
		
		dvLogFileStatic = gd.getNextString();
		String dvLogFile = dvLogFileStatic;		
				
		final BufferedReader in;
		
		int dim=-1, z=-1, w=-1, t=-1, sizeX=-1, sizeY=-1;
		double xRes=-1, yRes=-1, zRes=-1;
		
		try
		{
			in = openFileReadEx( dvLogFile );
			
			boolean foundExperimentRecord = false;
		
			// extract information
			while ( in.ready() && !foundExperimentRecord )
			{
				String line = in.readLine().trim();
				
				if ( line.contains( "ZWT Dimensions" ) )
				{
					String info = line.substring( line.indexOf(':') + 1, line.length() );

					String[] content = info.split("x");
					z = Integer.parseInt( content[0].trim() );
					w = Integer.parseInt( content[1].trim() );
					t = Integer.parseInt( content[2].trim() );
					
				}
				
				if ( line.contains( "Pixel Size" ))
				{
					String info = line.substring( line.indexOf(':') + 1, line.length() ).trim();

					String[] content = info.split(" ");
					xRes = Double.parseDouble( content[0].trim() );
					yRes = Double.parseDouble( content[1].trim() );
					zRes = Double.parseDouble( content[2].trim() );					
				}
				
				if ( line.contains( "XY Dimensions" ) )
				{
					String info = line.substring( line.indexOf(':') + 1, line.length() );

					String[] content = info.split("x");
					sizeX = Integer.parseInt( content[0].trim() );
					sizeY = Integer.parseInt( content[1].trim() );					
				}
				
				if ( line.contains( "Experiment Record:" ))
					foundExperimentRecord = true;
			}
			
			
			if ( z > 1 )
				dim = 3;
			else
				dim = 2;
			
			Log.info( "slices: " + z + ", dimensionality = " + dim );
			Log.info( "channels: " + w );
			Log.info( "timepoints: " + t );
			Log.info( "x-resolution: " + xRes );
			Log.info( "y-resolution: " + yRes );
			Log.info( "z-resolution: " + zRes );
			Log.info( "image size x: " + sizeX );
			Log.info( "image size y: " + sizeY );

			if ( t > 1 )
			{
				Log.error( "Cannot handle more than one timepoint" );
				return;
			}
			
			PrintWriter out = openFileWriteEx( dvLogFile + ".tileconfiguration.txt" );
			
			out.println("# Define the number of dimensions we are working on");
	        out.println("dim = " + dim);
	        out.println("");
	        out.println("# Define the image coordinates");

			
			int countChannel = 0;
			int countImages = 0;
			
			while ( in.ready() )
			{
				String line = in.readLine().trim();
				
				if ( line.contains( "Stage coordinates:" ) && countChannel++ % w == 0)
				{
					String coordinates = line.substring( line.indexOf('(') + 1, line.indexOf( ')') );
					
					String[] content = coordinates.split(",");
					
					double xPos = Double.parseDouble( content[0].trim() ) / xRes;
					double yPos = Double.parseDouble( content[1].trim() ) / yRes;
					double zPos = Double.parseDouble( content[2].trim() ) / zRes;
					
			        if (dim == 3)
		    			out.println( "image_" + Stitch_Image_Grid.getLeadingZeros( 8, countImages) + "; ; (" + xPos + ", " + yPos + ", " + zPos + ")");
		    		else
		    			out.println( "image_" + Stitch_Image_Grid.getLeadingZeros( 8, countImages) + "; ; (" + xPos + ", " + yPos + ")");
			        
			        ++countImages;
				}
			}
			
			in.close();
			out.close();
		}
		catch ( Exception e )
		{
			Log.error( "Cannot open file '" + dvLogFile + "': " + e );
			return;
		}
		
				
	}	
	
	public static void main( String[] args )
	{
		DeltaVision_Converter dv = new DeltaVision_Converter();
		dv.run( "" );
	}
	
	public static PrintWriter openFileWriteEx( final String fileName ) throws IOException { return new PrintWriter( new FileWriter( fileName ) ); }
	public static BufferedReader openFileReadEx( final String fileName ) throws IOException{ return new BufferedReader( new FileReader( fileName ) ); }	
	
}
