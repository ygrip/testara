package io.github.ygrip.testara.database.nosql;

import com.fasterxml.jackson.core.type.TypeReference;
import io.github.ygrip.testara.core.context.TestComponent;
import io.github.ygrip.testara.core.mapper.MapperHelper;
import io.github.ygrip.testara.core.registry.RegistryScope;
import io.github.ygrip.testara.core.support.CommonHelper;
import io.github.ygrip.testara.database.config.MongoProperties;
import io.github.ygrip.testara.database.model.MongoDbConnection;
import io.github.ygrip.testara.database.model.MongoModel;
import io.github.ygrip.testara.database.support.AwaitStream;
import com.mongodb.ConnectionString;
import com.mongodb.MongoBulkWriteException;
import com.mongodb.MongoClientSettings;
import com.mongodb.MongoCredential;
import com.mongodb.ServerAddress;
import com.mongodb.client.model.Filters;
import com.mongodb.client.result.DeleteResult;
import com.mongodb.client.result.InsertManyResult;
import com.mongodb.client.result.InsertOneResult;
import com.mongodb.client.result.UpdateResult;
import com.mongodb.reactivestreams.client.MongoCollection;
import com.mongodb.reactivestreams.client.MongoDatabase;
import lombok.extern.log4j.Log4j2;
import org.bson.BsonArray;
import org.bson.BsonDocument;
import org.bson.BsonValue;
import org.bson.Document;
import org.bson.codecs.BsonArrayCodec;
import org.bson.codecs.BsonDocumentCodec;
import org.bson.codecs.BsonValueCodecProvider;
import org.bson.codecs.DecoderContext;
import org.bson.codecs.DocumentCodecProvider;
import org.bson.codecs.ValueCodecProvider;
import org.bson.codecs.configuration.CodecRegistries;
import org.bson.codecs.configuration.CodecRegistry;
import org.bson.codecs.pojo.PojoCodecProvider;
import org.bson.json.JsonReader;
import org.bson.json.JsonWriterSettings;

import java.net.UnknownServiceException;
import java.security.InvalidParameterException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

import static io.github.ygrip.testara.core.support.CommonHelper.isBlank;


/**
 * <p>MongoHelperImpl class.</p>
 *
 * @author yunaz.ramadhan on 10/4/2019
 * @version $Id: $Id
 */
@TestComponent(scope = RegistryScope.TEST)
@Log4j2
public class MongoHelperImpl implements MongoHelper {
  private final Map<String, MongoModel> properties;
  private final CodecRegistry registry;
  private final Map<String, MongoDbConnection> connections;
  private String currentService;
  private String currentCollection;

  /**
   * <p>Constructor for MongoHelperImpl.</p>
   *
   * @param properties a {@link io.github.ygrip.testara.database.config.MongoProperties} object.
   */
  public MongoHelperImpl(MongoProperties properties) {
    this.properties = properties.getService() == null ? new HashMap<>() : new HashMap<>(properties.getService());
    this.connections = new HashMap<>();
    CodecRegistry pojoCodecRegistry = CodecRegistries.fromProviders(Arrays.asList(new ValueCodecProvider(),
        new BsonValueCodecProvider(),
        new DocumentCodecProvider(),
        PojoCodecProvider.builder().automatic(true).build()));
    this.registry = CodecRegistries.fromRegistries(MongoClientSettings.getDefaultCodecRegistry(), pojoCodecRegistry);
    if (properties.isPreEmptiveConnectionEnabled()) {
      initializeAtStartUp();
      this.currentService = null;
    }
  }

  private void initializeAtStartUp() {
    log.info("#Mongo use pre-emptive connection, try to connect to all defined database in properties");
    Set<String> services = this.properties.keySet();
    for (String service : services) {
      try {
        init(service);
      } catch (Exception ignored) {

      }
    }
  }

