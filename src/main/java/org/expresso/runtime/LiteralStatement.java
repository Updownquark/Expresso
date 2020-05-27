package org.expresso.runtime;

import org.expresso.Expression;

public class LiteralStatement<E extends ExpressoEnvironment<E>, T> implements Statement<E, T>, Result<T> {
	private final Expression theExpression;
	private final T theValue;

	public LiteralStatement(Expression expression, T value) {
		theExpression = expression;
		theValue = value;
	}

	@Override
	public Expression getExpression() {
		return theExpression;
	}

	@Override
	public ControlFlowDirective getDirective() {
		return ControlFlowDirective.NORMAL;
	}

	@Override
	public String getDirectiveLabel() {
		return null;
	}

	@Override
	public Result<T> execute(E env) {
		return this;
	}

	@Override
	public T get() {
		return theValue;
	}

	@Override
	public String toString() {
		return theExpression.toString();
	}
}
