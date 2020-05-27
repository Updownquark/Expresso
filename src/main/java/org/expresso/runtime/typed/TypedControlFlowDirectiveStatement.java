package org.expresso.runtime.typed;

import org.expresso.Expression;
import org.expresso.runtime.ControlFlowDirective;
import org.expresso.runtime.ControlFlowDirectiveStatement;

import com.google.common.reflect.TypeToken;

public class TypedControlFlowDirectiveStatement<E extends TypedExpressoEnvironment<E>, R> extends ControlFlowDirectiveStatement<E, R>
	implements TypedStatement<E, R> {
	private final TypeToken<R> theType;

	public TypedControlFlowDirectiveStatement(Expression expression, TypeToken<R> type, ControlFlowDirective directive,
		String targetLabel) {
		super(expression, directive, targetLabel);
		theType = type;
	}

	@Override
	public TypeToken<R> getReturnType() {
		return theType;
	}

	@Override
	public TypedResult<? extends R> execute(E env) {
		return new TypedResult<R>() {
			@Override
			public ControlFlowDirective getDirective() {
				return TypedControlFlowDirectiveStatement.this.getDirective();
			}

			@Override
			public String getDirectiveLabel() {
				return getTargetLabel();
			}

			@Override
			public R get() {
				throw new IllegalStateException();
			}

			@Override
			public TypeToken<R> getType() {
				return theType;
			}
		};
	}
}
