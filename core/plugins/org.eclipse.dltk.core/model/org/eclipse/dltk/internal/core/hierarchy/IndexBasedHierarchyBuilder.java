/*******************************************************************************
 * Copyright (c) 2000, 2016 IBM Corporation and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 *******************************************************************************/
package org.eclipse.dltk.internal.core.hierarchy;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Map;

import org.eclipse.core.resources.IFile;
import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.core.runtime.SubProgressMonitor;
import org.eclipse.dltk.compiler.CharOperation;
import org.eclipse.dltk.compiler.util.HashtableOfObject;
import org.eclipse.dltk.compiler.util.HashtableOfObjectToInt;
import org.eclipse.dltk.core.IModelElement;
import org.eclipse.dltk.core.IProjectFragment;
import org.eclipse.dltk.core.IScriptProject;
import org.eclipse.dltk.core.ISearchableEnvironment;
import org.eclipse.dltk.core.ISourceModule;
import org.eclipse.dltk.core.IType;
import org.eclipse.dltk.core.ModelException;
import org.eclipse.dltk.core.search.DLTKSearchParticipant;
import org.eclipse.dltk.core.search.IDLTKSearchConstants;
import org.eclipse.dltk.core.search.IDLTKSearchScope;
import org.eclipse.dltk.core.search.SearchParticipant;
import org.eclipse.dltk.core.search.SearchPattern;
import org.eclipse.dltk.core.search.indexing.IIndexConstants;
import org.eclipse.dltk.core.search.indexing.IndexManager;
import org.eclipse.dltk.core.search.matching.MatchLocator;
import org.eclipse.dltk.internal.compiler.env.AccessRuleSet;
import org.eclipse.dltk.internal.core.IPathRequestor;
import org.eclipse.dltk.internal.core.Member;
import org.eclipse.dltk.internal.core.ModelManager;
import org.eclipse.dltk.internal.core.Openable;
import org.eclipse.dltk.internal.core.ScriptProject;
import org.eclipse.dltk.internal.core.search.IndexQueryRequestor;
import org.eclipse.dltk.internal.core.search.SubTypeSearchJob;
import org.eclipse.dltk.internal.core.search.matching.SuperTypeReferencePattern;
import org.eclipse.dltk.internal.core.util.HandleFactory;

public class IndexBasedHierarchyBuilder extends HierarchyBuilder {
	public static final int MAXTICKS = 800; // heuristic so that there still
											// progress for deep hierachies
	/**
	 * A temporary cache of compilation units to handles to speed info to handle
	 * translation - it only contains the entries for the types in the region
	 * (in other words, it contains no supertypes outside the region).
	 */
	protected Map cuToHandle;

	/**
	 * The scope this hierarchy builder should restrain results to.
	 */
	protected IDLTKSearchScope scope;

	/**
	 * Cache used to record binaries recreated from index matches
	 */
	protected Map binariesFromIndexMatches;

	/**
	 * Collection used to queue subtype index queries
	 */
	static class Queue {
		public char[][] names = new char[10][];
		public int start = 0;
		public int end = -1;

		public void add(char[] name) {
			if (++this.end == this.names.length) {
				this.end -= this.start;
				System.arraycopy(this.names, this.start,
						this.names = new char[this.end * 2][], 0, this.end);
				this.start = 0;
			}
			this.names[this.end] = name;
		}

		public char[] retrieve() {
			if (this.start > this.end) {
				return null; // none
			}

			char[] name = this.names[this.start++];
			if (this.start > this.end) {
				this.start = 0;
				this.end = -1;
			}
			return name;
		}

		@Override
		public String toString() {
			StringBuffer buffer = new StringBuffer("Queue:\n"); //$NON-NLS-1$
			for (int i = this.start; i <= this.end; i++) {
				buffer.append(this.names[i]).append('\n');
			}
			return buffer.toString();
		}
	}

	public IndexBasedHierarchyBuilder(TypeHierarchy hierarchy,
			IDLTKSearchScope scope) throws ModelException {
		super();
		setRequestor(hierarchy);
		this.cuToHandle = new HashMap(5);
		this.binariesFromIndexMatches = new HashMap(10);
		this.scope = scope;
	}

