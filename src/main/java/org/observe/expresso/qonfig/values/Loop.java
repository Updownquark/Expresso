package org.observe.expresso.qonfig.values;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.stream.Stream;

import org.observe.ObservableAction;
import org.observe.ObservableValue;
import org.observe.SettableValue;
import org.observe.expresso.ExpressoInterpretationException;
import org.observe.expresso.ModelInstantiationException;
import org.observe.expresso.ModelType.ModelInstanceType;
import org.observe.expresso.ModelTypes;
import org.observe.expresso.ObservableModelSet.InterpretedValueSynth;
import org.observe.expresso.ObservableModelSet.ModelInstantiator;
import org.observe.expresso.ObservableModelSet.ModelSetInstance;
import org.observe.expresso.ObservableModelSet.ModelValueInstantiator;
import org.observe.expresso.qonfig.CompiledExpression;
import org.observe.expresso.qonfig.DocumentMap;
import org.observe.expresso.qonfig.ExElement;
import org.observe.expresso.qonfig.ExElementTraceable;
import org.observe.expresso.qonfig.ExpressoBaseV0_1;
import org.observe.expresso.qonfig.ExpressoQIS;
import org.observe.expresso.qonfig.ModelValueElement;
import org.observe.expresso.qonfig.QonfigAttributeGetter;
import org.observe.expresso.qonfig.QonfigChildGetter;
import org.qommons.Causable;
import org.qommons.QommonsUtils;
import org.qommons.Transaction;
import org.qommons.collect.BetterList;
import org.qommons.config.QonfigElementOrAddOn;
import org.qommons.config.QonfigInterpretationException;
import org.qommons.io.ErrorReporting;

/** A set of actions that occur as long as a condition remains true */
@ExElementTraceable(toolkit = ExpressoBaseV0_1.BASE,
	qonfigType = "loop",
	interpretation = Loop.Interpreted.class,
	instance = Loop.Instantiator.class)
