package org.expresso2;

import java.util.List;

import org.expresso.parse.BranchableStream;

public class ErrorExpression<S extends BranchableStream<?, ?>> extends ComposedExpression<S> {
	private final String message;

	public ErrorExpression(S stream, ExpressionComponent<? super S> type, List<? extends Expression<S>> children, String message) {
		super(stream, type, children);
		this.message = message;
	}
}
