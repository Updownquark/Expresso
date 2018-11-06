package org.expresso2;

import java.io.IOException;
import java.util.Arrays;

import org.expresso.parse.BranchableStream;

public class LeadUpExpressionComponent<S extends BranchableStream<?, ?>> extends ExpressionComponent<S> {
	private final ExpressionComponent<S> theTerminal;

	public LeadUpExpressionComponent(int id, ExpressionComponent<S> terminal) {
		super(id);
		theTerminal = terminal;
	}

	@Override
	public <S2 extends S> ExpressionPossibility<S2> tryParse(ExpressoParser<S2> parser) throws IOException {
		ExpressionPossibility<S2> terminal = parser.parseWith(theTerminal);
		return new LeadUpPossibility<>(this, parser, terminal);
	}

	private static class LeadUpPossibility<S extends BranchableStream<?, ?>> implements ExpressionPossibility<S> {
		private final LeadUpExpressionComponent<? super S> theType;
		private final ExpressoParser<S> theParser;
		private final ExpressionPossibility<S> theTerminal;

		LeadUpPossibility(LeadUpExpressionComponent<? super S> type, ExpressoParser<S> parser, ExpressionPossibility<S> terminal) {
			theType = type;
			theParser = parser;
			theTerminal = terminal;
		}

		@Override
		public S getStream() {
			return theParser.getStream();
		}

		@Override
		public int length() {
			return theTerminal.length() + (theTerminal.getStream().getPosition() - theParser.getStream().getPosition());
		}

		@Override
		public ExpressionPossibility<S> next() throws IOException {
			// TODO Auto-generated method stub
			return null;
		}

		@Override
		public int getErrorCount() {
			return theTerminal.getErrorCount();
		}

		@Override
		public boolean isComplete() {
			return theTerminal.isComplete();
		}

		@Override
		public Expression<S> getExpression() {
			return new LeadUpExpression<>(theType, theParser.getStream(), theTerminal.getExpression());
		}
	}

	private static class LeadUpExpression<S extends BranchableStream<?, ?>> extends ComposedExpression<S> {
		public LeadUpExpression(LeadUpExpressionComponent<? super S> type, S stream, Expression<S> terminal) {
			super(stream, type, Arrays.asList(terminal));
		}
	}
}
