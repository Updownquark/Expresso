package org.expresso2;

import java.io.IOException;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

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
	public abstract int hashCode();

	@Override
	public abstract boolean equals(Object o);

	@Override
	public <S2 extends S> ExpressionPossibility<S2> parse(ExpressoParser<S2> parser) throws IOException {
		return new LiteralPossibility<>(this, parser);
	}

	@Override
	public String toString() {
		return "Literal(" + theValue + ")";
	}

	private static class LiteralPossibility<C, S extends BranchableStream<?, ? super C>> implements ExpressionPossibility<S> {
		private final LiteralExpressionType<C, ? super S> theType;
		private final ExpressoParser<S> theParser;
		private final int theLength;

		LiteralPossibility(LiteralExpressionType<C, ? super S> type, ExpressoParser<S> parser) throws IOException {
			theType = type;
			theParser = parser;
			parser.getStream().discoverTo(type.getLength());
			theLength = type.getMatchUntil(parser.getStream());
		}

		private LiteralPossibility(LiteralExpressionType<C, ? super S> type, ExpressoParser<S> parser, int length) throws IOException {
			theType = type;
			theParser = parser;
			theLength = length;
		}

		@Override
		public LiteralExpressionType<C, ? super S> getType() {
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
		public Collection<? extends ExpressionPossibility<S>> fork() throws IOException {
			if (theLength > 1)
				return Arrays.asList(new LiteralPossibility<>(theType, theParser, theLength - 1));
			else
				return Collections.emptyList();
		}

		@Override
		public int getErrorCount() {
			return theLength == theType.getLength() ? 0 : 1;
		}

		@Override
		public int getFirstErrorPosition() {
			return theLength == theType.getLength() ? -1 : theLength;
		}

		@Override
		public boolean equals(Object o) {
			if (this == o)
				return true;
			else if (!(o instanceof LiteralPossibility))
				return false;
			LiteralPossibility<C, S> other = (LiteralPossibility<C, S>) o;
			return getType().equals(other.getType()) && getStream().getPosition() == other.getStream().getPosition()
				&& theLength == other.theLength;
		}

		@Override
		public int hashCode() {
			return Objects.hash(theType, getStream().getPosition(), theLength);
		}

		@Override
		public Expression<S> getExpression() {
			return new LiteralExpression<>(theType, theParser.getStream(), theLength);
		}

		@Override
		public String toString() {
			if (theLength == getType().getLength())
				return getType().toString();
			else
				return getType().toString() + "[" + theLength + "]";
		}
	}

	private static class LiteralExpression<C, S extends BranchableStream<?, ? super C>> extends AbstractExpression<S> {
		private final int theLength;

		LiteralExpression(LiteralExpressionType<C, ? super S> type, S stream, int length) {
			super(stream, type);
			theLength = length;
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
			return theLength == getType().getLength() ? null : this;
		}

		@Override
		public int getErrorCount() {
			return theLength == getType().getLength() ? 0 : 1;
		}

		@Override
		public int getLocalErrorRelativePosition() {
			return theLength == getType().getLength() ? -1 : theLength;
		}

		@Override
		public String getLocalErrorMessage() {
			if (theLength == getType().getLength())
				return null;
			else
				return "\"" + getType() + "\" expected";
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