	@Override
	public void build(boolean computeSubtypes) {
		ModelManager manager = ModelManager.getModelManager();
		try {
			// optimize access to zip files while building hierarchy
			manager.cacheZipFiles();

			if (computeSubtypes) {
				// Note by construction there always is a focus type here
				IType focusType = getType();
				boolean focusIsObject = focusType.getElementName().equals(
						new String(IIndexConstants.OBJECT));
				int amountOfWorkForSubtypes = focusIsObject ? 5 : 80; // percentage
																		// of
																		// work
																		// needed
																		// to
																		// get
																		// possible
																		// subtypes
				IProgressMonitor possibleSubtypesMonitor = this.hierarchy.progressMonitor == null ? null
						: new SubProgressMonitor(
								this.hierarchy.progressMonitor,
								amountOfWorkForSubtypes);
				HashSet localTypes = new HashSet(10); // contains the paths
														// that have potential
														// subtypes that are
														// local/anonymous types
				String[] allPossibleSubtypes;
				if (((Member) focusType).getOuterMostLocalContext() == null) {
					// top level or member type
					allPossibleSubtypes = this.determinePossibleSubTypes(
							localTypes, possibleSubtypesMonitor);
				} else {
					// local or anonymous type
					allPossibleSubtypes = CharOperation.NO_STRINGS;
				}
				if (allPossibleSubtypes != null) {
					IProgressMonitor buildMonitor = this.hierarchy.progressMonitor == null ? null
							: new SubProgressMonitor(
									this.hierarchy.progressMonitor,
									100 - amountOfWorkForSubtypes);
					this.hierarchy.initialize(allPossibleSubtypes.length);
					buildFromPotentialSubtypes(allPossibleSubtypes, localTypes,
							buildMonitor);
				}
			} else {
				this.hierarchy.initialize(1);
				this.buildSupertypes();
			}
		} finally {
			manager.flushZipFiles();
		}
	}

