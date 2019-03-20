package org.expresso2;

import java.io.IOException;
import java.util.Arrays;

import org.expresso.parse.BranchableStream;

public class ForbidExpressionComponent<S extends BranchableStream<?, ?>> extends AbstractExpressionComponent<S> {
	private final ExpressionComponent<S> theForbidden;

	public ForbidExpressionComponent(int id, ExpressionComponent<S> forbidden) {
		super(id);
		theForbidden = forbidden;
	}

	@Override
	public <S2 extends S> ExpressionPossibility<S2> parse(ExpressoParser<S2> parser, boolean useCache) throws IOException {
		ExpressionPossibility<S2> forbidden = parser.parseWith(theForbidden, true);
		return forbidden == null ? null : new ForbiddenPossibility<>(this, parser, forbidden);
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
		public ExpressionComponent<? super S> getType() {
			return theType;
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
		public ExpressionPossibility<S> advance() throws IOException {
			ExpressionPossibility<S> forbidden = theForbidden.advance();
			return forbidden == null ? null : new ForbiddenPossibility<>(theType, theParser, forbidden);
		}

		@Override
		public ExpressionPossibility<S> leftFork() throws IOException {
			ExpressionPossibility<S> forbidden = theForbidden.leftFork();
			return forbidden == null ? null : new ForbiddenPossibility<>(theType, theParser, forbidden);
		}

		@Override
		public ExpressionPossibility<S> rightFork() throws IOException {
			ExpressionPossibility<S> forbidden = theForbidden.rightFork();
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
		public int getFirstErrorPosition() {
			int errPos = theForbidden.getFirstErrorPosition();
			if (errPos < 0 && theForbidden.length() > 0)
				errPos = 0;
			return errPos;
		}

		@Override
		public boolean isComplete() {
			return theForbidden.isComplete();
		}

		@Override
		public boolean isEquivalent(ExpressionPossibility<S> o) {
			if (this == o)
				return true;
			else if (!(o instanceof ForbiddenPossibility))
				return false;
			ForbiddenPossibility<S> other = (ForbiddenPossibility<S>) o;
			return getType().equals(other.getType()) && theForbidden.isEquivalent(other.theForbidden);
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
		public String getLocalErrorMessage() {
			int ec = super.getErrorCount();
			if (ec != 0)
				return null;
			else if (length() == 0)
				return null;
			else
				return "Forbidden content present";
		}

		@Override
		public Expression<S> unwrap() {
			return this;
		}
	}
}
