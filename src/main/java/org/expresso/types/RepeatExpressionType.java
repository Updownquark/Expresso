package org.expresso.types;

import java.util.Iterator;
import java.util.List;

import org.expresso.Expression;
import org.expresso.ExpressionType;
import org.expresso.stream.BranchableStream;
import org.expresso.util.ExpressoUtils;

/**
 * An expression composed of one or more repetitions of another sequence of expressions
 *
 * @param <S> The type of the stream
 */
public class RepeatExpressionType<S extends BranchableStream<?, ?>> extends AbstractSequencedExpressionType<S> {
	private final int theMinCount;
	private final int theMaxCount;
	private final SequenceExpressionType<S> theSequence;

	/**
	 * @param id The cache ID for this expression
	 * @param min The minimum repetition count for the sequence needed to satisfy this expression
	 * @param max The maximum number of times the sequence may be present in the stream
	 * @param components The components for the sequence
	 */
	public RepeatExpressionType(int id, int min, int max, List<? extends ExpressionType<? super S>> components) {
		super(id, new InfiniteSequenceRepeater<>());
		if (min > max)
			throw new IllegalArgumentException("min (" + min + ") must be <= max (" + max + ")");
		theMinCount = min;
		theMaxCount = max;
		theSequence = new SequenceExpressionType<>(-1, components);
		((InfiniteSequenceRepeater<SequenceExpressionType<S>>) getComponents()).theValue = theSequence;
	}

	@Override
	public int getEmptyQuality(int minQuality) {
		if (theMinCount == 0)
			return 0;
		else {
			int quality = minQuality;
			for (int i = 0; i < theMinCount; i++)
				quality += theSequence.getEmptyQuality(quality);
			return quality;
		}
	}

	/** @return The minimum repetition count for the sequence needed to satisfy this expression */
	public int getMinCount() {
		return theMinCount;
	}

	/** @return The maximum number of times the sequence may be present in the stream */
	public int getMaxCount() {
		return theMaxCount;
	}

	/** @return The repeating sequence */
	public SequenceExpressionType<S> getComponent() {
		return theSequence;
	}

	@Override
	protected boolean isComplete(int componentCount) {
		return componentCount >= theMinCount;
	}

	@Override
	protected CompositionError getErrorForComponents(List<? extends Expression<? extends S>> components, int minQuality) {
		if (components.size() < theMinCount) {
			// TrackNode r1Node = TRACKER.start("repeat1");
			// TrackNode posNode = TRACKER.start("length");
			int pos = ExpressoUtils.getLength(0, components);
			// posNode.end();
			int missing = theMinCount - components.size();
			int weight = missing * -theSequence.getEmptyQuality(minQuality == 0 ? 0 : (minQuality / missing) - 1);
			// r1Node.end();
			return new CompositionError(pos, () -> {
				return new StringBuilder("At least ").append(theMinCount).append(' ').append(theSequence).append(theMinCount > 1 ? "s" : "")
					.append(" expected, but found ").append(components.size()).toString();
			}, weight);
		} else if (components.size() > theMaxCount) {
			// TrackNode r2Node = TRACKER.start("repeat2");
			int weight = 0;
			// TrackNode posNode = TRACKER.start("length");
			int pos = theMaxCount == 0 ? 0 : ExpressoUtils.getEnd(components.get(theMaxCount - 1));
			// posNode.end();
			for (int i = theMaxCount; i < components.size(); i++)
				weight += Math.abs(components.get(i).getMatchQuality());

			// For more matches than allowed, the extra matches count against the match's quality
			// r2Node.end();
			return new CompositionError(pos, () -> {
				return new StringBuilder("No more than ").append(theMaxCount).append(' ').append(theSequence)
					.append(theMaxCount > 1 ? "s" : "").append(" expected, but found ").append(components.size()).toString();
			}, weight * 2);
		}
		return null;
	}

	@Override
	public String toString() {
		StringBuilder str = new StringBuilder("Repeat");
		if (theMinCount > 0 || theMaxCount != Integer.MAX_VALUE) {
			str.append('(').append(theMinCount);
			if (theMaxCount == Integer.MAX_VALUE)
				str.append('+');
			else
				str.append('-').append(theMaxCount);
			str.append(')');
		}
		return "Repeat:" + theSequence;
	}

	private static class InfiniteSequenceRepeater<E> implements Iterable<E> {
		private E theValue;

		@Override
		public Iterator<E> iterator() {
			return new ISIterator();
		}

		@Override
		public String toString() {
			return theValue + "*";
		}

		private class ISIterator implements Iterator<E> {
			@Override
			public boolean hasNext() {
				return true;
			}

			@Override
			public E next() {
				return theValue;
			}
		}
	}
}
