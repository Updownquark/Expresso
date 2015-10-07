package org.expresso.parse.impl;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import org.expresso.parse.BranchableStream;
import org.expresso.parse.ParseMatcher;

public abstract class ComposedMatcher<S extends BranchableStream<?, ?>> extends BaseMatcher<S> {
	private List<ParseMatcher<? super S>> theComposed;

	protected ComposedMatcher(String name, Set<String> tags) {
		super(name, tags);
		theComposed = new ArrayList<>();
	}

	protected void addComposed(ParseMatcher<? super S> composed) {
		theComposed.add(composed);
	}

	@Override
	public Set<String> getExternalTypeDependencies() {
		LinkedHashSet<String> depends = new LinkedHashSet<>();
		for(ParseMatcher<? super S> element : theComposed)
			depends.addAll(element.getExternalTypeDependencies());
		return depends;
	}

	public List<ParseMatcher<? super S>> getComposed() {
		return java.util.Collections.unmodifiableList(theComposed);
	}
}
