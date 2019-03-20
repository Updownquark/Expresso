package org.expresso2;

import java.io.IOException;
import java.util.Collections;
import java.util.List;

import org.expresso.parse.BranchableStream;

public abstract class LiteralExpressionType<C, S extends BranchableStream<?, ? super C>> extends AbstractExpressionComponent<S> {
	private final C theValue;

	public LiteralExpressionType(int id, C value) {
		super(id);
		theValue = value;
	}

	public C getValue() {
		return theValue;
	}

	/** @return The length of this matcher's value */
	protected abstract int getLength();

	/**
	 * @param stream The stream to check
	 * @return The character position in the stream up to the point where the stream no longer matches this matcher's value
	 */
	protected abstract int getMatchUntil(S stream) throws IOException;

	@Override
	public <S2 extends S> ExpressionPossibility<S2> parse(ExpressoParser<S2> parser, boolean useCache) throws IOException {
		return new LiteralPossibility<>(this, parser, getLength());
	}

	@Override
	public String toString() {
		return String.valueOf(theValue);
	}

	private static class LiteralPossibility<C, S extends BranchableStream<?, ? super C>> implements ExpressionPossibility<S> {
		private final LiteralExpressionType<C, ? super S> theType;
		private final ExpressoParser<S> theParser;
		private final int theLength;
		private final int matchedUntil;

		LiteralPossibility(LiteralExpressionType<C, ? super S> type, ExpressoParser<S> parser, int length) throws IOException {
			theType = type;
			theParser = parser;
			int discovered = parser.getStream().discoverTo(length);
			if (discovered < length) {
				theLength = discovered;
			} else {
				theLength = length;
			}
			matchedUntil = type.getMatchUntil(parser.getStream());
		}

		@Override
		public ExpressionComponent<? super S> getType() {
			return theType;
		}

		@Override
		public S getStream() {
			return theParser.getStream();
		}

		@Override
		public int length() {
			return theLength;
		}

		@Override
		public ExpressionPossibility<S> advance() throws IOException {
			return null;
		}

		@Override
		public ExpressionPossibility<S> leftFork() throws IOException {
			if (theLength > 1)
				return new LiteralPossibility<>(theType, theParser, theLength - 1);
			else
				return null;
		}

		@Override
		public ExpressionPossibility<S> rightFork() throws IOException {
			return null;
		}

		@Override
		public int getErrorCount() {
			return matchedUntil == theType.getLength() ? 0 : 1;
		}

		@Override
		public int getFirstErrorPosition() {
			return matchedUntil == theType.getLength() ? -1 : matchedUntil;
		}

		@Override
		public boolean isComplete() {
			return true;
		}

		@Override
		public boolean isEquivalent(ExpressionPossibility<S> o) {
			if (this == o)
				return true;
			else if (!(o instanceof LiteralPossibility))
				return false;
			LiteralPossibility<C, S> other = (LiteralPossibility<C, S>) o;
			return getType().equals(other.getType()) && matchedUntil == other.matchedUntil;
		}

		@Override
		public Expression<S> getExpression() {
			return new LiteralExpression<>(theType, theParser.getStream(), theLength, matchedUntil);
		}
	}

	private static class LiteralExpression<C, S extends BranchableStream<?, ? super C>> extends AbstractExpression<S> {
		private final int theLength;
		private final int matchedUntil;

		LiteralExpression(LiteralExpressionType<C, ? super S> type, S stream, int length, int matchedUntil) {
			super(stream, type);
			theLength = length;
			this.matchedUntil = matchedUntil;
		}

		@Override
		public LiteralExpressionType<C, ? super S> getType() {
			return (LiteralExpressionType<C, ? super S>) super.getType();
		}

		@Override
		public List<? extends Expression<S>> getChildren() {
			return Collections.emptyList();
		}

		@Override
		public Expression<S> getFirstError() {
			return matchedUntil == getType().getLength() ? null : this;
		}

		@Override
		public int getErrorCount() {
			return matchedUntil == getType().getLength() ? 0 : 1;
		}

		@Override
		public int getLocalErrorRelativePosition() {
			return matchedUntil == getType().getLength() ? -1 : matchedUntil;
		}

		@Override
		public String getLocalErrorMessage() {
			if (matchedUntil == getType().getLength())
				return null;
			else
				return "\"" + getType().getValue() + "\" expected";
		}

		@Override
		public int length() {
			return theLength;
		}

		@Override
		public Expression<S> unwrap() {
			return this;
		}
	}
}
