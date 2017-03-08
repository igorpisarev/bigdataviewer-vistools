package bdv.writecache.backingimage;

import static net.imglib2.cache.img.DiskCachedCellImgOptions.options;

import java.io.IOException;
import java.lang.reflect.InvocationTargetException;
import java.util.Iterator;

import org.scijava.ui.behaviour.DragBehaviour;
import org.scijava.ui.behaviour.io.InputTriggerConfig;
import org.scijava.ui.behaviour.util.Behaviours;

import bdv.util.Bdv;
import bdv.util.BdvFunctions;
import bdv.util.BdvOptions;
import bdv.util.BdvSource;
import bdv.util.BdvStackSource;
import bdv.viewer.ViewerPanel;
import net.imglib2.RandomAccess;
import net.imglib2.RandomAccessible;
import net.imglib2.algorithm.neighborhood.HyperSphereShape;
import net.imglib2.algorithm.neighborhood.Neighborhood;
import net.imglib2.cache.CacheLoader;
import net.imglib2.cache.img.DiskCachedCellImgFactory;
import net.imglib2.cache.img.DiskCachedCellImgOptions;
import net.imglib2.cache.img.DiskCachedCellImgOptions.CacheType;
import net.imglib2.img.Img;
import net.imglib2.img.array.ArrayImg;
import net.imglib2.img.array.ArrayImgs;
import net.imglib2.img.basictypeaccess.array.ArrayDataAccess;
import net.imglib2.img.basictypeaccess.array.DirtyLongArray;
import net.imglib2.img.basictypeaccess.array.LongArray;
import net.imglib2.img.cell.Cell;
import net.imglib2.img.cell.CellGrid;
import net.imglib2.img.cell.LazyCellImg;
import net.imglib2.realtransform.AffineTransform2D;
import net.imglib2.realtransform.RealViews;
import net.imglib2.type.NativeType;
import net.imglib2.type.numeric.ARGBType;
import net.imglib2.type.numeric.integer.LongType;
import net.imglib2.util.Fraction;
import net.imglib2.util.Intervals;
import net.imglib2.view.Views;

public class Playground
{
	public static class RandomAccessibleLoader<
				T extends NativeType< T >,
				A extends ArrayDataAccess< A > >
			implements CacheLoader< Long, Cell< A > >
	{
		private final CellGrid grid;

		private final RandomAccessible< T > source;

		private final T type;

		private final A creator;

		private final Fraction entitiesPerPixel;

		public RandomAccessibleLoader(
				final CellGrid grid,
				final RandomAccessible< T > source,
				final T type,
				final A creator )
		{
			this.grid = grid;
			this.source = source;
			this.type = type;
			this.creator = creator;
			entitiesPerPixel = type.getEntitiesPerPixel();
		}

		@Override
		public Cell< A > get( final Long key ) throws Exception
		{
			final long index = key;

			final int n = grid.numDimensions();
			final long[] cellMin = new long[ n ];
			final int[] cellDims = new int[ n ];
			final long[] imgDims = new long[ n ];
			final long[] cellMax = new long[ n ];

			grid.getCellDimensions( index, cellMin, cellDims );
			final long numEntities = entitiesPerPixel.mulCeil( Intervals.numElements( cellDims ) );
			final A data = creator.createArray( ( int ) numEntities );

			for ( int d = 0; d < n; ++d )
			{
				cellMax[ d ] = cellMin[ d ] + cellDims[ d ] - 1;
				imgDims[ d ] = cellDims[ d ];
			}

			final ArrayImg< T, A > img = new ArrayImg<>(
					( A ) new LongArray( ( long[] ) data.getCurrentStorageArray() ),
					imgDims, entitiesPerPixel );
			try
			{
				LazyCellImg.linkType( type, img );
			}
			catch ( NoSuchMethodException | SecurityException | InstantiationException | IllegalAccessException | IllegalArgumentException | InvocationTargetException e )
			{
				throw new RuntimeException( e );
			}
			final Iterator< T > it = Views.interval( source, cellMin, cellMax ).iterator();
			for ( final T t : img )
				t.set( it.next() );

			return new Cell<>( cellDims, cellMin, data );
		}
	}

	public static void main( final String[] args ) throws IOException
	{
		System.setProperty( "apple.laf.useScreenMenuBar", "true" );

		final long scale = 1000;

		final MandelbrotRealRandomAccessible mb = new MandelbrotRealRandomAccessible();
		final AffineTransform2D transform = new AffineTransform2D();
		transform.set(
				scale, 0, 2 * scale,
				0, scale, 2 * scale );
		final RandomAccessible< LongType > source = RealViews.affine( mb, transform );


		final int[] cellDimensions = new int[] { 512, 512 };
		final long[] dimensions = new long[] { 4 * scale, 4 * scale };

		final CacheLoader< Long, Cell< DirtyLongArray > > loader = new RandomAccessibleLoader<>(
				new CellGrid( dimensions, cellDimensions ),
				source,
				new LongType(),
				new DirtyLongArray( 0 ) );
		final DiskCachedCellImgOptions options = options()
				.cellDimensions( cellDimensions )
				.cacheType( CacheType.BOUNDED );
		final Img< LongType > img = new DiskCachedCellImgFactory< LongType >( options ).create(
				dimensions,
				new LongType(),
				loader );

		// Hack: add and remove dummy source to avoid that the initial transform shows a full slice.
		final BdvStackSource< ARGBType > dummy = BdvFunctions.show( ArrayImgs.argbs( 100, 100 ), "Dummy", BdvOptions.options().is2D() );
		final BdvSource bdv = BdvFunctions.show( img, "Cached", Bdv.options().addTo( dummy ) );
		dummy.removeFromBdv();
		bdv.setDisplayRangeBounds( 0, 100 );

		final Behaviours behaviours = new Behaviours( new InputTriggerConfig() );
		behaviours.install( bdv.getBdvHandle().getTriggerbindings(), "paint" );

		behaviours.behaviour( new DragBehaviour()
		{
			void draw( final int x, final int y )
			{
				final ViewerPanel viewer = bdv.getBdvHandle().getViewerPanel();
				final RandomAccess< Neighborhood< LongType > > sphere = new HyperSphereShape( 10 ).neighborhoodsRandomAccessible( Views.extendZero( img ) ).randomAccess();
				final double[] pos = new double[] { x, y, 0 };
				viewer.displayToGlobalCoordinates( pos );
				sphere.setPosition( Math.round( pos[ 0 ] ), 0 );
				sphere.setPosition( Math.round( pos[ 1 ] ), 1 );
				sphere.get().forEach( t -> t.set( 0 ) );
				viewer.requestRepaint();
			}

			@Override
			public void init( final int x, final int y )
			{
				draw( x, y );
			}

			@Override
			public void end( final int x, final int y )
			{}

			@Override
			public void drag( final int x, final int y )
			{
				draw( x, y );
			}
		}, "paint", "D" );
	}
}
