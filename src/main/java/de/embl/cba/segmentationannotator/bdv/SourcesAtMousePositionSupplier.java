package de.embl.cba.segmentationannotator.bdv;

import bdv.util.BdvHandle;
import bdv.viewer.SourceAndConverter;
import sc.fiji.bdvpg.services.SourceAndConverterServices;
import sc.fiji.bdvpg.sourceandconverter.SourceAndConverterHelper;

import java.util.Collection;
import java.util.List;
import java.util.function.Supplier;
import java.util.stream.Collectors;

public class SourcesAtMousePositionSupplier implements Supplier< Collection< SourceAndConverter< ? > > >
{
	BdvHandle bdvHandle;
	boolean is2D;

	public SourcesAtMousePositionSupplier( BdvHandle bdvHandle, boolean is2D )
	{
		this.bdvHandle = bdvHandle;
		this.is2D = is2D;
	}

	@Override
	public Collection< SourceAndConverter< ? > > get()
	{
		final BdvCurrentGlobalMousePositionProvider positionProvider = new BdvCurrentGlobalMousePositionProvider( bdvHandle );

		final List< SourceAndConverter< ? > > sourceAndConverters = SourceAndConverterServices.getBdvDisplayService().getSourceAndConverterOf( bdvHandle )
				.stream()
				.filter( sac -> SourceAndConverterHelper.isPositionWithinSourceInterval( sac, positionProvider.getPosition(), positionProvider.getTimePoint(), is2D ) )
				.filter( sac -> SourceAndConverterServices.getBdvDisplayService().isVisible( sac, bdvHandle ) )
				.collect( Collectors.toList() );

		return sourceAndConverters;
	}
}
