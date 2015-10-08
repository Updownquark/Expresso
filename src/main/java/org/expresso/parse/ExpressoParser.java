package org.expresso.parse;

import java.util.*;

public class ExpressoParser<S extends BranchableStream<?, ?>> implements ParseMatcher<S> {
	public static final String IGNORABLE = "ignorable";
	public static final String WHITE_SPACE = "white-space";

	private final String theName;
	private final Set<String> theTags;

	private final Map<String, ParseMatcher<? super S>> theMatchers;

	private final Map<String, List<ParseMatcher<? super S>>> theMatchersByTag;

	private final Set<String> theTerminators;

	protected ExpressoParser(String name, Set<String> tags) {
		theName = name;
		theTags = Collections.unmodifiableSet(tags);

		theMatchers = new LinkedHashMap<>();
		theMatchersByTag = new LinkedHashMap<>();
		theTerminators = new LinkedHashSet<>();
	}

	@Override
	public String getName() {
		return theName;
	}

	@Override
	public Set<String> getTags() {
		return theTags;
	}

	@Override
	public Set<String> getExternalTypeDependencies() {
		return Collections.EMPTY_SET; // This parser is self-contained
	}

	private void addMatcher(ParseMatcher<? super S> matcher) {
		theMatchers.put(matcher.getName(), matcher);
		for(String tag : matcher.getTags()) {
			List<ParseMatcher<? super S>> taggedMatchers = theMatchersByTag.get(tag);
			if(taggedMatchers == null) {
				taggedMatchers = new ArrayList<>();
				theMatchersByTag.put(tag, taggedMatchers);
			}
			taggedMatchers.add(matcher);
		}
	}

	private void addTerminator(String terminator) {
		theTerminators.add(terminator);
	}

	public Collection<ParseMatcher<? super S>> getParsersFor(String... types) {
		Collection<ParseMatcher<? super S>> matchers;
		if(types.length > 0) {
			matchers = new LinkedHashSet<>();
			for(String type : types) {
				ParseMatcher<? super S> matcher = theMatchers.get(type);
				if(matcher != null)
					matchers.add(matcher);
				else {
					List<ParseMatcher<? super S>> taggedMatchers = theMatchersByTag.get(type);
					if(taggedMatchers != null)
						matchers.addAll(taggedMatchers);
				}
			}
		} else
			matchers = theMatchers.values();
		return Collections.unmodifiableCollection(matchers);
	}

	protected ParseSession createSession() {
	}

	public <SS extends S> ParseMatch<SS> parse(SS stream) {
		return parse(stream, createSession());
	}

	@Override
	public <SS extends S> ParseMatch<SS> parse(SS stream, ExpressoParser<? super SS> parser, ParseSession session) {
		return parse(stream, session, new String[0]);
	}

	public <SS extends S> ParseMatch<SS> parse(SS stream, ParseSession session, String... types) {
		// TODO Auto-generated method stub
	}

	public <SS extends S> Iterable<ParseMatch<SS>> matches(SS stream) {
		return new Iterable<ParseMatch<SS>>() {
			@Override
			public Iterator<ParseMatch<SS>> iterator() {
				return new Iterator<ParseMatch<SS>>() {
					@Override
					public boolean hasNext() {
						return !stream.isDiscovered() || stream.getDiscoveredLength() > 0;
					}

					@Override
					public ParseMatch<SS> next() {
						return parse(stream);
					}
				};
			}
		};
	}

	public static <S extends BranchableStream<?, ?>> Builder<S> build(String name) {
		return new Builder<>(name);
	}

	public static class Builder<S extends BranchableStream<?, ?>> {
		private final String theName;

		private final Set<String> theParserTags;

		private final Map<String, ParseMatcher<? super S>> theMatchers;

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

		public Builder<S> addMatcher(ExpressoParser<? super S> matcher) {
			ParseMatcher<? super S> previous = theMatchers.get(matcher.getName());
			if(previous != null) {
				throw new IllegalArgumentException(
					"Duplicate matchers named \"" + matcher.getName() + "\": " + previous + " and " + matcher);
			}
			theMatchers.put(matcher.getName(), matcher);
			for(String tag : matcher.getTags())
				theMatcherTags.add(tag);
			return this;
		}

		public Builder<S> addTerminator(String terminator) {
			if(!theTerminators.add(terminator))
				throw new IllegalArgumentException("Terminator \"" + terminator + "\" already added");
			return this;
		}

		public ExpressoParser<S> build() {
			List<String []> unmetDepends = new ArrayList<>();
			for(ParseMatcher<? super S> matcher : theMatchers.values()) {
				for(String depend : matcher.getExternalTypeDependencies())
					if(!theMatchers.containsKey(depend) && !theMatcherTags.contains(depend))
						unmetDepends.add(new String[] {matcher.getName(), depend});
			}
			if(!unmetDepends.isEmpty()) {
				StringBuilder error = new StringBuilder("The following matchers contain dependencies that are unknown to the parser:");
				for(String [] depend : unmetDepends)
					error.append('\n').append(depend[0]).append(": ").append(depend[1]);
				throw new IllegalStateException(error.toString());
			}

			ExpressoParser<S> ret = new ExpressoParser<>(theName, theParserTags);
			for(ParseMatcher<? super S> matcher : theMatchers.values())
				ret.addMatcher(matcher);
			for(String terminator : theTerminators)
				ret.addTerminator(terminator);
			return ret;
		}
	}
}
