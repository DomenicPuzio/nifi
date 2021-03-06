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

package org.apache.nifi.cluster.coordination.http.replication;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import java.io.ByteArrayInputStream;
import java.net.SocketTimeoutException;
import java.net.URI;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import javax.ws.rs.HttpMethod;

import org.apache.nifi.cluster.coordination.ClusterCoordinator;
import org.apache.nifi.cluster.flow.DataFlowManagementService;
import org.apache.nifi.cluster.manager.NodeResponse;
import org.apache.nifi.cluster.manager.exception.IllegalClusterStateException;
import org.apache.nifi.cluster.protocol.NodeIdentifier;
import org.apache.nifi.util.NiFiProperties;
import org.apache.nifi.web.StandardOptimisticLockingManager;
import org.apache.nifi.web.api.entity.Entity;
import org.apache.nifi.web.api.entity.ProcessorEntity;
import org.junit.Assert;
import org.junit.BeforeClass;
import org.junit.Test;
import org.mockito.Mockito;
import org.mockito.internal.util.reflection.Whitebox;

import com.sun.jersey.api.client.Client;
import com.sun.jersey.api.client.ClientHandlerException;
import com.sun.jersey.api.client.ClientResponse;
import com.sun.jersey.api.client.ClientResponse.Status;
import com.sun.jersey.api.client.WebResource;
import com.sun.jersey.core.header.InBoundHeaders;
import com.sun.jersey.core.header.OutBoundHeaders;

public class TestThreadPoolRequestReplicator {

    @BeforeClass
    public static void setupClass() {
        System.setProperty(NiFiProperties.PROPERTIES_FILE_PATH, "src/test/resources/conf/nifi.properties");
    }

    /**
     * If we replicate a request, whenever we obtain the merged response from the AsyncClusterResponse object,
     * the response should no longer be available and should be cleared from internal state. This test is to
     * verify that this behavior occurs.
     */
    @Test
    public void testResponseRemovedWhenCompletedAndFetched() {
        withReplicator(replicator -> {
            final Set<NodeIdentifier> nodeIds = new HashSet<>();
            nodeIds.add(new NodeIdentifier("1", "localhost", 8000, "localhost", 8001, "localhost", 8002, false));
            final URI uri = new URI("http://localhost:8080/processors/1");
            final Entity entity = new ProcessorEntity();

            final AsyncClusterResponse response = replicator.replicate(nodeIds, HttpMethod.GET, uri, entity, new HashMap<>());

            // We should get back the same response object
            assertTrue(response == replicator.getClusterResponse(response.getRequestIdentifier()));

            assertEquals(HttpMethod.GET, response.getMethod());
            assertEquals(nodeIds, response.getNodesInvolved());

            assertTrue(response == replicator.getClusterResponse(response.getRequestIdentifier()));

            final NodeResponse nodeResponse = response.awaitMergedResponse(3, TimeUnit.SECONDS);
            assertEquals(8000, nodeResponse.getNodeId().getApiPort());
            assertEquals(ClientResponse.Status.OK.getStatusCode(), nodeResponse.getStatus());

            assertNull(replicator.getClusterResponse(response.getRequestIdentifier()));
        });
    }


    @Test(timeout = 15000)
    public void testLongWaitForResponse() {
        withReplicator(replicator -> {
            final Set<NodeIdentifier> nodeIds = new HashSet<>();
            final NodeIdentifier nodeId = new NodeIdentifier("1", "localhost", 8000, "localhost", 8001, "localhost", 8002, false);
            nodeIds.add(nodeId);
            final URI uri = new URI("http://localhost:8080/processors/1");
            final Entity entity = new ProcessorEntity();

            final AsyncClusterResponse response = replicator.replicate(nodeIds, HttpMethod.GET, uri, entity, new HashMap<>());

            // We should get back the same response object
            assertTrue(response == replicator.getClusterResponse(response.getRequestIdentifier()));
            assertFalse(response.isComplete());

            final NodeResponse nodeResponse = response.getNodeResponse(nodeId);
            assertNull(nodeResponse);

            final NodeResponse completedNodeResponse = response.awaitMergedResponse(2, TimeUnit.SECONDS);
            assertNotNull(completedNodeResponse);
            assertNotNull(completedNodeResponse.getThrowable());
            assertEquals(500, completedNodeResponse.getStatus());

            assertTrue(response.isComplete());
            assertNotNull(response.getMergedResponse());
            assertNull(replicator.getClusterResponse(response.getRequestIdentifier()));
        } , Status.OK, 1000, new ClientHandlerException(new SocketTimeoutException()));
    }

