/*
 * Copyright (c) 2018, salesforce.com, inc.
 * All rights reserved.
 * Licensed under the BSD 3-Clause license.
 * For full license text, see LICENSE.txt file in the repo root  or https://opensource.org/licenses/BSD-3-Clause
 */

package com.salesforce.dynamodbv2.mt.mappers;

import static java.util.stream.Collectors.toList;

import com.amazonaws.services.dynamodbv2.AmazonDynamoDB;
import com.amazonaws.services.dynamodbv2.model.CreateTableRequest;
import com.amazonaws.services.dynamodbv2.model.CreateTableResult;
import com.amazonaws.services.dynamodbv2.model.DeleteItemRequest;
import com.amazonaws.services.dynamodbv2.model.DeleteItemResult;
import com.amazonaws.services.dynamodbv2.model.DeleteTableRequest;
import com.amazonaws.services.dynamodbv2.model.DeleteTableResult;
import com.amazonaws.services.dynamodbv2.model.DescribeTableRequest;
import com.amazonaws.services.dynamodbv2.model.DescribeTableResult;
import com.amazonaws.services.dynamodbv2.model.GetItemRequest;
import com.amazonaws.services.dynamodbv2.model.GetItemResult;
import com.amazonaws.services.dynamodbv2.model.PutItemRequest;
import com.amazonaws.services.dynamodbv2.model.PutItemResult;
import com.amazonaws.services.dynamodbv2.model.QueryRequest;
import com.amazonaws.services.dynamodbv2.model.QueryResult;
import com.amazonaws.services.dynamodbv2.model.Record;
import com.amazonaws.services.dynamodbv2.model.ScanRequest;
import com.amazonaws.services.dynamodbv2.model.ScanResult;
import com.amazonaws.services.dynamodbv2.model.StreamSpecification;
import com.amazonaws.services.dynamodbv2.model.UpdateItemRequest;
import com.amazonaws.services.dynamodbv2.model.UpdateItemResult;
import com.amazonaws.services.dynamodbv2.streamsadapter.model.RecordAdapter;
import com.amazonaws.services.kinesis.clientlibrary.interfaces.v2.IRecordProcessor;
import com.amazonaws.services.kinesis.clientlibrary.interfaces.v2.IRecordProcessorFactory;
import com.amazonaws.services.kinesis.clientlibrary.types.InitializationInput;
import com.amazonaws.services.kinesis.clientlibrary.types.ProcessRecordsInput;
import com.amazonaws.services.kinesis.clientlibrary.types.ShutdownInput;
import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.Preconditions;
import com.salesforce.dynamodbv2.mt.context.MtAmazonDynamoDbContextProvider;
import java.util.List;
import java.util.Optional;

/**
 * Allows for dividing tenants into their own tables by prefixing table names
 * with the multi-tenant context.
 *
 * <p>The multi-tenant context is separated from the table name by the delimiter,
 * which is '.' by default.
 *
 * <p>To use, call the static builder() method. The following parameters are
 * required ... - an AmazonDynamoDB instance - a multi-tenant context
 *
 * <p>The following are optional arguments ... - delimiter: a String delimiter used
 * to separate the tenant identifier prefix from the table name
 *
 * <p>Supported: create|describe|delete Table, get|putItem, scan, query
 *
 * @author msgroi
 */
public class MtAmazonDynamoDbByTable extends MtAmazonDynamoDbBase {

    private final String delimiter;
    private final Optional<String> tablePrefix;

    private MtAmazonDynamoDbByTable(MtAmazonDynamoDbContextProvider mtContext, AmazonDynamoDB amazonDynamoDb,
                                    String delimiter, Optional<String> tablePrefix) {
        super(mtContext, amazonDynamoDb);
        this.delimiter = delimiter;
        this.tablePrefix = tablePrefix;
    }

    /**
     * TODO: write Javadoc.
     */
    public CreateTableResult createTable(CreateTableRequest createTableRequest) {
        createTableRequest = createTableRequest.clone();
        createTableRequest.withTableName(buildPrefixedTablename(createTableRequest.getTableName()));
        return getAmazonDynamoDb().createTable(createTableRequest);
    }