public class Loop extends ModelValueElement.Def.Abstract<ObservableAction, ModelValueElement<ObservableAction>>
implements ModelValueElement.CompiledSynth<ObservableAction, ModelValueElement<ObservableAction>> {
	private CompiledExpression theInit;
	private CompiledExpression theBefore;
	private CompiledExpression theWhile;
	private CompiledExpression theFinally;
	private final List<Action> theBody;

	/**
	 * @param parent The parent element of this value element
	 * @param qonfigType The Qonfig type of this value element
	 */
	public Loop(ExElement.Def<?> parent, QonfigElementOrAddOn qonfigType) {
		super(parent, qonfigType, ModelTypes.Action);
		theBody = new ArrayList<>();
	}

	/** @return An action to perform as soon as this loop begins execution */
	@QonfigAttributeGetter("init")
	public CompiledExpression getInit() {
		return theInit;
	}

	/** @return An action to perform before each check of the condition */
	@QonfigAttributeGetter("before-while")
	public CompiledExpression getBefore() {
		return theBefore;
	}

	/** @return The condition determining when this loop stops */
	@QonfigAttributeGetter("while")
	public CompiledExpression getWhile() {
		return theWhile;
	}

	/** @return An action to perform when the loop stops */
	@QonfigAttributeGetter("finally")
	public CompiledExpression getFinally() {
		return theFinally;
	}

	/** @return The actions to perform as long as the condition is true */
	@QonfigChildGetter("body")
	public List<Action> getBody() {
		return Collections.unmodifiableList(theBody);
	}

	@Override
	protected void doUpdate(ExpressoQIS session) throws QonfigInterpretationException {
		super.doUpdate(session);
		theInit = getAttributeExpression("init", session);
		theBefore = getAttributeExpression("before-while", session);
		theWhile = getAttributeExpression("while", session);
		theFinally = getAttributeExpression("finally", session);
		syncChildren(Action.class, theBody, session.forChildren("body"));
	}

	@Override
	public void doPrepare(ExpressoQIS session) throws QonfigInterpretationException {
		List<ExpressoQIS> bodySessions = session.forChildren("body");
		int i = 0;
		for (ModelValueElement.CompiledSynth<ObservableAction, ?> body : theBody)
			((ModelValueElement.Def<?, ?>) body).prepareModelValue(bodySessions.get(i++));
	}

	@Override
	public Loop.Interpreted interpretValue(ExElement.Interpreted<?> parent) {
		return new Interpreted(this, parent);
	}

	/** {@link Loop} interpretation */
	public static class Interpreted
	extends ModelValueElement.Interpreted.Abstract<ObservableAction, ObservableAction, ModelValueElement<ObservableAction>>
	implements ModelValueElement.InterpretedSynth<ObservableAction, ObservableAction, ModelValueElement<ObservableAction>> {
		private InterpretedValueSynth<ObservableAction, ObservableAction> theInit;
		private InterpretedValueSynth<ObservableAction, ObservableAction> theBefore;
		private InterpretedValueSynth<SettableValue<?>, SettableValue<Boolean>> theWhile;
		private InterpretedValueSynth<ObservableAction, ObservableAction> theFinally;
		private final List<Action.Interpreted> theBody;

		Interpreted(Loop definition, ExElement.Interpreted<?> parent) {
			super(definition, parent);
			theBody = new ArrayList<>();
		}

		@Override
		public Loop getDefinition() {
			return (Loop) super.getDefinition();
		}

		/** @return An action to perform as soon as this loop begins execution */
		public InterpretedValueSynth<ObservableAction, ObservableAction> getInit() {
			return theInit;
		}

		/** @return An action to perform before each check of the condition */
		public InterpretedValueSynth<ObservableAction, ObservableAction> getBefore() {
			return theBefore;
		}

		/** @return The condition determining when this loop stops */
		public InterpretedValueSynth<SettableValue<?>, SettableValue<Boolean>> getWhile() {
			return theWhile;
		}

		/** @return An action to perform when the loop stops */
		public InterpretedValueSynth<ObservableAction, ObservableAction> getFinally() {
			return theFinally;
		}

		/** @return The actions to perform as long as the condition is true */
		public List<Action.Interpreted> getBody() {
			return Collections.unmodifiableList(theBody);
		}

		@Override
		protected ModelInstanceType<ObservableAction, ObservableAction> getTargetType() {
			return getType(); // Not actually used, since getType() is overridden
		}

		@Override
		public ModelInstanceType<ObservableAction, ObservableAction> getType() {
			return ModelTypes.Action.instance();
		}

		@Override
		protected void doUpdate() throws ExpressoInterpretationException {
			super.doUpdate();
			theInit = interpret(getDefinition().getInit(), ModelTypes.Action.instance());
			theBefore = interpret(getDefinition().getBefore(), ModelTypes.Action.instance());
			theWhile = interpret(getDefinition().getWhile(), ModelTypes.Value.forType(boolean.class));
			theFinally = interpret(getDefinition().getFinally() == null ? null : getDefinition().getFinally(),
				ModelTypes.Action.instance());
			try (Transaction t = ModelValueElement.INTERPRETING_PARENTS.installParent(this)) {
				this.syncChildren(getDefinition().getBody(), theBody,
					def -> def.interpretValue(this), b -> b.updateValue());
			}
		}

		@Override
		public List<? extends InterpretedValueSynth<?, ?>> getComponents() {
			return BetterList.of(Stream.<InterpretedValueSynth<?, ?>> concat(//
				Stream.of(theInit, theBefore, theWhile, theFinally), //
				theBody.stream()).filter(Objects::nonNull));
		}

		@Override
		public Loop.Instantiator create() throws ModelInstantiationException {
			return new Instantiator(this);
		}
	}

	/** {@link Loop} instantiator */
	public static class Instantiator extends ModelValueElement.Abstract<ObservableAction> {
		private final DocumentMap<ModelInstantiator> theLocalModels;
		private final ModelValueInstantiator<? extends ObservableAction> theInit;
		private final ModelValueInstantiator<? extends ObservableAction> theBefore;
		private final ModelValueInstantiator<SettableValue<Boolean>> theWhile;
		private final List<Action.Instantiator> theBody;
		private final ModelValueInstantiator<? extends ObservableAction> theFinally;
		private final ErrorReporting theWhileReporting;

		Instantiator(Loop.Interpreted interpreted) throws ModelInstantiationException {
			super(interpreted);
			theLocalModels = interpreted.instantiateLocalModels();
			theInit = interpreted.getInit() == null ? null : interpreted.getInit().instantiate();
			theBefore = interpreted.getBefore() == null ? null : interpreted.getBefore().instantiate();
			theWhile = interpreted.getWhile().instantiate();
			theBody = QommonsUtils.filterMapE(interpreted.getBody(), null, e -> e.create());
			theFinally = interpreted.getFinally() == null ? null : interpreted.getFinally().instantiate();
			theWhileReporting = interpreted.reporting().at(interpreted.getDefinition().getWhile().getFilePosition());
		}

		/** @return An action to perform as soon as this loop begins execution */
		public ModelValueInstantiator<? extends ObservableAction> getInit() {
			return theInit;
		}

		/** @return An action to perform before each check of the condition */
		public ModelValueInstantiator<? extends ObservableAction> getBefore() {
			return theBefore;
		}

		/** @return The condition determining when this loop stops */
		public ModelValueInstantiator<SettableValue<Boolean>> getWhile() {
			return theWhile;
		}

		/** @return The actions to perform as long as the condition is true */
		public List<Action.Instantiator> getBody() {
			return theBody;
		}

		/** @return An action to perform when the loop stops */
		public ModelValueInstantiator<? extends ObservableAction> getFinally() {
			return theFinally;
		}

		@Override
		public void instantiate() throws ModelInstantiationException {
			theLocalModels.forEach(ModelInstantiator::instantiate);
			if (theInit != null)
				theInit.instantiate();
			if (theBefore != null)
				theBefore.instantiate();
			theWhile.instantiate();
			for (ModelValueInstantiator<?> body : theBody)
				body.instantiate();
			if (theFinally != null)
				theFinally.instantiate();
		}

		@Override
		public ObservableAction evaluate(ModelSetInstance models) throws ModelInstantiationException, IllegalStateException {
			models = theLocalModels.operate(models, (m, mi) -> mi.wrap(m));
			instantiate(models);
			ObservableAction init = theInit == null ? null : theInit.get(models);
			ObservableAction before = theBefore == null ? null : theBefore.get(models);
			SettableValue<Boolean> condition = theWhile.get(models);
			List<ObservableAction> body = new ArrayList<>(theBody.size());
			for (Action.Instantiator b : theBody)
				body.add(b.get(models));
			ObservableAction last = theFinally == null ? null : theFinally.get(models);
			return new LoopAction(init, before, condition, Collections.unmodifiableList(body), last, theWhileReporting);
		}

		@Override
		public ObservableAction forModelCopy(ObservableAction value, ModelSetInstance sourceModels, ModelSetInstance newModels)
			throws ModelInstantiationException {
			sourceModels = theLocalModels.operate(sourceModels, (m, mi) -> mi.wrap(m));
			newModels = theLocalModels.operate(newModels, (m, mi) -> mi.wrap(m));
			Loop.LoopAction loop = (Loop.LoopAction) value;
			ObservableAction initS = loop.getInit();
			ObservableAction initA = theInit == null ? null
				: ((ModelValueInstantiator<ObservableAction>) theInit).forModelCopy(initS, sourceModels, newModels);
			ObservableAction beforeS = loop.getBeforeCondition();
			ObservableAction beforeA = theBefore == null ? null
				: ((ModelValueInstantiator<ObservableAction>) theBefore).forModelCopy(beforeS, sourceModels, newModels);
			SettableValue<Boolean> whileS = (SettableValue<Boolean>) loop.getCondition();
			SettableValue<Boolean> whileA = theWhile.forModelCopy(whileS, sourceModels, newModels);
			boolean different = initS != initA || beforeS != beforeA || whileS != whileA;
			List<ObservableAction> execAs = new ArrayList<>(theBody.size());
			for (int i = 0; i < theBody.size(); i++) {
				ObservableAction bodyS = loop.getBody().get(i);
				ObservableAction bodyA = theBody.get(i).forModelCopy(bodyS, sourceModels, newModels);
				execAs.add(bodyA);
				different |= bodyS != bodyA;
			}
			ObservableAction finallyS = loop.getFinally();
			ObservableAction finallyA = theFinally == null ? null
				: ((ModelValueInstantiator<ObservableAction>) theFinally).forModelCopy(finallyS, sourceModels, newModels);
			different |= finallyS != finallyA;
			if (different)
				return new LoopAction(initA, beforeA, whileA, execAs, finallyA, theWhileReporting);
			else
				return value;
		}
	}

	static class LoopAction implements ObservableAction {
		private final ObservableAction theInit;
		private final ObservableAction theBeforeCondition;
		private final ObservableValue<Boolean> theCondition;
		private final List<ObservableAction> theBody;
		private final ObservableAction theFinally;
		private final ErrorReporting theWhileReporting;

		public LoopAction(ObservableAction init, ObservableAction before, ObservableValue<Boolean> condition,
			List<ObservableAction> body, ObservableAction finallly, ErrorReporting whileReporting) {
			theInit = init;
			theBeforeCondition = before;
			theCondition = condition;
			theBody = body;
			theFinally = finallly;
			theWhileReporting = whileReporting;
		}

		public ObservableAction getInit() {
			return theInit;
		}

		public ObservableAction getBeforeCondition() {
			return theBeforeCondition;
		}

		public ObservableValue<Boolean> getCondition() {
			return theCondition;
		}

		public List<ObservableAction> getBody() {
			return theBody;
		}

		public ObservableAction getFinally() {
			return theFinally;
		}

		@Override
		public ObservableValue<String> isEnabled() {
			// TODO This isn't right, but it seems hard to figure out, so leaving this for now
			return SettableValue.ALWAYS_ENABLED;
		}

		@Override
		public void act(Object cause) throws IllegalStateException {
			try (Causable.CausableInUse cause2 = Causable.cause(cause)) {
				if (theInit != null)
					theInit.act(cause2);

				try {
					// Prevent infinite loops. This structure isn't terribly efficient, so I think this should be sufficient.
					long count = 0;
					long conditionStamp = theCondition.getStamp();
					while (true) {
						if (theBeforeCondition != null)
							theBeforeCondition.act(cause2);
						if (!Boolean.TRUE.equals(theCondition.get()))
							break;
						for (ObservableAction body : theBody)
							body.act(cause2);
						count++;
						if (count % 10_000 == 0) {
							long newConditionStamp = theCondition.getStamp();
							if (conditionStamp == newConditionStamp) {
								theWhileReporting
								.error("This loop seems to be infinite--the 'while' condition is not affected by the body");
								break;
							} else
								conditionStamp = newConditionStamp;
						}
					}
				} finally {
					if (theFinally != null)
						theFinally.act(cause2);
				}
			}
		}

		@Override
		public boolean isEventing() {
			if (theInit != null && theInit.isEventing())
				return true;
			else if (theBeforeCondition != null && theBeforeCondition.isEventing())
				return true;
			for (ObservableAction body : theBody) {
				if (body.isEventing())
					return true;
			}
			return theFinally != null && theFinally.isEventing();
		}
	}
}