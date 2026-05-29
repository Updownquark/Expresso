package org.observe.expresso.qonfig.values;

import java.util.Collection;
import java.util.Collections;
import java.util.List;

import org.observe.Observable;
import org.observe.ObservableValue;
import org.observe.ObservableValueEvent;
import org.observe.SettableValue;
import org.observe.ObservableValue.Getter;
import org.observe.SettableValue.Setter;
import org.observe.expresso.ModelInstantiationException;
import org.observe.expresso.ObservableModelSet.InterpretedValueSynth;
import org.observe.expresso.ObservableModelSet.ModelSetInstance;
import org.observe.expresso.ObservableModelSet.ModelValueInstantiator;
import org.observe.expresso.qonfig.ExElement;
import org.observe.expresso.qonfig.ExpressoQIS;
import org.observe.expresso.qonfig.ModelValueElement;
import org.qommons.Transaction;
import org.qommons.CausalLock.Cause;
import org.qommons.Identifiable.AbstractIdentifiable;
import org.qommons.config.QonfigElementOrAddOn;
import org.qommons.config.QonfigInterpretationException;
import org.qommons.io.ErrorReporting;

/** &lt;constant> element */
public class ConstantValueDef extends AbstractCompiledValue {
	/**
	 * @param parent The parent element for this element
	 * @param qonfigType The Qonfig type of this element
	 */
	public ConstantValueDef(ExElement.Def<?> parent, QonfigElementOrAddOn qonfigType) {
		super(parent, qonfigType);
	}

	@Override
	protected void doPrepare(ExpressoQIS session) { // Nothing to do
	}

	@Override
	protected void doUpdate(ExpressoQIS session) throws QonfigInterpretationException {
		super.doUpdate(session.asElement(session.getFocusType().getSuperElement()));
	}

	@Override
	public ConstantValueDef.Interpreted<?> interpretValue(ExElement.Interpreted<?> parent) {
		return new ConstantValueDef.Interpreted<>(this, parent);
	}

	/**
	 * {@link ConstantValueDef} implementation
	 *
	 * @param <T> The type of the value
	 */
	public static class Interpreted<T> extends AbstractCompiledValue.Interpreted<T> {
		Interpreted(ConstantValueDef definition, ExElement.Interpreted<?> parent) {
			super(definition, parent);
		}

		@Override
		public ConstantValueDef getDefinition() {
			return (ConstantValueDef) super.getDefinition();
		}

		@Override
		public List<? extends InterpretedValueSynth<?, ?>> getComponents() {
			return Collections.emptyList();
		}

		@Override
		public ModelValueElement<SettableValue<T>> create() throws ModelInstantiationException {
			return new ConstantValueDef.Instantiator<>(this);
		}
	}

	/**
	 * {@link ConstantValueDef} instantiator
	 *
	 * @param <T> The type of the value
	 */
	public static class Instantiator<T> extends ModelValueElement.Abstract<SettableValue<T>> {
		private final ErrorReporting theReporting;
		Instantiator(ConstantValueDef.Interpreted<T> interpreted) throws ModelInstantiationException {
			super(interpreted);
			theReporting = interpreted.reporting();
		}

		@Override
		public ModelValueInstantiator<SettableValue<T>> getElementValue() {
			return (ModelValueInstantiator<SettableValue<T>>) super.getElementValue();
		}

		@Override
		public SettableValue<T> evaluate(ModelSetInstance models) throws ModelInstantiationException, IllegalStateException {
			instantiate(models);
			SettableValue<T> initV = getElementValue().get(models);
			try {
				return new ConstantValueDef.ConstantValue<>(getModelPath(), initV.get());
			} catch (RuntimeException e) {
				theReporting.error("Unable to generate constant value", e);
				return new ConstantValueDef.ConstantValue<>(getModelPath(), null);
			}
		}

		@Override
		public SettableValue<T> forModelCopy(SettableValue<T> value, ModelSetInstance sourceModels, ModelSetInstance newModels)
			throws ModelInstantiationException {
			return value; // Constants are constant--no need to copy
		}
	}

	static class ConstantValue<T> extends AbstractIdentifiable implements SettableValue<T> {
		private final String theModelPath;
		private final T theValue;

		public ConstantValue(String path, T value) {
			theModelPath = path;
			theValue = value;
		}

		@Override
		public long getStamp() {
			return 0;
		}

		@Override
		public T get() {
			return theValue;
		}

		@Override
		public Observable<ObservableValueEvent<T>> noInitChanges() {
			return Observable.empty();
		}

		@Override
		public Getter<T> lock(boolean tryOnly) {
			return Getter.of(this, Transaction.NONE);
		}

		@Override
		public Setter<T> lockWrite(boolean tryOnly, Object cause) {
			return new Setter.Unsettable<>(this, Transaction.NONE, "Constant value");
		}

		@Override
		public Collection<Cause> getCurrentCauses() {
			return Collections.emptyList();
		}

		@Override
		public T set(T value2) throws IllegalArgumentException, UnsupportedOperationException {
			throw new UnsupportedOperationException("Constant value");
		}

		@Override
		public String isAcceptable(T value2) {
			return "Constant value";
		}

		@Override
		public ObservableValue<String> isEnabled() {
			return ObservableValue.of("Constant value");
		}

		@Override
		protected Object createIdentity() {
			return theModelPath;
		}

		@Override
		public ConstantValueDef.ConstantValue<T> alias(String alias) {
			super.alias(alias);
			return this;
		}
	}
}