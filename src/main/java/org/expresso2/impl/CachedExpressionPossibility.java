package org.expresso2.impl;

import java.io.IOException;

import org.expresso.parse.BranchableStream;
import org.expresso2.Expression;
import org.expresso2.ExpressionPossibility;

class CachedExpressionPossibility<S extends BranchableStream<?, ?>> {
	private LinkedElement<S> theFirstFork;
	private LinkedElement<S> theLastFork;
	private ExpressionPossibility<S> theAdvanced;
	private boolean hasAdvanced;

	CachedExpressionPossibility() {
	}

	void setSequence(ExpressionPossibility<S> possibility) {
		if (possibility == null)
			theFirstFork = null;
		else
			theFirstFork = theLastFork = new LinkedElement<>(possibility);
	}

	ExpressionPossibility<S> asPossibility() {
		if (theFirstFork == null)
			return null;
		return new ExposedCachedPossibility(theFirstFork);
	}

	ExpressionPossibility<S> advance() throws IOException {
		if (!hasAdvanced)
			theAdvanced = theFirstFork.possibility.advance();
		return theAdvanced;
	}

	boolean hasNext(LinkedElement<S> possibility) {
		return possibility.next != null || theFirstFork.possibility.hasFork();
	}

	LinkedElement<S> getNext(LinkedElement<S> possibility) throws IOException {
		if (possibility.next != null)
			return possibility.next;
		else if (theFirstFork.possibility.hasFork()) {
			theLastFork.next = new LinkedElement<>(theFirstFork.possibility.fork());
			return theLastFork;
		} else
			return null;
	}

	private static class LinkedElement<S extends BranchableStream<?, ?>> {
		final ExpressionPossibility<S> possibility;
		LinkedElement<S> next;

		LinkedElement(ExpressionPossibility<S> possibility) {
			this.possibility = possibility;
		}
	}

	private class ExposedCachedPossibility implements ExpressionPossibility<S> {
		private LinkedElement<S> thePossibility;

		ExposedCachedPossibility(LinkedElement<S> possibility) {
			thePossibility = possibility;
		}

		@Override
		public S getStream() {
			return thePossibility.possibility.getStream();
		}

		@Override
		public int length() {
			return thePossibility.possibility.length();
		}

		@Override
		public ExpressionPossibility<S> advance() throws IOException {
			return thePossibility.possibility.advance();
		}

		@Override
		public boolean hasFork() {
			return hasNext(thePossibility);
		}

		@Override
		public ExpressionPossibility<S> fork() throws IOException {
			LinkedElement<S> next = getNext(thePossibility);
			if (next != null) {
				ExposedCachedPossibility fork = new ExposedCachedPossibility(next);
				return fork;
			} else
				return null;
		}

		@Override
		public int getErrorCount() {
			return thePossibility.possibility.getErrorCount();
		}

		@Override
		public boolean isComplete() {
			return thePossibility.possibility.isComplete();
		}

		@Override
		public Expression<S> getExpression() {
			return thePossibility.possibility.getExpression();
		}
	}
}
