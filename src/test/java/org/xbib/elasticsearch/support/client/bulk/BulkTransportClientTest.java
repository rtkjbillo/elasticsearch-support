
package org.xbib.elasticsearch.support.client.bulk;

import org.elasticsearch.client.transport.NoNodeAvailableException;
import org.elasticsearch.common.logging.ESLogger;
import org.elasticsearch.common.logging.ESLoggerFactory;
import org.elasticsearch.common.unit.TimeValue;
import org.elasticsearch.common.util.concurrent.EsExecutors;
import org.elasticsearch.index.query.QueryBuilders;
import org.xbib.elasticsearch.support.helper.AbstractNodeRandomTestHelper;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

import org.junit.Test;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;

public class BulkTransportClientTest extends AbstractNodeRandomTestHelper {

    private final static ESLogger logger = ESLoggerFactory.getLogger(BulkTransportClientTest.class.getName());

    @Test
    public void testBulkClient() {
        final BulkTransportClient client = new BulkTransportClient()
                .flushInterval(TimeValue.timeValueSeconds(5))
                .newClient(getAddress())
                .newIndex("test");
        if (client.hasThrowable()) {
            logger.error("error", client.getThrowable());
        }
        assertFalse(client.hasThrowable());
        try {
            client.deleteIndex("test")
              .newIndex("test")
              .deleteIndex("test");
        } catch (NoNodeAvailableException e) {
            logger.error("no node available");
        } finally {
            if (client.hasThrowable()) {
                logger.error("error", client.getThrowable());
            }
            assertFalse(client.hasThrowable());
            client.shutdown();
        }
    }

    @Test
    public void testSingleDocBulkClient() {
        final BulkTransportClient client = new BulkTransportClient()
                .maxActionsPerBulkRequest(1000)
                .flushInterval(TimeValue.timeValueSeconds(5))
                .newClient(getAddress())
                .newIndex("test");
        try {
            client.deleteIndex("test");
            client.newIndex("test");
            client.index("test", "test", "1", "{ \"name\" : \"Jörg Prante\"}"); // single doc ingest
            client.flush();
            client.waitForResponses(TimeValue.timeValueSeconds(30));
        } catch (InterruptedException e) {
            // ignore
        } catch (NoNodeAvailableException e) {
            logger.warn("skipping, no node available");
        } finally {
            logger.info("bulk requests = {}", client.getState().getTotalIngest().count());
            assertEquals(1, client.getState().getTotalIngest().count());
            if (client.hasThrowable()) {
                logger.error("error", client.getThrowable());
            }
            assertFalse(client.hasThrowable());
            client.shutdown();
        }
    }

    @Test
    public void testRandomDocsBulkClient() {
        final BulkTransportClient client = new BulkTransportClient()
                .maxActionsPerBulkRequest(1000)
                .flushInterval(TimeValue.timeValueSeconds(10))
                .newClient(getAddress())
                .newIndex("test");
        try {
            for (int i = 0; i < 12345; i++) {
                client.index("test", "test", null, "{ \"name\" : \"" + randomString(32) + "\"}");
            }
            client.flush();
            client.waitForResponses(TimeValue.timeValueSeconds(30));
        } catch (InterruptedException e) {
            // ignore
        } catch (NoNodeAvailableException e) {
            logger.warn("skipping, no node available");
        } finally {
            logger.info("bulk requests = {}", client.getState().getTotalIngest().count());
            assertEquals(13, client.getState().getTotalIngest().count(), 13);
            if (client.hasThrowable()) {
                logger.error("error", client.getThrowable());
            }
            assertFalse(client.hasThrowable());
            client.shutdown();
        }
    }

    @Test
    public void testThreadedRandomDocsBulkClient() throws Exception {
        int max = Runtime.getRuntime().availableProcessors();
        int maxactions = 1000;
        final int maxloop = 12345;

        final BulkTransportClient client = new BulkTransportClient()
                .flushInterval(TimeValue.timeValueSeconds(600)) // = disable autoflush for this test
                .maxActionsPerBulkRequest(maxactions)
                .newClient(getAddress())
                .newIndex("test")
                .startBulk("test");
        try {
            ThreadPoolExecutor pool = EsExecutors.newFixed(max, 30,
                    EsExecutors.daemonThreadFactory("bulkclient-test"));
            final CountDownLatch latch = new CountDownLatch(max);
            for (int i = 0; i < max; i++) {
                pool.execute(new Runnable() {
                    public void run() {
                        for (int i = 0; i < maxloop; i++) {
                            client.index("test", "test", null, "{ \"name\" : \"" + randomString(32) + "\"}");
                        }
                        latch.countDown();
                    }
                });
            }
            logger.info("waiting for max 60 seconds...");
            latch.await(60, TimeUnit.SECONDS);
            logger.info("client flush ...");
            client.flush();
            client.waitForResponses(TimeValue.timeValueSeconds(60));
            logger.info("test thread pool to be shut down ...");
            pool.shutdown();
            logger.info("poot shut down");
        } catch (NoNodeAvailableException e) {
            logger.warn("skipping, no node available");
        } finally {
            client.stopBulk("test");
            logger.info("bulk requests = {}", client.getState().getTotalIngest().count() );
            assertEquals(max * maxloop / maxactions + 1, client.getState().getTotalIngest().count());
            assertFalse(client.hasThrowable());
            client.refresh("test");
            assertEquals(max * maxloop,
                    client.client().prepareCount("test").setQuery(QueryBuilders.matchAllQuery()).execute().actionGet().getCount()
            );
            client.shutdown();
        }
    }

}
