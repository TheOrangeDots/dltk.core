/*******************************************************************************
 * Copyright (c) 2009, 2016 xored software, Inc.  
 *
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html  
 *
 * Contributors:
 *     xored software, Inc. - initial API and Implementation (Alex Panchenko)
 *******************************************************************************/
package org.eclipse.dltk.internal.core;

import org.eclipse.dltk.core.IDLTKAssociationManager;

public class NopAssociationManager implements IDLTKAssociationManager {

	@Override
	public boolean isAssociatedWith(String name) {
		return false;
	}

}