	private void buildForProject(ScriptProject project,
			ArrayList potentialSubtypes,
			org.eclipse.dltk.core.ISourceModule[] workingCopies,
			HashSet localTypes, IProgressMonitor monitor) throws ModelException {
		// resolve
		int openablesLength = potentialSubtypes.size();
		if (openablesLength > 0) {
			// copy vectors into arrays
			Openable[] openables = new Openable[openablesLength];
			potentialSubtypes.toArray(openables);

			// sort in the order of roots and in reverse alphabetical order for
			// .class file
			// since requesting top level types in the process of caching an
			// enclosing type is
			// not supported by the lookup environment
			IProjectFragment[] roots = project.getProjectFragments();
			int rootsLength = roots.length;
			final HashtableOfObjectToInt indexes = new HashtableOfObjectToInt(
					openablesLength);
			for (int i = 0; i < openablesLength; i++) {
				IModelElement root = openables[i]
						.getAncestor(IModelElement.PROJECT_FRAGMENT);
				int index;
				for (index = 0; index < rootsLength; index++) {
					if (roots[index].equals(root)) {
						break;
					}
				}
				indexes.put(openables[i], index);
			}
			Arrays.sort(openables, new Comparator() {
				@Override
				public int compare(Object a, Object b) {
					int aIndex = indexes.get(a);
					int bIndex = indexes.get(b);
					if (aIndex != bIndex) {
						return aIndex - bIndex;
					}
					return ((Openable) b).getElementName().compareTo(
							((Openable) a).getElementName());
				}
			});

			IType focusType = this.getType();
			boolean inProjectOfFocusType = focusType != null
					&& focusType.getScriptProject().equals(project);
			org.eclipse.dltk.core.ISourceModule[] unitsToLookInside = null;
			if (inProjectOfFocusType) {
				org.eclipse.dltk.core.ISourceModule unitToLookInside = focusType
						.getSourceModule();
				if (unitToLookInside != null) {
					int wcLength = workingCopies == null ? 0
							: workingCopies.length;
					if (wcLength == 0) {
						unitsToLookInside = new org.eclipse.dltk.core.ISourceModule[] { unitToLookInside };
					} else {
						unitsToLookInside = new org.eclipse.dltk.core.ISourceModule[wcLength + 1];
						unitsToLookInside[0] = unitToLookInside;
						System.arraycopy(workingCopies, 0, unitsToLookInside,
								1, wcLength);
					}
				} else {
					unitsToLookInside = workingCopies;
				}
			}

			ISearchableEnvironment searchableEnvironment = project
					.newSearchableNameEnvironment(unitsToLookInside);
			this.nameLookup = searchableEnvironment.getNameLookup();
//			Map options = project.getOptions(true);
			// disable task tags to speed up parsing
			// options.put(DLTKCore.COMPILER_TASK_TAGS, ""); //$NON-NLS-1$
			this.hierarchyResolver = new HierarchyResolver(this);
				//	searchableEnvironment, options, this,
					//new DefaultProblemFactory());

			if (focusType != null) {
				Member declaringMember = ((Member) focusType)
						.getOuterMostLocalContext();
				if (declaringMember == null) {
					// top level or member type
//					if (!inProjectOfFocusType) {
//						char[] typeQualifiedName = focusType
//								.getTypeQualifiedName('.').toCharArray();
//						String[] packageName = ((PackageFragment) focusType
//								.getPackageFragment()).names;
//						if (searchableEnvironment.findType(typeQualifiedName,
//								Util.toCharArrays(packageName)) == null) {
//							// focus type is not visible in this project: no
//							// need to go further
//							return;
//						}
//					}
				} else {
					// local or anonymous type
					Openable openable;
					openable = (Openable) declaringMember.getSourceModule();
					localTypes = new HashSet();
					localTypes.add(openable.getPath().toString());
					this.hierarchyResolver.resolve(new Openable[] { openable }, localTypes);
					return;
				}
			}
			this.hierarchyResolver.resolve(openables, localTypes);
		}
	}

