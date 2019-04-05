package org.expresso3;

import java.io.IOException;
import java.io.InputStream;
import java.net.URL;

import org.expresso.stream.BinarySequenceStream;
import org.expresso.stream.BranchableStream;
import org.expresso.stream.CharSequenceStream;

/**
 * Parses a {@link ExpressoGrammar grammar} from a stream
 *
 * @param <S> The type of stream that grammars produced by this parser can themselves parse
 */
public interface ExpressoGrammarParser<S extends BranchableStream<?, ?>> {
	/**
	 * @param name The name for the grammar
	 * @param stream The stream to parse the grammar data from
	 * @return The parsed grammar
	 * @throws IOException If the grammar data cannot be read
	 * @throws IllegalArgumentException If the grammar cannot be parsed
	 */
	ExpressoGrammar<S> parseGrammar(String name, InputStream stream) throws IOException, IllegalArgumentException;

	/**
	 * Parses a grammar from a URL file
	 * 
	 * @param url The URL of the grammar file to parse
	 * @return The parsed grammar
	 * @throws IOException If the grammar data cannot be read
	 * @throws IllegalArgumentException If the grammar cannot be parsed
	 */
	default ExpressoGrammar<S> parseGrammar(URL url) throws IOException, IllegalArgumentException {
		try (InputStream stream = url.openStream()) {
			return parseGrammar(url.toString(), stream);
		}
	}

	/** @return A default-configured text grammar parser */
	static ExpressoGrammarParser<CharSequenceStream> defaultText() {
		return DefaultGrammarParser.forText();
	}

	/** @return A default-configured binary grammar parser */
	static ExpressoGrammarParser<BinarySequenceStream> defaultBinary() {
		return DefaultGrammarParser.forBinary();
	}
}
