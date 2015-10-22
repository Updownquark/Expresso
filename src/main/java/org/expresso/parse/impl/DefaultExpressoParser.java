package org.expresso.parse.impl;

import java.io.IOException;
import java.util.*;

import org.expresso.parse.*;

/**
 * Default implementation of {@link ExpressoParser}
 *
 * @param <S> The type of stream that this parser can parse
 */
public class DefaultExpressoParser<S extends BranchableStream<?, ?>> extends BaseMatcher<S> implements ExpressoParser<S> {
	private final Map<String, ParseMatcher<? super S>> allMatchers;
	private final Map<String, List<ParseMatcher<? super S>>> theMatchersByTag;
	private final List<ParseMatcher<? super S>> theDefaultMatchers;

	/**
	 * Initializes the parser with a name and tags. These are only used if this parser is a sub-domain matcher in another parser.
	 *
	 * @param name The name for this parser
	 * @param tags The tags for this parser
	 */
	protected DefaultExpressoParser(String name, Set<String> tags) {
		super(name, tags);

		allMatchers = new java.util.LinkedHashMap<>();
		theMatchersByTag = new LinkedHashMap<>();
		theDefaultMatchers = new ArrayList<>();
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

	@Override
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

	@Override
	public ParseSession createSession() {
		return new ParseSessionImpl<>();
	}

	@Override
	public <SS extends S> ParseMatch<SS> parse(SS stream, ParseSession session, Collection<? extends ParseMatcher<? super SS>> matchers)
		throws IOException {
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

	/**
	 * @param <S> The type of stream to parse
	 * @param name The name for the parser
	 * @return A builder capable of constructing a {@link DefaultExpressoParser}
	 */
	public static <S extends BranchableStream<?, ?>> Builder<S> build(String name) {
		return new Builder<>(name);
	}

	/**
	 * Builds {@link DefaultExpressoParser}s. See {@link DefaultExpressoParser#build(String)}.
	 *
	 * @param <S> The type of stream for the builder to parse
	 */
	public static class Builder<S extends BranchableStream<?, ?>> {
		private final String theName;
		private final Set<String> theParserTags;

		private final Map<String, ParseMatcher<? super S>> allMatchers;
		private final Set<String> theDefaultMatchers;
		private final Set<String> theMatcherTags;

		Builder(String name) {
			theName = name;
			theParserTags = new LinkedHashSet<>();
			allMatchers = new LinkedHashMap<>();
			theMatcherTags = new LinkedHashSet<>();
			theDefaultMatchers = new LinkedHashSet<>();
		}

		/**
		 * Adds the given tags to the parser to be built
		 *
		 * @param tags The tags to tag the parser with
		 * @return This builder, for chaining
		 */
		public Builder<S> tag(String... tags) {
			for(String tag : tags)
				if(!theParserTags.add(tag))
					throw new IllegalArgumentException("Already tagged with \"" + tag + "\"");
			return this;
		}

		/**
		 * Defines one or more tags that this builder will recognize
		 *
		 * @param matcherTags The matcher tags to recognize (not throw errors on when building)
		 * @return This builder, for chaining
		 */
		public Builder<S> recognizeMatcherTag(String... matcherTags) {
			for(String matcherTag : matcherTags)
				theMatcherTags.add(matcherTag);
			return this;
		}

		/**
		 * Adds a matcher to the parser to be built
		 *
		 * @param matcher The matcher to add
		 * @param isDefault Whether to use the matcher by default (i.e. when not referred to explicitly by name or tag). See
		 *            {@link ExpressoParser#parseWith(BranchableStream, ParseSession, ParseMatcher...)}.
		 * @return This builder, for chaining
		 */
		public Builder<S> addMatcher(ParseMatcher<? super S> matcher, boolean isDefault) {
			ParseMatcher<? super S> previous = allMatchers.get(matcher.getName());
			if(previous != null)
				throw new IllegalArgumentException(
					"Duplicate matchers named \"" + matcher.getName() + "\": " + previous + " and " + matcher);
			if(theMatcherTags.contains(matcher.getName()))
				throw new IllegalArgumentException("Matcher \"" + matcher.getName() + "\" has the same name as a tag");
			for(String tag : matcher.getTags()) {
				if(allMatchers.containsKey(tag))
					throw new IllegalArgumentException(
						"Matcher \"" + matcher.getName() + "\" uses a tag (\"" + tag + "\") that is the same as the" + "name of a matcher");
			}
			allMatchers.put(matcher.getName(), matcher);
			for(String tag : matcher.getTags())
				theMatcherTags.add(tag);
			if(isDefault)
				theDefaultMatchers.add(matcher.getName());
			return this;
		}

		/**
		 * Builds the parser
		 *
		 * @return The new parser
		 */
		public DefaultExpressoParser<S> build() {
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

			DefaultExpressoParser<S> ret = new DefaultExpressoParser<>(theName, theParserTags);
			for(ParseMatcher<? super S> matcher : allMatchers.values())
				ret.addMatcher(matcher, theDefaultMatchers.contains(matcher.getName()));
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

			ParseMatch<SS> match(SS stream, ParseMatcher<? super SS> matcher) throws IOException {
				Matching matching = theTypeMatching.get(matcher.getName());
				if(matching != null)
					return matching.theMatch;
				matching = new Matching();
				theTypeMatching.put(matcher.getName(), matching);
				while(true) {
					ParseMatch<SS> match = matcher.match((SS) stream.branch(), DefaultExpressoParser.this, ParseSessionImpl.this);
					if(match != null && match.isBetter(matching.theMatch)) {
						matching.theMatch = match;
						if(matcher.getExternalTypeDependencies().isEmpty())
							break;
					} else
						break;
				}
				return matching.theMatch;
			}

			ParseMatch<SS> match(SS stream, Collection<? extends ParseMatcher<? super SS>> matchers) throws IOException {
				ParseMatch<SS> match = null;
				for(ParseMatcher<? super SS> matcher : matchers) {
					ParseMatch<SS> match_i = match(stream, matcher);
					if(match_i != null && match_i.isBetter(match))
						match = match_i;
				}
				return match;
			}
		}

		private final Set<String> theExcludedTypes;

		private final HashMap<Integer, StreamPositionData> thePositions;

		ParseSessionImpl() {
			theExcludedTypes = new java.util.LinkedHashSet<>();
			thePositions = new HashMap<>();
		}

		@Override
		public boolean excludeType(String type) {
			return theExcludedTypes.add(type);
		}

		@Override
		public boolean includeType(String type) {
			return theExcludedTypes.remove(type);
		}

		ParseMatch<SS> match(SS stream, Collection<? extends ParseMatcher<? super SS>> matchers) throws IOException {
			excludeTypes(matchers);
			if(matchers.isEmpty())
				return null;
			int pos = stream.getPosition();
			StreamPositionData posData = thePositions.get(pos);
			if(posData == null) {
				posData = new StreamPositionData(pos);
				thePositions.put(pos, posData);
			}
			return posData.match(stream, matchers);
		}

		private void excludeTypes(Collection<? extends ParseMatcher<? super SS>> matchers) {
			Iterator<? extends ParseMatcher<? super SS>> matcherIter = matchers.iterator();
			while(matcherIter.hasNext()) {
				ParseMatcher<? super SS> matcher = matcherIter.next();
				if(matcher.getName() != null && theExcludedTypes.contains(matcher.getName())) {
					matcherIter.remove();
					continue;
				}
				for(String tag : matcher.getTags())
					if(theExcludedTypes.contains(tag)) {
						matcherIter.remove();
						break;
					}
			}
		}
	}
}
