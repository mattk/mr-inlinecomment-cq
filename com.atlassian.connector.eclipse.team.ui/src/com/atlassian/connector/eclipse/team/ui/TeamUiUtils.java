/*******************************************************************************
 * Copyright (c) 2009 Atlassian and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *     Atlassian - initial API and implementation
 ******************************************************************************/

package com.atlassian.connector.eclipse.team.ui;

import com.atlassian.connector.eclipse.ui.commons.DecoratedResource;
import com.atlassian.theplugin.commons.util.MiscUtil;

import org.eclipse.compare.CompareEditorInput;
import org.eclipse.compare.CompareUI;
import org.eclipse.core.resources.IContainer;
import org.eclipse.core.resources.IProject;
import org.eclipse.core.resources.IResource;
import org.eclipse.core.resources.ResourcesPlugin;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.IPath;
import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.core.runtime.IStatus;
import org.eclipse.core.runtime.Path;
import org.eclipse.core.runtime.Status;
import org.eclipse.jface.dialogs.IDialogConstants;
import org.eclipse.jface.dialogs.MessageDialog;
import org.eclipse.mylyn.commons.core.StatusHandler;
import org.eclipse.mylyn.internal.provisional.commons.ui.WorkbenchUtil;
import org.eclipse.swt.widgets.Display;
import org.eclipse.ui.IWorkbench;
import org.eclipse.ui.IWorkbenchPage;
import org.eclipse.ui.PlatformUI;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collection;

/**
 * A utility class for doing UI related operations for team items
 * 
 * @author Shawn Minto
 */
@SuppressWarnings("restriction")
public final class TeamUiUtils {
	public static final String TEAM_PROV_ID_SVN_SUBVERSIVE = "org.eclipse.team.svn.core.svnnature";

	public static final String TEAM_PROVIDER_ID_CVS_ECLIPSE = "org.eclipse.team.cvs.core.cvsnature";

	public static final String TEAM_PROV_ID_SVN_SUBCLIPSE = "org.tigris.subversion.subclipse.core.svnnature";

	private static DefaultTeamUiResourceConnector defaultConnector = new DefaultTeamUiResourceConnector();

	private TeamUiUtils() {
	}

	public static DefaultTeamUiResourceConnector getDefaultConnector() {
		return defaultConnector;
	}

	public static boolean isInSync(IResource resource, String revision) {
		LocalStatus status;
		try {
			status = getLocalRevision(resource);
			return (status != null && !status.isDirty() && (status.getRevision().equals(revision) || status.getLastChangedRevision()
					.equals(revision)));
		} catch (CoreException e) {
			return false;
		}
	}

	public static Collection<String> getSupportedTeamConnectors() {
		Collection<String> res = MiscUtil.buildArrayList();
		TeamUiResourceManager teamResourceManager = AtlassianTeamUiPlugin.getDefault().getTeamResourceManager();
		for (ITeamUiResourceConnector connector : teamResourceManager.getTeamConnectors()) {
			if (connector.isEnabled()) {
				res.add(connector.getName());
			}
		}
		res.add(defaultConnector.getName());
		return res;
	}

	public static boolean hasNoTeamConnectors() {
		return AtlassianTeamUiPlugin.getDefault().getTeamResourceManager().getTeamConnectors().size() == 0;
	}

	public static boolean checkTeamConnectors() {
		if (hasNoTeamConnectors()) {
			handleMissingTeamConnectors();
			return false;
		}
		return true;
	}

	@Nullable
	public static IResource findResourceForPath(String repoUrl, String filePath, IProgressMonitor monitor) {
		IPath path = new Path(filePath);
		final IResource resource = ResourcesPlugin.getWorkspace().getRoot().findMember(path);
		if (resource == null && repoUrl != null) {
			for (IProject project : ResourcesPlugin.getWorkspace().getRoot().getProjects()) {
				final ScmRepository scmRepo = getApplicableRepository(project);
				if (scmRepo != null) {
					if (repoUrl.startsWith(scmRepo.getRootPath())) {
						final IResource member = findResourceForPath2(project, filePath);
						if (member != null) {
							return member;
						}
					}
				}
			}
		}

		if (resource == null) {
			return findResourceForPath2(ResourcesPlugin.getWorkspace().getRoot(), filePath);
		}
		return resource;
	}

	public static String findMatching(String projectScmPath, String filePath) {
		return null;
	}

	private static IResource findResourceForPath2(IContainer location, String filePath) {
		if (filePath == null || filePath.length() <= 0) {
			return null;
		}

		IPath path = new Path(filePath);
		IResource resource = null;
		while (!path.isEmpty() && resource == null) {
			resource = match(location, path);
			path = path.removeFirstSegments(1);
		}
		return resource;
	}

	private static IResource match(IContainer location, IPath path) {
		if (!path.isEmpty()) {
			return location.findMember(path);
		}
		return null;
	}