  /**
   * {@inheritDoc}
   */
  @Override
  public MongoHelper init(String service) throws Exception {
    service = service.trim();
    MongoModel props = properties.getOrDefault(service, null);
    if (props == null) {
      throw new UnknownServiceException("#Cannot found mongo properties for " + service);
    } else if (isBlank(props.getDbName())) {
      throw new InvalidParameterException("#Mongo database name should not be empty for " + service);
    }
    if (!this.connections.containsKey(service)) {
      try {
        log.debug("#Establishing connection to {} mongodb", service);
        String connectionString;

        MongoClientSettings.Builder setting = MongoClientSettings.builder().codecRegistry(this.registry);
        if (props.getConnectionString() != null) {
          connectionString = props.getConnectionString();
          if (!connectionString.startsWith("mongodb://") && !connectionString.startsWith("mongodb+srv://")) {
            throw new InvalidParameterException("Invalid connection string format, must start with mongodb:// or mongodb+srv://");
          }
        } else {
          MongoCredential credential = MongoCredential.createCredential(props.getUsername(),
              props.getDbName(),
              props.getPassword().toCharArray());
          if (props.getAuthMechanism() != null) {
            credential.withMechanism(props.getAuthMechanism());
          }
          setting.credential(credential);
          String connectionPrefix;
          if (props.isUseDnsSeed()) {
            connectionPrefix = "mongodb+srv://";
          } else {
            connectionPrefix = "mongodb://";
          }
          connectionString =
              props.getHosts().startsWith(connectionPrefix) ? props.getHosts() : connectionPrefix + props.getHosts();
          if (props.getWriteConcern() != null) {
            setting.writeConcern(props.getWriteConcern());
          }
        }
        setting.retryReads(props.isRetryReads());
        setting.retryWrites(props.isRetryWrites());

        ConnectionString connectionStringSetting = new ConnectionString(connectionString);
        setting.applyConnectionString(connectionStringSetting);
        if (props.isSslEnabled()) {
          setting.applyToSslSettings(builder -> builder.enabled(true));
        }
        setting.applyToSocketSettings(builder -> builder.connectTimeout(props.getSocketTimeoutMs(),
            TimeUnit.MILLISECONDS).readTimeout(props.getConnectionTimeoutMs(), TimeUnit.MILLISECONDS));
        setting.applyToConnectionPoolSettings(builder -> builder.maxWaitTime(props.getConnectionTimeoutMs(),
                TimeUnit.MILLISECONDS)
            .maxConnectionLifeTime(props.getMaxConnectionLifeTimeMs(), TimeUnit.MILLISECONDS)
            .maxConnectionIdleTime(props.getMaxIdleTimeMs(), TimeUnit.MILLISECONDS));
        setting.applyToServerSettings(builder -> builder.minHeartbeatFrequency(props.getMinHeartBeatFrequency(),
            TimeUnit.MILLISECONDS).heartbeatFrequency(props.getHeartBeatFrequency(), TimeUnit.MILLISECONDS));
        setting.applicationName(service);
        List<ServerAddress> addressList =
            connectionStringSetting.getHosts().stream().map(ServerAddress::new).collect(Collectors.toList());
        this.connections.put(service,
            new MongoDbConnection(service, addressList, props.getDbName(), setting.build(), props.getMaxIdleTimeMs()));
        this.currentService = service;
      } catch (Exception exception) {
        this.connections.remove(service);
        throw new Exception(String.format("#Error cannot establish connection to %s mongodb, log : ", service),
            exception);
      }
    } else {
      log.debug("#Acquire existing mongo db connection to {}", service);
      getConnection(service).wakeUp();
      this.currentService = service;
    }

    this.currentCollection = null;
    return this;
  }

  private MongoDatabase getDatabase(String serviceName) {
    return getConnection(serviceName).getDatabase();
  }

  private MongoDatabase getCurrentDatabase() {
    return getCurrentConnection().getDatabase();
  }