    @Test(timeout = 15000)
    public void testCompleteOnError() {
        withReplicator(replicator -> {
            final Set<NodeIdentifier> nodeIds = new HashSet<>();
            final NodeIdentifier id1 = new NodeIdentifier("1", "localhost", 8100, "localhost", 8101, "localhost", 8102, false);
            final NodeIdentifier id2 = new NodeIdentifier("2", "localhost", 8200, "localhost", 8201, "localhost", 8202, false);
            final NodeIdentifier id3 = new NodeIdentifier("3", "localhost", 8300, "localhost", 8301, "localhost", 8302, false);
            final NodeIdentifier id4 = new NodeIdentifier("4", "localhost", 8400, "localhost", 8401, "localhost", 8402, false);
            nodeIds.add(id1);
            nodeIds.add(id2);
            nodeIds.add(id3);
            nodeIds.add(id4);

            final URI uri = new URI("http://localhost:8080/processors/1");
            final Entity entity = new ProcessorEntity();

            final AsyncClusterResponse response = replicator.replicate(nodeIds, HttpMethod.GET, uri, entity, new HashMap<>());
            assertNotNull(response.awaitMergedResponse(1, TimeUnit.SECONDS));
        } , null, 0L, new IllegalArgumentException("Exception created for unit test"));
    }


    @Test(timeout = 15000)
    public void testMultipleRequestWithTwoPhaseCommit() {
        final Set<NodeIdentifier> nodeIds = new HashSet<>();
        nodeIds.add(new NodeIdentifier("1", "localhost", 8100, "localhost", 8101, "localhost", 8102, false));

        final ClusterCoordinator coordinator = Mockito.mock(ClusterCoordinator.class);

        final AtomicInteger requestCount = new AtomicInteger(0);
        final DataFlowManagementService dfmService = Mockito.mock(DataFlowManagementService.class);
        final ThreadPoolRequestReplicator replicator = new ThreadPoolRequestReplicator(2, new Client(), coordinator,
            "1 sec", "1 sec", null, null, null, new StandardOptimisticLockingManager(), dfmService) {
            @Override
            protected NodeResponse replicateRequest(final WebResource.Builder resourceBuilder, final NodeIdentifier nodeId, final String method, final URI uri, final String requestId) {
                // the resource builder will not expose its headers to us, so we are using Mockito's Whitebox class to extract them.
                final OutBoundHeaders headers = (OutBoundHeaders) Whitebox.getInternalState(resourceBuilder, "metadata");
                final Object expectsHeader = headers.getFirst(ThreadPoolRequestReplicator.NCM_EXPECTS_HTTP_HEADER);

                final int statusCode;
                if (requestCount.incrementAndGet() == 1) {
                    assertEquals(ThreadPoolRequestReplicator.NODE_CONTINUE, expectsHeader);
                    statusCode = 150;
                } else {
                    assertNull(expectsHeader);
                    statusCode = Status.OK.getStatusCode();
                }

                // Return given response from all nodes.
                final ClientResponse clientResponse = new ClientResponse(statusCode, new InBoundHeaders(), new ByteArrayInputStream(new byte[0]), null);
                return new NodeResponse(nodeId, method, uri, clientResponse, -1L, requestId);
            }
        };

        replicator.start();

        try {
            final AsyncClusterResponse clusterResponse = replicator.replicate(nodeIds, HttpMethod.POST,
                new URI("http://localhost:80/processors/1"), new ProcessorEntity(), new HashMap<>());
            clusterResponse.awaitMergedResponse();

            // Ensure that we received two requests - the first should contain the X-NcmExpects header; the second should not.
            // These assertions are validated above, in the overridden replicateRequest method.
            assertEquals(2, requestCount.get());
        } catch (final Exception e) {
            e.printStackTrace();
            Assert.fail(e.toString());
        } finally {
            replicator.stop();
        }
    }


