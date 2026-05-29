package org.observe.expresso.qonfig.values;

import java.util.Collections;
import java.util.List;

import org.observe.SettableValue;
import org.observe.expresso.ModelInstantiationException;
import org.observe.expresso.ModelTypes;
import org.observe.expresso.ModelType.ModelInstanceType;
import org.observe.expresso.ObservableModelSet.InterpretedValueSynth;
import org.observe.expresso.ObservableModelSet.ModelSetInstance;
import org.observe.expresso.ObservableModelSet.ModelValueInstantiator;
import org.observe.expresso.qonfig.ExElement;
import org.observe.expresso.qonfig.ExIntValue;
import org.observe.expresso.qonfig.ExTyped;
import org.observe.expresso.qonfig.ExpressoQIS;
import org.observe.expresso.qonfig.ModelValueElement;
import org.observe.util.TypeTokens;
import org.qommons.config.QonfigElementOrAddOn;
import org.qommons.config.QonfigInterpretationException;

import com.google.common.reflect.TypeToken;

/** &lt;value> element */
public class SimpleValueDef extends AbstractCompiledValue {
	/**
	 * @param parent The parent element of this value element
	 * @param qonfigType The Qonfig type of this value element
	 */
	public SimpleValueDef(ExElement.Def<?> parent, QonfigElementOrAddOn qonfigType) {
		super(parent, qonfigType);
	}

	@Override
	protected void doUpdate(ExpressoQIS session) throws QonfigInterpretationException {
		super.doUpdate(session.asElement(session.getFocusType().getSuperElement()));
		ExIntValue.Def intValue = getAddOn(ExIntValue.Def.class);
		if (intValue == null) {
			if (getElementValue() == null || getValueType() != null)
				reporting()
				.warn("This interpretation is intended to be used with internal values (children of a <model> element) only.");
			else
				throw new QonfigInterpretationException(
					"This interpretation is intended to be used with internal values (children of a <model> element) only.\n"
						+ "When this is not true, either a type or a value MUST be specified",
						reporting().getFileLocation().getPosition(0), 0);
		} else {
			if (getElementValue() != null) {
				if (intValue.getInit() != null)
					session.reporting()
					.warn("Either a value or an init value may be specified, but not both.  Initial value will be ignored.");
			} else if (intValue.getInit() == null && getValueType() == null)
				throw new QonfigInterpretationException("One of a type, a value, or an initial value MUST be specified",
					reporting().getFileLocation().getPosition(0), 0);
		}
	}

	@Override
	protected void doPrepare(ExpressoQIS session) { // Nothing to do
	}

	@Override
	public SimpleValueDef.Interpreted<?> interpretValue(ExElement.Interpreted<?> parent) {
		return new SimpleValueDef.Interpreted<>(this, parent);
	}

	/**
	 * {@link SimpleValueDef} interpretation
	 *
	 * @param <T> The type of the value
	 */
	public static class Interpreted<T> extends AbstractCompiledValue.Interpreted<T> {
		Interpreted(SimpleValueDef definition, ExElement.Interpreted<?> parent) {
			super(definition, parent);
		}

		@Override
		public SimpleValueDef getDefinition() {
			return (SimpleValueDef) super.getDefinition();
		}

		/** @return The initialization for the value */
		public InterpretedValueSynth<SettableValue<?>, SettableValue<T>> getInit() {
			return getAddOnValue(ExIntValue.Interpreted.class, ExIntValue.Interpreted::getInit);
		}

		@Override
		public List<? extends InterpretedValueSynth<?, ?>> getComponents() {
			if (getElementValue() != null)
				return Collections.singletonList(getElementValue());
			else
				return Collections.emptyList(); // Independent (fundamental) value
		}

		@Override
		public ModelInstanceType<SettableValue<?>, SettableValue<T>> getType() {
			TypeToken<T> type = getAddOnValue(ExTyped.Interpreted.class, ExTyped.Interpreted::getValueType);
			if (type != null)
				return ModelTypes.Value.forType(type);
			else if (getDefinition().getValueType() != null || getElementValue() != null)
				return super.getType();
			return getInit().getType();
		}

		@Override
		public ModelValueElement<SettableValue<T>> create() throws ModelInstantiationException {
			return new SimpleValueDef.Instantiator<>(this, getInit() == null ? null : getInit().instantiate());
		}
	}

	/**
	 * {@link SimpleValueDef} instantiator
	 *
	 * @param <T> The type of the value
	 */
	public static class Instantiator<T> extends ModelValueElement.Abstract<SettableValue<T>> {
		private final ModelValueInstantiator<SettableValue<T>> theInit;
		private final T theDefaultValue;
		private final String theAlias;

		Instantiator(SimpleValueDef.Interpreted<T> parent, ModelValueInstantiator<SettableValue<T>> init)
			throws ModelInstantiationException {
			super(parent);
			theInit = parent.getInit() == null ? null : parent.getInit().instantiate();
			theDefaultValue = (T) TypeTokens.get().getDefaultValue(parent.getType().getType(0));
			theAlias = getModelPath() + ":" + parent.toString();
		}

		@Override
		public ModelValueInstantiator<SettableValue<T>> getElementValue() {
			return (ModelValueInstantiator<SettableValue<T>>) super.getElementValue();
		}

		@Override
		public void instantiate() throws ModelInstantiationException {
			super.instantiate();
			if (theInit != null)
				theInit.instantiate();
		}

		@Override
		public SettableValue<T> evaluate(ModelSetInstance models) throws ModelInstantiationException, IllegalStateException {
			instantiate(models);
			if (getElementValue() != null)
				return getElementValue().get(models)//
					.alias(theAlias);
			else {
				SettableValue.Builder<T> builder = SettableValue.build();
				if (getModelPath() != null)
					builder.withDescription(getModelPath());
				if (theInit != null) {
					SettableValue<T> initV = theInit.get(models);
					builder.withValue(initV.get());
				} else
					builder.withValue(theDefaultValue);
				return builder.build()//
					.alias(theAlias);
			}
		}

		@Override
		public SettableValue<T> forModelCopy(SettableValue<T> value, ModelSetInstance sourceModels, ModelSetInstance newModels)
			throws ModelInstantiationException {
			if (getElementValue() != null)
				return getElementValue().forModelCopy(value, sourceModels, newModels)//
					.alias(theAlias);
			else
				return value; // Independent (fundamental) value
		}
	}
}