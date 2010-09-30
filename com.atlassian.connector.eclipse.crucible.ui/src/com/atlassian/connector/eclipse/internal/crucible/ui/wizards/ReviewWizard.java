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

package com.atlassian.connector.eclipse.internal.crucible.ui.wizards;

import com.atlassian.connector.eclipse.internal.crucible.ui.CrucibleUiPlugin;
import com.atlassian.connector.eclipse.internal.crucible.ui.CrucibleUiUtil;
import com.atlassian.connector.eclipse.team.ui.ITeamUiResourceConnector;
import com.atlassian.connector.eclipse.ui.commons.DecoratedResource;
import com.atlassian.theplugin.commons.crucible.api.model.BasicProject;
import com.atlassian.theplugin.commons.crucible.api.model.PermId;
import com.atlassian.theplugin.commons.crucible.api.model.Review;

import org.eclipse.core.resources.IResource;
import org.eclipse.core.resources.ResourcesPlugin;
import org.eclipse.jface.wizard.IWizardPage;
import org.eclipse.jface.wizard.WizardPage;
import org.eclipse.mylyn.internal.tasks.core.LocalTask;
import org.eclipse.mylyn.internal.tasks.ui.util.TasksUiInternal;
import org.eclipse.mylyn.tasks.core.TaskRepository;
import org.eclipse.mylyn.tasks.ui.TasksUi;
import org.eclipse.mylyn.tasks.ui.TasksUiUtil;
import org.eclipse.mylyn.tasks.ui.wizards.NewTaskWizard;
import org.eclipse.ui.INewWizard;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Wizard for creating a new review
 * 
 * @author Thomas Ehrnhoefer
 * @author Pawel Niewiadomski
 * @author Jacek Jaroczynski
 */
@SuppressWarnings("restriction")
public class ReviewWizard extends NewTaskWizard implements INewWizard {

	public enum Type {
		ADD_CHANGESET, ADD_PATCH, ADD_WORKSPACE_PATCH, ADD_SCM_RESOURCES, ADD_UPLOAD_ITEMS, ADD_RESOURCES, ADD_COMMENT_TO_FILE;
	}

	private CrucibleReviewDetailsPage detailsPage;

	private Review crucibleReview;

	private ResourceSelectionPage resourceSelectionPage;

	private final Set<Type> types;

	private final List<IResource> selectedWorkspaceResources = new ArrayList<IResource>();

	private ITeamUiResourceConnector selectedWorkspaceTeamConnector;

	public ReviewWizard(TaskRepository taskRepository, Set<Type> types) {
		super(taskRepository, null);
		setWindowTitle("New Crucible Review");
		setNeedsProgressMonitor(true);
		this.types = types;
		this.selectedWorkspaceResources.addAll(Arrays.asList((IResource[]) ResourcesPlugin.getWorkspace()
				.getRoot()
				.getProjects()));
	}

	public ReviewWizard(Review review, Set<Type> types) {
		this(CrucibleUiUtil.getCrucibleTaskRepository(review), types);
		this.crucibleReview = review;
	}

	public ReviewWizard(Review review, Type type) {
		this(review, new HashSet<Type>(Arrays.asList(type)));
	}

	@Override
	public void addPages() {
		// mixed review
		if (types.contains(Type.ADD_RESOURCES)) {
			resourceSelectionPage = new ResourceSelectionPage(getTaskRepository(), selectedWorkspaceTeamConnector,
					selectedWorkspaceResources);
			addPage(resourceSelectionPage);
		}

		// only add details page if review is not already existing
		if (crucibleReview == null) {
			detailsPage = new CrucibleReviewDetailsPage(getTaskRepository(), types.contains(Type.ADD_COMMENT_TO_FILE));
			addPage(detailsPage);
		}
	}

	@Override
	public boolean canFinish() {
		if (detailsPage != null) {
			return detailsPage.isPageComplete();
		}
		return super.canFinish();
	}

	@Override
	public boolean performFinish() {

		setErrorMessage(null);

		crucibleReview = detailsPage.getReview();
		LocalTask task = TasksUiInternal.createNewLocalTask("Review: " + crucibleReview.getSummary());
		crucibleReview.setPermId(new PermId(task.getTaskId()));

		if (detailsPage != null) {
			// save project selection
			final BasicProject selectedProject = detailsPage.getSelectedProject();
			CrucibleUiPlugin.getDefault().updateLastSelectedProject(getTaskRepository(),
					selectedProject != null ? selectedProject.getKey() : null);

			// save checkbox selections
			CrucibleUiPlugin.getDefault().updateAllowAnyoneOption(getTaskRepository(),
					detailsPage.isAllowAnyoneToJoin());
			CrucibleUiPlugin.getDefault().updateStartReviewOption(getTaskRepository(),
					detailsPage.isStartReviewImmediately());
		}

		if (resourceSelectionPage != null && types.contains(Type.ADD_RESOURCES)) {
			final List<DecoratedResource> resources = resourceSelectionPage.getSelection();
			if (resources != null && resources.size() > 0) {
				// create review from workbench selection (post- and pre-commit)
			}
		}

		TasksUiUtil.openTask(task);
		TasksUi.getTaskActivityManager().activateTask(task);
		CrucibleUiPlugin.getDefault()
				.getActiveReviewManager()
				.reviewAdded(task.getRepositoryUrl(), task.getTaskId(), crucibleReview);

		return true;
	}

	private void setErrorMessage(String message) {
		IWizardPage page = getContainer().getCurrentPage();
		if (page instanceof WizardPage) {
			((WizardPage) page).setErrorMessage(message != null ? message.replace("\n", " ") : null);
		}
	}

	public void setRoots(ITeamUiResourceConnector teamConnector, List<IResource> list) {
		this.selectedWorkspaceResources.clear();
		this.selectedWorkspaceResources.addAll(list);
		this.selectedWorkspaceTeamConnector = teamConnector;
	}

}