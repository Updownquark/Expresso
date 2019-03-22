package org.expresso2;

import java.util.AbstractCollection;
import java.util.Collection;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.Objects;

import org.qommons.IterableUtils;

public class CompositeCollection<E> extends AbstractCollection<E> {
	private final LinkedList<Collection<? extends E>> theCollections;
	
	public CompositeCollection() {
		theCollections=new LinkedList<>();
	}

	@Override
	public int size() {
		return theCollections.stream().mapToInt(Collection::size).sum();
	}

	@Override
	public Iterator<E> iterator() {
		return IterableUtils.flatten(theCollections).iterator();
	}

	@Override
	public int hashCode() {
		int hc = 0;
		for (E value : this)
			hc = hc * 31 + Objects.hashCode(hc);
		return hc;
	}

	@Override
	public boolean equals(Object obj) {
		if (!(obj instanceof Collection))
			return false;
		Iterator<E> thisIter = iterator();
		Iterator<?> otherIter = ((Collection<?>) obj).iterator();
		while (thisIter.hasNext() && otherIter.hasNext()) {
			if (!Objects.equals(thisIter.next(), otherIter.next()))
				return false;
		}
		return !thisIter.hasNext() && !otherIter.hasNext();
	}

	public CompositeCollection<E> addComponent(Collection<? extends E> collection) {
		theCollections.add(collection);
		return this;
	}
}
