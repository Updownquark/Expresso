package org.expresso2;

import java.io.IOException;
import java.util.Arrays;
import java.util.List;

import org.expresso.parse.BranchableStream;
import org.qommons.IntList;

public class ExcludeExpression<S extends BranchableStream<?, ?>> extends ExpressionComponent<S> {
	private final List<ExpressionClass<S>> theExcludedClasses;
	private final List<ExpressionType<S>> theExcludedTypes;
	private int[] theExcludedIds;
	private final ExpressionComponent<S> theComponent;

	public ExcludeExpression(List<ExpressionClass<S>> excludedClasses, List<ExpressionType<S>> excludedTypes,
		ExpressionComponent<S> component) {
		theExcludedClasses = excludedClasses;
		theExcludedTypes = excludedTypes;
		theComponent = component;
	}

	public List<ExpressionClass<S>> getExcludedClasses() {
		return theExcludedClasses;
	}

	public List<ExpressionType<S>> getExcludedTypes() {
		return theExcludedTypes;
	}

	public ExpressionComponent<S> getComponent() {
		return theComponent;
	}

	protected int[] getExcludedIds() {
		if (theExcludedIds == null) {
			IntList excluded = new IntList(true, true);
			for (ExpressionClass<S> clazz : theExcludedClasses)
				for (ExpressionType<S> type : clazz.getComponents())
					excluded.add(type.id);
			for (ExpressionType<S> type : theExcludedTypes)
				excluded.add(type.id);
			theExcludedIds = excluded.toArray();
		}
		return theExcludedIds;
	}

	@Override
	public <S2 extends S> PossibilitySequence<? extends Expression<S2>> tryParse(ExpressoParser<S2> session) {
		return new ExcludedPossibilitySequence<>(this, session.exclude(getExcludedIds()));
	}

	private static class ExcludedPossibilitySequence<S extends BranchableStream<?, ?>> implements PossibilitySequence<Expression<S>> {
		private final ExcludeExpression<? super S> theType;
		private final ExpressoParser<S> theSession;
		private final PossibilitySequence<? extends Expression<S>> thePossibilities;

		ExcludedPossibilitySequence(ExcludeExpression<? super S> type, ExpressoParser<S> session) {
			theType = type;
			theSession = session;
			thePossibilities = session.parseWith(type.getComponent());
		}

		@Override
		public Expression<S> getNextPossibility() throws IOException {
			Expression<S> ex = thePossibilities.getNextPossibility();
			return ex == null ? null : new ExcludedExpression<>(theSession.getStream(), theType, ex);
		}
	}

	private static class ExcludedExpression<S extends BranchableStream<?, ?>> extends Expression<S> {
		private final Expression<S> theWrapped;

		public ExcludedExpression(S stream, ExcludeExpression<? super S> type, Expression<S> wrapped) {
			super(stream, type);
			theWrapped = wrapped;
		}

		@Override
		public List<? extends Expression<S>> getChildren() {
			return Arrays.asList(theWrapped);
		}

		@Override
		public ErrorExpression<S> getFirstError() {
			return theWrapped.getFirstError();
		}

		@Override
		public int getErrorCount() {
			return theWrapped.getErrorCount();
		}

		@Override
		public boolean isComplete() {
			return theWrapped.isComplete();
		}

		@Override
		public int length() {
			return theWrapped.length();
		}
	}
}
