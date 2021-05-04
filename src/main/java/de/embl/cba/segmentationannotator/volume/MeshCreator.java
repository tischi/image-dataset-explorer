package de.embl.cba.segmentationannotator.volume;

import bdv.viewer.Source;
import customnode.CustomTriangleMesh;
import de.embl.cba.bdv.utils.objects3d.FloodFill;
import de.embl.cba.tables.Logger;
import de.embl.cba.tables.Utils;
import de.embl.cba.tables.ij3d.UniverseUtils;
import de.embl.cba.tables.imagesegment.ImageSegment;
import de.embl.cba.tables.mesh.MeshExtractor;
import isosurface.MeshEditor;
import net.imglib2.FinalInterval;
import net.imglib2.FinalRealInterval;
import net.imglib2.RandomAccessibleInterval;
import net.imglib2.algorithm.neighborhood.DiamondShape;
import net.imglib2.realtransform.AffineTransform3D;
import net.imglib2.type.numeric.RealType;
import net.imglib2.util.Intervals;
import net.imglib2.view.Views;
import org.scijava.vecmath.Point3f;

import java.util.ArrayList;
import java.util.Arrays;

public class MeshCreator< S extends ImageSegment >
{
	private int meshSmoothingIterations;
	private double maxNumSegmentVoxels;

	public MeshCreator( int meshSmoothingIterations, double maxNumSegmentVoxels )
	{
		this.meshSmoothingIterations = meshSmoothingIterations;
		this.maxNumSegmentVoxels = maxNumSegmentVoxels;
	}

	private float[] createMesh( ImageSegment segment, double voxelSpacing, Source< ? > labelsSource )
	{
		Integer level = 0; // getLevel( segment, labelsSource, voxelSpacing );
		double[] voxelSpacings = Utils.getVoxelSpacings( labelsSource ).get( level );

		final RandomAccessibleInterval< ? extends RealType< ? > > labelsRAI = ( RandomAccessibleInterval< ? extends RealType< ? > > ) labelsSource.getSource( segment.timePoint(), level  );

		if ( segment.boundingBox() == null )
			setSegmentBoundingBox( segment, labelsRAI, voxelSpacings );

		FinalInterval boundingBox = getIntervalInVoxelUnits( segment.boundingBox(), voxelSpacings );
		final long numElements = Intervals.numElements( boundingBox );

		if ( voxelSpacing == 0 ) // auto-resolution
		{
			if ( numElements > maxNumSegmentVoxels )
			{
				Logger.info( "# 3D View:\n" +
						"The bounding box of the selected segment has " + numElements + " voxels.\n" +
						"The maximum recommended number is however only " + maxNumSegmentVoxels + ".\n" +
						"It can take a bit of time to load...." );
			}
		}

		if ( ! Intervals.contains( labelsRAI, boundingBox ) )
		{
			System.err.println( "The segment bounding box " + boundingBox + " is not fully contained in the image interval: " + Arrays.toString( Intervals.minAsLongArray( labelsRAI ) ) + "-" +  Arrays.toString( Intervals.maxAsDoubleArray( labelsRAI ) ));
		}

		final MeshExtractor meshExtractor = new MeshExtractor(
				Views.extendZero( ( RandomAccessibleInterval ) labelsRAI ),
				boundingBox,
				new AffineTransform3D(),
				new int[]{ 1, 1, 1 },
				() -> false );

		final float[] meshCoordinates = meshExtractor.generateMesh( segment.labelId() );

		for ( int i = 0; i < meshCoordinates.length; )
		{
			meshCoordinates[ i++ ] *= voxelSpacings[ 0 ];
			meshCoordinates[ i++ ] *= voxelSpacings[ 1 ];
			meshCoordinates[ i++ ] *= voxelSpacings[ 2 ];
		}

		if ( meshCoordinates.length == 0 )
		{
			Logger.warn( "Could not find any pixels for segment with label " + segment.labelId()
					+ "\nwithin bounding box " + boundingBox );
			return null;
		}

		return meshCoordinates;
	}

	public CustomTriangleMesh createSmoothCustomTriangleMesh( S segment, double voxelSpacing, boolean recomputeMesh, Source< ? > source )
	{
		CustomTriangleMesh triangleMesh = createCustomTriangleMesh( segment, voxelSpacing, recomputeMesh, source );
		MeshEditor.smooth2( triangleMesh, meshSmoothingIterations );
		return triangleMesh;
	}

