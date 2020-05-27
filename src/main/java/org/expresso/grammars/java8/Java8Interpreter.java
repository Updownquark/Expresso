package org.expresso.grammars.java8;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedList;
import java.util.List;

import org.expresso.Expression;
import org.expresso.runtime.CompilationException;
import org.expresso.runtime.ControlFlowDirective;
import org.expresso.runtime.typed.EmptyStatement;
import org.expresso.runtime.typed.TypedBlock;
import org.expresso.runtime.typed.TypedControlFlowDirectiveStatement;
import org.expresso.runtime.typed.TypedIfStatement;
import org.expresso.runtime.typed.TypedLabeledStatement;
import org.expresso.runtime.typed.TypedLiteralStatement;
import org.expresso.runtime.typed.TypedLoop;
import org.expresso.runtime.typed.TypedReturnDirective;
import org.expresso.runtime.typed.TypedStatement;
import org.expresso.runtime.typed.TypedSwitchStatement;
import org.expresso.runtime.typed.TypedThrowDirective;
import org.expresso.runtime.typed.TypedTryStatement;
import org.expresso.runtime.typed.TypedVariableDeclaration;

import com.google.common.reflect.TypeToken;

public abstract class Java8Interpreter<E extends JavaEnvironment<E>> {
	public static final TypeToken<Boolean> BOOLEAN = TypeToken.of(boolean.class);
	public static final TypeToken<Character> CHAR = TypeToken.of(char.class);
	public static final TypeToken<String> STRING = TypeToken.of(String.class);
	public static final TypeToken<Integer> INT = TypeToken.of(int.class);
	public static final TypeToken<Long> LONG = TypeToken.of(long.class);
	public static final TypeToken<Float> FLOAT = TypeToken.of(float.class);
	public static final TypeToken<Double> DOUBLE = TypeToken.of(double.class);
	public static final TypeToken<Void> VOID = TypeToken.of(void.class);
	public static final TypeToken<Object> OBJECT = TypeToken.of(Object.class);
	public static final TypeToken<Throwable> THROWABLE = TypeToken.of(Throwable.class);

	private final LinkedList<E> theStack;

	public Java8Interpreter() {
		theStack = new LinkedList<>();
	}

	public <X> TypedStatement<E, ? extends X> evaluate(Expression expression, E env, TypeToken<X> targetType) throws CompilationException {
		TypedStatement<E, ?> evald = _evaluate(expression, env, targetType);
		if (!targetType.isAssignableFrom(evald.getReturnType())) // TODO better exception
			throw new IllegalStateException("Bad return type: " + evald + " (" + evald.getReturnType() + ") for " + targetType);
		return (TypedStatement<E, ? extends X>) evald;
	}

