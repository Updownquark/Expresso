package org.expresso3.types;

import java.util.Iterator;
import java.util.List;

import org.expresso.stream.BranchableStream;
import org.expresso3.Expression;
import org.expresso3.ExpressionType;
import org.qommons.BiTuple;

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
	public RepeatExpressionType(int id, int min, int max, List<ExpressionType<S>> components) {
		super(id, new InfiniteSequenceRepeater<>());
		if (min > max)
			throw new IllegalArgumentException("min (" + min + ") must be <= max (" + max + ")");
		theMinCount = min;
		theMaxCount = max;
		theSequence = new SequenceExpressionType<>(-1, components);
		((InfiniteSequenceRepeater<SequenceExpressionType<S>>) getSequence()).theValue = theSequence;
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
	public int getSpecificity() {
		return theMinCount * theSequence.getSpecificity();
	}

	@Override
	protected BiTuple<String, Integer> getErrorForComponents(List<? extends Expression<? extends S>> components) {
		if (components.size() < theMinCount)
			return new BiTuple<>(
				"At least " + theMinCount + " " + theSequence + (theMinCount > 1 ? "s" : "") + " expected, but found " + components.size(),
				theSequence.getSpecificity() * (theMinCount - components.size()));
		else if (components.size() > theMaxCount) {
			int weight = 0;
			for (int i = theMaxCount; i < components.size(); i++)
				weight += Math.abs(components.get(i).getMatchQuality());
			// For more matches than allowed, the extra matches count against the match's quality
			return new BiTuple<>("No more than " + theMaxCount + " " + theSequence + (theMaxCount > 1 ? "s" : "") + " expected, but found "
				+ components.size(), weight * 2);
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
			return new InifiniteRepeatingIterator<>(theValue);
		}

		@Override
		public String toString() {
			return theValue + "*";
		}
	}

	private static class InifiniteRepeatingIterator<E> implements Iterator<E> {
		private final E theValue;

		InifiniteRepeatingIterator(E value) {
			theValue = value;
		}

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
