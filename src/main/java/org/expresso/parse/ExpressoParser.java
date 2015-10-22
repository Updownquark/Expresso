package org.expresso.parse;

import java.io.IOException;
import java.util.*;

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
	default Set<String> getExternalTypeDependencies() {
		return Collections.EMPTY_SET; // A parser is typically self-contained
	}

	/**
	 * @param types The matcher names and tags to get matchers for
	 * @return Matchers in this parser whose name or one of its tags matches one of the given types
	 */
	Collection<ParseMatcher<? super S>> getMatchersFor(String... types);

	/** @return A new session valid for parsing a complete set of data in this parser */
	ParseSession createSession();

	/**
	 * Parses one match (as complete as can be) out of the stream, advancing the stream to the first position after the match
	 *
	 * @param <SS> The type of the stream
	 * @param stream The stream to parse
	 * @return The match parsed from the stream
	 * @throws IOException If an error occurs while retrieving the data to parse
	 */
	default <SS extends S> ParseMatch<SS> parse(SS stream) throws IOException {
		return parseByType(stream, createSession(), new String[0]);
	}

	@Override
	default <SS extends S> ParseMatch<SS> match(SS stream, ExpressoParser<? super SS> parser, ParseSession session) throws IOException {
		return parseByType(stream, session, new String[0]);
	}

	/**
	 * Parses a match out of the stream whose matcher is named or tagged one of the given types. If <code>types</code> is not provided
	 * (zero-length), then any default matcher will do.
	 *
	 * @param <SS> The type of the stream
	 * @param stream The stream to parse
	 * @param session The parsing session
	 * @param types The matcher names or tags to use, or empty to use all default matchers
	 * @return The match parsed from the stream
	 * @throws IOException If an error occurs while retrieving the data to parse
	 */
	default <SS extends S> ParseMatch<SS> parseByType(SS stream, ParseSession session, String... types) throws IOException {
		if(session == null)
			session = createSession();
		return parse(stream, session, getMatchersFor(types));
	}

	/**
	 * Parses a match from the stream using one of the given matchers. If zero-length, then all of this parser's default matchers will be
	 * used.
	 *
	 * @param <SS> The type of the stream
	 * @param stream The stream to parse
	 * @param session The parsing session
	 * @param matchers The matchers to use, or emtpy to use all default matchers. These matchers need not be known by this parser.
	 * @return The match parsed from the stream
	 * @throws IOException If an error occurs while retrieving the data to parse
	 */
	default <SS extends S> ParseMatch<SS> parseWith(SS stream, ParseSession session, ParseMatcher<? super SS>... matchers)
		throws IOException {
		return parse(stream, session, matchers.length > 0 ? Arrays.asList(matchers) : getMatchersFor());
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
	 * @return The match parsed from the stream.
	 * @throws IOException If an error occurs while retrieving the data to parse
	 */
	<SS extends S> ParseMatch<SS> parse(SS stream, ParseSession session, Collection<? extends ParseMatcher<? super SS>> matchers)
		throws IOException;

	/**
	 * Parses matches from the stream iteratively. If an error occurs retrieving data, the {@link Iterator#next()} method may throw an
	 * {@link IllegalStateException} wrapping the {@link IOException}.
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
							return parse(stream);
						} catch(IOException e) {
							throw new IllegalStateException("Could not retrieve data to parse next match", e);
						}
					}
				};
			}
		};
	}
}
