package org.expresso.antlr;

import java.io.FileInputStream;

import org.antlr.v4.runtime.ANTLRInputStream;
import org.antlr.v4.runtime.CommonTokenStream;
import org.antlr.v4.runtime.TokenStream;
import org.expresso.Expression;
import org.expresso.grammars.java8.Java8Lexer;
import org.expresso.grammars.java8.Java8Parser;
import org.junit.Assert;
import org.junit.Test;

public class AntlrTester {
	@Test
	public void testAgainstThisClass() throws Exception{
		System.out.println("Parsing " + getClass().getName());
		// lexer splits input into tokens
		ANTLRInputStream input = new ANTLRInputStream(
			new FileInputStream("src/test/java/" + getClass().getName().replaceAll("\\.", "/") + ".java"));
		TokenStream tokens = new CommonTokenStream(new Java8Lexer(input));

		// parser generates abstract syntax tree
		Java8Parser parser = new Java8Parser(tokens);
		System.out.println("\nTranslating");
		Expression parsed = Expression.of(parser, parser.compilationUnit());

		System.out.println("Searching");
		Assert.assertNotNull(parsed.search().get("importDeclaration", "typeName").text("org.junit.Test"));
		Expression thisMethod = parsed.search().get("methodDeclaration")
			.where(srch -> srch.get("methodDeclarator").firstChild().text("testAgainstThisClass")).get("block").findAny();
		Assert.assertNotNull(thisMethod);

		Assert.assertEquals("parsed",
			thisMethod.search().get("localVariableDeclaration")
				.where(srch -> srch.get("unannType").text(Expression.class.getSimpleName())).get("variableDeclaratorId")//
				.find()//
				.toString());
	}
}
