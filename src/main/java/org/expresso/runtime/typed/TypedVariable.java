package org.expresso.runtime.typed;

import org.expresso.runtime.Variable;

import com.google.common.reflect.TypeToken;

public interface TypedVariable<T> extends Variable<T> {
	TypeToken<T> getType();
}
