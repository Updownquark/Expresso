package org.expresso3.types;

import java.io.IOException;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

import org.expresso.stream.BranchableStream;
import org.expresso3.BareContentExpressionType;
import org.expresso3.Expression;
import org.expresso3.ExpressoParser;

/**
 * Represents a piece of content that must be present in the stream exactly, without variation
 *
 * @param <C> The stream's chunk type
 * @param <S> The type of the stream
 */
public abstract class LiteralExpressionType<C, S extends BranchableStream<?, ? super C>> extends AbstractExpressionType<S>
	implements BareContentExpressionType<S> {
	private final C theValue;

	/**
	 * @param id The cache ID of the expression
	 * @param value The literal value to expect
	 */
	public LiteralExpressionType(int id, C value) {
		super(id);
		theValue = value;
	}

	/** @return This expression's literal value */
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
		if (possibility.getMatchQuality() >= parser.getQualityLevel())
			return possibility;
		return null;
	}

	@Override
	public int getSpecificity() {
		// Literals are very specific.
		// The presence of a literal is a very strong indicator that an expression possibility is on the right track.
		// The absence of an expected literal is a very strong indicator that an expression possibility was not intended.
		// the specificity of a literal is proportional to its length
		return getLength();
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
		public Expression<S> nextMatch() throws IOException {
			if (theLength == 1)
				return null;
			LiteralPossibility<C, S> p = new LiteralPossibility<>(theType, theParser, theLength - 1);
			if (p.getMatchQuality() >= theParser.getQualityLevel())
				return p;
			return null;
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
		public int getMatchQuality() {
			return (theType.getLength() - theLength);
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
