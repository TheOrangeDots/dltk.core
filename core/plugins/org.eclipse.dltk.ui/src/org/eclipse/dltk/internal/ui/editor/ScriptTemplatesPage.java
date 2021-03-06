/*******************************************************************************
 * Copyright (c) 2007, 2016 Dakshinamurthy Karra, IBM Corporation and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *     Dakshinamurthy Karra (Jalian Systems) - Templates View - https://bugs.eclipse.org/bugs/show_bug.cgi?id=69581
 *******************************************************************************/
package org.eclipse.dltk.internal.ui.editor;

import java.util.HashSet;
import java.util.Iterator;
import java.util.Set;

import org.eclipse.dltk.core.ISourceModule;
import org.eclipse.dltk.internal.ui.preferences.ScriptSourcePreviewerUpdater;
import org.eclipse.dltk.ui.DLTKPluginImages;
import org.eclipse.dltk.ui.DLTKUILanguageManager;
import org.eclipse.dltk.ui.IDLTKUILanguageToolkit;
import org.eclipse.dltk.ui.preferences.EditTemplateDialog;
import org.eclipse.dltk.ui.templates.ScriptTemplateContextType;
import org.eclipse.dltk.ui.text.ScriptSourceViewerConfiguration;
import org.eclipse.dltk.ui.text.ScriptTextTools;
import org.eclipse.dltk.ui.text.templates.ITemplateAccess;
import org.eclipse.dltk.ui.text.templates.TemplateVariableProcessor;
import org.eclipse.jface.preference.IPreferenceStore;
import org.eclipse.jface.resource.JFaceResources;
import org.eclipse.jface.text.BadLocationException;
import org.eclipse.jface.text.Document;
import org.eclipse.jface.text.IDocument;
import org.eclipse.jface.text.ITextSelection;
import org.eclipse.jface.text.ITextViewerExtension;
import org.eclipse.jface.text.Position;
import org.eclipse.jface.text.Region;
import org.eclipse.jface.text.TextSelection;
import org.eclipse.jface.text.source.ISourceViewer;
import org.eclipse.jface.text.source.SourceViewer;
import org.eclipse.jface.text.templates.ContextTypeRegistry;
import org.eclipse.jface.text.templates.DocumentTemplateContext;
import org.eclipse.jface.text.templates.Template;
import org.eclipse.jface.text.templates.TemplateContextType;
import org.eclipse.jface.text.templates.TemplateProposal;
import org.eclipse.jface.text.templates.persistence.TemplateStore;
import org.eclipse.jface.window.Window;
import org.eclipse.swt.SWT;
import org.eclipse.swt.graphics.Font;
import org.eclipse.swt.graphics.Image;
import org.eclipse.swt.layout.GridData;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Control;
import org.eclipse.ui.texteditor.templates.AbstractTemplatesPage;

/**
 * The templates page for the Script editor.
 * 
 * @since 3.0
 */
public class ScriptTemplatesPage extends AbstractTemplatesPage {

	private final TemplateVariableProcessor fTemplateProcessor;
	private final ScriptEditor fScriptEditor;
	private final ITemplateAccess fTemplateAccess;

	/**
	 * Create a new AbstractTemplatesPage for the JavaEditor
	 * 
	 * @param scriptEditor
	 *            the java editor
	 */
	public ScriptTemplatesPage(ScriptEditor scriptEditor,
			ITemplateAccess templateAccess) {
		super(scriptEditor, scriptEditor.getViewer());
		fScriptEditor = scriptEditor;
		fTemplateProcessor = new TemplateVariableProcessor();
		fTemplateAccess = templateAccess;
	}

