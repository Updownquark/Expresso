package org.expresso.types;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;

import org.expresso.Expression;
import org.expresso.ExpressionType;
import org.expresso.ExpressoParser;
import org.expresso.stream.BranchableStream;
import org.expresso.util.ExpressoUtils;
import org.qommons.IntList;

/**
 * An expression that must be satisfied by one or more specific expressions in order
 *
 * @param <S> The type of the stream
 */
public abstract class AbstractSequencedExpressionType<S extends BranchableStream<?, ?>> extends AbstractExpressionType<S> {
	private final Iterable<? extends ExpressionType<? super S>> theSequence;
	private Iterator<? extends ExpressionType<? super S>> theSequenceIterator;
	private final List<ExpressionType<? super S>> theSequenceCache;
	private IntList theEmptyQuality;

	/**
	 * @param id The cache ID for the sequence
	 * @param sequence The components of the sequence
	 */
	public AbstractSequencedExpressionType(int id, Iterable<? extends ExpressionType<? super S>> sequence) {
		super(id);
		theSequence = sequence;
		if (sequence instanceof List) {
			theSequenceIterator = null;
			theSequenceCache = (List<ExpressionType<? super S>>) sequence;
		} else {
			theSequenceIterator = sequence.iterator();
			theSequenceCache = new ArrayList<>();
		}
	}

	/** @return The components of this sequence */
	@Override
	public Iterable<? extends ExpressionType<? super S>> getComponents() {
		return theSequence;
	}

	@Override
	public int getEmptyQuality(int minQuality) {
		if (theEmptyQuality == null)
			theEmptyQuality = new IntList();
		int eqIndex = -minQuality;
		while (theEmptyQuality.size() <= eqIndex)
			theEmptyQuality.add(1);
		if (theEmptyQuality.get(eqIndex) > 0) {
			theEmptyQuality.set(eqIndex, 0); // To prevent infinite recursion
			int quality = minQuality;
			for (ExpressionType<?> component : theSequence) {
				int childQuality = component.getEmptyQuality(quality);
				quality += childQuality;
				if (quality < minQuality)
					break;
			}
			theEmptyQuality.set(eqIndex, quality);
		}
		return theEmptyQuality.get(eqIndex);
	}

	/**
	 * @param componentCount The number of components matched
	 * @return Whether the given number of components is enough to completely satisfy this sequence
	 */
	protected abstract boolean isComplete(int componentCount);

	/**
	 * @param components The components matched
	 * @param minQuality The minimum quality needed for the expression
	 * @return null if The given component count is fine for this expression type. Otherwise, a tuple containing an error message and error
	 *         weight to apply (negatively) to the match's {@link Expression#getMatchQuality() quality}.
	 */
	protected abstract CompositionError getErrorForComponents(List<? extends Expression<? extends S>> components, int minQuality);

	@Override
	public <S2 extends S> Expression<S2> parse(ExpressoParser<S2> parser, Expression<S2> lowBound, Expression<S2> highBound)
		throws IOException {
		return branch(//
			lowBound == null ? new LinkedList<>() : new LinkedList<>(lowBound.getChildren()), //
			parser, lowBound != null, //
			highBound == null ? null : highBound.getChildren());
	}

