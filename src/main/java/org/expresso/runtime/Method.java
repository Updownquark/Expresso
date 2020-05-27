package org.expresso.runtime;

import java.util.List;

import org.qommons.Named;

public interface Method<E extends ExpressoEnvironment<E>, R> extends Named {
	Result<R> invoke(E env, List<? extends Result<?>> parameters);
}
