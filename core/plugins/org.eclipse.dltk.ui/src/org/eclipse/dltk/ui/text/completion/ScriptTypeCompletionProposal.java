/*******************************************************************************
 * Copyright (c) 2000, 2007 IBM Corporation and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 
 *******************************************************************************/
package org.eclipse.dltk.ui.text.completion;

import org.eclipse.core.runtime.CoreException;
import org.eclipse.dltk.core.ISourceModule;
import org.eclipse.dltk.ui.DLTKUIPlugin;
import org.eclipse.jface.text.BadLocationException;
import org.eclipse.jface.text.IDocument;
import org.eclipse.jface.viewers.StyledString;
import org.eclipse.swt.graphics.Image;


/**
 * If passed compilation unit is not null, the replacement string will be seen as a qualified type name.
  */
public class ScriptTypeCompletionProposal extends ScriptCompletionProposal {

	protected final ISourceModule fSourceModule;

	/** The unqualified type name. */
	private final String fUnqualifiedTypeName;
	
	/** The fully qualified type name. */
	private final String fFullyQualifiedTypeName;

	public ScriptTypeCompletionProposal(String replacementString, ISourceModule cu, int replacementOffset, int replacementLength, Image image, String displayString, int relevance) {
		this(replacementString, cu, replacementOffset, replacementLength, image, displayString, relevance, null);
	}

	public ScriptTypeCompletionProposal(String replacementString,
			ISourceModule cu, int replacementOffset, int replacementLength,
			Image image, String displayString, int relevance,
			String fullyQualifiedTypeName) {
		this(replacementString, cu, replacementOffset, replacementLength, image,
				new StyledString(displayString), relevance, null);
	}

	/**
	 * @since 5.5
	 */
	public ScriptTypeCompletionProposal(String replacementString,
			ISourceModule cu, int replacementOffset, int replacementLength,
			Image image, StyledString displayString, int relevance,
		String fullyQualifiedTypeName)
	{
		super(replacementString, replacementOffset, replacementLength, image,
				displayString, relevance, false);
		fSourceModule= cu;
		fFullyQualifiedTypeName= fullyQualifiedTypeName;
		fUnqualifiedTypeName = null;
	}

	protected boolean updateReplacementString(IDocument document, char trigger, int offset) throws CoreException, BadLocationException {
		return false;
	}


	@Override
	public void apply(IDocument document, char trigger, int offset) {
		try {

			boolean importAdded= updateReplacementString(document, trigger, offset);

			if (importAdded)
				setCursorPosition(getReplacementString().length());

			super.apply(document, trigger, offset);

		} catch (CoreException e) {
			DLTKUIPlugin.log(e);
		} catch (BadLocationException e) {
			DLTKUIPlugin.log(e);
		}
	}
	
	@Override
	protected boolean isValidPrefix(String prefix) {
		return super.isValidPrefix(prefix) || isPrefix(prefix, fUnqualifiedTypeName) || isPrefix(prefix, fFullyQualifiedTypeName);
	}

	@Override
	public CharSequence getPrefixCompletionText(IDocument document, int completionOffset) {
		return fUnqualifiedTypeName;
	}

	@Override
	protected boolean isSmartTrigger(char trigger) {		
		return false;
	}
}
