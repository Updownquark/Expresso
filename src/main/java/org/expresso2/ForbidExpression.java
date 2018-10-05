package org.expresso2;

import java.io.IOException;
import java.util.Arrays;

import org.expresso.parse.BranchableStream;

public class ForbidExpression<S extends BranchableStream<?, ?>> extends ExpressionComponent<S> {
	private final ExpressionComponent<S> theForbidden;

	public ForbidExpression(ExpressionComponent<S> forbidden) {
		theForbidden = forbidden;
	}

	@Override
	public <S2 extends S> PossibilitySequence<? extends Expression<S2>> tryParse(ExpressoParser<S2> session) {
		PossibilitySequence<? extends Expression<S2>> forbidden = session.parseWith(theForbidden);
		return new ForbiddenPossibilities<>(this, session, forbidden);
	}

	private static class ForbiddenPossibilities<S extends BranchableStream<?, ?>> implements PossibilitySequence<Expression<S>> {
		private final ForbidExpression<? super S> theType;
		private final ExpressoParser<S> theSession;
		private final PossibilitySequence<? extends Expression<S>> theForbidden;

		public ForbiddenPossibilities(ForbidExpression<? super S> type, ExpressoParser<S> session,
			PossibilitySequence<? extends Expression<S>> forbidden) {
			theType = type;
			theSession = session;
			theForbidden = forbidden;
		}

		@Override
		public Expression<S> getNextPossibility() throws IOException {
			Expression<S> forbidden;
			do {
				forbidden = theForbidden.getNextPossibility();
			} while (forbidden != null && forbidden.getFirstError() != null);
			if (forbidden == null)
				return Expression.empty(theSession.getStream(), theType);
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
