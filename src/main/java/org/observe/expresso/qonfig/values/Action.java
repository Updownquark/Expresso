package org.observe.expresso.qonfig.values;

import java.util.List;
import java.util.Objects;

import org.observe.ObservableAction;
import org.observe.ObservableValue;
import org.observe.SettableValue;
import org.observe.expresso.ExpressoInterpretationException;
import org.observe.expresso.ModelInstantiationException;
import org.observe.expresso.ModelTypes;
import org.observe.expresso.ModelType.ModelInstanceType;
import org.observe.expresso.ObservableModelSet.InterpretedValueSynth;
import org.observe.expresso.ObservableModelSet.ModelSetInstance;
import org.observe.expresso.ObservableModelSet.ModelValueInstantiator;
import org.observe.expresso.qonfig.CompiledExpression;
import org.observe.expresso.qonfig.ExElement;
import org.observe.expresso.qonfig.ExElementTraceable;
import org.observe.expresso.qonfig.ExpressoBaseV0_1;
import org.observe.expresso.qonfig.ExpressoQIS;
import org.observe.expresso.qonfig.ModelValueElement;
import org.observe.expresso.qonfig.QonfigAttributeGetter;
import org.qommons.Causable;
import org.qommons.ThreadConstraint;
import org.qommons.collect.BetterList;
import org.qommons.config.QonfigElementOrAddOn;
import org.qommons.config.QonfigInterpretationException;
import org.qommons.io.ErrorReporting;
import org.qommons.threading.QommonsTimer;

/** A simple action to perform */
@ExElementTraceable(toolkit = ExpressoBaseV0_1.BASE,
	qonfigType = "action",
	interpretation = Action.Interpreted.class,
	instance = Action.Instantiator.class)
