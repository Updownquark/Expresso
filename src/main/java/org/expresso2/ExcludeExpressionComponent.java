package org.expresso2;

import java.io.IOException;

import org.expresso.parse.BranchableStream;

public class ExcludeExpressionComponent<S extends BranchableStream<?, ?>> extends ExpressionComponent<S> {
	private int[] theExcludedIds;
	private final ExpressionComponent<S> theComponent;

	public ExcludeExpressionComponent(int id, int[] excludedIds, ExpressionComponent<S> component) {
		super(id);
		theExcludedIds = excludedIds;
		theComponent = component;
	}

	public ExpressionComponent<S> getComponent() {
		return theComponent;
	}

	@Override
	public <S2 extends S> ExpressionPossibility<S2> tryParse(ExpressoParser<S2> session) throws IOException {
		return theComponent.tryParse(session.exclude(theExcludedIds));
	}
}
