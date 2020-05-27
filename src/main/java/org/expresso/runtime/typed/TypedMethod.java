package org.expresso.runtime.typed;

import java.util.List;

import org.expresso.runtime.Method;

import com.google.common.reflect.TypeToken;

public interface TypedMethod<E extends TypedExpressoEnvironment<E>, R> extends Method<E, R> {
	TypeToken<R> getReturnType();

	List<TypeToken<?>> getParameterTypes();

	boolean isVarArg();
}
