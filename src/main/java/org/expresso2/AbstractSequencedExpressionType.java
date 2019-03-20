package org.expresso2;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;

import org.expresso.parse.BranchableStream;
import org.qommons.BiTuple;

public abstract class AbstractSequencedExpressionType<S extends BranchableStream<?, ?>> extends AbstractExpressionComponent<S> {
	private final Iterable<? extends ExpressionComponent<? super S>> theSequence;

	public AbstractSequencedExpressionType(int id, Iterable<? extends ExpressionComponent<? super S>> sequence) {
		super(id);
		theSequence = sequence;
	}

	protected Iterable<? extends ExpressionComponent<? super S>> getSequence() {
		return theSequence;
	}

	protected abstract int getInitComponentCount();

	protected abstract boolean isComplete(int componentCount);

	protected abstract String getErrorForComponentCount(int componentCount);

	@Override
	public <S2 extends S> ExpressionPossibility<S2> parse(ExpressoParser<S2> parser) throws IOException {
		int tries = getInitComponentCount();
		List<ExpressionPossibility<S2>> repetitions = new ArrayList<>(tries);
		ExpressoParser<S2> branched = parser;
		Iterator<? extends ExpressionComponent<? super S>> sequenceIter = theSequence.iterator();
		for (int i = 0; i < tries && sequenceIter.hasNext(); i++) {
			ExpressionComponent<? super S> component = sequenceIter.next();
			ExpressionPossibility<S2> repetition = branched.parseWith(component);
			if (repetition == null)
				return null;
			repetitions.add(repetition);
			if (!repetition.isComplete() || repetition.getErrorCount() > 0)
				break;
			branched = branched.advance(repetition.length());
		}
		return new SequencePossibility<>(this, parser, Collections.unmodifiableList(repetitions));
	}

	private enum DegreeOfFreedom {
		Repetition, TerminalFork
	}

	private static class SequencePossibility<S extends BranchableStream<?, ?>> implements ExpressionPossibility<S> {
		private final AbstractSequencedExpressionType<? super S> theType;
		private final ExpressoParser<S> theParser;
		private final List<ExpressionPossibility<S>> theRepetitions;
		private final boolean allowLeftFork;
		private final boolean allowRightFork;
		private final boolean allowFewerReps;
		private final boolean allowMoreReps;
		private final int theLength;
		private final int theErrorCount;
		private final int theErrorPos;

		SequencePossibility(AbstractSequencedExpressionType<? super S> type, ExpressoParser<S> parser,
			List<ExpressionPossibility<S>> repetitions) {
			this(type, parser, repetitions, true, true, true, true);
		}

		private SequencePossibility(AbstractSequencedExpressionType<? super S> type, ExpressoParser<S> parser,
			List<ExpressionPossibility<S>> repetitions, boolean allowLeftFork, boolean allowRightFork, boolean allowMoreReps,
			boolean allowFewerReps) {
			theType = type;
			theParser = parser;
			theRepetitions = repetitions;
			this.allowLeftFork = allowLeftFork;
			this.allowRightFork = allowRightFork;
			this.allowFewerReps = allowFewerReps;
			this.allowMoreReps = allowMoreReps;
			int length = 0;
			int errorCount = 0;
			int errorPos = -1;
			for (ExpressionPossibility<S> rep : repetitions) {
				if (errorPos == -1) {
					errorPos = rep.getFirstErrorPosition();
					if (errorPos >= 0)
						errorPos += length;
				}
				length += rep.length();
				errorCount += rep.getErrorCount();
			}
			theLength = length;
			theErrorCount = errorCount;
			theErrorPos = errorPos;
		}

		@Override
		public S getStream() {
			return theParser.getStream();
		}

		@Override
		public int length() {
			return theLength;
		}

		@Override
		public ExpressionPossibility<S> advance() throws IOException {
			if (theRepetitions.isEmpty())
				return null;
			ExpressionPossibility<S> last = theRepetitions.get(theRepetitions.size() - 1);
			ExpressionPossibility<S> lastAdvanced = last.advance();
			if (lastAdvanced == null)
				return null;
			List<ExpressionPossibility<S>> repetitions = new ArrayList<>(theRepetitions.size());
			for (int i = 0; i < theRepetitions.size() - 1; i++)
				repetitions.add(theRepetitions.get(i));
			repetitions.add(lastAdvanced);
			return new SequencePossibility<>(theType, theParser, Collections.unmodifiableList(repetitions));
		}

