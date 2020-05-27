package org.expresso.runtime.typed;

import org.expresso.Expression;
import org.expresso.runtime.EvaluationTargetException;
import org.expresso.runtime.Loop;

import com.google.common.reflect.TypeToken;

public class TypedLoop<E extends TypedExpressoEnvironment<E>, R> extends Loop<E, R> implements TypedStatement<E, R> {
	private final TypeToken<R> theType;

	public TypedLoop(Expression expression, TypeToken<R> type, TypedStatement<E, ?> initialization,
		TypedStatement<E, ? extends Boolean> condition,
		TypedStatement<E, ?> increment, boolean checkInitialCondition, TypedStatement<E, ? extends R> body) {
		super(expression, initialization, condition, increment, checkInitialCondition, body);
		theType = type;
	}

	@Override
	public TypeToken<R> getReturnType() {
		return theType;
	}

	@Override
	public TypedResult<? extends R> execute(E env) throws EvaluationTargetException {
		return (TypedResult<? extends R>) super.execute(env);
	}

	@Override
	public TypedResult<R> getDefaultResult() {
		return TypedResult.noReturn(theType);
	}
}
