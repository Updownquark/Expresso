package org.expresso.runtime.typed;

import java.util.List;

import org.expresso.Expression;
import org.expresso.runtime.EvaluationTargetException;
import org.expresso.runtime.TryStatement;

import com.google.common.reflect.TypeToken;

public class TypedTryStatement<E extends TypedExpressoEnvironment<E>, R> extends TryStatement<E, R> implements TypedStatement<E, R> {
	public static class TypedCatch<E extends TypedExpressoEnvironment<E>, R, X extends Throwable> extends Catch<E, R, X> {
		private final TypeToken<R> theType;

		public TypedCatch(TypeToken<R> type, List<Class<? extends X>> exceptionTypes,
			TypedVariableDeclaration.TypedDeclaredVariable<E, X> variable, TypedBlock<E, R> block, boolean isFinal) {
			super(exceptionTypes, variable, block, isFinal);
			theType = type;
		}
	}

	private final TypeToken<R> theType;

	public TypedTryStatement(Expression expression, TypeToken<R> type, List<? extends TypedStatement<E, ? extends AutoCloseable>> resources,
		TypedBlock<E, R> body, List<? extends TypedCatch<E, R, ?>> catches, TypedBlock<E, R> _finally) {
		super(expression, resources, body, catches, _finally);
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
}
