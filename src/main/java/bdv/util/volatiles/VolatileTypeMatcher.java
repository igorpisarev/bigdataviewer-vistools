package bdv.util.volatiles;

import net.imglib2.type.NativeType;
import net.imglib2.type.numeric.integer.UnsignedByteType;
import net.imglib2.type.volatiles.VolatileUnsignedByteType;

public class VolatileTypeMatcher
{
	public static < T extends NativeType< T > > NativeType< ? > getVolatileTypeForType( final T type )
	{
		if ( type instanceof UnsignedByteType )
			return new VolatileUnsignedByteType();

		return null;
	}
}