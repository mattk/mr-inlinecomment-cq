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

package com.atlassian.connector.eclipse.internal.crucible.ui;

import com.atlassian.connector.eclipse.internal.crucible.core.CrucibleConstants;
import com.atlassian.connector.eclipse.internal.crucible.core.client.CrucibleClient;
import com.atlassian.connector.eclipse.internal.crucible.core.client.model.IReviewCacheListener;
import com.atlassian.connector.eclipse.internal.crucible.ui.annotations.CrucibleAnnotationModelManager;
import com.atlassian.theplugin.commons.crucible.api.model.Review;
import com.atlassian.theplugin.commons.crucible.api.model.notification.CrucibleNotification;
import com.atlassian.theplugin.commons.util.MiscUtil;

import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.core.runtime.IStatus;
import org.eclipse.core.runtime.Status;
import org.eclipse.core.runtime.jobs.IJobChangeEvent;
import org.eclipse.core.runtime.jobs.Job;
import org.eclipse.core.runtime.jobs.JobChangeAdapter;
import org.eclipse.mylyn.commons.core.StatusHandler;
import org.eclipse.mylyn.internal.tasks.ui.util.TasksUiInternal;
import org.eclipse.mylyn.tasks.core.ITask;
import org.eclipse.mylyn.tasks.core.ITaskActivationListener;
import org.eclipse.mylyn.tasks.core.TaskRepository;
import org.eclipse.mylyn.tasks.core.sync.SynchronizationJob;

import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Class to manage the currently active review for other models
 * 
 * @author sminto
 */
public class ActiveReviewManager implements ITaskActivationListener, IReviewCacheListener {

	/**
	 * All methods are never called from UI thread.
	 * 
	 * @author pniewiadomski
	 */
	public interface IReviewActivationListener {
		void reviewActivated(ITask task, Review review);

		void reviewDeactivated(ITask task, Review review);

		void reviewUpdated(ITask task, Review review, Collection<CrucibleNotification> differences);
	};

	private static final long ACTIVE_REVIEW_POLLING_INTERVAL = 120000L;

	private final JobChangeAdapter refreshJobRescheduler = new JobChangeAdapter() {
		@Override
		public void done(IJobChangeEvent event) {
			synchronizeJob = null;
			createAndScheduleRefreshJob();
		}
	};

	private final List<IReviewActivationListener> activationListeners;

	private Review activeReview;

	private ITask activeTask;

	private SynchronizationJob synchronizeJob;

	private final boolean increasedRefresh;

	private final Map<ITask, Review> reviewByTask = new HashMap<ITask, Review>();

	public ActiveReviewManager(boolean increasedRefresh) {
		this.activationListeners = MiscUtil.buildArrayList();
		this.increasedRefresh = increasedRefresh;
	}

	public synchronized void addReviewActivationListener(IReviewActivationListener l) {
		activationListeners.add(l);
	}

	public synchronized void removeReviewActivationListener(IReviewActivationListener l) {
		activationListeners.remove(l);
	}

	private synchronized void fireReviewActivated(final ITask task, final Review review) {
		for (final IReviewActivationListener l : activationListeners) {
			l.reviewActivated(task, review);
		}
	}

	private synchronized void fireReviewDectivated(final ITask task, final Review review) {
		for (final IReviewActivationListener l : activationListeners) {
			l.reviewDeactivated(task, review);
		}
	}

	private synchronized void fireReviewUpdated(final ITask task, final Review review,
			Collection<CrucibleNotification> differences) {
		for (final IReviewActivationListener l : activationListeners) {
			l.reviewUpdated(task, review, differences);
		}
	}

	public void dispose() {
	}

	public synchronized void taskActivated(ITask task) {
		System.setProperty(CrucibleConstants.REVIEW_ACTIVE_SYSTEM_PROPERTY, "true");

		this.activeTask = task;
		this.activeReview = reviewByTask.get(task);
		if (activeReview != null) {
			scheduleDownloadJob(task);

			if (increasedRefresh) {
				startIncreasedChangePolling();
			}
			fireReviewActivated(task, activeReview);
		}
	}

	public synchronized void taskDeactivated(ITask task) {
		Review oldReview = this.activeReview;
		ITask oldTask = this.activeTask;

		this.activeTask = null;
		this.activeReview = null;
		System.setProperty(CrucibleConstants.REVIEW_ACTIVE_SYSTEM_PROPERTY, "false");
		stopIncreasedChangePolling();

		fireReviewDectivated(oldTask, oldReview);
	}

