package org.expresso2.impl;

import java.io.IOException;

import org.expresso.parse.BranchableStream;
import org.expresso2.Expression;
import org.expresso2.ExpressionPossibility;
import org.expresso2.ExpressionPossibilitySequence;

class CachedParseElement<S extends BranchableStream<?, ?>> {
	private ExpressionPossibilitySequence<S> theSequence;
	private LinkedElement<S> theFirstResult;
	private LinkedElement<S> theLastResult;

	CachedParseElement() {
	}

	void setSequence(ExpressionPossibilitySequence<S> sequence) {
		theSequence = sequence;
	}

	ExpressionPossibilitySequence<S> asNewSequence() {
		if (theSequence == null)
			return ExpressionPossibilitySequence.empty(); // Recursion
		return new ExpressionPossibilitySequence<S>() {
			private LinkedElement<S> theLink = theFirstResult;

			@Override
			public ExpressionPossibility<S> getNextPossibility() throws IOException {
				if (theLink != null) {
					ExpressionPossibility<S> p = theLink.possibility;
					theLink = theLink.next;
					return p;
				} else {
					ExpressionPossibility<S> p = theSequence.getNextPossibility();
					if (p != null) {
						LinkedElement<S> newLink = new LinkedElement<>(p);
						theLastResult.next = newLink;
						theLastResult = newLink;
					}
					return p;
				}
			}
		};
	}

	private static class LinkedElement<S extends BranchableStream<?, ?>> {
		final ExpressionPossibility<S> possibility;
		LinkedElement<S> next;

		LinkedElement(ExpressionPossibility<S> possibility) {
			this.possibility = possibility;
		}
	}

	private static class PreComputedPossibility<S extends BranchableStream<?, ?>> implements ExpressionPossibility<S> {
		private final Expression<S> theExpression;

		PreComputedPossibility(Expression<S> expression) {
			theExpression = expression;
		}

		@Override
		public int length() {
			return theExpression.length();
		}

		@Override
		public int advanceInStream() throws IOException {
			return 0;
		}

		@Override
		public int getErrorCount() {
			return theExpression.getErrorCount();
		}

		@Override
		public boolean isComplete() {
			return theExpression.isComplete();
		}

		@Override
		public Expression<S> getExpression() {
			return theExpression;
		}
	}
}
