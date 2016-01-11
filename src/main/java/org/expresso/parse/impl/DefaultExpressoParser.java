package org.expresso.parse.impl;

import java.io.IOException;
import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.expresso.parse.*;
import org.expresso.parse.debug.ExpressoParsingDebugger;
import org.qommons.ex.ExIterable;
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

	@Override
	public DefaultExpressoParser<S> setDebugger(ExpressoParsingDebugger<S> debugger) {
		if(debugger == null)
			debugger = new org.expresso.parse.debug.NullDebugger<>();
		theDebugger = debugger;
		debugger.init(this);
		return this;
	}

	@Override
	public ExpressoParsingDebugger<S> getDebugger() {
		return theDebugger;
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
	public <SS extends S> ExIterable<ParseMatch<SS>, IOException> parse(SS stream, ParseSession session,
			Collection<? extends ParseMatcher<? super SS>> matchers) {
		Collection<ParseMatcher<? super SS>> memberMatchers = new ArrayList<>();
		Collection<ParseMatcher<? super SS>> foreignMatchers = new ArrayList<>();
		for(ParseMatcher<? super SS> matcher : matchers) {
			if(matcher.getName() == null || allMatchers.get(matcher.getName()) != matcher)
				foreignMatchers.add(matcher);
			else
				memberMatchers.add(matcher);
		}
		ParseSessionImpl<SS> sessionImpl = (ParseSessionImpl<SS>) session;
		MatchResultIterable<SS> matches = new MatchResultIterable<>(stream, sessionImpl);
		if(!memberMatchers.isEmpty())
			matches.add(sessionImpl.match(stream, memberMatchers));
		if(!foreignMatchers.isEmpty()){
			Function<ParseMatcher<? super SS>, ParseMatcherState<SS>> map;
			map = matcher -> new ParseMatcherState<SS>(session, matcher,
					matcher.match((SS) stream.branch(), this, session).map(match -> new ParseMatchResult<>(match, false)));
			matches.add(foreignMatchers.stream().map(map).collect(Collectors.toList()));
		}
		return matches;
	}

	@Override
	public <SS extends BranchableStream<?, ?>> ExIterable<ParseMatch<SS>, IOException> parseMatchPaths(SS stream, ParseSession session,
			SimpleMatchParser<SS> parser, int minDepth, int maxDepth, ParseMatcher<? super SS> matcher, Function<Integer, String> error) {
		SS copy = (SS) stream.branch();
		return () -> new ExIterator<ParseMatch<SS>, IOException>() {
			private final ExIterator<List<ParseMatch<SS>>, IOException> pathParser = parseBranch(new ArrayList<>(), copy, session, parser,
					0, maxDepth).iterator();

			@Override
			public boolean hasNext() throws IOException {
				return pathParser.hasNext();
			}

			@Override
			public ParseMatch<SS> next() throws IOException {
				List<ParseMatch<SS>> path = pathParser.next();

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
				return new ParseMatch<>(matcher, copy, length, path, errorMsg, path.size() == maxDepth);
			}
		};
	}

	private <SS extends BranchableStream<?, ?>> ExIterable<List<ParseMatch<SS>>, IOException> parseBranch(List<ParseMatch<SS>> path,
			SS stream, ParseSession session, org.expresso.parse.ExpressoParser.SimpleMatchParser<SS> parser, int depth, int maxDepth){
		/* The path variable will be re-used here for performance.  If the paths returned from the iterator need to be persisted, then
		 * copies must be made */
		return () -> new ExIterator<List<ParseMatch<SS>>, IOException>() {
			private final ExIterator<ParseMatch<SS>, IOException> matchIterator = parser.parse(stream, session, depth).iterator();
			private ExIterator<List<ParseMatch<SS>>, IOException> subIterator;
			private ParseMatch<SS> match;
			private boolean calledHasNext;

			@Override
			public boolean hasNext() throws IOException {
				calledHasNext = true;
				while (subIterator == null || !subIterator.hasNext()) {
					subIterator = null;
					if (match != null)
						path.remove(path.size() - 1);
					match = matchIterator.next();
					if (match == null)
						continue;
					path.add(match);

					if (depth == maxDepth - 1 || !match.isComplete()) {
						return true;
					} else {
						SS advanced = (SS) stream.branch().advance(match.getLength());
						subIterator = parseBranch(path, advanced, session, parser, depth + 1, maxDepth).iterator();
					}
				}
				return subIterator != null && subIterator.hasNext();
			}

			@Override
			public List<ParseMatch<SS>> next() throws IOException {
				if (!calledHasNext && !hasNext())
					throw new java.util.NoSuchElementException();

				calledHasNext = false;
				if (subIterator != null)
					return subIterator.next();
				else
					return path;
			}
		};
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

	class ParseMatchResult<SS extends BranchableStream<?, ?>> {
		final ParseMatch<SS> match;
		final boolean cached;

		ParseMatchResult(ParseMatch<SS> m, boolean cache) {
			match = m;
			cached = cache;
		}
	}

	class ParseMatcherState<SS extends BranchableStream<?, ?>> {
		final ParseSession session;
		final ParseMatcher<? super SS> matcher;
		final ExIterable<ParseMatchResult<SS>, IOException> results;

		ParseMatcherState(ParseSession sess, ParseMatcher<? super SS> matchr, ExIterable<ParseMatchResult<SS>, IOException> res) {
			session = sess;
			matcher = matchr;
			results = res;
		}
	}

	private class MatchResultIterable<SS extends S> implements ExIterable<ParseMatch<SS>, IOException> {
		private final SS theStream;
		private final ParseSessionImpl<SS> theRootSession;
		private final List<Iterable<ParseMatcherState<SS>>> theStates;

		MatchResultIterable(SS stream, ParseSessionImpl<SS> session) {
			theStream = stream;
			theRootSession = session;
			theStates = new ArrayList<>();
		}

		void add(Iterable<ParseMatcherState<SS>> states) {
			theStates.add(states);
		}

		private class MatchResultIterator implements ExIterator<ParseMatch<SS>, IOException> {
			private final Iterator<Iterable<ParseMatcherState<SS>>> theMatches = theStates.iterator();
			private Iterator<ParseMatcherState<SS>> theCurrentMatches;
			private ParseMatcherState<SS> theCurrentState;
			private ExIterator<ParseMatchResult<SS>, IOException> theResultIter;
			private ParseMatch<SS> theBestMatch;
			private boolean isRoot;
			private boolean hasStarted;
			private boolean hasFinished;

			@Override
			public boolean hasNext() throws IOException {
				if (!hasStarted) {
					hasStarted = true;
					isRoot = !theRootSession.isDebuggingStarted;
					if (isRoot) {
						theDebugger.start(theStream);
						theRootSession.isDebuggingStarted = true;
					}
				}
				outer: while (theResultIter == null || !theResultIter.hasNext()) {
					while (theCurrentMatches == null || !theCurrentMatches.hasNext()) {
						if (!theMatches.hasNext())
							break outer;
						theCurrentMatches = theMatches.next().iterator();
					}
					theCurrentState = theCurrentMatches.next();
					theResultIter = theCurrentState.results.iterator();
				}
				boolean hasNext = theResultIter != null && theResultIter.hasNext();
				if (!hasNext && isRoot && !hasFinished) {
					hasFinished = true;
					if (theBestMatch == null || theBestMatch.getError() != null || !theBestMatch.isComplete())
						theDebugger.fail(theStream, theBestMatch);
					else
						theDebugger.end(theBestMatch);
					theRootSession.isDebuggingStarted = false;
				}
				return hasNext;
			}

			@Override
			public ParseMatch<SS> next() throws IOException {
				if (!hasNext())
					throw new java.util.NoSuchElementException();
				SS copy = (SS) theStream.branch();
				theDebugger.preParse(copy, theCurrentState.matcher, theCurrentState.session);
				ParseMatchResult<SS> next = theResultIter.next();
				if (next.cached)
					theDebugger.usedCache(theCurrentState.matcher, next.match);
				else
					theDebugger.postParse(copy, theCurrentState.matcher, next.match);
				if (next.match == null)
					theDebugger.matchDiscarded(theCurrentState.matcher, next.match);
				else if (next.match.isBetter(theBestMatch)) {
					if (theBestMatch != null)
						theDebugger.matchDiscarded(theBestMatch.getMatcher(), theBestMatch);
					theBestMatch = next.match;
				} else
					theDebugger.matchDiscarded(theCurrentState.matcher, next.match);
				return next.match;
			}
		}

		@Override
		public ExIterator<ParseMatch<SS>, IOException> iterator() {
			return new MatchResultIterator();
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

			Iterable<ParseMatcherState<SS>> match(SS stream, ParseSessionImpl<SS> session,
					Collection<? extends ParseMatcher<? super SS>> matchers) {
				/* This is the core parsing code.  The basic strategy is to disallow recursive parsing, using the cache to allow
				 * matchers access to their own or others' matches.  This makes debugging much, much easier and makes the debugging process
				 * more controllable and, I think, faster.  Not sure what kind of code I'd have to write if I were allowing straight
				 * recursion, so I can't compare it to this; but the code I had to write here isn't real pretty.  I tried to make the code
				 * as easy to read as possible and document my logic in the comments as much as possible, but BEWARE of altering this code.
				 * It breaks parsing pretty much every time I add an optimization or a refactor.  Here there be dragons. */
				/*
				 * Some notes on parsing
				 * * Recursive parsing on a given position is not allowed.  If matcher A has a reference to matcher B in its first place,
				 * 		B will be parsed.  If A has a reference in its first place that can resolve to matcher A, A will not be parsed; the
				 * 		cached result will be used.  On the first iteration, the cached result will be null and, if required, the top-level
				 * 		match will fail.  If a refers to B, which refers to A, all in the first place, the second A will similarly not be
				 * 		parsed.
				 * * If such recursion is or may be required for the match to succeed (detected by attempted recursion, stored in the
				 * 		ParseSession.hasUsedOwnEvaluatingCache flag), the entire set of matches for which the match was not final will be
				 * 		iterated over again, with the previous best match used as the cache, so that recursion will use better and better
				 * 		matches for each iteration.
				 * Thus, recursive matching is flattened and tamed.  This approach is much easier to debug (the deep tree structure is
				 * 		difficult to understand in the UI and the deep stack is difficult in the debugger) and, I believe, more performant.
				 */
				return new Iterable<ParseMatcherState<SS>>() {
					@Override
					public Iterator<ParseMatcherState<SS>> iterator() {
						final Collection<ParseMatcher<? super SS>> matchersCopy = new ArrayList<>(matchers);
						final Set<String> uncachedTypeNames = new LinkedHashSet<>();
						for (ParseMatcher<? super SS> matcher : matchers) {
							Matching matching = theTypeMatching.get(matcher.getName());
							if (matching != null) {
								if (matching.theEvaluatingSession != null) {
									// Mark the session as having used cache elements that are still being evaluated so the results are not
									// cached
									session.markUsedEvaulatingCache(matching.theEvaluatingSession, matcher);
								}
							} else {
								uncachedTypeNames.add(matcher.getName());
								theTypeMatching.put(matcher.getName(), new Matching(session));
							}
						}
						return new Iterator<ParseMatcherState<SS>>() {
							private Iterator<ParseMatcher<? super SS>> matcherIter = matchersCopy.iterator();
							boolean needsAnotherLoop;
							Set<String> notToCache = new LinkedHashSet<>();
							Map<String, ParseMatch<SS>> loopMatches = new LinkedHashMap<>();

							@Override
							public boolean hasNext() {
								if (matcherIter.hasNext())
									return true;

								// After each round, save the matches in the cache
								for (String name : uncachedTypeNames)
									theTypeMatching.get(name).theMatch = loopMatches.get(name);

								if (needsAnotherLoop)
									matcherIter = matchersCopy.iterator();
								return matcherIter.hasNext();
							}

							@Override
							public ParseMatcherState<SS> next() {
								ParseMatcher<? super SS> matcher = matcherIter.next();
								return new ParseMatcherState<>(session, matcher, new ExIterable<ParseMatchResult<SS>, IOException>() {
									@Override
									public ExIterator<ParseMatchResult<SS>, IOException> iterator() {
										if (uncachedTypeNames.contains(matcher.getName())) {
											return new ExIterator<ParseMatchResult<SS>, IOException>() {
												private ExIterator<ParseMatch<SS>, IOException> backing = matcher
														.match(stream, DefaultExpressoParser.this, session).iterator();

												@Override
												public boolean hasNext() throws IOException {
													return backing.hasNext();
												}

												@Override
												public ParseMatchResult<SS> next() throws IOException {
													theDebugger.preParse(stream, matcher, session);
													ParseMatch<SS> match = backing.next();
													theDebugger.postParse(stream, matcher, match);
													matched(matcher, match);
													// TODO Auto-generated method stub
												}
											};
										} else {
											// TODO Retrieve from cache
										}
									}
								});
							}

							void matched(ParseMatcher<? super SS> matcher, ParseMatch<SS> match) {
								// If the matcher referred to one of the currently evaluating matchers, we need to keep iterating because
								// this
								// cache may change as a result of each successive iteration
								needsAnotherLoop |= session.hasUsedOwnEvaluatingCache();
								if (session.hasUsedEvaluatingCache() || hasCommon(session.getUsedOwnEvaluatingCache(), notToCache)) {
									// Don't want to cache if the evaluation used cache elements that are currently being evaluated by a
									// different session
									notToCache.add(matcher.getName());
								} else if (!session.hasUsedOwnEvaluatingCache()) {
									// The matcher didn't need anything that is currently being evaluated, so we can cache the result and
									// not
									// evaluate it again
									Matching cached = theTypeMatching.get(matcher.getName());
									cached.theMatch = match;
									cached.theEvaluatingSession = null;
									matcherIter.remove();
									uncachedTypeNames.remove(matcher.getName());
								}
								session.clearUsedEvaluatingCache();

								// Store the result in this frame
								ParseMatch<SS> oldMatch = loopMatches.get(matcher.getName());
								if (match != null && match.isBetter(oldMatch)) {
									if (oldMatch != null)
										theDebugger.matchDiscarded(matcher, oldMatch);
									loopMatches.put(matcher.getName(), match);
								} else
									theDebugger.matchDiscarded(matcher, match);
							}
						};
					}
				};
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

		Iterable<ParseMatcherState<SS>> match(SS stream, ParseSessionImpl<SS> session,
				Collection<? extends ParseMatcher<? super SS>> matchers) {
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

		Iterable<ParseMatcherState<SS>> match(SS stream, Collection<? extends ParseMatcher<? super SS>> matchers) {
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
