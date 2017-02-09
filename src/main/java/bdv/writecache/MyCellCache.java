package bdv.writecache;

import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.LinkedBlockingQueue;

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

		void save( long index, A data );
	}

	private final IoSync< A > io;

	private final Cache< Key, VolatileCell< A > > cache;

	/**
	 * @param maxNumLevels
	 *            the highest occurring mipmap level plus 1.
	 * @param numFetcherThreads
	 */
	public MyCellCache( final BlockIO< A > io )
	{
		this.io = new IoSync<>( io );
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


	public static class IoSync< A extends VolatileAccess >
	{
		private final BlockIO< A > io;

		private final ConcurrentHashMap< Long, Entry > writing;

		private final BlockingQueue< Long > writeQueue;

		class Entry
		{
			final A data;

			Entry( final A data )
			{
				this.data = data;
			}
		}

		public IoSync( final BlockIO< A > io )
		{
			this.io = io;
			writing = new ConcurrentHashMap<>();
			writeQueue = new LinkedBlockingQueue<>();

			new Thread( () -> {
				while ( true )
				{
					try
					{
						final Long key = writeQueue.take();
						final Entry entry = writing.get( key );
						if ( entry != null )
						{
							System.out.println( String.format( "saving %d", key ) );
							io.save( key, entry.data );
							writing.remove( key, entry );
						}
					}
					catch ( final InterruptedException e )
					{
						// TODO Auto-generated catch block
						e.printStackTrace();
					}
				}

			} ).start();
		}

		public VolatileCell< A > load( final long index, final int[] cellDims, final long[] cellMin )
		{
			final Entry entry = writing.get( index );
			final A data = ( entry != null ) ? entry.data : io.load( index );
			return new VolatileCell<>( cellDims, cellMin, data );
		}

		public void save( final long index, final A data )
		{
			writing.put( index, new Entry( data ) );
			try
			{
				writeQueue.put( index );
			}
			catch ( final InterruptedException e )
			{
				// TODO Auto-generated catch block
				e.printStackTrace();
			}
		}
	}


	@Override
	public VolatileCell< A > load( final long index, final int[] cellDims, final long[] cellMin )
	{
		try
		{
			return cache.get( new Key( index ),
					() -> io.load( index, cellDims, cellMin ),
					( k, v ) -> io.save( k.index, v.getData() ) );
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
