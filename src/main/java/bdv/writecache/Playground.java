package bdv.writecache;

import java.io.File;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.IntBuffer;
import java.nio.MappedByteBuffer;
import java.nio.channels.FileChannel.MapMode;
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
import net.imglib2.cache.UncheckedLoadingCache;
import net.imglib2.cache.ref.SoftRefListenableCache;
import net.imglib2.img.Img;
import net.imglib2.img.array.ArrayImgs;
import net.imglib2.img.basictypeaccess.array.IntArray;
import net.imglib2.img.cell.Cell;
import net.imglib2.img.cell.CellGrid;
import net.imglib2.position.transform.Round;
import net.imglib2.type.NativeType;
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

	private final Random random = new Random();

	private final Path blockcache;

	private final CellGrid grid;

	Playground( final Path blockcache, final CellGrid grid )
	{
		this.blockcache = blockcache;
		this.grid = grid;
	}

	private String blockname( final long index )
	{
		return String.format( "%s/%d", blockcache, index );
	}

	private Path blockpath( final long index )
	{
		return Paths.get( blockname( index ) );
	}

	public Cell< IntArray > load( final long index )
	{
		final long[] cellMin = new long[ grid.numDimensions() ];
		final int[] cellDims = new int[ grid.numDimensions() ];
		grid.getCellDimensions( index, cellMin, cellDims );
		final int blocksize = ( int ) Intervals.numElements( cellDims );
		final IntArray array = new IntArray( blocksize );
		try
		{
			if ( Files.exists( blockpath( index ) ) )
			{
				System.out.println( "reading " + blockname( index ) );
				final RandomAccessFile mmFile = new RandomAccessFile( blockname( index ), "rw" );
				final MappedByteBuffer in = mmFile.getChannel().map( MapMode.READ_ONLY, 0, blocksize * 4 );
				IntBuffer.wrap( array.getCurrentStorageArray() ).put( in.asIntBuffer() );
				mmFile.close();
			}
			else
				Arrays.fill( array.getCurrentStorageArray(), random.nextInt() & 0xFF0000FF );
		}
		catch ( final IOException e )
		{
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		return new Cell<>( cellDims, cellMin, array );
	}

	public void save( final long index, final Cell< IntArray > cell )
	{
		try
		{
			System.out.println( "writing " + blockname( index ) );
			final RandomAccessFile mmFile = new RandomAccessFile( blockname( index ), "rw" );
			final int[] array = cell.getData().getCurrentStorageArray();
			final MappedByteBuffer out = mmFile.getChannel().map(
					MapMode.READ_WRITE, 0, array.length * 4 );
			out.asIntBuffer().put( IntBuffer.wrap( array ) );
			mmFile.close();
		}
		catch ( final IOException e )
		{
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
	}

	public < T extends NativeType< T > > CachedCellImg< T, IntArray > getImage( final T type )
	{
		final IoSync< Long, Cell< IntArray > > iosync = new IoSync<>( this::load, this::save );
		final UncheckedLoadingCache< Long, Cell< IntArray > > cache = new SoftRefListenableCache< Long, Cell< IntArray > >()
				.withRemovalListener( iosync )
				.withLoader( iosync )
				.unchecked();
		final CachedCellImg< T, IntArray > img = new CachedCellImg<>( grid, type, i -> cache.get( i ) );
		return img;
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
		final Img< ARGBType > img = new Playground( blockcache, grid ).getImage( new ARGBType() );

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
