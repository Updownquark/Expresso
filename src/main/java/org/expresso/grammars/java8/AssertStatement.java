package org.expresso.grammars.java8;

import org.expresso.Expression;
import org.expresso.runtime.EvaluationTargetException;
import org.expresso.runtime.typed.TypedResult;
import org.expresso.runtime.typed.TypedStatement;

import com.google.common.reflect.TypeToken;

public class AssertStatement<E extends JavaEnvironment<E>, R> implements TypedStatement<E, R> {
	private final Expression theExpression;
	private final TypeToken<R> theType;
	private final TypedStatement<E, Boolean> theAssertion;
	private final TypedStatement<E, ?> theFailValue;

	public AssertStatement(Expression expression, TypeToken<R> type, TypedStatement<E, ? extends Boolean> assertion,
		TypedStatement<E, ?> failValue) {
		theExpression = expression;
		theType = type;
		theAssertion = (TypedStatement<E, Boolean>) assertion;
		theFailValue = failValue;
	}

	@Override
	public Expression getExpression() {
		return theExpression;
	}

	@Override
	public TypeToken<R> getReturnType() {
		return theType;
	}

	@Override
	public TypedResult<R> execute(E env) throws EvaluationTargetException {
		if (env.isAssertionEnabled() && !theAssertion.execute(env).get()) {
			if (theFailValue != null)
				throw new AssertionError(theFailValue.execute(env).get());
			else
				throw new AssertionError();
		}
		return TypedResult.noReturn(theType);
	}
}
