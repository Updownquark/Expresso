package org.observe.expresso.qonfig.values;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import org.observe.ObservableAction;
import org.observe.ObservableValue;
import org.observe.expresso.ExpressoInterpretationException;
import org.observe.expresso.ModelInstantiationException;
import org.observe.expresso.ModelTypes;
import org.observe.expresso.ModelType.ModelInstanceType;
import org.observe.expresso.ObservableModelSet.InterpretedValueSynth;
import org.observe.expresso.ObservableModelSet.ModelSetInstance;
import org.observe.expresso.ObservableModelSet.ModelValueInstantiator;
import org.observe.expresso.qonfig.ExElement;
import org.observe.expresso.qonfig.ExElementTraceable;
import org.observe.expresso.qonfig.ExpressoBaseV0_1;
import org.observe.expresso.qonfig.ExpressoQIS;
import org.observe.expresso.qonfig.ModelValueElement;
import org.observe.expresso.qonfig.QonfigChildGetter;
import org.observe.expresso.qonfig.ModelValueElement.CompiledSynth;
import org.observe.expresso.qonfig.values.Action.Instantiator;
import org.observe.expresso.qonfig.values.Action.Interpreted;
import org.qommons.QommonsUtils;
import org.qommons.Transaction;
import org.qommons.config.QonfigElementOrAddOn;
import org.qommons.config.QonfigInterpretationException;
import org.qommons.fn.FunctionUtils;

/** A group of actions to perform in sequence */
@ExElementTraceable(toolkit = ExpressoBaseV0_1.BASE,
	qonfigType = "action-group",
	interpretation = ActionGroup.Interpreted.class,
	instance = ActionGroup.Instantiator.class)
