package org.expresso.runtime;

import org.expresso.Expression;

public class Loop<E extends ExpressoEnvironment<E>, R> implements Statement<E, R> {
	private final Expression theExpression;
	private final Statement<E, ?> theInitialization;
	private final Statement<E, Boolean> theCondition;
	private final Statement<E, ?> theIncrement;
	private final boolean checkInitialCondition;
	private final Statement<E, ? extends R> theBody;

	public Loop(Expression expression, Statement<E, ?> initialization, Statement<E, ? extends Boolean> condition, Statement<E, ?> increment,
		boolean checkInitialCondition, Statement<E, ? extends R> body) {
		theExpression = expression;
		theInitialization = initialization;
		theCondition = (Statement<E, Boolean>) condition;
		theIncrement = increment;
		this.checkInitialCondition = checkInitialCondition;
		theBody = body;
	}

	@Override
	public Expression getExpression() {
		return theExpression;
	}

	public Statement<E, ?> getInitialization() {
		return theInitialization;
	}

	public Statement<E, Boolean> getCondition() {
		return theCondition;
	}

	public Statement<E, ?> getIncrement() {
		return theIncrement;
	}

	public boolean isCheckInitialCondition() {
		return checkInitialCondition;
	}

	public Statement<E, ? extends R> getBody() {
		return theBody;
	}

	@Override
	public Result<? extends R> execute(E env) throws EvaluationTargetException {
		if (theInitialization != null)
			theInitialization.execute(env);
		boolean keepGoing;
		if (checkInitialCondition)
			keepGoing = theCondition.execute(env).get();
		else
			keepGoing = true;
		loop: while (keepGoing) {
			Result<? extends R> result = theBody.execute(env.scope(null));
			switch (result.getDirective()) {
			case RETURN:
			case THROW:
				return result;
			case BREAK:
				if (result.getDirectiveLabel() != null && !result.getDirectiveLabel().equals(env.getCurrentScopeLabel()))
					return result;
				break loop;
			case CONTINUE:
				if (result.getDirectiveLabel() != null && !result.getDirectiveLabel().equals(env.getCurrentScopeLabel()))
					return result;
				//$FALL-THROUGH$
			case NORMAL:
				break;
			}
			if (theIncrement != null)
				theIncrement.execute(env);

			keepGoing = theCondition.execute(env).get();
		}

		return getDefaultResult();
	}

	public Result<R> getDefaultResult() {
		return Result.noReturn();
	}
}
