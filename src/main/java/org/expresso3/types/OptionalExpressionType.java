package org.expresso3.types;

import java.io.IOException;
import java.util.Arrays;
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
	public <S2 extends S> Expression<S2> parse(ExpressoParser<S2> parser) throws IOException {
		Expression<S2> superPossibility = super.parse(parser);
		if (superPossibility == null)
			superPossibility = Expression.empty(parser.getStream(), this);
		return new OptionalPossibility<>(this, parser, superPossibility);
	}

	@Override
	public int getEmptyQuality(int minQuality) {
		return 0;
	}

	@Override
	public String toString() {
		return "Optional:" + super.toString();
	}

	private static class OptionalPossibility<S extends BranchableStream<?, ?>> extends ComposedExpression<S> {
		private final Expression<S> theOption;

		OptionalPossibility(OptionalExpressionType<? super S> type, ExpressoParser<S> parser, Expression<S> option) {
			super(type, parser, Arrays.asList(option));
			theOption = option;
		}

		@Override
		public OptionalExpressionType<? super S> getType() {
			return (OptionalExpressionType<? super S>) super.getType();
		}

		@Override
		public Expression<S> nextMatch(ExpressoParser<S> parser) throws IOException {
			if (theOption.isInvariant())
				return null;
			Expression<S> optionNext = parser.nextMatch(theOption);
			if (optionNext != null)
				return new OptionalPossibility<>(getType(), parser, optionNext);
			if (theOption.length() > 0)
				new OptionalPossibility<>(getType(), parser, Expression.empty(parser.getStream(), theOption.getType()));
			return null;
		}

		@Override
		public StringBuilder print(StringBuilder str, int indent, String metadata) {
			return theOption.print(str, indent, metadata + "(optional)");
		}
	}
}
