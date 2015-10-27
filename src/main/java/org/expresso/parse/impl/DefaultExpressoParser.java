package org.expresso.parse.impl;

import java.io.IOException;
import java.util.*;
import java.util.stream.Collectors;

import org.expresso.parse.*;
import org.expresso.parse.debug.ExpressoParsingDebugger;

/**
 * Default implementation of {@link ExpressoParser}
 *
 * @param <S> The type of stream that this parser can parse
 */
public class DefaultExpressoParser<S extends BranchableStream<?, ?>> extends BaseMatcher<S> implements ExpressoParser<S> {
	private final Map<String, ParseMatcher<? super S>> allMatchers;
	private final Map<String, List<ParseMatcher<? super S>>> theMatchersByTag;
	private final List<ParseMatcher<? super S>> theDefaultMatchers;

	private ExpressoParsingDebugger<S> theDebugger;

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

		theDebugger = new org.expresso.parse.debug.NullDebugger<>();
	}

	/** @param debugger The debugger to be notified of this parser's parsing steps */
	public void setDebugger(ExpressoParsingDebugger<S> debugger) {
		if(debugger == null)
			debugger = new org.expresso.parse.debug.NullDebugger<>();
		theDebugger = debugger;
		debugger.init(this);
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
	public Collection<ParseMatcher<? super S>> getMatchersFor(ParseSession session, String... types) {
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
			matchers = new ArrayList<>(theDefaultMatchers);
		ParseSessionImpl<?> sessImpl = (ParseSessionImpl<?>) session;
		if(sessImpl != null && !sessImpl.theExcludedTypes.isEmpty())
			ExpressoParser.removeTypes(matchers, sessImpl.theExcludedTypes);
		return Collections.unmodifiableCollection(matchers);
	}

	@Override
	public List<ParseMatcher<? super S>> getComposed() {
		return Collections.unmodifiableList(new ArrayList<>(allMatchers.values()));
	}

	@Override
	public ParseSession createSession() {
		return new ParseSessionImpl<>(new ParsingCache<>());
	}

	@Override
	public <SS extends S> ParseMatch<SS> parse(SS stream, ParseSession session, Collection<? extends ParseMatcher<? super SS>> matchers)
		throws IOException {
		boolean finishDebugging = false;
		if(!((ParseSessionImpl<S>) session).isDebuggingStarted) {
			theDebugger.start(stream);
			((ParseSessionImpl<S>) session).isDebuggingStarted = true;
			finishDebugging = true;
		}
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
			theDebugger.preParse(stream, matcher);
			ParseMatch<SS> fMatch = matcher.match((SS) stream.branch(), this, session);
			theDebugger.postParse(stream, matcher, fMatch);
			if(fMatch != null && fMatch.isBetter(match)) {
				if(match != null)
					theDebugger.matchDiscarded(match);
				match = fMatch;
			} else if(fMatch != null)
				theDebugger.matchDiscarded(fMatch);
		}

		if(match != null)
			stream.advance(match.getLength());
		if(finishDebugging) {
			if(match == null || match.getError() != null || !match.isComplete())
				theDebugger.fail(stream, match);
			else
				theDebugger.end(match);
			((ParseSessionImpl<S>) session).isDebuggingStarted = false;
		}
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
			isDefault &= !matcher.getTags().contains(IGNORABLE);
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

	private class ParsingCache<SS extends S> {
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

			ParseMatch<SS> match(SS stream, ParseSession session, Collection<? extends ParseMatcher<? super SS>> matchers)
				throws IOException {
				Set<ParseMatcher<? super SS>> uncached = new java.util.LinkedHashSet<>();
				Set<ParseMatcher<? super SS>> cached = new java.util.LinkedHashSet<>();
				for(ParseMatcher<? super SS> matcher : matchers) {
					if(theTypeMatching.containsKey(matcher.getName()))
						cached.add(matcher);
					else
						uncached.add(matcher);
				}
				Set<String> uncachedTypeNames = uncached.stream().map(ParseMatcher::getName).collect(Collectors.toSet());
				boolean loop = false;
				/* If a match with one any of these parsers may also be the first component in a match with the same or a different one in
				 * this set, then we need to parse repeatedly until it can't create a further composition.  This is the case with operators
				 * like addition, where a+b+c needs to be ((a+b)+c). */
				for(ParseMatcher<? super SS> matcher : uncached) {
					// Keep the parser from descending deep into unnecessary recursive matching. Do it iteratively instead.
					theTypeMatching.put(matcher.getName(), new Matching());
					if(!loop)
						loop |= containsAny(matcher.getPotentialBeginningTypeReferences(DefaultExpressoParser.this, session),
							uncachedTypeNames);
				}

				ParseMatch<SS> match = null;
				// For the cached matchers, just get the best cached match
				for(ParseMatcher<? super SS> matcher : cached) {
					ParseMatch<SS> match_i = theTypeMatching.get(matcher.getName()).theMatch;
					theDebugger.usedCache(matcher, match_i);
					if(match_i != null && match_i.isBetter(match)) {
						if(match != null)
							theDebugger.matchDiscarded(match);
						match = match_i;
					}
				}
				// See if we can beat the best cached match with the uncached matchers
				boolean hadBetter;
				do {
					hadBetter = false;
					Iterator<ParseMatcher<? super SS>> iter = uncached.iterator();
					while(iter.hasNext()) {
						ParseMatcher<? super SS> matcher = iter.next();
						Matching matching = theTypeMatching.get(matcher.getName());

						theDebugger.preParse(stream, matcher);
						ParseMatch<SS> match_i = matcher.match((SS) stream.branch(), DefaultExpressoParser.this, session);
						theDebugger.postParse(stream, matcher, match_i);

						// Cache the result
						if(match_i != null && match_i.isBetter(matching.theMatch)) {
							if(matching.theMatch != null)
								theDebugger.matchDiscarded(matching.theMatch);
							matching.theMatch = match_i;

							// If we beat the cached match, maybe we'll beat the overall match
							if(match_i != null && match_i.isBetter(match)) {
								hadBetter = true;
								if(match != null)
									theDebugger.matchDiscarded(match);
								match = match_i;
							}
						} else {
							if(match_i != null)
								theDebugger.matchDiscarded(match_i);
							iter.remove();
						}
					}
				} while(loop && hadBetter);
				return match;
			}

			private boolean containsAny(Set<String> refs, Set<String> typeNames) {
				for(String type : typeNames)
					if(refs.contains(type))
						return true;
				return false;
			}
		}

		private final HashMap<Integer, StreamPositionData> thePositions;

		ParsingCache() {
			thePositions = new HashMap<>();
		}

		ParseMatch<SS> match(SS stream, ParseSession session, Collection<? extends ParseMatcher<? super SS>> matchers) throws IOException {
			if(matchers.isEmpty())
				return null;
			int pos = stream.getPosition();
			StreamPositionData posData = thePositions.get(pos);
			if(posData == null) {
				posData = new StreamPositionData(pos);
				thePositions.put(pos, posData);
			}
			return posData.match(stream, session, matchers);
		}
	}

	private class ParseSessionImpl<SS extends S> implements ParseSession {
		private final ParsingCache<SS> theCache;
		private final Set<String> theExcludedTypes;

		boolean isDebuggingStarted;

		boolean warnedOnlyExcludeIgnore;

		ParseSessionImpl(ParsingCache<SS> cache) {
			this(cache, false);
		}

		private ParseSessionImpl(ParsingCache<SS> cache, boolean alreadyStarted) {
			theCache = cache;
			theExcludedTypes = new java.util.LinkedHashSet<>();
			isDebuggingStarted = alreadyStarted;
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
			return theCache.match(stream, new ParseSessionImpl<>(theCache, true), matchers);
		}

		private void excludeTypes(Collection<? extends ParseMatcher<? super SS>> matchers) {
			Iterator<? extends ParseMatcher<? super SS>> matcherIter = matchers.iterator();
			while(matcherIter.hasNext()) {
				ParseMatcher<? super SS> matcher = matcherIter.next();
				String removeType = null;
				if(matcher.getName() != null && theExcludedTypes.contains(matcher.getName()))
					removeType = matcher.getName();
				if(removeType == null) {
					for(String tag : matcher.getTags())
						if(theExcludedTypes.contains(tag)) {
							removeType = tag;
							break;
						}
				}
				if(removeType != null) {
					if(!matcher.getName().equals(IGNORABLE) && !matcher.getTags().contains(IGNORABLE)) {
						if(!warnedOnlyExcludeIgnore) {
							System.err.println("Session-excluded type \"" + removeType + "\" would exclude non-ignorable type \""
								+ matcher.getName() + "\". Only ignorable types may be session-excluded (e.g. via <without>).");
							warnedOnlyExcludeIgnore = true;
						}
					} else
						matcherIter.remove();
				}
			}
		}
	}
}