	public void preTaskActivated(ITask task) {
		// ignore
	}

	public synchronized void preTaskDeactivated(ITask task) {
		// ignore
	}

	private synchronized void activeReviewUpdated(Review cachedReview, ITask task,
			Collection<CrucibleNotification> differences) {
		if (activeTask != null && task != null && activeTask.equals(task)) {
			reviewByTask.put(task, cachedReview);
			if (activeReview == null) {
				this.activeReview = cachedReview;
				fireReviewActivated(activeTask, activeReview);
			} else {
				this.activeReview = cachedReview;
				CrucibleAnnotationModelManager.updateAllOpenEditors(activeReview);
				fireReviewUpdated(activeTask, activeReview, differences);
			}
		}
	}

	public synchronized Review getActiveReview() {
		return activeReview;
	}

	public synchronized ITask getActiveTask() {
		return activeTask;
	}

	private void startIncreasedChangePolling() {
		createAndScheduleRefreshJob();
	}

	private synchronized void createAndScheduleRefreshJob() {
		if (synchronizeJob == null && getActiveTask() != null) {
			Set<ITask> tasks = new HashSet<ITask>();
			tasks.add(getActiveTask());
//			synchronizeJob = TasksUiPlugin.getTaskJobFactory().createSynchronizeTasksJob(
//					CrucibleCorePlugin.getRepositoryConnector(), tasks);
//			synchronizeJob.setUser(false);
//			synchronizeJob.addJobChangeListener(refreshJobRescheduler);
//			synchronizeJob.schedule(ACTIVE_REVIEW_POLLING_INTERVAL);
		}
	}

	private void stopIncreasedChangePolling() {
		if (synchronizeJob != null) {
			synchronizeJob.removeJobChangeListener(refreshJobRescheduler);
			synchronizeJob.cancel();
			synchronizeJob = null;
		}
	}

	private void scheduleDownloadJob(final ITask task) {
		Job downloadJob = new Job("Retrieving review from server") {

			@Override
			protected IStatus run(IProgressMonitor monitor) {
				IStatus errorStatus = null;
				try {
					TaskRepository repository = CrucibleUiUtil.getCrucibleTaskRepository(task.getRepositoryUrl());

					if (repository != null) {
						String taskId = task.getTaskId();

						CrucibleClient client = CrucibleUiPlugin.getClient(repository);
						if (client != null) {
							// This should fire off a listener that we listen to to update the review properly
							client.getReview(repository, taskId, false, monitor);
						} else {
							errorStatus = new Status(IStatus.ERROR, CrucibleUiPlugin.PLUGIN_ID,
									"Unable to get crucible client for repository");

						}
					} else {
						errorStatus = new Status(IStatus.ERROR, CrucibleUiPlugin.PLUGIN_ID,
								"Crucible repository does not exist");

					}
				} catch (CoreException e) {
					errorStatus = e.getStatus();

				} finally {
					if (errorStatus != null && !errorStatus.isOK()) {
						StatusHandler.log(errorStatus);
						TasksUiInternal.asyncDisplayStatus("Unable to retrieve Review", errorStatus);
					}
				}
				return Status.OK_STATUS;
			}
		};
		downloadJob.setUser(true);
		downloadJob.setPriority(Job.INTERACTIVE);
		downloadJob.schedule();
	}

	public synchronized boolean isReviewActive() {
		return activeTask != null && activeReview != null;
	}

	public void reviewAdded(String repositoryUrl, String taskId, Review review) {
		if (activeTask != null) {
			if (activeTask.getRepositoryUrl().equals(repositoryUrl) && activeTask.getTaskId().equals(taskId)) {
				activeReviewUpdated(review, activeTask, Collections.<CrucibleNotification> emptyList());
			}
		}
	}

	public synchronized void reviewUpdated(String repositoryUrl, String taskId, Review review,
			List<CrucibleNotification> differences) {
		if (activeTask != null) {
			if (activeTask.getRepositoryUrl().equals(repositoryUrl) && activeTask.getTaskId().equals(taskId)) {
				activeReviewUpdated(review, activeTask, differences);
			}
		}
	}

	/**
	 * public for testing only!
	 * 
	 * @param review
	 * @param task
	 */
	public void setActiveReview(Review review, ITask task) {
		this.activeTask = task;
		this.activeReview = review;
	}

	public void activeReviewUpdated() {
		activeReviewUpdated(activeReview, activeTask, Collections.<CrucibleNotification> emptyList());
	}
}
