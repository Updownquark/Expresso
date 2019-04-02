package org.expresso.types;

import java.io.IOException;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

import org.expresso.BareContentExpressionType;
import org.expresso.Expression;
import org.expresso.ExpressoParser;
import org.expresso.stream.BranchableStream;

public abstract class LiteralExpressionType<C, S extends BranchableStream<?, ? super C>> extends AbstractExpressionType<S>
	implements BareContentExpressionType<S> {
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
	 * @throws IOException If an exception occurs reading the stream
	 */
	protected abstract int getMatchUntil(S stream) throws IOException;

	@Override
	public abstract int hashCode();

	@Override
	public abstract boolean equals(Object o);

	@Override
	public <S2 extends S> Expression<S2> parse(ExpressoParser<S2> parser) throws IOException {
		LiteralPossibility<C, S2> possibility = new LiteralPossibility<>(this, parser);
		if (!parser.tolerateErrors() && possibility.getErrorCount() > 0)
			return null;
		return possibility;
	}

	@Override
	public String toString() {
		return "L:" + theValue;
	}

	private static class LiteralPossibility<C, S extends BranchableStream<?, ? super C>> implements Expression<S> {
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
		public Collection<? extends Expression<S>> fork() throws IOException {
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
		public Expression<S> getFirstError() {
			return theLength == theType.getLength() ? null : this;
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
		public int getComplexity() {
			return 1;
		}

		@Override
		public List<? extends Expression<S>> getChildren() {
			return Collections.emptyList();
		}

		@Override
		public Expression<S> unwrap() {
			return this;
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
		public StringBuilder print(StringBuilder str, int indent, String metadata) {
			for (int i = 0; i < indent; i++)
				str.append('\t');
			str.append(theType).append(metadata);
			if (theLength > 0) {
				str.append(": ");
				theParser.getStream().printContent(0, theLength, str);
			} else
				str.append("(no match)");
			return str;
		}

		@Override
		public String toString() {
			return print(new StringBuilder(), 0, "").toString();
		}
	}
}
