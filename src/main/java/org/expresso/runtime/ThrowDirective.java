package org.expresso.runtime;

import org.expresso.Expression;

public class ThrowDirective<E extends ExpressoEnvironment<E>, R, X extends Throwable> implements Statement<E, R> {
	private final Expression theExpression;
	private final Statement<E, ? extends X> theValue;

	public ThrowDirective(Expression expression, Statement<E, ? extends X> value) {
		theExpression = expression;
		theValue = value;
	}

	@Override
	public Expression getExpression() {
		return theExpression;
	}

	public Statement<E, ? extends X> getValue() {
		return theValue;
	}

	@Override
	public Result<? extends R> execute(E env) throws EvaluationTargetException {
		throw new EvaluationTargetException(theValue.execute(env).get());
	}
}
