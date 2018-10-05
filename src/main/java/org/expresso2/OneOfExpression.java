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
	public <S2 extends S> PossibilitySequence<? extends Expression<S2>> tryParse(ExpressoParser<S2> session) {
		Iterator<? extends ExpressionComponent<? super S>> iter = theComponents.iterator();
		return new IteratedPossibilitySequence<>(iter, session);
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
