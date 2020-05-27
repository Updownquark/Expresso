package org.expresso.runtime;

import org.expresso.Expression;

public class IfStatement<E extends ExpressoEnvironment<E>, R> implements Statement<E, R> {
	private final Expression theExpression;
	private final Statement<E, Boolean> theCondition;
	private final Statement<E, ? extends R> theBody;
	private final Statement<E, ? extends R> theElse;

	public IfStatement(Expression expression, Statement<E, ? extends Boolean> condition, Statement<E, ? extends R> body,
		Statement<E, ? extends R> _else) {
		theExpression = expression;
		theCondition = (Statement<E, Boolean>) condition;
		theBody = body;
		theElse = _else;
	}

	@Override
	public Expression getExpression() {
		return theExpression;
	}

	@Override
	public Result<? extends R> execute(E env) throws EvaluationTargetException {
		if (theCondition.execute(env).get())
			return theBody.execute(env);
		else if (theElse != null)
			return theElse.execute(env);
		else
			return getDefaultResult();
	}

	public Result<R> getDefaultResult() {
		return Result.noReturn();
	}
}
