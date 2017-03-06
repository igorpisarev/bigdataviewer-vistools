/*
 * #%L
 * ImgLib2: a general-purpose, multidimensional image processing library.
 * %%
 * Copyright (C) 2009 - 2016 Tobias Pietzsch, Stephan Preibisch, Stephan Saalfeld,
 * John Bogovic, Albert Cardona, Barry DeZonia, Christian Dietz, Jan Funke,
 * Aivar Grislis, Jonathan Hale, Grant Harris, Stefan Helfrich, Mark Hiner,
 * Martin Horn, Steffen Jaensch, Lee Kamentsky, Larry Lindsey, Melissa Linkert,
 * Mark Longair, Brian Northan, Nick Perry, Curtis Rueden, Johannes Schindelin,
 * Jean-Yves Tinevez and Michael Zinsmaier.
 * %%
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions are met:
 *
 * 1. Redistributions of source code must retain the above copyright notice,
 *    this list of conditions and the following disclaimer.
 * 2. Redistributions in binary form must reproduce the above copyright notice,
 *    this list of conditions and the following disclaimer in the documentation
 *    and/or other materials provided with the distribution.
 *
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS"
 * AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE
 * IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE
 * ARE DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT HOLDERS OR CONTRIBUTORS BE
 * LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR
 * CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF
 * SUBSTITUTE GOODS OR SERVICES; LOSS OF USE, DATA, OR PROFITS; OR BUSINESS
 * INTERRUPTION) HOWEVER CAUSED AND ON ANY THEORY OF LIABILITY, WHETHER IN
 * CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE)
 * ARISING IN ANY WAY OUT OF THE USE OF THIS SOFTWARE, EVEN IF ADVISED OF THE
 * POSSIBILITY OF SUCH DAMAGE.
 * #L%
 */

package bdv.writecache.diskcached;

import static net.imglib2.cache.img.PrimitiveType.BYTE;
import static net.imglib2.cache.img.PrimitiveType.CHAR;
import static net.imglib2.cache.img.PrimitiveType.DOUBLE;
import static net.imglib2.cache.img.PrimitiveType.FLOAT;
import static net.imglib2.cache.img.PrimitiveType.INT;
import static net.imglib2.cache.img.PrimitiveType.LONG;
import static net.imglib2.cache.img.PrimitiveType.SHORT;

import java.io.IOException;
import java.nio.file.Path;

import net.imglib2.cache.IoSync;
import net.imglib2.cache.UncheckedLoadingCache;
import net.imglib2.cache.img.AccessIo;
import net.imglib2.cache.img.DiskCellCache;
import net.imglib2.cache.img.EmptyCellCacheLoader;
import net.imglib2.cache.img.PrimitiveType;
import net.imglib2.cache.ref.SoftRefListenableCache;
import net.imglib2.exception.IncompatibleTypeException;
import net.imglib2.img.ImgFactory;
import net.imglib2.img.NativeImgFactory;
import net.imglib2.img.basictypeaccess.array.ArrayDataAccess;
import net.imglib2.img.basictypeaccess.array.ByteArray;
import net.imglib2.img.basictypeaccess.array.CharArray;
import net.imglib2.img.basictypeaccess.array.DoubleArray;
import net.imglib2.img.basictypeaccess.array.FloatArray;
import net.imglib2.img.basictypeaccess.array.IntArray;
import net.imglib2.img.basictypeaccess.array.LongArray;
import net.imglib2.img.basictypeaccess.array.ShortArray;
import net.imglib2.img.cell.Cell;
import net.imglib2.img.cell.CellGrid;
import net.imglib2.img.cell.CellImgFactory;
import net.imglib2.type.NativeType;
import net.imglib2.util.Fraction;

/**
 * Factory for creating {@link DiskCachedCellImg}s. The cell dimensions for a
 * standard cell can be supplied in the constructor of the factory. If no cell
 * dimensions are given, the factory creates cells of size <em>10 x 10 x
 * ... x 10</em>.
 *
 * @author Tobias Pietzsch
 */
public class DiskCachedCellImgFactory< T extends NativeType< T > > extends NativeImgFactory< T >
{
	private final int[] defaultCellDimensions;

