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

package com.atlassian.connector.eclipse.internal.crucible.ui.dialogs;

import com.atlassian.connector.commons.misc.IntRange;
import com.atlassian.connector.commons.misc.IntRanges;
import com.atlassian.connector.eclipse.internal.crucible.core.client.CrucibleClient;
import com.atlassian.connector.eclipse.internal.crucible.ui.CrucibleUiPlugin;
import com.atlassian.connector.eclipse.internal.crucible.ui.CrucibleUiUtil;
import com.atlassian.connector.eclipse.team.ui.CrucibleFile;
import com.atlassian.theplugin.commons.crucible.api.model.Comment;
import com.atlassian.theplugin.commons.crucible.api.model.Comment.ReadState;
import com.atlassian.theplugin.commons.crucible.api.model.CrucibleFileInfo;
import com.atlassian.theplugin.commons.crucible.api.model.CustomFieldBean;
import com.atlassian.theplugin.commons.crucible.api.model.CustomFieldDef;
import com.atlassian.theplugin.commons.crucible.api.model.CustomFieldValue;
import com.atlassian.theplugin.commons.crucible.api.model.Review;
import com.atlassian.theplugin.commons.crucible.api.model.VersionedComment;

import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.core.runtime.IStatus;
import org.eclipse.core.runtime.Status;
import org.eclipse.jface.dialogs.IDialogConstants;
import org.eclipse.jface.layout.GridDataFactory;
import org.eclipse.jface.operation.IRunnableWithProgress;
import org.eclipse.jface.resource.JFaceResources;
import org.eclipse.jface.text.ITextListener;
import org.eclipse.jface.text.TextEvent;
import org.eclipse.jface.text.source.LineRange;
import org.eclipse.jface.viewers.ArrayContentProvider;
import org.eclipse.jface.viewers.ComboViewer;
import org.eclipse.jface.viewers.LabelProvider;
import org.eclipse.jface.window.Window;
import org.eclipse.mylyn.commons.core.StatusHandler;
import org.eclipse.mylyn.tasks.core.TaskRepository;
import org.eclipse.swt.SWT;
import org.eclipse.swt.events.DisposeEvent;
import org.eclipse.swt.events.DisposeListener;
import org.eclipse.swt.events.SelectionAdapter;
import org.eclipse.swt.events.SelectionEvent;
import org.eclipse.swt.layout.GridLayout;
import org.eclipse.swt.widgets.Button;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Control;
import org.eclipse.swt.widgets.Label;
import org.eclipse.swt.widgets.Shell;
import org.eclipse.ui.forms.widgets.FormToolkit;

import java.lang.reflect.InvocationTargetException;
import java.util.HashMap;
import java.util.Map;

/**
 * Dialog shown to the user when they add a comment to a review
 * 
 * @author Thomas Ehrnhoefer
 * @author Shawn Minto
 */
public class CrucibleAddCommentDialog extends AbstractCrucibleCommentDialog {

	public class AddCommentRunnable implements IRunnableWithProgress {

		public void run(IProgressMonitor monitor) throws InvocationTargetException, InterruptedException {
			monitor.beginTask("Adding comment", IProgressMonitor.UNKNOWN);
			if (newComment.length() > 0) {
				CrucibleFileInfo crucibleFileInfo = crucibleFile.getCrucibleFileInfo();
				VersionedComment comment = new VersionedComment(getReview(), crucibleFileInfo);
				comment.setMessage(newComment);
				comment.setAuthor(CrucibleUiUtil.getCachedUsers(getReview()).iterator().next());
				comment.setReadState(ReadState.UNREAD);
				Map<String, IntRanges> lineRanges = new HashMap<String, IntRanges>();
				lineRanges.put("1.1",
						new IntRanges(new IntRange(commentLines.getStartLine(), commentLines.getStartLine()
								+ commentLines.getNumberOfLines())));
				comment.setLineRanges(lineRanges);
				crucibleFile.getCrucibleFileInfo().addComment(comment);
				CrucibleUiPlugin.getDefault().getActiveReviewManager().activeReviewUpdated();
			}
		}
	}

	private final String shellTitle;

	private final CrucibleClient client;

	private LineRange commentLines;

	private Comment parentComment;

	private CrucibleFile crucibleFile;

	private static final String SAVE_LABEL = "&Post";

	private static final String DRAFT_LABEL = "Post as &Draft";

	private static final String DEFECT_LABEL = "Defect";

	private final boolean edit = false;

	private FormToolkit toolkit;

	private boolean draft = false;

	private boolean defect = false;

	private String newComment;

	private Button saveButton;

	private Button saveDraftButton;

	public CrucibleAddCommentDialog(Shell parentShell, String shellTitle, Review review, String taskKey, String taskId,
			TaskRepository taskRepository, CrucibleClient client) {
		super(parentShell, taskRepository, review, taskKey, taskId);
		this.shellTitle = shellTitle;
		this.client = client;
	}

