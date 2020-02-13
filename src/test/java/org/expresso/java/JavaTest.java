package org.expresso.java;

import java.io.IOException;
import java.io.InputStreamReader;
import java.io.Reader;
import java.net.URL;
import java.util.function.Supplier;

import org.expresso.DefaultGrammarParser;
import org.expresso.Expression;
import org.expresso.ExpressionTester;
import org.expresso.ExpressoGrammar;
import org.expresso.ExpressoGrammarParser;
import org.expresso.GrammarExpressionType;
import org.expresso.stream.CharSequenceStream;
import org.junit.Assert;
import org.qommons.ArrayUtils;
import org.qommons.BreakpointHere;
import org.qommons.QommonsUtils;
import org.qommons.config.QommonsConfig;

/** An abstract class that simplifies testing expressions using the embedded Java grammar */
public abstract class JavaTest {
	/** Don't terminate early if debugging, 1 second otherwise */
	public static final long TIMEOUT = BreakpointHere.isDebugEnabled() == null ? 1000 : 0;

	private ExpressoGrammar<CharSequenceStream> theParser;

	/**
	 * Builds the parser from the embedded Java grammar
	 * 
	 * @throws IOException If the grammar file cannot be read
	 */
	public void setupParser() throws IOException {
		ExpressoGrammarParser<CharSequenceStream> grammarParser = ExpressoGrammarParser.defaultText();
		theParser = grammarParser.parseGrammar(DefaultGrammarParser.class.getResource("/org/expresso/grammars/Java8v3.xml"));
	}

	@SuppressWarnings("deprecation")
	private static <T> T getWithin(Supplier<T> task, long time) {
		if (time == 0)
			time = Long.MAX_VALUE;
		// return task.get();
		boolean[] done = new boolean[1];
		Object[] result = new Object[1];
		Throwable[] ex = new Throwable[1];
		Thread worker = new Thread(() -> {
			try {
				result[0] = task.get();
				done[0] = true;
			} catch (ThreadDeath e) {
			} catch (RuntimeException | Error e) {
				ex[0] = e;
				done[0] = true;
			}
		}, JavaTest.class.getSimpleName() + " worker");
		worker.start();
		long start = System.currentTimeMillis();
		while (System.currentTimeMillis() - start < time && !done[0]) {
			try {
				Thread.sleep(10);
			} catch (InterruptedException e) {
			}
		}
		if (done[0]) {
			if (ex[0] != null) {
				ex[0].setStackTrace(QommonsUtils.patchStackTraces(//
					ex[0].getStackTrace(), //
					ArrayUtils.remove(Thread.currentThread().getStackTrace(), 0), //
					Thread.class.getName(), "run"));
				if (ex[0] instanceof RuntimeException)
					throw (RuntimeException) ex[0];
				else
					throw (Error) ex[0];
			} else
				return (T) result[0];
		} else {
			worker.stop();
			throw new IllegalStateException("Task took longer than " + QommonsUtils.printTimeLength(time));
		}
	}

	private Expression<CharSequenceStream> parse(String expression, String type, boolean checkForErrors, long time) {
		return parse(CharSequenceStream.from(expression), type, checkForErrors, time);
	}

	private Expression<CharSequenceStream> parse(CharSequenceStream stream, String type, boolean checkForErrors, long time) {
		return getWithin(() -> {
			GrammarExpressionType<CharSequenceStream> component = theParser.getExpressionsByName().get(type);
			if (component == null)
				component = theParser.getExpressionClasses().get(type);
			if (component == null)
				throw new IllegalArgumentException("No such type or class: " + type);
			Expression<CharSequenceStream> result;
			try {
				result = theParser.parse(stream, component, checkForErrors ? 0 : -5);
			} catch (IOException e) {
				throw new IllegalStateException(e);
			}
			if (checkForErrors) {
				Assert.assertTrue("No result!", result != null);
				if (result.getErrorCount() > 0)
					Assert.assertEquals(result.getFirstError().getLocalErrorMessage(), 0, result.getErrorCount());
			}
			Assert.assertEquals("Incomplete match: " + result.printContent(false), stream.length(), result.length());
			return result;
		}, time);
	}

	/**
	 * Tests an expression string
	 * 
	 * @param expression The expression to parse
	 * @param expressionType The expression type to parse with
	 * @param errorFree Whether to assert that the result has no errors
	 * @param time The time to allow for parsing
	 * @param validation A tester to validate the parsed result
	 */
	protected void testExpression(String expression, String expressionType, boolean errorFree, long time, ExpressionTester validation) {
		Expression<CharSequenceStream> result = parse(expression, expressionType, errorFree, time);
		validation.test(result);
	}

	/**
	 * Tests an expression parsed from a classpath-resolved java file
	 * 
	 * @param fileName The java file to parse
	 * @param time The time to allow for parsing
	 * @param validation A tester to validate the parsed result
	 * @throws IOException If an error occurs locating or reading the file
	 */
	protected void testExpressionOnFile(String fileName, long time, ExpressionTester validation) throws IOException {
		URL self = QommonsConfig.toUrl("src/test/java/org/expresso/java/" + getClass().getSimpleName() + ".java");
		URL file = QommonsConfig.toUrl(QommonsConfig.resolve(fileName, self.toString()));
		Expression<CharSequenceStream> result;
		// These lines are for potentially inspecting the file extension and using different grammar based on that,
		// but I'm not set up to do anything like that in this tester yet.
		// int lastDot=fileName.lastIndexOf('.');
		// String extension=lastDot<0 ? null : fileName.substring(lastDot+1);
		try (Reader reader = new InputStreamReader(file.openStream())) {
			result = parse(CharSequenceStream.from(reader, 4096), "java-file", true, time);
		}
		result = result.unwrap();
		validation.test(result);
	}
}
