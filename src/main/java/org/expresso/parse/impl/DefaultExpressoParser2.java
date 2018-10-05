package org.expresso.parse.impl;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Set;
import java.util.function.Function;

import org.expresso.parse.BranchableStream;
import org.expresso.parse.ExpressoParser;
import org.expresso.parse.ParseMatch;
import org.expresso.parse.ParseMatcher;
import org.expresso.parse.ParseSession;
import org.expresso.parse.debug.ExpressoParsingDebugger;
import org.expresso.parse.debug.MatchData;
import org.expresso.parse.matchers.BaseMatcher;
import org.qommons.ex.ExIterable;
import org.qommons.ex.ExIterator;

public class DefaultExpressoParser2<S extends BranchableStream<?, ?>> extends BaseMatcher<S> implements ExpressoParser<S> {
	private final Map<String, ParseMatcher<? super S>> allMatchers;
	private final Map<String, List<ParseMatcher<? super S>>> theMatchersByTag;
	private final List<ParseMatcher<? super S>> theDefaultMatchers;

	private ExpressoParsingDebugger<S> theDebugger;

	protected DefaultExpressoParser2(String name, Set<String> tags) {
		super(name, tags);

		allMatchers = new java.util.LinkedHashMap<>();
		theMatchersByTag = new LinkedHashMap<>();
		theDefaultMatchers = new ArrayList<>();

		theDebugger = new org.expresso.parse.debug.NullDebugger<>();
	}

	@Override
	public DefaultExpressoParser2<S> setDebugger(ExpressoParsingDebugger<S> debugger) {
		if (debugger == null)
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
		if (isDefault)
			theDefaultMatchers.add(matcher);
		for (String tag : matcher.getTags()) {
			List<ParseMatcher<? super S>> taggedMatchers = theMatchersByTag.get(tag);
			if (taggedMatchers == null) {
				taggedMatchers = new ArrayList<>();
				theMatchersByTag.put(tag, taggedMatchers);
			}
			taggedMatchers.add(matcher);
		}
	}

