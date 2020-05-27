package org.expresso.runtime.typed;

import java.util.List;

import org.expresso.Expression;
import org.expresso.runtime.EvaluationTargetException;
import org.expresso.runtime.VariableDeclaration;

import com.google.common.reflect.TypeToken;

public class TypedVariableDeclaration<E extends TypedExpressoEnvironment<E>, R> extends VariableDeclaration<E, R>
	implements TypedStatement<E, R> {
	public static abstract class TypedDeclaredVariable<E extends TypedExpressoEnvironment<E>, T> extends DeclaredVariable<E, T> {
		public final TypeToken<T> type;

		public TypedDeclaredVariable(String name, TypeToken<T> type, TypedStatement<E, T> init) {
			super(name, init);
			this.type = type;
		}

		@Override
		public TypedVariable<T> get(E env, boolean isFinal) throws EvaluationTargetException {
			return (TypedVariable<T>) super.get(env, isFinal);
		}

		@Override
		public abstract TypedVariable<T> declare(E env, boolean isFinal, T value);
	}

	private final TypeToken<R> theType;

	public TypedVariableDeclaration(Expression expression, TypeToken<R> type, List<? extends TypedDeclaredVariable<E, ?>> variables,
		boolean isFinal) {
		super(expression, variables, isFinal);
		theType = type;
	}

	@Override
	public TypedResult<R> execute(E env) throws EvaluationTargetException {
		return (TypedResult<R>) super.execute(env);
	}

	@Override
	public TypeToken<R> getReturnType() {
		return theType;
	}

	@Override
	public TypedResult<R> getResult() {
		return TypedResult.noReturn(theType);
	}
}
