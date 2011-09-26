/**
 * Copyright (C) 2003-2007 eXo Platform SAS.
 *
 * This program is free software; you can redistribute it and/or
 * modify it under the terms of the GNU Affero General Public License
 * as published by the Free Software Foundation; either version 3
 * of the License, or (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program; if not, see<http://www.gnu.org/licenses/>.
 */
package org.exoplatform.social.core.storage;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.locks.Lock;

import javax.jcr.Node;
import javax.jcr.NodeIterator;
import javax.jcr.PathNotFoundException;
import javax.jcr.Property;
import javax.jcr.PropertyIterator;
import javax.jcr.Session;
import javax.jcr.Value;
import javax.jcr.query.Query;
import javax.jcr.query.QueryManager;
import javax.jcr.query.QueryResult;

import org.exoplatform.services.cache.CacheService;
import org.exoplatform.services.cache.ExoCache;
import org.exoplatform.services.jcr.access.AccessControlEntry;
import org.exoplatform.services.jcr.access.PermissionType;
import org.exoplatform.services.jcr.access.SystemIdentity;
import org.exoplatform.services.jcr.core.ExtendedNode;
import org.exoplatform.services.jcr.util.IdGenerator;
import org.exoplatform.services.log.ExoLogger;
import org.exoplatform.services.log.Log;
import org.exoplatform.social.common.jcr.JCRSessionManager;
import org.exoplatform.social.common.jcr.LockManager;
import org.exoplatform.social.common.jcr.SocialDataLocation;
import org.exoplatform.social.core.model.AvatarAttachment;
import org.exoplatform.social.core.space.model.Space;


public class SpaceStorage {

  private static final Log LOG = ExoLogger.getLogger(SpaceStorage.class);

  final static private String SPACE_NODETYPE = "exo:space".intern();
  final static private String SPACE_NAME = "exo:name".intern();
  final static private String SPACE_GROUPID = "exo:groupId".intern();
  final static private String SPACE_APP = "exo:app".intern();
  final static private String SPACE_PARENT = "exo:parent".intern();
  final static private String SPACE_DESCRIPTION = "exo:description".intern();
  final static private String SPACE_TAG = "exo:tag".intern();
  final static private String SPACE_PENDING_USER = "exo:pendingUsers".intern();
  final static private String SPACE_INVITED_USER = "exo:invitedUsers".intern();
  final static private String SPACE_TYPE = "exo:type".intern();
  final static private String SPACE_URL = "exo:url".intern();
  final static private String SPACE_VISIBILITY = "exo:visibility".intern();
  final static private String SPACE_REGISTRATION = "exo:registration".intern();
  final static private String SPACE_PRIORITY = "exo:priority".intern();

  private static final String SPACE_PROPERTIES_NAME_PATTERN;
  static {
    StringBuilder buffer = new StringBuilder(256);
    final char separator = '|';
    buffer.append(SPACE_NAME).append(separator);
    buffer.append(SPACE_GROUPID).append(separator);
    buffer.append(SPACE_APP).append(separator);
    buffer.append(SPACE_PARENT).append(separator);
    buffer.append(SPACE_DESCRIPTION).append(separator);
    buffer.append(SPACE_TAG).append(separator);
    buffer.append(SPACE_PENDING_USER).append(separator);
    buffer.append(SPACE_INVITED_USER).append(separator);
    buffer.append(SPACE_TYPE).append(separator);
    buffer.append(SPACE_URL).append(separator);
    buffer.append(SPACE_VISIBILITY).append(separator);
    buffer.append(SPACE_REGISTRATION).append(separator);
    buffer.append(SPACE_PRIORITY);
    SPACE_PROPERTIES_NAME_PATTERN = buffer.toString();
  }


  private SocialDataLocation dataLocation;
  private JCRSessionManager sessionManager;
  private String workspace;

  /** The Lock manager. */
  private final LockManager lockManager;

  /**
   * The cache for the spaces
   */
  private final ExoCache<String, Space> spaceCache;

  public SpaceStorage(SocialDataLocation dataLocation, LockManager lockManager, CacheService cacheService) {
    this.spaceCache = cacheService.getCacheInstance(getClass().getName() + "spaceCache");
    this.lockManager = lockManager;
    this.dataLocation = dataLocation;
    this.sessionManager = dataLocation.getSessionManager();
    this.workspace = dataLocation.getWorkspace();
  }

  private Node getSpaceHome(Session session) throws Exception {
    String path = dataLocation.getSocialSpaceHome();
    return session.getRootNode().getNode(path);
  }

  public String getWorkspace() throws Exception {
    return workspace;
  }

