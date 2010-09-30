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
import com.atlassian.theplugin.commons.crucible.api.model.Review;
import com.atlassian.theplugin.commons.util.MiscUtil;

import org.eclipse.core.runtime.jobs.IJobChangeEvent;
import org.eclipse.core.runtime.jobs.JobChangeAdapter;
import org.eclipse.mylyn.tasks.core.ITask;
import org.eclipse.mylyn.tasks.core.ITaskActivationListener;
import org.eclipse.mylyn.tasks.core.sync.SynchronizationJob;

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
public class ActiveReviewManager implements ITaskActivationListener {

	/**
	 * All methods are never called from UI thread.
	 * 
	 * @author pniewiadomski
	 */
	public interface IReviewActivationListener {
		void reviewActivated(ITask task, Review review);

		void reviewDeactivated(ITask task, Review review);

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
	}

	public synchronized boolean isReviewActive() {
		return activeTask != null && activeReview != null;
	}

	public void reviewAdded(String repositoryUrl, String taskId, Review review) {
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

}
