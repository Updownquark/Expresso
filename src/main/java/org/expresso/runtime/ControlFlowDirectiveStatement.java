package org.expresso.runtime;

import org.expresso.Expression;

public class ControlFlowDirectiveStatement<E extends ExpressoEnvironment<E>, R> implements Statement<E, R> {
	private final Expression theExpression;
	private final ControlFlowDirective theDirective;
	private final String theTargetLabel;

	public ControlFlowDirectiveStatement(Expression expression, ControlFlowDirective directive, String targetLabel) {
		switch (directive) {
		case BREAK:
		case CONTINUE:
			break;
		default:
			throw new IllegalArgumentException("" + directive);
		}
		theExpression = expression;
		theDirective = directive;
		theTargetLabel = targetLabel;
	}

	@Override
	public Expression getExpression() {
		return theExpression;
	}

	public ControlFlowDirective getDirective() {
		return theDirective;
	}

	public String getTargetLabel() {
		return theTargetLabel;
	}

	@Override
	public Result<? extends R> execute(E env) {
		return new Result<R>() {
			@Override
			public R get() {
				throw new IllegalStateException();
			}

			@Override
			public ControlFlowDirective getDirective() {
				return theDirective;
			}

			@Override
			public String getDirectiveLabel() {
				return theTargetLabel;
			}
		};
	}
}