	private <X> TypedStatement<E, ? extends X> _evaluate(Expression expression, E env, TypeToken<X> targetType)
		throws CompilationException {
		switch (expression.getType()) {
		// Evaluated types
		case "DecimalIntegerLiteral":
			boolean isLong = expression.search().get("IntegerTypeSuffix").findAny() != null;
			if (isLong)
				return literal(expression, LONG, Long.parseLong(expression.toString()), targetType);
			else
				return literal(expression, INT, Integer.parseInt(expression.toString()), targetType);
		case "HexIntegerLiteral":
			isLong = expression.search().get("IntegerTypeSuffix").findAny() != null;
			String text = expression.search().get("HexDigits").find().toString();
			if (isLong)
				return literal(expression, LONG, Long.parseLong(text, 16), targetType);
			else
				return literal(expression, INT, Integer.parseInt(text, 16), targetType);
		case "OctalIntegerLiteral":
			isLong = expression.search().get("IntegerTypeSuffix").findAny() != null;
			text = expression.search().get("OctalDigits").find().toString();
			if (isLong)
				return literal(expression, LONG, Long.parseLong(text, 8), targetType);
			else
				return literal(expression, INT, Integer.parseInt(text, 8), targetType);
		case "BinaryIntegerLiteral":
			isLong = expression.search().get("IntegerTypeSuffix").findAny() != null;
			text = expression.search().get("BinaryDigits").find().toString();
			if (isLong)
				return literal(expression, LONG, Long.parseLong(text, 2), targetType);
			else
				return literal(expression, INT, Integer.parseInt(text, 2), targetType);
		case "DecimalFloatingPointLiteral":
			Expression type = expression.search().get("FloatingTypeSuffix").findAny();
			text = expression.toString();
			if (type != null)
				text = text.substring(0, text.length() - 1);
			if (type == null || type.toString().equalsIgnoreCase("d"))
				return literal(expression, DOUBLE, Double.parseDouble(text), targetType);
			else
				return literal(expression, FLOAT, Float.parseFloat(text), targetType);
		case "BooleanLiteral":
			return literal(expression, BOOLEAN, "true".equals(expression.toString()), targetType);
		case "CharacterLiteral":
			Expression escaped = expression.search().get("EscapeSequence").findAny();
			if (escaped == null)
				return literal(expression, CHAR, expression.toString().charAt(0), targetType);
			else
				return literal(expression, CHAR, evaluateEscape(escaped), targetType);
		case "StringLiteral":
			return literal(expression, STRING, compileString(expression.search().get("StringCharacters").find().getComponents()),
				targetType);
		case "NullLiteral":
			return literal(expression, targetType, null, targetType);
		// Statement structures
		case "block":
			List<Expression> content = expression.search().get("blockStatement").findAll();
			List<TypedStatement<E, ? extends X>> statements = new ArrayList<>(content.size());
			for (Expression c : content)
				statements.add(evaluate(c.getComponents().getFirst(), env.scope(null), targetType));
			return new TypedBlock<>(expression, targetType, Collections.unmodifiableList(statements));
		case "statementExpressionList":
			content = expression.search().get("statementExpression").findAll();
			statements = new ArrayList<>(content.size());
			for (Expression c : content)
				statements.add(evaluate(c.getComponents().getFirst(), env, targetType));
			return new TypedBlock<>(expression, targetType, Collections.unmodifiableList(statements));
		case "emptyStatement":
			return new EmptyStatement<>(expression, targetType);
		case "synchronizedStatement":
			return new SynchronizedStatement<>(expression, targetType, //
				evaluate(expression.getComponent("expression"), env, OBJECT), //
				((TypedBlock<E, X>) evaluate(expression.getComponent("block"), env, targetType)).getStatements());
		case "tryStatement":
			Expression child = expression.getComponent("tryWithResourcesStatement");
			if (child == null)
				return parseTryStatement(expression, env, targetType);
			else
				expression = child;
			//$FALL-THROUGH$
		case "tryWithResourcesStatement":
			return parseTryStatement(expression, env, targetType);
		case "labeledStatement":
		case "labeledStatementNoShortIf":
			String label = expression.getComponents().getFirst().toString();
			return new TypedLabeledStatement<>(expression, targetType, label, //
				evaluate(expression.getComponents().getLast(), env.scope(label), targetType));
		case "breakStatement":
		case "continueStatement":
			Expression target = expression.getComponents().get(1);
			if (!(target.getType().equals("Identifier")))
				target = null;
			return new TypedControlFlowDirectiveStatement<>(expression, targetType,
				ControlFlowDirective.valueOf(expression.getComponents().getFirst().toString().toUpperCase()), //
				target == null ? null : target.toString());
		case "returnStatement":
			target = expression.getComponents().get(1);
			if (!(target.getType().equals("expression")))
				target = null;
			boolean isVoid = targetType.equals(VOID);
			if (target == null && !isVoid)
				throw new CompilationException(expression, "Value expected for return statement in non-void method");
			else if (target != null && isVoid)
				throw new CompilationException(expression, "No value expected for return statement in void method");
			return new TypedReturnDirective<>(expression, targetType, //
				target == null ? null : evaluate(target, env, targetType));
		case "throwStatement":
			target = expression.getComponents().get(1);
			return new TypedThrowDirective<>(expression, targetType, //
				evaluate(target, env, THROWABLE));
		case "assertStatement":
			target = expression.getComponents().size() < 5 ? null : expression.getComponents().get(3);
			return new AssertStatement<E, X>(expression, targetType, //
				evaluate(expression.getComponents().get(1), env, BOOLEAN), //
				target == null ? null : evaluate(target, env, targetType));
		// Control flow statements
		case "ifThenStatement":
		case "ifThenElseStatement":
		case "ifThenElseStatementNoShortIf":
			target = expression.getComponents().size() < 7 ? null : expression.getComponents().get(5);
			return new TypedIfStatement<>(expression, targetType, //
				evaluate(expression.getComponents().get(2), env, BOOLEAN), //
				evaluate(expression.getComponents().get(4), env, targetType), //
				target == null ? null : evaluate(target, env, targetType));
		case "switchStatement":
			return parseCaseStatement(expression, env, targetType);
		// Loop statements
		case "doStatement":
		case "whileStatement":
			return new TypedLoop<>(expression, targetType, null, //
				evaluate(expression.getComponent("expression"), env, BOOLEAN), null, false, //
				evaluate(expression.getComponent("statement"), env, targetType));
		case "whileStatementNoShortIf":
			return new TypedLoop<>(expression, targetType, null, //
				evaluate(expression.getComponent("expression"), env, BOOLEAN), null, false, //
				evaluate(expression.getComponent("statementNoShortIf"), env, targetType));
		case "basicForStatement":
		case "basicForStatementNoShortIf":
			Expression init = expression.getComponent("forInit");
			Expression update = expression.getComponent("forUpdate");
			return new TypedLoop<>(expression, targetType, //
				init == null ? null : evaluate(init.getComponents().getFirst(), env, targetType), //
				evaluate(expression.getComponent("expression"), env, BOOLEAN), //
				update == null ? null : evaluate(update, env, targetType), true, //
				evaluate(expression.getComponents().getLast(), env, targetType));
		case "enhancedForStatement":
		case "enhancedForStatementNoShortIf":
			// TODO
			// Fields, methods, and constructors
		case "localVariableDeclaration":
			boolean isFinal = expression.toString().contains("final ");
			TypeToken<? extends X> varType = evaluateType(expression.search().get("unannType").find(), env, targetType);
			List<TypedVariableDeclaration.TypedDeclaredVariable<E, ?>> vbls = new ArrayList<>();
			for (Expression varDec : expression.search().get("variableDeclarator").findAll()) {
				int dimCount = varDec.search().text("[").findAll().size();
				TypeToken<?> varType_i = arrayType(varType, dimCount);
				vbls.add(declareVariable(varDec, env, varType_i, isFinal));
			}
			return new TypedVariableDeclaration<>(expression, targetType, vbls, isFinal);
		case "fieldAccess":
		case "fieldAccess_lfno_primary":
			// TODO
		case "arrayAccess":
		case "arrayAccess_lfno_primary":
			// TODO
		case "methodInvocation":
		case "methodInvocation_lfno_primary":
			// TODO
		case "classInstanceCreationExpression":
		case "classInstanceCreationExpression_lf_primary":
		case "classInstanceCreationExpression_lfno_primary":
			// TODO
		case "lambdaExpression":
			// TODO
			// Expressions
		case "assignment":
		case "castExpression":
		case "primaryNoNewArray":
		case "methodReference":
		case "methodReference_lfno_primary":
		case "arrayCreationExpression":
		case "postfixExpression":
		case "postIncrementExpression":
		case "postDecrementExpression":
		case "postDecrementExpression_lf_postfixExpression":
		case "expressionName":
		case "primary":
		case "primaryNoNewArray_lfno_primary":
		case "primaryNoNewArray_lf_primary":
			// TODO
			// Possible binary-operation types
		case "conditionalExpression":
		case "conditionalOrExpression":
		case "conditionalAndExpression":
		case "inclusiveOrExpression":
		case "exclusiveOrExpression":
		case "andExpression":
		case "equalityExpression":
		case "relationalExpression":
		case "additiveExpression":
		case "multiplicativeExpression":
			// Possible unary-operation types
		case "unaryExpression":
		case "preIncrementExpression":
		case "preDecrementExpression":
		case "unaryExpressionNotPlusMinus":
			// Pass-through types
		case "literal":
		case "IntegerLiteral":
		case "FloatingPointLiteral":
		case "localVariableDeclarationStatement":
		case "statement":
		case "statementWithoutTrailingSubstatement":
		case "expressionStatement":
		case "statementExpression":
		case "statementNoShortIf":
		case "expression":
		case "assignmentExpression":
		case "constantExpression":
		case "forStatement":
		case "forStatementNoShortIf":
			return evaluate(expression.getComponents().getFirst(), env, targetType);
		// Unsupported types
		case "HexadecimalFloatingPointLIteral":
			throw new IllegalArgumentException("Hexadecimal floating-point literals are not supported yet");
		case "classDeclaration":
			throw new IllegalArgumentException("Inner class declarations are not supported");
		}
		// TODO Auto-generated method stub
	}

