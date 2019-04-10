package org.expresso3.types;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;

import org.expresso.stream.BranchableStream;
import org.expresso.util.ExpressoUtils;
import org.expresso3.Expression;
import org.expresso3.ExpressionType;
import org.expresso3.ExpressoParser;

/**
 * An expression that must be satisfied by one or more specific expressions in order
 *
 * @param <S> The type of the stream
 */
public abstract class AbstractSequencedExpressionType<S extends BranchableStream<?, ?>> extends AbstractExpressionType<S> {
	private final Iterable<? extends ExpressionType<? super S>> theSequence;
	private Iterator<? extends ExpressionType<? super S>> theSequenceIterator;
	private final List<ExpressionType<? super S>> theSequenceCache;

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
	protected abstract CompositionError getErrorForComponents(List<? extends Expression<? extends S>> components);

	@Override
	public <S2 extends S> Expression<S2> parse(ExpressoParser<S2> parser) throws IOException {
		return branch(//
			new LinkedList<>(), parser, false);
	}

	<S2 extends S> Expression<S2> branch(LinkedList<Expression<S2>> repetitions, ExpressoParser<S2> parser, boolean mustChange)
		throws IOException {
		// TrackNode branchNode = TRACKER.start("branch");
		boolean changed = false;
		int childQuality = 0;
		for (Expression<S2> rep : repetitions)
			childQuality += rep.getMatchQuality();
		try {
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
						// branchNode.end();
						// branchNode = null;
						Expression<S2> repetition = branched.parseWith(component);
						// branchNode = TRACKER.start("branch");
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
					// branchNode.end();
					// branchNode = null;
					Expression<S2> repetition = branched.nextMatch(last);
					// branchNode = TRACKER.start("branch");
					if (repetition != null) {
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
		} finally {
			// if (branchNode != null)
			// branchNode.end();
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
			CompositionError error = getErrorForComponents(repetitions);
			// efcNode.end();
			int quality = error == null ? 0 : -error.errorWeight;
			int threshold = parser.getQualityLevel();
			if (quality < threshold)
				return null;
			quality += childQuality;
			if (quality < threshold)
				return null;
			// TrackNode createNode = TRACKER.start("create");
			SequencePossibility<S2> seq = new SequencePossibility<>(this, parser.getStream(), repetitions);
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
		SequencePossibility(AbstractSequencedExpressionType<? super S> type, S stream, List<Expression<S>> repetitions) {
			super(type, stream, repetitions);
		}

		@Override
		public AbstractSequencedExpressionType<? super S> getType() {
			return (AbstractSequencedExpressionType<? super S>) super.getType();
		}

		@Override
		protected CompositionError getSelfError() {
			return getType().getErrorForComponents(getChildren());
		}

		@Override
		public Expression<S> nextMatch(ExpressoParser<S> parser) throws IOException {
			return getType()//
				.branch(//
					new LinkedList<>(getChildren()), parser, true);
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
