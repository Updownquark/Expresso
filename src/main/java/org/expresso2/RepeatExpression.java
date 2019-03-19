package org.expresso2;

import java.util.Iterator;
import java.util.List;

import org.expresso.parse.BranchableStream;

public class RepeatExpression<S extends BranchableStream<?, ?>> extends AbstractSequencedExpressionType<S> {
	private final int theMinCount;
	private final int theMaxCount;
	private final org.expresso2.SequenceExpression<S> theSequence;

	public RepeatExpression(int id, int min, int max, List<ExpressionComponent<S>> components) {
		super(id, new InfiniteSequenceRepeater());
		if (min > max)
			throw new IllegalArgumentException("min (" + min + ") must be <= max (" + max + ")");
		theMinCount = min;
		theMaxCount = max;
		theSequence = new org.expresso2.SequenceExpression<>(id, components);
		((InfiniteSequenceRepeater<org.expresso2.SequenceExpression<S>>) getSequence()).theValue = theSequence;
	}

	public int getMinCount() {
		return theMinCount;
	}

	public int getMaxCount() {
		return theMaxCount;
	}

	public org.expresso2.SequenceExpression<S> getComponent() {
		return theSequence;
	}

	@Override
	protected int getInitComponentCount() {
		return theMinCount == 0 ? 1 : theMinCount;
	}

	@Override
	protected boolean isComplete(int componentCount) {
		return componentCount >= theMinCount;
	}

	@Override
	protected String getErrorForComponentCount(int componentCount) {
		if (componentCount < theMinCount)
			return "At least " + theMinCount + " " + theSequence + (theMinCount > 1 ? "s" : "") + " expected, but found " + componentCount;
		else if (componentCount > theMaxCount)
			return "No more than " + theMaxCount + " " + theSequence + (theMaxCount > 1 ? "s" : "") + " expected, but found "
				+ componentCount;
		return null;
	}

	private static class InfiniteSequenceRepeater<E> implements Iterable<E> {
		private E theValue;

		@Override
		public Iterator<E> iterator() {
			return new InifiniteRepeatingIterator<>(theValue);
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