    @Test(timeout = 15000)
    public void testOneNodeRejectsTwoPhaseCommit() {
        final Set<NodeIdentifier> nodeIds = new HashSet<>();
        nodeIds.add(new NodeIdentifier("1", "localhost", 8100, "localhost", 8101, "localhost", 8102, false));
        nodeIds.add(new NodeIdentifier("2", "localhost", 8200, "localhost", 8201, "localhost", 8202, false));

        final ClusterCoordinator coordinator = Mockito.mock(ClusterCoordinator.class);

        final AtomicInteger requestCount = new AtomicInteger(0);
        final DataFlowManagementService dfmService = Mockito.mock(DataFlowManagementService.class);
        final ThreadPoolRequestReplicator replicator = new ThreadPoolRequestReplicator(2, new Client(), coordinator,
            "1 sec", "1 sec", null, null, null, new StandardOptimisticLockingManager(), dfmService) {
            @Override
            protected NodeResponse replicateRequest(final WebResource.Builder resourceBuilder, final NodeIdentifier nodeId, final String method, final URI uri, final String requestId) {
                // the resource builder will not expose its headers to us, so we are using Mockito's Whitebox class to extract them.
                final OutBoundHeaders headers = (OutBoundHeaders) Whitebox.getInternalState(resourceBuilder, "metadata");
                final Object expectsHeader = headers.getFirst(ThreadPoolRequestReplicator.NCM_EXPECTS_HTTP_HEADER);

                final int requestIndex = requestCount.incrementAndGet();
                assertEquals(ThreadPoolRequestReplicator.NODE_CONTINUE, expectsHeader);

                if (requestIndex == 1) {
                    final ClientResponse clientResponse = new ClientResponse(150, new InBoundHeaders(), new ByteArrayInputStream(new byte[0]), null);
                    return new NodeResponse(nodeId, method, uri, clientResponse, -1L, requestId);
                } else {
                    final IllegalClusterStateException explanation = new IllegalClusterStateException("Intentional Exception for Unit Testing");
                    return new NodeResponse(nodeId, method, uri, explanation);
                }
            }
        };

        replicator.start();

        try {
            final AsyncClusterResponse clusterResponse = replicator.replicate(nodeIds, HttpMethod.POST,
                new URI("http://localhost:80/processors/1"), new ProcessorEntity(), new HashMap<>());
            clusterResponse.awaitMergedResponse();

            Assert.fail("Expected to get an IllegalClusterStateException but did not");
        } catch (final IllegalClusterStateException e) {
            // Expected
        } catch (final Exception e) {
            Assert.fail(e.toString());
        } finally {
            replicator.stop();
        }
    }



    private void withReplicator(final WithReplicator function) {
        withReplicator(function, ClientResponse.Status.OK, 0L, null);
    }

    private void withReplicator(final WithReplicator function, final Status status, final long delayMillis, final RuntimeException failure) {
        final ClusterCoordinator coordinator = Mockito.mock(ClusterCoordinator.class);

        final DataFlowManagementService dfmService = Mockito.mock(DataFlowManagementService.class);
        final ThreadPoolRequestReplicator replicator = new ThreadPoolRequestReplicator(2, new Client(), coordinator,
            "1 sec", "1 sec", null, null, null, new StandardOptimisticLockingManager(), dfmService) {
            @Override
            protected NodeResponse replicateRequest(final WebResource.Builder resourceBuilder, final NodeIdentifier nodeId, final String method, final URI uri, final String requestId) {
                if (delayMillis > 0L) {
                    try {
                        Thread.sleep(delayMillis);
                    } catch (InterruptedException e) {
                        Assert.fail("Thread Interrupted durating test");
                    }
                }

                if (failure != null) {
                    throw failure;
                }

                // Return given response from all nodes.
                final ClientResponse clientResponse = new ClientResponse(status, new InBoundHeaders(), new ByteArrayInputStream(new byte[0]), null);
                return new NodeResponse(nodeId, method, uri, clientResponse, -1L, requestId);
            }
        };

        replicator.start();

        try {
            function.withReplicator(replicator);
        } catch (final Exception e) {
            e.printStackTrace();
            Assert.fail(e.toString());
        } finally {
            replicator.stop();
        }
    }

    private interface WithReplicator {
        void withReplicator(ThreadPoolRequestReplicator replicator) throws Exception;
    }
}