  /**
   * {@inheritDoc}
   */
  @Override
  public MongoDbConnection getCurrentConnection() {
    return this.connections.getOrDefault(this.currentService, null);
  }

  private MongoDbConnection getConnection(String service) {
    return this.connections.getOrDefault(service, null);
  }

  private void checkConnection() {
    if (!isConnected()) {
      getCurrentConnection().wakeUp();
    }
  }

  /**
   * {@inheritDoc}
   */
  @Override
  public Boolean isConnected() {
    if (!isBlank(this.currentService)) {
      return isConnected(this.currentService);
    } else {
      return false;
    }
  }

  private Boolean isConnected(String serviceName) {
    return this.connections.containsKey(serviceName) && this.connections.get(serviceName).isConnected();
  }

  /**
   * {@inheritDoc}
   */
  @Override
  public MongoHelper selectCollection(String collectionName) {
    this.currentCollection = collectionName;
    return this;
  }

  /**
   * {@inheritDoc}
   */
  @Override
  public MongoCollection<Document> getCollection() {
    return getCurrentDatabase().getCollection(this.currentCollection);
  }

  /**
   * {@inheritDoc}
   */
  @Override
  public List<LinkedHashMap<String, Object>> rawQuery(String query, int limit) {
    String projections = "{}";
    String sort = "{}";
    return rawQuery(query, sort, projections, limit, 0);
  }

  /**
   * {@inheritDoc}
   */
  @Override
  public List<LinkedHashMap<String, Object>> rawQuery(String query, String sort, int limit) {
    String projections = "{}";
    return rawQuery(query, sort, projections, limit, 0);
  }

  /**
   * {@inheritDoc}
   */
  @Override
  public List<LinkedHashMap<String, Object>> rawQuery(String query) {
    String projections = "{}";
    String sort = "{}";
    return rawQuery(query, sort, projections, 0, 0);
  }

  /**
   * {@inheritDoc}
   */
  @Override
  public List<LinkedHashMap<String, Object>> rawQuery(String query, String projections) {
    String sort = "{}";
    return rawQuery(query, sort, projections, 0, 0);
  }

  /**
   * {@inheritDoc}
   */
  @Override
  public List<LinkedHashMap<String, Object>> rawQuery(String query,
      String sort,
      String projections,
      int limit,
      int skip) {
    log.debug("#Mongo find data in collection {} with query {} projections {} sort {} limit {} and skip {}",
        getCollection().getNamespace().getCollectionName(),
        query,
        projections,
        sort,
        limit,
        skip);
    Document parsedQuery = parseSinglePipeline(query);
    Document parsedProjections = parseSinglePipeline(projections);
    Document parsedSort = parseSinglePipeline(sort);

    try {
      checkConnection();
      List<LinkedHashMap<String, Object>> result = new ArrayList<>();
      if (limit > 0) {
        result = AwaitStream.many(getCollection().find(parsedQuery)
                .projection(parsedProjections)
                .sort(parsedSort)
                .skip(skip)
                .limit(limit))
            .stream()
            .map(record -> MapperHelper.toObject(record, new TypeReference<LinkedHashMap<String, Object>>() {
            }))
            .collect(Collectors.toList());
      } else {
        result = AwaitStream.many(getCollection().find(parsedQuery)
                .projection(parsedProjections)
                .sort(parsedSort)
                .skip(skip))
            .stream()
            .map(record -> MapperHelper.toObject(record, new TypeReference<LinkedHashMap<String, Object>>() {
            }))
            .collect(Collectors.toList());
      }
      return result;
    } catch (Exception e) {
      log.error("#Error while executing query {}, log {}", query, e);
      return null;
    }
  }

  /**
   * {@inheritDoc}
   */
  @Override
  public List<LinkedHashMap<String, Object>> delete(String query, String sort) {
    return delete(query, sort, false);
  }

