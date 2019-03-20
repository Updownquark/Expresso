package org.expresso2;

import java.io.IOException;
import java.util.Collections;
import java.util.List;
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
	public <S2 extends S> ExpressionPossibility<S2> parse(ExpressoParser<S2> session, boolean useCache) throws IOException {
		return new TextPatternPossibility<>(this, session);
	}

	@Override
	public String toString() {
		return thePattern.toString();
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
		public ExpressionPossibility<S> advance() throws IOException {
			return null;
		}

		@Override
		public ExpressionPossibility<S> leftFork() throws IOException {
			return null;
		}

		@Override
		public ExpressionPossibility<S> rightFork() throws IOException {
			return null;
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
		public boolean isComplete() {
			return true;
		}

		@Override
		public boolean isEquivalent(ExpressionPossibility<S> o) {
			if (this == o)
				return true;
			else if (!(o instanceof TextPatternPossibility))
				return false;
			return getType().equals(o.getType()) && getStream().getPosition() == o.getStream().getPosition();
		}

		@Override
		public Expression<S> getExpression() {
			return new TextPatternExpression<>(getStream(), theType, theMatcher);
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
