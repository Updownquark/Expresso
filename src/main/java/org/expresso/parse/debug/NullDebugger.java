package org.expresso.parse.debug;

import org.expresso.parse.ExpressoParser;
import org.expresso.parse.ParseMatch;

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
	public void init(ExpressoParser<?> parser) {}

	@Override
	public void start(S text) {}

	@Override
	public void end(ParseMatch<? extends S> bestMatch) {}

	@Override
	public void fail(S stream, ParseMatch<? extends S> match) {}

	@Override
	public void preParse(S stream, MatchData<? extends S> match) {}

	@Override
	public void postParse(S stream, MatchData<? extends S> match) {}

	@Override
	public void display() {}
}
