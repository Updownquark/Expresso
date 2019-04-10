package org.expresso3.impl;

import java.util.function.Predicate;
import java.util.function.Supplier;

public class PersistentStack<E> implements Supplier<E> {
	private final PersistentStack<E> theParent;
	private final E theValue;

	public PersistentStack(PersistentStack<E> parent, E value) {
		theParent = parent;
		theValue = value;
	}

	public PersistentStack<E> getParent() {
		return theParent;
	}

	@Override
	public E get() {
		return theValue;
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
