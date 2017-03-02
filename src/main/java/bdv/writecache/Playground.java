package bdv.writecache;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.Random;

import org.scijava.ui.behaviour.DragBehaviour;
import org.scijava.ui.behaviour.io.InputTriggerConfig;
import org.scijava.ui.behaviour.util.Behaviours;

import bdv.img.gencache.CachedCellImg;
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
import net.imglib2.cache.ref.SoftRefListenableCache;
import net.imglib2.img.array.ArrayImgs;
import net.imglib2.img.basictypeaccess.array.IntArray;
import net.imglib2.img.cell.Cell;
import net.imglib2.img.cell.CellGrid;
import net.imglib2.position.transform.Round;
import net.imglib2.type.numeric.ARGBType;
import net.imglib2.util.Intervals;
import net.imglib2.view.Views;

public class Playground
{
	public static boolean deleteDirectory( final File dir )
	{
		if ( dir.isDirectory() )
		{
			final File[] children = dir.listFiles();
			for ( int i = 0; i < children.length; i++ )
			{
				final boolean success = deleteDirectory( children[ i ] );
				if ( !success )
					return false;
			}
		}
		// either file or an empty directory
		return dir.delete();
	}

	public static class RandomLoader implements CacheLoader< Long, Cell< IntArray > >
	{
		private final Random random = new Random();

		private final CellGrid grid;

		public RandomLoader( final CellGrid grid )
		{
			this.grid = grid;
		}

		@Override
		public Cell< IntArray > get( final Long key ) throws Exception
		{
			final long index = key;
			final long[] cellMin = new long[ grid.numDimensions() ];
			final int[] cellDims = new int[ grid.numDimensions() ];
			grid.getCellDimensions( index, cellMin, cellDims );
			final int blocksize = ( int ) Intervals.numElements( cellDims );
			final IntArray array = new IntArray( blocksize );
			Arrays.fill( array.getCurrentStorageArray(), random.nextInt() & 0xFF0000FF );
			return new Cell<>( cellDims, cellMin, array );
		}
	}

	public static void main( final String[] args ) throws IOException
	{
		System.setProperty( "apple.laf.useScreenMenuBar", "true" );

		final Path blockcache = Paths.get( "/Users/pietzsch/Desktop/blockcache/" );
//		deleteDirectory( blockcache.toFile() );
		Files.createDirectories( blockcache );

		final long[] dimensions = new long[] { 640, 640, 640 };
		final int[] cellDimensions = new int[] { 32, 32, 32 };
		final CellGrid grid = new CellGrid( dimensions, cellDimensions );

		final ARGBType type = new ARGBType();

		final DiskCellCache< IntArray > diskcache = new DiskCellCache<>(
				blockcache,
				grid,
				new RandomLoader( grid ),
				new DiskCellCache.IntArrayType(),
				type );
		final IoSync< Long, Cell< IntArray > > iosync = new IoSync<>( diskcache );
		final UncheckedLoadingCache< Long, Cell< IntArray > > cache = new SoftRefListenableCache< Long, Cell< IntArray > >()
				.withRemovalListener( iosync )
				.withLoader( iosync )
				.unchecked();
		final CachedCellImg< ARGBType, IntArray > img = new CachedCellImg<>( grid, type, cache::get );

		// Hack: add and remove dummy source to avoid that the initial transform shows a full slice.
		final BdvStackSource< ARGBType > dummy = BdvFunctions.show( ArrayImgs.argbs( 10, 10, 10 ), "Dummy" );
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
