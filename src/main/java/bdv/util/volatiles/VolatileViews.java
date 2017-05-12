package bdv.util.volatiles;

import java.util.concurrent.Callable;

import bdv.cache.CacheControl;
import bdv.img.cache.CreateInvalidVolatileCell;
import bdv.img.cache.VolatileCachedCellImg;
import net.imglib2.AbstractWrappedInterval;
import net.imglib2.Interval;
import net.imglib2.RandomAccess;
import net.imglib2.RandomAccessibleInterval;
import net.imglib2.Volatile;
import net.imglib2.cache.Cache;
import net.imglib2.cache.img.AccessFlags;
import net.imglib2.cache.img.CachedCellImg;
import net.imglib2.cache.queue.BlockingFetchQueues;
import net.imglib2.cache.queue.FetcherThreads;
import net.imglib2.cache.ref.WeakRefVolatileCache;
import net.imglib2.cache.volatiles.CacheHints;
import net.imglib2.cache.volatiles.CreateInvalid;
import net.imglib2.cache.volatiles.LoadingStrategy;
import net.imglib2.cache.volatiles.VolatileCache;
import net.imglib2.img.basictypeaccess.volatiles.VolatileArrayDataAccess;
import net.imglib2.img.cell.Cell;
import net.imglib2.img.cell.CellGrid;
import net.imglib2.type.NativeType;

public class VolatileViews
{
	static class SharedQueue implements CacheControl
	{
		private final BlockingFetchQueues< Callable< ? > > queue;

		SharedQueue( final int numPriorities, final int numFetcherThreads )
		{
			queue = new BlockingFetchQueues<>( numPriorities );
			new FetcherThreads( queue, numFetcherThreads );
		}

		@Override
		public void prepareNextFrame()
		{
			queue.clearToPrefetch();
		}
	}

	// TODO: these should go to Options
	static final SharedQueue sharedQueue = new SharedQueue( 1, 1 );
	static final CacheHints hints = new CacheHints( LoadingStrategy.VOLATILE, 0, false );

	public static class VolatileViewData< T, V extends Volatile< T > >
	{
		public RandomAccessibleInterval< V > img;

		public CacheControl cacheControl;

		public T type;

		public V volatileType;

		public VolatileViewData(
				final RandomAccessibleInterval< V > img,
				final CacheControl cacheControl,
				final T type,
				final V volatileType )
		{
			this.img = img;
			this.cacheControl = cacheControl;
			this.type = type;
			this.volatileType = volatileType;
		}
	}

	public interface VolatileView< T, V extends Volatile< T > >
	{
		public VolatileViewData< T, V > getVolatileViewData();
	}

	public static class VolatileRandomAccessibleIntervalView< T, V extends Volatile< T > >
		extends AbstractWrappedInterval< RandomAccessibleInterval< V > >
		implements VolatileView< T, V >, RandomAccessibleInterval< V >
	{
		private final VolatileViewData< T, V > viewData;

		public VolatileRandomAccessibleIntervalView(
				final RandomAccessibleInterval< V > source,
				final VolatileViewData< T, V > viewData )
		{
			super( source );
			this.viewData = viewData;
		}

		@Override
		public VolatileViewData< T, V > getVolatileViewData()
		{
			return viewData;
		}

		@Override
		public RandomAccess< V > randomAccess()
		{
			return sourceInterval.randomAccess();
		}

		@Override
		public RandomAccess< V > randomAccess( final Interval interval )
		{
			return sourceInterval.randomAccess( interval );
		}
	}

	public static < T, V extends Volatile< T > > RandomAccessibleInterval< V > wrapAsVolatile( final RandomAccessibleInterval< T > rai )
	{
		@SuppressWarnings( "unchecked" )
		final VolatileViewData< T, V > viewData = ( VolatileViewData< T, V > ) wrapAsVolatileViewData( rai );
		return new VolatileRandomAccessibleIntervalView<>( viewData.img, viewData );
	}

	@SuppressWarnings( "unchecked" )
	public static < T > VolatileViewData< T, ? > wrapAsVolatileViewData( final RandomAccessibleInterval< T > rai )
	{
		if ( rai instanceof CachedCellImg )
		{
			@SuppressWarnings( "rawtypes" )
			final Object o = wrapCachedCellImg( ( CachedCellImg ) rai );
			/*
			 * Need to assign to a Object first to satisfy Eclipse... Otherwise
			 * the following "unnecessary cast" will be removed, followed by
			 * compile error. Proposed solution: Add cast. Doh...
			 */
			final VolatileViewData< T, ? > view = ( VolatileViewData< T, ? > ) o;
			return view;
		}

		throw new IllegalArgumentException();
	}

	@SuppressWarnings( "unchecked" )
	private static < T extends NativeType< T >, V extends Volatile< T > & NativeType< V >, A >
		VolatileViewData< T, V > wrapCachedCellImg(	final CachedCellImg< T, A > cachedCellImg )
	{
		final T type = cachedCellImg.createLinkedType();
		final CellGrid grid = cachedCellImg.getCellGrid();
		final Cache< Long, Cell< A > > cache = cachedCellImg.getCache();

		final AccessFlags[] flags = AccessFlags.of( cachedCellImg.getAccessType() );
		if ( !AccessFlags.isVolatile( flags ) )
			throw new IllegalArgumentException( "underlying " + CachedCellImg.class.getSimpleName() + " must have volatile access type" );

		final V vtype = ( V ) VolatileTypeMatcher.getVolatileTypeForType( type );
		final VolatileCachedCellImg< V, ? > img = createVolatileCachedCellImg( grid, vtype, flags, sharedQueue.queue, ( Cache ) cache, hints );

		return new VolatileViewData<>( img, sharedQueue, type, vtype );
	}

	private static < T extends NativeType< T >, A extends VolatileArrayDataAccess< A > > VolatileCachedCellImg< T, A > createVolatileCachedCellImg(
			final CellGrid grid,
			final T type,
			final AccessFlags[] accessFlags,
			final BlockingFetchQueues< Callable< ? > > queue,
			final Cache< Long, Cell< A > > cache,
			final CacheHints hints )
	{
		final CreateInvalid< Long, Cell< A > > createInvalid = CreateInvalidVolatileCell.get( grid, type, accessFlags );
		final VolatileCache< Long, Cell< A > > volatileCache = new WeakRefVolatileCache<>( cache, queue, createInvalid );
		final VolatileCachedCellImg< T, A > volatileImg = new VolatileCachedCellImg<>( grid, type, hints, volatileCache.unchecked()::get );
		return volatileImg;
	}
}
