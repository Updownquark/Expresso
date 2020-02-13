package org.expresso;

import java.util.List;
import java.util.stream.Stream;

import org.expresso.stream.BranchableStream;
import org.expresso.types.OneOfExpressionType;
import org.qommons.collect.BetterList;

/**
 * A class of expressions, any of which can satisfy this type
 *
 * @param <S> The stream super-type of the expression types
 */
public abstract class ExpressionClass<S extends BranchableStream<?, ?>> extends OneOfExpressionType<S> implements GrammarExpressionType<S> {
	private final ExpressoGrammar<S> theGrammar;
	private final String theName;
	private final List<ExpressionClass<S>> theParentClasses;
	private final List<ExpressionClass<S>> theChildClasses;
	private final BetterList<? extends ExpressionClass<S>> theLocalIgnorables;
	private BetterList<? extends ExpressionClass<S>> theIgnorables;

	/**
	 * @param grammar The grammar that this class belongs to
	 * @param id The cache ID for the expression
	 * @param name The name of the class
	 * @param parentClasses The classes that this class extends
	 * @param childClasses The classes that extend this class (populated later externally)
	 * @param members The class's members, sorted by priority, then by order of occurrence
	 * @param ignorables Classes of expressions that may be a part of this expression's content without affecting its syntax
	 */
	public ExpressionClass(ExpressoGrammar<S> grammar, int id, String name, List<ExpressionClass<S>> parentClasses,
		List<ExpressionClass<S>> childClasses, BetterList<? extends GrammarExpressionType<S>> members,
		BetterList<? extends ExpressionClass<S>> ignorables) {
		super(id, members);
		theGrammar = grammar;
		theName = name;
		theParentClasses = parentClasses;
		theChildClasses = childClasses;
		theLocalIgnorables = ignorables;
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
	public BetterList<? extends GrammarExpressionType<S>> getComponents() {
		return (BetterList<? extends GrammarExpressionType<S>>) super.getComponents();
	}

	/** @return The classes that this class extends */
	public List<ExpressionClass<S>> getParentClasses() {
		return theParentClasses;
	}

	/** @return The classes that extend this class */
	public List<ExpressionClass<S>> getChildClasses() {
		return theChildClasses;
	}

	@Override
	public BetterList<? extends ExpressionClass<S>> getIgnorables() {
		if (theIgnorables == null)
			theIgnorables = BetterList
				.of(Stream.concat(theParentClasses.stream().flatMap(pc -> pc.getIgnorables().stream()), theLocalIgnorables.stream()));
		return theIgnorables;
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
	public String toString() {
		return "Class " + theName;
	}
}
