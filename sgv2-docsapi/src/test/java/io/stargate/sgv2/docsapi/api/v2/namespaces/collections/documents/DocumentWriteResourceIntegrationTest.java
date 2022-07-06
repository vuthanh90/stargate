package io.stargate.sgv2.docsapi.api.v2.namespaces.collections.documents;

import static io.restassured.RestAssured.given;
import static net.javacrumbs.jsonunit.JsonMatchers.jsonEquals;
import static net.javacrumbs.jsonunit.JsonMatchers.jsonPartMatches;
import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.any;
import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.notNullValue;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.junit.TestProfile;
import io.restassured.RestAssured;
import io.restassured.response.Response;
import io.stargate.sgv2.common.cql.builder.Replication;
import io.stargate.sgv2.docsapi.config.constants.Constants;
import io.stargate.sgv2.docsapi.service.schema.NamespaceManager;
import io.stargate.sgv2.docsapi.service.schema.TableManager;
import io.stargate.sgv2.docsapi.testprofiles.IntegrationTestProfile;
import java.time.Duration;
import java.util.Iterator;
import java.util.concurrent.TimeUnit;
import javax.enterprise.context.control.ActivateRequestContext;
import javax.inject.Inject;
import org.apache.commons.lang3.RandomStringUtils;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.testcontainers.shaded.org.awaitility.Awaitility;

