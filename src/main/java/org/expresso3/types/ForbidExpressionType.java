package org.expresso3.types;

import java.io.IOException;
import java.util.Arrays;
import java.util.Collections;

import org.expresso.stream.BranchableStream;
import org.expresso3.Expression;
import org.expresso3.ExpressionType;
import org.expresso3.ExpressoParser;

/**
 * Represents content that should <B>NOT</b> be present in the stream at a certain place
 *
 * @param <S> The type of the stream
 */
public class ForbidExpressionType<S extends BranchableStream<?, ?>> extends AbstractExpressionType<S> {
	private final ExpressionType<S> theForbidden;

	/**
	 * @param id The cache ID for the expression type
	 * @param forbidden The expression that should not be present in the stream
	 */
	public ForbidExpressionType(int id, ExpressionType<S> forbidden) {
		super(id);
		theForbidden = forbidden;
	}

	@Override
	public <S2 extends S> Expression<S2> parse(ExpressoParser<S2> parser) throws IOException {
		Expression<S2> forbidden = parser.parseWith(theForbidden);
		if (forbidden == null)
			return Expression.empty(parser.getStream(), this);
		ForbiddenPossibility<S2> p = new ForbiddenPossibility<>(this, parser, forbidden);
		if (p.getMatchQuality() >= parser.getQualityLevel())
			return p;
		return null;
	}

	@Override
	public int getSpecificity() {
		return 0;
	}

	@Override
	public String toString() {
		return "Forbidden:" + theForbidden;
	}

	private static class ForbiddenPossibility<S extends BranchableStream<?, ?>> extends ComposedExpression<S> {
		private final Expression<S> theForbidden;

		ForbiddenPossibility(ForbidExpressionType<? super S> type, ExpressoParser<S> parser, Expression<S> forbidden) {
			super(type, parser, Collections.unmodifiableList(Arrays.asList(forbidden)));
			theForbidden = forbidden;
		}

		@Override
		public ForbidExpressionType<? super S> getType() {
			return (ForbidExpressionType<? super S>) super.getType();
		}

		@Override
		public Expression<S> nextMatch() throws IOException {
			Expression<S> fMatch = theForbidden.nextMatch();
			if (fMatch == null)
				return null;
			ForbiddenPossibility<S> next = new ForbiddenPossibility<>(getType(), getParser(), fMatch);
			if (next.getMatchQuality() >= getParser().getQualityLevel())
				return next;
			return null;
		}

		@Override
		protected int getSelfComplexity() {
			return 1;
		}

		@Override
		protected CompositionError getSelfError() {
			if (theForbidden.getErrorCount() == 0)
				return new CompositionError(0, theForbidden.getType() + " not allowed here", -theForbidden.getMatchQuality());
			else
				return null;
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
		public Expression<S> getFirstError() {
			Expression<S> err = theForbidden.getFirstError();
			if (err == null && theForbidden.length() > 0)
				err = this;
			return null;
		}
	}
}
