package org.expresso3.types;

import java.io.IOException;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.expresso.stream.CharSequenceStream;
import org.expresso3.BareContentExpressionType;
import org.expresso3.Expression;
import org.expresso3.ExpressoParser;

/**
 * An expression type that matches text content via a regular expression ({@link Pattern})
 *
 * @param <S> The sub-type of text stream
 */
public class TextPatternExpressionType<S extends CharSequenceStream> extends AbstractExpressionType<S>
	implements BareContentExpressionType<S> {
	private final Pattern thePattern;
	private final int theMaxLength;

	/**
	 * @param id The cache ID of the expression
	 * @param maxLength The amount of text to pre-discover for this expression
	 * @param pattern The pattern to match
	 */
	public TextPatternExpressionType(int id, int maxLength, Pattern pattern) {
		super(id);
		theMaxLength = maxLength;
		if (pattern.pattern().length() == 0)
			throw new IllegalArgumentException("Text pattern matchers cannot search for empty strings");
		thePattern = pattern;
	}

	@Override
	public boolean isCacheable() {
		return true;
	}

	/** @return This expression's pattern matcher */
	public Pattern getPattern() {
		return thePattern;
	}

	/** @return The amount of text to pre-discover for this expression */
	public int getMaxLength() {
		return theMaxLength;
	}

	@Override
	public int getSpecificity() {
		// Patterns have some specificity, but they may also match content that is not intended.
		// Patterns may match content of different length, so it's not possible to account for that here.
		// TODO Individual patterns may have different specificity.
		// Is there some way to figure out how specific a particular pattern is?
		return 10;
	}

	@Override
	public <S2 extends S> Expression<S2> parse(ExpressoParser<S2> parser) throws IOException {
		TextPatternPossibility<S2> possibility = new TextPatternPossibility<>(this, parser);
		if (possibility.getMatchQuality() >= parser.getQualityLevel())
			return possibility;
		return null;
	}

	@Override
	public int hashCode() {
		return thePattern.hashCode();
	}

	@Override
	public boolean equals(Object obj) {
		if (this == obj)
			return true;
		else if (!(obj instanceof TextPatternExpressionType))
			return false;
		return thePattern.equals(((TextPatternExpressionType<?>) obj).thePattern);
	}

	@Override
	public String toString() {
		return "P:" + thePattern;
	}

	private static class TextPatternPossibility<S extends CharSequenceStream> implements Expression<S> {
		private final TextPatternExpressionType<? super S> theType;
		private final ExpressoParser<S> theParser;
		private final Matcher theMatcher;

		private TextPatternPossibility(TextPatternExpressionType<? super S> type, ExpressoParser<S> parser) throws IOException {
			theType = type;
			theParser = parser;
			parser.getStream().discoverTo(theType.getMaxLength());
			theMatcher = theType.getPattern().matcher(parser.getStream());
		}

		TextPatternPossibility(TextPatternExpressionType<? super S> type, ExpressoParser<S> parser, Matcher matcher) throws IOException {
			theType = type;
			theParser = parser;
			theMatcher = matcher;
		}

		@Override
		public TextPatternExpressionType<? super S> getType() {
			return theType;
		}

		@Override
		public S getStream() {
			return theParser.getStream();
		}

		@Override
		public int length() {
			return theMatcher.lookingAt() ? theMatcher.end() : 0;
		}

		@Override
		public List<? extends Expression<S>> getChildren() {
			return Collections.emptyList();
		}

		@Override
		public Expression<S> nextMatch() throws IOException {
			int length = length();
			if (length < 1)
				return null;
			CharSequence subSequence = theParser.getStream().subSequence(0, length - 1);
			Matcher matcher = theType.getPattern().matcher(subSequence);
			TextPatternPossibility<S> possibility = new TextPatternPossibility<>(theType, theParser, matcher);
			if (possibility.getMatchQuality() >= theParser.getQualityLevel())
				return possibility;
			return null;
		}

		@Override
		public int getErrorCount() {
			return theMatcher.lookingAt() ? 0 : 1;
		}

		@Override
		public Expression<S> getFirstError() {
			return theMatcher.lookingAt() ? null : this;
		}

		@Override
		public int getLocalErrorRelativePosition() {
			return theMatcher.lookingAt() ? -5 : 0;
		}

		@Override
		public String getLocalErrorMessage() {
			return theMatcher.lookingAt() ? null : "Text matching " + getType().getPattern() + " expected";
		}

		@Override
		public int getComplexity() {
			return 1;
		}

		@Override
		public int getMatchQuality() {
			return theMatcher.lookingAt() ? 0 : -2;
		}

		@Override
		public Expression<S> unwrap() {
			return this;
		}

		@Override
		public boolean equals(Object o) {
			if (this == o)
				return true;
			else if (!(o instanceof TextPatternPossibility))
				return false;
			TextPatternPossibility<S> other = (TextPatternPossibility<S>) o;
			return getType().equals(other.getType()) && getStream().getPosition() == other.getStream().getPosition();
		}

		@Override
		public int hashCode() {
			return Objects.hash(theType, getStream().getPosition());
		}

		@Override
		public StringBuilder print(StringBuilder str, int indent, String metadata) {
			for (int i = 0; i < indent; i++)
				str.append('\t');
			str.append(theType).append(metadata);
			if (theMatcher.lookingAt()) {
				str.append(": ");
				theParser.getStream().printContent(0, theMatcher.end(), str);
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
