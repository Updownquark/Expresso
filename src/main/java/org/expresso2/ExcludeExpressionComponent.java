package org.expresso2;

import java.io.IOException;
import java.util.List;

import org.expresso.parse.BranchableStream;

public class ExcludeExpressionComponent<S extends BranchableStream<?, ?>> extends SequenceExpression<S> {
	private final int[] theExcludedIds;

	public ExcludeExpressionComponent(int id, int[] excludedIds, List<ExpressionComponent<S>> components) {
		super(id, components);
		theExcludedIds = excludedIds;
	}

	@Override
	public <S2 extends S> ExpressionPossibility<S2> parse(ExpressoParser<S2> session) throws IOException {
		return super.parse(session.exclude(theExcludedIds));
	}
}
