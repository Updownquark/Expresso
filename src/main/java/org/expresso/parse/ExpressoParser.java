package org.expresso.parse;

import java.io.IOException;
import java.util.*;
import java.util.function.Function;

/**
 * Contains the mechanism for parsing data from a stream based on the matching capabilities of many component matchers
 *
 * @param <S> The type of stream that this parser can parse
 */
public interface ExpressoParser<S extends BranchableStream<?, ?>> extends ParseMatcher<S> {
	/**
	 * The tag for ignorable matchers, i.e., matchers for bits of data that can be present just about anywhere and don't typically
	 * contribute to the syntax. E.g. whitespace, comments, etc.
	 */
	public static final String IGNORABLE = "ignorable";

	/** The tag for white space matchers */
	public static final String WHITE_SPACE = "white-space";

	@Override
	default String getTypeName() {
		return "parser";
	}

	@Override
	default Map<String, String> getAttributes() {
		return Collections.EMPTY_MAP;
	}

	@Override
	default Set<String> getExternalTypeDependencies() {
		return Collections.EMPTY_SET; // A parser is typically self-contained
	}

	@Override
	default Set<String> getPotentialBeginningTypeReferences(ExpressoParser<?> parser, ParseSession session) {
		return java.util.Collections.EMPTY_SET;
	}

	/**
	 * @param session The currently executing session. May be null.
	 * @param types The matcher names and tags to get matchers for
	 * @return Matchers in this parser whose name or one of its tags matches one of the given types
	 */
	Collection<ParseMatcher<? super S>> getMatchersFor(ParseSession session, String... types);

	/** @return A new session valid for parsing a complete set of data in this parser */
	ParseSession createSession();

	/**
	 * Parses possibilities for the first match (as complete as can be) out of the stream
	 *
	 * @param <SS> The type of the stream
	 * @param stream The stream to parse
	 * @return The possible matches parsed from the stream
	 * @throws IOException If an error occurs while retrieving the data to parse
	 */
	default <SS extends S> List<ParseMatch<SS>> parse(SS stream) throws IOException {
		return parseByType(stream, createSession(), new String[0]);
	}

	/**
	 * Parses the best possibility for the first match (as complete as can be) out of the stream, advancing the stream beyond it
	 *
	 * @param <SS> The type of the stream
	 * @param stream The stream to parse
	 * @return The possible matches parsed from the stream
	 * @throws IOException If an error occurs while retrieving the data to parse
	 */
	default <SS extends S> ParseMatch<SS> parseBest(SS stream) throws IOException {
		SS copy = (SS) stream.branch();
		List<ParseMatch<SS>> matches = parse(copy);
		ParseMatch<SS> best = null;
		for (ParseMatch<SS> m : matches) {
			if (m != null && m.isBetter(best))
				best = m;
		}
		if (best != null)
			stream.advance(best.getLength());
		return best;
	}

	@Override
	default <SS extends S> List<ParseMatch<SS>> match(SS stream, ExpressoParser<? super SS> parser, ParseSession session)
			throws IOException {
		return parseByType(stream, session, new String[0]);
	}

	/**
	 * Parses the best possibility for the first match (as complete as can be) out of the stream, advancing the stream beyond it
	 *
	 * @param <SS> The type of the stream
	 * @param stream The stream to parse
	 * @param types The matcher names or tags to use, or empty to use all default matchers
	 * @return The possible matches parsed from the stream
	 * @throws IOException If an error occurs while retrieving the data to parse
	 */
	default <SS extends S> ParseMatch<SS> parseBestByType(SS stream, String... types) throws IOException {
		SS copy = (SS) stream.branch();
		List<ParseMatch<SS>> matches = parseByType(copy, null, types);
		ParseMatch<SS> best = null;
		for (ParseMatch<SS> m : matches) {
			if (m != null && m.isBetter(best))
				best = m;
		}
		if (best != null)
			stream.advance(best.getLength());
		return best;
	}

	/**
	 * Parses possibilities for the first match out of the stream whose matcher is named or tagged one of the given types. If
	 * <code>types</code> is not provided (zero-length), then any default matcher will do.
	 *
	 * @param <SS> The type of the stream
	 * @param stream The stream to parse
	 * @param session The parsing session
	 * @param types The matcher names or tags to use, or empty to use all default matchers
	 * @return The possible matches parsed from the stream
	 * @throws IOException If an error occurs while retrieving the data to parse
	 */
	default <SS extends S> List<ParseMatch<SS>> parseByType(SS stream, ParseSession session, String... types) throws IOException {
		if(session == null)
			session = createSession();
		return parse(stream, session, getMatchersFor(session, types));
	}

	/**
	 * Parses possibilities for the first match from the stream using one of the given matchers. If zero-length, then all of this parser's
	 * default matchers will be used.
	 *
	 * @param <SS> The type of the stream
	 * @param stream The stream to parse
	 * @param session The parsing session
	 * @param matchers The matchers to use, or emtpy to use all default matchers. These matchers need not be known by this parser.
	 * @return The possible matches parsed from the stream
	 * @throws IOException If an error occurs while retrieving the data to parse
	 */
	default <SS extends S> List<ParseMatch<SS>> parseWith(SS stream, ParseSession session,
			Collection<? extends ParseMatcher<? super SS>> matchers)
					throws IOException{
		return parse(stream, session, matchers.isEmpty() ? getMatchersFor(session) : matchers);
	}

