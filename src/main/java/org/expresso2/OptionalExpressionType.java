package org.expresso2;

import java.io.IOException;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import org.expresso.parse.BranchableStream;

public class OptionalExpressionType<S extends BranchableStream<?, ?>> extends SequenceExpression<S> {
	public OptionalExpressionType(int id, List<ExpressionComponent<S>> components) {
		super(id, components);
	}

	@Override
	public <S2 extends S> ExpressionPossibility<S2> parse(ExpressoParser<S2> session) throws IOException {
		return new OptionalPossibility<>(this, session, super.parse(session), true);
	}

	private static class OptionalPossibility<S extends BranchableStream<?, ?>> implements ExpressionPossibility<S> {
		private final OptionalExpressionType<? super S> theType;
		private final ExpressoParser<S> theParser;
		private final ExpressionPossibility<S> theOption;
		private final boolean isEmpty;

		OptionalPossibility(OptionalExpressionType<? super S> type, ExpressoParser<S> parser, ExpressionPossibility<S> option,
			boolean empty) {
			theType = type;
			theParser = parser;
			theOption = option;
			isEmpty = empty;
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
		public ExpressionPossibility<S> advance() throws IOException {
			ExpressionPossibility<S> optAdvanced = theOption.advance();
			return optAdvanced == null ? null : new OptionalPossibility<>(theType, theParser, optAdvanced, false);
		}

		@Override
		public ExpressionPossibility<S> leftFork() throws IOException {
			if (isEmpty)
				return null;
			ExpressionPossibility<S> optionFork = theOption.leftFork();
			if (optionFork != null)
				return new OptionalPossibility<>(theType, theParser, optionFork, false);
			else
				return new OptionalPossibility<>(theType, theParser,
					ExpressionPossibility.empty(theParser.getStream(), theType), true);
		}

		@Override
		public ExpressionPossibility<S> rightFork() throws IOException {
			ExpressionPossibility<S> optionFork = theOption.rightFork();
			return optionFork == null ? null : new OptionalPossibility<>(theType, theParser, optionFork, false);
		}

		@Override
		public int getErrorCount() {
			return theOption.getErrorCount();
		}

		@Override
		public boolean isComplete() {
			return theOption.isComplete();
		}

		@Override
		public Expression<S> getExpression() {
			return new OptionalExpression<>(getStream(), theType, theOption.getExpression());
		}
	}

	private static class OptionalExpression<S extends BranchableStream<?, ?>> extends ComposedExpression<S> {
		OptionalExpression(S stream, OptionalExpressionType<? super S> type, Expression<S> optional) {
			super(stream, type, optional == null ? Collections.emptyList() : Arrays.asList(optional));
		}
	}
}
