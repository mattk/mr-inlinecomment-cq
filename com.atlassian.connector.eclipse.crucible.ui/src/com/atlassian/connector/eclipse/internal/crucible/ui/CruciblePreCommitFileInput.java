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

import com.atlassian.connector.eclipse.team.ui.CrucibleFile;

import org.eclipse.core.internal.filesystem.local.LocalFile;
import org.eclipse.core.resources.IStorage;
import org.eclipse.core.runtime.IPath;
import org.eclipse.core.runtime.Path;
import org.eclipse.jface.resource.ImageDescriptor;
import org.eclipse.ui.IPathEditorInput;
import org.eclipse.ui.IPersistableElement;
import org.eclipse.ui.IStorageEditorInput;
import org.eclipse.ui.ide.FileStoreEditorInput;

/**
 * 
 * @author Jacek Jaroczynski
 */
@SuppressWarnings("restriction")
public class CruciblePreCommitFileInput extends FileStoreEditorInput implements IStorageEditorInput, IPathEditorInput,
		ICrucibleFileProvider {
	private final CruciblePreCommitFileStorage storage;

	public CruciblePreCommitFileInput(CruciblePreCommitFileStorage storage, LocalFile localFile) {
		super(localFile);
		this.storage = storage;
	}

	public boolean exists() {
		return true;
	}

	public ImageDescriptor getImageDescriptor() {
		return null;
	}

	public String getName() {
		return storage.getName() + String.format(" [%s]", storage.getCrucibleFile().getSelectedFile().getRevision());
	}

	public IPersistableElement getPersistable() {
		return null;
	}

	public IStorage getStorage() {
		return storage;
	}

	public String getToolTipText() {
		return storage.getFullPath().toString();
	}

	public CrucibleFile getCrucibleFile() {
		return storage.getCrucibleFile();
	}

	public IPath getPath() {
		if (storage.getLocalFilePath() != null) {
			return new Path(storage.getLocalFilePath());
		}
		return null;
	}

	@Override
	public int hashCode() {
		final int prime = 31;
		int result = 1;
		result = prime * result + ((storage == null) ? 0 : storage.hashCode());
		return result;
	}

	@Override
	public boolean equals(Object obj) {
		if (this == obj) {
			return true;
		}
		if (obj == null) {
			return false;
		}
		if (getClass() != obj.getClass()) {
			return false;
		}
		CruciblePreCommitFileInput other = (CruciblePreCommitFileInput) obj;
		if (storage == null) {
			if (other.storage != null) {
				return false;
			}
		} else if (!storage.equals(other.storage)) {
			return false;
		}
		return true;
	}

}