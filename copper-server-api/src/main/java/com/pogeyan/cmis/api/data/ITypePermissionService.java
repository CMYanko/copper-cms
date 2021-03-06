package com.pogeyan.cmis.api.data;

import java.util.List;

import com.pogeyan.cmis.api.auth.IUserObject;
import com.pogeyan.cmis.api.data.common.PermissionType;

public interface ITypePermissionService {
	public Boolean checkTypeAccess(String repositoryId, IUserObject role, String typeId);

	public List<String> getFieldAccess(String repositoryId, IUserObject role, String typeId);

	public Boolean checkPermissionAccess(String repositoryId, IUserObject role, String typeId,
			PermissionType permissionAccess);

	public List<String> getAclPropagationList(String repositoryId, IUserObject role, String typeId);

	public List<String> getRoles(String repositoryId, IUserObject role, String typeId);
}