	public <X> TypeToken<? extends X> evaluateType(Expression expr, E env, TypeToken<X> targetType) throws CompilationException {}

	public <X> TypeToken<X> getCommonType(List<TypeToken<? extends X>> types) {}

	public TypeToken<?> arrayType(TypeToken<?> componentType, int dimension) {}

	public <X, V> TypedLiteralStatement<E, ? extends X> literal(Expression expression, TypeToken<V> type, V value, TypeToken<X> targetType)
		throws CompilationException {
		if (targetType != type && !targetType.isAssignableFrom(type))
			throw new CompilationException(expression, type + " " + expression + " is not acceptable for type " + targetType);
		return (TypedLiteralStatement<E, ? extends X>) new TypedLiteralStatement<E, V>(expression, type, value);
	}

	private <T> TypedVariableDeclaration.TypedDeclaredVariable<E, T> declareVariable(Expression varDec, E env, TypeToken<T> type,
		boolean isFinal)
		throws CompilationException {
		String name = varDec.getComponents().getFirst().getComponents().getFirst().toString();
		env.declareVariable(name, type, isFinal);
		Expression init = varDec.search().get("variableInitializer").findAny();
		TypedStatement<E, T> initStmt = init == null ? null : (TypedStatement<E, T>) evaluate(init, env, type);
		return declareVariable(name, type, initStmt);
	}