public class ActionGroup extends ModelValueElement.Def.Abstract<ObservableAction, ModelValueElement<ObservableAction>>
implements ModelValueElement.CompiledSynth<ObservableAction, ModelValueElement<ObservableAction>> {
	private final List<Action> theActions;

	/**
	 * @param parent The parent element of this value element
	 * @param qonfigType The Qonfig type of this value element
	 */
	public ActionGroup(ExElement.Def<?> parent, QonfigElementOrAddOn qonfigType) {
		super(parent, qonfigType, ModelTypes.Action);
		theActions = new ArrayList<>();
	}

	/** @return The actions to perform */
	@QonfigChildGetter("action")
	public List<ModelValueElement.CompiledSynth<ObservableAction, ?>> getActions() {
		return Collections.unmodifiableList(theActions);
	}

	@Override
	protected void doUpdate(ExpressoQIS session) throws QonfigInterpretationException {
		super.doUpdate(session);
		syncChildren(ModelValueElement.CompiledSynth.class, theActions, session.forChildren("action"));
	}

	@Override
	protected void doPrepare(ExpressoQIS session) throws QonfigInterpretationException {
		List<ExpressoQIS> actionSessions = session.forChildren("action");
		int i = 0;
		for (ModelValueElement.CompiledSynth<ObservableAction, ?> action : theActions)
			action.prepareModelValue(actionSessions.get(i++));
	}

	@Override
	public ActionGroup.Interpreted interpretValue(ExElement.Interpreted<?> parent) {
		return new Interpreted(this, parent);
	}

	/** {@link ActionGroup} interpretation */
	public static class Interpreted
	extends ModelValueElement.Interpreted.Abstract<ObservableAction, ObservableAction, ModelValueElement<ObservableAction>>
	implements ModelValueElement.InterpretedSynth<ObservableAction, ObservableAction, ModelValueElement<ObservableAction>> {
		private final List<Action.Interpreted> theActions;

		Interpreted(ActionGroup definition, ExElement.Interpreted<?> parent) {
			super(definition, parent);
			theActions = new ArrayList<>();
		}

		@Override
		public ActionGroup getDefinition() {
			return (ActionGroup) super.getDefinition();
		}

		/** @return The actions to perform */
		public List<Action.Interpreted> getActions() {
			return Collections.unmodifiableList(theActions);
		}

		@Override
		protected ModelInstanceType<ObservableAction, ObservableAction> getTargetType() {
			return ModelTypes.Action.instance(); // Not actually used, since getType() is overridden
		}

		@Override
		public ModelInstanceType<ObservableAction, ObservableAction> getType() {
			return ModelTypes.Action.instance();
		}

		@Override
		protected void doUpdate() throws ExpressoInterpretationException {
			super.doUpdate();
			try (Transaction t = ModelValueElement.INTERPRETING_PARENTS.installParent(this)) {
				syncChildren(getDefinition().getActions(), theActions,
					d -> (Action.Interpreted) d.interpret(getExpressoEnv(d.getDocument())), i -> i.update());
			}
		}

		@Override
		public List<? extends InterpretedValueSynth<?, ?>> getComponents() {
			return Collections.unmodifiableList(theActions);
		}

		@Override
		public ModelValueElement<ObservableAction> create() throws ModelInstantiationException {
			return new Instantiator(this);
		}
	}

	/** {@link ActionGroup} instantiator */
	public static class Instantiator extends ModelValueElement.Abstract<ObservableAction> {
		private final List<Action.Instantiator> theActions;
		private final String thePrint;

		Instantiator(ActionGroup.Interpreted interpreted) throws ModelInstantiationException, RuntimeException {
			super(interpreted);
			theActions = QommonsUtils.filterMapE(interpreted.getActions(), null, a -> a.create());
			thePrint = getModelPath() + ":" + interpreted;
		}

		/** @return The actions to perform */
		public List<Action.Instantiator> getActions() {
			return theActions;
		}

		@Override
		protected void doUpdate(ExElement.Interpreted<?> interpreted) throws ModelInstantiationException {
			super.doUpdate(interpreted);
			ActionGroup.Interpreted myInterpreted = (ActionGroup.Interpreted) interpreted;
			int a = 0;
			for (Action.Instantiator action : theActions)
				action.update(myInterpreted.getActions().get(a++), this);
		}

		@Override
		public void instantiate() throws ModelInstantiationException {
			for (ModelValueInstantiator<?> action : theActions)
				action.instantiate();
		}

		@Override
		public ObservableAction evaluate(ModelSetInstance models) throws ModelInstantiationException, IllegalStateException {
			instantiate(models);
			ObservableAction[] actions = new ObservableAction[theActions.size()];
			for (int i = 0; i < actions.length; i++)
				actions[i] = theActions.get(i).get(models);
			return new GroupAction(actions, thePrint);
		}

		@Override
		public ObservableAction forModelCopy(ObservableAction value, ModelSetInstance sourceModels, ModelSetInstance newModels)
			throws ModelInstantiationException {
			ActionGroup.GroupAction action = (ActionGroup.GroupAction) value;
			ObservableAction[] actionCopies = new ObservableAction[action.getActions().length];
			boolean different = false;
			for (int i = 0; i < theActions.size(); i++) {
				actionCopies[i] = ((ModelValueInstantiator<ObservableAction>) theActions.get(i)).forModelCopy(action.getActions()[i],
					sourceModels, newModels);
				if (actionCopies[i] != action.getActions()[i])
					different = true;
			}
			if (different)
				return new GroupAction(actionCopies, thePrint);
			else
				return value;
		}
	}

	static class GroupAction implements ObservableAction {
		private final ObservableAction[] theActions;
		private final ObservableValue<String> theEnabled;
		private final String thePrint;

		GroupAction(ObservableAction[] actions, String print) {
			theActions = actions;
			ObservableValue<String>[] actionsEnabled = new ObservableValue[actions.length];
			for (int i = 0; i < actions.length; i++)
				actionsEnabled[i] = actions[i].isEnabled();
			theEnabled = ObservableValue.firstValue(FunctionUtils.NON_NULL, null, actionsEnabled);
			thePrint = print;
		}

		ObservableAction[] getActions() {
			return theActions;
		}

		@Override
		public void act(Object cause) throws IllegalStateException {
			// Don't do any actions if any are disabled
			String msg = theEnabled.get();
			if (msg != null)
				throw new IllegalStateException(msg);
			for (ObservableAction action : theActions)
				action.act(cause);
		}

		@Override
		public boolean isEventing() {
			for (ObservableAction action : theActions) {
				if (action.isEventing())
					return true;
			}
			return false;
		}

		@Override
		public ObservableValue<String> isEnabled() {
			return theEnabled;
		}

		@Override
		public String toString() {
			return thePrint;
		}
	}
}