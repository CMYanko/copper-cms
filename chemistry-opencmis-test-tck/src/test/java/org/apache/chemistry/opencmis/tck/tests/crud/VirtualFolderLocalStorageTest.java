package org.apache.chemistry.opencmis.tck.tests.crud;

import static org.apache.chemistry.opencmis.tck.CmisTestResultStatus.FAILURE;
import static org.apache.chemistry.opencmis.tck.CmisTestResultStatus.INFO;
import static org.apache.chemistry.opencmis.tck.CmisTestResultStatus.SKIPPED;
import static org.apache.chemistry.opencmis.tck.CmisTestResultStatus.UNEXPECTED_EXCEPTION;
import static org.apache.chemistry.opencmis.tck.CmisTestResultStatus.WARNING;

import java.io.File;
import java.io.FileReader;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.apache.chemistry.opencmis.client.api.CmisObject;
import org.apache.chemistry.opencmis.client.api.Folder;
import org.apache.chemistry.opencmis.client.api.ObjectType;
import org.apache.chemistry.opencmis.client.api.Session;
import org.apache.chemistry.opencmis.commons.PropertyIds;
import org.apache.chemistry.opencmis.commons.data.CreatablePropertyTypes;
import org.apache.chemistry.opencmis.commons.data.NewTypeSettableAttributes;
import org.apache.chemistry.opencmis.commons.enums.Cardinality;
import org.apache.chemistry.opencmis.commons.enums.CmisVersion;
import org.apache.chemistry.opencmis.commons.enums.PropertyType;
import org.apache.chemistry.opencmis.commons.enums.Updatability;
import org.apache.chemistry.opencmis.commons.exceptions.CmisBaseException;
import org.apache.chemistry.opencmis.commons.exceptions.CmisObjectNotFoundException;
import org.apache.chemistry.opencmis.commons.impl.dataobjects.AbstractPropertyDefinition;
import org.apache.chemistry.opencmis.commons.impl.dataobjects.FolderTypeDefinitionImpl;
import org.apache.chemistry.opencmis.commons.impl.dataobjects.PropertyBooleanDefinitionImpl;
import org.apache.chemistry.opencmis.commons.impl.json.JSONArray;
import org.apache.chemistry.opencmis.commons.impl.json.JSONObject;
import org.apache.chemistry.opencmis.commons.impl.json.parser.JSONParser;
import org.apache.chemistry.opencmis.tck.CmisTestResult;
import org.apache.chemistry.opencmis.tck.impl.AbstractSessionTest;

public class VirtualFolderLocalStorageTest extends AbstractSessionTest {
	@Override
	public void init(Map<String, String> parameters) {
		super.init(parameters);
		setName("Create Type with Virtual Folder Implementation");
		setDescription("Creates a Folder type with VirtualFolder property definition and creates object of that type");
	}

	@Override
	public void run(Session session) throws Exception {
		if (session.getRepositoryInfo().getCmisVersion() == CmisVersion.CMIS_1_0) {
			addResult(createResult(SKIPPED, "Type mutability is not supported by CMIS 1.0. Test skipped!"));
			return;
		}

		ObjectType parentType = session.getTypeDefinition(getFolderTestTypeId());
		if (parentType.getTypeMutability() == null
				|| !Boolean.TRUE.equals(parentType.getTypeMutability().canCreate())) {
			addResult(createResult(SKIPPED, "Test document type doesn't allow creating a sub-type. Test skipped!"));
			return;
		}
		createTypeWithProperties(session, parentType);
	}

	private void createTypeWithProperties(Session session, ObjectType parentType) throws Exception {
		CmisTestResult failure = null;

		CreatablePropertyTypes cpt = session.getRepositoryInfo().getCapabilities().getCreatablePropertyTypes();
		if (cpt == null || cpt.canCreate() == null || cpt.canCreate().isEmpty()) {
			addResult(createResult(FAILURE, "Repository Info does not indicate, which property types can be created!"));
			return;
		}

		// define the type
		FolderTypeDefinitionImpl newTypeDef = createFolderTypeDefinition(session, "cmis_ext:folder", parentType);
		newTypeDef.addPropertyDefinition(createPropertyDefinition(PropertyType.BOOLEAN));

		// create the type
		ObjectType newType = createType(session, newTypeDef);
		if (newType == null) {
			return;
		}

		// get the type
		ObjectType newType2 = null;
		try {
			newType2 = session.getTypeDefinition(newType.getId());

			// assert type definitions
			failure = createResult(FAILURE,
					"The type definition returned by createType() doesn't match the type definition returned by getTypeDefinition()!");
			addResult(assertEquals(newType, newType2, null, failure));
		} catch (CmisObjectNotFoundException e) {
			addResult(createResult(FAILURE, "Newly created type can not be fetched. Id: " + newType.getId(), e, false));
		}

		// add object of that type
		// create a test folder
		Folder testFolder = createTestFolder(session);
		checkIfFolderExistsInLocal(testFolder, false);
		try {
			Folder nonVirFolder = createFolderWithCustomProperties(session, testFolder, "NonVirFolder", newType.getId(),
					false);
			checkIfFolderExistsInLocal(nonVirFolder, false);
			Folder virFolder = createFolderWithCustomProperties(session, testFolder, "VirFolder", newType.getId(),
					true);
			checkIfFolderExistsInLocal(virFolder, true);

		} finally {
			deleteTestFolder();
			deleteType(session, newType.getId());
		}
		addResult(createInfoResult("Tested the creation of type with VirtualFolder and adding object of that type"));
	}

