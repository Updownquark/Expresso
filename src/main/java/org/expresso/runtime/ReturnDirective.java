package org.expresso.runtime;

import org.expresso.Expression;

public class ReturnDirective<E extends ExpressoEnvironment<E>, R> implements Statement<E, R> {
	private final Expression theExpression;
	private final Statement<E, ? extends R> theValue;

	public ReturnDirective(Expression expression, Statement<E, ? extends R> value) {
		theExpression = expression;
		theValue = value;
	}

	@Override
	public Expression getExpression() {
		return theExpression;
	}

	public Statement<E, ? extends R> getValue() {
		return theValue;
	}

	@Override
	public Result<? extends R> execute(E env) throws EvaluationTargetException {
		if (theValue != null) {
			Result<? extends R> result = theValue.execute(env);
			return new Result<R>() {
				@Override
				public R get() {
					return result.get();
				}

				@Override
				public ControlFlowDirective getDirective() {
					return ControlFlowDirective.RETURN;
				}

				@Override
				public String getDirectiveLabel() {
					return null;
				}
			};
		} else
			return new Result<R>() {
				@Override
				public R get() {
					return null;
				}

				@Override
				public ControlFlowDirective getDirective() {
					return ControlFlowDirective.RETURN;
				}

				@Override
				public String getDirectiveLabel() {
					return null;
				}
			};
	}
}
