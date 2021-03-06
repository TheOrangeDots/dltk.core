/*******************************************************************************
 * Copyright (c) 2005, 2016 IBM Corporation and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 *******************************************************************************/
package org.eclipse.dltk.console.ui;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.Iterator;
import java.util.List;
import java.util.SortedSet;
import java.util.TreeSet;

import org.eclipse.jface.text.rules.FastPartitioner;
import org.eclipse.jface.text.rules.IPredicateRule;
import org.eclipse.jface.text.rules.IRule;
import org.eclipse.jface.text.rules.IToken;
import org.eclipse.jface.text.rules.MultiLineRule;
import org.eclipse.jface.text.rules.RuleBasedPartitionScanner;
import org.eclipse.jface.text.rules.Token;
import org.eclipse.swt.SWT;
import org.eclipse.swt.custom.StyleRange;
import org.eclipse.ui.console.IConsoleDocumentPartitioner;

public class ScriptConsolePartitioner extends FastPartitioner
		implements IConsoleDocumentPartitioner {

	private SortedSet<StyleRange> ranges = new TreeSet<StyleRange>(
			new Comparator<StyleRange>() {
				@Override
				public int compare(StyleRange sr1, StyleRange sr2) {
					int start = sr1.start - sr2.start;
					if (start == 0) {
						return sr1.length - sr2.length;
					}
					return start;
				}
			});

	private static class Constants {
		public static final String MY_DOUBLE_QUOTED = "__my_double"; //$NON-NLS-1$

		public static final String MY_SINGLE_QUOTED = "__my_single"; //$NON-NLS-1$
	}

	private static class MyPartitionScanner extends RuleBasedPartitionScanner {
		public MyPartitionScanner() {
			IToken myDouble = new Token(Constants.MY_DOUBLE_QUOTED);
			IToken mySingle = new Token(Constants.MY_SINGLE_QUOTED);

			List<IRule> rules = new ArrayList<IRule>();

			rules.add(new MultiLineRule("\'", "\'", mySingle, '\\')); //$NON-NLS-1$ //$NON-NLS-2$
			rules.add(new MultiLineRule("\"", "\"", myDouble, '\\')); //$NON-NLS-1$ //$NON-NLS-2$

			IPredicateRule[] result = new IPredicateRule[rules.size()];
			rules.toArray(result);

			setPredicateRules(result);
		}
	}

	public ScriptConsolePartitioner() {

		super(new MyPartitionScanner(), new String[] {
				Constants.MY_DOUBLE_QUOTED, Constants.MY_SINGLE_QUOTED });
	}

	public void addRange(StyleRange r) {
		ranges.add(r);
	}

	public void addRanges(StyleRange[] r) {
		ranges.addAll(Arrays.asList(r));
	}

	public void clearRanges() {
		ranges.clear();
	}

	@Override
	public StyleRange[] getStyleRanges(int offset, int length) {
		List<StyleRange> result = new ArrayList<StyleRange>();
		// get the sublist with length = 0 so that it will return all with that
		// offset.
		StyleRange sr = new StyleRange(offset, 0, null, null, SWT.NO);
		for (Iterator<StyleRange> iterator = ranges.tailSet(sr)
				.iterator(); iterator.hasNext();) {
			StyleRange r = iterator.next();
			if (r.start >= offset && r.start + r.length <= offset + length)
				result.add((StyleRange) r.clone());
			else
				break;
		}

		if (result.size() > 0)
			return result.toArray(new StyleRange[result.size()]);

		sr.length = length;
		return new StyleRange[] { sr };
	}

	@Override
	public boolean isReadOnly(int offset) {
		return false;
	}
}