public class Action extends ModelValueElement.Def.Abstract<ObservableAction, ModelValueElement<?>>
implements ModelValueElement.CompiledSynth<ObservableAction, ModelValueElement<?>> {
	private CompiledExpression theAction;
	private CompiledExpression onThread;
	private boolean isAlwaysEnabled;

	/**
	 * @param parent The parent element of this value element
	 * @param qonfigType The Qonfig type of this value element
	 */
	public Action(ExElement.Def<?> parent, QonfigElementOrAddOn qonfigType) {
		super(parent, qonfigType, ModelTypes.Action);
	}

	/** @return The action to perform */
	public CompiledExpression getAction() {
		return theAction;
	}

	/** @return The threading to delegate the action to (if asynchronous) */
	@QonfigAttributeGetter("on-thread")
	public CompiledExpression getOnThread() {
		return onThread;
	}

	/** @return Whether this action will always report <code>null</code> for its {@link ObservableAction#isEnabled() enablement} */
	@QonfigAttributeGetter("always-enabled")
	public boolean isAlwaysEnabled() {
		return isAlwaysEnabled;
	}

	@Override
	protected void doUpdate(ExpressoQIS session) throws QonfigInterpretationException {
		super.doUpdate(session);
		theAction = getValueExpression(session);
		onThread = getAttributeExpression("on-thread", session);
		isAlwaysEnabled = session.getAttribute("always-enabled", boolean.class);
	}

	@Override
	public void doPrepare(ExpressoQIS session) { // Nothing to do
	}

	@Override
	public Action.Interpreted interpretValue(ExElement.Interpreted<?> parent) {
		return new Interpreted(this, parent);
	}

	/** {@link Action} interpretation */
	public static class Interpreted
	extends ModelValueElement.Interpreted.Abstract<ObservableAction, ObservableAction, ModelValueElement<ObservableAction>>
	implements ModelValueElement.InterpretedSynth<ObservableAction, ObservableAction, ModelValueElement<ObservableAction>> {
		private InterpretedValueSynth<SettableValue<?>, SettableValue<ThreadConstraint>> onThread;

		/**
		 * @param definition The definition to interpret
		 * @param parent The parent element for this action element
		 */
		protected Interpreted(Action definition, ExElement.Interpreted<?> parent) {
			super(definition, parent);
		}

		@Override
		public Action getDefinition() {
			return (Action) super.getDefinition();
		}

		@Override
		protected ModelInstanceType<ObservableAction, ObservableAction> getTargetType() {
			return ModelTypes.Action.<ObservableAction> anyAs();
		}

		@Override
		public List<? extends InterpretedValueSynth<?, ?>> getComponents() {
			return BetterList.of(getElementValue());
		}

		/** @return The threading to delegate the action to (if asynchronous) */
		public InterpretedValueSynth<SettableValue<?>, SettableValue<ThreadConstraint>> getOnThread() {
			return onThread;
		}

		@Override
		protected void doUpdate() throws ExpressoInterpretationException {
			super.doUpdate();
			onThread = interpret(getDefinition().getOnThread(), ModelTypes.Value.forType(ThreadConstraint.class));
		}

		@Override
		public Action.Instantiator create() throws ModelInstantiationException {
			return new Instantiator(this);
		}
	}

	/** {@link Action} instantiator */
	public static class Instantiator extends ModelValueElement.Abstract<ObservableAction> {
		private ModelValueInstantiator<SettableValue<ThreadConstraint>> onThread;
		private boolean isAlwaysEnabled;
		private final ErrorReporting theReporting;

		Instantiator(Action.Interpreted interpreted) throws ModelInstantiationException {
			super(interpreted);
			onThread = interpreted.getOnThread() == null ? null : interpreted.getOnThread().instantiate();
			isAlwaysEnabled = interpreted.getDefinition().isAlwaysEnabled();
			theReporting = interpreted.reporting();
		}

		@Override
		public ModelValueInstantiator<ObservableAction> getElementValue() {
			return (ModelValueInstantiator<ObservableAction>) super.getElementValue();
		}

		/** @return The threading to delegate the action to (if asynchronous) */
		public ModelValueInstantiator<SettableValue<ThreadConstraint>> getOnThread() {
			return onThread;
		}

		/** @return Whether this action will always report <code>null</code> for its {@link ObservableAction#isEnabled() enablement} */
		public boolean isAlwaysEnabled() {
			return isAlwaysEnabled;
		}

		@Override
		public void instantiate() throws ModelInstantiationException {
			getElementValue().instantiate();
			if (onThread != null)
				onThread.instantiate();
		}

		@Override
		public ObservableAction evaluate(ModelSetInstance models) throws ModelInstantiationException, IllegalStateException {
			instantiate(models);
			ObservableAction action = getElementValue().get(models);
			if (onThread != null || isAlwaysEnabled)
				return new ModifiedAction(action, onThread == null ? null : onThread.get(models), isAlwaysEnabled, getModelPath(),
					theReporting);
			else
				return action;
		}

		@Override
		public ObservableAction forModelCopy(ObservableAction value, ModelSetInstance sourceModels, ModelSetInstance newModels)
			throws ModelInstantiationException {
			if (onThread != null || isAlwaysEnabled) {
				if (value instanceof Action.ModifiedAction) {
					Action.ModifiedAction async = (Action.ModifiedAction) value;
					ObservableAction newWrapped = getElementValue().forModelCopy(async.theWrapped, sourceModels, newModels);
					SettableValue<ThreadConstraint> newOnThread = getOnThread() == null ? null
						: getOnThread().forModelCopy(((Action.ModifiedAction) value).onThread, sourceModels, newModels);
					if (async.theWrapped == newWrapped && async.onThread == newOnThread)
						return async;
					return new ModifiedAction(newWrapped, newOnThread, isAlwaysEnabled, getModelPath(), theReporting);
				} else
					return get(newModels);
			} else if (value instanceof Action.ModifiedAction)
				return get(newModels);
			else
				return getElementValue().forModelCopy(value, sourceModels, newModels);
		}
	}

	static class ModifiedAction implements ObservableAction {
		final ObservableAction theWrapped;
		final SettableValue<ThreadConstraint> onThread;
		final boolean isAlwaysEnabled;
		private final String theModelPath;
		private final ErrorReporting theReporting;

		ModifiedAction(ObservableAction wrapped, SettableValue<ThreadConstraint> onThread, boolean alwaysEnabled, String modelPath,
			ErrorReporting reporting) {
			theWrapped = wrapped;
			this.onThread = onThread;
			isAlwaysEnabled = alwaysEnabled;
			theModelPath = modelPath;
			theReporting = reporting;
		}

		@Override
		public void act(Object cause) throws IllegalStateException {
			ThreadConstraint threading = onThread == null ? null : onThread.get();
			if (threading == null) {
				theWrapped.act(cause);
				return;
			} else if (!threading.supportsInvoke()) {
				theReporting.warn("on-thread returned " + threading + ", which does not support invocation");
				theWrapped.act(cause);
				return;
			}

			QommonsTimer.TaskHandle[] handle = new QommonsTimer.TaskHandle[1];
			long[] firstReInvoke = new long[1];
			Runnable task = () -> {
				if (theWrapped.isEventing()) {
					long now = System.currentTimeMillis();
					if (firstReInvoke[0] == 0)
						firstReInvoke[0] = now;
					else if (now - firstReInvoke[0] > 1000) {
						theReporting.error("This is a long-running task and cannot be re-invoked while it is still running");
						return;
					}
					handle[0].runImmediately();
					return;
				}
				try {
					if (cause instanceof Causable)
						theWrapped.act(Causable.broken(cause));
					else
						theWrapped.act(cause);
				} catch (RuntimeException e) {
					if (!isAlwaysEnabled)
						theReporting.error("Error executing action", e);
				}
			};
			handle[0] = QommonsTimer.getCommonInstance().build(task, null, false)//
				.withThreading(threading)//
				.runImmediately();
		}

		@Override
		public boolean isEventing() {
			return theWrapped.isEventing();
		}

		@Override
		public ObservableValue<String> isEnabled() {
			if (isAlwaysEnabled)
				return SettableValue.ALWAYS_ENABLED;
			else
				return theWrapped.isEnabled();
		}

		@Override
		public int hashCode() {
			return Objects.hash(theWrapped, onThread, isAlwaysEnabled);
		}

		@Override
		public boolean equals(Object obj) {
			if (this == obj)
				return true;
			else if (!(obj instanceof Action.ModifiedAction))
				return false;
			Action.ModifiedAction other = (Action.ModifiedAction) obj;
			return theWrapped.equals(other.theWrapped) && Objects.equals(onThread, other.onThread)
				&& isAlwaysEnabled == other.isAlwaysEnabled;
		}

		@Override
		public String toString() {
			return theModelPath + ":" + theReporting;
		}
	}
}