package org.expresso.runtime;

import java.util.List;

import org.expresso.Expression;

public class VariableDeclaration<E extends ExpressoEnvironment<E>, R> implements Statement<E, R> {
	public static abstract class DeclaredVariable<E extends ExpressoEnvironment<E>, T> {
		public final String name;
		public final Statement<E, T> init;

		public DeclaredVariable(String name, Statement<E, T> init) {
			this.name = name;
			this.init = init;
		}

		public Variable<T> get(E env, boolean isFinal) throws EvaluationTargetException {
			T value;
			if (init != null)
				value = init.execute(env).get();
			else
				value = null;
			Variable<T> v = declare(env, isFinal, value);
			return v;
		}

		public abstract Variable<T> declare(E env, boolean isFinal, T value);
	}

	private final Expression theExpression;
	private final List<? extends DeclaredVariable<E, ?>> theVariables;
	private final boolean isFinal;

	public VariableDeclaration(Expression expression, List<? extends DeclaredVariable<E, ?>> variables, boolean isFinal) {
		theExpression = expression;
		theVariables = variables;
		this.isFinal = isFinal;
	}

	@Override
	public Expression getExpression() {
		return theExpression;
	}

	@Override
	public Result<R> execute(E env) throws EvaluationTargetException {
		for (DeclaredVariable<E, ?> v : theVariables)
			v.get(env, isFinal);
		return getResult();
	}

	public Result<R> getResult() {
		return Result.noReturn();
	}
}
