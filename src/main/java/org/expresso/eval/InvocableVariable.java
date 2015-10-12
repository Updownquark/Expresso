package org.expresso.eval;

import java.util.List;

import com.google.common.reflect.TypeToken;

public interface InvocableVariable<T> {
	String getName();

	TypeToken<T> getReturnType();
	List<Variable<?>> getParameters();

	boolean isInvocable();
	boolean isInvocableWith(TypeToken<?>... parameterTypes);

	T invoke(Object... parameters);
}
