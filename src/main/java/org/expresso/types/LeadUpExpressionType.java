package org.expresso.types;

import java.io.IOException;
import java.util.Arrays;
import java.util.Collections;

import org.expresso.BareContentExpressionType;
import org.expresso.Expression;
import org.expresso.ExpressionType;
import org.expresso.ExpressoParser;
import org.expresso.stream.BranchableStream;

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
	public int getEmptyQuality(int minQuality) {
		return theTerminal.getEmptyQuality(minQuality);
	}

	@Override
	public <S2 extends S> Expression<S2> parse(ExpressoParser<S2> parser) throws IOException {
		ExpressoParser<S2> branched = parser;
		while (branched != null) {
			Expression<S2> terminal = branched.parseWith(theTerminal);
			if (terminal != null)
				return new LeadUpPossibility<>(this, parser, theTerminal, terminal);
			branched = branched.advance(1);
		}
		return null;
	}

	@Override
	public Iterable<? extends ExpressionType<? super S>> getComponents() {
		return Collections.unmodifiableList(Arrays.asList(theTerminal));
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
			super(type, parser, Arrays.asList(terminalPossibility));
			theTerminal = terminal;
			theTerminalPossibility = terminalPossibility;
		}

		@Override
		public LeadUpExpressionType<? super S> getType() {
			return (LeadUpExpressionType<? super S>) super.getType();
		}

		@Override
		public Expression<S> nextMatch(ExpressoParser<S> parser) throws IOException {
			Expression<S> next = parser.nextMatch(theTerminalPossibility);
			if (next != null)
				return new LeadUpPossibility<>(getType(), parser, theTerminal, next);
			return null;
		}

		@Override
		public Expression<S> nextMatchLowPriority(ExpressoParser<S> parser) throws IOException {
			return null;
		}
	}
}
