package org.expresso2.impl;

public class SimpleLinkedList<E> {
	public class Link {
		public E value;
		private Link theNext;

		Link(E value, SimpleLinkedList<E>.Link next) {
			this.value = value;
			this.theNext = next;
		}

		public Link getNext() {
			return theNext;
		}
	}

	private Link theFirst;
	private Link theLast;
	private int theSize;

	public void add(E value) {
		Link link = new Link(value, null);
		if (theFirst == null)
			theFirst = link;
		if (theLast == null)
			theLast = link;
		else
			theLast.theNext = link;
		theSize++;
	}

	public Link getFirst() {
		return theFirst;
	}
}
