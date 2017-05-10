package bdv.writecache.wrapvolatile;

import static net.imglib2.cache.img.AccessFlags.DIRTY;
import static net.imglib2.cache.img.AccessFlags.VOLATILE;

import java.util.Arrays;
import java.util.concurrent.Callable;

import bdv.img.cache.CreateInvalidVolatileCell;
import bdv.img.cache.VolatileCachedCellImg;
import net.imglib2.Dirty;
import net.imglib2.Interval;
import net.imglib2.RandomAccess;
import net.imglib2.RandomAccessible;
import net.imglib2.RandomAccessibleInterval;
import net.imglib2.Volatile;
import net.imglib2.cache.Cache;
import net.imglib2.cache.img.AccessFlags;
import net.imglib2.cache.img.CachedCellImg;
import net.imglib2.cache.img.PrimitiveType;
import net.imglib2.cache.queue.BlockingFetchQueues;
import net.imglib2.cache.queue.FetcherThreads;
import net.imglib2.cache.ref.WeakRefVolatileCache;
import net.imglib2.cache.volatiles.CacheHints;
import net.imglib2.cache.volatiles.CreateInvalid;
import net.imglib2.cache.volatiles.LoadingStrategy;
import net.imglib2.cache.volatiles.VolatileCache;
import net.imglib2.img.basictypeaccess.volatiles.VolatileAccess;
import net.imglib2.img.basictypeaccess.volatiles.VolatileArrayDataAccess;
import net.imglib2.img.cell.Cell;
import net.imglib2.img.cell.CellGrid;
import net.imglib2.type.NativeType;
import net.imglib2.type.numeric.integer.UnsignedByteType;
import net.imglib2.type.numeric.integer.VolatileUnsignedByteType;

public class VolatileViews
{
	@SuppressWarnings( { "unchecked", "rawtypes" } )
	public static < T > RandomAccessibleInterval< ? extends Volatile< T > > figure_it_out( final RandomAccessible< T > ra )
	{
		final RandomAccess< T > a = ra.randomAccess();
		if ( ra instanceof Interval )
			( ( Interval ) ra ).min( a );
		final T type = a.get();

		if ( type instanceof NativeType )
			return figure_it_out1( ( RandomAccessible ) ra, ( NativeType ) type, ( RandomAccess) a );
		else
			System.out.println( "I cannot figure it out" );

		return null;
	}


	public static class VolatileTypeMatcher
	{
		public static < T extends NativeType< T > > NativeType< ? > getVolatileTypeForType( final T type )
		{
			if ( type instanceof UnsignedByteType )
				return new VolatileUnsignedByteType();

			return null;
		}
	}


	private static < T extends NativeType< T >, A extends VolatileArrayDataAccess< A > > VolatileCachedCellImg< T, A > createImg(
			final CellGrid grid,
			final T type,
			final AccessFlags[] accessFlags,
			final BlockingFetchQueues< Callable< ? > > queue,
			final Cache< Long, Cell< A > > cache )
	{
		final CreateInvalid< Long, Cell< A > > createInvalid = CreateInvalidVolatileCell.get( grid, type, accessFlags );
		final VolatileCache< Long, Cell< A > > volatileCache = new WeakRefVolatileCache<>( cache, queue, createInvalid );
		final CacheHints hints = new CacheHints( LoadingStrategy.VOLATILE, 0, false );
		final VolatileCachedCellImg< T, A > volatileImg = new VolatileCachedCellImg<>( grid, type, hints, volatileCache.unchecked()::get );
		return volatileImg;
	}


	public static < T extends NativeType< T >, A >
			VolatileCachedCellImg< ? extends Volatile< T >, A >
			figure_it_out2( final CachedCellImg< T, A > cachedCellImg, final T type, final RandomAccess< T > sampler )
	{
		final CellGrid grid = cachedCellImg.getCellGrid();
		final Cache< Long, Cell< A > > cache = cachedCellImg.getCache();

		final PrimitiveType primitiveType = PrimitiveType.forNativeType( type );
		System.out.println( primitiveType );

		// TODO: access type should be obtainable from CachedCellImg without needing to load anything
		final A access = cachedCellImg.update( sampler );
		final boolean dirtyAccesses = ( access instanceof Dirty );
		final boolean volatileAccesses = ( access instanceof VolatileAccess );

		if ( !volatileAccesses )
			throw new IllegalArgumentException( "need volatile access type" );

		final AccessFlags[] accessFlags = dirtyAccesses
				? ( volatileAccesses
						? new AccessFlags[] { DIRTY, VOLATILE }
						: new AccessFlags[] { DIRTY } )
				: ( volatileAccesses
						? new AccessFlags[] { VOLATILE }
						: new AccessFlags[] {} );

		Arrays.asList( accessFlags ).forEach( a -> System.out.println( a ) );

		// TODO: should reuse same fetcher queue across many invocations?
		final int maxNumLevels = 1;
		final int numFetcherThreads = 10;
		final BlockingFetchQueues< Callable< ? > > queue = new BlockingFetchQueues<>( maxNumLevels );
		new FetcherThreads( queue, numFetcherThreads );

		@SuppressWarnings( "rawtypes" )
		final NativeType vtype = VolatileTypeMatcher.getVolatileTypeForType( type );

		final VolatileCachedCellImg img = createImg( grid, vtype, accessFlags, queue, ( Cache ) cache );
		return img;
	}

	public static < T extends NativeType< T > >
			VolatileCachedCellImg< ? extends Volatile< T >, ? >
			figure_it_out1( final RandomAccessible< T > ra, final T type, final RandomAccess< T > sampler )
	{
		System.out.println( "NativeType" );
		if ( ra instanceof CachedCellImg )
		{
			@SuppressWarnings( "unchecked" )
			final CachedCellImg< T, ? > cachedCellImg = ( CachedCellImg< T, ? > ) ra;
			return figure_it_out2( cachedCellImg, type, sampler );
		}
		System.out.println( "I cannot figure it out" );
		return null;
	}
}