	/**
	 * Configure this type hierarchy based on the given potential subtypes.
	 */
	private void buildFromPotentialSubtypes(String[] allPotentialSubTypes,
			HashSet localTypes, IProgressMonitor monitor) {
		IType focusType = this.getType();

		// substitute compilation units with working copies
		HashMap wcPaths = new HashMap(); // a map from path to working copies
		int wcLength;
		org.eclipse.dltk.core.ISourceModule[] workingCopies = this.hierarchy.workingCopies;
		if (workingCopies != null && (wcLength = workingCopies.length) > 0) {
			String[] newPaths = new String[wcLength];
			for (int i = 0; i < wcLength; i++) {
				org.eclipse.dltk.core.ISourceModule workingCopy = workingCopies[i];
				String path = workingCopy.getPath().toString();
				wcPaths.put(path, workingCopy);
				newPaths[i] = path;
			}
			int potentialSubtypesLength = allPotentialSubTypes.length;
			System.arraycopy(allPotentialSubTypes, 0,
					allPotentialSubTypes = new String[potentialSubtypesLength
							+ wcLength], 0, potentialSubtypesLength);
			System.arraycopy(newPaths, 0, allPotentialSubTypes,
					potentialSubtypesLength, wcLength);
		}

		int length = allPotentialSubTypes.length;

		// inject the compilation unit of the focus type (so that types in
		// this cu have special visibility permission (this is also usefull
		// when the cu is a working copy)
		Openable focusCU = (Openable) focusType.getSourceModule();
		String focusPath = null;
		if (focusCU != null) {
			focusPath = focusCU.getPath().toString();
			if (length > 0) {
				System.arraycopy(allPotentialSubTypes, 0,
						allPotentialSubTypes = new String[length + 1], 0,
						length);
				allPotentialSubTypes[length] = focusPath;
			} else {
				allPotentialSubTypes = new String[] { focusPath };
			}
			length++;
		}

		/*
		 * Sort in alphabetical order so that potential subtypes are grouped per
		 * project
		 */
		Arrays.sort(allPotentialSubTypes);

		ArrayList potentialSubtypes = new ArrayList();
		try {
			// create element infos for subtypes
			HandleFactory factory = new HandleFactory();
			IScriptProject currentProject = null;
			if (monitor != null) {
				monitor.beginTask("", length * 2); //$NON-NLS-1$ // 1 for build binding, 1 for connect hierarchy
			}
			for (int i = 0; i < length; i++) {
				try {
					String resourcePath = allPotentialSubTypes[i];

					// skip duplicate paths (e.g. if focus path was injected
					// when it was already a potential subtype)
					if (i > 0
							&& resourcePath.equals(allPotentialSubTypes[i - 1])) {
						continue;
					}

					Openable handle;
					org.eclipse.dltk.core.ISourceModule workingCopy = (org.eclipse.dltk.core.ISourceModule) wcPaths
							.get(resourcePath);
					if (workingCopy != null) {
						handle = (Openable) workingCopy;
					} else {
						handle = resourcePath.equals(focusPath) ? focusCU
								: factory.createOpenable(resourcePath,
										this.scope);
						if (handle == null) {
							continue; // match is outside classpath
						}
					}

					IScriptProject project = handle.getScriptProject();
					if (currentProject == null) {
						currentProject = project;
						potentialSubtypes = new ArrayList(5);
					} else if (!currentProject.equals(project)) {
						// build current project
						this.buildForProject((ScriptProject) currentProject,
								potentialSubtypes, workingCopies, localTypes,
								monitor);
						currentProject = project;
						potentialSubtypes = new ArrayList(5);
					}

					potentialSubtypes.add(handle);
				} catch (ModelException e) {
					continue;
				}
			}

			// build last project
			try {
				if (currentProject == null) {
					// case of no potential subtypes
					currentProject = focusType.getScriptProject();
					potentialSubtypes.add(focusType.getSourceModule());
				}
				this.buildForProject((ScriptProject) currentProject,
						potentialSubtypes, workingCopies, localTypes, monitor);
			} catch (ModelException e) {
				// ignore
			}

			// Compute hierarchy of focus type if not already done (case of a
			// type with potential subtypes that are not real subtypes)
			if (!this.hierarchy.contains(focusType)) {
				try {
					currentProject = focusType.getScriptProject();
					potentialSubtypes = new ArrayList();
					potentialSubtypes.add(focusType.getSourceModule());

					this.buildForProject((ScriptProject) currentProject,
							potentialSubtypes, workingCopies, localTypes,
							monitor);
				} catch (ModelException e) {
					// ignore
				}
			}

			// Add focus if not already in (case of a type with no explicit
			// super type)
//			if (!this.hierarchy.contains(focusType)) {
//				this.hierarchy.addRootClass(focusType);
//			}
		} finally {
			if (monitor != null) {
				monitor.done();
			}
		}
	}

	@Override
	protected ISourceModule createCompilationUnitFromPath(Openable handle,
			IFile file) {
		ISourceModule unit = super.createCompilationUnitFromPath(handle,
				file);
		this.cuToHandle.put(unit, handle);
		return unit;
	}

	/**
	 * Returns all of the possible subtypes of this type hierarchy. Returns null
	 * if they could not be determine.
	 */
	protected String[] determinePossibleSubTypes(final HashSet localTypes,
			IProgressMonitor monitor) {

		class PathCollector implements IPathRequestor {
			HashSet paths = new HashSet(10);

			@Override
			public void acceptPath(String path, boolean containsLocalTypes) {
				this.paths.add(path);
				if (containsLocalTypes) {
					localTypes.add(path);
				}
			}
		}
		PathCollector collector = new PathCollector();

		try {
			if (monitor != null) {
				monitor.beginTask("", MAXTICKS); //$NON-NLS-1$
			}
			searchAllPossibleSubTypes(this.getType(), this.scope,
					this.binariesFromIndexMatches, collector,
					IDLTKSearchConstants.WAIT_UNTIL_READY_TO_SEARCH, monitor);
		} finally {
			if (monitor != null) {
				monitor.done();
			}
		}

		HashSet paths = collector.paths;
		int length = paths.size();
		String[] result = new String[length];
		int count = 0;
		for (Iterator iter = paths.iterator(); iter.hasNext();) {
			result[count++] = (String) iter.next();
		}
		return result;
	}

