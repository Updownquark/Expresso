package org.expresso.runtime.typed;

import java.util.List;

import org.expresso.Expression;
import org.expresso.runtime.CompilationException;
import org.expresso.runtime.EvaluationTargetException;
import org.expresso.runtime.SwitchStatement;

import com.google.common.reflect.TypeToken;

public class TypedSwitchStatement<E extends TypedExpressoEnvironment<E>, R, C> extends SwitchStatement<E, R, C>
	implements TypedStatement<E, R> {
	public static class TypedCaseStatement<E extends TypedExpressoEnvironment<E>, R, C> extends CaseStatement<E, R, C>
		implements TypedStatement<E, R> {
		private final TypeToken<R> theType;

		public TypedCaseStatement(Expression expression, TypeToken<R> type, List<? extends TypedStatement<E, ? extends C>> caseValues,
			List<? extends TypedStatement<E, ? extends R>> statements) {
			super(expression, caseValues, statements);
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

	public static class TypedDefaultCaseLabel<E extends TypedExpressoEnvironment<E>, C> extends DefaultCaseLabel<E, C>
		implements TypedStatement<E, C> {
		private final TypeToken<C> theType;

		public TypedDefaultCaseLabel(Expression expression, TypeToken<C> type) {
			super(expression);
			theType = type;
		}

		@Override
		public TypeToken<C> getReturnType() {
			return theType;
		}

		@Override
		public TypedResult<? extends C> execute(E env) {
			return (TypedResult<? extends C>) super.execute(env);
		}
	}

	private final TypeToken<R> theType;

	public TypedSwitchStatement(Expression expression, TypeToken<R> type, TypedStatement<E, C> condition,
		List<? extends TypedCaseStatement<E, ? extends R, ? extends C>> caseStatements, E env) throws CompilationException {
		super(expression, condition, caseStatements, env);
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
	public TypedResult<? extends R> getDefaultResult() {
		return TypedResult.noReturn(theType);
	}
}
