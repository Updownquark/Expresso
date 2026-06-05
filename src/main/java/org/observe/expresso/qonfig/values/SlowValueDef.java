package org.observe.expresso.qonfig.values;

import java.util.Collection;
import java.util.List;

import org.observe.Observable;
import org.observe.ObservableValue;
import org.observe.ObservableValueEvent;
import org.observe.Observer;
import org.observe.SettableValue;
import org.observe.expresso.ExpressoInterpretationException;
import org.observe.expresso.ModelInstantiationException;
import org.observe.expresso.ModelType.ModelInstanceType;
import org.observe.expresso.ModelTypes;
import org.observe.expresso.ObservableModelSet.InterpretedValueSynth;
import org.observe.expresso.ObservableModelSet.ModelInstantiator;
import org.observe.expresso.ObservableModelSet.ModelSetInstance;
import org.observe.expresso.ObservableModelSet.ModelValueInstantiator;
import org.observe.expresso.qonfig.DocumentMap;
import org.observe.expresso.qonfig.ElementTypeTraceability;
import org.observe.expresso.qonfig.ExElement;
import org.observe.expresso.qonfig.ExElementTraceable;
import org.observe.expresso.qonfig.ExElementType;
import org.observe.expresso.qonfig.ExFlexibleElementModelAddOn.ActionIfSatisfied;
import org.observe.expresso.qonfig.ExWithElementModel;
import org.observe.expresso.qonfig.ExpressoBaseV0_1;
import org.observe.expresso.qonfig.ExpressoQIS;
import org.observe.expresso.qonfig.ModelValueElement;
import org.observe.expresso.qonfig.ModelValueElement.InterpretedSynth;
import org.observe.expresso.qonfig.TraceabilityConfiguration;
import org.qommons.CausalLock;
import org.qommons.DefaultCausalLock;
import org.qommons.Identifiable;
import org.qommons.Subscription;
import org.qommons.ThreadConstraint;
import org.qommons.Transaction;
import org.qommons.collect.ListenerList;
import org.qommons.collect.MutableCollectionElement.StdMsg;
import org.qommons.collect.ThreadConstrainedLockingStrategy;
import org.qommons.config.QonfigElementOrAddOn;
import org.qommons.config.QonfigInterpretationException;
import org.qommons.io.ErrorReporting;
import org.qommons.threading.QommonsTimer;

import com.google.common.reflect.TypeToken;

/** &lt;slow-value> element */
@ExElementTraceable(toolkit = ExpressoBaseV0_1.BASE,
qonfigType = SlowValueDef.SLOW_VALUE,
interpretation = FieldValueDef.Interpreted.class,
instance = FieldValueDef.Instantiator.class)
public class SlowValueDef extends AbstractCompiledValue {
	/** The XML name of this element */
	public static final String SLOW_VALUE = "slow-value";
	public static final ExElementType.ExElementModelValue<SettableValue<?>, SettableValue<Integer>> LOAD_ID = ExElementType
		.modelAttributeValue("load-id-as", int.class);
	public static final ExElementType.ExElementModelValue<SettableValue<?>, SettableValue<?>> PREVIOUS_VALUE//
	= ExElementType.singleTypedModelAttributeValue("previous-value-as", ModelTypes.Value,
		interpreted -> ((SlowValueDef.Interpreted<?>) interpreted).getValueType());
	public static final ExElementType.ValueExpression<ThreadConstraint, ?> ON_THREAD = ExElementType.valueExpression("on-thread",
		ThreadConstraint.class, null);
	public static final ExElementType.EventExpression<?, ?> REFRESH = ExElementType.eventExpression("refresh", Object.class);
	public static final ExElementType.ValueExpression<?, ?> VALUE = ExElementType.valueExpression("value", Object.class, null);
	public static final ExElementType.ValueExpression<?, ?> WHILE_LOADING = ExElementType.valueExpression("while-loading",
		interpreted -> ((SlowValueDef.Interpreted<?>) interpreted).getValueType(), null);
	public static final ExElementType SLOW_VALUE_TYPE = ExElementType.build(ExpressoBaseV0_1.NAME, ExpressoBaseV0_1.VERSION, SLOW_VALUE)//
		.withModelValue(LOAD_ID)//
		.withModelValue(PREVIOUS_VALUE)//
		.withValue(ON_THREAD)//
		.withValue(REFRESH)//
		.withValue(VALUE)//
		.withValue(WHILE_LOADING)//
		.build(null);

