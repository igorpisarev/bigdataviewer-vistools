package bdv.writecache.backingimage;

import static net.imglib2.cache.img.DiskCachedCellImgOptions.options;

import java.io.IOException;

import org.scijava.ui.behaviour.DragBehaviour;
import org.scijava.ui.behaviour.io.InputTriggerConfig;
import org.scijava.ui.behaviour.util.Behaviours;

import bdv.util.Bdv;
import bdv.util.BdvFunctions;
import bdv.util.BdvOptions;
import bdv.util.BdvSource;
import bdv.util.BdvStackSource;
import bdv.viewer.ViewerPanel;
import net.imglib2.RandomAccess;
import net.imglib2.RandomAccessible;
import net.imglib2.algorithm.neighborhood.HyperSphereShape;
import net.imglib2.algorithm.neighborhood.Neighborhood;
import net.imglib2.cache.CacheLoader;
import net.imglib2.cache.img.AccessFlags;
import net.imglib2.cache.img.DiskCachedCellImgFactory;
import net.imglib2.cache.img.DiskCachedCellImgOptions;
import net.imglib2.cache.img.DiskCachedCellImgOptions.CacheType;
import net.imglib2.cache.img.RandomAccessibleCacheLoader;
import net.imglib2.img.Img;
import net.imglib2.img.array.ArrayImgs;
import net.imglib2.img.basictypeaccess.array.DirtyLongArray;
import net.imglib2.img.cell.Cell;
import net.imglib2.img.cell.CellGrid;
import net.imglib2.realtransform.AffineTransform2D;
import net.imglib2.realtransform.RealViews;
import net.imglib2.type.numeric.ARGBType;
import net.imglib2.type.numeric.integer.LongType;
import net.imglib2.view.Views;

public class Playground
{
	public static void main( final String[] args ) throws IOException
	{
		System.setProperty( "apple.laf.useScreenMenuBar", "true" );

		final MandelbrotRealRandomAccessible mb = new MandelbrotRealRandomAccessible();

		final long scale = 1000;
		final AffineTransform2D transform = new AffineTransform2D();
		transform.set(
				scale, 0, 2 * scale,
				0, scale, 2 * scale );
		final RandomAccessible< LongType > source = RealViews.affine( mb, transform );

		final int[] cellDimensions = new int[] { 512, 512 };
		final long[] dimensions = new long[] { 4 * scale, 4 * scale };

		final CacheLoader< Long, Cell< DirtyLongArray > > loader = RandomAccessibleCacheLoader.get( new CellGrid( dimensions, cellDimensions ), source, AccessFlags.DIRTY );
		final DiskCachedCellImgOptions options = options()
				.cellDimensions( cellDimensions )
				.cacheType( CacheType.BOUNDED );
		final Img< LongType > img = new DiskCachedCellImgFactory< LongType >( options ).create( dimensions, new LongType(), loader );

		// Hack: add and remove dummy source to avoid that the initial transform shows a full slice.
		final BdvStackSource< ARGBType > dummy = BdvFunctions.show( ArrayImgs.argbs( 100, 100 ), "Dummy", BdvOptions.options().is2D() );
		final BdvSource bdv = BdvFunctions.show( img, "Cached", Bdv.options().addTo( dummy ) );
		dummy.removeFromBdv();
		bdv.setDisplayRangeBounds( 0, 100 );

		final Behaviours behaviours = new Behaviours( new InputTriggerConfig() );
		behaviours.install( bdv.getBdvHandle().getTriggerbindings(), "paint" );

		behaviours.behaviour( new DragBehaviour()
		{
			void draw( final int x, final int y )
			{
				final ViewerPanel viewer = bdv.getBdvHandle().getViewerPanel();
				final RandomAccess< Neighborhood< LongType > > sphere = new HyperSphereShape( 10 ).neighborhoodsRandomAccessible( Views.extendZero( img ) ).randomAccess();
				final double[] pos = new double[] { x, y, 0 };
				viewer.displayToGlobalCoordinates( pos );
				sphere.setPosition( Math.round( pos[ 0 ] ), 0 );
				sphere.setPosition( Math.round( pos[ 1 ] ), 1 );
				sphere.get().forEach( t -> t.set( 0 ) );
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
