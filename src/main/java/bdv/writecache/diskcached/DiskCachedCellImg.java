package bdv.writecache.diskcached;

import net.imglib2.img.ImgFactory;
import net.imglib2.img.cell.Cell;
import net.imglib2.img.cell.CellGrid;
import net.imglib2.img.cell.LazyCellImg;
import net.imglib2.type.NativeType;
import net.imglib2.util.Fraction;

public class DiskCachedCellImg< T extends NativeType< T >, A > extends LazyCellImg< T, A >
{
	private final DiskCachedCellImgFactory< T > factory;

	public DiskCachedCellImg(
			final DiskCachedCellImgFactory< T > factory,
			final CellGrid grid,
			final Fraction entitiesPerPixel,
			final Get< Cell< A > > get )
	{
		super( grid, entitiesPerPixel, get );
		this.factory = factory;
	}

	@Override
	public ImgFactory< T > factory()
	{
		return factory;
	}
}
