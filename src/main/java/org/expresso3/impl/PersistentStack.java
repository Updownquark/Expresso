package org.expresso3.impl;

import java.util.function.Predicate;
import java.util.function.Supplier;

public class PersistentStack<E> implements Supplier<E> {
	private final PersistentStack<E> theParent;
	private final int theSize;
	private final E theValue;

	public PersistentStack(PersistentStack<E> parent, E value) {
		theParent = parent;
		theSize = theParent == null ? 1 : theParent.theSize + 1;
		theValue = value;
	}

	public PersistentStack<E> getParent() {
		return theParent;
	}

	@Override
	public E get() {
		return theValue;
	}

	public int size() {
		return theSize;
	}

	public E search(Predicate<? super E> filter) {
		if (filter.test(theValue))
			return theValue;
		else if (theParent != null)
			return theParent.search(filter);
		else
			return null;
	}

	public boolean searchStacks(Predicate<PersistentStack<E>> action) {
		if (theParent != null && theParent.searchStacks(action))
			return true;
		else
			return action.test(this);
	}
}