		@Override
		public ExpressionPossibility<S> leftFork() throws IOException {
			if (allowLeftFork && !theRepetitions.isEmpty()) {
				ExpressionPossibility<S> last = theRepetitions.get(theRepetitions.size() - 1);
				ExpressionPossibility<S> lastForked = last.leftFork();
				if (lastForked != null) {
					List<ExpressionPossibility<S>> repetitions = new ArrayList<>(theRepetitions.size());
					for (int i = 0; i < theRepetitions.size() - 1; i++)
						repetitions.add(theRepetitions.get(i));
					repetitions.add(lastForked);
					return new SequencePossibility<>(theType, theParser, Collections.unmodifiableList(repetitions), true, false, true,
						true);
				}
			}
			if (allowFewerReps && !theRepetitions.isEmpty()) {
				return new SequencePossibility<>(theType, theParser, theRepetitions.subList(0, theRepetitions.size() - 1), true, true, true,
					false);
			}
			return null;
		}

		@Override
		public ExpressionPossibility<S> rightFork() throws IOException {
			if (allowRightFork && !theRepetitions.isEmpty()) {
				ExpressionPossibility<S> last = theRepetitions.get(theRepetitions.size() - 1);
				ExpressionPossibility<S> lastForked = last.rightFork();
				if (lastForked != null) {
					List<ExpressionPossibility<S>> repetitions = new ArrayList<>(theRepetitions.size());
					for (int i = 0; i < theRepetitions.size() - 1; i++)
						repetitions.add(theRepetitions.get(i));
					repetitions.add(lastForked);
					return new SequencePossibility<>(theType, theParser, Collections.unmodifiableList(repetitions), true, true, false,
						true);
				}
			}
			if (allowMoreReps) {
				int len = length();
				if (getStream().discoverTo(len + 1) > len) {
					Iterator<? extends ExpressionComponent<? super S>> sequenceIter = theType.theSequence.iterator();
					// Advance past pre-computed repetitions
					for (int i = 0; i < theRepetitions.size() && sequenceIter.hasNext(); i++)
						sequenceIter.next();
					if (!sequenceIter.hasNext()) {
						List<ExpressionPossibility<S>> repetitions = new ArrayList<>(theRepetitions.size() + 1);
						ExpressionComponent<? super S> nextComponent = sequenceIter.next();
						repetitions.add(theParser.advance(length()).parseWith(nextComponent));
						return new SequencePossibility<>(theType, theParser, Collections.unmodifiableList(repetitions), true, true, false,
							true);
					}
				}
			}
			return null;
		}

		@Override
		public int getErrorCount() {
			return theErrorCount;
		}

		@Override
		public int getFirstErrorPosition() {
			return theErrorPos;
		}

		@Override
		public boolean isComplete() {
			if (!theRepetitions.isEmpty()) {
				ExpressionPossibility<S> last = theRepetitions.get(theRepetitions.size() - 1);
				if (!last.isComplete())
					return false;
			}
			return theType.isComplete(theRepetitions.size());
		}

		@Override
		public Expression<S> getExpression() {
			List<Expression<S>> repetitions = new ArrayList<>(theRepetitions.size());
			for (ExpressionPossibility<S> rep : theRepetitions)
				repetitions.add(rep.getExpression());
			return new SequenceExpression<>(getStream(), theType, Collections.unmodifiableList(repetitions));
		}
	}

	public static final class SequenceExpression<S extends BranchableStream<?, ?>> extends ComposedExpression<S> {
		SequenceExpression(S stream, AbstractSequencedExpressionType<? super S> type, List<Expression<S>> repetitions) {
			super(stream, type, repetitions);
		}

		@Override
		public AbstractSequencedExpressionType<? super S> getType() {
			return (AbstractSequencedExpressionType<? super S>) super.getType();
		}

		@Override
		protected BiTuple<Integer, String> getSelfError() {
			BiTuple<Integer, String> selfError = super.getSelfError();
			if (selfError == null) {
				String ccError = getType().getErrorForComponentCount(getChildren().size());
				if (ccError == null)
					selfError = new BiTuple<>(length(), ccError);
			}
			return selfError;
		}

		@Override
		public Expression<S> unwrap() {
			return this;
		}
	}
}