	@Override
	protected Control createPageControls(Composite parent) {
		// CHECKSTYLE:MAGIC:OFF
		getShell().setText(shellTitle);
		setTitle(shellTitle);

		if (parentComment == null) {
			setMessage("Create a new comment");
		} else {
			setMessage("Reply to a comment from: " + parentComment.getAuthor().getDisplayName());
		}

		// CHECKSTYLE:MAGIC:OFF
		Composite composite = new Composite(parent, SWT.NONE);
		composite.setLayout(new GridLayout(1, false));

		if (toolkit == null) {
			toolkit = new FormToolkit(getShell().getDisplay());
		}
		parent.addDisposeListener(new DisposeListener() {
			public void widgetDisposed(DisposeEvent e) {
				if (toolkit != null) {
					toolkit.dispose();
				}
			}
		});

		createAdditionalControl(composite);
		createWikiTextControl(composite, toolkit);

		commentText.getViewer().addTextListener(new ITextListener() {

			public void textChanged(TextEvent event) {
				boolean enabled = false;
				if (commentText != null && commentText.getText().trim().length() > 0) {
					enabled = true;
				}

				if (saveButton != null && !saveButton.isDisposed()
						&& (parentComment == null || !parentComment.isDraft())) {
					saveButton.setEnabled(enabled);
				}

				if (saveDraftButton != null && !saveDraftButton.isDisposed()) {
					saveDraftButton.setEnabled(enabled);
				}
			}
		});

		((GridLayout) parent.getLayout()).makeColumnsEqualWidth = false;
		// create buttons according to (implicit) reply type
		int nrOfCustomFields = 0;
		if (parentComment == null) { // "defect button" needed if new comment
			Composite compositeCustomFields = new Composite(composite, SWT.NONE);
			compositeCustomFields.setLayout(new GridLayout(1, false));
			createDefectButton(compositeCustomFields);
			GridDataFactory.fillDefaults()
					.grab(true, false)
					.span(nrOfCustomFields + 1, 1)
					.applyTo(compositeCustomFields);
		}

		GridDataFactory.fillDefaults().grab(true, true).hint(SWT.DEFAULT, SWT.DEFAULT).applyTo(composite);

		applyDialogFont(composite);
		return composite;
	}

	protected void createAdditionalControl(Composite composite) {
	}

	protected void processFields() {
		newComment = commentText.getText();
		if (defect) { // process custom field selection only when defect is selected
			for (CustomFieldDef field : customCombos.keySet()) {
				CustomFieldValue customValue = (CustomFieldValue) customCombos.get(field).getElementAt(
						customCombos.get(field).getCombo().getSelectionIndex());
				if (customValue != null) {
					CustomFieldBean bean = new CustomFieldBean();
					bean.setConfigVersion(field.getConfigVersion());
					bean.setValue(customValue.getName());
					customFieldSelections.put(field.getName(), bean);
				}
			}
		}
	}

	protected Button createDefectButton(Composite parent) {
		// increment the number of columns in the button bar
		((GridLayout) parent.getLayout()).numColumns++;
		defectButton = new Button(parent, SWT.CHECK);
		defectButton.setText(DEFECT_LABEL);
		defectButton.setFont(JFaceResources.getDialogFont());
		defectButton.addSelectionListener(new SelectionAdapter() {
			@Override
			public void widgetSelected(SelectionEvent event) {
				defect = !defect;
				// toggle combos
				for (CustomFieldDef field : customCombos.keySet()) {
					customCombos.get(field).getCombo().setEnabled(defect);
				}
			}
		});
		return defectButton;
	}

	protected void createCombo(Composite parent, final CustomFieldDef customField, int selection) {
		((GridLayout) parent.getLayout()).numColumns++;
		Label label = new Label(parent, SWT.NONE);
		label.setText("Select " + customField.getName());
		((GridLayout) parent.getLayout()).numColumns++;
		ComboViewer comboViewer = new ComboViewer(parent);
		comboViewer.setContentProvider(new ArrayContentProvider());
		comboViewer.setLabelProvider(new LabelProvider() {
			@Override
			public String getText(Object element) {
				CustomFieldValue fieldValue = (CustomFieldValue) element;
				return fieldValue.getName();
			}
		});
		comboViewer.setInput(customField.getValues());
		comboViewer.getCombo().setEnabled(false);
		customCombos.put(customField, comboViewer);
	}

	public boolean addComment() {
		try {
			newComment = commentText.getText();
			processFields();
			setMessage("");
			run(true, false, new AddCommentRunnable());
		} catch (InvocationTargetException e) {
			StatusHandler.log(new Status(IStatus.ERROR, CrucibleUiPlugin.PLUGIN_ID, e.getMessage(), e));
			setErrorMessage("Unable to add the comment to the review");
			return false;
		} catch (InterruptedException e) {
			StatusHandler.log(new Status(IStatus.ERROR, CrucibleUiPlugin.PLUGIN_ID, e.getMessage(), e));
			setErrorMessage("Unable to add the comment to the review");
			return false;
		}

		setReturnCode(Window.OK);
		close();
		return true;
	}

	@Override
	protected void createButtonsForButtonBar(Composite parent) {
		saveButton = createButton(parent, IDialogConstants.CLIENT_ID + 2, SAVE_LABEL, false);
		saveButton.addSelectionListener(new SelectionAdapter() {
			@Override
			public void widgetSelected(SelectionEvent e) {
				addComment();
			}
		});
		saveButton.setEnabled(false);
		if (!edit) { // if it is a new reply, saving as draft is possible
			saveDraftButton = createButton(parent, IDialogConstants.CLIENT_ID + 2, DRAFT_LABEL, false);
			saveDraftButton.addSelectionListener(new SelectionAdapter() {
				@Override
				public void widgetSelected(SelectionEvent e) {
					draft = true;
					addComment();
				}
			});
			saveDraftButton.setEnabled(false);
		}
		createButton(parent, IDialogConstants.CANCEL_ID, IDialogConstants.CANCEL_LABEL, false).addSelectionListener(
				new SelectionAdapter() {
					@Override
					public void widgetSelected(SelectionEvent e) {
						cancelPressed();
					}
				});
	}

	public void setReviewItem(CrucibleFile reviewItem) {
		this.crucibleFile = reviewItem;
	}

	public void setParentComment(Comment comment) {
		this.parentComment = comment;
	}

	public void setCommentLines(LineRange commentLines2) {
		this.commentLines = commentLines2;
	}

}
