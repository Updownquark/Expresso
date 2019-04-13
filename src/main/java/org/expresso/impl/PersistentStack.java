package org.expresso.impl;

import java.util.function.Predicate;
import java.util.function.Supplier;

class PersistentStack<E> implements Supplier<E> {
	private final PersistentStack<E> theParent;
	private final E theValue;

	PersistentStack(PersistentStack<E> parent, E value) {
		theParent = parent;
		theValue = value;
	}

	@Override
	public E get() {
		return theValue;
	}

	E search(Predicate<? super E> filter) {
		if (filter.test(theValue))
			return theValue;
		else if (theParent != null)
			return theParent.search(filter);
		else
			return null;
	}

	@Override
	public String toString() {
		return print(new StringBuilder()).toString();
	}

	private Object print(StringBuilder str) {
		if (theParent != null) {
			theParent.print(str);
			str.append('\n');
		}
		str.append(theValue);
		return str;
	}
}