  /**
   * {@inheritDoc}
   */
  @Override
  public List<LinkedHashMap<String, Object>> delete(String query, String sort, boolean useMany) {
    log.debug("#Mongo delete {} data {} on collection {}",
        useMany ? "multiple" : "single",
        query,
        getCollection().getNamespace().getCollectionName());
    Document parsedQuery = parseSinglePipeline(query);
    Document parsedSort = parseSinglePipeline(sort);

    List<LinkedHashMap<String, Object>> result;
    try {
      checkConnection();
      if (useMany) {
        DeleteResult deleted = AwaitStream.one(getCollection().deleteMany(parsedQuery));
        result = MapperHelper.toObject(Collections.singletonList(deleted), new TypeReference<>() {
        });
      } else {
        Document doc = AwaitStream.one(getCollection().find(parsedQuery).sort(parsedSort).limit(1));
        if (doc != null) {
          DeleteResult deleted = AwaitStream.one(getCollection().deleteOne(Filters.eq("_id", doc.getObjectId("_id"))));
          result = MapperHelper.toObject(Collections.singletonList(deleted), new TypeReference<>() {
          });
        } else {
          result =
              MapperHelper.toObject(Collections.singletonList(DeleteResult.acknowledged(0)), new TypeReference<>() {
              });
        }
      }
      return result;
    } catch (Exception e) {
      log.error("#Error while executing delete query {}, log {}", query, e);
      return null;
    }
  }

  /**
   * {@inheritDoc}
   */
  @Override
  public List<LinkedHashMap<String, Object>> update(String query, String update, boolean useMany) {
    log.debug("#Mongo update on collection {} for data {} to set {} and useMany {}",
        getCollection().getNamespace().getCollectionName(),
        query,
        update,
        useMany);
    Document parsedQuery = parseSinglePipeline(query);
    Document parsedUpdate = parseSinglePipeline(update);
    if (isBlank(parsedQuery) || isBlank(parsedUpdate)) {
      return null;
    }
    List<LinkedHashMap<String, Object>> result = new ArrayList<>();
    try {
      checkConnection();
      UpdateResult updated;
      if (useMany) {
        updated = AwaitStream.one(getCollection().updateMany(parsedQuery, parsedUpdate));
      } else {
        updated = AwaitStream.one(getCollection().updateOne(parsedQuery, parsedUpdate));
      }
      result = MapperHelper.toObject(Collections.singletonList(updated), new TypeReference<>() {
      });
    } catch (Exception e) {
      log.error("#Error while executing update query to {} log {}",
          getCollection().getNamespace().getCollectionName(),
          e);
    }
    return result;
  }

  /**
   * {@inheritDoc}
   */
  @Override
  public List<LinkedHashMap<String, Object>> aggregate(String query) {
    log.debug("#Mongo aggregate on collection {} with pipelines {}",
        getCollection().getNamespace().getCollectionName(),
        query);
    if (isBlank(query)) {
      return null;
    }
    List<Document> parsedQuery = parseMultiplePipelines(query);
    List<LinkedHashMap<String, Object>> result = new ArrayList<>();

    try {
      checkConnection();
      List<Document> aggregation = AwaitStream.many(getCollection().aggregate(parsedQuery));
      for (Document document : aggregation) {
        result.add(MapperHelper.toObject(document, new TypeReference<>() {
        }));
      }

      return result;
    } catch (Exception e) {
      log.error("#Error while executing aggregation query {}, log {}", query, e);
      return null;
    }
  }

