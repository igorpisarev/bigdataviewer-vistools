package bdv.writecache;

import java.util.concurrent.ExecutionException;

import bdv.cache.CacheHints;
import bdv.cache.revised.Cache;
import bdv.cache.revised.SoftRefCache;
import bdv.img.cache.VolatileCell;
import bdv.img.cache.VolatileImgCells.CellCache;
import net.imglib2.img.basictypeaccess.volatiles.VolatileAccess;

public class MyCellCache< A extends VolatileAccess > implements CellCache< A >
{
	/**
	 * Key for a cell identified by timepoint, setup, level, and index
	 * (flattened spatial coordinate).
	 */
	public static class Key
	{
		private final long index;

		/**
		 * Create a Key for the specified cell.
		 *
		 * @param index
		 *            index of the cell (flattened spatial coordinate of the
		 *            cell)
		 */
		public Key( final long index )
		{
			this.index = index;

			final int value = Long.hashCode( index );
			hashcode = value;
		}

		@Override
		public boolean equals( final Object other )
		{
			if ( this == other )
				return true;
			if ( !( other instanceof MyCellCache.Key ) )
				return false;
			final Key that = ( Key ) other;
			return ( this.index == that.index );
		}

		final int hashcode;

		@Override
		public int hashCode()
		{
			return hashcode;
		}
	}

	public interface BlockIO< A >
	{
		A load( long index );
	}

	private final BlockIO< A > io;

	private final Cache< Key, VolatileCell< A > > cache;

	/**
	 * @param maxNumLevels
	 *            the highest occurring mipmap level plus 1.
	 * @param numFetcherThreads
	 */
	public MyCellCache( final BlockIO< A > io )
	{
		this.io = io;
		cache = new SoftRefCache<>();
	}

	/**
	 * Remove all references to loaded data as well as all enqueued requests
	 * from the cache.
	 */
	public void clearCache()
	{
		cache.invalidateAll();
	}

	@Override
	public VolatileCell< A > get( final long index )
	{
		return cache.getIfPresent( new Key( index ) );
	}

	@Override
	public VolatileCell< A > load( final long index, final int[] cellDims, final long[] cellMin )
	{
		try
		{
			return cache.get( new Key( index ), () -> new VolatileCell<>( cellDims, cellMin, io.load( index ) ) );
		}
		catch ( final ExecutionException e )
		{
			e.printStackTrace();
			return null;
		}
	}

	@Override
	public void setCacheHints( final CacheHints cacheHints )
	{
		// TODO Auto-generated method stub

	}
}
