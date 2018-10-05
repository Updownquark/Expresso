package org.expresso2;

import java.io.IOException;
import java.util.Collections;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.expresso.parse.impl.CharSequenceStream;

public class TextPatternExpressionType<S extends CharSequenceStream> extends ExpressionComponent<S> {
	private final Pattern thePattern;

	public TextPatternExpressionType(Pattern pattern) {
		if (pattern.pattern().length() == 0)
			throw new IllegalArgumentException("Text pattern matchers cannot search for empty strings");
		thePattern = pattern;
	}

	public Pattern getPattern() {
		return thePattern;
	}

	@Override
	public <S2 extends S> PossibilitySequence<? extends Expression<S2>> tryParse(ExpressoParser<S2> session) {
		return new PatternPossibilitySequence<>(this, session);
	}

	private static class PatternPossibilitySequence<S extends CharSequenceStream> implements PossibilitySequence<Expression<S>> {
		private final TextPatternExpressionType<? super S> theType;
		private final ExpressoParser<S> theSession;
		private boolean isDone;

		PatternPossibilitySequence(TextPatternExpressionType<? super S> type, ExpressoParser<S> session) {
			theType = type;
			theSession = session;
		}

		@Override
		public Expression<S> getNextPossibility() throws IOException {
			if (isDone)
				return null;
			isDone = true;
			Matcher matcher = theType.getPattern().matcher(theSession.getStream());
			if (matcher.lookingAt())
				return new TextPatternExpression<>(theSession.getStream(), theType, matcher.end());
			else
				return new ErrorExpression<>(theSession.getStream(), theType, Collections.emptyList(),
					"Text matching " + theType.getPattern() + " expected");
		}
	}

	private static class TextPatternExpression<S extends CharSequenceStream> extends Expression<S> {
		private final int theLength;

		public TextPatternExpression(S stream, TextPatternExpressionType<? super S> type, int length) {
			super(stream, type);
			theLength = length;
		}

		@Override
		public List<? extends Expression<S>> getChildren() {
			return Collections.emptyList();
		}

		@Override
		public ErrorExpression<S> getFirstError() {
			return null;
		}

		@Override
		public int getErrorCount() {
			return 0;
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