@QuarkusTest
@TestProfile(IntegrationTestProfile.class)
@ActivateRequestContext
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class DocumentWriteResourceIntegrationTest {

  public static final String BASE_PATH = "/v2/namespaces/{namespace}/collections/{collection}";
  public static final String DEFAULT_NAMESPACE = RandomStringUtils.randomAlphanumeric(16);
  public static final String DEFAULT_COLLECTION = RandomStringUtils.randomAlphanumeric(16);
  public static final String DEFAULT_PAYLOAD =
      "{\"test\": \"document\", \"this\": [\"is\", 1, true]}";
  public static final String MALFORMED_PAYLOAD = "{\"malformed\": ";

  @Inject NamespaceManager namespaceManager;

  @Inject TableManager tableManager;

  @Inject ObjectMapper objectMapper;

  @BeforeAll
  public void init() {

    RestAssured.enableLoggingOfRequestAndResponseIfValidationFails();

    namespaceManager
        .createNamespace(DEFAULT_NAMESPACE, Replication.simpleStrategy(1))
        .await()
        .atMost(Duration.ofSeconds(10));

    tableManager
        .createCollectionTable(DEFAULT_NAMESPACE, DEFAULT_COLLECTION)
        .await()
        .atMost(Duration.ofSeconds(10));
  }

  @Nested
  class WriteDocument {
    @Test
    public void happyPath() {
      Response postResponse =
          given()
              .header(Constants.AUTHENTICATION_TOKEN_HEADER_NAME, "")
              .header("Content-Type", "application/json")
              .body(DEFAULT_PAYLOAD)
              .when()
              .post(BASE_PATH, DEFAULT_NAMESPACE, DEFAULT_COLLECTION)
              .peek();

      assertThat(postResponse.statusCode()).isEqualTo(201);
      assertThat(postResponse.header("location")).isNotNull();
      String location = postResponse.header("location");

      given()
          .header(Constants.AUTHENTICATION_TOKEN_HEADER_NAME, "")
          .queryParam("raw", "true")
          .when()
          .get(location)
          .then()
          .statusCode(200)
          .body(jsonEquals(DEFAULT_PAYLOAD));
    }

    @Test
    public void happyPathNoCollection() {
      Response postResponse =
          given()
              .header(Constants.AUTHENTICATION_TOKEN_HEADER_NAME, "")
              .header("Content-Type", "application/json")
              .body(DEFAULT_PAYLOAD)
              .when()
              .post(BASE_PATH, DEFAULT_NAMESPACE, "newtable")
              .peek();

      assertThat(postResponse.statusCode()).isEqualTo(201);
      assertThat(postResponse.header("location")).isNotNull();
      String location = postResponse.header("location");

      given()
          .header(Constants.AUTHENTICATION_TOKEN_HEADER_NAME, "")
          .queryParam("raw", "true")
          .when()
          .get(location)
          .then()
          .statusCode(200)
          .body(jsonEquals(DEFAULT_PAYLOAD));
    }

    @Test
    public void writeArray() {
      Response postResponse =
          given()
              .header(Constants.AUTHENTICATION_TOKEN_HEADER_NAME, "")
              .header("Content-Type", "application/json")
              .body("[1, 2, 3]")
              .when()
              .post(BASE_PATH, DEFAULT_NAMESPACE, DEFAULT_COLLECTION)
              .peek();

      assertThat(postResponse.statusCode()).isEqualTo(201);
      assertThat(postResponse.header("location")).isNotNull();
      String location = postResponse.header("location");

      given()
          .header(Constants.AUTHENTICATION_TOKEN_HEADER_NAME, "")
          .queryParam("raw", "true")
          .when()
          .get(location)
          .then()
          .statusCode(200)
          .body(jsonEquals("[1, 2, 3]"));
    }

    @Test
    public void withProfile() {
      given()
          .header(Constants.AUTHENTICATION_TOKEN_HEADER_NAME, "")
          .header("Content-Type", "application/json")
          .queryParam("profile", "true")
          .body(DEFAULT_PAYLOAD)
          .when()
          .post(BASE_PATH, DEFAULT_NAMESPACE, DEFAULT_COLLECTION)
          .then()
          .statusCode(201)
          .body("profile", notNullValue());
    }

    @Test
    public void withTtl() {
      Response postResponse =
          given()
              .header(Constants.AUTHENTICATION_TOKEN_HEADER_NAME, "")
              .header("Content-Type", "application/json")
              .queryParam("ttl", "1")
              .body(DEFAULT_PAYLOAD)
              .when()
              .post(BASE_PATH, DEFAULT_NAMESPACE, DEFAULT_COLLECTION)
              .peek();

      assertThat(postResponse.statusCode()).isEqualTo(201);
      assertThat(postResponse.header("location")).isNotNull();
      String location = postResponse.header("location");

      Awaitility.await()
          .atMost(2000, TimeUnit.MILLISECONDS)
          .untilAsserted(
              () -> {
                given()
                    .header(Constants.AUTHENTICATION_TOKEN_HEADER_NAME, "")
                    .when()
                    .get(location)
                    .then()
                    .statusCode(404);
              });
    }

    @Test
    public void withLongerTtl() {
      Response postResponse =
          given()
              .header(Constants.AUTHENTICATION_TOKEN_HEADER_NAME, "")
              .header("Content-Type", "application/json")
              .queryParam("ttl", "10")
              .body(DEFAULT_PAYLOAD)
              .when()
              .post(BASE_PATH, DEFAULT_NAMESPACE, DEFAULT_COLLECTION)
              .peek();

      assertThat(postResponse.statusCode()).isEqualTo(201);
      assertThat(postResponse.header("location")).isNotNull();
      String location = postResponse.header("location");

      given()
          .header(Constants.AUTHENTICATION_TOKEN_HEADER_NAME, "")
          .when()
          .get(location)
          .then()
          .statusCode(200)
          .body("data", jsonEquals(DEFAULT_PAYLOAD));
    }

    @Test
    public void malformedJson() {
      given()
          .header(Constants.AUTHENTICATION_TOKEN_HEADER_NAME, "")
          .header("Content-Type", "application/json")
          .body(MALFORMED_PAYLOAD)
          .when()
          .post(BASE_PATH, DEFAULT_NAMESPACE, DEFAULT_COLLECTION)
          .then()
          .statusCode(400);
    }

    @Test
    public void emptyObject() {
      given()
          .header(Constants.AUTHENTICATION_TOKEN_HEADER_NAME, "")
          .header("Content-Type", "application/json")
          .body("{}")
          .when()
          .post(BASE_PATH, DEFAULT_NAMESPACE, DEFAULT_COLLECTION)
          .then()
          .statusCode(400)
          .body("code", equalTo(400))
          .body(
              "description",
              equalTo(
                  "Updating a key with just an empty object or an empty array is not allowed. Hint: update the parent path with a defined object instead."));
    }

    @Test
    public void emptyArray() {
      given()
          .header(Constants.AUTHENTICATION_TOKEN_HEADER_NAME, "")
          .header("Content-Type", "application/json")
          .body("[]")
          .when()
          .post(BASE_PATH, DEFAULT_NAMESPACE, DEFAULT_COLLECTION)
          .then()
          .body("code", equalTo(400))
          .body(
              "description",
              equalTo(
                  "Updating a key with just an empty object or an empty array is not allowed. Hint: update the parent path with a defined object instead."));
    }

    @Test
    public void singlePrimitive() {
      given()
          .header(Constants.AUTHENTICATION_TOKEN_HEADER_NAME, "")
          .header("Content-Type", "application/json")
          .body("true")
          .when()
          .post(BASE_PATH, DEFAULT_NAMESPACE, DEFAULT_COLLECTION)
          .then()
          .body("code", equalTo(400))
          .body(
              "description",
              equalTo(
                  "Updating a key with just a JSON primitive is not allowed. Hint: update the parent path with a defined object instead."));
    }

    @Test
    public void noBody() {
      given()
          .header(Constants.AUTHENTICATION_TOKEN_HEADER_NAME, "")
          .header("Content-Type", "application/json")
          .when()
          .post(BASE_PATH, DEFAULT_NAMESPACE, DEFAULT_COLLECTION)
          .then()
          .statusCode(400)
          .body("code", equalTo(400))
          .body("description", equalTo("Request invalid: must not be null."));
    }

    @Test
    public void unauthorized() {
      given()
          .header("Content-Type", "application/json")
          .body(DEFAULT_PAYLOAD)
          .when()
          .post(BASE_PATH, DEFAULT_NAMESPACE, DEFAULT_COLLECTION)
          .then()
          .statusCode(401);
    }

    @Test
    public void keyspaceNotExists() {
      given()
          .header(Constants.AUTHENTICATION_TOKEN_HEADER_NAME, "")
          .header("Content-Type", "application/json")
          .body(DEFAULT_PAYLOAD)
          .when()
          .post(BASE_PATH, "notakeyspace", DEFAULT_COLLECTION)
          .then()
          .statusCode(404)
          .body("code", equalTo(404))
          .body(
              "description", equalTo("Unknown namespace notakeyspace, you must create it first."));
    }
  }

  @Nested
  class WriteDocumentBatch {

    @Test
    public void happyPath() {
      given()
          .header(Constants.AUTHENTICATION_TOKEN_HEADER_NAME, "")
          .header("Content-Type", "application/json")
          .queryParam("ttl", "1")
          .body(String.format("[%s, %s, %s]", DEFAULT_PAYLOAD, DEFAULT_PAYLOAD, DEFAULT_PAYLOAD))
          .when()
          .post(BASE_PATH + "/batch", DEFAULT_NAMESPACE, DEFAULT_COLLECTION)
          .then()
          .statusCode(202)
          .body("documentIds", notNullValue())
          .body(jsonPartMatches("documentIds[0]", any(String.class)))
          .body(jsonPartMatches("documentIds[1]", any(String.class)))
          .body(jsonPartMatches("documentIds[2]", any(String.class)));
    }

    @Test
    public void idPath() throws JsonProcessingException {
      String doc1 = "{\"id\": \"1\", \"name\":\"a\"}";
      String doc2 = "{\"id\": \"2\", \"name\":\"b\"}";
      String doc3 = "{\"id\": \"3\", \"name\":\"c\"}";
      given()
          .header(Constants.AUTHENTICATION_TOKEN_HEADER_NAME, "")
          .header("Content-Type", "application/json")
          .queryParam("id-path", "id")
          .body(String.format("[%s, %s, %s]", doc1, doc2, doc3))
          .when()
          .post(BASE_PATH + "/batch", DEFAULT_NAMESPACE, DEFAULT_COLLECTION)
          .then()
          .statusCode(202)
          .body("documentIds", containsInAnyOrder("1", "2", "3"));
    }

    @Test
    public void idPathOverwrite() {
      given()
          .header(Constants.AUTHENTICATION_TOKEN_HEADER_NAME, "")
          .header("Content-Type", "application/json")
          .body(DEFAULT_PAYLOAD)
          .when()
          .put(BASE_PATH + "/1", DEFAULT_NAMESPACE, DEFAULT_COLLECTION)
          .then()
          .statusCode(200);

      String doc1 = "{\"id\": \"1\", \"name\":\"a\"}";
      String doc2 = "{\"id\": \"2\", \"name\":\"b\"}";
      String doc3 = "{\"id\": \"3\", \"name\":\"c\"}";
      given()
          .header(Constants.AUTHENTICATION_TOKEN_HEADER_NAME, "")
          .header("Content-Type", "application/json")
          .queryParam("id-path", "id")
          .body(String.format("[%s, %s, %s]", doc1, doc2, doc3))
          .when()
          .post(BASE_PATH + "/batch", DEFAULT_NAMESPACE, DEFAULT_COLLECTION)
          .then()
          .statusCode(202);

      // Check that the data for document ID 1 was overwritten properly
      given()
          .header(Constants.AUTHENTICATION_TOKEN_HEADER_NAME, "")
          .queryParam("raw", "true")
          .when()
          .get(BASE_PATH + "/1", DEFAULT_NAMESPACE, DEFAULT_COLLECTION)
          .then()
          .statusCode(200)
          .body(jsonEquals(doc1));
    }

    @Test
    public void withProfile() {
      given()
          .header(Constants.AUTHENTICATION_TOKEN_HEADER_NAME, "")
          .header("Content-Type", "application/json")
          .queryParam("profile", "true")
          .body("[" + DEFAULT_PAYLOAD + "]")
          .when()
          .post(BASE_PATH + "/batch", DEFAULT_NAMESPACE, DEFAULT_COLLECTION)
          .then()
          .statusCode(202)
          .body("profile", notNullValue());
    }

    @Test
    public void withTtl() throws JsonProcessingException {
      Response postResponse =
          given()
              .header(Constants.AUTHENTICATION_TOKEN_HEADER_NAME, "")
              .header("Content-Type", "application/json")
              .queryParam("ttl", "1")
              .body("[" + DEFAULT_PAYLOAD + "]")
              .when()
              .post(BASE_PATH + "/batch", DEFAULT_NAMESPACE, DEFAULT_COLLECTION)
              .peek();

      assertThat(postResponse.statusCode()).isEqualTo(202);
      assertThat(postResponse.body()).isNotNull();
      ArrayNode ids =
          (ArrayNode)
              objectMapper.readTree(postResponse.body().asString()).requiredAt("/documentIds");

      Awaitility.await()
          .atMost(2000, TimeUnit.MILLISECONDS)
          .untilAsserted(
              () -> {
                Iterator<JsonNode> iter = ids.iterator();
                while (iter.hasNext()) {
                  String id = iter.next().asText();
                  given()
                      .header(Constants.AUTHENTICATION_TOKEN_HEADER_NAME, "")
                      .when()
                      .get(BASE_PATH + "/{document-id}", DEFAULT_NAMESPACE, DEFAULT_COLLECTION, id)
                      .then()
                      .statusCode(404);
                }
              });
    }

    @Test
    public void illegalDuplicatedId() {
      given()
          .header(Constants.AUTHENTICATION_TOKEN_HEADER_NAME, "")
          .header("Content-Type", "application/json")
          .queryParam("id-path", "test")
          .body(String.format("[%s, %s]", DEFAULT_PAYLOAD, DEFAULT_PAYLOAD))
          .when()
          .post(BASE_PATH + "/batch", DEFAULT_NAMESPACE, DEFAULT_COLLECTION)
          .then()
          .statusCode(400)
          .body("code", equalTo(400))
          .body(
              "description",
              equalTo(
                  "Found duplicate ID document in more than one document when doing batched document write."));
    }

    @Test
    public void invalidIdPath() {
      given()
          .header(Constants.AUTHENTICATION_TOKEN_HEADER_NAME, "")
          .header("Content-Type", "application/json")
          .queryParam("id-path", "not.valid.path")
          .body(String.format("[%s]", DEFAULT_PAYLOAD))
          .when()
          .post(BASE_PATH + "/batch", DEFAULT_NAMESPACE, DEFAULT_COLLECTION)
          .then()
          .statusCode(400)
          .body("code", equalTo(400))
          .body(
              "description",
              equalTo(
                  "JSON document {\"test\":\"document\",\"this\":[\"is\",1,true]} requires a String value at the path /not/valid/path in order to resolve document ID, found missing node. Batch write failed."));
    }

    @Test
    public void malformedJson() {
      given()
          .header(Constants.AUTHENTICATION_TOKEN_HEADER_NAME, "")
          .header("Content-Type", "application/json")
          .body(MALFORMED_PAYLOAD)
          .when()
          .post(BASE_PATH + "/batch", DEFAULT_NAMESPACE, DEFAULT_COLLECTION)
          .then()
          .statusCode(400);
    }

    @Test
    public void unauthorized() {
      given()
          .when()
          .post(BASE_PATH + "/batch", DEFAULT_NAMESPACE, DEFAULT_COLLECTION)
          .then()
          .statusCode(401);
    }

    @Test
    public void keyspaceNotExists() {
      given()
          .header(Constants.AUTHENTICATION_TOKEN_HEADER_NAME, "")
          .header("Content-Type", "application/json")
          .body("[" + DEFAULT_PAYLOAD + "]")
          .when()
          .post(BASE_PATH + "/batch", "notakeyspace", DEFAULT_COLLECTION)
          .then()
          .statusCode(404)
          .body("code", equalTo(404))
          .body(
              "description", equalTo("Unknown namespace notakeyspace, you must create it first."));
    }
  }
}