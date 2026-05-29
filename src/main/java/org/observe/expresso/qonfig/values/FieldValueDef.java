package org.observe.expresso.qonfig.values;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import org.observe.ObservableAction;
import org.observe.ObservableValue;
import org.observe.SettableValue;
import org.observe.SimpleObservable;
import org.observe.expresso.ExpressoInterpretationException;
import org.observe.expresso.ModelInstantiationException;
import org.observe.expresso.ModelTypes;
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
import org.observe.expresso.qonfig.ExWithElementModel;
import org.observe.expresso.qonfig.ExpressoBaseV0_1;
import org.observe.expresso.qonfig.ExpressoQIS;
import org.observe.expresso.qonfig.ModelValueElement;
import org.observe.expresso.qonfig.QonfigAttributeGetter;
import org.observe.expresso.qonfig.QonfigChildGetter;
import org.observe.expresso.qonfig.ModelValueElement.CompiledSynth;
import org.observe.expresso.qonfig.ModelValueElement.InterpretedSynth;
import org.qommons.QommonsUtils;
import org.qommons.Transaction;
import org.qommons.collect.BetterList;
import org.qommons.config.QonfigElementOrAddOn;
import org.qommons.config.QonfigInterpretationException;
import org.qommons.io.ErrorReporting;

import com.google.common.reflect.TypeToken;

/** &lt;field-value> element */
@ExElementTraceable(toolkit = ExpressoBaseV0_1.BASE,
	qonfigType = FieldValueDef.FIELD_VALUE,
	interpretation = FieldValueDef.Interpreted.class,
	instance = FieldValueDef.Instantiator.class)
public class FieldValueDef extends AbstractCompiledValue {
	/** The XML name of this element */
	public static final String FIELD_VALUE = "field-value";

	private ModelComponentId theTargetAs;
	private CompiledExpression theSource;
	private CompiledExpression theSave;
	private final List<ModelValueElement.CompiledSynth<?, ?>> thePostActions;

	/**
	 * @param parent The parent element of this value element
	 * @param qonfigType The Qonfig type of this value element
	 */
	public FieldValueDef(ExElement.Def<?> parent, QonfigElementOrAddOn qonfigType) {
		super(parent, qonfigType);
		thePostActions = new ArrayList<>();
	}

	/** @return The model ID of the model value in which this value will store the value being set when the save action is called */
	@QonfigAttributeGetter("target-as")
	public ModelComponentId getTargetAs() {
		return theTargetAs;
	}

	/** @return The expression to get this value from */
	@QonfigAttributeGetter("source")
	public CompiledExpression getSource() {
		return theSource;
	}

	/**
	 * @return The action that will change the effective value of the {@link #getSource()} expression using the value in the
	 *         {@link #getTargetAs()} model value
	 */
	@QonfigAttributeGetter("save")
	public CompiledExpression getSave() {
		return theSave;
	}

	/** @return Actions to occur after a save */
	@QonfigChildGetter("post-action")
	public List<ModelValueElement.CompiledSynth<?, ?>> getPostActions() {
		return Collections.unmodifiableList(thePostActions);
	}

	@Override
	protected void doUpdate(ExpressoQIS session) throws QonfigInterpretationException {
		super.doUpdate(session.asElement(session.getFocusType().getSuperElement()));

		theSource = getAttributeExpression("source", session);
		theSave = getAttributeExpression("save", session);
		String targetAsName = session.getAttributeText("target-as");
		ExWithElementModel.Def elModels = getAddOn(ExWithElementModel.Def.class);
		theTargetAs = elModels.getElementValueModelId(targetAsName);
		elModels.<FieldValueDef.Interpreted<?>, SettableValue<?>> satisfyElementSingleValueType(theTargetAs, ModelTypes.Value,
			Interpreted::getOrEvalSourceType);
		syncChildren(ModelValueElement.CompiledSynth.class, thePostActions, session.forChildren("post-action"));
	}

	@Override
	protected void doPrepare(ExpressoQIS session) { // Nothing to do
	}

	@Override
	public InterpretedSynth<SettableValue<?>, ?, ? extends ModelValueElement<?>> interpretValue(ExElement.Interpreted<?> parent) {
		return new FieldValueDef.Interpreted<>(this, parent);
	}

	/**
	 * {@link FieldValueDef} interpretation
	 *
	 * @param <T> The type of the value
	 */
	public static class Interpreted<T> extends AbstractCompiledValue.Interpreted<T> {
		private InterpretedValueSynth<SettableValue<?>, SettableValue<T>> theSource;
		private InterpretedValueSynth<ObservableAction, ObservableAction> theSave;
		private final List<Action.Interpreted> thePostActions;

