package org.expresso.parse;

import java.util.*;

public class ExpressoParser<S extends BranchableStream<?, ?>> implements ParseMatcher<S> {
	public static final String IGNORABLE = "ignorable";
	public static final String WHITE_SPACE = "white-space";

	private final String theName;
	private final Set<String> theTags;

	private final Map<String, ParseMatcher<? super S>> allMatchers;
	private final Map<String, List<ParseMatcher<? super S>>> theMatchersByTag;

	private final List<ParseMatcher<? super S>> theDefaultMatchers;

	private final Set<String> theTerminators;

	protected ExpressoParser(String name, Set<String> tags) {
		theName = name;
		theTags = Collections.unmodifiableSet(tags);

		allMatchers = new java.util.LinkedHashMap<>();
		theMatchersByTag = new LinkedHashMap<>();
		theDefaultMatchers = new ArrayList<>();
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

	private void addMatcher(ParseMatcher<? super S> matcher, boolean isDefault) {
		allMatchers.put(matcher.getName(), matcher);
		if(isDefault)
			theDefaultMatchers.add(matcher);
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

	public Collection<ParseMatcher<? super S>> getMatchersFor(String... types) {
		Collection<ParseMatcher<? super S>> matchers;
		if(types.length > 0) {
			matchers = new LinkedHashSet<>();
			for(String type : types) {
				ParseMatcher<? super S> matcher = allMatchers.get(type);
				if(matcher != null)
					matchers.add(matcher);
				else {
					List<ParseMatcher<? super S>> taggedMatchers = theMatchersByTag.get(type);
					if(taggedMatchers != null)
						matchers.addAll(taggedMatchers);
				}
			}
		} else
			matchers = theDefaultMatchers;
		return Collections.unmodifiableCollection(matchers);
	}

	protected ParseSession createSession() {
		return new ParseSessionImpl<>();
	}

	public <SS extends S> ParseMatch<SS> parse(SS stream) {
		return parse(stream, createSession(), new String[0]);
	}

	@Override
	public <SS extends S> ParseMatch<SS> match(SS stream, ExpressoParser<? super SS> parser, ParseSession session) {
		return parse(stream, session, new String[0]);
	}

	public <SS extends S> ParseMatch<SS> parse(SS stream, ParseSession session, String... types) {
		return parse(stream, session, getMatchersFor(types));
	}

	public <SS extends S> ParseMatch<SS> parse(SS stream, ParseSession session, ParseMatcher<? super SS>... matchers) {
		return parse(stream, session, Arrays.asList(matchers));
	}

	public <SS extends S> ParseMatch<SS> parse(SS stream, ParseSession session, Collection<? extends ParseMatcher<? super SS>> matchers) {
		Collection<ParseMatcher<? super SS>> memberMatchers = new ArrayList<>();
		Collection<ParseMatcher<? super SS>> foreignMatchers = new ArrayList<>();
		for(ParseMatcher<? super SS> matcher : matchers) {
			if(matcher.getName() == null || allMatchers.get(matcher.getName()) != matcher)
				foreignMatchers.add(matcher);
			else
				memberMatchers.add(matcher);
		}
		ParseMatch<SS> match = null;
		if(!memberMatchers.isEmpty())
			match = ((ParseSessionImpl<SS>) session).match(stream, memberMatchers);
		for(ParseMatcher<? super SS> matcher : foreignMatchers) {
			ParseMatch<SS> fMatch = matcher.match((SS) stream.branch(), this, session);
			if(fMatch != null && fMatch.isBetter(match))
				match = fMatch;
		}
		if(match != null)
			stream.advance(match.getLength());
		return match;
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

		private final Map<String, ParseMatcher<? super S>> allMatchers;

		private final Set<String> theDefaultMatchers;

		private final Set<String> theMatcherTags;

		private final Set<String> theTerminators;

		Builder(String name) {
			theName = name;
			theParserTags = new LinkedHashSet<>();
			allMatchers = new LinkedHashMap<>();
			theMatcherTags = new LinkedHashSet<>();
			theDefaultMatchers = new LinkedHashSet<>();
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

		public Builder<S> addMatcher(ExpressoParser<? super S> matcher, boolean isDefault) {
			ParseMatcher<? super S> previous = allMatchers.get(matcher.getName());
			if(previous != null) {
				throw new IllegalArgumentException(
					"Duplicate matchers named \"" + matcher.getName() + "\": " + previous + " and " + matcher);
			}
			allMatchers.put(matcher.getName(), matcher);
			for(String tag : matcher.getTags())
				theMatcherTags.add(tag);
			if(isDefault)
				theDefaultMatchers.add(matcher.getName());
			return this;
		}

		public Builder<S> addTerminator(String terminator) {
			if(!theTerminators.add(terminator))
				throw new IllegalArgumentException("Terminator \"" + terminator + "\" already added");
			return this;
		}

		public ExpressoParser<S> build() {
			List<String []> unmetDepends = new ArrayList<>();
			for(ParseMatcher<? super S> matcher : allMatchers.values()) {
				for(String depend : matcher.getExternalTypeDependencies())
					if(!allMatchers.containsKey(depend) && !theMatcherTags.contains(depend))
						unmetDepends.add(new String[] {matcher.getName(), depend});
			}
			if(!unmetDepends.isEmpty()) {
				StringBuilder error = new StringBuilder("The following matchers contain dependencies that are unknown to the parser:");
				for(String [] depend : unmetDepends)
					error.append('\n').append(depend[0]).append(": ").append(depend[1]);
				throw new IllegalStateException(error.toString());
			}

			ExpressoParser<S> ret = new ExpressoParser<>(theName, theParserTags);
			for(ParseMatcher<? super S> matcher : allMatchers.values())
				ret.addMatcher(matcher, theDefaultMatchers.contains(matcher.getName()));
			for(String terminator : theTerminators)
				ret.addTerminator(terminator);
			return ret;
		}
	}

	private class ParseSessionImpl<SS extends S> implements ParseSession {
		private class StreamPositionData {
			private class Matching {
				ParseMatch<SS> theMatch;
			}

			@SuppressWarnings("unused")
			private final int thePosition;

			private Map<String, Matching> theTypeMatching;

			StreamPositionData(int position) {
				thePosition = position;
				theTypeMatching = new HashMap<>();
			}

			ParseMatch<SS> match(SS stream, ParseMatcher<? super SS> matcher) {
				Matching matching=theTypeMatching.get(matcher.getName());
				if(matching!=null)
					return matching.theMatch;
				matching = new Matching();
				theTypeMatching.put(matcher.getName(), matching);
				while(true) {
					ParseMatch<SS> match = matcher.match(stream, ExpressoParser.this, ParseSessionImpl.this);
					if(match != null && match.isBetter(matching.theMatch))
						matching.theMatch = match;
					else
						break;
				}
				return matching.theMatch;
			}

			ParseMatch<SS> match(SS stream, Collection<? extends ParseMatcher<? super SS>> matchers) {
				ParseMatch<SS> match = null;
				for(ParseMatcher<? super SS> matcher : matchers) {
					ParseMatch<SS> match_i = match(stream, matcher);
					if(match_i != null && match_i.isBetter(match))
						match = match_i;
				}
				return match;
			}
		}

		private final HashMap<Integer, StreamPositionData> thePositions;

		ParseSessionImpl() {
			thePositions = new HashMap<>();
		}

		ParseMatch<SS> match(SS stream, Collection<? extends ParseMatcher<? super SS>> matchers) {
			int pos = stream.getPosition();
			StreamPositionData posData = thePositions.get(pos);
			if(posData == null) {
				posData = new StreamPositionData(pos);
				thePositions.put(pos, posData);
			}
			return posData.match(stream, matchers);
		}
	}
}
