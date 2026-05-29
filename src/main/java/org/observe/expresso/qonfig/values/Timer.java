package org.observe.expresso.qonfig.values;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.stream.Stream;

import org.observe.ObservableAction;
import org.observe.ObservableValue;
import org.observe.ObservableValueEvent;
import org.observe.SettableValue;
import org.observe.ObservableValue.Getter;
import org.observe.SettableValue.Setter;
import org.observe.SettableValue.WrappingSettableValue;
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
import org.observe.expresso.qonfig.ExElement.Def;
import org.qommons.Causable;
import org.qommons.collect.BetterList;
import org.qommons.collect.MutableCollectionElement.StdMsg;
import org.qommons.config.QonfigElementOrAddOn;
import org.qommons.config.QonfigInterpretationException;
import org.qommons.io.ErrorReporting;
import org.qommons.threading.QommonsTimer;

/** A customizable task that executes every so often */
@ExElementTraceable(toolkit = ExpressoBaseV0_1.BASE,
	qonfigType = Timer.TIMER,
	interpretation = Timer.Interpreted.class,
	instance = Timer.Instantiator.class)
public class Timer extends ModelValueElement.Def.SingleTyped<SettableValue<?>, ModelValueElement<SettableValue<Instant>>>
implements ModelValueElement.CompiledSynth<SettableValue<?>, ModelValueElement<SettableValue<Instant>>> {
	/** The XML name of this type */
	public static final String TIMER = "timer";

	private CompiledExpression isActive;
	private CompiledExpression theFrequency;
	private boolean isStrictTiming;
	private boolean isBackground;
	private CompiledExpression theRemainingExecutions;
	private CompiledExpression theUntil;
	private CompiledExpression theRunNextIn;
	private CompiledExpression theNextExecution;
	private CompiledExpression theExecutionCount;
	private CompiledExpression isExecuting;

	/**
	 * @param parent The parent element of this value element
	 * @param qonfigType The Qonfig type of this value element
	 */
	public Timer(Def<?> parent, QonfigElementOrAddOn qonfigType) {
		super(parent, qonfigType, ModelTypes.Value);
	}

	/** @return Controls when the timer is active */
	@QonfigAttributeGetter("active")
	public CompiledExpression isActive() {
		return isActive;
	}

	/** @return The frequency with which the task runs */
	@QonfigAttributeGetter("frequency")
	public CompiledExpression getFrequency() {
		return theFrequency;
	}

	/**
	 * @return The way in which this timer obeys its {@link #getFrequency() frequency}. If true, the beginnings of the task's execution
	 *         will be spaced by the frequency, meaning that if the task takes a while, there will be a smaller interval between the end
	 *         of one execution and the beginning of another. If false, the timer will wait for its frequency after each execution's end
	 *         before beginning a new execution.
	 */
	@QonfigAttributeGetter("strict-timing")
	public boolean isStrictTiming() {
		return isStrictTiming;
	}

	/** @return Whether this timer executes in the background (not on the UI thread) */
	@QonfigAttributeGetter("background")
	public boolean isBackground() {
		return isBackground;
	}

	/** @return The number of executions left before the timer stops (not set (null) by default) */
	@QonfigAttributeGetter("remaining-executions")
	public CompiledExpression getRemainingExecutions() {
		return theRemainingExecutions;
	}

	/** @return An instant after which the timer will stop (null by default) */
	@QonfigAttributeGetter("until")
	public CompiledExpression getUntil() {
		return theUntil;
	}

	/** @return A duration that, when set, will cause the timer's next execution to be that duration from the current time */
	@QonfigAttributeGetter("run-next-in")
	public CompiledExpression getRunNextIn() {
		return theRunNextIn;
	}

	/** @return The settable next execution of this timer */
	@QonfigAttributeGetter("next-execution")
	public CompiledExpression getNextExecution() {
		return theNextExecution;
	}

	/** @return The number of times this timer has executed */
	@QonfigAttributeGetter("execution-count")
	public CompiledExpression getExecutionCount() {
		return theExecutionCount;
	}

	/** @return Whether this timer is currently executing */
	@QonfigAttributeGetter("executing")
	public CompiledExpression isExecuting() {
		return isExecuting;
	}

	@Override
	protected void doPrepare(ExpressoQIS session) throws QonfigInterpretationException {
		isActive = getAttributeExpression("active", session);
		theFrequency = getAttributeExpression("frequency", session);
		isStrictTiming = session.getAttribute("strict-timing", boolean.class);
		isBackground = session.getAttribute("background", boolean.class);
		theRemainingExecutions = getAttributeExpression("remaining-executions", session);
		theUntil = getAttributeExpression("until", session);
		theRunNextIn = getAttributeExpression("run-next-in", session);
		theNextExecution = getAttributeExpression("next-execution", session);
		theExecutionCount = getAttributeExpression("execution-count", session);
		isExecuting = getAttributeExpression("executing", session);
	}

	@Override
	public Timer.Interpreted interpretValue(ExElement.Interpreted<?> parent) {
		return new Interpreted(this, parent);
	}

	/** {@link Timer} interpretation */
	public static class Interpreted extends
	ModelValueElement.Def.SingleTyped.Interpreted<SettableValue<?>, SettableValue<Instant>, ModelValueElement<SettableValue<Instant>>>
	implements
	ModelValueElement.InterpretedSynth<SettableValue<?>, SettableValue<Instant>, ModelValueElement<SettableValue<Instant>>> {
		private InterpretedValueSynth<SettableValue<?>, SettableValue<Boolean>> isActive;
		private InterpretedValueSynth<SettableValue<?>, SettableValue<Duration>> theFrequency;
		private InterpretedValueSynth<SettableValue<?>, SettableValue<Integer>> theRemainingExecutions;
		private InterpretedValueSynth<SettableValue<?>, SettableValue<Instant>> theUntil;
		private InterpretedValueSynth<SettableValue<?>, SettableValue<Duration>> theRunNextIn;
		private InterpretedValueSynth<SettableValue<?>, SettableValue<Instant>> theNextExecution;
		private InterpretedValueSynth<SettableValue<?>, SettableValue<Integer>> theExecutionCount;
		private InterpretedValueSynth<SettableValue<?>, SettableValue<Boolean>> isExecuting;

		Interpreted(Timer definition, ExElement.Interpreted<?> parent) {
			super(definition, parent);
		}

		@Override
		public Timer getDefinition() {
			return (Timer) super.getDefinition();
		}

		@Override
		public ModelInstanceType<SettableValue<?>, SettableValue<Instant>> getType() {
			return ModelTypes.Value.forType(Instant.class);
		}

		// A little hacky here because the type of the element value (action) isn't the same as the type of this element
		// (value<Instant>)
		// The super class doesn't expect this situation
		@Override
		protected ModelInstanceType<SettableValue<?>, SettableValue<Instant>> getTargetType() {
			return (ModelInstanceType<SettableValue<?>, SettableValue<Instant>>) (ModelInstanceType<?, ?>) ModelTypes.Action.instance();
		}

		/** @return Controls when the timer is active */
		public InterpretedValueSynth<SettableValue<?>, SettableValue<Boolean>> isActive() {
			return isActive;
		}

		/** @return The frequency with which the task runs */
		public InterpretedValueSynth<SettableValue<?>, SettableValue<Duration>> getFrequency() {
			return theFrequency;
		}

		/** @return The number of executions left before the timer stops (not set (null) by default) */
		public InterpretedValueSynth<SettableValue<?>, SettableValue<Integer>> getRemainingExecutions() {
			return theRemainingExecutions;
		}

		/** @return An instant after which the timer will stop (null by default) */
		public InterpretedValueSynth<SettableValue<?>, SettableValue<Instant>> getUntil() {
			return theUntil;
		}

		/** @return A duration that, when set, will cause the timer's next execution to be that duration from the current time */
		public InterpretedValueSynth<SettableValue<?>, SettableValue<Duration>> getRunNextIn() {
			return theRunNextIn;
		}

		/** @return The settable next execution of this timer */
		public InterpretedValueSynth<SettableValue<?>, SettableValue<Instant>> getNextExecution() {
			return theNextExecution;
		}

		/** @return The number of times this timer has executed */
		public InterpretedValueSynth<SettableValue<?>, SettableValue<Integer>> getExecutionCount() {
			return theExecutionCount;
		}

		/** @return Whether this timer is currently executing */
		public InterpretedValueSynth<SettableValue<?>, SettableValue<Boolean>> isExecuting() {
			return isExecuting;
		}

		@Override
		protected void doUpdate() throws ExpressoInterpretationException {
			super.doUpdate();

			isActive = interpret(getDefinition().isActive(), ModelTypes.Value.BOOLEAN);
			theFrequency = interpret(getDefinition().getFrequency(), ModelTypes.Value.forType(Duration.class));
			theRemainingExecutions = interpret(getDefinition().getRemainingExecutions(), ModelTypes.Value.INT);
			theUntil = interpret(getDefinition().getUntil(), ModelTypes.Value.forType(Instant.class));
			theRunNextIn = interpret(getDefinition().getRunNextIn(), ModelTypes.Value.forType(Duration.class));
			theNextExecution = interpret(getDefinition().getNextExecution(), ModelTypes.Value.forType(Instant.class));
			theExecutionCount = interpret(getDefinition().getExecutionCount(), ModelTypes.Value.INT);
			isExecuting = interpret(getDefinition().isExecuting(), ModelTypes.Value.BOOLEAN);
		}

		@Override
		public List<? extends InterpretedValueSynth<?, ?>> getComponents() {
			return BetterList.of(Stream.of(isActive, theFrequency, theRemainingExecutions, theUntil, theRunNextIn, theNextExecution,
				theExecutionCount, isExecuting, getElementValue()).filter(Objects::nonNull));
		}

		@Override
		public ModelValueElement<SettableValue<Instant>> create() throws ModelInstantiationException {
			return new Instantiator(this);
		}
	}

	/** {@link Timer} instantiator */
	public static class Instantiator extends ModelValueElement.Abstract<SettableValue<Instant>> {
		private final ModelValueInstantiator<SettableValue<Boolean>> isActive;
		private final ModelValueInstantiator<SettableValue<Duration>> theFrequency;
		private final boolean isStrictTiming;
		private final boolean isBackground;
		private final ModelValueInstantiator<SettableValue<Integer>> theRemainingExecutions;
		private final ModelValueInstantiator<SettableValue<Instant>> theUntil;
		private final ModelValueInstantiator<SettableValue<Duration>> theRunNextIn;
		private final ModelValueInstantiator<SettableValue<Instant>> theNextExecution;
		private final ModelValueInstantiator<SettableValue<Integer>> theExecutionCount;
		private final ModelValueInstantiator<SettableValue<Boolean>> isExecuting;
		private final ErrorReporting theActionReporting;

		Instantiator(Timer.Interpreted interpreted) throws ModelInstantiationException {
			super(interpreted);
			isActive = interpreted.isActive().instantiate();
			theFrequency = interpreted.getFrequency().instantiate();
			isStrictTiming = interpreted.getDefinition().isStrictTiming();
			isBackground = interpreted.getDefinition().isBackground();
			theRemainingExecutions = interpreted.getRemainingExecutions() == null ? null
				: interpreted.getRemainingExecutions().instantiate();
			theUntil = interpreted.getUntil() == null ? null : interpreted.getUntil().instantiate();
			theRunNextIn = interpreted.getRunNextIn() == null ? null : interpreted.getRunNextIn().instantiate();
			theNextExecution = interpreted.getNextExecution() == null ? null : interpreted.getNextExecution().instantiate();
			theExecutionCount = interpreted.getExecutionCount() == null ? null : interpreted.getExecutionCount().instantiate();
			isExecuting = interpreted.isExecuting() == null ? null : interpreted.isExecuting().instantiate();
			theActionReporting = getElementValue() == null ? null
				: interpreted.reporting().at(interpreted.getDefinition().getElementValue().getFilePosition());
		}

		/** @return Controls when the timer is active */
		public ModelValueInstantiator<SettableValue<Boolean>> isActive() {
			return isActive;
		}

		/** @return The frequency with which the task runs */
		public ModelValueInstantiator<SettableValue<Duration>> getFrequency() {
			return theFrequency;
		}

		/** @return Whether this timer obeys its frequency strictly */
		public boolean isStrictTiming() {
			return isStrictTiming;
		}

		/** @return Whether this timer executes in the background (not on the UI thread) */
		public boolean isBackground() {
			return isBackground;
		}

		/** @return The number of executions left before the timer stops (not set (null) by default) */
		public ModelValueInstantiator<SettableValue<Integer>> getRemainingExecutions() {
			return theRemainingExecutions;
		}

		/** @return An instant after which the timer will stop (null by default) */
		public ModelValueInstantiator<SettableValue<Instant>> getUntil() {
			return theUntil;
		}

		/** @return A duration that, when set, will cause the timer's next execution to be that duration from the current time */
		public ModelValueInstantiator<SettableValue<Duration>> getRunNextIn() {
			return theRunNextIn;
		}

		/** @return The settable next execution of this timer */
		public ModelValueInstantiator<SettableValue<Instant>> getNextExecution() {
			return theNextExecution;
		}

		/** @return The number of times this timer has executed */
		public ModelValueInstantiator<SettableValue<Integer>> getExecutionCount() {
			return theExecutionCount;
		}

		/** @return Whether this timer is currently executing */
		public ModelValueInstantiator<SettableValue<Boolean>> isExecuting() {
			return isExecuting;
		}

		@Override
		public ModelValueInstantiator<ObservableAction> getElementValue() {
			return (ModelValueInstantiator<ObservableAction>) super.getElementValue();
		}

		ErrorReporting getActionReporting() {
			return theActionReporting;
		}

		@Override
		public void instantiate() throws ModelInstantiationException {
			super.instantiate();
			isActive.instantiate();
			theFrequency.instantiate();
			if (theRemainingExecutions != null)
				theRemainingExecutions.instantiate();
			if (theUntil != null)
				theUntil.instantiate();
			if (theRunNextIn != null)
				theRunNextIn.instantiate();
			if (theNextExecution != null)
				theNextExecution.instantiate();
			if (theExecutionCount != null)
				theExecutionCount.instantiate();
			if (isExecuting != null)
				isExecuting.instantiate();
		}

		@Override
		public SettableValue<Instant> evaluate(ModelSetInstance models) throws ModelInstantiationException, IllegalStateException {
			instantiate(models);
			SettableValue<Boolean> active = isActive.get(models);
			SettableValue<Duration> frequency = theFrequency.get(models);
			SettableValue<Integer> remainingExecutions = theRemainingExecutions == null ? null : theRemainingExecutions.get(models);
			SettableValue<Instant> until = theUntil == null ? null : theUntil.get(models);
			SettableValue<Duration> runNextIn = theRunNextIn == null ? null : theRunNextIn.get(models);
			SettableValue<Instant> nextExecution = theNextExecution == null ? null : theNextExecution.get(models);
			SettableValue<Integer> executionCount = theExecutionCount == null ? null : theExecutionCount.get(models);
			SettableValue<Boolean> executing = isExecuting == null ? null : isExecuting.get(models);
			ObservableAction action = getElementValue() == null ? null : getElementValue().get(models);
			return new TimerInstance(active, frequency, isStrictTiming, isBackground, remainingExecutions, until, runNextIn,
				nextExecution, executionCount, executing, action, theActionReporting);
		}

		@Override
		public SettableValue<Instant> forModelCopy(SettableValue<Instant> value, ModelSetInstance sourceModels,
			ModelSetInstance newModels) throws ModelInstantiationException {
			Timer.TimerInstance timer = (Timer.TimerInstance) value;
			SettableValue<Boolean> active = isActive.forModelCopy(timer.isActive, sourceModels, newModels);
			SettableValue<Duration> frequency = theFrequency.forModelCopy(timer.theFrequency, sourceModels, newModels);
			SettableValue<Integer> remainingExecutions = theRemainingExecutions == null ? null
				: theRemainingExecutions.forModelCopy(timer.theRemainingExecutions, sourceModels, newModels);
			SettableValue<Instant> until = theUntil == null ? null : theUntil.forModelCopy(timer.theUntil, sourceModels, newModels);
			SettableValue<Duration> runNextIn = theRunNextIn == null ? null
				: theRunNextIn.forModelCopy(timer.theRunNextIn, sourceModels, newModels);
			SettableValue<Instant> nextExecution = theNextExecution == null ? null
				: theNextExecution.forModelCopy(timer.theNextExecution, sourceModels, newModels);
			SettableValue<Integer> executionCount = theExecutionCount == null ? null
				: theExecutionCount.forModelCopy(timer.theExecutionCount, sourceModels, newModels);
			SettableValue<Boolean> executing = isExecuting == null ? null
				: isExecuting.forModelCopy(timer.isExecuting, sourceModels, newModels);
			ObservableAction action = getElementValue() == null ? null
				: getElementValue().forModelCopy(timer.theAction, sourceModels, newModels);

			if (active != timer.isActive || frequency != timer.theFrequency || remainingExecutions != timer.theRemainingExecutions
				|| until != timer.theUntil || runNextIn != timer.theRunNextIn || nextExecution != timer.theNextExecution
				|| executionCount != timer.theExecutionCount || executing != timer.isExecuting || action != timer.theAction)
				return new TimerInstance(active, frequency, isStrictTiming, isBackground, remainingExecutions, until, runNextIn,
					nextExecution, executionCount, executing, action, theActionReporting);
			else
				return timer;
		}
	}

	static class TimerInstance extends WrappingSettableValue<Instant> {
		final SettableValue<Boolean> isActive;
		final SettableValue<Duration> theFrequency;
		final boolean isStrictTiming;
		final boolean isBackground;
		final SettableValue<Integer> theRemainingExecutions;
		final SettableValue<Instant> theUntil;
		final SettableValue<Duration> theRunNextIn;
		final SettableValue<Instant> theNextExecution;
		final SettableValue<Integer> theExecutionCount;
		final SettableValue<Boolean> isExecuting;
		final ObservableAction theAction;
		final ErrorReporting theActionReporting;

		final QommonsTimer.TaskHandle theHandle;
		private final Causable.CausableKey theExecuteFinish;

		private volatile boolean theCallbackLock;

		TimerInstance(SettableValue<Boolean> active, SettableValue<Duration> frequency, boolean strictTiming, boolean background,
			SettableValue<Integer> remainingExecutions, SettableValue<Instant> until, SettableValue<Duration> runNextIn,
			SettableValue<Instant> nextExecution, SettableValue<Integer> executionCount, SettableValue<Boolean> executing,
			ObservableAction action, ErrorReporting actionReporting) {
			super(SettableValue.<Instant> build().withDescription("timer").build());
			isActive = active;
			theFrequency = frequency;
			isStrictTiming = strictTiming;
			isBackground = background;
			theRemainingExecutions = remainingExecutions;
			theUntil = until;
			theRunNextIn = runNextIn;
			theNextExecution = nextExecution;
			theExecutionCount = executionCount;
			isExecuting = executing;
			theAction = action;
			theActionReporting = actionReporting;

			QommonsTimer.TaskHandle task = QommonsTimer.getCommonInstance().build(this::executeIfAllowed, theFrequency.get(),
				isStrictTiming);
			if (!isBackground)
				task.onEDT();
			theHandle = task;
			theExecuteFinish = Causable.key((cause, data) -> {
			}, (cause, data) -> {
				if (isExecuting != null && isExecuting.isAcceptable(false) == null)
					isExecuting.set(false, cause);
			});
			theFrequency.noInitChanges().act(evt -> {
				if (evt.getNewValue() != null)
					task.setFrequency(evt.getNewValue(), isStrictTiming);
				else
					deactivate();
			});
			if (theRunNextIn != null) {
				theRunNextIn.changes().act(evt -> {
					if (evt.getNewValue() != null) {
						Instant nextRun = Instant.now().plus(evt.getNewValue());
						activate(nextRun, evt, false);
						task.runNextIn(evt.getNewValue());
					} else
						deactivate();
				});
			}
			if (theNextExecution != null) {
				theNextExecution.changes().act(evt -> {
					if (theCallbackLock)
						return;
					if (evt.getNewValue() != null) {
						activate(evt.getNewValue(), evt, false);
						task.runNextAt(evt.getNewValue());
					} else
						deactivate();
				});
			}
			getWrapped().noInitChanges().act(this::execute);
			// Activate last
			isActive.changes().act(evt -> {
				if (theCallbackLock)
					return;
				task.setActive(Boolean.TRUE.equals(evt.getNewValue()));
			});
		}

		private void executeIfAllowed() {
			Instant time = theHandle.getPreviousExecution();
			if (theUntil != null) {
				Instant until = theUntil.get();
				if (until != null && time.compareTo(until) > 0) {
					deactivate();
					return;
				}
			}
			// First, make sure we're allowed to execute
			if (theRemainingExecutions != null) {
				Integer remaining = theRemainingExecutions.get();
				if (remaining != null && remaining <= 0) {
					deactivate();
					return;
				}
			}
			getWrapped().set(time, null);
		}

		private void execute(ObservableValueEvent<Instant> event) {
			if (isExecuting != null && isExecuting.isAcceptable(true) == null) {
				isExecuting.set(true, event);
				event.onFinish(theExecuteFinish);
			}
			if (theRemainingExecutions != null) {
				Integer remaining = theRemainingExecutions.get();
				if (remaining != null && remaining > 0) {
					remaining = remaining - 1;
					if (theRemainingExecutions.isAcceptable(remaining) == null)
						theRemainingExecutions.set(remaining, event);
				}
			}
			if (theNextExecution != null && theNextExecution.isAcceptable(theHandle.getNextExecution()) == null) {
				theCallbackLock = true;
				try {
					theNextExecution.set(theHandle.getNextExecution(), event);
				} finally {
					theCallbackLock = false;
				}
			}
			if (theExecutionCount != null) {
				Integer count = theExecutionCount.get();
				count = count == null ? 1 : count + 1;
				if (theExecutionCount.isAcceptable(count) == null)
					theExecutionCount.set(count, event);
			}
			if (theAction != null) {
				try {
					theAction.act(event);
				} catch (Throwable e) {
					theActionReporting.error("Timer action throw an exception", e);
				}
			}
		}

		private void activate(Instant nextRun, Object cause, boolean activateTask) {
			if (nextRun == null)
				nextRun = Instant.now();
			if (theUntil != null) {
				Instant until = theUntil.get();
				if (until != null && until.compareTo(nextRun) <= 0)
					theUntil.set(null, null);
			}
			if (activateTask) {
				if (!Boolean.TRUE.equals(isActive.get()) && isActive.isAcceptable(true) == null)
					isActive.set(true, null);
				else
					theHandle.setActive(true);
			} else {
				theCallbackLock = true;
				try {
					if (!Boolean.TRUE.equals(isActive.get()) && isActive.isAcceptable(true) == null)
						isActive.set(true, null);
				} finally {
					theCallbackLock = false;
				}
			}
		}

		private void deactivate() {
			if (!Boolean.FALSE.equals(isActive.get()) && isActive.isAcceptable(false) == null)
				isActive.set(false, null);
			else
				theHandle.setActive(false);
		}

		@Override
		public Instant set(Instant value) throws IllegalArgumentException, UnsupportedOperationException {
			throw new UnsupportedOperationException(StdMsg.UNSUPPORTED_OPERATION);
		}

		@Override
		public Setter<Instant> lockWrite(boolean tryOnly, Object cause) {
			Getter<Instant> getter = lock(tryOnly);
			if (getter == null)
				return null;
			return new Setter.Unsettable<>(getter, getter, StdMsg.UNSUPPORTED_OPERATION);
		}

		@Override
		public Instant set(Instant value, Object cause) throws IllegalArgumentException, UnsupportedOperationException {
			throw new UnsupportedOperationException(StdMsg.UNSUPPORTED_OPERATION);
		}

		@Override
		public String isAcceptable(Instant value) {
			return StdMsg.UNSUPPORTED_OPERATION;
		}

		@Override
		public ObservableValue<String> isEnabled() {
			return ObservableValue.of(StdMsg.UNSUPPORTED_OPERATION);
		}

		@Override
		public String toString() {
			return "timer";
		}
	}
}