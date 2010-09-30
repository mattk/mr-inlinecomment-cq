/*******************************************************************************
 * Copyright (c) 2008 Atlassian and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *     Atlassian - initial API and implementation
 ******************************************************************************/

package com.atlassian.theplugin.commons.crucible.api.model;

import java.util.Date;
import java.util.Set;

/**
 * Contains almost all review data which can be transferred quite cheaply by Crucible for (e.g. while doing queries
 * returning many reviews).
 * 
 * The only thing which is not returned (as of Crucible 1.6.x) are files ({@link CrucibleFileInfo}).
 * 
 * @author wseliga
 */
public class BasicReview {
	private Set<Reviewer> reviewers;

	private User author;

	private User creator;

	private String description;

	private User moderator;

	private String name;

	/** this field seems to be not initialized by ACC at all */
	private PermId parentReview;

	private PermId permId;

	private String projectKey;

	private String repoName;

	private State state;

	private boolean allowReviewerToJoin;

	private int metricsVersion;

	private Date createDate;

	private Date closeDate;

	private Date dueDate;

	private String summary;

	private final String serverUrl;

	private final ReviewType type;

	public BasicReview(ReviewType type, String serverUrl, String projectKey, User author, User moderator) {
		this.type = type;
		this.serverUrl = serverUrl;
		this.projectKey = projectKey;
		this.author = author;
		this.moderator = moderator;
	}

	public ReviewType getType() {
		return type;
	}

	public void setReviewers(Set<Reviewer> reviewers) {
		this.reviewers = reviewers;
	}

	public void setAuthor(final User author) {
		this.author = author;
	}

	public String getServerUrl() {
		return serverUrl;
	}

	public Set<Reviewer> getReviewers() {
		return reviewers;
	}

	public boolean isCompleted() {

		for (Reviewer reviewer : reviewers) {
			if (!reviewer.isCompleted()) {
				return false;
			}
		}
		return true;
	}

	public User getAuthor() {
		return author;
	}

	public User getCreator() {
		return creator;
	}

	public void setCreator(User value) {
		this.creator = value;
	}

	public String getDescription() {
		return description;
	}

	public void setDescription(String value) {
		this.description = value;
	}

	public User getModerator() {
		return moderator;
	}

	public void setModerator(User value) {
		this.moderator = value;
	}

	public String getName() {
		return name;
	}

	public void setName(String value) {
		this.name = value;
	}

	public PermId getParentReview() {
		return parentReview;
	}

	public void setParentReview(PermId value) {
		this.parentReview = value;
	}

	public PermId getPermId() {
		return permId;
	}

	public void setPermId(PermId value) {
		this.permId = value;
	}

	public String getProjectKey() {
		return projectKey;
	}

	public void setProjectKey(String value) {
		this.projectKey = value;
	}

	public String getRepoName() {
		return repoName;
	}

	public void setRepoName(String value) {
		this.repoName = value;
	}

	public State getState() {
		return state;
	}

	public void setState(State value) {
		this.state = value;
	}

	public boolean isAllowReviewerToJoin() {
		return allowReviewerToJoin;
	}

	public void setAllowReviewerToJoin(boolean allowReviewerToJoin) {
		this.allowReviewerToJoin = allowReviewerToJoin;
	}

	public int getMetricsVersion() {
		return metricsVersion;
	}

	public void setMetricsVersion(int metricsVersion) {
		this.metricsVersion = metricsVersion;
	}

	public Date getCreateDate() {
		return createDate;
	}

	public void setCreateDate(Date createDate) {
		this.createDate = createDate;
	}

	public Date getCloseDate() {
		return closeDate;
	}

	public void setCloseDate(Date closeDate) {
		this.closeDate = closeDate;
	}

	@Override
	public boolean equals(Object o) {
		if (this == o) {
			return true;
		}
		if (o == null || getClass() != o.getClass()) {
			return false;
		}

		BasicReview that = (BasicReview) o;

		return !(permId != null ? !permId.equals(that.permId) : that.permId != null);
	}

	@Override
	public int hashCode() {
		int result;
		result = (permId != null ? permId.hashCode() : 0);
		return result;
	}

	public String getSummary() {
		return this.summary;
	}

	public void setSummary(String summary) {
		this.summary = summary;
	}

	public void setDueDate(Date dueDate) {
		this.dueDate = dueDate;
	}

	public Date getDueDate() {
		return dueDate;
	}

}