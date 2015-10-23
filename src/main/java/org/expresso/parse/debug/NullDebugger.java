package org.expresso.parse.debug;

import org.expresso.parse.ExpressoParser;
import org.expresso.parse.ParseMatch;
import org.expresso.parse.ParseMatcher;

/**
 * A debugger that doesn't do anything
 *
 * @param <S> The type of stream to parse
 */
public class NullDebugger<S extends org.expresso.parse.BranchableStream<?, ?>> implements ExpressoParsingDebugger<S> {
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
	public void preParse(S stream, ParseMatcher<?> matcher) {
	}

	@Override
	public void postParse(S stream, ParseMatcher<?> matcher, ParseMatch<? extends S> match) {
	}

	@Override
	public void matchDiscarded(ParseMatch<? extends S> match) {
	}

	@Override
	public void usedCache(ParseMatch<? extends S> match) {
	}
}
