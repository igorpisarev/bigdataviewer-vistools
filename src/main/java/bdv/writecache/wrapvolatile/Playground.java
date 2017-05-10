package bdv.writecache.wrapvolatile;

import java.io.IOException;

import org.scijava.ui.behaviour.DragBehaviour;
import org.scijava.ui.behaviour.io.InputTriggerConfig;
import org.scijava.ui.behaviour.util.Behaviours;

import bdv.img.openconnectome.OpenConnectomeImageLoader;
import bdv.img.openconnectome.OpenConnectomeTokenInfo;
import bdv.util.Bdv;
import bdv.util.BdvFunctions;
import bdv.util.BdvStackSource;
import bdv.viewer.ViewerPanel;
import net.imglib2.RandomAccess;
import net.imglib2.RandomAccessibleInterval;
import net.imglib2.Volatile;
import net.imglib2.algorithm.neighborhood.HyperSphereShape;
import net.imglib2.algorithm.neighborhood.Neighborhood;
import net.imglib2.cache.img.DiskCachedCellImgFactory;
import net.imglib2.cache.img.DiskCachedCellImgOptions;
import net.imglib2.img.Img;
import net.imglib2.img.array.ArrayImgs;
import net.imglib2.position.transform.Round;
import net.imglib2.realtransform.AffineTransform3D;
import net.imglib2.type.numeric.ARGBType;
import net.imglib2.type.numeric.integer.UnsignedByteType;
import net.imglib2.view.Views;

public class Playground
{
	public static void main( final String[] args ) throws IOException
	{
		System.setProperty( "apple.laf.useScreenMenuBar", "true" );

		final String baseUrl = "http://openconnecto.me/ocp/ca";
		final String token = "bock11";
		final String mode = "neariso";
		final int level = 4;

		final OpenConnectomeTokenInfo info = OpenConnectomeImageLoader.tryFetchTokenInfo( baseUrl, token, 20 );
		final long[] dimensions = info.getLevelDimensions( mode )[ level ];
		final int[] cellDimensions = info.getLevelCellDimensions()[ level ];
		final long zMin = Math.round( info.getOffsets( mode )[ 0 ][ 2 ] );

		final DiskCachedCellImgFactory< UnsignedByteType > factory = new DiskCachedCellImgFactory<>(
				DiskCachedCellImgOptions.options()
						.cellDimensions( cellDimensions )
						.dirtyAccesses( false ) );
		final Img< UnsignedByteType > img = factory.create( dimensions, new UnsignedByteType(), new OpenConnectomeCellLoader( baseUrl, token, mode, zMin, level ) );

		final RandomAccessibleInterval< ? extends Volatile< UnsignedByteType > > vimg = VolatileViews.figure_it_out( img );

		// Hack: add and remove dummy source to avoid that the initial transform shows a full slice.
		final BdvStackSource< ARGBType > dummy = BdvFunctions.show( ArrayImgs.argbs( 100, 100, 1 ), "Dummy" );
		final AffineTransform3D t = new AffineTransform3D();
		t.set( 2.8226839209736334, 0.0, 0.0, -10917.749851665443, 0.0, 2.8226839209736334, 0.0, -10185.12559948522, 0.0, 0.0, 2.8226839209736334, -873.6206735413396 );
		dummy.getBdvHandle().getViewerPanel().setCurrentViewerTransform( t );

		final Bdv bdv = BdvFunctions.show( vimg, "Cached", Bdv.options().addTo( dummy ) );
		dummy.removeFromBdv();

		final Behaviours behaviours = new Behaviours( new InputTriggerConfig() );
		behaviours.install( bdv.getBdvHandle().getTriggerbindings(), "paint" );

		behaviours.behaviour( new DragBehaviour()
		{
			final ViewerPanel viewer = bdv.getBdvHandle().getViewerPanel();
			final RandomAccess< Neighborhood< UnsignedByteType > > sphere = new HyperSphereShape( 10 ).neighborhoodsRandomAccessible( Views.extendZero( img ) ).randomAccess();
			final Round< ? > rsphere = new Round<>( sphere );

			void draw( final int x, final int y )
			{
				viewer.displayToGlobalCoordinates( x, y, rsphere );
				sphere.get().forEach( t -> t.set( 0xff ) );
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