	@TraceabilityConfiguration
	public static void configureTraceability(
		ElementTypeTraceability.SingleTypeTraceabilityBuilder<ModelValueElement<?>, SlowValueDef.Interpreted<?>, SlowValueDef> traceability) {
		SLOW_VALUE_TYPE.configureElementTraceability(traceability)//
		.configure(SlowValueDef::getTypeData, Interpreted::getTypeData, null);
	}

	private final ExElementType.DefTypeData theTypeData;

	/**
	 * @param parent The parent element of this value element
	 * @param qonfigType The Qonfig type of this value element
	 */
	public SlowValueDef(ExElement.Def<?> parent, QonfigElementOrAddOn qonfigType) {
		this(parent, qonfigType, SLOW_VALUE_TYPE);
	}

	protected SlowValueDef(ExElement.Def<?> parent, QonfigElementOrAddOn qonfigType, ExElementType elementType) {
		super(parent, qonfigType);
		theTypeData = elementType.createDefData();
	}

	protected ExElementType.DefTypeData getTypeData() {
		return theTypeData;
	}

	@Override
	protected void doUpdate(ExpressoQIS session) throws QonfigInterpretationException {
		super.doUpdate(session.asElement(session.getFocusType().getSuperElement()));

		theTypeData.update(this, session);
	}

	@Override
	protected void doPrepare(ExpressoQIS session) { // Nothing to do
	}

	@Override
	public InterpretedSynth<SettableValue<?>, ?, ? extends ModelValueElement<?>> interpretValue(ExElement.Interpreted<?> parent) {
		return new SlowValueDef.Interpreted<>(this, parent);
	}

	/**
	 * {@link FieldValueDef} interpretation
	 *
	 * @param <T> The type of the value
	 */
	public static class Interpreted<T> extends AbstractCompiledValue.Interpreted<T> {
		private final ExElementType.InterpretedTypeData theTypeData;

		Interpreted(SlowValueDef definition, ExElement.Interpreted<?> parent) {
			super(definition, parent);
			theTypeData = definition.getTypeData().interpret();
		}

		@Override
		public SlowValueDef getDefinition() {
			return (SlowValueDef) super.getDefinition();
		}

		protected ExElementType.InterpretedTypeData getTypeData() {
			return theTypeData;
		}

		@Override
		public List<? extends InterpretedValueSynth<?, ?>> getComponents() {
			return theTypeData.getComponents();
		}

		@Override
		protected ModelInstanceType<SettableValue<?>, SettableValue<T>> getTargetType() {
			return (ModelInstanceType<SettableValue<?>, SettableValue<T>>) (Object) theTypeData.getValue(VALUE).getType();
		}

		/**
		 * @return The type of this value
		 * @throws ExpressoInterpretationException If the value type could not be determined
		 */
		protected TypeToken<T> getValueType() throws ExpressoInterpretationException {
			return (TypeToken<T>) theTypeData.getOrInterpretValue(VALUE, this).getType().getType(0);
		}

		@Override
		protected void doUpdate() throws ExpressoInterpretationException {
			super.doUpdate();

			theTypeData.update(this);
		}

		@Override
		public ModelValueElement<SettableValue<T>> create() throws ModelInstantiationException {
			return new SlowValueDef.Instantiator<>(this);
		}
	}

	/**
	 * {@link FieldValueDef} instantiator
	 *
	 * @param <T> The type of the value
	 */
	public static class Instantiator<T> extends ModelValueElement.Abstract<SettableValue<T>> {
		private final DocumentMap<ModelInstantiator> theLocalModels;
		private ExElementType.InstanceTypeData theTypeData;
		private final ErrorReporting theReporting;

		Instantiator(SlowValueDef.Interpreted<T> interpreted) throws ModelInstantiationException {
			super(interpreted);
			theTypeData = interpreted.getTypeData().instantiate(this);
			theLocalModels = interpreted.instantiateLocalModels();
			theReporting = interpreted.reporting();
		}

		protected ExElementType.InstanceTypeData getTypeData() {
			return theTypeData;
		}

		@Override
		public void instantiate() throws ModelInstantiationException {
			super.instantiate();
			theTypeData.instantiated();
			theLocalModels.forEach(ModelInstantiator::instantiate);
		}

