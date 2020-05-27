package org.expresso.runtime;

import org.expresso.Expression;

public class LabeledStatement<E extends ExpressoEnvironment<E>, R> implements Statement<E, R> {
	private final Expression theExpression;
	private final String theLabel;
	private final Statement<E, R> theStatement;

	public LabeledStatement(Expression expression, String label, Statement<E, R> statement) {
		theExpression = expression;
		theLabel = label;
		theStatement = statement;
	}

	@Override
	public Expression getExpression() {
		return theExpression;
	}

	public String getLabel() {
		return theLabel;
	}

	public Statement<E, R> getStatement() {
		return theStatement;
	}

	@Override
	public Result<? extends R> execute(E env) throws EvaluationTargetException {
		return theStatement.execute(env);
	}
}
