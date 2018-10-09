package org.expresso2;

import org.expresso.parse.BranchableStream;

public class ExcludeExpression<S extends BranchableStream<?, ?>> extends ExpressionComponent<S> {
	private int[] theExcludedIds;
	private final ExpressionComponent<S> theComponent;

	public ExcludeExpression(int id, int[] excludedIds, ExpressionComponent<S> component) {
		super(id);
		theExcludedIds = excludedIds;
		theComponent = component;
	}

	public ExpressionComponent<S> getComponent() {
		return theComponent;
	}

	@Override
	public <S2 extends S> ExpressionPossibilitySequence<S2> tryParse(ExpressoParser<S2> session) {
		return theComponent.tryParse(session.exclude(theExcludedIds));
	}
}
