package org.expresso2;

import org.expresso.parse.BranchableStream;

public class LeadUpExpression<S extends BranchableStream<?, ?>> extends ExpressionComponent<S> {
	private final ExpressionComponent<S> theContent;

	public LeadUpExpression(int id, ExpressionComponent<S> content) {
		super(id);
		theContent = content;
	}
}
