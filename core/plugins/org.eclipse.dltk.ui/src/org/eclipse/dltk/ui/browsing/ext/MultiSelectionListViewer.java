package org.eclipse.dltk.ui.browsing.ext;

import java.util.ArrayList;
import java.util.List;

import org.eclipse.jface.viewers.ILabelProvider;
import org.eclipse.jface.viewers.ISelection;
import org.eclipse.jface.viewers.ISelectionChangedListener;
import org.eclipse.jface.viewers.IStructuredSelection;
import org.eclipse.jface.viewers.ITreeContentProvider;
import org.eclipse.jface.viewers.SelectionChangedEvent;
import org.eclipse.jface.viewers.TreeViewer;
import org.eclipse.jface.viewers.Viewer;
import org.eclipse.swt.SWT;
import org.eclipse.swt.custom.ScrolledComposite;
import org.eclipse.swt.events.ControlEvent;
import org.eclipse.swt.events.ControlListener;
import org.eclipse.swt.graphics.Rectangle;
import org.eclipse.swt.layout.GridData;
import org.eclipse.swt.layout.GridLayout;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Tree;

public class MultiSelectionListViewer extends ScrolledComposite {
	// Contain ListViewers
	private List<TreeViewer> viewers = new ArrayList<TreeViewer>();
	private int elements = 0;
	private ColumnForm columnForm;
	private ITreeContentProvider contentProvider;
	private Object input;
	private ILabelProvider labelProvider;
	private int selectedView = 0;

	public class SelectionUpdater implements ISelectionChangedListener {
		int controlIndex = 0;

		public SelectionUpdater(int controlIndex) {
			this.controlIndex = controlIndex;
		}

		public void selectionChanged(SelectionChangedEvent event) {
			updateAll(this.controlIndex);
		}
	}

	private void updateAll(int controlIndex) {
		if (this.viewers.size() == 0) {
			addPane();
			return;
		}
		// we need to update all elements with bigger indexes.
		for (int i = controlIndex + 1; i < viewers.size(); i++) {
			TreeViewer viewer = viewers.get(i);
			if (!viewer.getControl().isDisposed()) {
				viewer.refresh(true);
			}
		}
		selectedView = controlIndex;
		// Add pane if required
		Object[] elementsFrom = getElementsFrom(controlIndex);
		boolean required = false;
		if (elementsFrom.length > 0) {
			required = true;
		}
		int disposeCount = 0;
		for (int i = viewers.size() - 1; i > controlIndex; i--) {
			elementsFrom = getElementsFrom(i);
			if (elementsFrom.length == 0) {
				disposeCount++;
			}
		}
		for (int j = 0; j < disposeCount; j++) {
			if (!(j == disposeCount - 1 && required)) {
				removePane();
			}
		}

		if ((controlIndex == viewers.size() - 1) && required) {
			addPane();
		}

		// lets scroll for corrent position
		TreeViewer viewer = viewers.get(controlIndex);
		Tree tree = viewer.getTree();
		if (!tree.isDisposed()) {
			Rectangle bounds = tree.getBounds();
//			Point origin = MultiSelectionListViewer.this.getOrigin();
//			double step = ( origin.x - ( bounds.x + bounds.width ) ) / 10;
//			for (int i = 0; i < 50; i++) {
//				MultiSelectionListViewer.this.setOrigin(origin.x - (int)step*i, bounds.y);
//				redraw();
//			}
		}
		elementSelectionChanged(viewer.getSelection());
	}

	private class TreeContentProvider implements ITreeContentProvider {
		private int controlIndex = 0;
		Object[] NONE = new Object[0];

		public TreeContentProvider(int index) {
			this.controlIndex = index;
		}

		public Object[] getElements(Object inputElement) {
			if (controlIndex == 0) {
				return contentProvider.getElements(input);
			} else {
				return getElementsFrom(this.controlIndex - 1);
			}
		}

		public void dispose() {
		}

		public void inputChanged(Viewer viewer, Object oldInput, Object newInput) {
		}

		public Object[] getChildren(Object parentElement) {
			return NONE;
		}

		public Object getParent(Object element) {
			return null;
		}

		public boolean hasChildren(Object element) {
			return false;
		}
	}

	public MultiSelectionListViewer(Composite parent, int style) {
		super(parent, style | SWT.H_SCROLL);
		setLayout(new GridLayout());
		columnForm = new ColumnForm(this, SWT.HORIZONTAL | SWT.SMOOTH);
		this.columnForm.setSize(400, 300);
		this.setContent(this.columnForm);
		this.setExpandVertical(true);
		this.addControlListener(new ControlListener() {
			public void controlMoved(ControlEvent e) {
			}

			public void controlResized(ControlEvent e) {
				columnForm.layout();
			}
		});
		layout();
		this.columnForm.layout();
	}

	/**
	 * After calling addPane child composite could be added
	 */
	private void addPane() {
		if (isDisposed()) {
			return;
		}
		Tree list = new Tree(columnForm, SWT.SINGLE);
		list.setLayoutData(new GridData(SWT.FILL, SWT.FILL, true, true));
		TreeViewer viewer = new TreeViewer(list);
		viewer.setContentProvider(new TreeContentProvider(elements));
		viewer.setLabelProvider(this.labelProvider);
		configureViewer(viewer);
		viewer.setInput(input);
		viewer.addSelectionChangedListener(new SelectionUpdater(elements));
		list.setSize(100, 100);
		list.layout();
		viewers.add(viewer);
		this.columnForm.layout();
		this.layout();
		elements += 1;
	}

	protected void configureViewer(TreeViewer viewer) {
	}

	private void removePane() {
		if (elements > 0 && !isDisposed()) {
			elements -= 1;
			TreeViewer last = viewers.get(viewers.size() - 1);
			if (!last.getControl().isDisposed()) {
				last.getControl().dispose();
			}
			viewers.remove(last);
			columnForm.layout();
			this.layout();
		}
	}

	public void setContentProvider(ITreeContentProvider provider) {
		this.contentProvider = provider;
	}

	public void setInput(Object input) {
		if (input != null && !input.equals(this.input)) {
			this.contentProvider.inputChanged(null, this.input, input);
		}
		this.input = input;
		int len = this.viewers.size();
		for (int i = 0; i < len; i++) {
			removePane();
		}
		// Remove all disposed widgets

		updateAll(0);
	}

	public void setLabelProvider(ILabelProvider labelProvider) {
		this.labelProvider = labelProvider;
	}

	private Object[] getElementsFrom(int controlIndex) {
		if (controlIndex < this.viewers.size()) {
			TreeViewer prevViewer = viewers.get(controlIndex);
			ISelection selection = prevViewer.getSelection();
			if (selection != null && selection instanceof IStructuredSelection) {
				IStructuredSelection sel = (IStructuredSelection) selection;
				Object firstElement = sel.getFirstElement();
				if (firstElement != null) {
					return contentProvider.getChildren(firstElement);
				}
			}
		} else {
			return contentProvider.getElements(this.input);
		}
		return new Object[0];
	}

	public ISelection getSelection() {
		if (this.viewers.size() > selectedView) {
			TreeViewer viewer = this.viewers.get(selectedView);
			return viewer.getSelection();
		}
		return null;
	}

	public void elementSelectionChanged(ISelection selection) {
	}

	public Object getInput() {
		return this.input;
	}

	public void refresh() {
		for (int i = 0; i < viewers.size(); i++) {
			TreeViewer viewer = viewers.get(i);
			if (!viewer.getControl().isDisposed()) {
				viewer.refresh(true);
			}
		}
	}
}
