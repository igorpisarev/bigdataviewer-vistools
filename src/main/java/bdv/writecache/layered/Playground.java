package bdv.writecache.layered;

import static net.imglib2.cache.img.AccessFlags.DIRTY;
import static net.imglib2.cache.img.AccessFlags.VOLATILE;

import java.io.IOException;
import java.nio.file.Path;
import java.util.concurrent.Callable;

import org.scijava.ui.behaviour.DragBehaviour;
import org.scijava.ui.behaviour.io.InputTriggerConfig;
import org.scijava.ui.behaviour.util.Behaviours;

import bdv.img.cache.VolatileCachedCellImg;
import bdv.img.openconnectome.OpenConnectomeImageLoader;
import bdv.img.openconnectome.OpenConnectomeTokenInfo;
import bdv.img.openconnectome.OpenConnectomeVolatileArrayLoader;
import bdv.util.Bdv;
import bdv.util.BdvFunctions;
import bdv.util.BdvStackSource;
import bdv.viewer.ViewerPanel;
import net.imglib2.RandomAccess;
import net.imglib2.algorithm.neighborhood.HyperSphereShape;
import net.imglib2.algorithm.neighborhood.Neighborhood;
import net.imglib2.cache.LoaderCache;
import net.imglib2.cache.IoSync;
import net.imglib2.cache.LoaderRemoverCache;
import net.imglib2.cache.img.AccessIo;
import net.imglib2.cache.img.DirtyDiskCellCache;
import net.imglib2.cache.img.DiskCellCache;
import net.imglib2.cache.queue.BlockingFetchQueues;
import net.imglib2.cache.queue.FetcherThreads;
import net.imglib2.cache.ref.SoftRefLoaderRemoverCache;
import net.imglib2.cache.ref.WeakRefVolatileLoaderCache;
import net.imglib2.cache.volatiles.CacheHints;
import net.imglib2.cache.volatiles.LoadingStrategy;
import net.imglib2.cache.volatiles.VolatileLoaderCache;
import net.imglib2.cache.volatiles.VolatileCacheLoader;
import net.imglib2.img.Img;
import net.imglib2.img.array.ArrayImgs;
import net.imglib2.img.basictypeaccess.volatiles.array.DirtyVolatileByteArray;
import net.imglib2.img.basictypeaccess.volatiles.array.VolatileByteArray;
import net.imglib2.img.cell.Cell;
import net.imglib2.img.cell.CellGrid;
import net.imglib2.img.cell.LazyCellImg;
import net.imglib2.position.transform.Round;
import net.imglib2.realtransform.AffineTransform3D;
import net.imglib2.type.numeric.ARGBType;
import net.imglib2.type.numeric.integer.UnsignedByteType;
import net.imglib2.view.Views;

public class Playground
{

	static class OpenConnectome
	{
		final int numScales;

		final double[][] mipmapResolutions;

		final long[][] imageDimensions;

		final int[][] blockDimensions;

		final AffineTransform3D[] mipmapTransforms;

		final OpenConnectomeVolatileArrayLoader loader;

		public OpenConnectome( final String baseUrl, final String token, final String mode )
		{
			final OpenConnectomeTokenInfo info = OpenConnectomeImageLoader.tryFetchTokenInfo( baseUrl, token, 20 );

			numScales = info.dataset.cube_dimension.size();

			mipmapResolutions = info.getLevelScales( mode );
			imageDimensions = info.getLevelDimensions( mode );
			blockDimensions = info.getLevelCellDimensions();
			mipmapTransforms = info.getLevelTransforms( mode );

			loader = new OpenConnectomeVolatileArrayLoader(
					baseUrl,
					token,
					mode,
					Math.round( info.getOffsets( mode )[ 0 ][ 2 ] ) );
		}
	}

	static DirtyVolatileByteArray wrapDirty( final VolatileByteArray a )
	{
		return new DirtyVolatileByteArray( a.getCurrentStorageArray(), a.isValid() );
	}