    /**
     * TODO: write Javadoc.
     */
    public DeleteItemResult deleteItem(DeleteItemRequest deleteItemRequest) {
        deleteItemRequest = deleteItemRequest.clone();
        deleteItemRequest.withTableName(buildPrefixedTablename(deleteItemRequest.getTableName()));
        return getAmazonDynamoDb().deleteItem(deleteItemRequest);
    }

    /**
     * TODO: write Javadoc.
     */
    public DeleteTableResult deleteTable(DeleteTableRequest deleteTableRequest) {
        String virtualTableName = deleteTableRequest.getTableName();
        deleteTableRequest = deleteTableRequest.clone();
        deleteTableRequest.withTableName(buildPrefixedTablename(deleteTableRequest.getTableName()));
        DeleteTableResult deleteTableResult = getAmazonDynamoDb().deleteTable(deleteTableRequest);
        deleteTableResult.getTableDescription().setTableName(virtualTableName);
        return deleteTableResult;
    }

    /**
     * TODO: write Javadoc.
     */
    public DescribeTableResult describeTable(DescribeTableRequest describeTableRequest) {
        String virtualTableName = describeTableRequest.getTableName();
        describeTableRequest = describeTableRequest.clone();
        describeTableRequest.withTableName(buildPrefixedTablename(describeTableRequest.getTableName()));
        DescribeTableResult describeTableResult = getAmazonDynamoDb().describeTable(describeTableRequest);
        describeTableResult.getTable().setTableName(virtualTableName);
        return describeTableResult;
    }

    /**
     * TODO: write Javadoc.
     */
    public GetItemResult getItem(GetItemRequest getItemRequest) {
        getItemRequest = getItemRequest.clone();
        String prefixedTableName = buildPrefixedTablename(getItemRequest.getTableName());
        getItemRequest.withTableName(prefixedTableName);
        return getAmazonDynamoDb().getItem(getItemRequest);
    }

    /**
     * TODO: write Javadoc.
     */
    public PutItemResult putItem(PutItemRequest putItemRequest) {
        putItemRequest = putItemRequest.clone();
        putItemRequest.withTableName(buildPrefixedTablename(putItemRequest.getTableName()));
        return getAmazonDynamoDb().putItem(putItemRequest);
    }

    /**
     * TODO: write Javadoc.
     */
    public QueryResult query(QueryRequest queryRequest) {
        queryRequest = queryRequest.clone();
        queryRequest.withTableName(buildPrefixedTablename(queryRequest.getTableName()));
        return getAmazonDynamoDb().query(queryRequest);
    }

    /**
     * TODO: write Javadoc.
     */
    public ScanResult scan(ScanRequest scanRequest) {
        scanRequest = scanRequest.clone();
        scanRequest.withTableName(buildPrefixedTablename(scanRequest.getTableName()));
        return getAmazonDynamoDb().scan(scanRequest);
    }

    /**
     * TODO: write Javadoc.
     */
    public UpdateItemResult updateItem(UpdateItemRequest updateItemRequest) {
        updateItemRequest = updateItemRequest.clone();
        updateItemRequest.withTableName(buildPrefixedTablename(updateItemRequest.getTableName()));
        return getAmazonDynamoDb().updateItem(updateItemRequest);
    }

    // TODO assumes prefix does not contain delimiter
    // TODO assumes everything that starts with prefix is in fact an MT table (ok?)
    // TODO assumes context does not contain delimiter
    @Override
    public List<MtStreamDescription> listStreams(IRecordProcessorFactory factory) {
        String prefix = tablePrefix.orElse("");
        return listAllTables().stream() //
            .filter(n -> n.startsWith(prefix) && n.indexOf(delimiter, prefix.length()) >= 0) //
            .map(n -> getAmazonDynamoDb().describeTable(n).getTable()) // TODO handle table not exists
            .filter(d -> Optional.ofNullable(d.getStreamSpecification()).map(StreamSpecification::isStreamEnabled)
                .orElse(false)) // only include tables with streaming enabled
            .map(d -> new MtStreamDescription() //
                .withLabel(d.getTableName()) // use raw name as label
                .withArn(d.getLatestStreamArn()) //
                .withRecordProcessorFactory(newAdapter(factory, d.getTableName().substring(prefix.length())))) //
            .collect(toList());
    }