	private CustomTriangleMesh createCustomTriangleMesh( S segment, double voxelSpacing, boolean recomputeMesh, Source< ? > source )
	{
		if ( segment.getMesh() == null || recomputeMesh )
		{
			try
			{
				final float[] mesh = createMesh( segment, voxelSpacing, source );
				if ( mesh == null )
				{
					throw new RuntimeException( "Could not create mesh for segment " + segment.labelId() + " at time point " + segment.timePoint() );
				}
				segment.setMesh( mesh );
			}
			catch ( Exception e )
			{
				e.printStackTrace();
				throw new RuntimeException( "Could not create mesh for segment " + segment.labelId() + " at time point " + segment.timePoint() );
			}
		}

		CustomTriangleMesh triangleMesh = asCustomTriangleMesh( segment.getMesh() );

		return triangleMesh;
	}

	private static CustomTriangleMesh asCustomTriangleMesh( final float[] meshCoordinates )
	{
		final ArrayList< Point3f > points = new ArrayList<>();

		for ( int i = 0; i < meshCoordinates.length; )
		{
			points.add( new Point3f(
					meshCoordinates[ i++ ],
					meshCoordinates[ i++ ],
					meshCoordinates[ i++ ] ) );
		}

		CustomTriangleMesh mesh = new CustomTriangleMesh( points );

		return mesh;
	}

	private Integer getLevel( ImageSegment segment, Source< ? > labelSource, double voxelSpacing )
	{
		Integer level;

		if ( voxelSpacing != 0 )
		{
			level = UniverseUtils.getLevel( labelSource, voxelSpacing );
		}
		else // auto-resolution
		{
			if ( segment.boundingBox() == null )
			{
				Logger.error( "3D View:\n" +
						"Automated resolution level selection is enabled, but the segment has no bounding box.\n" +
						"This combination is currently not possible." );
				level = null;
			}
			else
			{
				final ArrayList< double[] > voxelSpacings = Utils.getVoxelSpacings( labelSource );

				final int numLevels = voxelSpacings.size();

				for ( level = 0; level < numLevels; level++ )
				{
					FinalInterval boundingBox = getIntervalInVoxelUnits( segment.boundingBox(), voxelSpacings.get( level ) );

					final long numElements = Intervals.numElements( boundingBox );

					if ( numElements <= maxNumSegmentVoxels )
						break;
				}

				if ( level == numLevels ) level = numLevels - 1;
			}
		}

		return level;
	}

	private void setSegmentBoundingBox(
			ImageSegment segment,
			RandomAccessibleInterval< ? extends RealType< ? > > labelsRAI,
			double[] voxelSpacing )
	{
		final long[] voxelCoordinate = getSegmentLocationInVoxelsUnits( segment, voxelSpacing );

		final FloodFill floodFill = new FloodFill(
				labelsRAI,
				new DiamondShape( 1 ),
				1000 * 1000 * 1000L );

		floodFill.run( voxelCoordinate );
		final RandomAccessibleInterval mask = floodFill.getCroppedRegionMask();

		final int numDimensions = segment.numDimensions();
		final double[] min = new double[ numDimensions ];
		final double[] max = new double[ numDimensions ];
		for ( int d = 0; d < numDimensions; d++ )
		{
			min[ d ] = mask.min( d ) * voxelSpacing[ d ];
			max[ d ] = mask.max( d ) * voxelSpacing[ d ];
		}

		segment.setBoundingBox( new FinalRealInterval( min, max ) );
	}

	private static long[] getSegmentLocationInVoxelsUnits(
			ImageSegment segment,
			double[] calibration )
	{
		final long[] voxelCoordinate = new long[ segment.numDimensions() ];
		for ( int d = 0; d < segment.numDimensions(); d++ )
			voxelCoordinate[ d ] = ( long ) ( segment.getDoublePosition( d ) / calibration[ d ] );
		return voxelCoordinate;
	}

	private FinalInterval getIntervalInVoxelUnits(
			FinalRealInterval realInterval,
			double[] calibration )
	{
		final long[] min = new long[ 3 ];
		final long[] max = new long[ 3 ];
		for ( int d = 0; d < 3; d++ )
		{
			min[ d ] = (long) ( realInterval.realMin( d ) / calibration[ d ] );
			max[ d ] = (long) ( realInterval.realMax( d ) / calibration[ d ] );
		}
		return new FinalInterval( min, max );
	}

}