	<S2 extends S> Expression<S2> branch(LinkedList<Expression<S2>> repetitions, ExpressoParser<S2> parser, boolean mustChange, //
		List<? extends Expression<S2>> highBound)
		throws IOException {
		boolean changed = false;
		int childQuality = 0;
		for (Expression<S2> rep : repetitions)
			childQuality += rep.getMatchQuality();
		while (true) {
			ExpressoParser<S2> branched;
			if (repetitions.isEmpty())
				branched = parser;
			else
				branched = parser.advance(ExpressoUtils.getLength(parser.getStream().getPosition(), repetitions));
			SequencePossibility<S2> seq = null;
			boolean tested = false;
			while (branched != null && hasChild(repetitions.size())) {
				if (!mustChange || changed) {
					tested = true;
					seq = buildIfSatisfactory(repetitions, parser, childQuality);
					if (seq != null)
						return seq;
				}
				ExpressionType<? super S> component = theSequenceCache.get(repetitions.size());
				if (!repetitions.isEmpty() && repetitions.getLast().length() == 0 && repetitions.getLast().getType() == component) {
					break; // Repeating the same type with zero length is bad
				} else {
					Expression<S2> repetition = branched.parseWith(component, null, //
						(highBound == null || highBound.size() <= repetitions.size()) ? null : highBound.get(repetitions.size()));
					if (repetition != null) {
						int tempCQ = childQuality + repetition.getMatchQuality();
						if (tempCQ >= parser.getQualityLevel()) {
							childQuality = tempCQ;
							tested = false;
							changed = true;
							repetitions.add(repetition);
							branched = branched.advance(repetition.length());
						}
					} else
						break;
				}
			}
			if (!tested && (!mustChange || changed)) {
				seq = buildIfSatisfactory(repetitions, parser, childQuality);
				if (seq != null)
					return seq;
			}
			while (true) {
				if (repetitions.isEmpty())
					return null; // All possibilities exhausted
				// Try a different branch of a previous element in the sequence
				Expression<S2> last = repetitions.removeLast();
				int lastPos = ExpressoUtils.getLength(parser.getStream().getPosition(), repetitions);
				childQuality -= last.getMatchQuality();
				branched = parser.advance(lastPos);
				Expression<S2> repetition = branched.parseWith(//
					last.getType(), last,
					(highBound == null || highBound.size() <= repetitions.size()) ? null : highBound.get(repetitions.size()));
				if (repetition != null) {
					// If the match doesn't differ in length or quality, we won't have any better luck with it
					if (repetition.length() != last.length() || repetition.getMatchQuality() >= last.getMatchQuality()) {
						int tempCQ = childQuality + repetition.getMatchQuality();
						if (tempCQ >= parser.getQualityLevel()) {
							childQuality = tempCQ;
							changed = true;
							repetitions.add(repetition);
							break;
						}
					}
				}
			}
		}
	}

	private boolean hasChild(int index) {
		if (theSequenceIterator != null && index >= theSequenceCache.size()) {
			while (theSequenceCache.size() <= index) {
				if (theSequenceIterator.hasNext()) {
					ExpressionType<? super S> component = theSequenceIterator.next();
					theSequenceCache.add(component);
				} else {
					theSequenceIterator = null;
					break;
				}
			}
		}
		return index < theSequenceCache.size();
	}

	private <S2 extends S> SequencePossibility<S2> buildIfSatisfactory(List<Expression<S2>> repetitions, ExpressoParser<S2> parser,
		int childQuality) {
		// This is a little hacky because it duplicates the logic for determining match quality
		// but actually building the matches and then calculating all the collective match metrics is expensive,
		// especially since many or even most of the matches would end up being thrown away immediately
		// TrackNode bisNode = TRACKER.start("buildIfSatisfactory");
		try {
			// TrackNode efcNode = TRACKER.start("errorForComponents");
			CompositionError error = getErrorForComponents(repetitions, parser.getQualityLevel());
			// efcNode.end();
			int quality = error == null ? 0 : -error.errorWeight;
			int threshold = parser.getQualityLevel();
			if (quality < threshold)
				return null;
			quality += childQuality;
			if (quality < threshold)
				return null;
			// TrackNode createNode = TRACKER.start("create");
			SequencePossibility<S2> seq = new SequencePossibility<>(this, parser, repetitions);
			// createNode.end();
			if (seq.getMatchQuality() >= parser.getQualityLevel())
				return seq;
			else
				return null;
		} finally {
			// bisNode.end();
		}
	}

	private static class SequencePossibility<S extends BranchableStream<?, ?>> extends ComposedExpression<S> {
		SequencePossibility(AbstractSequencedExpressionType<? super S> type, ExpressoParser<S> parser, List<Expression<S>> repetitions) {
			super(type, parser, repetitions);
		}

		@Override
		public AbstractSequencedExpressionType<? super S> getType() {
			return (AbstractSequencedExpressionType<? super S>) super.getType();
		}

		@Override
		protected CompositionError getSelfError(ExpressoParser<S> parser) {
			return getType().getErrorForComponents(getChildren(), parser.getQualityLevel());
		}

		@Override
		public Expression<S> nextMatch(ExpressoParser<S> parser) throws IOException {
			return getType()//
				.branch(//
					new LinkedList<>(getChildren()), parser, true);
		}

		@Override
		public Expression<S> nextMatchHighPriority(ExpressoParser<S> parser) throws IOException {
			return getType().parse(parser);
		}

		@Override
		public Expression<S> nextMatchLowPriority(ExpressoParser<S> parser, Expression<S> limit) throws IOException {
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