		@Override
		public SettableValue<T> evaluate(ModelSetInstance models) throws ModelInstantiationException, IllegalStateException {
			models = theLocalModels.operate(models, (m, mi) -> mi.wrap(m));
			theTypeData.instantiate(models, this);
			return new SlowValueDef.SlowValue<>(//
				theTypeData.satisfyModelValue(LOAD_ID, () -> SettableValue.create(0)), //
				(SettableValue<T>) theTypeData.satisfyModelValue(PREVIOUS_VALUE, SettableValue::create), //
				(SettableValue<T>) theTypeData.getValue(VALUE), //
				theTypeData.getValue(REFRESH), //
				(SettableValue<T>) theTypeData.getValue(WHILE_LOADING), //
				theTypeData.getValue(ON_THREAD).get(), //
				theReporting);
		}

		@Override
		public SettableValue<T> forModelCopy(SettableValue<T> value, ModelSetInstance sourceModels, ModelSetInstance newModels)
			throws ModelInstantiationException {
			sourceModels = theLocalModels.operate(sourceModels, (m, mi) -> mi.wrap(m));
			newModels = theLocalModels.operate(newModels, (m, mi) -> mi.wrap(m));
			return ((SlowValueDef.SlowValue<T>) value).copy(sourceModels, newModels, theTypeData, theReporting);
		}
	}

	static class SlowValue<T> extends AbstractIdentifiable implements SettableValue<T> {
		private final SettableValue<Integer> theLoadId;
		private final SettableValue<T> thePreviousValue;
		private final SettableValue<T> theValue;
		private final Observable<?> theRefresh;
		private final SettableValue<T> theWhileLoading;

		private T theCurrentValue;
		private long theRefreshingStamp;
		private long theValueStamp;
		private final CausalLock theLock;
		private final ListenerList<Observer<? super ObservableValueEvent<T>>> theListeners;
		private Subscription theWhileLoadingSub;

		public SlowValue(SettableValue<Integer> loadId, SettableValue<T> previousValue, SettableValue<T> value, Observable<?> refresh,
			SettableValue<T> whileLoading, ThreadConstraint threading, ErrorReporting reporting) {
			theLoadId = loadId;
			thePreviousValue = previousValue;
			theValue = value;
			theRefresh = refresh;
			theWhileLoading = whileLoading;

			initIdentity(Identifiable.baseId(reporting.toString(), this));
			theLock = new DefaultCausalLock(ThreadConstrainedLockingStrategy.get(threading));
			theRefreshingStamp = -1;
			theListeners = ListenerList.build().skipAddByDefault(true).withInUse(new ListenerList.InUseListener() {
				private Subscription theRefreshSub;

				@Override
				public void inUseChanged(boolean inUse) {
					if (inUse) {
						try (Transaction t = theRefresh.lock(false)) {
							long stamp = theRefresh.getStamp();
							if (stamp != theRefreshingStamp)
								refresh(stamp, null);
							theRefreshSub = theRefresh.act(cause -> refresh(theRefresh.getStamp(), cause));
						}
					} else {
						theRefreshSub.unsubscribe();
						theRefreshSub = null;
					}
				}
			}).build();
		}

		void refresh(long refreshStamp, Object cause) {
			int thisLoadId;
			synchronized (this) {
				theRefreshingStamp = refreshStamp;
				thisLoadId = theLoadId.get() + 1;
				theLoadId.set(thisLoadId, cause);
				if(thePreviousValue!=null)
					thePreviousValue.set(theCurrentValue, cause);
				if (theWhileLoadingSub == null && theWhileLoading != null)
					theWhileLoadingSub = theWhileLoading.changes().act(this::whileLoading);
			}

			QommonsTimer.getCommonInstance().offload(() -> {
				T newValue = theValue.get();
				T oldValue;
				synchronized (this) {
					if (thisLoadId != theLoadId.get())
						return;
					oldValue = theCurrentValue;
					theValueStamp = refreshStamp;
					theCurrentValue = newValue;
					if (theWhileLoadingSub != null) {
						theWhileLoadingSub.unsubscribe();
						theWhileLoadingSub = null;
					}
					fireNewValue(oldValue, theCurrentValue, null);
				}
			});
		}

		synchronized void whileLoading(ObservableValueEvent<T> evt) {
			if (theWhileLoadingSub == null)
				return;
			T old = theCurrentValue;
			theCurrentValue = evt.getNewValue();
			theValueStamp = theWhileLoading.getStamp();
			fireNewValue(old, evt.getNewValue(), evt);
		}

		private void fireNewValue(T oldValue, T newValue, Object cause) {
			theLock.getThreadConstraint().invoke(() -> {
				try (Transaction t = theLock.lockWrite(false, cause)) {
					ObservableValueEvent<T> evt = createChangeEvent(oldValue, newValue, getUnfinishedCauses());
					try (Transaction evtT = evt.use()) {
						theListeners.forEach(//
							l -> l.onNext(evt));
					}
				}
			});
		}