	@SuppressWarnings("unchecked")
	private void checkIfFolderExistsInLocal(Folder folder, boolean isVirtual) throws Exception {

		String envVariable = System.getenv("CMIS_REPO_JSON_LOCATION");
		if (envVariable == null) {
			addResult(createResult(FAILURE, "set the environment variables of CMIS_REPO_JSON_LOCATION"));
		}
		String path = null;
		Object obj = new JSONParser().parse(new FileReader(envVariable));
		JSONArray repoArray = (JSONArray) obj;
		for (Object object : repoArray) {
			JSONObject jsonObject = (JSONObject) object;
			Map<String, String> fileDetails = (Map<String, String>) jsonObject.get("file");
			path = fileDetails.get("location");
		}
		File newFile = new File(gettingFolderPath(path, gettingDocNamePath(folder.getPath())), folder.getName());

		if (newFile.exists()) {
			if (isVirtual) {
				addResult(assertEquals(true, isVirtual,
						createResult(FAILURE, "isVirtual is set to true, folder shouldnt exist", false), null));
			} else {
				addResult(assertEquals(true, isVirtual,
						createResult(FAILURE, "isVirtual is set to true, folder shouldnt exist", false),
						createResult(INFO, "isVirtual is set to false, folder should exist", false)));
			}
		} else {
			if (isVirtual) {
				addResult(assertEquals(true, isVirtual,
						createResult(INFO, "isVirtual is set to true, folder shouldnt exist", false), null));
			} else {
				addResult(assertEquals(true, isVirtual, null,
						createResult(FAILURE, "isVirtual is set to false, folder should exist", false)));
			}
		}
	}

	private static String gettingDocNamePath(String path) {
		String[] folderNames = path.split("/");
		String root = null;
		String lastToken = folderNames[folderNames.length - 1];
		for (String folderName : folderNames) {
			if (!folderName.isEmpty()) {
				if (lastToken != folderName) {
					if (root == null) {
						root = "/" + folderName;
					} else {
						root = root + "/" + folderName;
					}

				}
			}
		}
		if (root == null) {
			root = "/";
		}
		return root;

	}

	private static String gettingFolderPath(String rootPath, String path) {

		String folderPath = rootPath + path.replace(":", "_").replace("/", File.separator);
		return folderPath;
	}

