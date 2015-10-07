package org.expresso.parse.impl;

import java.util.*;

import org.expresso.parse.BranchableStream;
import org.expresso.parse.ExpressoParser;
import org.expresso.parse.ParseMatch;

public class CompositeParser<S extends BranchableStream<?, ?>> implements ExpressoParser<S> {
	private static class MatcherMetadata<S> {
		final ExpressoParser<? super S> matcher;

		final boolean isSubDomain;

		final boolean isIgnorable;

		MatcherMetadata(ExpressoParser<? super S> aMatcher, boolean subDomain, boolean ignorable) {
			this.matcher = aMatcher;
			isSubDomain = subDomain;
			isIgnorable = ignorable;
		}
	}

	private final String theName;
	private final Set<String> theTags;

	private final Map<String, MatcherMetadata<S>> theMatchers;
	private final Map<String, List<MatcherMetadata<S>>> theMatchersByTag;
	private final Set<String> theTerminators;

	protected CompositeParser(String name, Set<String> tags) {
		theName = name;
		theTags = Collections.unmodifiableSet(tags);

		theMatchers = new LinkedHashMap<>();
		theMatchersByTag = new LinkedHashMap<>();
		theTerminators = new LinkedHashSet<>();
	}

	private void addMatcher(ExpressoParser<? super S> matcher, boolean subDomain, boolean ignorable) {
		MatcherMetadata<S> met = new MatcherMetadata<>(matcher, subDomain, ignorable);
		theMatchers.put(matcher.getName(), met);
		for(String tag : matcher.getTags()) {
			List<MatcherMetadata<S>> taggedMatchers = theMatchersByTag.get(tag);
			if(taggedMatchers == null) {
				taggedMatchers = new ArrayList<>();
				theMatchersByTag.put(tag, taggedMatchers);
			}
			taggedMatchers.add(met);
		}
	}

	@Override
	public String getName() {
		return theName;
	}

	@Override
	public Set<String> getTags() {
		return theTags;
	}

	private void addTerminator(String terminator) {
		theTerminators.add(terminator);
	}

	@Override
	public <SS extends S> ParseMatch<SS> parse(SS stream, ExpressoParser<? super SS> parser) {
		return this.<SS> parse(stream, parser, new String[0]);
	}

	@Override
	public <SS extends S> ParseMatch<SS> parse(SS stream, String... types) {
		return parse(stream, this, types);
	}

	private <SS extends S> ParseMatch<SS> parse(SS stream, ExpressoParser<? super SS> parser, String... types) {
		Collection<MatcherMetadata<S>> matchers;
		if(types.length > 0) {
			matchers = new ArrayList<>();
			for(String type : types) {
				MatcherMetadata<S> matcher = theMatchers.get(type);
				if(matcher != null)
					matchers.add(matcher);
				else {
					List<MatcherMetadata<S>> taggedMatchers = theMatchersByTag.get(type);
					if(taggedMatchers != null)
						matchers.addAll(taggedMatchers);
				}
			}
		} else
			matchers = theMatchers.values();

		// TODO Auto-generated method stub
		return null;
	}

	public static <S extends BranchableStream<?, ?>> Builder<S> build(String name) {
		return new Builder<>(name);
	}

	public static class Builder<S extends BranchableStream<?, ?>> {
		private final String theName;
		private final Set<String> theParserTags;

		private final Map<String, MatcherMetadata<S>> theMatchers;
		private final Set<String> theMatcherTags;
		private final Set<String> theTerminators;

		Builder(String name) {
			theName = name;
			theParserTags = new LinkedHashSet<>();
			theMatchers = new LinkedHashMap<>();
			theMatcherTags = new LinkedHashSet<>();
			theTerminators = new LinkedHashSet<>();
		}

		public Builder<S> tag(String... tags) {
			for(String tag : tags)
				if(!theParserTags.add(tag))
					throw new IllegalArgumentException("Already tagged with \"" + tag + "\"");
			return this;
		}

		public Builder<S> recognizeMatcherTag(String... matcherTags) {
			for(String matcherTag : matcherTags)
				theMatcherTags.add(matcherTag);
			return this;
		}

		private Builder<S> add(ExpressoParser<? super S> matcher, boolean subDomain, boolean ignorable) {
			MatcherMetadata<S> previous = theMatchers.get(matcher.getName());
			if(previous != null) {
				if(previous.isSubDomain) {
					if(subDomain)
						throw new IllegalArgumentException(
							"Duplicate sub-domain parsers named \"" + matcher.getName() + "\": " + previous.matcher + " and " + matcher);
					else
						throw new IllegalArgumentException("Matcher named \"" + matcher.getName()
						+ " conflicts with sub-domain parser of the same name: " + previous.matcher);
				} else {
					if(subDomain)
						throw new IllegalArgumentException("Sub-domain parser named \"" + matcher.getName()
						+ " conflicts with matcher of the same name: " + previous.matcher);
					else
						throw new IllegalArgumentException(
							"Duplicate matchers named \"" + matcher.getName() + "\": " + previous + " and " + matcher);
				}
			}
			theMatchers.put(matcher.getName(), new MatcherMetadata<>(matcher, subDomain, ignorable));
			for(String tag : matcher.getTags())
				theMatcherTags.add(tag);
			return this;
		}

		public Builder<S> addMatcher(ExpressoParser<? super S> matcher) {
			return add(matcher, false, false);
		}

		public Builder<S> addSubDomain(ExpressoParser<? super S> parser) {
			return add(parser, true, false);
		}

		public Builder<S> addIgnorable(ExpressoParser<? super S> matcher) {
			return add(matcher, false, true);
		}

		public Builder<S> addTerminator(String terminator) {
			if(!theTerminators.add(terminator))
				throw new IllegalArgumentException("Terminator \"" + terminator + "\" already added");
			return this;
		}

		public CompositeParser<S> build() {
			List<String []> unmetDepends = new ArrayList<>();
			for(MatcherMetadata<S> matcher : theMatchers.values()) {
				for(String depend : matcher.matcher.getExternalTypeDependencies())
					if(!theMatchers.containsKey(depend) && !theMatcherTags.contains(depend))
						unmetDepends.add(new String[] {matcher.matcher.getName(), depend});
			}
			if(!unmetDepends.isEmpty()) {
				StringBuilder error = new StringBuilder("The following matchers contain dependencies that are unknown to the parser:");
				for(String [] depend : unmetDepends)
					error.append('\n').append(depend[0]).append(": ").append(depend[1]);
				throw new IllegalStateException(error.toString());
			}

			CompositeParser<S> ret = new CompositeParser<>(theName, theParserTags);
			for(MatcherMetadata<S> matcher : theMatchers.values())
				ret.addMatcher(matcher.matcher, matcher.isSubDomain, matcher.isIgnorable);
			for(String terminator : theTerminators)
				ret.addTerminator(terminator);
			return ret;
		}
	}
}
