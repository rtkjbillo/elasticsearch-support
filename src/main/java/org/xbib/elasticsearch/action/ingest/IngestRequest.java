package org.xbib.elasticsearch.action.ingest;

import org.elasticsearch.ElasticsearchIllegalArgumentException;
import org.elasticsearch.action.ActionRequest;
import org.elasticsearch.action.ActionRequestValidationException;
import org.elasticsearch.action.WriteConsistencyLevel;
import org.elasticsearch.action.delete.DeleteRequest;
import org.elasticsearch.action.index.IndexRequest;
import org.elasticsearch.action.support.replication.ReplicationType;
import org.elasticsearch.common.Nullable;
import org.elasticsearch.common.bytes.BytesArray;
import org.elasticsearch.common.bytes.BytesReference;
import org.elasticsearch.common.io.stream.StreamInput;
import org.elasticsearch.common.io.stream.StreamOutput;
import org.elasticsearch.common.unit.TimeValue;
import org.elasticsearch.common.xcontent.XContent;
import org.elasticsearch.common.xcontent.XContentFactory;
import org.elasticsearch.common.xcontent.XContentParser;
import org.elasticsearch.index.VersionType;

import java.io.IOException;
import java.util.Queue;
import java.util.concurrent.atomic.AtomicLong;

import static org.elasticsearch.action.ValidateActions.addValidationError;
import static org.elasticsearch.common.collect.Queues.newConcurrentLinkedQueue;

public class IngestRequest extends ActionRequest {

    private static final int REQUEST_OVERHEAD = 50;

    private final Queue<ActionRequest> requests = newQueue();

    private final AtomicLong sizeInBytes = new AtomicLong();

    private ReplicationType replicationType = ReplicationType.DEFAULT;

    private WriteConsistencyLevel consistencyLevel = WriteConsistencyLevel.DEFAULT;

    private TimeValue timeout = IngestShardRequest.DEFAULT_TIMEOUT;

    public Queue<ActionRequest> newQueue() {
        return newConcurrentLinkedQueue();
    }

    protected Queue<ActionRequest> requests() {
        return requests;
    }

    /**
     * Adds a list of requests to be executed. Either index or delete requests.
     */
    public IngestRequest add(ActionRequest... requests) {
        for (ActionRequest request : requests) {
            add(request);
        }
        return this;
    }

    public IngestRequest add(ActionRequest request) {
        if (request instanceof IndexRequest) {
            add((IndexRequest) request);
        } else if (request instanceof DeleteRequest) {
            add((DeleteRequest) request);
        } else {
            throw new ElasticsearchIllegalArgumentException("no support for request [" + request + "]");
        }
        return this;
    }

    /**
     * Adds a list of requests to be executed. Either index or delete requests.
     */
    public IngestRequest add(Iterable<ActionRequest> requests) {
        for (ActionRequest request : requests) {
            if (request instanceof IndexRequest) {
                add((IndexRequest) request);
            } else if (request instanceof DeleteRequest) {
                add((DeleteRequest) request);
            } else {
                throw new ElasticsearchIllegalArgumentException("no support for request [" + request + "]");
            }
        }
        return this;
    }

    /**
     * Adds an {@link org.elasticsearch.action.index.IndexRequest} to the list of actions to execute. Follows
     * the same behavior of {@link org.elasticsearch.action.index.IndexRequest} (for example, if no id is
     * provided, one will be generated, or usage of the create flag).
     */
    public IngestRequest add(IndexRequest request) {
        request.beforeLocalFork();
        return internalAdd(request);
    }

    IngestRequest internalAdd(IndexRequest request) {
        requests.offer(request);
        long length = request.source() != null ? request.source().length() + REQUEST_OVERHEAD : REQUEST_OVERHEAD;
        sizeInBytes.addAndGet(length);
        return this;
    }

    /**
     * Adds an {@link org.elasticsearch.action.delete.DeleteRequest} to the list of actions to execute.
     */
    public IngestRequest add(DeleteRequest request) {
        requests.offer(request);
        sizeInBytes.addAndGet(REQUEST_OVERHEAD);
        return this;
    }

    /**
     * The number of actions in the bulk request.
     */
    public int numberOfActions() {
        // for ConcurrentLinkedList, this call is not O(n), and may not be the size of the current list
        return requests.size();
    }

    /**
     * The estimated size in bytes of the bulk request.
     */
    public long estimatedSizeInBytes() {
        return sizeInBytes.longValue();
    }

    /**
     * Adds a framed data in binary format
     */
    public IngestRequest add(byte[] data, int from, int length, boolean contentUnsafe) throws Exception {
        return add(data, from, length, contentUnsafe, null, null);
    }

