package org.expresso3;

import java.io.IOException;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;

import org.expresso.stream.BranchableStream;
import org.expresso3.types.OneOfExpressionType;
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

	@Override
	public <S2 extends S> Expression<S2> parse(ExpressoParser<S2> parser) throws IOException {
		RecursionRegulator<S2> regulator = (RecursionRegulator<S2>) parser.getState(this);
		if (regulator == null && thePrioritizer != null) {
			regulator = new RecursionRegulator<>();
			parser = parser.withState(this, regulator);
		} else if (regulator.isInterrupting)
			return regulator.interrupt;
		Runnable done = regulator.interrupt(null);
		try {
			for (CollectionElement<? extends ExpressionType<? super S>> component : getComponents().elements()) {
				Expression<S2> possibility = parser.parseWith(//
					component.get());
				if (possibility != null)
					return new ClassExpression<>(this, possibility, component.getElementId(), possibility.isInvariant(), null, false);
			}
		} finally {
			done.run();
		}
		return null;
	}

	@Override
	public String toString() {
		return "Class " + theName;
	}

	class RecursionRegulator<S2 extends S> {
		boolean isInterrupting;
		Expression<S2> interrupt;

		Runnable interrupt(Expression<S2> interrupt) {
			this.interrupt = interrupt;
			isInterrupting = true;
			return () -> {
				this.interrupt = null;
				isInterrupting = false;
			};
		}
	}

	<S2 extends S> Expression<S2> recurse(ClassExpression<S2> expression, ElementId componentId, ExpressoParser<S2> parser)
		throws IOException {
		// HACK!! We only want to bother about recursion with classes,
		// since those are the only one-of operations that can be referred to by other components
		// Eventually, this code should go into ExpressionClass itself
		RecursionRegulator<S2> regulator = (RecursionRegulator<S2>) parser.getState(this);
		Runnable done;
		if (regulator != null)
			done = regulator.interrupt(new InvariantExpression<>(expression));
		else
			done = () -> {
			};
		try {
			CollectionElement<? extends ExpressionType<? super S>> component;
			if (regulator != null) // TODO always non-null
				component = getComponents().getElement(componentId);
			else
				component = getComponents().getAdjacentElement(componentId, true);

			ExpressionType<? super S> lowerBoundComponent = component.get();
			boolean after = false;
			while (component != null) {
				// if (!after && thePrioritizer != null) {
				// after = thePrioritizer.compare(component.get(), lowerBoundComponent) != 0;
				// }
				ElementId compId = component.getElementId();

				Expression<S2> recursed = parser.parseWith(component.get());
				if (recursed != null//
					&& (after || find(recursed, regulator.interrupt))// Not sure about this line
				)
					return new ClassExpression<>(this, recursed, component.getElementId(), false, expression.theSource,
						expression.useSourceNext);
				after = true;
				component = getComponents().getAdjacentElement(compId, true);
			}
			return null;
		} finally {
			done.run();
		}
	}

	private static boolean find(Expression<?> ancestor, Expression<?> descendant) {
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

	private static class ClassExpression<S extends BranchableStream<?, ?>> implements Expression<S> {
		private final ExpressionClass<? super S> theType;
		private final Expression<S> theComponent;
		private final ElementId theComponentId;
		private final boolean isInvariant;
		private final ClassExpression<S> theSource;
		private final boolean useSourceNext;

		ClassExpression(ExpressionClass<? super S> type, Expression<S> component, ElementId componentId, boolean invariant,
			ClassExpression<S> source, boolean useSourceNext) {
			theType = type;
			theComponent = component;
			theComponentId = componentId;
			isInvariant = invariant;
			theSource = source;
			this.useSourceNext = useSourceNext;
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
			Expression<S> next = parser.nextMatch(theComponent);
			if (next != null)
				return new ClassExpression<>(theType, next, theComponentId, false, this, true);
			else
				return nextMatch2(parser);
		}

		private Expression<S> nextMatch2(ExpressoParser<S> parser) throws IOException {
			ExpressionClass<? super S>.RecursionRegulator<S> regulator = (ExpressionClass<? super S>.RecursionRegulator<S>) parser
				.getState(theType);
			if (regulator == null) {
				regulator = theType.new RecursionRegulator<>();
				parser = parser.withState(theType, regulator);
			} else if (regulator.isInterrupting)
				return null; // TODO When does this happen?

			// Parse lower-priority matches, interrupting recursive class parsing with this expression
			Expression<S> next = theType.recurse(this, theComponentId, parser);
			if (next != null)
				return next;

			if (theSource != null)
				return theSource.nextMatch2(parser);

			return null;
			/*{ // Next, try to branch the component itself TODO ORDER
				OneOfExpressionType<? super S>.RecursionRegulator<S> regulator = (OneOfExpressionType<? super S>.RecursionRegulator<S>) parser
					.getState(theType);
				if (regulator == null) {
					regulator = theType.new RecursionRegulator<>();
					parser = parser.withState(theType, regulator);
				} else if (regulator.interrupt != null)
					regulator = null;
				if (regulator != null) {
					ExpressoParser<S> stopRecurse = parser.withState(theType, regulator);
					Expression<S> next = stopRecurse.nextMatch(theComponent);
					if (next != null)
						return new OneOfPossibility<>(theType, next, theComponentId, next.isInvariant());
				}
			}
			
			// First, try to recurse even further, getting a lower-priority operation with this as the first component TODO ORDER
			Expression<S> recursed = theType.recurse(new InvariantExpression<>(this), parser, theComponentId, false, true);
			if (recursed != null)
				return recursed;
			
			// Next, try to recurse on the invariant with the next component
			recursed = theType.recurse(theComponent, parser, theComponentId, true, false);
			if (recursed != null)
				return recursed;
			
			return null;*/
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
			return isInvariant;
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

	static class InvariantExpression<S extends BranchableStream<?, ?>> implements Expression<S> {
		private final Expression<S> theExpression;

		InvariantExpression(Expression<S> expression) {
			theExpression = expression;
		}

		@Override
		public ExpressionType<? super S> getType() {
			return theExpression.getType();
		}

		@Override
		public S getStream() {
			return theExpression.getStream();
		}

		@Override
		public int length() {
			return theExpression.length();
		}

		@Override
		public List<? extends Expression<S>> getChildren() {
			return theExpression.getChildren();
		}

		@Override
		public Expression<S> nextMatch(ExpressoParser<S> parser) throws IOException {
			return null;
		}

		@Override
		public int getErrorCount() {
			return theExpression.getErrorCount();
		}

		@Override
		public Expression<S> getFirstError() {
			return theExpression.getFirstError();
		}

		@Override
		public int getLocalErrorRelativePosition() {
			return theExpression.getLocalErrorRelativePosition();
		}

		@Override
		public String getLocalErrorMessage() {
			return theExpression.getLocalErrorMessage();
		}

		@Override
		public Expression<S> unwrap() {
			return theExpression.unwrap();
		}

		@Override
		public int getMatchQuality() {
			return theExpression.getMatchQuality();
		}

		@Override
		public boolean isInvariant() {
			return true;
		}

		@Override
		public StringBuilder print(StringBuilder str, int indent, String metadata) {
			return theExpression.print(str, indent, metadata);
		}

		@Override
		public int hashCode() {
			return theExpression.hashCode();
		}

		@Override
		public boolean equals(Object obj) {
			if (obj instanceof InvariantExpression)
				return theExpression.equals(((InvariantExpression<?>) obj).theExpression);
			else
				return theExpression.equals(obj);
		}

		@Override
		public String toString() {
			return theExpression.toString();
		}
	}
}
