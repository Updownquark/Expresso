package org.expresso.runtime.typed;

import org.expresso.runtime.EvaluationTargetException;
import org.expresso.runtime.Statement;

import com.google.common.reflect.TypeToken;

public interface TypedStatement<E extends TypedExpressoEnvironment<E>, R> extends Statement<E, R> {
	TypeToken<R> getReturnType();

	@Override
	TypedResult<? extends R> execute(E env) throws EvaluationTargetException;
}
