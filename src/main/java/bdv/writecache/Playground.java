package bdv.writecache;

import static net.imglib2.cache.img.AccessFlags.DIRTY;
import static net.imglib2.cache.img.PrimitiveType.INT;

import java.io.IOException;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

import org.scijava.ui.behaviour.DragBehaviour;
import org.scijava.ui.behaviour.io.InputTriggerConfig;
import org.scijava.ui.behaviour.util.Behaviours;

import bdv.util.Bdv;
import bdv.util.BdvFunctions;
import bdv.util.BdvStackSource;
import bdv.viewer.ViewerPanel;
import net.imglib2.RandomAccess;
import net.imglib2.algorithm.neighborhood.HyperSphereShape;
import net.imglib2.algorithm.neighborhood.Neighborhood;
import net.imglib2.cache.CacheLoader;
import net.imglib2.cache.IoSync;
import net.imglib2.cache.UncheckedLoadingCache;
import net.imglib2.cache.img.AccessIo;
import net.imglib2.cache.img.DirtyDiskCellCache;
import net.imglib2.cache.img.DiskCellCache;
import net.imglib2.cache.ref.GuardedStrongRefListenableCache;
import net.imglib2.img.Img;
import net.imglib2.img.array.ArrayImgs;
import net.imglib2.img.basictypeaccess.array.DirtyIntArray;
import net.imglib2.img.cell.Cell;
import net.imglib2.img.cell.CellGrid;
import net.imglib2.img.cell.LazyCellImg;
import net.imglib2.position.transform.Round;
import net.imglib2.type.numeric.ARGBType;
import net.imglib2.util.Fraction;
import net.imglib2.util.Intervals;
import net.imglib2.view.Views;

public class Playground
{
	public static class CheckerboardLoader implements CacheLoader< Long, Cell< DirtyIntArray > >
	{
		private final CellGrid grid;

		ConcurrentHashMap< Long, AtomicInteger > loadcounts = new ConcurrentHashMap<>();

		AtomicInteger loadcount = new AtomicInteger( 0 );

		public CheckerboardLoader( final CellGrid grid )
		{
			this.grid = grid;
		}

		@Override
		public Cell< DirtyIntArray > get( final Long key ) throws Exception
		{
			final long index = key;

//			final int count = loadcounts.computeIfAbsent( key, k -> new AtomicInteger( 0 ) ).incrementAndGet();
//			final int count = loadcount.incrementAndGet();
//			if ( count % 1024 == 0 )
//				System.out.println( count + "  (" + (count / 1024) + " GB)" );


			final int n = grid.numDimensions();
			final long[] cellMin = new long[ n ];
			final int[] cellDims = new int[ n ];
			grid.getCellDimensions( index, cellMin, cellDims );
			final int blocksize = ( int ) Intervals.numElements( cellDims );
			final DirtyIntArray array = new DirtyIntArray( blocksize );

			final long[] cellGridPosition = new long[ n ];
			grid.getCellGridPositionFlat( index, cellGridPosition );
			long sum = 0;
			for ( int d = 0; d < n; ++d )
				sum += cellGridPosition[ d ];
			final int color = ( sum & 0x01 ) == 0 ? 0xff000000 : 0xff000088;
//			color |= ( ( count % 256 ) << 8 );
			Arrays.fill( array.getCurrentStorageArray(), color );

			return new Cell<>( cellDims, cellMin, array );
		}
	}

	public static void main( final String[] args ) throws IOException
	{
		System.setProperty( "apple.laf.useScreenMenuBar", "true" );

		final int[] cellDimensions = new int[] { 64, 64, 64 };
		final long[] dimensions = new long[] { 100000, 100000, 100000 };

//		final Img< ARGBType > img = new DiskCachedCellImgFactory< ARGBType >( cellDimensions ).create( dimensions, new ARGBType() );

		final CellGrid grid = new CellGrid( dimensions, cellDimensions );
		final Path blockcache = DiskCellCache.createTempDirectory( "CellImg", true );
		final Fraction entitiesPerPixel = new ARGBType().getEntitiesPerPixel();
		final DiskCellCache< DirtyIntArray > diskcache = new DirtyDiskCellCache<>(
				blockcache,
				grid,
				new CheckerboardLoader( grid ),
				AccessIo.get( INT, DIRTY ),
				entitiesPerPixel );
		final IoSync< Long, Cell< DirtyIntArray > > iosync = new IoSync<>( diskcache );
		final UncheckedLoadingCache< Long, Cell< DirtyIntArray > > cache = new GuardedStrongRefListenableCache< Long, Cell< DirtyIntArray > >( 1000 )
				.withRemovalListener( iosync )
				.withLoader( iosync )
				.unchecked();
		final Img< ARGBType > img = new LazyCellImg<>( grid, new ARGBType(), cache::get );


		// Hack: add and remove dummy source to avoid that the initial transform shows a full slice.
		final BdvStackSource< ARGBType > dummy = BdvFunctions.show( ArrayImgs.argbs( 100, 100, 1 ), "Dummy" );
		final Bdv bdv = BdvFunctions.show( img, "Cached", Bdv.options().addTo( dummy ) );
		dummy.removeFromBdv();

		final Behaviours behaviours = new Behaviours( new InputTriggerConfig() );
		behaviours.install( bdv.getBdvHandle().getTriggerbindings(), "paint" );

		behaviours.behaviour( new DragBehaviour()
		{
			void draw( final int x, final int y )
			{
				final ViewerPanel viewer = bdv.getBdvHandle().getViewerPanel();
				final RandomAccess< Neighborhood< ARGBType > > sphere = new HyperSphereShape( 10 ).neighborhoodsRandomAccessible( Views.extendZero( img ) ).randomAccess();
				viewer.displayToGlobalCoordinates( x, y, new Round<>( sphere ) );
				sphere.get().forEach( t -> t.set( 0xFFFF0000 ) );
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
