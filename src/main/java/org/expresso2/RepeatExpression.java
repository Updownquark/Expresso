package org.expresso2;

import java.io.IOException;
import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;

import org.expresso.parse.BranchableStream;

public class RepeatExpression<S extends BranchableStream<?, ?>> extends ExpressionComponent<S> {
	private final int theMinCount;
	private final int theMaxCount;
	private final ExpressionComponent<S> theComponent;

	public RepeatExpression(int id, int min, int max, ExpressionComponent<S> component) {
		super(id);
		theMinCount = min;
		theMaxCount = max;
		theComponent = component;
	}

	@Override
	public <S2 extends S> PossibilitySequence<? extends Expression<S2>> tryParse(ExpressoParser<S2> session) {
		return new RepeatingPossibilitySequence<>(this, theComponent, session);
	}

	private static class RepeatingPossibilitySequence<S extends BranchableStream<?, ?>> implements PossibilitySequence<Expression<S>> {
		private final ExpressionComponent<? super S> theType;
		private final ExpressionComponent<? super S> theComponent;
		private final ExpressoParser<S> theSession;
		private final LinkedList<ParsePoint> theStack;
		private boolean isStarted;

		public RepeatingPossibilitySequence(ExpressionComponent<? super S> type, ExpressionComponent<? super S> component,
			ExpressoParser<S> session) {
			theType = type;
			theComponent = component;
			theSession = session;
			theStack = new LinkedList<>();
		}

		@Override
		public Expression<S> getNextPossibility() throws IOException {
			ParsePoint lastPoint = null;
			if (!isStarted) {
				isStarted = true;
				PossibilitySequence<? extends Expression<S>> seq = theSession.parseWith(theComponent);
				Expression<S> ex = seq.getNextPossibility();
				if (ex == null) {
					return null;
				} else {
					lastPoint = new ParsePoint(theSession, seq, ex);
					theStack.push(lastPoint);
				}
			} else if (theStack.isEmpty())
				return null;
			else
				lastPoint = theStack.getLast();

			ExpressoParser<S> session = lastPoint == null ? theSession : lastPoint.session.advance(lastPoint.lastResult.length());
			PossibilitySequence<? extends Expression<S>> seq = session.parseWith(theComponent);
			Expression<S> ex = seq.getNextPossibility();
			if (ex != null) {
				lastPoint = new ParsePoint(session, seq, ex);
				theStack.push(lastPoint);
				return createExpression();
			}

			// No more repeating possibilities along this branch. Try another.
			while (!theStack.isEmpty()) {
				lastPoint = theStack.getLast();
				lastPoint.lastResult = lastPoint.sequence.getNextPossibility();
				if (lastPoint.lastResult != null) {
					return createExpression(); // Found a new possibility at this branch in the tree
				} else {
					theStack.pop(); // No more possibilities on this branch; move back up toward the root
				}
			}
			return null;
		}

		private Expression<S> createExpression() {
			List<Expression<S>> exCopy = new ArrayList<>(theStack.size());
			for (ParsePoint pp : theStack)
				exCopy.add(pp.lastResult);
			return new ComposedExpression<>(theSession.getStream(), theType, exCopy);
		}

		private class ParsePoint {
			final ExpressoParser<S> session;
			final PossibilitySequence<? extends Expression<S>> sequence;
			Expression<S> lastResult;

			ParsePoint(ExpressoParser<S> session, PossibilitySequence<? extends Expression<S>> sequence, Expression<S> result) {
				this.session = session;
				this.sequence = sequence;
				lastResult = result;
			}
		}
	}
}
