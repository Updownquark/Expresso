package org.expresso2;

import java.io.IOException;
import java.util.Arrays;

import org.expresso.parse.BranchableStream;

public class ForbidExpression<S extends BranchableStream<?, ?>> extends ExpressionComponent<S> {
	private final ExpressionComponent<S> theForbidden;

	public ForbidExpression(int id, ExpressionComponent<S> forbidden) {
		super(id);
		theForbidden = forbidden;
	}

	@Override
	public <S2 extends S> ExpressionPossibilitySequence<S2> tryParse(ExpressoParser<S2> session) {
		ExpressionPossibilitySequence<S2> forbidden = session.parseWith(theForbidden);
		return new ForbiddenPossibilities<>(this, session, forbidden);
	}

	private static class ForbiddenPossibilities<S extends BranchableStream<?, ?>> implements ExpressionPossibilitySequence<S> {
		private final ForbidExpression<? super S> theType;
		private final ExpressoParser<S> theSession;
		private final ExpressionPossibilitySequence<S> theForbidden;

		public ForbiddenPossibilities(ForbidExpression<? super S> type, ExpressoParser<S> session,
			ExpressionPossibilitySequence<S> forbidden) {
			theType = type;
			theSession = session;
			theForbidden = forbidden;
		}

		@Override
		public ExpressionPossibility<S> getNextPossibility() throws IOException {
			ExpressionPossibility<S> forbidden;
			do {
				forbidden = theForbidden.getNextPossibility();
			} while (forbidden != null && forbidden.getErrorCount() > 0);
			if (forbidden == null)
				return ExpressionPossibility.empty(theSession.getStream(), theType);
			else
				return new ForbiddenExpression<>(theSession.getStream(), theType, forbidden);
		}
	}

	private static class ForbiddenExpression<S extends BranchableStream<?, ?>> extends ErrorExpression<S> {
		public ForbiddenExpression(S stream, ForbidExpression<? super S> type, Expression<S> forbidden) {
			super(stream, type, Arrays.asList(forbidden), "Forbidden content present");
		}
	}
}
