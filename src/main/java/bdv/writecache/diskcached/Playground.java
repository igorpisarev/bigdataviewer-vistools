package bdv.writecache.diskcached;

import java.io.IOException;

import org.scijava.ui.behaviour.DragBehaviour;
import org.scijava.ui.behaviour.io.InputTriggerConfig;
import org.scijava.ui.behaviour.util.Behaviours;

import bdv.util.Bdv;
import bdv.util.BdvFunctions;
import bdv.util.BdvStackSource;
import bdv.viewer.ViewerPanel;
import net.imglib2.RandomAccess;
import net.imglib2.algorithm.neighborhood.HyperSphereShape;
import net.imglib2.algorithm.neighborhood.Neighborhood;
import net.imglib2.cache.img.DiskCachedCellImgFactory;
import net.imglib2.img.Img;
import net.imglib2.img.array.ArrayImgs;
import net.imglib2.position.transform.Round;
import net.imglib2.type.numeric.ARGBType;
import net.imglib2.view.Views;

public class Playground
{
	public static void main( final String[] args ) throws IOException
	{
		System.setProperty( "apple.laf.useScreenMenuBar", "true" );

		final Img< ARGBType > img = new DiskCachedCellImgFactory< ARGBType >( 32 ).create( new long[] { 640,  640, 640 }, new ARGBType() );

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
