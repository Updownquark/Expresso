package org.expresso2;

import java.io.IOException;
import java.util.Arrays;

import org.expresso.parse.BranchableStream;

public class ForbidExpressionComponent<S extends BranchableStream<?, ?>> extends ExpressionComponent<S> {
	private final ExpressionComponent<S> theForbidden;

	public ForbidExpressionComponent(int id, ExpressionComponent<S> forbidden) {
		super(id);
		theForbidden = forbidden;
	}

	@Override
	public <S2 extends S> ExpressionPossibility<S2> tryParse(ExpressoParser<S2> parser) throws IOException {
		ExpressionPossibility<S2> forbidden = parser.parseWith(theForbidden);
		return new ForbiddenPossibility<>(this, parser, forbidden);
	}

	private static class ForbiddenPossibility<S extends BranchableStream<?, ?>> implements ExpressionPossibility<S> {
		private final ForbidExpressionComponent<? super S> theType;
		private final ExpressoParser<S> theParser;
		private final ExpressionPossibility<S> theForbidden;

		ForbiddenPossibility(ForbidExpressionComponent<? super S> type, ExpressoParser<S> parser, ExpressionPossibility<S> forbidden) {
			theType = type;
			theParser = parser;
			theForbidden = forbidden;
		}

		@Override
		public S getStream() {
			return theParser.getStream();
		}

		@Override
		public int length() {
			return theForbidden.length();
		}

		@Override
		public ExpressionPossibility<S> next() throws IOException {
			ExpressionPossibility<S> forbidden = theForbidden.next();
			return forbidden == null ? null : new ForbiddenPossibility<>(theType, theParser, forbidden);
		}

		@Override
		public int getErrorCount() {
			int fec = theForbidden.getErrorCount();
			if (fec != 0)
				return fec;
			else if (theForbidden.length() == 0)
				return 0;
			else
				return 1;
		}

		@Override
		public boolean isComplete() {
			return theForbidden.isComplete();
		}

		@Override
		public Expression<S> getExpression() {
			return new ForbiddenExpression<>(theParser.getStream(), theType, theForbidden.getExpression());
		}
	}

	private static class ForbiddenExpression<S extends BranchableStream<?, ?>> extends ComposedExpression<S> {
		public ForbiddenExpression(S stream, ForbidExpressionComponent<? super S> type, Expression<S> forbidden) {
			super(stream, type, Arrays.asList(forbidden));
		}

		@Override
		public Expression<S> getFirstError() {
			int ec = super.getErrorCount();
			if (ec != 0)
				return super.getFirstError();
			else if (length() == 0)
				return null;
			else
				return this;
		}

		@Override
		public int getErrorCount() {
			int ec = super.getErrorCount();
			if (ec != 0)
				return ec;
			else if (length() == 0)
				return 0;
			else
				return 1;
		}

		@Override
		public String getErrorMessage() {
			int ec = super.getErrorCount();
			if (ec != 0)
				return null;
			else if (length() == 0)
				return null;
			else
				return "Forbidden content present";
		}
	}
}