    /**
     * Adds a framed data in binary format
     */
    public IngestRequest add(byte[] data, int from, int length, boolean contentUnsafe, @Nullable String defaultIndex, @Nullable String defaultType) throws Exception {
        return add(new BytesArray(data, from, length), contentUnsafe, defaultIndex, defaultType);
    }

    /**
     * Adds a framed data in binary format
     */
    public IngestRequest add(BytesReference data, boolean contentUnsafe, @Nullable String defaultIndex, @Nullable String defaultType) throws Exception {
        XContent xContent = XContentFactory.xContent(data);
        int from = 0;
        int length = data.length();
        byte marker = xContent.streamSeparator();
        while (true) {
            int nextMarker = findNextMarker(marker, from, data, length);
            if (nextMarker == -1) {
                break;
            }
            // now parse the move
            XContentParser parser = xContent.createParser(data.slice(from, nextMarker - from));

            try {
                // move pointers
                from = nextMarker + 1;

                // Move to START_OBJECT
                XContentParser.Token token = parser.nextToken();
                if (token == null) {
                    continue;
                }
                assert token == XContentParser.Token.START_OBJECT;
                // Move to FIELD_NAME, that's the move
                token = parser.nextToken();
                assert token == XContentParser.Token.FIELD_NAME;
                String action = parser.currentName();

                String index = defaultIndex;
                String type = defaultType;
                String id = null;
                String routing = null;
                String parent = null;
                String timestamp = null;
                Long ttl = null;
                String opType = null;
                long version = 0;
                VersionType versionType = VersionType.INTERNAL;

                // at this stage, next token can either be END_OBJECT (and use default index and type, with auto generated id)
                // or START_OBJECT which will have another set of parameters

                String currentFieldName = null;
                while ((token = parser.nextToken()) != XContentParser.Token.END_OBJECT) {
                    if (token == XContentParser.Token.FIELD_NAME) {
                        currentFieldName = parser.currentName();
                    } else if (token.isValue()) {
                        if ("_index".equals(currentFieldName)) {
                            index = parser.text();
                        } else if ("_type".equals(currentFieldName)) {
                            type = parser.text();
                        } else if ("_id".equals(currentFieldName)) {
                            id = parser.text();
                        } else if ("_routing".equals(currentFieldName) || "routing".equals(currentFieldName)) {
                            routing = parser.text();
                        } else if ("_parent".equals(currentFieldName) || "parent".equals(currentFieldName)) {
                            parent = parser.text();
                        } else if ("_timestamp".equals(currentFieldName) || "timestamp".equals(currentFieldName)) {
                            timestamp = parser.text();
                        } else if ("_ttl".equals(currentFieldName) || "ttl".equals(currentFieldName)) {
                            if (parser.currentToken() == XContentParser.Token.VALUE_STRING) {
                                ttl = TimeValue.parseTimeValue(parser.text(), null).millis();
                            } else {
                                ttl = parser.longValue();
                            }
                        } else if ("op_type".equals(currentFieldName) || "opType".equals(currentFieldName)) {
                            opType = parser.text();
                        } else if ("_version".equals(currentFieldName) || "version".equals(currentFieldName)) {
                            version = parser.longValue();
                        } else if ("_version_type".equals(currentFieldName) || "_versionType".equals(currentFieldName) || "version_type".equals(currentFieldName) || "versionType".equals(currentFieldName)) {
                            versionType = VersionType.fromString(parser.text());
                        }
                    }
                }

                if ("delete".equals(action)) {
                    add(new DeleteRequest(index, type, id).parent(parent).version(version).versionType(versionType).routing(routing));
                } else {
                    nextMarker = findNextMarker(marker, from, data, length);
                    if (nextMarker == -1) {
                        break;
                    }
                    // order is important, we set parent after routing, so routing will be set to parent if not set explicitly
                    // we use internalAdd so we don't fork here, this allows us not to copy over the big byte array to small chunks
                    // of index request. All index requests are still unsafe if applicable.
                    if ("index".equals(action)) {
                        if (opType == null) {
                            internalAdd(new IndexRequest(index, type, id).routing(routing).parent(parent).timestamp(timestamp).ttl(ttl).version(version).versionType(versionType)
                                    .source(data.slice(from, nextMarker - from), contentUnsafe));
                        } else {
                            internalAdd(new IndexRequest(index, type, id).routing(routing).parent(parent).timestamp(timestamp).ttl(ttl).version(version).versionType(versionType)
                                    .create("create".equals(opType))
                                    .source(data.slice(from, nextMarker - from), contentUnsafe));
                        }
                    } else if ("create".equals(action)) {
                        internalAdd(new IndexRequest(index, type, id).routing(routing).parent(parent).timestamp(timestamp).ttl(ttl).version(version).versionType(versionType)
                                .create(true)
                                .source(data.slice(from, nextMarker - from), contentUnsafe));
                    }
                    // move pointers
                    from = nextMarker + 1;
                }
            } finally {
                parser.close();
            }
        }
        return this;
    }

