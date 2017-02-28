package bdv.writecache;

import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.LinkedBlockingQueue;

import bdv.img.gencache.CachedCellImg;
import net.imglib2.cache.CacheLoader;
import net.imglib2.cache.ListenableCache;
import net.imglib2.cache.RemovalListener;
import net.imglib2.cache.ref.SoftRefListenableCache;
import net.imglib2.img.cell.Cell;
import net.imglib2.img.cell.CellGrid;
import net.imglib2.type.NativeType;

public class MyCellCache< A >
{
	public interface BlockIO< A >
	{
		A load( long index );

		void save( long index, A data );
	}

	public static class IoSync< A >
	{
		private final BlockIO< A > io;

		private final ConcurrentHashMap< Long, A > writing;

		private final BlockingQueue< Long > writeQueue;

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
						final A data = writing.get( key );
						if ( data != null )
						{
							System.out.println( String.format( "saving %d", key ) );
							io.save( key, data );
							writing.remove( key, data );
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

		public A load( final long index, final int[] cellDims, final long[] cellMin )
		{
			A data = writing.get( index );
			if ( data == null )
				data = io.load( index );
			return data;
		}

		public void save( final long index, final A data )
		{
			writing.put( index, data );
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

	/*
	 *
	 * ===========================================================
	 *
	 */

	private final IoSync< A > io;

	private final ListenableCache< Long, Cell< A > > cache;

	/**
	 * @param maxNumLevels
	 *            the highest occurring mipmap level plus 1.
	 * @param numFetcherThreads
	 */
	public MyCellCache( final BlockIO< A > io )
	{
		this.io = new IoSync<>( io );
		cache = new SoftRefListenableCache<>();
	}

	/**
	 * Remove all references to loaded data as well as all enqueued requests
	 * from the cache.
	 */
	public void clearCache()
	{
		cache.invalidateAll();
	}

//	@Override
//	public VolatileCell< A > load( final long index, final int[] cellDims, final long[] cellMin )
//	{
//		try
//		{
//			return cache.get( new Key( index ),
//					() -> io.load( index, cellDims, cellMin ),
//					( k, v ) -> io.save( k.index, v.getData() ) );
//		}
//		catch ( final ExecutionException e )
//		{
//			e.printStackTrace();
//			return null;
//		}
//	}

	public < T extends NativeType< T > > CachedCellImg< T, A > getImage( final CellGrid grid, final T type )
	{
		final CacheLoader< ? super Long, ? extends Cell< A > > loader = new CacheLoader< Long, Cell<A> >()
		{
			@Override
			public Cell< A > get( final Long index ) throws Exception
			{
				final int n = grid.numDimensions();
				final long[] cellMin = new long[ n ];
				final int[] cellDims = new int[ n ];
				grid.getCellDimensions( index, cellMin, cellDims );
				return new Cell<>( cellDims, cellMin, io.load( index, cellDims, cellMin ) );
			}
		};

		final RemovalListener< ? super Long, ? super Cell< A > > remover = new RemovalListener< Long, Cell< A > >()
		{
			@Override
			public void onRemoval( final Long key, final Cell< A > value )
			{
				io.save( key, value.getData() );
			}
		};

		final CachedCellImg< T, A > img = new CachedCellImg<>( grid, type, ( i ) -> {
			try
			{
				return cache.get( i, loader, remover );
			}
			catch ( final Exception e )
			{
				// TODO Auto-generated catch block
				e.printStackTrace();
			}
			return null;
		} );

		return img;
	}
}
