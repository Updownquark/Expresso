package org.expresso.types;

import java.io.IOException;
import java.util.List;

import org.expresso.Expression;
import org.expresso.ExpressionType;
import org.expresso.ExpressoParser;
import org.expresso.stream.BranchableStream;

public class ExcludeExpressionType<S extends BranchableStream<?, ?>> extends SequenceExpressionType<S> {
	private final int[] theExcludedIds;

	public ExcludeExpressionType(int id, int[] excludedIds, List<ExpressionType<S>> components) {
		super(id, components);
		theExcludedIds = excludedIds;
	}

	@Override
	public <S2 extends S> Expression<S2> parse(ExpressoParser<S2> session) throws IOException {
		return super.parse(session.exclude(theExcludedIds));
	}
}
