package org.expresso2;

import java.io.IOException;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.expresso.parse.impl.CharSequenceStream;

public class TextPatternExpressionType<S extends CharSequenceStream> extends AbstractExpressionComponent<S> {
	private final Pattern thePattern;
	private final int theMaxLength;

	public TextPatternExpressionType(Integer id, int maxLength, Pattern pattern) {
		super(id);
		theMaxLength = maxLength;
		if (pattern.pattern().length() == 0)
			throw new IllegalArgumentException("Text pattern matchers cannot search for empty strings");
		thePattern = pattern;
	}

	public Pattern getPattern() {
		return thePattern;
	}

	public int getMaxLength() {
		return theMaxLength;
	}

	@Override
	public <S2 extends S> ExpressionPossibility<S2> parse(ExpressoParser<S2> session) throws IOException {
		return new TextPatternPossibility<>(this, session);
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
		return "Pattern(" + thePattern + ")";
	}

	private static class TextPatternPossibility<S extends CharSequenceStream> implements ExpressionPossibility<S> {
		private final TextPatternExpressionType<? super S> theType;
		private final ExpressoParser<S> theParser;
		private final Matcher theMatcher;

		private TextPatternPossibility(TextPatternExpressionType<? super S> type, ExpressoParser<S> parser) throws IOException {
			theType = type;
			theParser = parser;
			parser.getStream().discoverTo(theType.getMaxLength());
			theMatcher = theType.getPattern().matcher(parser.getStream());
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
			return theMatcher.lookingAt() ? theMatcher.end() : 0;
		}

		@Override
		public Collection<? extends ExpressionPossibility<S>> fork() throws IOException {
			return Collections.emptyList();
		}

		@Override
		public int getErrorCount() {
			return theMatcher.lookingAt() ? 0 : 1;
		}

		@Override
		public int getFirstErrorPosition() {
			return theMatcher.lookingAt() ? -1 : 0;
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
		public Expression<S> getExpression() {
			return new TextPatternExpression<>(getStream(), theType, theMatcher);
		}

		@Override
		public String toString() {
			String str = getType().toString();
			if (theMatcher.lookingAt())
				str = str + "[" + getStream().printContent(0, length(), null) + "]";
			return str;
		}
	}

	private static class TextPatternExpression<S extends CharSequenceStream> extends AbstractExpression<S> {
		private final Matcher theMatcher;

		public TextPatternExpression(S stream, TextPatternExpressionType<? super S> type, Matcher matcher) {
			super(stream, type);
			theMatcher = matcher;
		}

		@Override
		public TextPatternExpressionType<? super S> getType() {
			return (TextPatternExpressionType<? super S>) super.getType();
		}

		@Override
		public List<? extends Expression<S>> getChildren() {
			return Collections.emptyList();
		}

		@Override
		public Expression<S> getFirstError() {
			return theMatcher.lookingAt() ? null : this;
		}

		@Override
		public int getLocalErrorRelativePosition() {
			return theMatcher.lookingAt() ? -1 : 0;
		}

		@Override
		public String getLocalErrorMessage() {
			return theMatcher.lookingAt() ? null : "Text matching " + getType().getPattern() + " expected";
		}

		@Override
		public int getErrorCount() {
			return theMatcher.lookingAt() ? 0 : 1;
		}

		@Override
		public int length() {
			return theMatcher.lookingAt() ? theMatcher.end() : 0;
		}

		@Override
		public Expression<S> unwrap() {
			return this;
		}
	}
}
