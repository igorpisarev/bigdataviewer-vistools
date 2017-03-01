package bdv.writecache;

import bdv.img.gencache.CachedCellImg;
import net.imglib2.cache.CacheLoader;
import net.imglib2.cache.RemovalListener;
import net.imglib2.cache.UncheckedLoadingCache;
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

	private final BlockIO< A > io;

	/**
	 * @param maxNumLevels
	 *            the highest occurring mipmap level plus 1.
	 * @param numFetcherThreads
	 */
	public MyCellCache( final BlockIO< A > io )
	{
		this.io = io;
	}

	public < T extends NativeType< T > > CachedCellImg< T, A > getImage( final CellGrid grid, final T type )
	{
		final CacheLoader<  Long, Cell< A >  > loader = new CacheLoader< Long, Cell<A> >()
		{
			@Override
			public Cell< A > get( final Long index ) throws Exception
			{
				final int n = grid.numDimensions();
				final long[] cellMin = new long[ n ];
				final int[] cellDims = new int[ n ];
				grid.getCellDimensions( index, cellMin, cellDims );
				return new Cell<>( cellDims, cellMin, io.load( index ) );
			}
		};

		final RemovalListener<  Long, Cell< A > > saver = new RemovalListener< Long, Cell< A > >()
		{
			@Override
			public void onRemoval( final Long key, final Cell< A > value )
			{
				io.save( key, value.getData() );
			}
		};

		final IoSync< Long, Cell< A > > iosync = new IoSync<>( loader, saver );

		final UncheckedLoadingCache< Long, Cell< A > > cache = new SoftRefListenableCache< Long, Cell< A > >()
				.withRemovalListener( iosync )
				.withLoader( iosync )
				.unchecked();

		final CachedCellImg< T, A > img = new CachedCellImg<>( grid, type, i -> cache.get( i ) );

		return img;
	}
}
