package org.expresso.runtime.typed;

import org.expresso.Expression;
import org.expresso.runtime.ControlFlowDirective;
import org.expresso.runtime.EvaluationTargetException;
import org.expresso.runtime.Result;
import org.expresso.runtime.ReturnDirective;

import com.google.common.reflect.TypeToken;

public class TypedReturnDirective<E extends TypedExpressoEnvironment<E>, R> extends ReturnDirective<E, R> implements TypedStatement<E, R> {
	private final TypeToken<R> theType;

	public TypedReturnDirective(Expression expression, TypeToken<R> type, TypedStatement<E, ? extends R> value) {
		super(expression, value);
		theType = type;
	}

	@Override
	public TypeToken<R> getReturnType() {
		return theType;
	}

	@Override
	public TypedStatement<E, ? extends R> getValue() {
		return (TypedStatement<E, ? extends R>) super.getValue();
	}

	@Override
	public TypedResult<? extends R> execute(E env) throws EvaluationTargetException {
		if (getValue() != null) {
			Result<? extends R> result = getValue().execute(env);
			return new TypedResult<R>() {
				@Override
				public TypeToken<R> getType() {
					return theType;
				}

				@Override
				public R get() {
					return result.get();
				}

				@Override
				public ControlFlowDirective getDirective() {
					return ControlFlowDirective.RETURN;
				}

				@Override
				public String getDirectiveLabel() {
					return null;
				}
			};
		} else
			return new TypedResult<R>() {
				@Override
				public TypeToken<R> getType() {
					return theType;
				}

				@Override
				public R get() {
					return null;
				}

				@Override
				public ControlFlowDirective getDirective() {
					return ControlFlowDirective.RETURN;
				}

				@Override
				public String getDirectiveLabel() {
					return null;
				}
			};
	}
}
