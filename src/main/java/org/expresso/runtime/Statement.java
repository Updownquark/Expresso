package org.expresso.runtime;

import org.expresso.Expression;

public interface Statement<E extends ExpressoEnvironment<E>, R> {
	Expression getExpression();

	Result<? extends R> execute(E env) throws EvaluationTargetException;
}