		@Override
		public SettableValue<T> alias(String alias) {
			super.alias(alias);
			return this;
		}

		@Override
		public T get() {
			long stamp = theRefresh.getStamp();
			if (theRefreshingStamp != stamp)
				refresh(stamp, null);
			return theCurrentValue;
		}

		@Override
		public Getter<T> lock(boolean tryOnly) {
			Transaction lock = theLock.lock(tryOnly);
			if (lock == null)
				return null;
			return Getter.of(this, lock);
		}

		@Override
		public Observable<ObservableValueEvent<T>> noInitChanges() {
			class SlowValueChanges extends AbstractIdentifiable implements Observable<ObservableValueEvent<T>> {
				@Override
				public boolean isEventing() {
					return theListeners.isFiring();
				}

				@Override
				public CoreId getCoreId() {
					return theLock.getCoreId();
				}

				@Override
				public long getStamp() {
					return SlowValue.this.getStamp();
				}

				@Override
				protected Object createIdentity() {
					return Identifiable.wrap(SlowValue.this.getIdentity(), "noInitChanges");
				}

				@Override
				public ThreadConstraint getThreadConstraint() {
					return theLock.getThreadConstraint();
				}

				@Override
				public Subscription subscribe(Observer<? super ObservableValueEvent<T>> observer) {
					return theListeners.addNew(observer);
				}

				@Override
				public Transaction lock(boolean tryOnly) {
					return theLock.lock(tryOnly);
				}

				@Override
				public CoreChangeSources getChangeSources() {
					return theRefresh.getChangeSources();
				}
			}
			return new SlowValueChanges();
		}

		@Override
		protected Object createIdentity() {
			throw new IllegalStateException();
		}

		@Override
		public long getStamp() {
			return theValueStamp;
		}

		@Override
		public Collection<Cause> getCurrentCauses() {
			return theLock.getCurrentCauses();
		}

		@Override
		public Setter<T> lockWrite(boolean tryOnly, Object cause) {
			Transaction lock = theLock.lock(tryOnly);
			return lock == null ? null : new Setter.Unsettable<>(this, lock, StdMsg.UNSUPPORTED_OPERATION);
		}

		@Override
		public ObservableValue<String> isEnabled() {
			// We need a value to determine
			return SettableValue.ALWAYS_DISABLED;
		}

		@Override
		public String isAcceptable(T value) {
			return StdMsg.UNSUPPORTED_OPERATION;
		}

		@Override
		public T set(T value) throws IllegalArgumentException, UnsupportedOperationException {
			throw new UnsupportedOperationException(StdMsg.UNSUPPORTED_OPERATION);
		}

		SlowValueDef.SlowValue<T> copy(ModelSetInstance sourceModels, ModelSetInstance newModels, ExElementType.InstanceTypeData typeData,
			ErrorReporting reporting) throws ModelInstantiationException {
			SettableValue<T> newValue = ((ModelValueInstantiator<SettableValue<T>>) (Object) typeData.getInstantiator(VALUE))//
				.forModelCopy(theValue, sourceModels, newModels);
			Observable<?> newRefresh = ((ModelValueInstantiator<Observable<?>>) (Object) typeData.getInstantiator(REFRESH))//
				.forModelCopy(theRefresh, sourceModels, newModels);
			SettableValue<T> newWhileLoading = theWhileLoading == null ? null
				: ((ModelValueInstantiator<SettableValue<T>>) (Object) typeData.getInstantiator(WHILE_LOADING))//
				.forModelCopy(theWhileLoading, sourceModels, newModels);
			if (newValue != theValue || newRefresh != theRefresh || newWhileLoading != theWhileLoading) {
				SettableValue<Integer> newLoadId = SettableValue.create(0);
				ExWithElementModel.satisfyElementValue(typeData.getValueId(LOAD_ID), newModels, newLoadId, ActionIfSatisfied.Replace);
				SettableValue<T> newPreviousValue = thePreviousValue==null ? null : SettableValue.create();
				if(newPreviousValue!=null)
					ExWithElementModel.satisfyElementValue(typeData.getValueId(PREVIOUS_VALUE), newModels, newPreviousValue,
						ActionIfSatisfied.Replace);
				return new SlowValueDef.SlowValue<>(newLoadId, newPreviousValue, newValue, newRefresh, newWhileLoading,
					theLock.getThreadConstraint(), reporting);
			} else
				return this;
		}
	}
}