	public static void main( final String[] args ) throws IOException
	{
		System.setProperty( "apple.laf.useScreenMenuBar", "true" );

		final String baseUrl = "http://openconnecto.me/ocp/ca";
		final String token = "bock11";
		final String mode = "neariso";
		final OpenConnectome oc = new OpenConnectome( baseUrl, token, mode );


		final UnsignedByteType type = new UnsignedByteType();

		final int level = 4;

		final long[] dimensions = oc.imageDimensions[ level ];
		final int[] cellDimensions = oc.blockDimensions[ level ];

		final CellGrid grid = new CellGrid( dimensions, cellDimensions );

		final VolatileCacheLoader< Long, Cell< DirtyVolatileByteArray > > backingLoader = new VolatileCacheLoader< Long, Cell< DirtyVolatileByteArray > >()
		{
			@Override
			public Cell< DirtyVolatileByteArray > get( final Long key ) throws Exception
			{
				final int n = grid.numDimensions();
				final long[] cellMin = new long[ n ];
				final int[] cellDims = new int[ n ];
				grid.getCellDimensions( key, cellMin, cellDims );
				return new Cell<>(
						cellDims,
						cellMin,
						wrapDirty(
								oc.loader.loadArray( 0, 0, level, cellDims, cellMin ) ) );
			}

			@Override
			public Cell< DirtyVolatileByteArray > createInvalid( final Long key ) throws Exception
			{
				final int n = grid.numDimensions();
				final long[] cellMin = new long[ n ];
				final int[] cellDims = new int[ n ];
				grid.getCellDimensions( key, cellMin, cellDims );
				return new Cell<>(
						cellDims,
						cellMin,
						wrapDirty(
								oc.loader.emptyArray( cellDims ) ) );
			}
		};




		final Path blockcache = DiskCellCache.createTempDirectory( "CellImg", true );
		final DiskCellCache< DirtyVolatileByteArray > diskcache = new DirtyDiskCellCache<>(
				blockcache,
				grid,
				backingLoader,
				AccessIo.get( type, DIRTY, VOLATILE ),
				type.getEntitiesPerPixel() );
		final IoSync< Long, Cell< DirtyVolatileByteArray > > iosync = new IoSync<>( diskcache );
		final LoaderRemoverCache< Long, Cell< DirtyVolatileByteArray > > listenableCache = new SoftRefLoaderRemoverCache<>();
		final LoaderCache< Long, Cell< DirtyVolatileByteArray > > cache = listenableCache.withRemover( iosync );

		final Img< UnsignedByteType > img = new LazyCellImg<>( grid, type,
				cache.withLoader( iosync ).unchecked()::get );

		final int maxNumLevels = 1;
		final int numFetcherThreads = 10;
		final BlockingFetchQueues< Callable< ? > > queue = new BlockingFetchQueues<>( maxNumLevels );
		new FetcherThreads( queue, numFetcherThreads );

		final VolatileLoaderCache< Long, Cell< DirtyVolatileByteArray > > volatileCache = new WeakRefVolatileLoaderCache<>( cache, queue );

		final CacheHints hints = new CacheHints( LoadingStrategy.DONTLOAD, 0, false );
		final VolatileCachedCellImg< UnsignedByteType, ? > volatileImg = new VolatileCachedCellImg<>( grid, type, hints,
				volatileCache.withLoader( new VolatileCacheLoader< Long, Cell< DirtyVolatileByteArray > >()
				{
					@Override
					public Cell< DirtyVolatileByteArray > get( final Long key ) throws Exception
					{
						return iosync.get( key );
					}

					@Override
					public Cell< DirtyVolatileByteArray > createInvalid( final Long key ) throws Exception
					{
						return backingLoader.createInvalid( key );
					}
				} ).unchecked()::get );



		// Hack: add and remove dummy source to avoid that the initial transform shows a full slice.
		final BdvStackSource< ARGBType > dummy = BdvFunctions.show( ArrayImgs.argbs( 100, 100, 1 ), "Dummy" );
		final Bdv bdv = BdvFunctions.show( volatileImg, "Cached", Bdv.options().addTo( dummy ) );
		dummy.removeFromBdv();

		final Behaviours behaviours = new Behaviours( new InputTriggerConfig() );
		behaviours.install( bdv.getBdvHandle().getTriggerbindings(), "paint" );

		behaviours.behaviour( new DragBehaviour()
		{
			void draw( final int x, final int y )
			{
				final ViewerPanel viewer = bdv.getBdvHandle().getViewerPanel();
				final RandomAccess< Neighborhood< UnsignedByteType > > sphere = new HyperSphereShape( 10 ).neighborhoodsRandomAccessible( Views.extendZero( img ) ).randomAccess();
				viewer.displayToGlobalCoordinates( x, y, new Round<>( sphere ) );
				sphere.get().forEach( t -> t.set( 0xff ) );
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
