package org.expresso2;

import java.io.IOException;
import java.util.Collections;
import java.util.List;

import org.expresso.parse.BranchableStream;

public abstract class LiteralExpressionType<C, S extends BranchableStream<?, ? super C>> extends ExpressionComponent<S> {
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
	 * @return Whether the given stream starts with this matcher's value
	 */
	protected abstract boolean startsWithValue(S stream) throws IOException;

	@Override
	public <S2 extends S> ExpressionPossibility<S2> tryParse(ExpressoParser<S2> parser) throws IOException {
		return new LiteralPossibility<>(this, parser, getLength(), true, true);
	}

	private static class LiteralPossibility<C, S extends BranchableStream<?, ? super C>> implements ExpressionPossibility<S> {
		private final LiteralExpressionType<C, ? super S> theType;
		private final ExpressoParser<S> theParser;
		private final int theLength;
		private final boolean isMatched;
		private boolean allowMore;
		private boolean allowLess;

		LiteralPossibility(LiteralExpressionType<C, ? super S> type, ExpressoParser<S> parser, int length, boolean allowMore,
			boolean allowLess) throws IOException {
			theType = type;
			theParser = parser;
			if (parser.getStream().discoverTo(length) < length) {
				theLength = parser.getStream().discoverTo(length);
				this.allowMore = false;
				isMatched = false;
			} else {
				theLength = length;
				this.allowMore = allowMore;
				isMatched = length == theType.getLength() && theType.startsWithValue(parser.getStream());
			}
			this.allowMore = allowMore;
			this.allowLess = length > 0 && allowLess;
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
		public boolean hasFork() {
			return allowLess || allowMore;
		}

		@Override
		public ExpressionPossibility<S> fork() throws IOException {
			if (allowLess) {
				allowLess = false;
				return new LiteralPossibility<>(theType, theParser, theLength - 1, true, false);
			} else if (allowMore) {
				allowMore = false;
				return new LiteralPossibility<>(theType, theParser, theLength + 1, false, true);
			} else
				return null;
		}

		@Override
		public int getErrorCount() {
			return isMatched ? 0 : 1;
		}

		@Override
		public boolean isComplete() {
			return true;
		}

		@Override
		public Expression<S> getExpression() {
			return new LiteralExpression<>(theType, theParser.getStream(), theLength, isMatched);
		}
	}

	private static class LiteralExpression<C, S extends BranchableStream<?, ? super C>> extends Expression<S> {
		private final int theLength;
		private final boolean isMatched;

		LiteralExpression(LiteralExpressionType<C, ? super S> type, S stream, int length, boolean match) {
			super(stream, type);
			theLength = length;
			isMatched = match;
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
			return isMatched ? null : this;
		}

		@Override
		public int getErrorCount() {
			return isMatched ? 0 : 1;
		}

		@Override
		public String getErrorMessage() {
			if(isMatched)
				return null;
			else
				return "\"" + getType().getValue() + "\" expected";
		}

		@Override
		public boolean isComplete() {
			return true;
		}

		@Override
		public int length() {
			return theLength;
		}
	}
}