	private FolderTypeDefinitionImpl createFolderTypeDefinition(Session session, String typeId, ObjectType parentType) {
		CmisTestResult failure = null;

		NewTypeSettableAttributes settableAttributes = session.getRepositoryInfo().getCapabilities()
				.getNewTypeSettableAttributes();
		if (settableAttributes == null) {
			addResult(createResult(WARNING, "Capability NewTypeSettableAttributes is not set!"));
		}

		FolderTypeDefinitionImpl result = new FolderTypeDefinitionImpl();

		result.setBaseTypeId(parentType.getBaseTypeId());
		result.setParentTypeId(parentType.getId());

		if (settableAttributes == null || Boolean.TRUE.equals(settableAttributes.canSetId())) {
			result.setId(typeId);
		} else if (settableAttributes != null) {
			failure = createResult(WARNING, "Flag 'id' in capability NewTypeSettableAttributes is not set!");
			addResult(assertNotNull(settableAttributes.canSetId(), null, failure));
		}

		if (settableAttributes == null || Boolean.TRUE.equals(settableAttributes.canSetLocalName())) {
			result.setLocalName("cmis_ext:folder");
		} else if (settableAttributes != null) {
			failure = createResult(WARNING, "Flag 'localName' in capability NewTypeSettableAttributes is not set!");
			addResult(assertNotNull(settableAttributes.canSetLocalName(), null, failure));
		}

		if (settableAttributes == null || Boolean.TRUE.equals(settableAttributes.canSetLocalNamespace())) {
			result.setLocalNamespace("cmis_ext:folder");
		} else if (settableAttributes != null) {
			failure = createResult(WARNING,
					"Flag 'localNamespace' in capability NewTypeSettableAttributes is not set!");
			addResult(assertNotNull(settableAttributes.canSetLocalNamespace(), null, failure));
		}

		if (settableAttributes == null || Boolean.TRUE.equals(settableAttributes.canSetDisplayName())) {
			result.setDisplayName("cmis_ext:folder");
		} else if (settableAttributes != null) {
			failure = createResult(WARNING, "Flag 'displayName' in capability NewTypeSettableAttributes is not set!");
			addResult(assertNotNull(settableAttributes.canSetDisplayName(), null, failure));
		}

		if (settableAttributes == null || Boolean.TRUE.equals(settableAttributes.canSetDescription())) {
			result.setDescription("description");
		} else if (settableAttributes != null) {
			failure = createResult(WARNING, "Flag 'description' in capability NewTypeSettableAttributes is not set!");
			addResult(assertNotNull(settableAttributes.canSetDescription(), null, failure));
		}

		if (settableAttributes == null || Boolean.TRUE.equals(settableAttributes.canSetQueryName())) {
			result.setQueryName("cmis_ext:folder");
		} else if (settableAttributes != null) {
			failure = createResult(WARNING, "Flag 'queryName' in capability NewTypeSettableAttributes is not set!");
			addResult(assertNotNull(settableAttributes.canSetQueryName(), null, failure));
		}

		if (settableAttributes == null || Boolean.TRUE.equals(settableAttributes.canSetQueryable())) {
			result.setIsQueryable(true);
		} else if (settableAttributes != null) {
			failure = createResult(WARNING, "Flag 'queryable' in capability NewTypeSettableAttributes is not set!");
			addResult(assertNotNull(settableAttributes.canSetQueryable(), null, failure));
		}

		if (settableAttributes == null || Boolean.TRUE.equals(settableAttributes.canSetFulltextIndexed())) {
			result.setIsFulltextIndexed(true);
		} else if (settableAttributes != null) {
			failure = createResult(WARNING,
					"Flag 'fulltextIndexed' in capability NewTypeSettableAttributes is not set!");
			addResult(assertNotNull(settableAttributes.canSetFulltextIndexed(), null, failure));
		}

		if (settableAttributes == null || Boolean.TRUE.equals(settableAttributes.canSetControllableAcl())) {
			result.setIsControllableAcl(true);
		} else if (settableAttributes != null) {
			failure = createResult(WARNING,
					"Flag 'controllableACL' in capability NewTypeSettableAttributes is not set!");
			addResult(assertNotNull(settableAttributes.canSetControllableAcl(), null, failure));
		}

		if (settableAttributes == null || Boolean.TRUE.equals(settableAttributes.canSetControllablePolicy())) {
			result.setIsControllablePolicy(true);
		} else if (settableAttributes != null) {
			failure = createResult(WARNING,
					"Flag 'controllablePolicy' in capability NewTypeSettableAttributes is not set!");
			addResult(assertNotNull(settableAttributes.canSetControllablePolicy(), null, failure));
		}

		if (settableAttributes == null || Boolean.TRUE.equals(settableAttributes.canSetCreatable())) {
			result.setIsCreatable(true);
		} else if (settableAttributes != null) {
			failure = createResult(WARNING, "Flag 'creatable' in capability NewTypeSettableAttributes is not set!");
			addResult(assertNotNull(settableAttributes.canSetCreatable(), null, failure));
		}

		if (settableAttributes == null || Boolean.TRUE.equals(settableAttributes.canSetFileable())) {
			result.setIsFileable(true);
		} else if (settableAttributes != null) {
			failure = createResult(WARNING, "Flag 'fileable' in capability NewTypeSettableAttributes is not set!");
			addResult(assertNotNull(settableAttributes.canSetFileable(), null, failure));
		}

		return result;
	}

	private AbstractPropertyDefinition<?> createPropertyDefinition(PropertyType propertyType) {
		switch (propertyType) {
		case BOOLEAN:
			PropertyBooleanDefinitionImpl result = new PropertyBooleanDefinitionImpl();
			setOtherProperties(result, propertyType);
			return result;
		default:
			break;
		}
		return null;
	}

