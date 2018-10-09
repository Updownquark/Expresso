package org.expresso2;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedList;
import java.util.List;

import org.expresso.parse.BranchableStream;

public class SequenceExpression<S extends BranchableStream<?, ?>> extends ExpressionComponent<S> {
	private final List<ExpressionComponent<S>> theComponents;

	public SequenceExpression(int id, List<ExpressionComponent<S>> components) {
		super(id);
		theComponents = Collections.unmodifiableList(components);
	}

	public List<ExpressionComponent<S>> getComponents() {
		return theComponents;
	}

	@Override
	public <S2 extends S> ExpressionPossibilitySequence<S2> tryParse(ExpressoParser<S2> session) {
		return new SequencePossibilities<>(this, theComponents, session);
	}

	private static class SequencePossibilities<S extends BranchableStream<?, ?>> implements ExpressionPossibilitySequence<S> {
		private final ExpressionComponent<? super S> theType;
		private final List<? extends ExpressionComponent<? super S>> theComponents;
		private final ExpressoParser<S> theSession;
		private final LinkedList<ParsePoint> theStack;
		private boolean isStarted;

		public SequencePossibilities(ExpressionComponent<? super S> type, List<? extends ExpressionComponent<? super S>> components,
			ExpressoParser<S> session) {
			theType = type;
			theComponents = components;
			theSession = session;
			theStack = new LinkedList<>();
		}

		@Override
		public ExpressionPossibility<S> getNextPossibility() throws IOException {
			ParsePoint lastPoint = null;
			if (!isStarted) {
				isStarted = true;
				ExpressionPossibilitySequence<S> seq = theSession.parseWith(theComponents.get(0));
				ExpressionPossibility<S> p = seq.getNextPossibility();
				if (p != null)
					theStack.add(new ParsePoint(theSession, seq, p));
				return currentPossibility();
			}
			while (true) {
				// Now try to fill out the next possibility
				while (theStack.size() < theComponents.size()) {
					ExpressoParser<S> session = lastPoint.session.advance(lastPoint.lastResult.length());
					ExpressionPossibilitySequence<S> seq = session.parseWith(theComponents.get(theStack.size()));
					ExpressionPossibility<S> ex = seq.getNextPossibility();
					if (ex != null) {
						lastPoint = new ParsePoint(session, seq, ex);
						theStack.push(lastPoint);
						return currentPossibility();
					} else
						break;
				}
				// First, advance past the previously found possibility
				while (!theStack.isEmpty()) {
					lastPoint = theStack.getLast();
					lastPoint.lastResult = lastPoint.sequence.getNextPossibility();
					if (lastPoint.lastResult != null) {
						break; // Found a new possibility at this branch in the tree
					} else {
						theStack.pop(); // No more possibilities on this branch; move back up toward the root
						lastPoint = null;
					}
				}
				if (theStack.isEmpty()) {
					// If the root (first) component is out of possibilities, then we're done
					return null;
				}
				// Now try to fill out the next possibility
				while (theStack.size() < theComponents.size()) {
					ExpressoParser<S> session = lastPoint.session.advance(lastPoint.lastResult.length());
					ExpressionPossibilitySequence<S> seq = session.parseWith(theComponents.get(theStack.size()));
					ExpressionPossibility<S> ex = seq.getNextPossibility();
					if (ex != null) {
						lastPoint = new ParsePoint(session, seq, ex);
						theStack.push(lastPoint);
					} else
						break;
				}
				if (theStack.size() == theComponents.size()) {
					List<Expression<S>> exCopy = new ArrayList<>(theStack.size());
					for (ParsePoint pp : theStack)
						exCopy.add(pp.lastResult);
					return new ComposedExpression<>(theSession.getStream(), theType, exCopy);
				}
			}
		}

		SequencePossibility<S> currentPossibility() {
			List<ExpressionPossibility<S>> stackCopy = new ArrayList<>(theStack.size());
			for (ParsePoint pp : theStack)
				stackCopy.add(pp.lastResult);
			return new SequencePossibility<>(theType, stackCopy);
		}

		private class ParsePoint {
			final ExpressoParser<S> session;
			final ExpressionPossibilitySequence<S> sequence;
			ExpressionPossibility<S> lastResult;

			ParsePoint(ExpressoParser<S> session, ExpressionPossibilitySequence<S> sequence, ExpressionPossibility<S> result) {
				this.session = session;
				this.sequence = sequence;
				lastResult = result;
			}
		}
	}

	private static class SequencePossibility<S extends BranchableStream<?, ?>> implements ExpressionPossibility<S> {}
}
