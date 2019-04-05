package org.expresso3.types;

import java.io.IOException;
import java.util.List;

import org.expresso.stream.BranchableStream;
import org.expresso3.Expression;
import org.expresso3.ExpressionType;
import org.expresso3.ExpressoParser;

/**
 * A sequence that precludes parsing certain expression types for its content
 *
 * @param <S> The type of the stream
 */
public class ExcludeExpressionType<S extends BranchableStream<?, ?>> extends SequenceExpressionType<S> {
	private final int[] theExcludedIds;

	/**
	 * @param id The cache ID for this expression type
	 * @param excludedIds The cache IDs of the expression types to exclude
	 * @param components The components of the sequence
	 */
	public ExcludeExpressionType(int id, int[] excludedIds, List<ExpressionType<S>> components) {
		super(id, components);
		theExcludedIds = excludedIds;
	}

	@Override
	public <S2 extends S> Expression<S2> parse(ExpressoParser<S2> session) throws IOException {
		return super.parse(session.exclude(theExcludedIds));
	}
}
