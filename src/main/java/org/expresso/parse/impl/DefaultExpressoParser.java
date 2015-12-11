package org.expresso.parse.impl;

import java.io.IOException;
import java.util.*;
import java.util.function.Function;

import org.expresso.parse.*;
import org.expresso.parse.debug.ExpressoParsingDebugger;
import org.qommons.ex.ExIterator;

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
					else if ("default".equals(type))
						matchers.addAll(theDefaultMatchers);
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
			theDebugger.preParse(stream, matcher, session);
			ParseMatch<SS> fMatch = matcher.match((SS) stream.branch(), this, session);
			theDebugger.postParse(stream, matcher, fMatch);
			if(fMatch != null && fMatch.isBetter(match)) {
				if(match != null)
					theDebugger.matchDiscarded(match.getMatcher(), match);
				match = fMatch;
			} else
				theDebugger.matchDiscarded(matcher, fMatch);
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

	@Override
	public <SS extends BranchableStream<?, ?>> List<ParseMatch<SS>> parseMatchPaths(SS stream, ParseSession session,
			SimpleMatchParser<SS> parser, int minDepth, int maxDepth, ParseMatcher<? super SS> matcher, Function<Integer, String> error)
					throws IOException {
		List<List<ParseMatch<SS>>> paths = new ArrayList<>();
		SS copy = (SS) stream.branch();
		// Recursively parse each possible path of matches from the stream
		parseBranch(new ArrayList<>(), copy, session, parser, 0, maxDepth, paths);

		copy = (SS) stream.branch();
		List<ParseMatch<SS>> ret = new ArrayList<>(paths.size());
		for (int i = 0; i < ret.size(); i++) {
			List<ParseMatch<SS>> path = paths.get(i);
			String errorMsg;
			if (!path.isEmpty() && path.get(path.size() - 1).getError() != null)
				errorMsg = null; // Let the deeper message come up
			else if (path.size() < minDepth)
				errorMsg = error.apply(path.size());
			else
				errorMsg = null;
			int length = 0;
			for (ParseMatch<SS> el : path)
				length += el.getLength();
			ret.add(new ParseMatch<>(matcher, copy, length, path, errorMsg, path.size() == maxDepth));
		}
		return ret;
	}

	private <SS extends BranchableStream<?, ?>> void parseBranch(List<ParseMatch<SS>> path, SS stream, ParseSession session,
			org.expresso.parse.ExpressoParser.SimpleMatchParser<SS> parser, int depth, int maxDepth, List<List<ParseMatch<SS>>> allPaths)
					throws IOException {
		/* A small note on the use of the paths variable.  Although this method is designed to handle any number of sequential matchers each
		 * returning any number of matches, in practice most branches in this tree will be trivial and not stored in allPaths.  Therefore,
		 * to prevent unnecessary list creation, I am re-using the path variable and creating copies only as necessary. */
		ExIterator<ParseMatch<SS>, IOException> matchIterator = parser.parse(stream, session, depth).iterator();
		boolean needPathCopy = false;
		while (matchIterator.hasNext()) {
			ParseMatch<SS> match = matchIterator.next();
			if (match == null)
				continue;
			if (needPathCopy)
				path = new ArrayList<>(path);
			path.add(match);

			if (depth == maxDepth - 1 || !match.isComplete()) {
				needPathCopy = true;
				allPaths.add(path);
			} else {
				SS advanced = (SS) stream.branch().advance(match.getLength());
				int preSize = allPaths.size();
				parseBranch(path, advanced, session, parser, depth + 1, maxDepth, allPaths);
				if (allPaths.size() > preSize)
					needPathCopy = true; // The recursive call used the path variable and put it in allPaths, so we may need to create a
				// copy
				else
					path.remove(match); // Re-use the path variable
			}
		}
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
					if (!allMatchers.containsKey(depend) && !theMatcherTags.contains(depend) && !"default".equals(depend))
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
				ParseSession theEvaluatingSession;

				Matching(ParseSession session) {
					theEvaluatingSession = session;
				}
			}

			@SuppressWarnings("unused")
			private final int thePosition;

			private Map<String, Matching> theTypeMatching;

			StreamPositionData(int position) {
				thePosition = position;
				theTypeMatching = new HashMap<>();
			}

			ParseMatch<SS> match(SS stream, ParseSessionImpl<SS> session, Collection<? extends ParseMatcher<? super SS>> matchers)
					throws IOException {
				/* This is the core parsing code.  The basic strategy is to disallow recursive parsing, using the cache to allow
				 * matchers access to their own or others' matches.  This makes debugging much, much easier and makes the debugging process
				 * more controllable and, I think, faster.  Not sure what kind of code I'd have to write if I were allowing straight
				 * recursion, so I can't compare it to this; but the code I had to write here isn't real pretty.  I tried to make the code
				 * as easy to read as possible and document my logic in the comments as much as possible, but BEWARE of altering this code.
				 * It breaks parsing pretty much every time I add an optimization or a refactor.  Here there be dragons. */
				Set<String> uncachedTypeNames = new LinkedHashSet<>();
				for (ParseMatcher<? super SS> matcher : matchers) {
					Matching matching = theTypeMatching.get(matcher.getName());
					if (matching != null) {
						if (matching.theEvaluatingSession != null) {
							// Mark the session as having used cache elements that are still being evaluated so the results are not cached
							session.markUsedEvaulatingCache(matching.theEvaluatingSession, matcher);
						}
					} else {
						uncachedTypeNames.add(matcher.getName());
						theTypeMatching.put(matcher.getName(), new Matching(session));
					}
				}

				Map<String, ParseMatch<SS>> matches = new LinkedHashMap<>();
				Set<String> notToCache = new LinkedHashSet<>();
				ParseMatch<SS> match = null;
				boolean hadBetter;
				boolean loop = false;
				do {
					hadBetter = false;
					Iterator<? extends ParseMatcher<? super SS>> iter = matchers.iterator();
					while(iter.hasNext()) {
						ParseMatcher<? super SS> matcher = iter.next();
						ParseMatch<SS> match_i;
						if(uncachedTypeNames.contains(matcher.getName())) {
							theDebugger.preParse(stream, matcher, session);
							match_i = matcher.match((SS) stream.branch(), DefaultExpressoParser.this, session);
							theDebugger.postParse(stream, matcher, match_i);
							// If the matcher referred to one of the currently evaluating matchers, we need to keep iterating because this
							// cache may change as a result of each successive iteration
							loop |= session.hasUsedOwnEvaluatingCache();
							if (session.hasUsedEvaluatingCache() || hasCommon(session.getUsedOwnEvaluatingCache(), notToCache)) {
								// Don't want to cache if the evaluation used cache elements that are currently being evaluated by a
								// different session
								notToCache.add(matcher.getName());
							} else if (!session.hasUsedOwnEvaluatingCache()) {
								// The matcher didn't need anything that is currently being evaluated, so we can cache the result and not
								// evaluate it again
								Matching cached = theTypeMatching.get(matcher.getName());
								cached.theMatch = match_i;
								cached.theEvaluatingSession = null;
								iter.remove();
								uncachedTypeNames.remove(matcher.getName());
							}
							session.clearUsedEvaluatingCache();

							// Store the result in this frame
							ParseMatch<SS> oldMatch = matches.get(matcher.getName());
							if (match_i != null && match_i.isBetter(oldMatch)) {
								if (oldMatch != null)
									theDebugger.matchDiscarded(matcher, oldMatch);
								matches.put(matcher.getName(), match_i);
							} else
								theDebugger.matchDiscarded(matcher, match_i);
						} else {
							match_i = theTypeMatching.get(matcher.getName()).theMatch;
							theDebugger.usedCache(matcher, match_i);
							iter.remove();
						}

						// Replace the overall match if the new match is better
						if (match_i != null && match_i.isBetter(match)) {
							hadBetter = true;
							if (match != null)
								theDebugger.matchDiscarded(match.getMatcher(), match);
							match = match_i;
						} else
							theDebugger.matchDiscarded(matcher, match_i);
					}

					// After each round, save the matches in the cache
					for (String name : uncachedTypeNames)
						theTypeMatching.get(name).theMatch = matches.get(name);
				} while(loop && hadBetter);

				// Activate the cache elements that need to be cached, clear the ones that don't
				for (String name : notToCache) {
					theTypeMatching.remove(name);
					uncachedTypeNames.remove(name);
				}
				for (String name : uncachedTypeNames)
					theTypeMatching.get(name).theEvaluatingSession = null;
				return match;
			}

			boolean isCached(String type, ParseSession session) {
				Matching m = theTypeMatching.get(type);
				return m != null && m.theEvaluatingSession != session;
			}
		}

		private boolean hasCommon(Set<String> set1, Set<String> set2) {
			if (set1.size() < set2.size()) {
				for (String s : set1)
					if (set2.contains(s))
						return true;
			} else {
				for (String s : set2)
					if (set1.contains(s))
						return true;
			}
			return false;
		}

		private final HashMap<Integer, StreamPositionData> thePositions;

		ParsingCache() {
			thePositions = new HashMap<>();
		}

		ParseMatch<SS> match(SS stream, ParseSessionImpl<SS> session, Collection<? extends ParseMatcher<? super SS>> matchers)
				throws IOException {
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

		boolean isCached(String type, int position, ParseSession session) {
			StreamPositionData posData = thePositions.get(position);
			if(posData==null)
				return false;
			return posData.isCached(type, session);
		}
	}

	private class ParseSessionImpl<SS extends S> implements ParseSession {
		private final ParseSessionImpl<SS> theParent;
		private final ParsingCache<SS> theCache;
		private final Set<String> theExcludedTypes;
		private boolean usedEvaluatingCache;
		private Set<String> usedOwnEvaluatingCache;

		boolean isDebuggingStarted;

		boolean warnedOnlyExcludeIgnore;

		ParseSessionImpl(ParsingCache<SS> cache) {
			this(null, cache, false);
		}

		private ParseSessionImpl(ParseSessionImpl<SS> parent, ParsingCache<SS> cache, boolean alreadyStarted) {
			theParent = parent;
			theCache = cache;
			theExcludedTypes = new LinkedHashSet<>();
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

		@Override
		public boolean isCached(String type, BranchableStream<?, ?> stream) {
			return theCache.isCached(type, stream.getPosition(), this);
		}

		ParseMatch<SS> match(SS stream, Collection<? extends ParseMatcher<? super SS>> matchers) throws IOException {
			excludeTypes(matchers);
			return theCache.match(stream, new ParseSessionImpl<>(this, theCache, true), matchers);
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

		boolean hasUsedEvaluatingCache() {
			return usedEvaluatingCache;
		}

		boolean hasUsedOwnEvaluatingCache() {
			return usedOwnEvaluatingCache != null && !usedOwnEvaluatingCache.isEmpty();
		}

		Set<String> getUsedOwnEvaluatingCache() {
			return usedOwnEvaluatingCache == null ? Collections.EMPTY_SET : usedOwnEvaluatingCache;
		}

		void markUsedEvaulatingCache(ParseSession evaluatingSession, ParseMatcher<? super SS> matcher) {
			if (evaluatingSession == this) {
				if (usedOwnEvaluatingCache == null)
					usedOwnEvaluatingCache = new LinkedHashSet<>();
				usedOwnEvaluatingCache.add(matcher.getName());
				return;
			}
			usedEvaluatingCache = true;
			if (theParent != null)
				theParent.markUsedEvaulatingCache(evaluatingSession, matcher);
		}

		void clearUsedEvaluatingCache() {
			usedEvaluatingCache = false;
			if (usedOwnEvaluatingCache != null)
				usedOwnEvaluatingCache.clear();
		}
	}
}
