package org.expresso;

import java.io.IOException;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.function.Predicate;

import org.expresso.stream.BranchableStream;
import org.expresso.types.OneOfExpressionType;
import org.qommons.collect.BetterList;
import org.qommons.collect.CollectionElement;
import org.qommons.collect.ElementId;

/**
 * A class of expressions, any of which can satisfy this type
 *
 * @param <S> The stream super-type of the expression types
 */
public class ExpressionClass<S extends BranchableStream<?, ?>> extends OneOfExpressionType<S> implements GrammarExpressionType<S> {
	private final ExpressoGrammar<S> theGrammar;
	private final String theName;
	private final List<ExpressionClass<S>> theParentClasses;
	private final List<ExpressionClass<S>> theChildClasses;
	private final Comparator<ExpressionType<? super S>> thePrioritizer;

	/**
	 * @param grammar The grammar that this class belongs to
	 * @param id The cache ID for the expression
	 * @param name The name of the class
	 * @param parentClasses The classes that this class extends
	 * @param childClasses The classes that extend this class (populated later externally)
	 * @param members The class's members, sorted by priority, then by order of occurrence
	 */
	public ExpressionClass(ExpressoGrammar<S> grammar, int id, String name, List<ExpressionClass<S>> parentClasses,
		List<ExpressionClass<S>> childClasses, BetterList<? extends ConfiguredExpressionType<S>> members) {
		super(id, members);
		theGrammar = grammar;
		theName = name;
		theParentClasses = parentClasses;
		theChildClasses = childClasses;
		thePrioritizer = (x1, x2) -> ((ConfiguredExpressionType<S>) x1).getPriority() - ((ConfiguredExpressionType<S>) x2).getPriority();
	}

	@Override
	public boolean isCacheable() {
		return true;
	}

	@Override
	public ExpressoGrammar<S> getGrammar() {
		return theGrammar;
	}

	@Override
	public String getName() {
		return theName;
	}

	@Override
	public BetterList<? extends ConfiguredExpressionType<S>> getComponents() {
		return (BetterList<? extends ConfiguredExpressionType<S>>) super.getComponents();
	}

	/** @return The classes that this class extends */
	public List<ExpressionClass<S>> getParentClasses() {
		return theParentClasses;
	}

	/** @return The classes that extend this class */
	public List<ExpressionClass<S>> getChildClasses() {
		return theChildClasses;
	}

	/**
	 * @param clazz The class to test
	 * @return True if this class is the same as or a direct or indirect extension of the given class
	 */
	public boolean doesExtend(ExpressionClass<S> clazz) {
		if (clazz == this)
			return true;
		for (ExpressionClass<S> parent : theParentClasses)
			if (parent.doesExtend(clazz))
				return true;
		return false;
	}

	// @Override
	// public <S2 extends S> Expression<S2> parse(ExpressoParser<S2> parser) throws IOException {
	// ExpressoParser<S2> stopRecurse = parser.withInterrupt(this, null);
	// for (CollectionElement<? extends ExpressionType<? super S>> component : getComponents().elements()) {
	// Expression<S2> possibility = stopRecurse.parseWith(//
	// component.get());
	// if (possibility != null)
	// return new ClassExpression<>(this, possibility, component.getElementId(), null, x -> true);
	// }
	// return null;
	// }

	@Override
	public String toString() {
		return "Class " + theName;
	}

	<S2 extends S> Expression<S2> recurse(ClassExpression<S2> expression, ExpressoParser<S2> parser) throws IOException {
		ExpressoParser<S2> stopRecurse = parser.withInterrupt(this, expression);
		CollectionElement<? extends ExpressionType<? super S>> component;
		// TODO This wasn't working for some expressions (e.g. "a+b")
		// component = getComponents().getElement(componentId);
		component = getComponents().getTerminalElement(true);
		// ExpressionType<? super S> lowerBoundComponent = component.get();
		while (component != null) {
			// if (!after && thePrioritizer != null) {
			// after = thePrioritizer.compare(component.get(), lowerBoundComponent) != 0;
			// }
			ElementId compId = component.getElementId();

			Expression<S2> recursed = stopRecurse.parseWith(component.get(), false);
			if (recursed != null && expression.theFilter.test(recursed) && find(recursed, expression))
				return new ClassExpression<>(this, recursed, component.getElementId(), expression.theSource, x -> find(x, expression));
			component = getComponents().getAdjacentElement(compId, true);
		}
		return null;
	}

