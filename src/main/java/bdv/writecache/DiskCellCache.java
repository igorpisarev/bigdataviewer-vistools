package bdv.writecache;

import java.io.File;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.MappedByteBuffer;
import java.nio.channels.FileChannel.MapMode;
import java.nio.file.Path;

import net.imglib2.cache.CacheLoader;
import net.imglib2.cache.RemovalListener;
import net.imglib2.img.cell.Cell;
import net.imglib2.img.cell.CellGrid;
import net.imglib2.type.NativeType;
import net.imglib2.util.Fraction;
import net.imglib2.util.Intervals;

public class DiskCellCache< A > implements RemovalListener< Long, Cell< A > >, CacheLoader< Long, Cell< A > >
{
	private final Path blockcache;

	private final CellGrid grid;

	private final int n;

	private final Fraction entitiesPerPixel;

	private final AccessIo< A > accessIo;

	private final CacheLoader< Long, Cell< A > > backingLoader;

	public < T extends NativeType< T > > DiskCellCache(
			final Path blockcache,
			final CellGrid grid,
			final CacheLoader< Long, Cell< A > > backingLoader,
			final AccessIo< A > accessIo,
			final T type
			)
	{
		this.blockcache = blockcache;
		this.grid = grid;
		this.n = grid.numDimensions();
		this.entitiesPerPixel = type.getEntitiesPerPixel();
		this.accessIo = accessIo;
		this.backingLoader = backingLoader;
	}

	private String blockname( final long index )
	{
//		final long[] cellGridPosition = new long[ n ];
//		grid.getCellGridPositionFlat( index, cellGridPosition );


		return String.format( "%s/%d", blockcache, index );
	}

	@Override
	public Cell< A > get( final Long key ) throws Exception
	{
		final long index = key;
		final String filename = blockname( index );

		if ( new File( filename ).exists() )
		{
			final long[] cellMin = new long[ n ];
			final int[] cellDims = new int[ n ];
			grid.getCellDimensions( index, cellMin, cellDims );
			final long blocksize = entitiesPerPixel.mulCeil( Intervals.numElements( cellDims ) );
			final long bytesize = blocksize * accessIo.getBytesPerElement();
			try (
					final RandomAccessFile mmFile = new RandomAccessFile( filename, "r" ); )
			{
				final MappedByteBuffer in = mmFile.getChannel().map( MapMode.READ_ONLY, 0, bytesize );
				final A access = accessIo.load( in, ( int ) blocksize );
				return new Cell<>( cellDims, cellMin, access );
			}
		}
		else
		{
			return backingLoader.get( key );
		}
	}

	@Override
	public void onRemoval( final Long key, final Cell< A > value )
	{
		final long index = key;
		final String filename = blockname( index );

		final int[] cellDims = new int[ n ];
		value.dimensions( cellDims );
		final long blocksize = entitiesPerPixel.mulCeil( Intervals.numElements( cellDims ) );
		final long bytesize = blocksize * accessIo.getBytesPerElement();
		try (
				final RandomAccessFile mmFile = new RandomAccessFile( filename, "rw" ); )
		{
			final MappedByteBuffer out = mmFile.getChannel().map( MapMode.READ_WRITE, 0, bytesize );
			accessIo.save( value.getData(), out, ( int ) blocksize );
		}
		catch ( final IOException e )
		{
			throw new RuntimeException( e );
		}
	}
}