	public DiskCachedCellImgFactory()
	{
		this( 10 );
	}

	public DiskCachedCellImgFactory( final int... cellDimensions )
	{
		defaultCellDimensions = cellDimensions.clone();
		CellImgFactory.verifyDimensions( defaultCellDimensions );
	}

	@Override
	public DiskCachedCellImg< T, ? > create( final long[] dim, final T type )
	{
		return ( DiskCachedCellImg< T, ? > ) type.createSuitableNativeImg( this, dim );
	}

	@Override
	public DiskCachedCellImg< T, ByteArray > createByteInstance( final long[] dimensions, final Fraction entitiesPerPixel )
	{
		return createInstance( new ByteArray( 1 ), dimensions, entitiesPerPixel, BYTE );
	}

	@Override
	public DiskCachedCellImg< T, CharArray > createCharInstance( final long[] dimensions, final Fraction entitiesPerPixel )
	{
		return createInstance( new CharArray( 1 ), dimensions, entitiesPerPixel, CHAR );
	}

	@Override
	public DiskCachedCellImg< T, ShortArray > createShortInstance( final long[] dimensions, final Fraction entitiesPerPixel )
	{
		return createInstance( new ShortArray( 1 ), dimensions, entitiesPerPixel, SHORT );
	}

	@Override
	public DiskCachedCellImg< T, IntArray > createIntInstance( final long[] dimensions, final Fraction entitiesPerPixel )
	{
		return createInstance( new IntArray( 1 ), dimensions, entitiesPerPixel, INT );
	}

	@Override
	public DiskCachedCellImg< T, LongArray > createLongInstance( final long[] dimensions, final Fraction entitiesPerPixel )
	{
		return createInstance( new LongArray( 1 ), dimensions, entitiesPerPixel, LONG );
	}

	@Override
	public DiskCachedCellImg< T, FloatArray > createFloatInstance( final long[] dimensions, final Fraction entitiesPerPixel )
	{
		return createInstance( new FloatArray( 1 ), dimensions, entitiesPerPixel, FLOAT );
	}

	@Override
	public DiskCachedCellImg< T, DoubleArray > createDoubleInstance( final long[] dimensions, final Fraction entitiesPerPixel )
	{
		return createInstance( new DoubleArray( 1 ), dimensions, entitiesPerPixel, DOUBLE );
	}

	@SuppressWarnings( { "unchecked", "rawtypes" } )
	@Override
	public < S > ImgFactory< S > imgFactory( final S type ) throws IncompatibleTypeException
	{
		if ( NativeType.class.isInstance( type ) )
			return new DiskCachedCellImgFactory( defaultCellDimensions );
		throw new IncompatibleTypeException( this, type.getClass().getCanonicalName() + " does not implement NativeType." );
	}

	private < A extends ArrayDataAccess< A > >
			DiskCachedCellImg< T, A >
			createInstance( final A creator, final long[] dimensions, final Fraction entitiesPerPixel, final PrimitiveType primitiveType )
	{
		CellImgFactory.verifyDimensions( dimensions );

		final int n = dimensions.length;
		final int[] cellDimensions = CellImgFactory.getCellDimensions( defaultCellDimensions, n, entitiesPerPixel );
		final CellGrid grid = new CellGrid( dimensions, cellDimensions );

		Path blockcache;
		try
		{
			blockcache = DiskCellCache.createTempDirectory( "CellImg", true );
		}
		catch ( final IOException e )
		{
			throw new RuntimeException( e );
		}

		final DiskCellCache< A > diskcache = new DiskCellCache< A >(
				blockcache,
				grid,
				EmptyCellCacheLoader.get( grid, entitiesPerPixel, primitiveType ), // TODO: flags?
				AccessIo.get( primitiveType ), // TODO: flags?
				entitiesPerPixel );

		final IoSync< Long, Cell< A > > iosync = new IoSync<>( diskcache );

		final UncheckedLoadingCache< Long, Cell< A > > cache = new SoftRefListenableCache< Long, Cell< A > >()
				.withRemovalListener( iosync )
				.withLoader( iosync )
				.unchecked();

		return new DiskCachedCellImg<>( this, grid, entitiesPerPixel, cache::get );
	}
}
