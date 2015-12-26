package org.expresso.parse.debug;

import org.expresso.parse.ExpressoParser;
import org.expresso.parse.ParseMatch;
import org.expresso.parse.ParseMatcher;
import org.expresso.parse.ParseSession;

/**
 * A debugger that doesn't do anything
 *
 * @param <S> The type of stream to parse
 */
public class NullDebugger<S extends org.expresso.parse.BranchableStream<?, ?>> implements ExpressoParsingDebugger<S> {
	/** An instance of this debugger */
	@SuppressWarnings("rawtypes")
	public static NullDebugger INSTANCE = new NullDebugger();
	@Override
	public void init(ExpressoParser<?> parser) {
	}

	@Override
	public void start(S text) {
	}

	@Override
	public void end(ParseMatch<? extends S>... matches) {
	}

	@Override
	public void fail(S stream, ParseMatch<? extends S> match) {
	}

	@Override
	public void preParse(S stream, ParseMatcher<?> matcher, ParseSession session) {
	}

	@Override
	public void postParse(S stream, ParseMatcher<?> matcher, ParseMatch<? extends S> match) {
	}

	@Override
	public void matchDiscarded(ParseMatcher<?> matcher, ParseMatch<? extends S> match) {
	}

	@Override
	public void usedCache(ParseMatcher<?> matcher, ParseMatch<? extends S> match) {
	}
}