	protected void insertTemplate(Template template, IDocument document) {
		if (!fScriptEditor.validateEditorInputState())
			return;

		ISourceViewer contextViewer = fScriptEditor.getViewer();
		ITextSelection textSelection = (ITextSelection) contextViewer
				.getSelectionProvider().getSelection();
		if (!isValidTemplate(document, template, textSelection.getOffset(),
				textSelection.getLength()))
			return;
		beginCompoundChange(contextViewer);
		/*
		 * The Editor checks whether a completion for a word exists before it
		 * allows for the template to be applied. We pickup the current text at
		 * the selection position and replace it with the first char of the
		 * template name for this to succeed. Another advantage by this method
		 * is that the template replaces the selected text provided the
		 * selection by itself is not used in the template pattern.
		 */
		String savedText;
		try {
			savedText = document.get(textSelection.getOffset(),
					textSelection.getLength());
			if (savedText.length() == 0) {
				String prefix = getIdentifierPart(document, template,
						textSelection.getOffset(), textSelection.getLength());
				if (prefix.length() > 0
						&& !template.getName().startsWith(prefix.toString())) {
					return;
				}
				if (prefix.length() > 0) {
					contextViewer.setSelectedRange(textSelection.getOffset()
							- prefix.length(), prefix.length());
					textSelection = (ITextSelection) contextViewer
							.getSelectionProvider().getSelection();
				}
			}
			document.replace(textSelection.getOffset(),
					textSelection.getLength(),
					template.getName().substring(0, 1));
		} catch (BadLocationException e) {
			endCompoundChange(contextViewer);
			return;
		}
		Position position = new Position(textSelection.getOffset() + 1, 0);
		Region region = new Region(textSelection.getOffset() + 1, 0);
		contextViewer.getSelectionProvider().setSelection(
				new TextSelection(textSelection.getOffset(), 1));
		ISourceModule compilationUnit = EditorUtility
				.getEditorInputModelElement(fScriptEditor, true);

		TemplateContextType type = getContextTypeRegistry().getContextType(
				template.getContextTypeId());
		DocumentTemplateContext context = ((ScriptTemplateContextType) type)
				.createContext(document, position, compilationUnit);
		context.setVariable("selection", savedText); //$NON-NLS-1$
		if (context.getKey().length() == 0) {
			try {
				document.replace(textSelection.getOffset(), 1, savedText);
			} catch (BadLocationException e) {
				endCompoundChange(contextViewer);
				return;
			}
		}
		TemplateProposal proposal = new TemplateProposal(template, context,
				region, null);
		fScriptEditor.getSite().getPage().activate(fScriptEditor);
		proposal.apply(fScriptEditor.getViewer(), ' ', 0, region.getOffset());
		endCompoundChange(contextViewer);
	}

	protected ContextTypeRegistry getContextTypeRegistry() {
		return fTemplateAccess.getContextTypeRegistry();
	}

	protected IPreferenceStore getTemplatePreferenceStore() {
		return fTemplateAccess.getTemplatePreferenceStore();
	}

	public TemplateStore getTemplateStore() {
		return fTemplateAccess.getTemplateStore();
	}

	protected boolean isValidTemplate(IDocument document, Template template,
			int offset, int length) {
		String[] contextIds = getContextTypeIds(document, offset);
		for (int i = 0; i < contextIds.length; i++) {
			if (contextIds[i].equals(template.getContextTypeId())) {
				DocumentTemplateContext context = getContext(document,
						template, offset, length);
				return context.canEvaluate(template)
						|| isTemplateAllowed(context, template);
			}
		}
		return false;
	}

	protected SourceViewer createPatternViewer(Composite parent) {
		IDocument document = new Document();
		ScriptTextTools tools = fScriptEditor.getTextTools();
		tools.setupDocumentPartitioner(document);
		IPreferenceStore store = uiToolkit().getCombinedPreferenceStore();
		ScriptSourceViewer viewer = new ScriptSourceViewer(parent, null, null,
				false, SWT.V_SCROLL | SWT.H_SCROLL, store);

		ScriptSourceViewerConfiguration configuration = uiToolkit()
				.createSourceViewerConfiguration();
		viewer.configure(configuration);
		viewer.setEditable(false);
		viewer.setDocument(document);

		Font font = JFaceResources.getFont(fScriptEditor.getSymbolicFontName());
		viewer.getTextWidget().setFont(font);
		new ScriptSourcePreviewerUpdater(viewer, configuration, store);

		Control control = viewer.getControl();
		GridData data = new GridData(GridData.HORIZONTAL_ALIGN_FILL
				| GridData.FILL_VERTICAL);
		control.setLayoutData(data);

		viewer.setEditable(false);
		return viewer;
	}

	protected Image getImage(Template template) {
		return DLTKPluginImages.get(DLTKPluginImages.IMG_OBJS_TEMPLATE);
	}

	protected Template editTemplate(Template template, boolean edit,
			boolean isNameModifiable) {
		EditTemplateDialog dialog = new EditTemplateDialog(uiToolkit(),
				getSite().getShell(), template, edit, isNameModifiable, true,
				getContextTypeRegistry());
		if (dialog.open() == Window.OK)
			return dialog.getTemplate();
		return null;
	}

	protected IDLTKUILanguageToolkit uiToolkit() {
		return DLTKUILanguageManager.getLanguageToolkit(fScriptEditor
				.getLanguageToolkit());
	}

	protected void updatePatternViewer(Template template) {
		if (template == null) {
			getPatternViewer().getDocument().set(""); //$NON-NLS-1$
			return;
		}
		String contextId = template.getContextTypeId();
		TemplateContextType type = getContextTypeRegistry().getContextType(
				contextId);
		fTemplateProcessor.setContextType(type);

		IDocument doc = getPatternViewer().getDocument();

		String start = null;
		if ("javadoc".equals(contextId)) { //$NON-NLS-1$
			start = "/**" + doc.getLegalLineDelimiters()[0]; //$NON-NLS-1$
		} else
			start = ""; //$NON-NLS-1$

		doc.set(start + template.getPattern());
		int startLen = start.length();
		getPatternViewer().setDocument(doc, startLen,
				doc.getLength() - startLen);
	}

