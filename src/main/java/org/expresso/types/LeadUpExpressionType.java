package org.expresso.types;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.stream.Collectors;

import org.expresso.BareContentExpressionType;
import org.expresso.Expression;
import org.expresso.ExpressionType;
import org.expresso.ExpressoParser;
import org.expresso.stream.BranchableStream;

public class LeadUpExpressionType<S extends BranchableStream<?, ?>> extends AbstractExpressionType<S>
	implements BareContentExpressionType<S> {
	private final ExpressionType<S> theTerminal;

	public LeadUpExpressionType(int id, ExpressionType<S> terminal) {
		super(id);
		theTerminal = terminal;
	}

	@Override
	public <S2 extends S> Expression<S2> parse(ExpressoParser<S2> parser) throws IOException {
		if (!parser.tolerateErrors()) {
			ExpressoParser<S2> branched = parser;
			while (branched != null) {
				Expression<S2> terminal = parser.parseWith(theTerminal);
				if (terminal != null && terminal.getErrorCount() == 0)
					return new LeadUpPossibility<>(this, parser, theTerminal, terminal);
				branched = branched.advance(1);
			}
			return null;
		} else {
			Expression<S2> terminal = parser.parseWith(theTerminal);
			return terminal == null ? null : new LeadUpPossibility<>(this, parser, theTerminal, terminal);
		}
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
		public Collection<? extends Expression<S>> fork() throws IOException {
			ExpressoParser<S> advanced = getParser().advance(1);
			Expression<S> newTerminal = advanced == null ? null : advanced.parseWith(theTerminal);
			Expression<S> mappedNewTerminal = newTerminal == null ? null
				: new LeadUpPossibility<>(getType(), advanced, theTerminal, newTerminal);
			Collection<? extends Expression<S>> terminalForks = theTerminalPossibility.fork();
			Collection<? extends Expression<S>> mappedTerminalForks;
			if (terminalForks.isEmpty())
				mappedTerminalForks = terminalForks;
			else
				mappedTerminalForks = terminalForks.stream().map(fork -> new LeadUpPossibility<>(getType(), getParser(), theTerminal, fork))
					.collect(Collectors.toCollection(() -> new ArrayList<>(terminalForks.size())));
			if (newTerminal == null)
				return mappedTerminalForks;
			else if (mappedTerminalForks.isEmpty())
				return Arrays.asList(mappedNewTerminal);
			else
				return new CompositeCollection<Expression<S>>().addComponent(Arrays.asList(mappedNewTerminal))
					.addComponent(mappedTerminalForks);
		}
	}
}
