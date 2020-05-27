package org.expresso.runtime.typed;

import org.expresso.Expression;
import org.expresso.runtime.LiteralStatement;

import com.google.common.reflect.TypeToken;

public class TypedLiteralStatement<E extends TypedExpressoEnvironment<E>, T> extends LiteralStatement<E, T>
	implements TypedStatement<E, T>, TypedResult<T> {
	private final TypeToken<T> theType;

	public TypedLiteralStatement(Expression expression, TypeToken<T> type, T value) {
		super(expression, value);
		theType = type;
	}

	@Override
	public TypeToken<T> getType() {
		return theType;
	}

	@Override
	public TypeToken<T> getReturnType() {
		return theType;
	}

	@Override
	public TypedResult<T> execute(E env) {
		return this;
	}
}
