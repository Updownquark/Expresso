package org.expresso3.types;

import java.io.IOException;
import java.util.Arrays;
import java.util.Collections;

import org.expresso.stream.BranchableStream;
import org.expresso3.BareContentExpressionType;
import org.expresso3.Expression;
import org.expresso3.ExpressionType;
import org.expresso3.ExpressoParser;

/**
 * An expression that contains any content up to (and including) a given expression
 *
 * @param <S> The type of the stream
 */
public class LeadUpExpressionType<S extends BranchableStream<?, ?>> extends AbstractExpressionType<S>
	implements BareContentExpressionType<S> {
	private final ExpressionType<S> theTerminal;

	/**
	 * @param id The cache ID of the expression
	 * @param terminal The terminal expression type
	 */
	public LeadUpExpressionType(int id, ExpressionType<S> terminal) {
		super(id);
		theTerminal = terminal;
	}

	@Override
	public <S2 extends S> Expression<S2> parse(ExpressoParser<S2> parser) throws IOException {
		ExpressoParser<S2> branched = parser;
		while (branched != null) {
			Expression<S2> terminal = parser.parseWith(theTerminal);
			if (terminal != null)
				return new LeadUpPossibility<>(this, parser, theTerminal, terminal);
			branched = branched.advance(1);
		}
		return null;
	}

	@Override
	public int getSpecificity() {
		return theTerminal.getSpecificity();
	}

	@Override
	public String toString() {
		return "..." + theTerminal;
	}

	private static class LeadUpPossibility<S extends BranchableStream<?, ?>> extends ComposedExpression<S> {
		private final ExpressionType<? super S> theTerminal;
		private final Expression<S> theTerminalPossibility;

		LeadUpPossibility(LeadUpExpressionType<? super S> type, ExpressoParser<S> parser, ExpressionType<? super S> terminal,
			Expression<S> terminalPossibility) {
			super(type, parser, Collections.unmodifiableList(Arrays.asList(terminalPossibility)));
			theTerminal = terminal;
			theTerminalPossibility = terminalPossibility;
		}

		@Override
		public LeadUpExpressionType<? super S> getType() {
			return (LeadUpExpressionType<? super S>) super.getType();
		}

		@Override
		protected int getSelfComplexity() {
			return 1;
		}

		@Override
		public Expression<S> nextMatch() throws IOException {
			Expression<S> next = theTerminalPossibility.nextMatch();
			if (next != null)
				return new LeadUpPossibility<>(getType(), getParser(), theTerminal, next);
			return null;
		}
	}
}