    private IRecordProcessorFactory newAdapter(IRecordProcessorFactory factory, String tableName) {
        int idx = tableName.indexOf(delimiter);
        String tenant = tableName.substring(0, idx);
        String name = tableName.substring(idx + delimiter.length(), tableName.length());
        return () -> new RecordProcessor(tenant, name, factory.createProcessor());
    }

    private static class RecordProcessor implements IRecordProcessor {
        private final String tenant;
        private final String tableName;
        private final IRecordProcessor processor;

        RecordProcessor(String tenant, String tableName, IRecordProcessor processor) {
            this.tenant = tenant;
            this.tableName = tableName;
            this.processor = processor;
        }

        @Override
        public void initialize(InitializationInput initializationInput) {
            processor.initialize(initializationInput);
        }

        @Override
        public void processRecords(ProcessRecordsInput processRecordsInput) {
            List<com.amazonaws.services.kinesis.model.Record> records = processRecordsInput.getRecords().stream()
                .map(RecordAdapter.class::cast).map(this::toMtRecord).collect(toList());
            processor.processRecords(processRecordsInput.withRecords(records));
        }

        private com.amazonaws.services.kinesis.model.Record toMtRecord(RecordAdapter adapter) {
            Record r = adapter.getInternalObject();
            return new RecordAdapter(new MtRecord() //
                .withAwsRegion(r.getAwsRegion()) //
                .withDynamodb(r.getDynamodb()) //
                .withEventID(r.getEventID()) //
                .withEventName(r.getEventName()) //
                .withEventSource(r.getEventSource()) //
                .withEventVersion(r.getEventVersion()) //
                .withContext(tenant) //
                .withTableName(tableName));
        }

        @Override
        public void shutdown(ShutdownInput shutdownInput) {
            processor.shutdown(shutdownInput);
        }

    }

    public static MtAmazonDynamoDbBuilder builder() {
        return new MtAmazonDynamoDbBuilder();
    }

    public static class MtAmazonDynamoDbBuilder {

        private AmazonDynamoDB amazonDynamoDb;
        private MtAmazonDynamoDbContextProvider mtContext;
        private String delimiter;
        private Optional<String> tablePrefix;

        public MtAmazonDynamoDbBuilder withAmazonDynamoDb(AmazonDynamoDB amazonDynamoDb) {
            this.amazonDynamoDb = amazonDynamoDb;
            return this;
        }

        public MtAmazonDynamoDbBuilder withContext(MtAmazonDynamoDbContextProvider mtContext) {
            this.mtContext = mtContext;
            return this;
        }

        MtAmazonDynamoDbBuilder withDelimiter(String delimiter) {
            this.delimiter = delimiter;
            return this;
        }

        public MtAmazonDynamoDbBuilder withTablePrefix(String tablePrefix) {
            this.tablePrefix = Optional.of(tablePrefix);
            return this;
        }

        /**
         * TODO: write Javadoc.
         */
        public MtAmazonDynamoDbByTable build() {
            setDefaults();
            Preconditions.checkNotNull(amazonDynamoDb, "amazonDynamoDb is required");
            Preconditions.checkNotNull(mtContext, "mtContext is required");
            return new MtAmazonDynamoDbByTable(mtContext, amazonDynamoDb, delimiter, tablePrefix);
        }

        private void setDefaults() {
            if (delimiter == null) {
                delimiter = ".";
            }
            if (tablePrefix == null) {
                tablePrefix = Optional.empty();
            }
        }

    }

    @VisibleForTesting
    String buildPrefixedTablename(String virtualTablename) {
        return (tablePrefix.orElse("")) + getMtContext().getContext() + delimiter + virtualTablename;
    }

}
