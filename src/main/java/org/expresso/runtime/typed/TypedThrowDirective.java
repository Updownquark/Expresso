package org.expresso.runtime.typed;

import org.expresso.Expression;
import org.expresso.runtime.EvaluationTargetException;
import org.expresso.runtime.ThrowDirective;

import com.google.common.reflect.TypeToken;

public class TypedThrowDirective<E extends TypedExpressoEnvironment<E>, R, X extends Throwable> extends ThrowDirective<E, R, X>
	implements TypedStatement<E, R> {
	private final TypeToken<R> theType;

	public TypedThrowDirective(Expression expression, TypeToken<R> type, TypedStatement<E, ? extends X> value) {
		super(expression, value);
		theType = type;
	}

	@Override
	public TypeToken<R> getReturnType() {
		return theType;
	}

	@Override
	public TypedResult<? extends R> execute(E env) throws EvaluationTargetException {
		throw new EvaluationTargetException(getValue().execute(env).get());
	}
}
