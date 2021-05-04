package de.embl.cba.segmentationannotator.bdv;

import bdv.util.BdvHandle;
import net.imglib2.RealPoint;

public class BdvCurrentGlobalMousePositionProvider
{
	private final RealPoint position;
	private final int timePoint;

	public BdvCurrentGlobalMousePositionProvider( BdvHandle bdvHandle )
	{
		// Gets mouse location in space (global 3D coordinates) and time
		position = new RealPoint( 3 );
		bdvHandle.getBdvHandle().getViewerPanel().getGlobalMouseCoordinates( position );
		timePoint = bdvHandle.getViewerPanel().state().getCurrentTimepoint();
	}

	public RealPoint getPosition()
	{
		return position;
	}

	public int getTimePoint()
	{
		return timePoint;
	}
}
