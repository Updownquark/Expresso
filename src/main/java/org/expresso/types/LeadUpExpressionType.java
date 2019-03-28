package org.expresso.types;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Objects;
import java.util.stream.Collectors;

import org.expresso.BareContentExpressionType;
import org.expresso.Expression;
import org.expresso.ExpressionPossibility;
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
	public <S2 extends S> ExpressionPossibility<S2> parse(ExpressoParser<S2> parser) throws IOException {
		if (!parser.tolerateErrors()) {
			ExpressoParser<S2> branched = parser;
			while (branched != null) {
				ExpressionPossibility<S2> terminal = parser.parseWith(theTerminal);
				if (terminal != null && terminal.getErrorCount() == 0)
					return new LeadUpPossibility<>(this, parser, theTerminal, terminal);
				branched = branched.advance(1);
			}
			return null;
		} else {
			ExpressionPossibility<S2> terminal = parser.parseWith(theTerminal);
			return terminal == null ? null : new LeadUpPossibility<>(this, parser, theTerminal, terminal);
		}
	}

	@Override
	public String toString() {
		return "..." + theTerminal;
	}

	private static class LeadUpPossibility<S extends BranchableStream<?, ?>> implements ExpressionPossibility<S> {
		private final LeadUpExpressionType<? super S> theType;
		private final ExpressoParser<S> theParser;
		private final ExpressionType<? super S> theTerminal;
		private final ExpressionPossibility<S> theTerminalPossibility;

		LeadUpPossibility(LeadUpExpressionType<? super S> type, ExpressoParser<S> parser, ExpressionType<? super S> terminal,
			ExpressionPossibility<S> terminalPossibility) {
			theType = type;
			theParser = parser;
			theTerminal = terminal;
			theTerminalPossibility = terminalPossibility;
		}

		@Override
		public ExpressionType<? super S> getType() {
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
		public Collection<? extends ExpressionPossibility<S>> fork() throws IOException {
			ExpressoParser<S> advanced = theParser.advance(1);
			ExpressionPossibility<S> newTerminal = advanced == null ? null : advanced.parseWith(theTerminal);
			ExpressionPossibility<S> mappedNewTerminal = newTerminal == null ? null
				: new LeadUpPossibility<>(theType, advanced, theTerminal, newTerminal);
			Collection<? extends ExpressionPossibility<S>> terminalForks = theTerminalPossibility.fork();
			Collection<? extends ExpressionPossibility<S>> mappedTerminalForks;
			if (terminalForks.isEmpty())
				mappedTerminalForks = terminalForks;
			else
				mappedTerminalForks = terminalForks.stream().map(fork -> new LeadUpPossibility<>(theType, theParser, theTerminal, fork))
					.collect(Collectors.toCollection(() -> new ArrayList<>(terminalForks.size())));
			if (newTerminal == null)
				return mappedTerminalForks;
			else if (mappedTerminalForks.isEmpty())
				return Arrays.asList(mappedNewTerminal);
			else
				return new CompositeCollection<ExpressionPossibility<S>>().addComponent(Arrays.asList(mappedNewTerminal))
					.addComponent(mappedTerminalForks);
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
		public boolean equals(Object o) {
			if (this == o)
				return true;
			else if (!(o instanceof LeadUpPossibility))
				return false;
			LeadUpPossibility<S> other = (LeadUpPossibility<S>) o;
			return getType().equals(other.getType()) && theTerminalPossibility.equals(other.theTerminalPossibility);
		}

		@Override
		public int hashCode() {
			return Objects.hash(theType, theTerminalPossibility);
		}

		@Override
		public Expression<S> getExpression() {
			return new LeadUpExpression<>(theType, theParser.getStream(), theTerminalPossibility.getExpression());
		}

		@Override
		public StringBuilder print(StringBuilder str, int indent, String metadata) {
			for (int i = 0; i < indent; i++)
				str.append('\t');
			str.append(theType).append(metadata).append('\n');
			for (int i = 0; i < indent + 1; i++)
				str.append('\t');
			theParser.getStream().printContent(0, theTerminalPossibility.getStream().getPosition() - theParser.getStream().getPosition(),
				str);
			theTerminalPossibility.print(str, indent + 1, "");
			return str;
		}

		@Override
		public String toString() {
			return print(new StringBuilder(), 0, "").toString();
		}
	}

	private static class LeadUpExpression<S extends BranchableStream<?, ?>> extends ComposedExpression<S> {
		public LeadUpExpression(LeadUpExpressionType<? super S> type, S stream, Expression<S> terminal) {
			super(stream, type, Arrays.asList(terminal));
		}

		@Override
		public Expression<S> unwrap() {
			return this;
		}
	}
}
