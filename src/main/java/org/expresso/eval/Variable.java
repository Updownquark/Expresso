package org.expresso.eval;

import java.util.function.Consumer;
import java.util.function.Supplier;

import com.google.common.reflect.TypeToken;

public interface Variable<T> extends Supplier<T>, Consumer<T> {
	String getName();

	TypeToken<T> getType();

	String isDereferenceable();

	String isAssignable();

	/**
	 * @return The current value of this variable
	 * @throws IllegalStateException If this variable is not {@link #isDereferenceable() dereferenceable}
	 */
	@Override
	T get() throws IllegalStateException;

	/**
	 * Sets the value of this variable
	 *
	 * @param value The new value for this variable
	 * @throws IllegalArgumentException If the given value is not acceptable for this variable
	 * @throws IllegalStateException If this variable is not {@link #isAssignable() assignable}
	 */
	@Override
	void accept(T value) throws IllegalArgumentException, IllegalStateException;
}
