/**
 *  Copyright (c) 2015 Intel Corporation 
 *
 *  Licensed under the Apache License, Version 2.0 (the "License");
 *  you may not use this file except in compliance with the License.
 *  You may obtain a copy of the License at
 *
 *       http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 */

package org.trustedanalytics.atk.domain.frame

import org.trustedanalytics.atk.domain.{ StorageFormats, Status, HasId }
import org.trustedanalytics.atk.domain.schema.{ EdgeSchema, VertexSchema, FrameSchema, Schema }
import org.apache.commons.lang3.StringUtils
import org.joda.time.DateTime

/**
 * Represents a particular revision of a Frame (as stored in a frame table in the meta data DB).
 *
 * The user experience of our product is that Frames are mutable but under the covers Frames are immutable.
 *
 * @param id unique id auto-generated by the database
 * @param name name assigned by user,
 * @param schema the schema of the frame (defines columns, etc)
 * @param status lifecycle status. For example, ACTIVE, DELETED (un-delete possible),
 *               DELETE_FINAL (no un-delete)
 * @param createdOn date/time this record was created
 * @param modifiedOn
 * @param storageFormat
 * @param storageLocation
 * @param description description of frame (a good default description might be the name of the input file)
 * @param rowCount number of rows in the frame
 * @param command the command id that created this revision
 * @param createdBy user who created this row
 * @param modifiedBy
 * @param materializedOn start time of materialization (calculating the physical data - does that include writing it to disk?)
 * @param materializationComplete end time of materialization (calculating the physical data - does that include writing it to disk?)
 * @param errorFrameId foreign key for the error data frame associated with this frame (parse errors go into this frame)
 * @param parent the parent is the previous 'revision' of this data frame as understood by the user
 * @param graphId a value means the frame is owned by a graph and shouldn't be exposed to the user in the same way
 */
case class FrameEntity(id: Long,
                       name: Option[String],
                       schema: Schema = FrameSchema(),
                       status: Long,
                       createdOn: DateTime,
                       modifiedOn: DateTime,
                       storageFormat: Option[String] = None,
                       storageLocation: Option[String] = None,
                       description: Option[String] = None,
                       rowCount: Option[Long] = None,
                       command: Option[Long] = None,
                       createdBy: Option[Long] = None,
                       modifiedBy: Option[Long] = None,
                       materializedOn: Option[DateTime] = None,
                       materializationComplete: Option[DateTime] = None,
                       errorFrameId: Option[Long] = None,
                       parent: Option[Long] = None,
                       graphId: Option[Long] = None,
                       lastReadDate: DateTime = new DateTime) extends HasId {
  require(id >= 0, "id must be zero or greater")
  require(name != null, "name must not be null")
  require(name match {
    case Some(n) => n.trim.length > 0
    case _ => true
  },
    "if name is set it must not be empty or whitespace")
  require(parent.isEmpty || parent.get > 0, "parent must be one or greater if provided")
  require(graphId != null, "graphId must not be null because it is an Option")

  def uri: String = FrameReference(id).uri

  def withSchema(newSchema: Schema) = this.copy(schema = newSchema)

  def isStatus(s: Status): Boolean = status == (s: Long)

  // TODO: we should be able to enable this check but it isn't working currently because 'lazy' removes graphId from old revisions --Todd 12/9/2014
  //if (isVertexFrame || isEdgeFrame) {
  //  require(graphId != None, "graphId is required for vertex and edge frames")
  //}

  def isVertexFrame: Boolean = schema.isInstanceOf[VertexSchema]

  def isEdgeFrame: Boolean = schema.isInstanceOf[EdgeSchema]

  /** Prefix used by plugin system */
  def entityType: String = {
    if (isVertexFrame) "frame:vertex"
    else if (isEdgeFrame) "frame:edge"
    else "frame:"
  }

  /** create a FrameReference for this frame */
  def toReference: FrameReference = {
    FrameReference(id)
  }

  /** label if this is a vertex or edge frame */
  def label: Option[String] = schema match {
    case v: VertexSchema => Some(v.label)
    case e: EdgeSchema => Some(e.label)
    case _ => None
  }

  /**
   * A minimal toString-like method for debugging messages
   */
  def toDebugString: String = {
    s"frameId: $id, name: $name, rowCount: $rowCount, storageFormat: $storageFormat, storageLocation: $storageLocation"
  }

  /** True if frame is stored in parquet file format */
  def isParquet: Boolean = {
    storageFormat.isDefined && storageFormat.get.equals(StorageFormats.FileParquet)
  }

  def getStorageLocation: String = {
    storageLocation.getOrElse(throw new RuntimeException(s"Storage location was not defined for $this"))
  }

  /**
   * Create a child frame from the current frame (a new revision)
   *
   * A child is basically a copy but not all fields are inherited from the parent
   *
   * @return the child
   */
  def createChild(createdBy: Option[Long], command: Option[Long], schema: Schema = FrameSchema()): FrameEntity = {
    // id will be auto-assigned on insert, initialize to zero
    copy(id = 0,
      name = None,
      status = Status.Active,
      schema = schema,
      rowCount = None,
      createdOn = new DateTime,
      createdBy = createdBy,
      modifiedOn = new DateTime,
      modifiedBy = None,
      materializedOn = None,
      materializationComplete = None,
      storageLocation = None,
      command = command,
      parent = Some(id),
      // TODO: for lazy graphId wouldn't be set to None here
      graphId = None)
  }

}

