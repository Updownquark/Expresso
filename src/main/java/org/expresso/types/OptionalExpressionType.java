package org.expresso.types;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

import org.expresso.Expression;
import org.expresso.ExpressionPossibility;
import org.expresso.ExpressionType;
import org.expresso.ExpressoParser;
import org.expresso.stream.BranchableStream;

public class OptionalExpressionType<S extends BranchableStream<?, ?>> extends SequenceExpressionType<S> {
	public OptionalExpressionType(int id, List<ExpressionType<S>> components) {
		super(id, components);
	}

	@Override
	public <S2 extends S> ExpressionPossibility<S2> parse(ExpressoParser<S2> session) throws IOException {
		ExpressionPossibility<S2> superPossibility = super.parse(session);
		boolean empty = superPossibility == null || superPossibility.getErrorCount() > 0;
		if (empty)
			superPossibility = ExpressionPossibility.empty(session.getStream(), this);
		return new OptionalPossibility<>(this, session, superPossibility, empty, true);
	}

	@Override
	public String toString() {
		return "Optional:" + super.toString();
	}

	private static class OptionalPossibility<S extends BranchableStream<?, ?>> implements ExpressionPossibility<S> {
		private final OptionalExpressionType<? super S> theType;
		private final ExpressoParser<S> theParser;
		private final ExpressionPossibility<S> theOption;
		private final boolean isEmpty;
		private final boolean isFirst;

		OptionalPossibility(OptionalExpressionType<? super S> type, ExpressoParser<S> parser, ExpressionPossibility<S> option,
			boolean empty, boolean first) {
			theType = type;
			theParser = parser;
			theOption = option;
			isEmpty = empty;
			isFirst = first;
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
			return theOption.length();
		}

		@Override
		public Collection<? extends ExpressionPossibility<S>> fork() throws IOException {
			Collection<? extends ExpressionPossibility<S>> optionForks = theOption.fork();
			Collection<? extends ExpressionPossibility<S>> mappedOptionForks;
			if (optionForks.isEmpty())
				mappedOptionForks = optionForks;
			else
				mappedOptionForks = optionForks.stream().map(fork -> new OptionalPossibility<>(theType, theParser, fork, false, false))
					.collect(Collectors.toCollection(() -> new ArrayList<>(optionForks.size())));
			List<ExpressionPossibility<S>> other;
			if (!isFirst)
				other = null;
			else if (isEmpty)
				other = Arrays.asList(new OptionalPossibility<>(theType, theParser,
					ExpressionPossibility.empty(theParser.getStream(), theOption.getType()), true, false));
			else
				other = null;
			if (other == null)
				return mappedOptionForks;
			else if (mappedOptionForks.isEmpty())
				return other;
			else
				return new CompositeCollection<ExpressionPossibility<S>>().addComponent(mappedOptionForks).addComponent(other);
		}

		@Override
		public int getErrorCount() {
			return theOption.getErrorCount();
		}

		@Override
		public int getFirstErrorPosition() {
			return theOption.getFirstErrorPosition();
		}

		@Override
		public boolean equals(Object o) {
			if (this == o)
				return true;
			else if (!(o instanceof OptionalPossibility))
				return false;
			OptionalPossibility<S> other = (OptionalPossibility<S>) o;
			return getType().equals(other.getType()) && theOption.equals(other.theOption);
		}

		@Override
		public int hashCode() {
			return Objects.hash(theType, theOption);
		}

		@Override
		public Expression<S> getExpression() {
			return new OptionalExpression<>(getStream(), theType, theOption.getExpression());
		}

		@Override
		public StringBuilder print(StringBuilder str, int indent, String metadata) {
			return theOption.print(str, indent, metadata + "(optional)");
		}

		@Override
		public String toString() {
			return print(new StringBuilder(), 0, "").toString();
		}
	}

	private static class OptionalExpression<S extends BranchableStream<?, ?>> extends ComposedExpression<S> {
		OptionalExpression(S stream, OptionalExpressionType<? super S> type, Expression<S> optional) {
			super(stream, type, optional == null ? Collections.emptyList() : Arrays.asList(optional));
		}

		@Override
		public Expression<S> unwrap() {
			if (getChildren().isEmpty())
				return null;
			else if (getChildren().size() == 1)
				return getChildren().get(0).unwrap();
			else
				return this;
		}
	}
}