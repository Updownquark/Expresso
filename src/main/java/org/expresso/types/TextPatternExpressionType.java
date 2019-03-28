package org.expresso.types;

import java.io.IOException;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.expresso.AbstractExpression;
import org.expresso.Expression;
import org.expresso.ExpressionPossibility;
import org.expresso.ExpressionType;
import org.expresso.ExpressoParser;
import org.expresso.stream.CharSequenceStream;

public class TextPatternExpressionType<S extends CharSequenceStream> extends AbstractExpressionType<S> {
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
		TextPatternPossibility<S2> possibility = new TextPatternPossibility<>(this, session);
		if (!session.tolerateErrors() && possibility.getErrorCount() > 0)
			return null;
		return possibility;
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
		public ExpressionType<? super S> getType() {
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
