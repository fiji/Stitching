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
package plugin;

import static stitching.CommonFunctions.addHyperLinkListener;
import fiji.util.gui.GenericDialogPlus;
import ij.IJ;
import ij.gui.MultiLineLabel;

import java.awt.Choice;
import java.awt.event.ItemEvent;
import java.awt.event.ItemListener;
import java.util.LinkedHashSet;

import javax.swing.ImageIcon;
import javax.swing.JLabel;

public class GridType 
{
	final private String paperURL = "http://bioinformatics.oxfordjournals.org/cgi/content/abstract/btp184";

	// Modified by John Lapage: added the 'Sequential Images' option. 	
    // This defines the value returned by getType() later:
	final public static String[] choose1 = new String[]{
        "Grid: row-by-row",           // 0
        "Grid: column-by-column",     // 1
        "Grid: snake by rows",        // 2
        "Grid: snake by columns",     // 3
        "Filename defined position",  // 4
        "Unknown position",           // 5
        "Positions from file",        // 6
        "Sequential Images"           // 7
    };
	final public static String[][] choose2 = new String[ choose1.length ][];
	final public static String[] allChoices;
	
	final public ImageIcon[][] images = new ImageIcon[ choose1.length ][];
	int type = -1, order = -1;
	
	public GridType()
	{
		images[ 0 ] = new ImageIcon[ 4 ];
		images[ 0 ][ 0 ] = GenericDialogPlus.createImageIcon( getClass().getResource( "/images/row1.png" ) );
		images[ 0 ][ 1 ] = GenericDialogPlus.createImageIcon( getClass().getResource( "/images/row2.png" ) );
		images[ 0 ][ 2 ] = GenericDialogPlus.createImageIcon( getClass().getResource( "/images/row3.png" ) );
		images[ 0 ][ 3 ] = GenericDialogPlus.createImageIcon( getClass().getResource( "/images/row4.png" ) );

		images[ 1 ] = new ImageIcon[ 4 ];
		images[ 1 ][ 0 ] = GenericDialogPlus.createImageIcon( getClass().getResource( "/images/column1.png" ) );
		images[ 1 ][ 1 ] = GenericDialogPlus.createImageIcon( getClass().getResource( "/images/column2.png" ) );
		images[ 1 ][ 2 ] = GenericDialogPlus.createImageIcon( getClass().getResource( "/images/column3.png" ) );
		images[ 1 ][ 3 ] = GenericDialogPlus.createImageIcon( getClass().getResource( "/images/column4.png" ) );

		images[ 2 ] = new ImageIcon[ 4 ];
		images[ 2 ][ 0 ] = GenericDialogPlus.createImageIcon( getClass().getResource( "/images/snake1.png" ) );
		images[ 2 ][ 1 ] = GenericDialogPlus.createImageIcon( getClass().getResource( "/images/snake3.png" ) );
		images[ 2 ][ 2 ] = GenericDialogPlus.createImageIcon( getClass().getResource( "/images/snake5.png" ) );
		images[ 2 ][ 3 ] = GenericDialogPlus.createImageIcon( getClass().getResource( "/images/snake7.png" ) );

		images[ 3 ] = new ImageIcon[ 4 ];
		images[ 3 ] = new ImageIcon[ 4 ];
		images[ 3 ][ 0 ] = GenericDialogPlus.createImageIcon( getClass().getResource( "/images/snake2.png" ) );
		images[ 3 ][ 1 ] = GenericDialogPlus.createImageIcon( getClass().getResource( "/images/snake4.png" ) );
		images[ 3 ][ 2 ] = GenericDialogPlus.createImageIcon( getClass().getResource( "/images/snake6.png" ) );
		images[ 3 ][ 3 ] = GenericDialogPlus.createImageIcon( getClass().getResource( "/images/snake8.png" ) );

		images[ 4 ] = new ImageIcon[ 1 ];
		images[ 4 ][ 0 ] = GenericDialogPlus.createImageIcon( getClass().getResource( "/images/position.png" ) );
		
		images[ 5 ] = new ImageIcon[ 1 ];
		images[ 5 ][ 0 ] = GenericDialogPlus.createImageIcon( getClass().getResource( "/images/unknown.png" ) );

		images[ 6 ] = new ImageIcon[ 2 ];
		images[ 6 ][ 0 ] = GenericDialogPlus.createImageIcon( getClass().getResource( "/images/fromFile.png" ) );
		images[ 6 ][ 1 ] = GenericDialogPlus.createImageIcon( getClass().getResource( "/images/fromFile.png" ) );
		
		// John Lapage added this: use the correct image if sequential images is selected
		images[ 7 ] = new ImageIcon[ 1 ];
		images[ 7 ][ 0 ] = GenericDialogPlus.createImageIcon( getClass().getResource( "/images/sequential.png" ) );

		final GenericDialogPlus gd = new GenericDialogPlus( "Grid/Collection stitching" );
		gd.addChoice( "Type", choose1, choose1[ Stitching_Grid.defaultGridChoice1 ] );
		
		if ( !IJ.macroRunning() )
		{
			gd.addChoice( "Order", choose2[ Stitching_Grid.defaultGridChoice1 ], choose2[ Stitching_Grid.defaultGridChoice1 ][ Stitching_Grid.defaultGridChoice2 ] );
			
			try
			{
				final ImageIcon display = new ImageIcon( images[ Stitching_Grid.defaultGridChoice1 ][ Stitching_Grid.defaultGridChoice2 ].getImage() );
				final JLabel label = gd.addImage( display );	
				
				// start the listener
				imageSwitch( (Choice) gd.getChoices().get(0), (Choice) gd.getChoices().get(1), images, display, label );
			}
			catch (Exception e )
			{
				gd.addMessage( "" );
				gd.addMessage( "Cannot load images to visualize the grid types ... " );
			}
		}
		else
		{
			// the interactive changing is not compatible with the macro language, 
			// thats why we show all possible options and figure out what was meant
			gd.addChoice( "Order", allChoices, allChoices[ 0 ] );
		}

		gd.addMessage( "Please note that the Stitching is\n" +
					   "based on a publication. If you use\n" + 
					   "it for your research please be so\n" +
					   "kind to cite us:\n" +
					   "Preibisch et al., Bioinformatics (2009)" );
		
		MultiLineLabel text = (MultiLineLabel) gd.getMessage();
		addHyperLinkListener( text, paperURL );

		gd.showDialog();
		
		if ( gd.wasCanceled() )
			return;
		
		type = Stitching_Grid.defaultGridChoice1 = gd.getNextChoiceIndex();

		if ( !IJ.macroRunning() )
		{
			order = gd.getNextChoiceIndex();
		}
		else
		{
			final int tmp = gd.getNextChoiceIndex();
			
			// position
			if ( tmp == 11 )
				order = 1;
			else if ( tmp >= 8 )
				order = 0;
			else
				order = tmp % 4;				
		}

		Stitching_Grid.defaultGridChoice2 = order;
	}
	