	/**
	 * Find the set of candidate subtypes of a given type.
	 *
	 * The requestor is notified of super type references (with actual path of
	 * its occurrence) for all types which are potentially involved inside a
	 * particular hierarchy. The match locator is not used here to narrow down
	 * the results, the type hierarchy resolver is rather used to compute the
	 * whole hierarchy at once.
	 *
	 * @param type
	 * @param scope
	 * @param binariesFromIndexMatches
	 * @param pathRequestor
	 * @param waitingPolicy
	 * @param progressMonitor
	 */
	public static void searchAllPossibleSubTypes(IType type,
			IDLTKSearchScope scope, final Map binariesFromIndexMatches,
			final IPathRequestor pathRequestor, int waitingPolicy, // WaitUntilReadyToSearch
																	// |
																	// ForceImmediateSearch
																	// |
																	// CancelIfNotReadyToSearch
			IProgressMonitor progressMonitor) {

		/*
		 * embed constructs inside arrays so as to pass them to (inner)
		 * collector
		 */
		final Queue queue = new Queue();
		final HashtableOfObject foundSuperNames = new HashtableOfObject(5);

		IndexManager indexManager = ModelManager.getModelManager()
				.getIndexManager();

		/* use a special collector to collect paths and queue new subtype names */
		IndexQueryRequestor searchRequestor = new IndexQueryRequestor() {
			@Override
			public boolean acceptIndexMatch(String documentPath,
					SearchPattern indexRecord, SearchParticipant participant,
					AccessRuleSet access) {
				SuperTypeReferencePattern record = (SuperTypeReferencePattern) indexRecord;
				boolean isLocalOrAnonymous = record.enclosingTypeName == IIndexConstants.ONE_ZERO;
				pathRequestor.acceptPath(documentPath, isLocalOrAnonymous);
				char[] typeName = record.simpleName;

				if (!isLocalOrAnonymous // local or anonymous types cannot have
										// subtypes outside the cu that define
										// them
						&& !foundSuperNames.containsKey(typeName)) {
					foundSuperNames.put(typeName, typeName);
					queue.add(typeName);
				}
				return true;
			}
		};

		int superRefKind;
		superRefKind = SuperTypeReferencePattern.ALL_SUPER_TYPES;
		SuperTypeReferencePattern pattern = new SuperTypeReferencePattern(null,
				null, superRefKind, SearchPattern.R_EXACT_MATCH
						| SearchPattern.R_CASE_SENSITIVE, scope.getLanguageToolkit());
		MatchLocator.setFocus(pattern, type);
		SubTypeSearchJob job = new SubTypeSearchJob(pattern,
				new DLTKSearchParticipant(), // java search only
				scope, searchRequestor);

		int ticks = 0;
		queue.add(type.getElementName().toCharArray());
		try {
			while (queue.start <= queue.end) {
				if (progressMonitor != null && progressMonitor.isCanceled()) {
					return;
				}

				// all subclasses of OBJECT are actually all types
				char[] currentTypeName = queue.retrieve();
				if (CharOperation.equals(currentTypeName,
						IIndexConstants.OBJECT)) {
					currentTypeName = null;
				}

				// search all index references to a given supertype
				pattern.superSimpleName = currentTypeName;
				indexManager.performConcurrentJob(job, waitingPolicy, null); // no
																				// sub
																				// progress
																				// monitor
																				// since
																				// its
																				// too
																				// costly
																				// for
																				// deep
																				// hierarchies
				if (progressMonitor != null && ++ticks <= MAXTICKS) {
					progressMonitor.worked(1);
				}

				// in case, we search all subtypes, no need to search further
				if (currentTypeName == null) {
					break;
				}
			}
		} finally {
			job.finished();
		}
	}
}
