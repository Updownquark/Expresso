package org.expresso.runtime.typed;

import java.util.List;

import org.expresso.Expression;
import org.expresso.runtime.Block;
import org.expresso.runtime.EvaluationTargetException;

import com.google.common.reflect.TypeToken;

public class TypedBlock<E extends TypedExpressoEnvironment<E>, R> extends Block<E, R> implements TypedStatement<E, R> {
	private final TypeToken<R> theType;

	public TypedBlock(Expression expression, TypeToken<R> type, List<? extends TypedStatement<E, ? extends R>> statements) {
		super(expression, statements);
		theType = type;
	}

	@Override
	public TypeToken<R> getReturnType() {
		return theType;
	}

	@Override
	public List<? extends TypedStatement<E, ? extends R>> getStatements() {
		return (List<? extends TypedStatement<E, ? extends R>>) super.getStatements();
	}

	@Override
	public TypedResult<? extends R> execute(E env) throws EvaluationTargetException {
		return (TypedResult<? extends R>) super.execute(env);
	}

	@Override
	public TypedResult<R> getDefaultResult() {
		return TypedResult.noReturn(getReturnType());
	}
}