	@Override
	public Collection<ParseMatcher<? super S>> getMatchersFor(ParseSession session, String... types) {
		Collection<ParseMatcher<? super S>> matchers;
		if (types.length > 0) {
			matchers = new LinkedHashSet<>();
			for (String type : types) {
				ParseMatcher<? super S> matcher = allMatchers.get(type);
				if (matcher != null)
					matchers.add(matcher);
				else {
					List<ParseMatcher<? super S>> taggedMatchers = theMatchersByTag.get(type);
					if (taggedMatchers != null)
						matchers.addAll(taggedMatchers);
					else if ("default".equals(type))
						matchers.addAll(theDefaultMatchers);
				}
			}
		} else
			matchers = new ArrayList<>(theDefaultMatchers);
		ParseSessionImpl<?> sessImpl = (ParseSessionImpl<?>) session;
		if (sessImpl != null && !sessImpl.theExcludedTypes.isEmpty())
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
	public <SS extends S> ExIterable<ParseMatch<SS>, IOException> parseMatchPaths(SS stream, ParseSession session,
			SimpleMatchParser<SS> parser, int minDepth, int maxDepth, ParseMatcher<? super SS> matcher, Function<Integer, String> error) {
		return newParseMatchPaths(stream, session, parser, minDepth, maxDepth, matcher, error);
	}

	private <SS extends S> ExIterable<ParseMatch<SS>, IOException> newParseMatchPaths(SS stream, ParseSession session,
			SimpleMatchParser<SS> parser, int minDepth, int maxDepth, ParseMatcher<? super SS> matcher, Function<Integer, String> error) {
		PathElementIterable<SS> elementIterable = new PathElementIterable<>(stream, (ParseSessionImpl<SS>) session, parser, minDepth,
			maxDepth, matcher, error);
		ExIterable<List<ParseMatch<SS>>, IOException> pathIterable=ExIterable.combine(elementIterable);
		MatchData<SS> matchData=new MatchData<>(matcher, session);
		return pathIterable.map(path -> {
			int length = 0;
			List<ParseMatch<SS>> pathCopy=new ArrayList<>(path);
			if(!pathCopy.isEmpty() && pathCopy.get(pathCopy.size()-1)==null)
				pathCopy.remove(pathCopy.size()-1);
			for (ParseMatch<SS> el : pathCopy)
				length += el.getLength();
			ParseMatch<SS> pathMatch = new ParseMatch<>(matcher, stream, length, pathCopy, null, true);
			return pathMatch;
		}).beforeEach(() -> {
			PathElementIterable elIter = elementIterable;
			theDebugger.preParse(stream, matchData);
		}).onEach(value -> {
			theDebugger.postParse(stream, matchData);
		});
	}

	private class PathElementIterable<SS extends S> implements ExIterator<ExIterable<ParseMatch<SS>, IOException>, IOException> {
		private final SS theStream;
		private final ParseSessionImpl<SS> theSession;
		private final SimpleMatchParser<SS> theParser;
		private final int theMinDepth;
		private final int theMaxDepth;
		private final ParseMatcher<? super SS> theMatcher;
		private final Function<Integer, String> theError;

		//State
		private SS theCumulativeStream;
		private int theClearPathDepth;
		private int theDepth;

		PathElementIterable(SS stream, ParseSessionImpl<SS> session, SimpleMatchParser<SS> parser, int minDepth, int maxDepth,
			ParseMatcher<? super SS> matcher, Function<Integer, String> error) {
			theStream = stream;
			theSession = session;
			theParser = parser;
			theMinDepth = minDepth;
			theMaxDepth = maxDepth;
			theMatcher = matcher;
			theError = error;

			theCumulativeStream = theStream;
			theClearPathDepth = -1;
		}

		@Override public boolean hasNext() throws IOException {
			return theMaxDepth <= 0 || theDepth < theMaxDepth;
		}

		@Override public ExIterable<ParseMatch<SS>, IOException> next() throws IOException {
			int depth = theDepth;
			theDepth++;
			System.out.println(theMatcher.toShortString() + " creating element iterator @" + depth);
			return new ExIterable<ParseMatch<SS>, IOException>(){
				@Override public ExIterator<ParseMatch<SS>, IOException> iterator() {
					return new ExIterator<ParseMatch<SS>, IOException>(){
						private final SS theElementStream = theCumulativeStream;
						private final ExIterator<ParseMatch<SS>, IOException> theMatchIterator = theParser
							.parse(theElementStream, theSession, depth).iterator();
						private boolean checkedHasNext = false;
						private boolean hasNext = false;
						private boolean hasNextMatch = false;

						@Override
						public boolean hasNext() throws IOException {
							if (checkedHasNext)
								return hasNext;
							checkedHasNext = true;
							hasNextMatch = false;
							if (theClearPathDepth < depth - 1)
								hasNext = false;
							else if (theMatchIterator.hasNext())
								hasNext = hasNextMatch = true;
							else if (depth < theMinDepth)
								hasNext = true; //Add an error match for possible incomplete match
							System.out.println(theMatcher.toShortString() + " @" + depth + ": hasNext()=" + hasNext);
							return hasNext;
						}

						@Override
						public ParseMatch<SS> next() throws IOException {
							if (!checkedHasNext && !hasNext())
								throw new NoSuchElementException();
							else if (!hasNext)
								throw new NoSuchElementException();
							else if (hasNextMatch) {
								checkedHasNext = false;
								ParseMatch<SS> nextMatch = theMatchIterator.next();
								System.out.println(theMatcher.toShortString() + " @" + depth + ": next()="
										+ (nextMatch == null ? "null" : "\"" + nextMatch + "\" (" + nextMatch.isComplete() + ")"));
								if (nextMatch != null) {
									if(!nextMatch.isComplete()) {
										theClearPathDepth = depth - 1;
									} else{
										theClearPathDepth = depth;
									}
									theCumulativeStream = (SS) theElementStream.advance(nextMatch.getLength());
									return nextMatch;
								} else {
									theClearPathDepth = depth - 1;
									return null;
								}
							} else {
								hasNext = false;
								theClearPathDepth = depth - 1;
								return errorMatch();
							}
						}

						private ParseMatch<SS> errorMatch() {
							if (theError == null)
								return null;
							String errorMsg = theError.apply(depth);
							return new ParseMatch<>(theMatcher, theElementStream, 0, null, errorMsg, false);
						}
					};
				}
			};
		}
	}

	private <SS extends S> ExIterable<ParseMatch<SS>, IOException> oldParseMatchPaths(SS stream, ParseSession session,
			SimpleMatchParser<SS> parser, int minDepth, int maxDepth, ParseMatcher<? super SS> matcher, Function<Integer, String> error) {
		SS copy = (SS) stream.clone();
		return () -> new ExIterator<ParseMatch<SS>, IOException>() {
			private final ExIterator<LinkedList<ParseMatch<SS>>, IOException> pathParser = parseBranch(new LinkedList<>(), copy, session,
					parser, 0, minDepth, maxDepth).iterator();

			@Override
			public boolean hasNext() throws IOException {
				return pathParser.hasNext();
			}

			@Override
			public ParseMatch<SS> next() throws IOException {
				LinkedList<ParseMatch<SS>> path;
				do {
					path = pathParser.next();
				} while (path == null && pathParser.hasNext());
				if (path == null)
					return null;

				String errorMsg;
				if (!path.isEmpty() && path.getLast().getError() != null)
					errorMsg = null; // Let the deeper message come up
				else if (path.size() < minDepth)
					errorMsg = error.apply(path.size());
				else
					errorMsg = null;
				int length = 0;
				for (ParseMatch<SS> el : path)
					length += el.getLength();
				return new ParseMatch<>(matcher, copy, length, path, errorMsg, path.size() >= minDepth);
			}
		};
	}

	private <SS extends BranchableStream<?, ?>> ExIterable<LinkedList<ParseMatch<SS>>, IOException> parseBranch(
			LinkedList<ParseMatch<SS>> path, SS stream, ParseSession session,
			org.expresso.parse.ExpressoParser.SimpleMatchParser<SS> parser, int depth, int minDepth, int maxDepth) {
		/* The path variable will be re-used here for performance.  If the paths returned from the iterator need to be persisted, then
		 * copies must be made */
		return () -> new ExIterator<LinkedList<ParseMatch<SS>>, IOException>() {
			private final ExIterator<ParseMatch<SS>, IOException> matchIterator = parser.parse(stream, session, depth).iterator();
			private ExIterator<LinkedList<ParseMatch<SS>>, IOException> subIterator;
			private ParseMatch<SS> match;
			private boolean hasReturnedPath;

			@Override
			public boolean hasNext() throws IOException {
				if (subIterator != null && subIterator.hasNext())
					return true;
				else if (matchIterator.hasNext())
					return true;
				else if (!hasReturnedPath && (depth > 0 || minDepth == 0)) // Don't return the zero-length match if the min depth is > 0
					return true;
				else
					return false;
			}

			@Override
			public LinkedList<ParseMatch<SS>> next() throws IOException {
				while ((subIterator == null || !subIterator.hasNext()) && matchIterator.hasNext()) {
					if (match != null)
						path.removeLast();
					match = matchIterator.next();
					if (match == null) {
						return null;
					}
					path.add(match);

					if (depth == maxDepth - 1 || !match.isComplete()) {
						return path;
					} else {
						hasReturnedPath = false;
						SS advanced = (SS) stream.clone().advance(match.getLength());
						subIterator = parseBranch(path, advanced, session, parser, depth + 1, minDepth, maxDepth).iterator();
					}
				}

				if (subIterator != null && subIterator.hasNext())
					return subIterator.next();
				else {
					if (match != null)
						path.removeLast();
					if (!hasReturnedPath && (depth > 0 || minDepth == 0)) {
						hasReturnedPath = true;
						return path;
					}
				}
				return null;
			}
		};
	}

	@Override
	public <SS extends S> ExIterable<ParseMatch<SS>, IOException> parse(SS stream, ParseSession session,
			Collection<? extends ParseMatcher<? super SS>> matchers) {
		SS copy = (SS) stream.clone();
		return new MatchResultIterable<>(copy, (ParseSessionImpl<SS>) session, matchers);
	}

	private class MatchResultIterable<SS extends S> implements ExIterable<ParseMatch<SS>, IOException> {
		private final SS theStream;
		private final ParseSessionImpl<SS> theSession;
		private final List<ParseMatcher<? super SS>> theForeignMatchers;
		private final MemberMatchResultIterable<SS> theMemberMatches;

		MatchResultIterable(SS stream, ParseSessionImpl<SS> session, Collection<? extends ParseMatcher<? super SS>> matchers) {
			theStream = stream;
			theSession = session;
			theForeignMatchers = new ArrayList<>();
			List<ParseMatcher<? super SS>> memberMatchers = new ArrayList<>();

			for (ParseMatcher<? super SS> matcher : matchers) {
				if (matcher.getName() == null || allMatchers.get(matcher.getName()) != matcher)
					theForeignMatchers.add(matcher);
				else
					memberMatchers.add(matcher);
			}
			theMemberMatches = new MemberMatchResultIterable<>(theStream, memberMatchers, theSession);
		}

		@Override
		public ExIterator<ParseMatch<SS>, IOException> iterator() {
			boolean started = !theSession.isDebuggingStarted;
			if (started) {
				theDebugger.start(theStream);
				theSession.isDebuggingStarted = true;
			}
			return new ExIterator<ParseMatch<SS>, IOException>() {
				private Iterator<ParseMatcher<? super SS>> foriegnMatchIterator = theForeignMatchers.iterator();
				Iterator<ParseMatchState<SS>> memberMatchIterator = theMemberMatches.iterator();
				private MatchData<SS> theCurrentMatch;
				private ExIterator<ParseMatch<SS>, IOException> theCurrentMatches;
				private boolean hasEnded;
				private ParseMatch<SS> theBestMatch;

				@Override
				public boolean hasNext() throws IOException {
					while (theCurrentMatches == null || !theCurrentMatches.hasNext()) {
						if (foriegnMatchIterator.hasNext()) {
							ParseMatcher<? super SS> matcher = foriegnMatchIterator.next();
							theCurrentMatch = new MatchData<>(matcher, theSession);
							theCurrentMatches = matcher.match((SS) theStream.clone(), DefaultExpressoParser2.this, theSession).iterator();
							theCurrentMatch.nextRound();
						} else if (memberMatchIterator.hasNext()) {
							ParseMatchState<SS> state = memberMatchIterator.next();
							theCurrentMatch = state.matchData;
							theCurrentMatches = state.matches.iterator();
						} else
							break;
					}
					if (theCurrentMatches != null && theCurrentMatches.hasNext())
						return true;
					end();
					return false;
				}

				@Override
				public ParseMatch<SS> next() throws IOException {
					if (!hasNext())
						throw new NoSuchElementException();
					theDebugger.preParse(theStream, theCurrentMatch);
					ParseMatch<SS> match = theCurrentMatches.next();
					while (match == null && theCurrentMatches.hasNext()) {
						theDebugger.postParse(theStream, theCurrentMatch);
						theDebugger.preParse(theStream, theCurrentMatch);
						match = theCurrentMatches.next();
					}
					if (match != null)
						theCurrentMatch.addMatch(match);
					theDebugger.postParse(theStream, theCurrentMatch);
					return match;
				}

				@Override
				protected void finalize() {
					end();
				}

				private void end() {
					if (!hasEnded) {
						hasEnded = true;
						if (started) {
							if (theBestMatch == null || theBestMatch.getError() != null || !theBestMatch.isComplete())
								theDebugger.fail(theStream, theBestMatch);
							else
								theDebugger.end(theBestMatch);
							theSession.isDebuggingStarted = false;
						}
					}
				}
			};
		}
	}

	private class MemberMatchResultIterable<SS extends S> implements Iterable<ParseMatchState<SS>> {
		private final SS theStream;
		private final List<ParseMatcher<? super SS>> theMatchers;
		private final ParseSessionImpl<SS> theSession;
		private final StreamPositionCache<SS> theCache;
		private final ParseSessionImpl<SS> theSubSession;
		private final Set<String> theNoCacheMatchers;

		MemberMatchResultIterable(SS stream, List<ParseMatcher<? super SS>> matchers, ParseSessionImpl<SS> session) {
			theStream = stream;
			theMatchers = matchers;
			theSession = session;
			theCache = session.theCache.getPositionCache(theStream.getPosition());
			theSubSession = new ParseSessionImpl<>(theSession, theSession.theCache, true);
			theNoCacheMatchers = new HashSet<>();

			theSession.excludeTypes(matchers);
		}

		@Override
		public Iterator<ParseMatchState<SS>> iterator() {
			List<ParseMatcher<? super SS>> matchers = new ArrayList<>(theMatchers);
			return new Iterator<ParseMatchState<SS>>() {
				private Iterator<ParseMatcher<? super SS>> matchIter = matchers.iterator();
				private Map<String, List<ParseMatch<SS>>> theMatches = new HashMap<>();
				private boolean hasNewBest;
				private boolean doLoop;

				@Override
				public boolean hasNext() {
					if (matchIter.hasNext())
						return true;
					else if (doLoop && hasNewBest) {
						postLoop();
						doLoop = false;
						matchIter = matchers.iterator();
						if (matchIter.hasNext())
							return true;
						else {
							end();
							return false;
						}
					} else {
						postLoop();
						end();
						return false;
					}
				}

				@Override
				public ParseMatchState<SS> next() {
					if (!hasNext())
						throw new NoSuchElementException();
					ParseMatcher<? super SS> matcher = matchIter.next();
					MatchData<SS> matchData = new MatchData<>(matcher, theSubSession);
					MatchInstanceData<SS> matchCache = theCache.getFor(matcher, theSubSession);
					matchData.setCached(matchCache.evaluatingSession != theSubSession);
					matchData.nextRound();
					ExIterable<ParseMatch<SS>, IOException> matches;
					if (matchCache.evaluatingSession == theSubSession) {
						// These matches have not been calculated yet
						List<ParseMatch<SS>> accumulated = new ArrayList<>();
						matches = matcher.match(theStream, DefaultExpressoParser2.this, theSubSession);
						ParseMatch<SS> previousBest = matchData.getBestMatch();
						matches = matches.onEach(match -> accumulated.add(match)).onFinish(() -> {
							// When the matches have been exhausted, see what we need to do about looping and caching

							if (matchData.getBestMatch() != previousBest)
								hasNewBest = true;
							// If the matcher referred to one of the currently evaluating matchers, we need to keep iterating because the
							// cache may change as a result of each successive iteration
							if (!doLoop)
								doLoop = theSubSession.hasUsedOwnEvaluatingCache();
							if (theSubSession.hasUsedEvaluatingCache()
									|| hasCommon(theSubSession.getUsedOwnEvaluatingCache(), theNoCacheMatchers)) {
								// Don't want to cache if the evaluation used cache elements that are currently being evaluated by a
								// different session
								theMatches.put(matcher.getName(), accumulated);
								theNoCacheMatchers.add(matcher.getName());
							} else if (!theSubSession.hasUsedOwnEvaluatingCache()) {
								// The matcher didn't need anything that is currently being evaluated, so we can cache the result and not
								// evaluate it again
								matchCache.matches.clear();
								matchCache.matches.addAll(accumulated);
								matchCache.evaluatingSession = null;
								matchIter.remove();
								theMatches.remove(matcher.getName());
							} else {
								theMatches.put(matcher.getName(), accumulated);
							}
							theSubSession.clearUsedEvaluatingCache();
						});
					} else {
						if (matchCache.evaluatingSession != null) {
							// Another instance of this iterable is calculating these somewhere upstream
							// Mark the session as having used cache elements that are still being evaluated so the results are not cached
							theSubSession.markUsedEvaulatingCache(matchCache.evaluatingSession, matcher);
						} else {
							// These matches are verified
						}
						// return the cached matches and don't ask for them again
						matches = ExIterable.forEx(ExIterable.fromIterable(matchCache.matches));
						matchIter.remove();
					}
					return new ParseMatchState<SS>(matchData, matches);
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

				private void postLoop() {
					// After each round, save the matches in the cache
					for (Map.Entry<String, List<ParseMatch<SS>>> match : theMatches.entrySet()) {
						MatchInstanceData<SS> matchCache = theCache.cacheData.get(match.getKey());
						matchCache.matches.clear();
						matchCache.matches.addAll(match.getValue());
					}
				}

				private void end() {
					// Activate (mark as verified) the cache elements that need to be cached, clear the ones that don't
					for (String name : theNoCacheMatchers) {
						theCache.cacheData.remove(name);
						theMatches.remove(name);
					}
					for (String name : theMatches.keySet())
						theCache.cacheData.get(name).evaluatingSession = null;
				}
			};
		}
	}

	private class ParseMatchState<SS extends S> {
		final MatchData<SS> matchData;
		final ExIterable<ParseMatch<SS>, IOException> matches;

		public ParseMatchState(MatchData<SS> _matchData, ExIterable<ParseMatch<SS>, IOException> _matches) {
			matchData = _matchData;
			matches = _matches;
		}

		@Override
		public String toString() {
			return matchData.toString();
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
			StreamPositionCache<SS> posCache = theCache.cacheData.get(stream.getPosition());
			if (posCache == null)
				return false;
			MatchInstanceData<SS> matchCache = posCache.cacheData.get(type);
			if (matchCache == null)
				return false;
			return matchCache.evaluatingSession != this;
		}

		void excludeTypes(Collection<? extends ParseMatcher<? super SS>> matchers) {
			Iterator<? extends ParseMatcher<? super SS>> matcherIter = matchers.iterator();
			while (matcherIter.hasNext()) {
				ParseMatcher<? super SS> matcher = matcherIter.next();
				String removeType = null;
				if (matcher.getName() != null && theExcludedTypes.contains(matcher.getName()))
					removeType = matcher.getName();
				if (removeType == null) {
					for (String tag : matcher.getTags())
						if (theExcludedTypes.contains(tag)) {
							removeType = tag;
							break;
						}
				}
				if (removeType != null) {
					if (!matcher.getName().equals(IGNORABLE) && !matcher.getTags().contains(IGNORABLE)) {
						if (!warnedOnlyExcludeIgnore) {
							System.err.println("ExpressoParser-excluded type \"" + removeType + "\" would exclude non-ignorable type \""
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

	private class ParsingCache<SS extends S> {
		final Map<Integer, StreamPositionCache<SS>> cacheData;

		ParsingCache() {
			cacheData = new LinkedHashMap<>();
		}

		StreamPositionCache<SS> getPositionCache(int position) {
			StreamPositionCache<SS> pos = cacheData.get(position);
			if (pos == null) {
				pos = new StreamPositionCache<>(position);
				cacheData.put(position, pos);
			}
			return pos;
		}
	}

	private class StreamPositionCache<SS extends S> {
		private final int thePosition;
		final Map<String, MatchInstanceData<SS>> cacheData;

		StreamPositionCache(int pos) {
			thePosition = pos;
			cacheData = new LinkedHashMap<>();
		}

		MatchInstanceData<SS> getFor(ParseMatcher<? super SS> matcher, ParseSessionImpl<SS> session) {
			MatchInstanceData<SS> match = cacheData.get(matcher.getName());
			if (match == null) {
				match = new MatchInstanceData<>(matcher, session);
				cacheData.put(matcher.getName(), match);
			}
			return match;
		}
	}

	private class MatchInstanceData<SS extends S> {
		private final ParseMatcher<? super SS> theMatcher;
		final List<ParseMatch<SS>> matches;
		ParseSessionImpl<SS> evaluatingSession;

		MatchInstanceData(ParseMatcher<? super SS> matcher, ParseSessionImpl<SS> session) {
			theMatcher = matcher;
			evaluatingSession = session;
			matches = new ArrayList<>();
		}
	}

	/**
	 * @param <S> The type of stream to parse
	 * @param name The name for the parser
	 * @return A builder capable of constructing a {@link DefaultExpressoParser2}
	 */
	public static <S extends BranchableStream<?, ?>> Builder<S> build(String name) {
		return new Builder<>(name);
	}

	/**
	 * Builds {@link DefaultExpressoParser2}s. See {@link DefaultExpressoParser2#build(String)}.
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
			for (String tag : tags)
				if (!theParserTags.add(tag))
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
			for (String matcherTag : matcherTags)
				theMatcherTags.add(matcherTag);
			return this;
		}

		/**
		 * Adds a matcher to the parser to be built
		 *
		 * @param matcher The matcher to add
		 * @param isDefault Whether to use the matcher by default (i.e. when not referred to explicitly by name or tag). See
		 *        {@link ExpressoParser#parseWith(BranchableStream, ParseSession, ParseMatcher...)}.
		 * @return This builder, for chaining
		 */
		public Builder<S> addMatcher(ParseMatcher<? super S> matcher, boolean isDefault) {
			ParseMatcher<? super S> previous = allMatchers.get(matcher.getName());
			if (previous != null)
				throw new IllegalArgumentException(
						"Duplicate matchers named \"" + matcher.getName() + "\": " + previous + " and " + matcher);
			if (theMatcherTags.contains(matcher.getName()))
				throw new IllegalArgumentException("Matcher \"" + matcher.getName() + "\" has the same name as a tag");
			for (String tag : matcher.getTags()) {
				if (allMatchers.containsKey(tag))
					throw new IllegalArgumentException("Matcher \"" + matcher.getName() + "\" uses a tag (\"" + tag
							+ "\") that is the same as the" + "name of a matcher");
			}
			isDefault &= !matcher.getTags().contains(IGNORABLE);
			allMatchers.put(matcher.getName(), matcher);
			for (String tag : matcher.getTags())
				theMatcherTags.add(tag);
			if (isDefault)
				theDefaultMatchers.add(matcher.getName());
			return this;
		}

		/**
		 * Builds the parser
		 *
		 * @return The new parser
		 */
		public DefaultExpressoParser2<S> build() {
			List<String[]> unmetDepends = new ArrayList<>();
			for (ParseMatcher<? super S> matcher : allMatchers.values()) {
				for (String depend : matcher.getExternalTypeDependencies())
					if (!allMatchers.containsKey(depend) && !theMatcherTags.contains(depend) && !"default".equals(depend))
						unmetDepends.add(new String[] { matcher.getName(), depend });
			}
			if (!unmetDepends.isEmpty()) {
				StringBuilder error = new StringBuilder("The following matchers contain dependencies that are unknown to the parser:");
				for (String[] depend : unmetDepends)
					error.append('\n').append(depend[0]).append(": ").append(depend[1]);
				throw new IllegalStateException(error.toString());
			}

			DefaultExpressoParser2<S> ret = new DefaultExpressoParser2<>(theName, theParserTags);
			for (ParseMatcher<? super S> matcher : allMatchers.values())
				ret.addMatcher(matcher, theDefaultMatchers.contains(matcher.getName()));
			return ret;
		}
	}
}