	protected abstract <T> TypedVariableDeclaration.TypedDeclaredVariable<E, T> declareVariable(String name, TypeToken<T> type,
		TypedStatement<E, T> initStmt);

	private <R, X extends Throwable> TypedTryStatement<E, R> parseTryStatement(Expression expression, E env, TypeToken<R> targetType)
		throws CompilationException {
		List<Expression> resourceExprs = expression.getComponents("resourceSpecification", "resourceList", "resource");
		List<TypedStatement<E, ? extends AutoCloseable>> resources = new ArrayList<>(resourceExprs.size());

		// TODO resources;
		List<Expression> catchExprs = expression.getComponents("catches", "catchClause");
		List<TypedTryStatement.TypedCatch<E, R, ?>> catches = new ArrayList<>(catchExprs.size());
		for (Expression catchEx : catchExprs) {
			List<TypeToken<? extends X>> exceptionTypes = new ArrayList<>();
			List<Class<? extends X>> exceptionClasses = new ArrayList<>(exceptionTypes.size());
			for (Expression exTypeExpr : catchEx.getComponent("catchFormalParameter", "catchType").getComponents()) {
				if (exTypeExpr.getType().endsWith("Type"))
					exceptionTypes.add((TypeToken<? extends X>) evaluateType(exTypeExpr, env, THROWABLE));
			}
			TypeToken<X> lcdType = getCommonType(exceptionTypes);
			String vblName = catchEx.getComponent("catchFormalParameter", "variableDeclaratorId", "Identifier").toString();
			boolean isFinal = catchEx.getComponent("catchFormalParameter").toString().contains("final ");
			catches.add(new TypedTryStatement.TypedCatch<>(targetType, Collections.unmodifiableList(exceptionClasses), //
				declareVariable(vblName, lcdType, null), //
				(TypedBlock<E, R>) evaluate(catchEx.getComponent("block"), env, null), isFinal));
		}
		Expression finallyEx = expression.getComponent("finally_");
		return new TypedTryStatement<>(expression, targetType, Collections.unmodifiableList(resources), //
			(TypedBlock<E, R>) evaluate(expression.getComponent("block"), env, null), Collections.unmodifiableList(catches),
			finallyEx == null ? null : (TypedBlock<E, R>) evaluate(finallyEx.getComponent("block"), env, null));
	}