  /**
   * {@inheritDoc}
   */
  @Override
  public List<LinkedHashMap<String, Object>> insert(Object document) {
    log.debug("#Mongo insert data \n{}\n on collection {}",
        document,
        getCollection().getNamespace().getCollectionName());
    if (isBlank(document)) {
      return null;
    } else {
      checkConnection();
      if (CommonHelper.isCollection(document)) {
        List<?> listOfDocs = CommonHelper.convertObjectToList(document);
        List<Document> documents = new ArrayList<>();
        listOfDocs.forEach(item -> {
          if (item instanceof Document) {
            documents.add((Document) item);
          } else {
            documents.add(new Document(MapperHelper.toObject(item, new TypeReference<Map<String, ?>>() {
            })));
          }
        });

        if (isBlank(documents)) {
          log.warn("#WARN try to insert empty document, process is skipped");
          return null;
        } else {

          List<LinkedHashMap<String, Object>> result = new ArrayList<>();
          LinkedHashMap<String, Object> item = new LinkedHashMap<>();

          try {
            InsertManyResult res = AwaitStream.one(getCollection().insertMany(documents));

            item.put("insertedIds", res.getInsertedIds());
            item.put("wasAcknowledged", res.wasAcknowledged());
            item.put("insertCount", documents.size());
            item.put("message", "write document success");
            item.put("writeErrors", new ArrayList<>());
          } catch (MongoBulkWriteException e) {
            item.put("insertCount", e.getWriteResult().getInsertedCount());
            item.put("message", e.getMessage());
            item.put("writeErrors", e.getWriteErrors());
          } catch (Exception ex) {
            log.error("#Error while executing insert query to {} log {}",
                getCollection().getNamespace().getCollectionName(),
                ex);
          }
          if (!item.isEmpty()) {
            result.add(item);
          }
          return result;
        }
      } else {
        Document parsedDocument = document instanceof Document ?
            (Document) document :
            new Document(MapperHelper.toObject(document, new TypeReference<LinkedHashMap<String, Object>>() {
            }));

        if (isBlank(parsedDocument)) {
          log.warn("#WARN try to insert empty document, process is skipped");
          return null;
        } else {

          List<LinkedHashMap<String, Object>> result = new ArrayList<>();
          LinkedHashMap<String, Object> item = new LinkedHashMap<>();

          try {
            InsertOneResult res = AwaitStream.one(getCollection().insertOne(parsedDocument));

            item.put("insertedId", res.getInsertedId());
            item.put("wasAcknowledged", res.wasAcknowledged());
            item.put("insertCount", 1);
            item.put("message", "write document success");
            item.put("writeErrors", new ArrayList<>());
          } catch (MongoBulkWriteException e) {
            item.put("insertCount", e.getWriteResult().getInsertedCount());
            item.put("message", e.getMessage());
            item.put("writeErrors", e.getWriteErrors());
          } catch (Exception ex) {
            log.error("#Error while executing insert query to {} log {}",
                getCollection().getNamespace().getCollectionName(),
                ex);
          }
          result.add(item);
          return result;
        }
      }
    }
  }

  /**
   * {@inheritDoc}
   */
  @Override
  public void close() {
    if (!isBlank(this.currentService)) {
      close(this.currentService);
    }
  }

  /**
   * {@inheritDoc}
   */
  @Override
  public void close(String serviceName) {
    try {
      if (isConnected(serviceName)) {
        MongoDbConnection connection = getConnection(serviceName);
        if (connection != null) {
          connection.close();
        }
        this.connections.remove(serviceName);
      } else {
        log.debug("#No active mongo connection to {} is established, action skipped", serviceName);
      }
    } catch (Exception ignored) {

    }
  }

  /**
   * {@inheritDoc}
   */
  @Override
  public void closeAll() {
    if (!isBlank(this.connections)) {
      try {
        log.debug("#Closing all active mongo connections");
        final Set<String> services = this.connections.keySet();
        for (String key : services) {
          close(key);
        }
        this.connections.clear();
      } catch (Exception ignored) {

      }
    }
  }

  /**
   * {@inheritDoc}
   */
  @Override
  public long count(String query) {
    query = isBlank(query) ? "{}" : query;
    log.debug("#Mongo count data on collection {} with query {}", getCollection().getNamespace(), query);
    Document parsedQuery = parseSinglePipeline(query);
    try {
      checkConnection();
      Long result = AwaitStream.one(getCollection().countDocuments(parsedQuery));
      return result != null ? result : 0L;
    } catch (Exception e) {
      log.error("#Error while executing count query {}, log {}", query, e);
      return 0L;
    }
  }