  /**
   * Gets the all spaces.
   *
   * @return the all spaces
   */
  public List<Space> getAllSpaces() {
    List<Space> spaces = new ArrayList<Space>();
    try {
      Session session = sessionManager.openSession();
      Node spaceHomeNode = getSpaceHome(session);
      NodeIterator iter = spaceHomeNode.getNodes();
      Space space;
      while (iter.hasNext()) {
        Node spaceNode = iter.nextNode();
        space = getSpace(spaceNode, session);
        spaces.add(space);
      }
      return spaces;
    } catch (Exception e) {
      e.printStackTrace();
      return null;
    } finally {
      sessionManager.closeSession();
    }
  }

  public Space getSpaceById(String id) {
    Space cachedSpace = spaceCache.get(id);
    if (cachedSpace!= null) {
      return cachedSpace;
    }
    try {
      Session session = sessionManager.openSession();
      Node spaceHomeNode = getSpaceHome(session);
      StringBuilder queryString = new StringBuilder("/").append(spaceHomeNode.getPath())
              .append("/element(*,").append(SPACE_NODETYPE).append(")[(@")
              .append("jcr:uuid").append("='").append(id).append("')]");
      QueryManager queryManager = session.getWorkspace().getQueryManager();
      Query query = queryManager.createQuery(queryString.toString(), Query.XPATH);
      QueryResult queryResult = query.execute();
      NodeIterator nodeIterator = queryResult.getNodes();
      Node identityNode = null;

      if (nodeIterator.hasNext()) {
        identityNode = (Node) nodeIterator.next();
      } else {
        LOG.debug("No node found for space");
      }
      return getSpace(identityNode, session);
    } catch (Exception e) {
      // TODO: handle exception
    } finally {
      sessionManager.closeSession();
    }
    return null;
  }

  /**
   * Gets a space by its groupId.
   *
   * @param id the group Id
   * @return
   * @since 1.1.3
   */
  public Space getSpaceByGroupId(String id) {
    try {
      Session session = sessionManager.openSession();
      Node spaceHomeNode = getSpaceHome(session);
      StringBuilder queryString = new StringBuilder("/").append(spaceHomeNode.getPath())
              .append("/element(*,").append(SPACE_NODETYPE).append(")[(@")
              .append(SPACE_GROUPID).append("='").append(id).append("')]");
      QueryManager queryManager = session.getWorkspace().getQueryManager();
      Query query = queryManager.createQuery(queryString.toString(), Query.XPATH);
      QueryResult queryResult = query.execute();
      NodeIterator nodeIterator = queryResult.getNodes();
      Node spaceNode = null;

      if (nodeIterator.hasNext()) {
        spaceNode = (Node) nodeIterator.next();
      } else {
        LOG.debug("No space could be found with the group id " + id);
        return null;
      }
      return getSpace(spaceNode, session);
    } catch (Exception e) {
      LOG.error(e.getMessage(), e);
    } finally {
      sessionManager.closeSession();
    }
    return null;
  }
  /**
   * Gets all spaces that have name or description match input condition.
   *
   * @param condition the search condition
   * @return the identities by profile filter
   * @throws Exception the exception
   */
  public List<Space> getSpacesBySearchCondition(String condition) throws Exception {
    Session session = sessionManager.openSession();
    Node spaceHomeNode = getSpaceHome(session);
    List<Space> listSpace = new ArrayList<Space>();
    NodeIterator nodeIterator = null;

    try {
      QueryManager queryManager = session.getWorkspace().getQueryManager() ;
      StringBuilder queryString = new StringBuilder("/").append(spaceHomeNode.getPath())
              .append("/element(*,").append(SPACE_NODETYPE).append(')');

      if (condition.length() != 0) {
        queryString.append("[");

        if(condition.indexOf("*")<0){
          if(condition.charAt(0) != '*') condition = "*" + condition;
          if(condition.charAt(condition.length()-1) != '*') condition += "*";
        }
        queryString.append("(jcr:contains(@exo:name, ").append("'").append(condition).append("'))");
        queryString.append(" or (jcr:contains(@exo:description, '").append(condition).append("'))");

        queryString.append("]");
      }
      Query query = queryManager.createQuery(queryString.toString(), Query.XPATH);
      QueryResult queryResult = query.execute();
      nodeIterator = queryResult.getNodes();
    } catch (Exception e) {
      LOG.warn("error while filtering identities: " + e.getMessage());
      return (new ArrayList<Space>());
    } finally {
      sessionManager.closeSession();
    }

    Space space;
    Node spaceNode;
    while (nodeIterator.hasNext()) {
      spaceNode = nodeIterator.nextNode();
      space = getSpace(spaceNode, session);
      listSpace.add(space);
    }

    return listSpace;
  }

