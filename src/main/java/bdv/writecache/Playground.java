package bdv.writecache;

import java.util.Arrays;
import java.util.Random;

import org.scijava.ui.behaviour.DragBehaviour;
import org.scijava.ui.behaviour.io.InputTriggerConfig;
import org.scijava.ui.behaviour.util.Behaviours;

import bdv.img.cache.CachedCellImg;
import bdv.img.cache.VolatileImgCells;
import bdv.img.cache.VolatileImgCells.CellCache;
import bdv.util.Bdv;
import bdv.util.BdvFunctions;
import bdv.util.BdvStackSource;
import bdv.viewer.ViewerPanel;
import bdv.writecache.MyCellCache.BlockIO;
import net.imglib2.RandomAccess;
import net.imglib2.algorithm.neighborhood.HyperSphereShape;
import net.imglib2.algorithm.neighborhood.Neighborhood;
import net.imglib2.img.array.ArrayImgs;
import net.imglib2.img.basictypeaccess.volatiles.array.VolatileIntArray;
import net.imglib2.position.transform.Round;
import net.imglib2.type.numeric.ARGBType;
import net.imglib2.util.Fraction;
import net.imglib2.util.Intervals;
import net.imglib2.view.Views;

public class Playground
{
	public static void main( final String[] args )
	{
		System.setProperty( "apple.laf.useScreenMenuBar", "true" );

		final long[] dimensions = new long[] { 640, 640, 640 };
		final int[] cellDimensions = new int[] { 32, 32, 32 };

		final BlockIO< VolatileIntArray > io = new BlockIO< VolatileIntArray >()
		{
			private final int blocksize = ( int ) Intervals.numElements( cellDimensions );

			private final Random random = new Random();

			@Override
			public VolatileIntArray load( final long index )
			{
				final VolatileIntArray a = new VolatileIntArray( blocksize, true );
				Arrays.fill( a.getCurrentStorageArray(), random.nextInt() & 0xFF0000FF );
				return a;
			}
		};

		final CellCache< VolatileIntArray > c = new MyCellCache<>( io );
		final VolatileImgCells< VolatileIntArray > cells = new VolatileImgCells<>( c, new Fraction(), dimensions, cellDimensions );
		final CachedCellImg< ARGBType, VolatileIntArray > img = new CachedCellImg<>( cells );
		final ARGBType linkedType = new ARGBType( img );
		img.setLinkedType( linkedType );

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
