package org.expresso2;

import java.io.IOException;
import java.util.Iterator;
import java.util.List;

import org.expresso.parse.BranchableStream;

public class OneOfExpression<S extends BranchableStream<?, ?>> extends ExpressionComponent<S> {
	private final List<? extends ExpressionComponent<? super S>> theComponents;

	public OneOfExpression(int id, List<? extends ExpressionComponent<? super S>> components) {
		super(id);
		theComponents = components;
	}

	public List<? extends ExpressionComponent<? super S>> getComponents() {
		return theComponents;
	}

	@Override
	public <S2 extends S> ExpressionPossibility<S2> tryParse(ExpressoParser<S2> session) throws IOException {
		Iterator<? extends ExpressionComponent<? super S>> iter = theComponents.iterator();
		return new IteratedPossibilitySequence<>(iter, session);
	}

	private static class OneOfPossibility<S extends BranchableStream<?, ?>> implements ExpressionPossibility<S> {
		private final OneOfExpression<? super S> theType;
		private final ExpressoParser<S> theParser;
		private final ExpressionPossibility<S> theComponent;
		private final Iterator<? super ExpressionComponent<? super S>> theIterator;

		OneOfPossibility(OneOfExpression<? super S> type, ExpressoParser<S> parser, ExpressionPossibility<S> component,
			Iterator<? super ExpressionComponent<? super S>> iterator) {
			theType = type;
			theParser = parser;
			theComponent = component;
			theIterator = iterator;
		}

		@Override
		public S getStream() {
			return theParser.getStream();
		}

		@Override
		public int length() {
			return theComponent.length();
		}

		@Override
		public ExpressionPossibility<S> next() throws IOException {
			// TODO Auto-generated method stub
			return null;
		}

		@Override
		public int getErrorCount() {
			// TODO Auto-generated method stub
			return 0;
		}

		@Override
		public boolean isComplete() {
			// TODO Auto-generated method stub
			return false;
		}

		@Override
		public Expression<S> getExpression() {
			// TODO Auto-generated method stub
			return null;
		}
	}

	private static class IteratedPossibilitySequence<S extends BranchableStream<?, ?>> implements PossibilitySequence<Expression<S>> {
		private final Iterator<? extends ExpressionComponent<? super S>> theComponents;
		private final ExpressoParser<S> theSession;
		private PossibilitySequence<? extends Expression<S>> theCurrentSequence;

		IteratedPossibilitySequence(Iterator<? extends ExpressionComponent<? super S>> components, ExpressoParser<S> session) {
			theComponents = components;
			theSession = session;
			if (theComponents.hasNext())
				theCurrentSequence = theSession.parseWith(theComponents.next());
		}

		@Override
		public Expression<S> getNextPossibility() throws IOException {
			while (theCurrentSequence != null) {
				Expression<S> ex = theCurrentSequence.getNextPossibility();
				if (ex != null)
					return ex;
				else if (theComponents.hasNext())
					theCurrentSequence = theSession.parseWith(theComponents.next());
				else
					theCurrentSequence = null;
			}
			return null;
		}
	}
}