	@Nullable
	public static ScmRepository getApplicableRepository(@NotNull IResource resource) {
		TeamUiResourceManager teamResourceManager = AtlassianTeamUiPlugin.getDefault().getTeamResourceManager();

		for (ITeamUiResourceConnector connector : teamResourceManager.getTeamConnectors()) {
			if (connector.isEnabled()) {
				try {
					ScmRepository res = connector.getApplicableRepository(resource);
					if (res != null) {
						return res;
					}
				} catch (CoreException e) {
					StatusHandler.log(new Status(IStatus.WARNING, AtlassianTeamUiPlugin.PLUGIN_ID, e.getMessage(), e));
					// and try the next connector
				}
			}
		}
		return null;

	}

	public static LocalStatus getLocalRevision(@NotNull IResource resource) throws CoreException {
		ITeamUiResourceConnector connector = AtlassianTeamUiPlugin.getDefault()
				.getTeamResourceManager()
				.getTeamConnector(resource);

		if (connector != null && connector.isEnabled()) {
			LocalStatus res = connector.getLocalRevision(resource);
			if (res != null) {
				return res;
			}
		}
		return null;
	}

	public static void openCompareEditorForInput(final CompareEditorInput compareEditorInput) {
		if (Display.getCurrent() != null) {
			internalOpenCompareEditorForInput(compareEditorInput);
		} else {
			PlatformUI.getWorkbench().getDisplay().asyncExec(new Runnable() {
				public void run() {
					internalOpenCompareEditorForInput(compareEditorInput);
				}
			});
		}
	}

	private static void internalOpenCompareEditorForInput(CompareEditorInput compareEditorInput) {
		IWorkbench workbench = AtlassianTeamUiPlugin.getDefault().getWorkbench();
		IWorkbenchPage page = workbench.getActiveWorkbenchWindow().getActivePage();
		CompareUI.openCompareEditorOnPage(compareEditorInput, page);
	}

	public static void handleMissingTeamConnectors() {
		Display.getDefault().syncExec(new Runnable() {
			public void run() {
				new MessageDialog(
						WorkbenchUtil.getShell(),
						"No Atlassian SCM Integration installed",
						null,
						"In order to access this functionality you need to install an Atlassian SCM Integration feature.\n\n"
								+ "You may install them by opening: Help | Install New Software, selecting 'Atlassian Connector for Eclipse' Update Site "
								+ "and chosing one or more integation features in 'Atlassian Integrations' category.",
						MessageDialog.INFORMATION, new String[] { IDialogConstants.OK_LABEL }, 0).open();
			}
		});
	}

	/**
	 * @param monitor
	 *            progress monitor
	 * @return all supported repositories configured in current workspace
	 */
	@NotNull
	public static Collection<ScmRepository> getRepositories(IProgressMonitor monitor) {
		TeamUiResourceManager teamResourceManager = AtlassianTeamUiPlugin.getDefault().getTeamResourceManager();
		Collection<ScmRepository> res = MiscUtil.buildArrayList();

		for (ITeamUiResourceConnector connector : teamResourceManager.getTeamConnectors()) {
			if (connector.isEnabled()) {
				res.addAll(connector.getRepositories(monitor));
			}
		}
		res.addAll(defaultConnector.getRepositories(monitor));
		return res;

	}

	public static DecoratedResource getDecoratedResource(IResource resource, ITeamUiResourceConnector connector) {

		final String PRE_COMMIT_EXPLANATION = " and will be added to the review in pre-commit mode.";

		if (connector.isResourceAcceptedByFilter(resource, ITeamUiResourceConnector.State.SF_UNVERSIONED)) {
			return new DecoratedResource(resource, false, "pre-commit", "This file is unversioned"
					+ PRE_COMMIT_EXPLANATION);
		} else if (connector.isResourceAcceptedByFilter(resource, ITeamUiResourceConnector.State.SF_IGNORED)) {
			return new DecoratedResource(resource, false, "pre-commit", "This file is ignored in version control"
					+ PRE_COMMIT_EXPLANATION);
		} else if (connector.isResourceAcceptedByFilter(resource, ITeamUiResourceConnector.State.SF_ANY_CHANGE)) {
			return new DecoratedResource(resource, false, "pre-commit", "This file has been added or changed locally"
					+ PRE_COMMIT_EXPLANATION);
		} else if (connector.isResourceAcceptedByFilter(resource, ITeamUiResourceConnector.State.SF_VERSIONED)) {
			return new DecoratedResource(resource, true, "", "This file is up to date.");
		} else {
			// ignore the resource
		}

		return null;
	}

	public static String getScmPath(IResource resource, ITeamUiResourceConnector connector) {
		if (connector.isResourceAcceptedByFilter(resource, ITeamUiResourceConnector.State.SF_VERSIONED)
				&& !connector.isResourceAcceptedByFilter(resource, ITeamUiResourceConnector.State.SF_ANY_CHANGE)) {
			try {
				LocalStatus status = connector.getLocalRevision(resource);
				if (status.getScmPath() != null && status.getScmPath().length() > 0) {
					LocalStatus projectStatus = connector.getLocalRevision(resource.getProject());
					return projectStatus != null && projectStatus.getScmPath() != null ? projectStatus.getScmPath()
							: status.getScmPath();
				}
			} catch (CoreException e) {
				// resource is probably not under version control
				// skip
			}
		}

		return null;
	}

	public static ITeamUiResourceConnector getTeamConnector(IResource resource) {
		return AtlassianTeamUiPlugin.getDefault().getTeamResourceManager().getTeamConnector(resource);
	}

}
