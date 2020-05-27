package org.expresso.runtime;

import org.qommons.Named;

public interface Variable<T> extends Result<T>, Named {
	T set(T newValue, Object cause);
}
