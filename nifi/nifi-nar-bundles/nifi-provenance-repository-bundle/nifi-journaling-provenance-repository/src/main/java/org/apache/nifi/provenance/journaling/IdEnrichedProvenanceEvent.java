/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.apache.nifi.provenance.journaling;

import java.util.List;
import java.util.Map;
import java.util.Set;

import org.apache.nifi.provenance.ProvenanceEventRecord;
import org.apache.nifi.provenance.ProvenanceEventType;


/**
 * Wraps a Provenance Event Record but provides a different Event ID for the record. This is done because we
 * do not want to allow the ProvenanceEventBuilder to expose an identifier, because the identifier should be
 * assigned only when the event has been persisted to the repository.
 */
public class IdEnrichedProvenanceEvent implements ProvenanceEventRecord {
    private final ProvenanceEventRecord event;
    private final long id;
    
    public IdEnrichedProvenanceEvent(final ProvenanceEventRecord event, final long id) {
        this.event = event;
        this.id = id;
    }

    @Override
    public long getEventId() {
        return id;
    }
    
    public long getEventTime() {
        return event.getEventTime();
    }

    public long getFlowFileEntryDate() {
        return event.getFlowFileEntryDate();
    }

    public long getLineageStartDate() {
        return event.getLineageStartDate();
    }

    public Set<String> getLineageIdentifiers() {
        return event.getLineageIdentifiers();
    }

    public long getFileSize() {
        return event.getFileSize();
    }

    public Long getPreviousFileSize() {
        return event.getPreviousFileSize();
    }

    public long getEventDuration() {
        return event.getEventDuration();
    }

    public ProvenanceEventType getEventType() {
        return event.getEventType();
    }

    public Map<String, String> getAttributes() {
        return event.getAttributes();
    }

    public Map<String, String> getPreviousAttributes() {
        return event.getPreviousAttributes();
    }

    public Map<String, String> getUpdatedAttributes() {
        return event.getUpdatedAttributes();
    }

    public String getComponentId() {
        return event.getComponentId();
    }

    public String getComponentType() {
        return event.getComponentType();
    }

    public String getTransitUri() {
        return event.getTransitUri();
    }

    public String getSourceSystemFlowFileIdentifier() {
        return event.getSourceSystemFlowFileIdentifier();
    }

    public String getFlowFileUuid() {
        return event.getFlowFileUuid();
    }

    public List<String> getParentUuids() {
        return event.getParentUuids();
    }

    public List<String> getChildUuids() {
        return event.getChildUuids();
    }

    public String getAlternateIdentifierUri() {
        return event.getAlternateIdentifierUri();
    }

    public String getDetails() {
        return event.getDetails();
    }

    public String getRelationship() {
        return event.getRelationship();
    }

    public String getSourceQueueIdentifier() {
        return event.getSourceQueueIdentifier();
    }

    public String getContentClaimSection() {
        return event.getContentClaimSection();
    }

    public String getPreviousContentClaimSection() {
        return event.getPreviousContentClaimSection();
    }

    public String getContentClaimContainer() {
        return event.getContentClaimContainer();
    }

    public String getPreviousContentClaimContainer() {
        return event.getPreviousContentClaimContainer();
    }

    public String getContentClaimIdentifier() {
        return event.getContentClaimIdentifier();
    }

    public String getPreviousContentClaimIdentifier() {
        return event.getPreviousContentClaimIdentifier();
    }

    public Long getContentClaimOffset() {
        return event.getContentClaimOffset();
    }

    public Long getPreviousContentClaimOffset() {
        return event.getPreviousContentClaimOffset();
    }
}