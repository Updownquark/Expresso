package org.observe.expresso.logging;

import java.util.Collections;
import java.util.Set;

import org.observe.expresso.qonfig.ExpressoQIS;
import org.qommons.Version;
import org.qommons.config.QonfigInterpretation;
import org.qommons.config.QonfigInterpreterCore;
import org.qommons.config.QonfigToolkit;
import org.qommons.config.SpecialSession;

public class ExpressoLoggingV0_1 implements QonfigInterpretation {
	/** The name of the expresso logging toolkit */
	public static final String NAME = "Expresso-Logging";

	/** The version of this implementation of the expresso logging toolkit */
	public static final Version VERSION = new Version(0, 1, 0);

	/** {@link #NAME} and {@link #VERSION} combined */
	public static final String BASE = "Expresso-Logging 0.1";

	@Override
	public Set<Class<? extends SpecialSession<?>>> getExpectedAPIs() {
		return Collections.singleton(ExpressoQIS.class);
	}

	@Override
	public String getToolkitName() {
		return NAME;
	}

	@Override
	public Version getVersion() {
		return VERSION;
	}

	@Override
	public void init(QonfigToolkit toolkit) {
		// Not needed
	}

	@Override
	public QonfigInterpreterCore.Builder configureInterpreter(QonfigInterpreterCore.Builder interpreter) {
		int todo; // TODO
		return interpreter;
	}
}
