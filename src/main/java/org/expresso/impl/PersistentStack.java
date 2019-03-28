package org.expresso.impl;

import java.util.function.Predicate;

class PersistentStack<E> {
	private final PersistentStack<E> theParent;
	private final E theValue;

	public PersistentStack(PersistentStack<E> parent, E value) {
		theParent = parent;
		theValue = value;
	}

	E search(Predicate<? super E> filter) {
		if (filter.test(theValue))
			return theValue;
		else if (theParent != null)
			return theParent.search(filter);
		else
			return null;
	}
}