		Interpreted(FieldValueDef definition, ExElement.Interpreted<?> parent) {
			super(definition, parent);
			thePostActions = new ArrayList<>();
		}

		@Override
		public FieldValueDef getDefinition() {
			return (FieldValueDef) super.getDefinition();
		}

		/** @return The expression to get this value from */
		public InterpretedValueSynth<SettableValue<?>, SettableValue<T>> getSource() {
			return theSource;
		}

		/**
		 * @return The action that will change the effective value of the {@link #getSource()} expression using the value in the
		 *         {@link #getTargetAs()} model value
		 */
		public InterpretedValueSynth<ObservableAction, ObservableAction> getSave() {
			return theSave;
		}

		/** @return Actions to occur after a save */
		public List<Action.Interpreted> getPostActions() {
			return Collections.unmodifiableList(thePostActions);
		}

		@Override
		public List<? extends InterpretedValueSynth<?, ?>> getComponents() {
			return Arrays.asList(theSource, theSave);
		}

		@Override
		protected ModelInstanceType<SettableValue<?>, SettableValue<T>> getTargetType() {
			return theSource.getType();
		}

		/**
		 * @return The type of this value
		 * @throws ExpressoInterpretationException If the value type could not be determined
		 */
		protected TypeToken<T> getOrEvalSourceType() throws ExpressoInterpretationException {
			return (TypeToken<T>) theSource.getType().getType(0);
		}

		@Override
		protected void doUpdate() throws ExpressoInterpretationException {
			theSource = interpret(getDefinition().getSource(), ModelTypes.Value.anyAsV());

			super.doUpdate();

			getOrEvalSourceType();
			theSave = interpret(getDefinition().getSave(), ModelTypes.Action.instance());
			try (Transaction t = ModelValueElement.INTERPRETING_PARENTS.installParent(this)) {
				syncChildren(getDefinition().getPostActions(), thePostActions, //
					d -> (Action.Interpreted) d.interpret(getDefaultEnv()),
					Action.Interpreted::update);
			}
		}

		@Override
		public ModelValueElement<SettableValue<T>> create() throws ModelInstantiationException {
			return new FieldValueDef.Instantiator<>(this, BetterList.of2(thePostActions.stream(), Action.Interpreted::create));
		}
	}

	/**
	 * {@link FieldValueDef} instantiator
	 *
	 * @param <T> The type of the value
	 */
	public static class Instantiator<T> extends ModelValueElement.Abstract<SettableValue<T>> {
		private final DocumentMap<ModelInstantiator> theLocalModels;
		private final ModelValueInstantiator<SettableValue<T>> theSource;
		private final ModelValueInstantiator<ObservableAction> theSave;
		private final ModelComponentId theTargetAs;
		private final List<Action.Instantiator> thePostActions;
		private final ErrorReporting theReporting;

		Instantiator(FieldValueDef.Interpreted<T> interpreted, List<Action.Instantiator> postActions)
			throws ModelInstantiationException {
			super(interpreted);
			theLocalModels = interpreted.instantiateLocalModels();
			theSource = interpreted.getSource().instantiate();
			theSave = interpreted.getSave().instantiate();
			theTargetAs = interpreted.getDefinition().getTargetAs();
			thePostActions = postActions;
			theReporting = interpreted.reporting();
		}

		/** @return The expression to get this value from */
		public ModelValueInstantiator<SettableValue<T>> getSource() {
			return theSource;
		}

		/**
		 * @return The action that will change the effective value of the {@link #getSource()} expression using the value in the
		 *         {@link #getTargetAs()} model value
		 */
		public ModelValueInstantiator<ObservableAction> getSave() {
			return theSave;
		}

		/** @return Actions to occur after a save */
		public List<Action.Instantiator> getPostActions() {
			return thePostActions;
		}

		/** @return The model ID of the model value in which this value will store the value being set when the save action is called */
		public ModelComponentId getTargetAs() {
			return theTargetAs;
		}

		@Override
		public void instantiate() throws ModelInstantiationException {
			theLocalModels.forEach(ModelInstantiator::instantiate);
			theSource.instantiate();
			theSave.instantiate();
			for (Action.Instantiator postAction : thePostActions)
				postAction.instantiate();
		}

