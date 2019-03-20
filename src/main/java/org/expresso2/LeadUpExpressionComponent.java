package org.expresso2;

import java.io.IOException;
import java.util.Arrays;

import org.expresso.parse.BranchableStream;

public class LeadUpExpressionComponent<S extends BranchableStream<?, ?>> extends AbstractExpressionComponent<S> {
	private final ExpressionComponent<S> theTerminal;

	public LeadUpExpressionComponent(int id, ExpressionComponent<S> terminal) {
		super(id);
		theTerminal = terminal;
	}

	@Override
	public <S2 extends S> ExpressionPossibility<S2> parse(ExpressoParser<S2> parser, boolean useCache) throws IOException {
		ExpressionPossibility<S2> terminal = parser.parseWith(theTerminal, true);
		return terminal == null ? null : new LeadUpPossibility<>(this, parser, theTerminal, terminal);
	}

	@Override
	public String toString() {
		return "..." + theTerminal;
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
		public ExpressionComponent<? super S> getType() {
			return theType;
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
			ExpressionPossibility<S> terminal = advanced.parseWith(theTerminal, true);
			return terminal == null ? null : new LeadUpPossibility<>(theType, theParser, theTerminal, terminal);
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
		public int getFirstErrorPosition() {
			int errPos = theTerminalPossibility.getFirstErrorPosition();
			if (errPos >= 0)
				errPos += theTerminalPossibility.getStream().getPosition() - theParser.getStream().getPosition();
			return errPos;
		}

		@Override
		public boolean isComplete() {
			return theTerminalPossibility.isComplete();
		}

		@Override
		public boolean isEquivalent(ExpressionPossibility<S> o) {
			if (this == o)
				return true;
			else if (!(o instanceof LeadUpPossibility))
				return false;
			LeadUpPossibility<S> other = (LeadUpPossibility<S>) o;
			return getType().equals(other.getType()) && theTerminalPossibility.isEquivalent(other.theTerminalPossibility);
		}

		@Override
		public Expression<S> getExpression() {
			return new LeadUpExpression<>(theType, theParser.getStream(), theTerminalPossibility.getExpression());
		}

		@Override
		public String toString() {
			return getStream().printContent(0, theTerminalPossibility.getStream().getPosition(), null).append(theTerminalPossibility)
				.toString();
		}
	}

	private static class LeadUpExpression<S extends BranchableStream<?, ?>> extends ComposedExpression<S> {
		public LeadUpExpression(LeadUpExpressionComponent<? super S> type, S stream, Expression<S> terminal) {
			super(stream, type, Arrays.asList(terminal));
		}

		@Override
		public Expression<S> unwrap() {
			return this;
		}
	}
}
