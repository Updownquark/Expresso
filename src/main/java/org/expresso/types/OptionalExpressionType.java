package org.expresso.types;

import java.io.IOException;
import java.util.Arrays;
import java.util.List;

import org.expresso.Expression;
import org.expresso.ExpressionType;
import org.expresso.ExpressoParser;
import org.expresso.stream.BranchableStream;

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
	public <S2 extends S> Expression<S2> parse(ExpressoParser<S2> parser, Expression<S2> lowBound, Expression<S2> highBound)
		throws IOException {
		OptionalPossibility<S2> low = (OptionalPossibility<S2>) lowBound;
		if (low != null && low.theOption.length() == 0)
			return null;
		OptionalPossibility<S2> high = (OptionalPossibility<S2>) highBound;
		Expression<S2> superPossibility = super.parse(parser, //
			low == null ? null : low.theOption, //
			(high == null || high.theOption.length() == 0) ? null : high.theOption);
		if (superPossibility == null) {
			if (high != null && high.theOption.length() == 0)
				return null;
			superPossibility = Expression.empty(parser.getStream(), this);
		}
		return new OptionalPossibility<>(this, parser, superPossibility);
	}

	@Override
	public int compare(Expression<? extends S> o1, Expression<? extends S> o2) {
		Expression<? extends S> opt1 = ((OptionalPossibility<? extends S>) o1).theOption;
		Expression<? extends S> opt2 = ((OptionalPossibility<? extends S>) o2).theOption;
		if (opt1.length() == 0) {
			if (opt2.length() == 0)
				return 0;
			else
				return 1;
		} else if (opt2.length() == 0)
			return -1;
		else
			return super.compare(opt1, opt2);
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
			Expression<S> optionNext = parser.nextMatch(theOption);
			if (optionNext != null)
				return new OptionalPossibility<>(getType(), parser, optionNext);
			if (theOption.length() > 0)
				new OptionalPossibility<>(getType(), parser, Expression.empty(parser.getStream(), theOption.getType()));
			return null;
		}

		@Override
		public Expression<S> nextMatchHighPriority(ExpressoParser<S> parser) throws IOException {
			return null;
		}

		@Override
		public Expression<S> nextMatchLowPriority(ExpressoParser<S> parser, Expression<S> limit) throws IOException {
			return null;
		}

		@Override
		public StringBuilder print(StringBuilder str, int indent, String metadata) {
			return theOption.print(str, indent, metadata + "(optional)");
		}
	}
}
