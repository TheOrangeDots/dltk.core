package org.eclipse.dltk.internal.launching.execution;

import org.eclipse.core.runtime.IAdapterFactory;
import org.eclipse.dltk.core.environment.IExecutionEnvironment;
import org.eclipse.dltk.core.internal.environment.LocalEnvironment;

public class LocalExecEnvironmentAdapter implements IAdapterFactory {
	public static final Class<?>[] ADAPTER_LIST = {
			IExecutionEnvironment.class };
	private IExecutionEnvironment localEnvironment = new LocalExecEnvironment();

	@SuppressWarnings("unchecked")
	@Override
	public <T> T getAdapter(Object adaptableObject, Class<T> adapterType) {
		if (adapterType == IExecutionEnvironment.class
				&& adaptableObject instanceof LocalEnvironment) {
			return (T) localEnvironment;
		}
		return null;
	}

	@Override
	public Class<?>[] getAdapterList() {
		return ADAPTER_LIST;
	}

}