  /**
   * {@inheritDoc}
   */
  @Override
  public List<LinkedHashMap<String, Object>> getIndexes() {
    log.debug("#Mongo get index info for collection {}", getCollection().getNamespace());
    List<LinkedHashMap<String, Object>> result = new ArrayList<>();
    try {
      checkConnection();
      List<Document> indexes = AwaitStream.many(getCollection().listIndexes());
      for (Document document : indexes) {
        result.add(MapperHelper.toObject(document, new TypeReference<LinkedHashMap<String, Object>>() {
        }));
      }
    } catch (Exception e) {
      log.error("#Error while getting index on collection {}, log {}", getCollection().getNamespace(), e);
    }
    return result;
  }

  /**
   * {@inheritDoc}
   */
  @Override
  public List<Object> distinct(String field) {
    return distinct(field, null);
  }

  /**
   * {@inheritDoc}
   */
  @Override
  public List<Object> distinct(String field, String query) {
    if (isBlank(field)) {
      log.warn("#WARNING executing distinct on empty field");
      return new ArrayList<>();
    } else {
      List<Object> result = new ArrayList<>();
      checkConnection();
      query = isBlank(query) ? "{}" : query;
      Document parsedQuery = parseSinglePipeline(query);

      try {
        log.debug("#Mongo distinct value for field {} with query {} on collection {}",
            field,
            parsedQuery,
            getCollection().getNamespace());
        AwaitStream.many(getCollection().distinct(field, parsedQuery, BsonValue.class)).forEach(item -> {
          result.add(parseBsonValue(item));
        });
      } catch (Exception e) {
        log.error("#Error while executing distinct value for field {} with query {} on collection {}, log {}",
            field,
            parsedQuery,
            getCollection().getNamespace(),
            e);
      }
      return result;
    }
  }

  private Object parseBsonValue(BsonValue bson) {
    Object item = null;
    if (bson.isDocument()) {
      item = bson.asDocument().toJson(JsonWriterSettings.builder().build());
    } else if (bson.isArray()) {
      item = bson.asArray().stream().map(this::parseBsonValue).collect(Collectors.toList());
    } else if (bson.isBoolean()) {
      item = bson.asBoolean().getValue();
    } else if (bson.isString()) {
      item = bson.asString().getValue();
    } else if (bson.isNumber()) {
      item = bson.asNumber().longValue();
    } else if (bson.isDateTime()) {
      item = bson.asDateTime().getValue();
    } else if (bson.isBinary()) {
      item = bson.asBinary().getData();
    } else if (bson.isInt32()) {
      item = bson.asInt32().intValue();
    } else if (bson.isInt64()) {
      item = bson.asInt64().intValue();
    } else if (bson.isObjectId()) {
      item = bson.asObjectId().getValue();
    } else if (bson.isTimestamp()) {
      item = bson.asTimestamp().getValue();
    } else if (bson.isDouble()) {
      item = bson.asDouble().getValue();
    } else if (bson.isDecimal128()) {
      item = bson.asDecimal128().doubleValue();
    }

    return item;
  }

  private List<Document> parseMultiplePipelines(String query) {
    JsonReader reader = new JsonReader(query);
    BsonArrayCodec arrayReader = new BsonArrayCodec(this.registry);
    List<Document> result = new ArrayList<>();
    BsonArray docArray = arrayReader.decode(reader, DecoderContext.builder().build());
    for (BsonValue value : docArray.getValues()) {
      result.add(Document.parse(value.toString()));
    }
    return result;
  }

  private Document parseSinglePipeline(String query) {
    JsonReader reader = new JsonReader(query);
    BsonDocumentCodec codec = new BsonDocumentCodec(this.registry);
    BsonDocument doc = codec.decode(reader, DecoderContext.builder().build());
    return Document.parse(doc.toJson());
  }
}
