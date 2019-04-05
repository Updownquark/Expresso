package org.expresso3.types;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;

import org.expresso.stream.BranchableStream;
import org.expresso3.Expression;
import org.expresso3.ExpressionType;
import org.expresso3.ExpressoParser;
import org.qommons.BiTuple;

/**
 * An expression that must be satisfied by one or more specific expressions in order
 *
 * @param <S> The type of the stream
 */
public abstract class AbstractSequencedExpressionType<S extends BranchableStream<?, ?>> extends AbstractExpressionType<S> {
	private final Iterable<? extends ExpressionType<? super S>> theSequence;

	/**
	 * @param id The cache ID for the sequence
	 * @param sequence The components of the sequence
	 */
	public AbstractSequencedExpressionType(int id, Iterable<? extends ExpressionType<? super S>> sequence) {
		super(id);
		theSequence = sequence;
	}

	/** @return The components of this sequence */
	protected Iterable<? extends ExpressionType<? super S>> getSequence() {
		return theSequence;
	}

	/**
	 * @param componentCount The number of components matched
	 * @return Whether the given number of components is enough to completely satisfy this sequence
	 */
	protected abstract boolean isComplete(int componentCount);

	/**
	 * @param components The components matched
	 * @return null if The given component count is fine for this expression type. Otherwise, a tuple containing an error message and error
	 *         weight to apply (negatively) to the match's {@link Expression#getMatchQuality() quality}.
	 */
	protected abstract BiTuple<String, Integer> getErrorForComponents(List<? extends Expression<? extends S>> components);

	@Override
	public <S2 extends S> Expression<S2> parse(ExpressoParser<S2> parser) throws IOException {
		List<Expression<S2>> repetitions = new LinkedList<>();
		ExpressoParser<S2> branched = parser;
		Iterator<? extends ExpressionType<? super S>> sequenceIter = theSequence.iterator();
		while (branched != null && sequenceIter.hasNext()) {
			ExpressionType<? super S> component = sequenceIter.next();
			Expression<S2> repetition = branched.parseWith(component);
			if (repetition == null)
				break;
			if (repetition.getErrorCount() > 0)
				break;
			repetitions.add(repetition);
			branched = branched.advance(repetition.length());
		}
		SequencePossibility<S2> seq = new SequencePossibility<>(this, parser, Collections.unmodifiableList(repetitions));
		if (seq.getMatchQuality() >= parser.getQualityLevel())
			return seq;
		return null;
	}

	private static class SequencePossibility<S extends BranchableStream<?, ?>> extends ComposedExpression<S> {
		private final boolean allowFewerReps;
		private final boolean allowMoreReps;

		SequencePossibility(AbstractSequencedExpressionType<? super S> type, ExpressoParser<S> parser,
			List<Expression<S>> repetitions) {
			this(type, parser, repetitions, true, true);
		}

		private SequencePossibility(AbstractSequencedExpressionType<? super S> type, ExpressoParser<S> parser,
			List<? extends Expression<S>> repetitions, boolean allowFewerReps, boolean allowMoreReps) {
			super(type, parser, repetitions);
			allowFewerReps = false; // Let's try turning this off
			this.allowFewerReps = allowFewerReps;
			this.allowMoreReps = allowMoreReps;
		}

		@Override
		public AbstractSequencedExpressionType<? super S> getType() {
			return (AbstractSequencedExpressionType<? super S>) super.getType();
		}

		@Override
		protected int getSelfComplexity() {
			return 1;
		}

		@Override
		protected CompositionError getSelfError() {
			BiTuple<String, Integer> error = getType().getErrorForComponents(getChildren());
			if (error == null)
				return null;
			else
				return new CompositionError(length(), error.getValue1(), error.getValue2());
		}

		@Override
		public Expression<S> nextMatch() throws IOException {
			SequencePossibility<S> next;
			// 3 potential ways to branch a sequence...
			if (!getChildren().isEmpty()) {
				// 1. Branch the last element in the sequence
				Expression<S> last = getChildren().get(getChildren().size() - 1);
				Expression<S> lastNext = last.nextMatch();
				if (lastNext != null) {
					List<Expression<S>> repetitions = new ArrayList<>(getChildren().size());
					for (int i = 0; i < getChildren().size() - 1; i++)
						repetitions.add(getChildren().get(i));
					repetitions.add(lastNext);

					next = new SequencePossibility<>(getType(), getParser(), Collections.unmodifiableList(repetitions));
					if (next.getMatchQuality() >= getParser().getQualityLevel())
						return next;
				} else if (allowFewerReps && getChildren().size() > 1) {
					// 2. Remove the last element in the sequence
					next = new SequencePossibility<>(getType(), getParser(), getChildren().subList(0, getChildren().size() - 1), true,
						false);
					if (next.getMatchQuality() >= getParser().getQualityLevel())
						return next;
				}
			}
			if (allowMoreReps) {
				int len = length();
				if (getStream().discoverTo(len + 1) > len) {
					Iterator<? extends ExpressionType<? super S>> sequenceIter = getType().theSequence.iterator();
					// Advance past pre-computed repetitions
					for (int i = 0; i < getChildren().size() && sequenceIter.hasNext(); i++)
						sequenceIter.next();
					if (sequenceIter.hasNext()) {
						// 3. Append another repetition to the sequence
						ExpressionType<? super S> nextComponent = sequenceIter.next();
						Expression<S> nextPossibility = getParser().advance(len).parseWith(nextComponent);
						if (nextPossibility != null) {
							List<Expression<S>> repetitions = new ArrayList<>(getChildren().size() + 1);
							repetitions.addAll(getChildren());
							repetitions.add(nextPossibility);
							next = new SequencePossibility<>(getType(), getParser(), Collections.unmodifiableList(repetitions), false,
								true);
							if (next.getMatchQuality() >= getParser().getQualityLevel())
								return next;
						}
					}
				}
			}
			return null;
		}

		@Override
		public Expression<S> unwrap() {
			return this;
		}

		@Override
		protected boolean shouldPrintErrorInfo() {
			return true;
		}

		@Override
		protected boolean shouldPrintContent() {
			return true;
		}
	}
}