	/**
	 * Parses possibilities for the first match from the stream using one of the given matchers. If zero-length, then all of this parser's
	 * default matchers will be used.
	 *
	 * @param <SS> The type of the stream
	 * @param stream The stream to parse
	 * @param session The parsing session
	 * @param matchers The matchers to use, or emtpy to use all default matchers. These matchers need not be known by this parser.
	 * @return The possible matches parsed from the stream
	 * @throws IOException If an error occurs while retrieving the data to parse
	 */
	default <SS extends S> List<ParseMatch<SS>> parseWith(SS stream, ParseSession session, ParseMatcher<? super SS>... matchers)
			throws IOException {
		return parse(stream, session, matchers.length > 0 ? Arrays.asList(matchers) : getMatchersFor(session));
	}

	/**
	 * Typically an internal method, not called from external or matcher implementation code. Parses a match from the stream using one of
	 * the given matchers only. If calling externally, {@link #parseWith(BranchableStream, ParseSession, ParseMatcher...)} is probably wha
	 * you want.
	 *
	 * @param <SS> The type of the stream
	 * @param stream The stream to parse
	 * @param session The parsing session
	 * @param matchers The matchers to use. Need not be known by this parser.
	 * @return The possible matches parsed from the stream.
	 * @throws IOException If an error occurs while retrieving the data to parse
	 */
	<SS extends S> List<ParseMatch<SS>> parse(SS stream, ParseSession session, Collection<? extends ParseMatcher<? super SS>> matchers)
			throws IOException;

	interface SimpleMatchParser<S extends BranchableStream<?, ?>> {
		List<ParseMatch<S>> parse(S stream, ParseSession session, int depth) throws IOException;
	}

	default <SS extends BranchableStream<?, ?>> List<ParseMatch<SS>> parseMatchPaths(SS stream, ParseSession session,
			SimpleMatchParser<SS> parser, int maxDepth, ParseMatcher<? super SS> matcher, Function<Integer, String> error)
					throws IOException {
		List<List<ParseMatch<SS>>> paths = new ArrayList<>();
		SS copy = (SS) stream.branch();
		// preParse(parser, copy, session);
		for (ParseMatch<SS> possibility : parser.parse(copy, session, 0)) {
			// postParse(parser, possibility, copy, session);
			List<ParseMatch<SS>> path = new ArrayList<>();
			path.add(possibility);
			paths.add(path);
		}
		boolean madeProgress = true;
		for (int depth = 1; depth < maxDepth && madeProgress; depth++) {
			madeProgress = false;
			ListIterator<List<ParseMatch<SS>>> pathIter = paths.listIterator();
			while (pathIter.hasNext()) {
				List<ParseMatch<SS>> path = pathIter.next();
				if (path.size() != depth || !path.get(path.size() - 1).isComplete())
					continue;

				madeProgress = true;
				copy = (SS) stream.branch();
				for (ParseMatch<SS> pathEl : path)
					copy.advance(pathEl.getLength());
				List<ParseMatch<SS>> newMatches = parser.parse(copy, session, depth);
				if (newMatches.isEmpty())
					pathIter.remove();
				else {
					// Re-use the path list for the first match
					path.add(newMatches.get(newMatches.size() - 1));
					for (int j = 0; j < newMatches.size() - 1; j++) {
						List<ParseMatch<SS>> newPath = new ArrayList<>();
						// The path now has the first new match in it, so we can't just do addAll
						for (int k = 0; k < path.size() - 1; k++)
							newPath.add(path.get(k));
						newPath.add(newMatches.get(j));
						pathIter.add(newPath);
					}
				}
			}
		}

		copy = (SS) stream.branch();
		List<ParseMatch<SS>> ret = new ArrayList<>(paths.size());
		for (int i = 0; i < ret.size(); i++) {
			List<ParseMatch<SS>> path = paths.get(i);
			String errorMsg;
			if (!path.isEmpty() && path.get(path.size() - 1).getError() != null)
				errorMsg = null; // Let the deeper message come up
			else if (path.size() < maxDepth)
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

	/**
	 * Parses matches from the stream iteratively. If an error occurs retrieving data, the {@link Iterator#next()} method may throw an
	 * {@link IllegalStateException} wrapping the {@link IOException}. The best quality match is provided from the possibilities for each
	 * match.
	 *
	 * @param <SS> The type of the stream
	 * @param stream The stream to parse
	 * @return An iterable providing matches from the stream as long as the stream has more content
	 */
	default <SS extends S> Iterable<ParseMatch<SS>> matches(SS stream) {
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
						try {
							return parseBest(stream);
						} catch(IOException e) {
							throw new IllegalStateException("Could not retrieve data to parse next match", e);
						}
					}
				};
			}
		};
	}

	/**
	 * Removes matchers from a collection whose name or tag is given in the excluded tags
	 *
	 * @param matchers The matcher collection to filter
	 * @param excludeTypes The types (names or tags) to exclude
	 */
	public static void removeTypes(Collection<? extends ParseMatcher<?>> matchers, Set<String> excludeTypes) {
		if(excludeTypes.isEmpty())
			return;
		Iterator<? extends ParseMatcher<?>> iter = matchers.iterator();
		while(iter.hasNext()) {
			ParseMatcher<?> matcher = iter.next();
			if(matcher.getName() != null && excludeTypes.contains(matcher.getName()))
				iter.remove();
			else {
				for(String tag : matcher.getTags())
					if(excludeTypes.contains(tag)) {
						iter.remove();
						break;
					}
			}
		}
	}
}
