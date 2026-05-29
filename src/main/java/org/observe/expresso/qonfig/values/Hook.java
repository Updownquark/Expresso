package org.observe.expresso.qonfig.values;

import java.util.List;

import org.observe.Observable;
import org.observe.ObservableAction;
import org.observe.SettableValue;
import org.observe.expresso.CompiledExpressoEnv;
import org.observe.expresso.ExpressoInterpretationException;
import org.observe.expresso.ModelInstantiationException;
import org.observe.expresso.ModelType;
import org.observe.expresso.ModelTypes;
import org.observe.expresso.VariableType;
import org.observe.expresso.ModelType.ModelInstanceType;
import org.observe.expresso.ObservableModelSet.InterpretedValueSynth;
import org.observe.expresso.ObservableModelSet.ModelComponentId;
import org.observe.expresso.ObservableModelSet.ModelInstantiator;
import org.observe.expresso.ObservableModelSet.ModelSetInstance;
import org.observe.expresso.ObservableModelSet.ModelValueInstantiator;
import org.observe.expresso.qonfig.CompiledExpression;
import org.observe.expresso.qonfig.DocumentMap;
import org.observe.expresso.qonfig.ExElement;
import org.observe.expresso.qonfig.ExElementTraceable;
import org.observe.expresso.qonfig.ExFlexibleElementModelAddOn;
import org.observe.expresso.qonfig.ExTyped;
import org.observe.expresso.qonfig.ExWithElementModel;
import org.observe.expresso.qonfig.ExpressoBaseV0_1;
import org.observe.expresso.qonfig.ExpressoQIS;
import org.observe.expresso.qonfig.ModelValueElement;
import org.observe.expresso.qonfig.ObservableModelElement;
import org.observe.expresso.qonfig.QonfigAttributeGetter;
import org.observe.expresso.qonfig.ExElement.Def;
import org.observe.util.TypeTokens;
import org.qommons.QommonsUtils;
import org.qommons.config.QonfigElementOrAddOn;
import org.qommons.config.QonfigInterpretationException;

import com.google.common.reflect.TypeToken;

/** Performs an action when an event happens */
@ExElementTraceable(toolkit = ExpressoBaseV0_1.BASE,
	qonfigType = "hook",
	interpretation = Hook.Interpreted.class,
	instance = Hook.Instantiator.class)
