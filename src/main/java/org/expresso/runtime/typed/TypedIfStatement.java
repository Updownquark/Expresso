package org.expresso.runtime.typed;

import org.expresso.Expression;
import org.expresso.runtime.EvaluationTargetException;
import org.expresso.runtime.IfStatement;
import org.expresso.runtime.Statement;

import com.google.common.reflect.TypeToken;

public class TypedIfStatement<E extends TypedExpressoEnvironment<E>, R> extends IfStatement<E, R> implements TypedStatement<E, R> {
	private final TypeToken<R> theType;

	public TypedIfStatement(Expression expression, TypeToken<R> type, Statement<E, ? extends Boolean> condition,
		Statement<E, ? extends R> body, Statement<E, ? extends R> _else) {
		super(expression, condition, body, _else);
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