	private <X, C> TypedSwitchStatement<E, X, C> parseCaseStatement(Expression expression, E env, TypeToken<X> targetType)
		throws CompilationException {
		TypedStatement<E, C> condition = (TypedStatement<E, C>) evaluate(expression.getComponent("expression"), env, OBJECT);
		List<Expression> sgExs = expression.getComponents("switchBlock", "switchBlockStatementGroup");
		List<Expression> terminalLabels = expression.getComponents("switchBlock", "switchLabel");
		List<TypedSwitchStatement.TypedCaseStatement<E, X, C>> cases = new ArrayList<>(sgExs.size() + terminalLabels.size());
		for (Expression sgEx : sgExs) {
			List<Expression> caseValueExs = sgEx.getComponents("switchLabels", "switchLabel");
			List<TypedStatement<E, ? extends C>> caseValues = new ArrayList<>(caseValueExs.size());
			for (Expression cvEx : caseValueExs) {
				if (cvEx.getComponents().getFirst().toString().equals("default"))
					caseValues.add(new TypedSwitchStatement.TypedDefaultCaseLabel<>(cvEx, condition.getReturnType()));
				else
					caseValues.add(evaluate(cvEx.getComponents().get(1), env, condition.getReturnType()));
			}
			List<Expression> statementExs = sgEx.getComponents("blockStatements", "blockStatement");
			List<TypedStatement<E, ? extends X>> statements = new ArrayList<>(statementExs.size());
			for (Expression stmtEx : statementExs)
				statements.add(evaluate(//
					stmtEx.getComponents().getFirst(), env, targetType));
			cases.add(new TypedSwitchStatement.TypedCaseStatement<>(sgEx, targetType, Collections.unmodifiableList(caseValues),
				Collections.unmodifiableList(statements)));
		}
		return new TypedSwitchStatement<>(expression, targetType, condition, Collections.unmodifiableList(cases), env);
	}

	private static char evaluateEscape(Expression escaped) {
		Expression check = escaped.search().get("OctalEscape").findAny();
		if (check != null)
			return (char) Integer.parseInt(check.toString().substring(1), 8);
		check = escaped.search().get("UnicodeEscape").findAny();
		if (check != null)
			return (char) Integer.parseInt(check.toString().substring(2), 16);
		int codeChar = escaped.toString().charAt(1);
		switch (codeChar) {
		case 'n':
			return '\n';
		case 't':
			return '\t';
		case '\\':
			return '\\';
		case 'r':
			return '\r';
		case 'b':
			return '\b';
		case 'f':
			return '\f';
		}
		throw new IllegalArgumentException("Unrecognized escape sequence: " + escaped);
	}

	private static String compileString(List<Expression> contents) {
		StringBuilder str = new StringBuilder(contents.size());
		for (Expression c : contents) {
			if (c.getComponents().isEmpty() || !"EscapeSequence".equals(c.getComponents().getFirst().getType()))
				str.append(c.toString());
			else
				str.append(evaluateEscape(c.getComponents().getFirst()));
		}
		return str.toString();
	}
}
