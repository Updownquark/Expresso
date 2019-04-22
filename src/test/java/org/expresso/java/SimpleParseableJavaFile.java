package org.expresso.java;

/**
 * This file is only here to be parsed by a unit test
 * 
 * @author Andrew Butler
 */
public class SimpleParseableJavaFile {
	/**
	 * Adds 2 integers
	 * 
	 * @param a The first number to add
	 * @param b The second integer to add
	 * @return a+b
	 */
	public static int add(int a, int b) {
		return a + b;
	}

	/**
	 * Subtracts 2 integers
	 * 
	 * @param a The left argument
	 * @param b The right argument
	 * @return a-b
	 */
	public static int subtract(int a, int b) {
		return a - b;
	}

	/**
	 * Performs a simple integer operation
	 * 
	 * @param a The first argument
	 * @param b The second argument
	 * @param c The third argument
	 * @return a + b * c
	 */
	public static int op1(int a, int b, int c) {
		return a + b * c;
	}
}
