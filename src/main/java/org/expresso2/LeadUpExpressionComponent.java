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
	public <S2 extends S> ExpressionPossibility<S2> parse(ExpressoParser<S2> parser) throws IOException {
		ExpressionPossibility<S2> terminal = parser.parseWith(theTerminal);
		return new LeadUpPossibility<>(this, parser, theTerminal, terminal);
	}

	private static class LeadUpPossibility<S extends BranchableStream<?, ?>> implements ExpressionPossibility<S> {
		private final LeadUpExpressionComponent<? super S> theType;
		private final ExpressoParser<S> theParser;
		private final ExpressionComponent<? super S> theTerminal;
		private final ExpressionPossibility<S> theTerminalPossibility;

		LeadUpPossibility(LeadUpExpressionComponent<? super S> type, ExpressoParser<S> parser, ExpressionComponent<? super S> terminal,
			ExpressionPossibility<S> terminalPossibility) {
			theType = type;
			theParser = parser;
			theTerminal = terminal;
			theTerminalPossibility = terminalPossibility;
		}

		@Override
		public S getStream() {
			return theParser.getStream();
		}

		@Override
		public int length() {
			return theTerminalPossibility.length()
				+ (theTerminalPossibility.getStream().getPosition() - theParser.getStream().getPosition());
		}

		@Override
		public ExpressionPossibility<S> advance() throws IOException {
			ExpressoParser<S> advanced = theParser.advance(1);
			if (advanced == null)
				return null;
			ExpressionPossibility<S> terminal = advanced.parseWith(theTerminal);
			return new LeadUpPossibility<>(theType, theParser, theTerminal, terminal);
		}

		@Override
		public ExpressionPossibility<S> leftFork() throws IOException {
			ExpressionPossibility<S> terminalPossibility = theTerminalPossibility.leftFork();
			if (terminalPossibility == null)
				return null;
			return new LeadUpPossibility<>(theType, theParser, theTerminal, terminalPossibility);
		}

		@Override
		public ExpressionPossibility<S> rightFork() throws IOException {
			ExpressionPossibility<S> terminalPossibility = theTerminalPossibility.rightFork();
			if (terminalPossibility == null)
				return null;
			return new LeadUpPossibility<>(theType, theParser, theTerminal, terminalPossibility);
		}

		@Override
		public int getErrorCount() {
			return theTerminalPossibility.getErrorCount();
		}

		@Override
		public boolean isComplete() {
			return theTerminalPossibility.isComplete();
		}

		@Override
		public Expression<S> getExpression() {
			return new LeadUpExpression<>(theType, theParser.getStream(), theTerminalPossibility.getExpression());
		}
	}

	private static class LeadUpExpression<S extends BranchableStream<?, ?>> extends ComposedExpression<S> {
		public LeadUpExpression(LeadUpExpressionComponent<? super S> type, S stream, Expression<S> terminal) {
			super(stream, type, Arrays.asList(terminal));
		}
	}
}