  /**
   * Gets a space by its name.
   *
   * @param spaceName
   * @return
   */
  public Space getSpaceByName(String spaceName) {
    try {
      Session session = sessionManager.openSession();

      Node spaceHomeNode = getSpaceHome(session);
      NodeIterator iter = spaceHomeNode.getNodes();
      Space space;
      while (iter.hasNext()) {
        Node spaceNode = iter.nextNode();
        space = getSpace(spaceNode, session);
        if(space.getName().equals(spaceName)) return space;
      }
    }catch (Exception e) {
      return null;
    } finally {
      sessionManager.closeSession();
    }
    return null;
  }

  /**
   * Gets a space by its url.
   *
   * @param url
   * @return
   */
  public Space getSpaceByUrl(String url) {
    try {
      Session session = sessionManager.openSession();

      Node spaceHomeNode = getSpaceHome(session);
      NodeIterator iter = spaceHomeNode.getNodes();
      Space space;
      while (iter.hasNext()) {
        Node spaceNode = iter.nextNode();
        space = getSpace(spaceNode, session);
        if(space.getUrl().equals(url)) return space;
      }
    }catch (Exception e) {
      return null;
    } finally {
      sessionManager.closeSession();
    }
    return null;
  }

  /**
   * Delete a space by its id.
   *
   * @param id
   */
  public void deleteSpace(String id) {
    Session session = sessionManager.openSession();
    try {
      Node spaceNode = session.getNodeByUUID(id);
      if(spaceNode != null) {
        spaceNode.remove();
        session.save();
      }
      spaceCache.remove(id);
    } catch (Exception e) {
      LOG.warn(e.getMessage(), e);
    } finally {
      sessionManager.closeSession();
    }
  }

  /**
   * Saves a space.
   *
   * @param space
   * @param isNew
   */
  public void saveSpace(Space space, boolean isNew) {
    try {
      Session session = sessionManager.openSession();

      Node spaceHomeNode = getSpaceHome(session);
      saveSpace(spaceHomeNode, space, isNew, session);
    } catch (Exception e) {
      // TODO: handle exception
    } finally {
      sessionManager.closeSession();}
  }

  private void saveSpace(Node spaceHomeNode, Space space, boolean isNew, Session session) {
    Lock lock = lockManager.getLock("Space", space.getName());
    lock.lock();
    try {
      Node spaceNode;
      try {
        if(isNew) {
          spaceNode = spaceHomeNode.addNode(IdGenerator.generate(), SPACE_NODETYPE);
          spaceNode.addMixin("mix:referenceable");
        } else {
          spaceNode = session.getNodeByUUID(space.getId());
        }
        if(space.getId() == null) space.setId(spaceNode.getUUID());
        spaceNode.setProperty(SPACE_NAME, space.getName());
        spaceNode.setProperty(SPACE_GROUPID, space.getGroupId());
        spaceNode.setProperty(SPACE_APP, space.getApp());
        spaceNode.setProperty(SPACE_PARENT, space.getParent());
        spaceNode.setProperty(SPACE_DESCRIPTION, space.getDescription());
        spaceNode.setProperty(SPACE_TAG, space.getTag());
        spaceNode.setProperty(SPACE_PENDING_USER, space.getPendingUsers());
        spaceNode.setProperty(SPACE_INVITED_USER, space.getInvitedUsers());
        spaceNode.setProperty(SPACE_TYPE, space.getType());
        spaceNode.setProperty(SPACE_URL, space.getUrl());
        spaceNode.setProperty(SPACE_VISIBILITY, space.getVisibility());
        spaceNode.setProperty(SPACE_REGISTRATION, space.getRegistration());
        spaceNode.setProperty(SPACE_PRIORITY, space.getPriority());
        //  save image to contact
        AvatarAttachment attachment = space.getAvatarAttachment();
        if (attachment != null) {
          // fix load image on IE6 UI
          ExtendedNode extNode = (ExtendedNode)spaceNode;
          if (extNode.canAddMixin("exo:privilegeable")) extNode.addMixin("exo:privilegeable");
          String[] arrayPers = {PermissionType.READ, PermissionType.ADD_NODE, PermissionType.SET_PROPERTY, PermissionType.REMOVE} ;
          extNode.setPermission(SystemIdentity.ANY, arrayPers) ;
          List<AccessControlEntry> permsList = extNode.getACL().getPermissionEntries() ;
          for(AccessControlEntry accessControlEntry : permsList) {
            extNode.setPermission(accessControlEntry.getIdentity(), arrayPers) ;
          }

          if (attachment.getFileName() != null) {
            Node nodeFile = null ;
            try {
              nodeFile = spaceNode.getNode("image") ;
            } catch (PathNotFoundException ex) {
              nodeFile = spaceNode.addNode("image", "nt:file");
            }
            Node nodeContent = null ;
            try {
              nodeContent = nodeFile.getNode("jcr:content") ;
            } catch (PathNotFoundException ex) {
              nodeContent = nodeFile.addNode("jcr:content", "nt:resource") ;
            }
            long lastModified = attachment.getLastModified();
            long lastSaveTime = 0;
            if (nodeContent.hasProperty("jcr:lastModified"))
              lastSaveTime = nodeContent.getProperty("jcr:lastModified").getLong();
            if ((lastModified != 0) && (lastModified != lastSaveTime)) {
              nodeContent.setProperty("jcr:mimeType", attachment.getMimeType()) ;
              nodeContent.setProperty("jcr:data", attachment.getInputStream(session));
              nodeContent.setProperty("jcr:lastModified", attachment.getLastModified());
            }
          }
        } else {
          if(spaceNode.hasNode("image")) {
            spaceNode.getNode("image").remove() ;
            // add 12DEC
            session.save();
          }
        }
        if(isNew) {
          spaceHomeNode.save();
        } else {
          spaceNode.save();
          spaceCache.remove(spaceNode.getUUID());
        }
      } catch (Exception e) {
        LOG.error("failed to save space", e);
        // TODO: handle exception
      }
    } finally {
      lock.unlock();
    }
  }