	private void setOtherProperties(AbstractPropertyDefinition<?> result, PropertyType propertyType) {
		result.setPropertyType(propertyType);
		result.setId("cmis_ext:isVirtual");
		result.setLocalName("cmis_ext:isVirtual");
		result.setLocalNamespace("cmis_ext:isVirtual");
		result.setDisplayName("cmis_ext:isVirtual");
		result.setQueryName("cmis_ext:isVirtual");
		result.setDescription("description");
		if (result.getChoices().size() > 0) {
			result.setCardinality(Cardinality.MULTI);
		} else {
			result.setCardinality(Cardinality.SINGLE);
		}
		result.setUpdatability(Updatability.READWRITE);
		result.setIsInherited(false);
		result.setIsQueryable(true);
		result.setIsOrderable(true);
		result.setIsRequired(false);
		result.setIsOpenChoice(false);
	}

	/**
	 * Creates a folder.
	 */
	protected Folder createFolder(Session session, Folder parent, String name) {
		return createFolder(session, parent, name, getFolderTestTypeId());
	}

	/**
	 * Creates a folder.
	 */
	protected Folder createFolderWithCustomProperties(Session session, Folder parent, String name, String objectTypeId,
			boolean isVirtual) {
		if (parent == null) {
			throw new IllegalArgumentException("Parent is not set!");
		}
		if (name == null) {
			throw new IllegalArgumentException("Name is not set!");
		}
		if (objectTypeId == null) {
			throw new IllegalArgumentException("Object Type ID is not set!");
		}

		// check type
		ObjectType type;
		try {
			type = session.getTypeDefinition(objectTypeId);
		} catch (CmisObjectNotFoundException e) {
			addResult(createResult(UNEXPECTED_EXCEPTION,
					"Folder type '" + objectTypeId + "' is not available: " + e.getMessage(), e, true));
			return null;
		}

		if (Boolean.FALSE.equals(type.isCreatable())) {
			addResult(createResult(SKIPPED, "Folder type '" + objectTypeId + "' is not creatable!", true));
			return null;
		}

		// create
		Map<String, Object> properties = new HashMap<String, Object>();
		properties.put(PropertyIds.NAME, name);
		properties.put(PropertyIds.OBJECT_TYPE_ID, objectTypeId);
		properties.put("cmis_ext:isVirtual", isVirtual);

		Folder result = null;
		try {
			// create the folder
			result = parent.createFolder(properties, null, null, null, SELECT_ALL_NO_CACHE_OC);
		} catch (CmisBaseException e) {
			addResult(createResult(UNEXPECTED_EXCEPTION, "Folder could not be created! Exception: " + e.getMessage(), e,
					true));
			return null;
		}

		try {
			CmisTestResult f;

			// check folder name
			f = createResult(FAILURE, "Folder name does not match!", false);
			addResult(assertEquals(name, result.getName(), null, f));

			// check the new folder
			String[] propertiesToCheck = new String[result.getType().getPropertyDefinitions().size()];

			int i = 0;
			for (String propId : result.getType().getPropertyDefinitions().keySet()) {
				propertiesToCheck[i++] = propId;
			}

			addResult(checkObject(session, result, propertiesToCheck, "New folder object spec compliance"));

			// check object parents
			List<Folder> objectParents = result.getParents();

			f = createResult(FAILURE, "Newly created folder has no or more than one parent! ID: " + result.getId(),
					true);
			addResult(assertEquals(1, objectParents.size(), null, f));

			f = createResult(FAILURE,
					"First object parent of the newly created folder does not match parent! ID: " + result.getId(),
					true);
			assertShallowEquals(parent, objectParents.get(0), null, f);

			// check folder parent
			Folder folderParent = result.getFolderParent();
			f = createResult(FAILURE, "Newly created folder has no folder parent! ID: " + result.getId(), true);
			addResult(assertNotNull(folderParent, null, f));

			f = createResult(FAILURE,
					"Folder parent of the newly created folder does not match parent! ID: " + result.getId(), true);
			assertShallowEquals(parent, folderParent, null, f);

			// check children of parent
			boolean found = false;
			for (CmisObject child : parent.getChildren(SELECT_ALL_NO_CACHE_OC)) {
				if (child == null) {
					addResult(createResult(FAILURE, "Parent folder contains a null child!", true));
				} else {
					if (result.getId().equals(child.getId())) {
						found = true;

						f = createResult(FAILURE, "Folder and parent child don't match! ID: " + result.getId(), true);
						assertShallowEquals(result, child, null, f);
						break;
					}
				}
			}

			if (!found) {
				addResult(createResult(FAILURE, "Folder is not a child of the parent folder! ID: " + result.getId(),
						true));
			}
		} catch (CmisBaseException e) {
			addResult(createResult(UNEXPECTED_EXCEPTION,
					"Newly created folder is invalid! Exception: " + e.getMessage(), e, true));
		}

		return result;
	}
}
