package org.expresso3.types;

import java.io.IOException;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import org.expresso.stream.BranchableStream;
import org.expresso3.Expression;
import org.expresso3.ExpressionType;
import org.expresso3.ExpressoParser;

/**
 * An expression whose sequence may be present as a component in the stream, but also may be absent without invalidating the composite
 * parent
 *
 * @param <S> The type of the stream
 */
public class OptionalExpressionType<S extends BranchableStream<?, ?>> extends SequenceExpressionType<S> {
	/**
	 * @param id The cache ID for this expression type
	 * @param components The components of the sequence
	 */
	public OptionalExpressionType(int id, List<ExpressionType<S>> components) {
		super(id, components);
	}

	@Override
	public <S2 extends S> Expression<S2> parse(ExpressoParser<S2> session) throws IOException {
		Expression<S2> superPossibility = super.parse(session);
		boolean empty = superPossibility == null;
		if (empty)
			superPossibility = Expression.empty(session.getStream(), this);
		return new OptionalPossibility<>(this, session, superPossibility, empty, true);
	}

	@Override
	public String toString() {
		return "Optional:" + super.toString();
	}

	private static class OptionalPossibility<S extends BranchableStream<?, ?>> extends ComposedExpression<S> {
		private final Expression<S> theOption;
		private final boolean isEmpty;
		private final boolean isFirst;

		OptionalPossibility(OptionalExpressionType<? super S> type, ExpressoParser<S> parser, Expression<S> option, boolean empty,
			boolean first) {
			super(type, parser, Collections.unmodifiableList(Arrays.asList(option)));
			theOption = option;
			isEmpty = empty;
			isFirst = first;
		}

		@Override
		public OptionalExpressionType<? super S> getType() {
			return (OptionalExpressionType<? super S>) super.getType();
		}

		@Override
		protected int getSelfComplexity() {
			return 1;
		}

		@Override
		public Expression<S> nextMatch() throws IOException {
			Expression<S> optionNext = theOption.nextMatch();
			if (optionNext != null)
				return new OptionalPossibility<>(getType(), getParser(), optionNext, false, false);
			if (!isFirst && !isEmpty)
				new OptionalPossibility<>(getType(), getParser(), Expression.empty(getParser().getStream(), theOption.getType()), true,
					false);
			return null;
		}

		@Override
		public StringBuilder print(StringBuilder str, int indent, String metadata) {
			return theOption.print(str, indent, metadata + "(optional)");
		}
	}
}