		@Override
		public SettableValue<T> evaluate(ModelSetInstance models) throws ModelInstantiationException, IllegalStateException {
			models = theLocalModels.operate(models, (m, mi) -> mi.wrap(m));
			instantiate(models);
			SettableValue<T> source = theSource.get(models);
			ObservableAction save = theSave.get(models);
			SettableValue<T> targetAs = SettableValue.<T> build().build();
			ExFlexibleElementModelAddOn.satisfyElementValue(theTargetAs, models, targetAs);
			ModelSetInstance fModels = models;
			List<ObservableAction> postActions = QommonsUtils.filterMapE(thePostActions, null, a -> a.get(fModels));
			return new FieldValueDef.FieldValue<>(theReporting, source, save, targetAs, postActions);
		}

		@Override
		public SettableValue<T> forModelCopy(SettableValue<T> value, ModelSetInstance sourceModels, ModelSetInstance newModels)
			throws ModelInstantiationException {
			sourceModels = theLocalModels.operate(sourceModels, (m, mi) -> mi.wrap(m));
			newModels = theLocalModels.operate(newModels, (m, mi) -> mi.wrap(m));
			SettableValue<T> sourceSource = ((FieldValueDef.FieldValue<T>) value).getSource();
			ObservableAction sourceSave = ((FieldValueDef.FieldValue<T>) value).getSave();
			SettableValue<T> newSource = theSource.forModelCopy(sourceSource, sourceModels, newModels);
			ObservableAction newSave = theSave.forModelCopy(sourceSave, sourceModels, newModels);
			List<ObservableAction> sourcePostActions = ((FieldValueDef.FieldValue<T>) value).getPostActions();
			List<ObservableAction> postActions = new ArrayList<>(thePostActions.size());
			for (int i = 0; i < thePostActions.size(); i++)
				postActions.add(thePostActions.get(i).forModelCopy(sourcePostActions.get(i), sourceModels, newModels));
			if (sourceSource == newSource && sourceSave == newSave && sourcePostActions.equals(postActions))
				return value;
			else {
				SettableValue<T> targetAs = SettableValue.<T> build().build();
				ExFlexibleElementModelAddOn.satisfyElementValue(theTargetAs, newModels, targetAs);
				return new FieldValueDef.FieldValue<>(theReporting, newSource, newSave, targetAs, Collections.unmodifiableList(postActions));
			}
		}
	}

	static class FieldValue<T> extends SettableValue.RefreshingSettableValue<T> {
		private final ErrorReporting theReporting;
		private final SettableValue<T> theSource;
		private final ObservableAction theSave;
		private final SettableValue<T> theSourceAs;
		private final List<ObservableAction> thePostActions;
		private boolean isSetting;

		FieldValue(ErrorReporting reporting, SettableValue<T> source, ObservableAction save, SettableValue<T> sourceAs,
			List<ObservableAction> postActions) {
			super(source.alias(reporting.toString()), new SimpleObservable<>());
			theReporting = reporting;
			theSource = source;
			theSave = save;
			theSourceAs = sourceAs;
			thePostActions = postActions;
		}

		SettableValue<T> getSource() {
			return theSource;
		}

		ObservableAction getSave() {
			return theSave;
		}

		List<ObservableAction> getPostActions() {
			return thePostActions;
		}

		@Override
		public ObservableValue<String> isEnabled() {
			// We need a value to determine
			return SettableValue.ALWAYS_ENABLED;
		}

		@Override
		public String isAcceptable(T value) {
			theSourceAs.set(value, null);
			return theSave.isEnabled().get();
		}

		@Override
		public T set(T value) throws IllegalArgumentException, UnsupportedOperationException {
			T old = theSource.get();
			// Prevent reentrancy due to refresh
			if (isSetting && old == value)
				return value;
			try {
				isSetting = true;
				long preStamp = getStamp();
				theSourceAs.set(value);
				theSave.act(getRootCausable());
				for (ObservableAction postAction : thePostActions) {
					if (postAction.isEnabled().get() == null)
						postAction.act(getRootCausable());
				}
				if (preStamp == getStamp()) // Only refresh if none of the save or the actual actions caused an event
					((SimpleObservable<Void>) getRefresh()).onNext(null);
			} catch (RuntimeException e) {
				theReporting.error(e.getMessage(), e);
				throw e;
			} finally {
				isSetting = false;
			}
			return old;
		}

		@Override
		public Object getIdentity() {
			return theSource.getIdentity();
		}

		@Override
		protected Object createIdentity() {
			return theSource.getIdentity();
		}
	}
}