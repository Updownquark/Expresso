package org.expresso.parse.impl;

import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.TreeMap;

import org.expresso.parse.BranchableStream;
import org.expresso.parse.ExpressoParser;
import org.expresso.parse.ParseMatch;
import org.expresso.parse.ParseMatcher;

public class CompositeParser<S extends BranchableStream<?, ?, S>> implements ExpressoParser<S> {
	private final String theName;

	private final Map<String, ParseMatcher<? super S>> theMatchers;
	private final Map<String, ExpressoParser<? super S>> theSubDomains;

	protected CompositeParser(String name, Collection<ParseMatcher<? super S>> matchers,
		Collection<ExpressoParser<? super S>> subDomains) {
		theName = name;
		theMatchers = new TreeMap<>();
		theSubDomains = new TreeMap<>();

		for(ParseMatcher<? super S> matcher : matchers)
			theMatchers.put(matcher.getName(), matcher);
		for(ExpressoParser<? super S> subDomain : subDomains)
			theSubDomains.put(subDomain.getName(), subDomain);
	}

	@Override
	public String getName() {
		return theName;
	}

	@Override
	public ParseMatch parse(S stream, ExpressoParser<? super S> parser) {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public Iterable<ParseMatch> matches(S stream) {
		// TODO Auto-generated method stub
		return null;
	}

	public static class Builder<S extends BranchableStream<?, ?, S>> {
		private final String theName;

		private final Map<String, ParseMatcher<? super S>> theMatchers;
		private final Map<String, ExpressoParser<? super S>> theSubDomains;

		Builder(String name) {
			theName = name;
			theMatchers = new LinkedHashMap<>();
			theSubDomains = new LinkedHashMap<>();
		}

		public Builder<S> addMatcher(ParseMatcher<? super S> matcher) {
			ParseMatcher<? super S> previous = theMatchers.get(matcher.getName());
			if(previous != null)
				throw new IllegalArgumentException(
					"Duplicate matchers named \"" + matcher.getName() + "\": " + previous + " and " + matcher);
			ExpressoParser<? super S> parser = theSubDomains.get(matcher.getName());
			if(parser != null)
				throw new IllegalArgumentException(
					"Matcher named \"" + matcher.getName() + " conflicts with sub-domain parser of the same name: " + parser);
			theMatchers.put(matcher.getName(), matcher);
			return this;
		}

		public Builder<S> addSubDomain(ExpressoParser<S> parser) {
			ExpressoParser<? super S> previous = theSubDomains.get(parser.getName());
			if(previous != null)
				throw new IllegalArgumentException("Duplicate matchers named \"" + parser.getName() + "\": " + previous + " and " + parser);
			ParseMatcher<? super S> matcher = theMatchers.get(parser.getName());
			if(matcher != null)
				throw new IllegalArgumentException(
					"Sub-domain parser named \"" + parser.getName() + " conflicts with matcher of the same name: " + matcher);
			theSubDomains.put(parser.getName(), parser);
			return this;
		}

		public CompositeParser<S> build() {
			CompositeParser<S> ret = new CompositeParser<>(theName, theMatchers.values(), theSubDomains.values());
			return ret;
		}
	}

	public static <S extends BranchableStream<?, ?, S>> Builder<S> build(String name) {
		return new Builder<>(name);
	}
}
