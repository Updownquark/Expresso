package org.expresso2;

import java.io.IOException;
import java.util.Arrays;
import java.util.Collections;

import org.expresso.parse.BranchableStream;

public class OptionalExpressionType<S extends BranchableStream<?, ?>> extends ExpressionComponent<S> {
	private final ExpressionComponent<? super S> theComponent;

	public OptionalExpressionType(ExpressionComponent<? super S> component) {
		theComponent = component;
	}

	@Override
	public <S2 extends S> PossibilitySequence<? extends Expression<S2>> tryParse(ExpressoParser<S2> session) {
		return new OptionalPossibilitySequence<>(this, session, session.parseWith(theComponent));
	}

	private static class OptionalPossibilitySequence<S extends BranchableStream<?, ?>> implements PossibilitySequence<Expression<S>> {
		private final OptionalExpressionType<? super S> theType;
		private final ExpressoParser<S> theSession;
		private final PossibilitySequence<? extends Expression<S>> theOptionalPossibilities;
		private boolean isDone;

		OptionalPossibilitySequence(OptionalExpressionType<? super S> type, ExpressoParser<S> session,
			PossibilitySequence<? extends Expression<S>> optionalPossibilities) {
			theType = type;
			theSession = session;
			theOptionalPossibilities = optionalPossibilities;
		}

		@Override
		public Expression<S> getNextPossibility() throws IOException {
			if(isDone)
				return null;
			Expression<S> option=theOptionalPossibilities.getNextPossibility();
			if(option!=null)
				return new OptionalExpression<>(theSession.getStream(), theType, option);
			else{
				isDone=true;
				return Expression.empty(theSession.getStream(), theType);
			}
		}
	}
	
	private static class OptionalExpression<S extends BranchableStream<?, ?>> extends ComposedExpression<S> {
		OptionalExpression(S stream, OptionalExpressionType<? super S> type, Expression<S> optional) {
			super(stream, type, optional == null ? Collections.emptyList() : Arrays.asList(optional));
		}
	}
}
