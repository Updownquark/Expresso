package org.expresso.runtime;

import java.util.List;

import org.expresso.Expression;

public class Block<E extends ExpressoEnvironment<E>, T> extends Object implements Statement<E, T> {
	private final Expression theExpression;
	private final List<? extends Statement<E, ? extends T>> theStatements;

	public Block(Expression expression, List<? extends Statement<E, ? extends T>> statements) {
		theExpression = expression;
		theStatements = statements;
	}

	@Override
	public Expression getExpression() {
		return theExpression;
	}

	public List<? extends Statement<E, ? extends T>> getStatements() {
		return theStatements;
	}

	@Override
	public Result<? extends T> execute(E env) throws EvaluationTargetException {
		for (Statement<E, ? extends T> stmt : theStatements) {
			Result<? extends T> result = stmt.execute(env);
			if (result.getDirective() != ControlFlowDirective.NORMAL)
				return result;
		}
		return getDefaultResult();
	}

	public Result<T> getDefaultResult() {
		return Result.noReturn();
	}
}
