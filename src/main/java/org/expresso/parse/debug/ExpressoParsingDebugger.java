package org.expresso.parse.debug;

import org.expresso.parse.BranchableStream;
import org.expresso.parse.ExpressoParser;
import org.expresso.parse.ParseMatch;

/**
 * An interface for a debugger to be notified of parsing events
 *
 * @param <S> The type of stream to parse
 */
public interface ExpressoParsingDebugger<S extends BranchableStream<?, ?>> {
	/**
	 * Called when the debugger is installed in a parser
	 *
	 * @param parser The parser the debugger is being installed in
	 */
	void init(ExpressoParser<?> parser);

	/**
	 * Called at the start of parsing a sequence
	 *
	 * @param text The sequence to parse
	 */
	void start(S text);

	/**
	 * Called when the parsing of a sequence has finished successfully
	 *
	 * @param bestMatch The best match parsed from the sequence
	 */
	void end(ParseMatch<? extends S> bestMatch);

	/**
	 * Called when the parsing of a sequence fails
	 *
	 * @param stream The stream whose parsing was attempted
	 * @param match The match parsed from the sequence before the failure
	 * @param e The exception that is the source of the failure
	 */
	void fail(S stream, ParseMatch<? extends S> match);

	/**
	 * Called before attempting to parse a subsequence according to a particular parse config
	 *
	 * @param stream The stream at the location to parse
	 * @param matcher The parsing matcher that will attempt to match the beginning of the stream
	 * @param session The session to use to parse next piece of the stream
	 */
	void preParse(S stream, MatchData<? extends S> match);

	/**
	 * Called after attempting to parse a subsequence
	 *
	 * @param stream The stream at the start of the location that was parsed (or attempted)
	 * @param matcher The matcher that parsing attempted to use
	 * @param match The match resulting from the parsing, or null if the match could not be made
	 */
	void postParse(S stream, MatchData<? extends S> match);

	/** Causes any UI of this debugger to be displayed. Helpful for use from java breakpoints. */
	void display();
}
