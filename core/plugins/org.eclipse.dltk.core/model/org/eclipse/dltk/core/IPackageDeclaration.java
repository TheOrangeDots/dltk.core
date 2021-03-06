/*******************************************************************************
 * Copyright (c) 2000, 2016 IBM Corporation and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 *******************************************************************************/
package org.eclipse.dltk.core;

/**
 * Represents a package declaration in source module.
 * <p>
 * This interface is not intended to be implemented by clients.
 * </p>
 */
public interface IPackageDeclaration extends IModelElement, ISourceReference {
	/**
	 * Returns the name of the package the statement refers to. This is a
	 * handle-only method.
	 * 
	 * @return the name of the package the statement
	 */
	@Override
	String getElementName();
}