	public int getType() { return type; }
	public int getOrder() { return order; }
	
    // order options definitions:
	static
	{
        // 0: Grid: row-by-row
		choose2[ 0 ] = new String[]{ "Right & Down                ", "Left & Down", "Right & Up", "Left & Up" };
        // 1: Grid: column-by-column
		choose2[ 1 ] = new String[]{ "Down & Right                ", "Down & Left", "Up & Right", "Up & Left" };
        // 2: Grid: snake by rows
		choose2[ 2 ] = new String[]{ "Right & Down                ", "Left & Down", "Right & Up", "Left & Up" };
        // 3: Grid: snake by columns
		choose2[ 3 ] = new String[]{ "Down & Right                ", "Down & Left", "Up & Right", "Up & Left" };
        // 4: Filename defined position
		choose2[ 4 ] = new String[]{ "Defined by filename         " };
        // 5: Unknown position
		choose2[ 5 ] = new String[]{ "All files in directory" };
        // 6: Positions from file
		choose2[ 6 ] = new String[]{ "Defined by TileConfiguration", "Defined by image metadata" };
        // 7: Sequential Images
		choose2[ 7 ] = new String[]{ "All files in directory" };

		// the interactive changing is not compatible with the macro language, 
		// thats why we show all possible options and figure out what was meant
		final LinkedHashSet<String> allChoicesSet = new LinkedHashSet<String>();
		for ( final String[] choices : choose2 )
			for ( final String choice : choices )
				allChoicesSet.add(choice);
		allChoices = allChoicesSet.toArray( new String[ allChoicesSet.size() ] );
	}
	
	protected final void imageSwitch( final Choice choice1, final Choice choice2, final ImageIcon[][] images, final ImageIcon display, final JLabel label )
	{
		choice1.addItemListener( new ItemListener()
		{
			@Override
			public void itemStateChanged(ItemEvent ie)
			{
				try
				{
					final int state1 = choice1.getSelectedIndex();
					final int state2;
					
					if ( state1 == 4 )
						state2 = 0;
					else
						state2 = choice2.getSelectedIndex();
					
					// update the texts in choice2
					choice2.removeAll();
					for ( int i = 0; i < choose2[ state1 ].length; ++i )
						choice2.add( choose2[ state1 ][ i ] );
					
					choice2.select( state2 );
					
					
					display.setImage( images[ state1 ][ state2 ].getImage() );
					label.update( label.getGraphics() );
				}
				catch( Exception e ){}
			}
		});

		choice2.addItemListener( new ItemListener()
		{
			@Override
			public void itemStateChanged(ItemEvent ie)
			{
				try
				{
					final int state1 = choice1.getSelectedIndex();
					final int state2;
					
					if ( state1 == 4 )
						state2 = 0;
					else
						state2 = choice2.getSelectedIndex();
						
					display.setImage( images[ state1 ][ state2 ].getImage() );
					label.update( label.getGraphics() );
				}
				catch( Exception e ){}
			}
		});
	}
	
}