	protected String getPreferencePageId() {
		return uiToolkit().getEditorTemplatesPreferencePageId();
	}

	/**
	 * Undomanager - end compound change
	 * 
	 * @param viewer
	 *            the viewer
	 */
	private void endCompoundChange(ISourceViewer viewer) {
		if (viewer instanceof ITextViewerExtension)
			((ITextViewerExtension) viewer).getRewriteTarget()
					.endCompoundChange();
	}

	/**
	 * Undomanager - begin a compound change
	 * 
	 * @param viewer
	 *            the viewer
	 */
	private void beginCompoundChange(ISourceViewer viewer) {
		if (viewer instanceof ITextViewerExtension)
			((ITextViewerExtension) viewer).getRewriteTarget()
					.beginCompoundChange();
	}

	/**
	 * Check whether the template is allowed even though the context can't
	 * evaluate it. This is needed because the Dropping of a template is more
	 * lenient than Ctrl-space invoked code assist.
	 * 
	 * @param context
	 *            the template context
	 * @param template
	 *            the template
	 * @return true if the template is allowed
	 */
	private boolean isTemplateAllowed(DocumentTemplateContext context,
			Template template) {
		int offset;
		try {
			// if (template.getContextTypeId().equals(JavaDocContextType.ID)) {
			// return (offset = context.getCompletionOffset()) > 0
			// && Character.isWhitespace(context.getDocument()
			// .getChar(offset - 1));
			// } else {
			return ((offset = context.getCompletionOffset()) > 0 && !isTemplateNamePart(context
					.getDocument().getChar(offset - 1)));
			// }
		} catch (BadLocationException e) {
		}
		return false;
	}

	/**
	 * Checks whether the character is a valid character in Java template names
	 * 
	 * @param ch
	 *            the character
	 * @return <code>true</code> if the character is part of a template name
	 */
	private boolean isTemplateNamePart(char ch) {
		return !Character.isWhitespace(ch) && ch != '(' && ch != ')'
				&& ch != '{' && ch != '}' && ch != ';';
	}

	/**
	 * Get context
	 * 
	 * @param document
	 *            the document
	 * @param template
	 *            the template
	 * @param offset
	 *            the offset
	 * @param length
	 *            the length
	 * @return the context
	 */
	private DocumentTemplateContext getContext(IDocument document,
			Template template, final int offset, int length) {
		final ScriptTemplateContextType contextType = (ScriptTemplateContextType) getContextTypeRegistry()
				.getContextType(template.getContextTypeId());
		final ISourceModule module = EditorUtility
				.getEditorInputModelElement(fScriptEditor, true);
		return contextType.createContext(document, offset, length, module);
	}

	/**
	 * Get the active contexts for the given position in the document.
	 * <p>
	 * FIXME: should trigger code assist to get the context.
	 * </p>
	 * 
	 * @param document
	 *            the document
	 * @param offset
	 *            the offset
	 * @return an array of valid context id
	 */
	protected String[] getContextTypeIds(IDocument document, int offset) {
		final Set<String> ids = new HashSet<String>();
		@SuppressWarnings("unchecked")
		final Iterator<TemplateContextType> i = getContextTypeRegistry()
				.contextTypes();
		while (i.hasNext()) {
			ids.add(i.next().getId());
		}
		return ids.toArray(new String[ids.size()]);
		// try {
		// String partition = TextUtilities.getContentType(document,
		// IJavaPartitions.JAVA_PARTITIONING, offset, true);
		// String[] ids = new String[] { JavaContextType.ID_ALL,
		// JavaContextType.ID_MEMBERS, JavaContextType.ID_STATEMENTS,
		// SWTContextType.ID_ALL, SWTContextType.ID_STATEMENTS,
		// SWTContextType.ID_MEMBERS };
		// if (partition.equals(IJavaPartitions.JAVA_DOC))
		// ids = new String[] { JavaDocContextType.ID };
		// return ids;
		// } catch (BadLocationException e) {
		// return new String[0];
		// }
	}

	/**
	 * Get the Java identifier terminated at the given offset
	 * 
	 * @param document
	 *            the document
	 * @param template
	 *            the template
	 * @param offset
	 *            the offset
	 * @param length
	 *            the length
	 * @return the identifier part the Java identifier
	 */
	private String getIdentifierPart(IDocument document, Template template,
			int offset, int length) {
		return getContext(document, template, offset, length).getKey();
	}
}
