package org.expresso2;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

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
		for (int i = 0; i < tries && branched != null && sequenceIter.hasNext(); i++) {
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

	private static class SequencePossibility<S extends BranchableStream<?, ?>> implements ExpressionPossibility<S> {
		private final AbstractSequencedExpressionType<? super S> theType;
		private final ExpressoParser<S> theParser;
		private final List<ExpressionPossibility<S>> theRepetitions;
		private final boolean allowFewerReps;
		private final boolean allowMoreReps;
		private final int theLength;
		private final int theErrorCount;
		private final int theErrorPos;

		SequencePossibility(AbstractSequencedExpressionType<? super S> type, ExpressoParser<S> parser,
			List<ExpressionPossibility<S>> repetitions) {
			this(type, parser, repetitions, true, true);
		}

		private SequencePossibility(AbstractSequencedExpressionType<? super S> type, ExpressoParser<S> parser,
			List<ExpressionPossibility<S>> repetitions, boolean allowMoreReps, boolean allowFewerReps) {
			theType = type;
			theParser = parser;
			theRepetitions = repetitions;
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
		public ExpressionComponent<? super S> getType() {
			return theType;
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
		public Collection<? extends ExpressionPossibility<S>> fork() throws IOException {
			CompositeCollection<ExpressionPossibility<S>> forks = new CompositeCollection<>();
			if (!theRepetitions.isEmpty()) {
				ExpressionPossibility<S> last = theRepetitions.get(theRepetitions.size() - 1);
				Collection<? extends ExpressionPossibility<S>> lastForks = last.fork();
				if (!lastForks.isEmpty()) {
					forks.addComponent(lastForks.stream().map(lastFork -> {
						List<ExpressionPossibility<S>> repetitions = new ArrayList<>(theRepetitions.size());
						for (int i = 0; i < theRepetitions.size() - 1; i++)
							repetitions.add(theRepetitions.get(i));
						repetitions.add(lastFork);
						return new SequencePossibility<>(theType, theParser, Collections.unmodifiableList(repetitions));
					}).collect(Collectors.toCollection(() -> new ArrayList<>(lastForks.size()))));
				}
				if (allowFewerReps) {
					forks.addComponent(Arrays.asList(
						new SequencePossibility<>(theType, theParser, theRepetitions.subList(0, theRepetitions.size() - 1), true, false)));
				}
			}
			if (allowMoreReps) {
				int len = length();
				if (getStream().discoverTo(len + 1) > len) {
					Iterator<? extends ExpressionComponent<? super S>> sequenceIter = theType.theSequence.iterator();
					// Advance past pre-computed repetitions
					for (int i = 0; i < theRepetitions.size() && sequenceIter.hasNext(); i++)
						sequenceIter.next();
					if (sequenceIter.hasNext()) {
						List<ExpressionPossibility<S>> repetitions = new ArrayList<>(theRepetitions.size() + 1);
						ExpressionComponent<? super S> nextComponent = sequenceIter.next();
						repetitions.add(theParser.advance(length()).parseWith(nextComponent));
						forks.addComponent(Arrays
							.asList(new SequencePossibility<>(theType, theParser, Collections.unmodifiableList(repetitions), false, true)));
					}
				}
			}
			return forks;
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
		public boolean equals(Object o) {
			if (o == this)
				return true;
			else if (o == null || o.getClass() != getClass())
				return false;
			SequencePossibility<S> other = (SequencePossibility<S>) o;
			if (!getType().equals(other.getType()))
				return false;
			if (getStream().getPosition() != other.getStream().getPosition())
				return false;
			if (length() != other.length())
				return false;
			if (theRepetitions.size() != other.theRepetitions.size())
				return false;
			for (int i = 0; i < theRepetitions.size(); i++)
				if (!theRepetitions.get(i).equals(other.theRepetitions.get(i)))
					return false;
			return true;
		}

		@Override
		public int hashCode() {
			return Objects.hash(getClass(), theType, getStream().getPosition(), length(), theRepetitions);
		}

		@Override
		public Expression<S> getExpression() {
			List<Expression<S>> repetitions = new ArrayList<>(theRepetitions.size());
			for (ExpressionPossibility<S> rep : theRepetitions)
				repetitions.add(rep.getExpression());
			return new SequenceExpression<>(getStream(), theType, Collections.unmodifiableList(repetitions));
		}

		@Override
		public String toString() {
			return theRepetitions.toString();
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