	static boolean find(Expression<?> ancestor, Expression<?> descendant) {
		if (ancestor == descendant)
			return true;
		for (Expression<?> child : ancestor.getChildren()) {
			if (find(child, descendant))
				return true;
			else if (child.length() > 0)
				return false;
		}
		return false;
	}

	<S2 extends S> Expression<S2> lowerPriority(ElementId componentId, ExpressoParser<S2> parser) throws IOException {
		ExpressoParser<S2> stopRecurse = parser.withInterrupt(this, null);
		CollectionElement<? extends ExpressionType<? super S>> component = getComponents().getAdjacentElement(componentId, true);
		while (component != null) {
			Expression<S2> possibility = stopRecurse.parseWith(//
				component.get());
			if (possibility != null)
				return new ClassExpression<>(this, possibility, component.getElementId(), null, x -> true);
			component = getComponents().getAdjacentElement(component.getElementId(), true);
		}
		return null;
	}

	private static class ClassExpression<S extends BranchableStream<?, ?>> implements Expression<S> {
		private final ExpressionClass<? super S> theType;
		private final Expression<S> theComponent;
		private final ElementId theComponentId;
		private final ClassExpression<S> theSource;
		final Predicate<Expression<S>> theFilter;

		ClassExpression(ExpressionClass<? super S> type, Expression<S> component, ElementId componentId, ClassExpression<S> source,
			Predicate<Expression<S>> filter) {
			theType = type;
			theComponent = component;
			theComponentId = componentId;
			theSource = source;
			theFilter = filter;
		}

		@Override
		public OneOfExpressionType<? super S> getType() {
			return theType;
		}

		@Override
		public S getStream() {
			return theComponent.getStream();
		}

		@Override
		public int length() {
			return theComponent.length();
		}

		@Override
		public List<? extends Expression<S>> getChildren() {
			return Arrays.asList(theComponent);
		}

		@Override
		public Expression<S> nextMatch(ExpressoParser<S> parser) throws IOException {
			// Try to branch the component itself
			ExpressoParser<S> stopRecurse = parser.withInterrupt(theType, null);
			Expression<S> next = stopRecurse.nextMatch(theComponent);
			if (next != null && theFilter.test(next))
				return new ClassExpression<>(theType, next, theComponentId, this, theFilter);
			else
				return nextMatch2(parser);
		}

		private Expression<S> nextMatch2(ExpressoParser<S> parser) throws IOException {
			// Parse lower-priority matches, interrupting recursive class parsing with this expression
			Expression<S> next = theType.recurse(this, parser);
			if (next != null)
				return next;

			if (theSource != null)
				return theSource.nextMatch2(parser);
			else
				return theType.lowerPriority(theComponentId, parser);
		}

		@Override
		public int getErrorCount() {
			return theComponent.getErrorCount();
		}

		@Override
		public Expression<S> getFirstError() {
			return theComponent.getFirstError();
		}

		@Override
		public int getLocalErrorRelativePosition() {
			return -1;
		}

		@Override
		public String getLocalErrorMessage() {
			return null;
		}

		@Override
		public int getMatchQuality() {
			return theComponent.getMatchQuality();
		}

		@Override
		public boolean isInvariant() {
			return false;
		}

		@Override
		public Expression<S> unwrap() {
			return theComponent.unwrap();
		}

		@Override
		public boolean equals(Object o) {
			if (this == o)
				return true;
			else if (!(o instanceof ClassExpression))
				return false;
			ClassExpression<S> other = (ClassExpression<S>) o;
			return getType().equals(other.getType()) && getStream().getPosition() == other.getStream().getPosition()
				&& theComponent.equals(other.theComponent);
		}

		@Override
		public int hashCode() {
			return Objects.hash(theType, getStream().getPosition(), theComponent);
		}

		@Override
		public StringBuilder print(StringBuilder str, int indent, String metadata) {
			for (int i = 0; i < indent; i++)
				str.append('\t');
			str.append(theType).append(metadata);
			if (length() == 0)
				str.append("(empty)");
			else
				str.append(": ").append(printContent(true));
			str.append('\n');
			theComponent.print(str, indent + 1, "");
			return str;
		}

		@Override
		public String toString() {
			return print(new StringBuilder(), 0, "").toString();
		}
	}
}
