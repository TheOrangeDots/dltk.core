/*******************************************************************************
 * Copyright (c) 2000, 2016 IBM Corporation and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 *******************************************************************************/
package org.eclipse.dltk.internal.ui.wizards.buildpath.newsourcepage;

import java.lang.reflect.InvocationTargetException;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

import org.eclipse.core.resources.IResource;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.core.runtime.IStatus;
import org.eclipse.core.runtime.NullProgressMonitor;
import org.eclipse.core.runtime.SubProgressMonitor;
import org.eclipse.dltk.core.DLTKCore;
import org.eclipse.dltk.core.IProjectFragment;
import org.eclipse.dltk.core.IScriptProject;
import org.eclipse.dltk.core.ModelException;
import org.eclipse.dltk.internal.corext.buildpath.BuildpathModifier;
import org.eclipse.dltk.internal.ui.wizards.NewWizardMessages;
import org.eclipse.dltk.internal.ui.wizards.buildpath.BPListElement;
import org.eclipse.dltk.ui.DLTKPluginImages;
import org.eclipse.dltk.ui.DLTKUIPlugin;
import org.eclipse.jface.action.Action;
import org.eclipse.jface.dialogs.ErrorDialog;
import org.eclipse.jface.dialogs.MessageDialog;
import org.eclipse.jface.operation.IRunnableWithProgress;
import org.eclipse.jface.viewers.ISelection;
import org.eclipse.jface.viewers.ISelectionChangedListener;
import org.eclipse.jface.viewers.IStructuredSelection;
import org.eclipse.jface.viewers.SelectionChangedEvent;
import org.eclipse.jface.viewers.StructuredSelection;
import org.eclipse.swt.widgets.Shell;
import org.eclipse.ui.IWorkbenchPage;
import org.eclipse.ui.IWorkbenchPart;
import org.eclipse.ui.IWorkbenchPartReference;
import org.eclipse.ui.IWorkbenchSite;
import org.eclipse.ui.PlatformUI;
import org.eclipse.ui.part.ISetSelectionTarget;


public class IncludeToBuildpathAction extends Action implements ISelectionChangedListener {

	private final IWorkbenchSite fSite;
	private final List fSelectedElements; //IResources

	public IncludeToBuildpathAction(IWorkbenchSite site) {
		super(NewWizardMessages.NewSourceContainerWorkbookPage_ToolBar_Unexclude_label, DLTKPluginImages.DESC_ELCL_INCLUDE_ON_BUILDPATH);
		setToolTipText(NewWizardMessages.NewSourceContainerWorkbookPage_ToolBar_Unexclude_tooltip);
		setDisabledImageDescriptor(DLTKPluginImages.DESC_DLCL_INCLUDE_ON_BUILDPATH);
		fSite= site;
		fSelectedElements= new ArrayList();
	}

	/**
	 * {@inheritDoc}
	 */
	public void run() {
		IResource resource= (IResource)fSelectedElements.get(0);
		final IScriptProject project= DLTKCore.create(resource.getProject());

		try {
			final IRunnableWithProgress runnable= new IRunnableWithProgress() {
				public void run(IProgressMonitor monitor) throws InvocationTargetException, InterruptedException {
					try {
						List result= unExclude(fSelectedElements, project, monitor);
						selectAndReveal(new StructuredSelection(result));
					} catch (CoreException e) {
						throw new InvocationTargetException(e);
					}
				}
			};
			PlatformUI.getWorkbench().getProgressService().run(true, false, runnable);
		} catch (final InvocationTargetException e) {
			if (e.getCause() instanceof CoreException) {
				showExceptionDialog((CoreException)e.getCause());
			} else {
				DLTKUIPlugin.log(e);
			}
		} catch (final InterruptedException e) {
		}
	}

	protected List unExclude(List elements, IScriptProject project, IProgressMonitor monitor) throws ModelException {
		if (monitor == null)
			monitor= new NullProgressMonitor();
		try {
			monitor.beginTask(NewWizardMessages.BuildpathModifier_Monitor_Including, 2 * elements.size()); 

			List entries= BuildpathModifier.getExistingEntries(project);
			for (int i= 0; i < elements.size(); i++) {
				IResource resource= (IResource) elements.get(i);
				IProjectFragment root= BuildpathModifier.getFragmentRoot(resource, project, new SubProgressMonitor(monitor, 1));
				if (root != null) {
					BPListElement entry= BuildpathModifier.getBuildpathEntry(entries, root);
					BuildpathModifier.unExclude(resource, entry, project, new SubProgressMonitor(monitor, 1));
				}
			}

			BuildpathModifier.commitBuildPath(entries, project, new SubProgressMonitor(monitor, 4));
			List resultElements= BuildpathModifier.getCorrespondingElements(elements, project);
			return resultElements;
		} finally {
			monitor.done();
		}
	}

	/**
	 * {@inheritDoc}
	 */
	public void selectionChanged(final SelectionChangedEvent event) {
		final ISelection selection = event.getSelection();
		if (selection instanceof IStructuredSelection) {
			setEnabled(canHandle((IStructuredSelection) selection));
		} else {
			setEnabled(canHandle(StructuredSelection.EMPTY));
		}
	}

	private boolean canHandle(IStructuredSelection elements) {
		if (elements.size() == 0)
			return false;

		try {
			fSelectedElements.clear();
			for (Iterator iter= elements.iterator(); iter.hasNext();) {
				Object element= iter.next();
				fSelectedElements.add(element);
				if (element instanceof IResource) {
					IResource resource= (IResource)element;
					IScriptProject project= DLTKCore.create(resource.getProject());
					if (project == null || !project.exists())
						return false;

					if (!BuildpathModifier.isExcluded(resource, project))
						return false;
				} else {
					return false;
				}
			}
			return true;
		} catch (CoreException e) {
		}
		return false;
	}

	private void showExceptionDialog(CoreException exception) {
		showError(exception, fSite.getShell(), NewWizardMessages.IncludeToBuildpathAction_ErrorTitle, exception.getMessage());
	}

	private void showError(CoreException e, Shell shell, String title, String message) {
		IStatus status= e.getStatus();
		if (status != null) {
			ErrorDialog.openError(shell, message, title, status);
		} else {
			MessageDialog.openError(shell, title, message);
		}
	}
	
	private void selectAndReveal(final ISelection selection) {
		// validate the input
		IWorkbenchPage page= fSite.getPage();
		if (page == null)
			return;

		// get all the view and editor parts
		List<IWorkbenchPart> parts = new ArrayList<IWorkbenchPart>();
		IWorkbenchPartReference refs[]= page.getViewReferences();
		for (int i= 0; i < refs.length; i++) {
			IWorkbenchPart part= refs[i].getPart(false);
			if (part != null)
				parts.add(part);
		}
		refs= page.getEditorReferences();
		for (int i= 0; i < refs.length; i++) {
			if (refs[i].getPart(false) != null)
				parts.add(refs[i].getPart(false));
		}

		for (IWorkbenchPart part : parts) {

			// get the part's ISetSelectionTarget implementation
			ISetSelectionTarget target= null;
			if (part instanceof ISetSelectionTarget)
				target= (ISetSelectionTarget) part;
			else
				target = part.getAdapter(ISetSelectionTarget.class);

			if (target != null) {
				// select and reveal resource
				final ISetSelectionTarget finalTarget= target;
				page.getWorkbenchWindow().getShell().getDisplay().asyncExec(new Runnable() {
					public void run() {
						finalTarget.selectReveal(selection);
					}
				});
			}
		}
	}

}