public class Hook extends ExElement.Def.Abstract<ModelValueElement<?>>
implements ModelValueElement.CompiledSynth<Observable<?>, ModelValueElement<?>> {
	private String theModelPath;
	private CompiledExpression theEvent;
	private CompiledExpression theAction;
	private ModelComponentId theEventVariable;

	/**
	 * @param parent The parent element of this value element
	 * @param qonfigType The Qonfig type of this value element
	 */
	public Hook(Def<?> parent, QonfigElementOrAddOn qonfigType) {
		super(parent, qonfigType);
	}

	@Override
	public String getModelPath() {
		return theModelPath;
	}

	@Override
	public ModelType<Observable<?>> getModelType(CompiledExpressoEnv env) {
		return ModelTypes.Event;
	}

	/** @return The type of the event */
	public VariableType getEventType() {
		return getAddOnValue(ExTyped.Def.class, ExTyped.Def::getValueType);
	}

	/** @return The event to listen to */
	@QonfigAttributeGetter("on")
	public CompiledExpression getEvent() {
		return theEvent;
	}

	/** @return The action to perform */
	public CompiledExpression getAction() {
		return theAction;
	}

	@Override
	public CompiledExpression getElementValue() {
		return theAction;
	}

	/** @return The model ID of the variable that will contain the event that fired while its action is executing */
	public ModelComponentId getEventVariable() {
		return theEventVariable;
	}

	@Override
	protected void doUpdate(ExpressoQIS session) throws QonfigInterpretationException {
		super.doUpdate(session);
		theModelPath = session.get(ModelValueElement.PATH_KEY, String.class);
		theEvent = getAttributeExpression("on", session);
		theAction = getValueExpression(session);
		ExWithElementModel.Def elModels = getAddOn(ExWithElementModel.Def.class);
		theEventVariable = elModels.getElementValueModelId("event");
		elModels.<Hook.Interpreted<?>, SettableValue<?>> satisfyElementSingleValueType(theEventVariable, ModelTypes.Value,
			Interpreted::getOrEvalEventType);
	}

	@Override
	public void prepareModelValue(ExpressoQIS session) { // Nothing to do
	}

	@Override
	public Hook.Interpreted<?> interpretValue(ExElement.Interpreted<?> parent) {
		return new Hook.Interpreted<>(this, parent);
	}

	/**
	 * {@link Hook} interpretation
	 *
	 * @param <T> The type of the event
	 */
	public static class Interpreted<T> extends ExElement.Interpreted.Abstract<ModelValueElement<Observable<T>>>
	implements ModelValueElement.InterpretedSynth<Observable<?>, Observable<T>, ModelValueElement<Observable<T>>> {
		private ObservableModelElement.Interpreted<?> theInterpretedModel;
		private TypeToken<T> theEventType;
		private InterpretedValueSynth<Observable<?>, Observable<T>> theEvent;
		private InterpretedValueSynth<ObservableAction, ObservableAction> theAction;

		Interpreted(Hook definition, ExElement.Interpreted<?> parent) {
			super(definition, parent);
		}

		@Override
		public Hook getDefinition() {
			return (Hook) super.getDefinition();
		}

		@Override
		public ObservableModelElement.Interpreted<?> getInterpretedModel() {
			return theInterpretedModel;
		}

		@Override
		public void setInterpretedModel(ObservableModelElement.Interpreted<?> interpretedModel) {
			theInterpretedModel = interpretedModel;
		}

		@Override
		public InterpretedValueSynth<?, ?> getElementValue() {
			return theAction;
		}

		/** @return The event to listen to */
		public InterpretedValueSynth<Observable<?>, Observable<T>> getEvent() {
			return theEvent;
		}

		TypeToken<T> getOrEvalEventType() throws ExpressoInterpretationException {
			if (theEvent == null) {
				VariableType defType = getDefinition().getEventType();
				if (defType != null) {
					theEventType = (TypeToken<T>) interpretType(defType);
					theEvent = interpret(getDefinition().getEvent(), ModelTypes.Event.forType(theEventType));
				} else if (getDefinition().getEvent() != null) {
					theEvent = interpret(getDefinition().getEvent(), ModelTypes.Event.<Observable<T>> anyAs());
					theEventType = (TypeToken<T>) theEvent.getType().getType(0);
				} else {
					theEventType = (TypeToken<T>) TypeTokens.get().VOID;
					theEvent = null;
				}
			}
			return theEventType;
		}

		/** @return The action to perform */
		public InterpretedValueSynth<ObservableAction, ObservableAction> getAction() {
			return theAction;
		}

		@Override
		public void updateValue() throws ExpressoInterpretationException {
			update();
		}

		@Override
		protected void doUpdate() throws ExpressoInterpretationException {
			super.doUpdate();
			getOrEvalEventType();
			theAction = interpret(getDefinition().getAction(), ModelTypes.Action.instance());
		}

		@Override
		public List<? extends InterpretedValueSynth<?, ?>> getComponents() {
			return QommonsUtils.unmodifiableCopy(theEvent, theAction);
		}

		@Override
		public ModelInstanceType<Observable<?>, Observable<T>> getType() {
			return ModelTypes.Event.forType(theEventType);
		}

		/** @return The type of the event */
		public TypeToken<T> getEventType() {
			return theEventType;
		}

		@Override
		public ModelValueElement<Observable<T>> create() throws ModelInstantiationException {
			return new Hook.Instantiator<>(this);
		}
	}

	/**
	 * {@link Hook} instantiator
	 *
	 * @param <T> The type of the event
	 */
	public static class Instantiator<T> extends ModelValueElement.Abstract<Observable<T>> {
		private final DocumentMap<ModelInstantiator> theLocalModels;
		private final ModelValueInstantiator<Observable<T>> theEvent;
		private final ModelValueInstantiator<ObservableAction> theAction;
		private final T theDefaultEventValue;
		private final ModelComponentId theEventValue;

		Instantiator(Hook.Interpreted<T> interpreted) throws ModelInstantiationException {
			super(interpreted);
			theLocalModels = interpreted.instantiateLocalModels();
			theEvent = interpreted.getEvent() == null ? null : interpreted.getEvent().instantiate();
			theDefaultEventValue = TypeTokens.get().getDefaultValue(interpreted.getEventType());
			theAction = interpreted.getAction().instantiate();
			theEventValue = interpreted.getDefinition().getEventVariable();
		}

		/** @return The event to listen to */
		public ModelValueInstantiator<Observable<T>> getEvent() {
			return theEvent;
		}

		/** @return The action to perform */
		public ModelValueInstantiator<ObservableAction> getAction() {
			return theAction;
		}

		@Override
		public void instantiate() throws ModelInstantiationException {
			theLocalModels.forEach(ModelInstantiator::instantiate);
			if (theEvent != null)
				theEvent.instantiate();
			theAction.instantiate();
		}

		@Override
		public Observable<T> evaluate(ModelSetInstance models) throws ModelInstantiationException, IllegalStateException {
			models = theLocalModels.operate(models, (m, mi) -> mi.wrap(m));
			instantiate(models);
			Observable<T> on = theEvent == null ? null : theEvent.get(models);
			ObservableAction action = theAction.get(models);
			return create(on, action, models);
		}

		Observable<T> create(Observable<T> on, ObservableAction action, ModelSetInstance models) throws ModelInstantiationException {
			SettableValue<T> event = SettableValue.<T> build()//
				.withValue(theDefaultEventValue).build();
			ExFlexibleElementModelAddOn.satisfyElementValue(theEventValue, models, event);
			if (on != null) {
				on.takeUntil(models.getUntil()).act(v -> {
					event.set(v, null);
					action.act(v);
				});
				return on;
			} else {
				action.act(null);
				return Observable.empty();
			}
		}

		@Override
		public Observable<T> forModelCopy(Observable<T> value, ModelSetInstance sourceModels, ModelSetInstance newModels)
			throws ModelInstantiationException {
			Observable<T> oldEvent = theEvent == null ? null : theEvent.get(sourceModels);
			Observable<T> newEvent = theEvent == null ? null : theEvent.forModelCopy(oldEvent, sourceModels, newModels);
			ObservableAction oldAction = theAction.get(sourceModels);
			ObservableAction newAction = theAction.forModelCopy(oldAction, sourceModels, newModels);
			if (oldEvent == newEvent && oldAction == newAction)
				return value;
			else
				return create(newEvent, newAction, theLocalModels.operate(newModels, (m, mi) -> mi.wrap(m)));
		}
	}
}