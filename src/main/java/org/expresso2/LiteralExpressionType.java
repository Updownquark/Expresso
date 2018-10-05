package org.expresso2;

import java.io.IOException;
import java.util.Collections;
import java.util.List;

import org.expresso.parse.BranchableStream;

public abstract class LiteralExpressionType<C, S extends BranchableStream<?, ? super C>> extends ExpressionComponent<S> {
	private final C theValue;

	public LiteralExpressionType(C value) {
		theValue = value;
	}

	/** @return The length of this matcher's value */
	protected abstract int getLength();

	/**
	 * @param stream The stream to check
	 * @return Whether the given stream starts with this matcher's value
	 */
	protected abstract boolean startsWithValue(S stream);

	@Override
	public <S2 extends S> PossibilitySequence<? extends Expression<S2>> tryParse(ExpressoParser<S2> session) {
		return new LiteralPossibilitySequence<>(session);
	}

	private class LiteralPossibilitySequence<S2 extends S> implements PossibilitySequence<Expression<S2>> {
		private final ExpressoParser<S2> theSession;

		LiteralPossibilitySequence(ExpressoParser<S2> session) {
			theSession = session;
		}

		@Override
		public Expression<S2> getNextPossibility() throws IOException {
			int length = getLength();
			if (theSession.getStream().discoverTo(length) < length)
				return null;
			if (!startsWithValue(theSession.getStream()))
				return new ErrorExpression<>(theSession.getStream(), LiteralExpressionType.this, Collections.emptyList(),
					"Expected \"" + theValue + "\"");
			return new LiteralExpression<>(theSession.getStream(), LiteralExpressionType.this);
		}
	}

	private static class LiteralExpression<C, S extends BranchableStream<?, ? super C>> extends Expression<S> {
		public LiteralExpression(S stream, LiteralExpressionType<C, ? super S> type) {
			super(stream, type);
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
			return getType().getLength();
		}
	}
}