    /**
     * Sets the consistency level of write. Defaults to
     * {@link org.elasticsearch.action.WriteConsistencyLevel#DEFAULT}
     */
    public IngestRequest consistencyLevel(WriteConsistencyLevel consistencyLevel) {
        this.consistencyLevel = consistencyLevel;
        return this;
    }

    public WriteConsistencyLevel consistencyLevel() {
        return this.consistencyLevel;
    }

    /**
     * Set the replication type for this operation.
     */
    public IngestRequest replicationType(ReplicationType replicationType) {
        this.replicationType = replicationType;
        return this;
    }

    public ReplicationType replicationType() {
        return this.replicationType;
    }

    /**
     * Set the timeout for this operation.
     */
    public IngestRequest timeout(TimeValue timeout) {
        this.timeout = timeout;
        return this;
    }

    public TimeValue timeout() {
        return this.timeout;
    }

    /**
     * Take all requests from queue. This method is thread safe.
     *
     * @return a bulk request
     */
    public IngestRequest takeAll() {
        IngestRequest request = new IngestRequest();
        while (!requests.isEmpty()) {
            ActionRequest actionRequest = requests.poll();
            request.add(actionRequest);
            if (actionRequest instanceof IndexRequest) {
                IndexRequest indexRequest = (IndexRequest) actionRequest;
                long length = indexRequest.source() != null ? indexRequest.source().length() + REQUEST_OVERHEAD : REQUEST_OVERHEAD;
                sizeInBytes.addAndGet(-length);
            } else if (actionRequest instanceof DeleteRequest) {
                sizeInBytes.addAndGet(REQUEST_OVERHEAD);
            }
        }
        return request;
    }

    /**
     * Take a number of requests from the bulk request queue.
     * <p/>
     * This method is thread safe.
     *
     * @param numRequests number of requests
     * @return a partial bulk request
     */
    public IngestRequest take(int numRequests) {
        IngestRequest request = new IngestRequest();
        for (int i = 0; i < numRequests; i++) {
            ActionRequest actionRequest = requests.poll();
            request.add(actionRequest);
            if (actionRequest instanceof IndexRequest) {
                IndexRequest indexRequest = (IndexRequest) actionRequest;
                long length = indexRequest.source() != null ? indexRequest.source().length() + REQUEST_OVERHEAD : REQUEST_OVERHEAD;
                sizeInBytes.addAndGet(-length);
            } else if (actionRequest instanceof DeleteRequest) {
                sizeInBytes.addAndGet(REQUEST_OVERHEAD);
            }
        }
        return request;
    }

    private int findNextMarker(byte marker, int from, BytesReference data, int length) {
        for (int i = from; i < length; i++) {
            if (data.get(i) == marker) {
                return i;
            }
        }
        return -1;
    }

    @Override
    public ActionRequestValidationException validate() {
        ActionRequestValidationException validationException = null;
        if (requests.isEmpty()) {
            validationException = addValidationError("no requests added", null);
        }
        for (ActionRequest request : requests) {
            ActionRequestValidationException ex = request.validate();
            if (ex != null) {
                if (validationException == null) {
                    validationException = new ActionRequestValidationException();
                }
                validationException.addValidationErrors(ex.validationErrors());
            }
        }
        return validationException;
    }

    @Override
    public void readFrom(StreamInput in) throws IOException {
        replicationType = ReplicationType.fromId(in.readByte());
        consistencyLevel = WriteConsistencyLevel.fromId(in.readByte());
        timeout = TimeValue.readTimeValue(in);
        int size = in.readVInt();
        for (int i = 0; i < size; i++) {
            byte type = in.readByte();
            if (type == 0) {
                IndexRequest request = new IndexRequest();
                request.readFrom(in);
                requests.add(request);
            } else if (type == 1) {
                DeleteRequest request = new DeleteRequest();
                request.readFrom(in);
                requests.add(request);
            }
        }
    }

    @Override
    public void writeTo(StreamOutput out) throws IOException {
        out.writeByte(replicationType.id());
        out.writeByte(consistencyLevel.id());
        timeout.writeTo(out);
        out.writeVInt(requests.size());
        for (ActionRequest request : requests) {
            if (request instanceof IndexRequest) {
                out.writeByte((byte) 0);
            } else if (request instanceof DeleteRequest) {
                out.writeByte((byte) 1);
            }
            request.writeTo(out);
        }
    }
}