  private Space getSpace(Node spaceNode, Session session) throws Exception{
    String id = spaceNode.getUUID();
    Space space = spaceCache.get(id);
    if (space != null) {
      return space;
    }
    Lock lock = lockManager.getLock("SpaceById", id);
    lock.lock();
    try {
      space = spaceCache.get(id);
      if (space != null) {
        return space;
      }
      space = new Space();
      space.setId(spaceNode.getUUID());
      PropertyIterator it = spaceNode.getProperties(SPACE_PROPERTIES_NAME_PATTERN);
      while (it.hasNext()) {
        Property p = it.nextProperty();
        String propertyName = p.getName();
        if (SPACE_NAME.equals(propertyName))
          space.setName(p.getString());
        else if (SPACE_GROUPID.equals(propertyName))
          space.setGroupId(p.getString());
        else if (SPACE_APP.equals(propertyName))
          space.setApp(p.getString());
        else if (SPACE_PARENT.equals(propertyName))
          space.setParent(p.getString());
        else if (SPACE_DESCRIPTION.equals(propertyName))
          space.setDescription(p.getString());
        else if (SPACE_TAG.equals(propertyName))
          space.setTag(p.getString());
        else if (SPACE_PENDING_USER.equals(propertyName))
          space.setPendingUsers(convertValuesToStrings(p.getValues()));
        else if (SPACE_INVITED_USER.equals(propertyName))
          space.setInvitedUsers(convertValuesToStrings(p.getValues()));
        else if (SPACE_TYPE.equals(propertyName))
          space.setType(p.getString());
        else if (SPACE_URL.equals(propertyName))
          space.setUrl(p.getString());
        else if (SPACE_VISIBILITY.equals(propertyName))
          space.setVisibility(p.getString());
        else if (SPACE_REGISTRATION.equals(propertyName))
          space.setRegistration(p.getString());
        else if (SPACE_PRIORITY.equals(propertyName))
          space.setPriority(p.getString());
      }
      if(spaceNode.hasNode("image")){
        Node image = spaceNode.getNode("image");
        if (image.isNodeType("nt:file")) {
          AvatarAttachment file = new AvatarAttachment() ;
          file.setId(image.getPath()) ;
          file.setMimeType(image.getNode("jcr:content").getProperty("jcr:mimeType").getString()) ;
          try {
            file.setInputStream(image.getNode("jcr:content").getProperty("jcr:data").getValue().getStream());
          } catch (Exception ex) {
            LOG.warn(ex.getMessage(), ex);
          }
          file.setFileName(image.getName()) ;
          file.setLastModified(image.getNode("jcr:content").getProperty("jcr:lastModified").getLong());
          file.setWorkspace(session.getWorkspace().getName()) ;
          space.setAvatarAttachment(file) ;
        }
      }
      spaceCache.put(id, space);
    } finally {
      lock.unlock();
    }
    return space;
  }

  private String [] convertValuesToStrings(Value[] values) throws Exception {
    if(values.length == 1) return new String[]{values[0].getString()};
    String[] strArrays = new String[values.length];
    for(int i = 0; i < values.length; ++i) {
      strArrays[i] = values[i].getString();
    }
    return strArrays;
  }
}