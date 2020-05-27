package org.expresso.runtime.typed;

import org.expresso.Expression;

import com.google.common.reflect.TypeToken;

public class EmptyStatement<E extends TypedExpressoEnvironment<E>, T> implements TypedStatement<E, T> {
	private final Expression theExpression;
	private final TypeToken<T> theType;

	public EmptyStatement(Expression expression, TypeToken<T> type) {
		theExpression = expression;
		theType = type;
	}

	@Override
	public Expression getExpression() {
		return theExpression;
	}

	@Override
	public TypeToken<T> getReturnType() {
		return theType;
	}

	@Override
	public TypedResult<T> execute(E env) {
		return null;
	}
}
