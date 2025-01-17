// Copyright 2019, Oracle Corporation and/or its affiliates.  All rights reserved.
// Licensed under the Universal Permissive License v 1.0 as shown at
// http://oss.oracle.com/licenses/upl.

package oracle.kubernetes.operator.utils;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import com.fasterxml.jackson.dataformat.yaml.YAMLMapper;
import java.io.File;
import java.io.IOException;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Map;
import java.util.logging.Logger;

/** A Domain CRD utility class to manipulate domain yaml files */
public class DomainCRD {

  private final ObjectMapper objectMapper;
  private final JsonNode root;
  public static final Logger logger = Logger.getLogger("OperatorIT", "OperatorIT");

  /**
   * Constructor to read the yaml file and initialize the root JsonNode with yaml equivalent of JSON
   * tree
   *
   * @param yamlFile - Name of the yaml file containing the Domain CRD
   * @throws IOException
   */
  public DomainCRD(String yamlFile) throws IOException {
    this.objectMapper = new ObjectMapper();

    ObjectMapper yamlReader = new ObjectMapper(new YAMLFactory());
    Object obj = yamlReader.readValue(new File(yamlFile), Object.class);

    ObjectMapper jsonWriter = new ObjectMapper();
    String writeValueAsString = jsonWriter.writeValueAsString(obj);
    this.root = objectMapper.readTree(writeValueAsString);
  }

  /**
   * To convert the JSON tree back into Yaml and return it as a String
   *
   * @return - Domain CRD in Yaml format
   * @throws JsonProcessingException when JSON tree cannot be converted as a Yaml string
   */
  public String getYamlTree() throws JsonProcessingException {
    String jsonAsYaml = new YAMLMapper().writerWithDefaultPrettyPrinter().writeValueAsString(root);
    return jsonAsYaml;
  }

  /**
   * A utility method to add attributes to domain in domain.yaml
   *
   * @param attributes - A HashMap of key value pairs
   */
  public void addObjectNodeToDomain(Map<String, String> attributes) {
    JsonNode specNode = getSpecNode();
    for (Map.Entry<String, String> entry : attributes.entrySet()) {
      ((ObjectNode) specNode).put(entry.getKey(), entry.getValue());
    }
  }

  /**
   * A utility method to add attributes to domain in domain.yaml
   *
   * @param attributes - A HashMap of key value pairs
   */
  public void addShutdownOptionToDomain(Map<String, Object> attributes) throws Exception {
    JsonNode specNode = getSpecNode();
    addShutdownOptionToObjectNode(specNode, attributes);
  }

  /**
   * A utility method to add attributes to domain in domain.yaml
   *
   * @param attributes - A HashMap of key value pairs
   */
  public void addEnvOption(Map<String, String> attributes) throws Exception {
    JsonNode specNode = getSpecNode();
    JsonNode envNode = null;
    JsonNode podOptions = specNode.path("serverPod");
    JsonNode envNodes = (ArrayNode) specNode.path("serverPod").path("env");

    if (specNode.path("serverPod").isMissingNode()) {
      logger.info("Missing serverPod Node");
      podOptions = objectMapper.createObjectNode();
      envNodes = objectMapper.createArrayNode();
      ((ObjectNode) podOptions).set("env", envNodes);
      ((ObjectNode) specNode).set("serverPod", podOptions);

    } else {

      if (podOptions.path("env").isMissingNode()) {
        envNodes = objectMapper.createArrayNode();
        ((ObjectNode) podOptions).set("env", envNodes);
      }
    }
    envNodes = (ArrayNode) podOptions.path("env");
    for (Map.Entry<String, String> entry : attributes.entrySet()) {
      if (envNodes.size() != 0) {
        for (JsonNode envNode1 : envNodes) {

          logger.info(" checking node " + envNode1.get("name"));
          if (envNode1.get("name").equals(entry.getKey())) {
            ((ObjectNode) envNode1).put("value", entry.getValue());
          }
        }
        envNode = objectMapper.createObjectNode();
        ((ObjectNode) envNode).put("name", entry.getKey());
        ((ObjectNode) envNode).put("value", entry.getValue());
        ((ArrayNode) envNodes).add(envNode);

      } else {
        envNode = objectMapper.createObjectNode();
        ((ObjectNode) envNode).put("name", entry.getKey());
        ((ObjectNode) envNode).put("value", entry.getValue());
        ((ArrayNode) envNodes).add(envNode);
      }
    }
  }

  /**
   * A utility method to add attributes to adminServer node in domain.yaml
   *
   * @param attributes - A HashMap of key value pairs
   */
  public void addObjectNodeToAdminServer(Map<String, String> attributes) {

    JsonNode adminServerNode = getAdminServerNode();
    for (Map.Entry<String, String> entry : attributes.entrySet()) {
      ((ObjectNode) adminServerNode).put(entry.getKey(), entry.getValue());
    }
  }

  /**
   * A utility method to add attributes to cluster node in domain.yaml
   *
   * @param ClusterName - Name of the cluster to which the attributes to be added
   * @param attributes - A HashMap of key value pairs
   */
  public void addObjectNodeToCluster(String ClusterName, Map<String, Object> attributes) {

    JsonNode clusterNode = getClusterNode(ClusterName);
    for (Map.Entry<String, Object> entry : attributes.entrySet()) {
      Object entryValue = entry.getValue();
      if (entryValue instanceof String) {
        ((ObjectNode) clusterNode).put(entry.getKey(), (String) entryValue);
      } else if (entryValue instanceof Integer) {
        ((ObjectNode) clusterNode).put(entry.getKey(), ((Integer) entryValue).intValue());
      }
    }
  }

  /**
   * A utility method to add shutdown element and attributes to cluster node in domain.yaml
   *
   * @param ClusterName - Name of the cluster to which the attributes to be added
   * @param attributes - A HashMap of key value pairs
   */
  public void addShutdownOptionsToCluster(String ClusterName, Map<String, Object> attributes)
      throws Exception {

    JsonNode clusterNode = getClusterNode(ClusterName);
    addShutdownOptionToObjectNode(clusterNode, attributes);
  }

  /**
   * A utility method to add attributes to managed server node in domain.yaml
   *
   * @param managedServerName - Name of the managed server to which the attributes to be added
   * @param attributes - A HashMap of key value pairs
   */
  public void addObjectNodeToMS(String managedServerName, Map<String, String> attributes) {
    JsonNode managedServerNode = getManagedServerNode(managedServerName);
    for (Map.Entry<String, String> entry : attributes.entrySet()) {
      ((ObjectNode) managedServerNode).put(entry.getKey(), entry.getValue());
    }
    try {
      String jsonString =
          objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(managedServerNode);
      System.out.println(jsonString);
    } catch (Exception ex) {
    }
  }

  /**
   * A utility method to add attributes to managed server node in domain.yaml
   *
   * @param managedServerName - Name of the managed server to which the attributes to be added
   * @param attributes - A HashMap of key value pairs
   */
  public void addShutDownOptionToMS(String managedServerName, Map<String, Object> attributes)
      throws Exception {
    JsonNode managedServerNode = getManagedServerNode(managedServerName);
    addShutdownOptionToObjectNode(managedServerNode, attributes);
  }

  /**
   * A utility method to add attributes to managed server node in domain.yaml
   *
   * @param jsonNode - the json node (domain,cluster,or manserver to which the attributes to be
   *     added
   * @param attributes - A HashMap of key value pairs
   */
  private void addShutdownOptionToObjectNode(JsonNode jsonNode, Map<String, Object> attributes)
      throws Exception {
    String[] propNames = {"serverPod", "shutdown"};
    JsonNode myNode = jsonNode;
    for (int i = 0; i < propNames.length; i++) {
      if (myNode.path(propNames[i]).isMissingNode()) {

        logger.info("  property " + propNames[i] + " is not present, adding it  crd ");

        ObjectNode someSubNode = objectMapper.createObjectNode();
        ((ObjectNode) myNode).put(propNames[i], someSubNode);
        myNode = someSubNode;

      } else {
        myNode = myNode.path(propNames[i]);
      }
    }

    for (Map.Entry<String, Object> entry : attributes.entrySet()) {
      String dataType = entry.getValue().getClass().getSimpleName();

      if (dataType.equalsIgnoreCase("Integer")) {
        logger.info(
            "Read Json Key :" + entry.getKey() + " | type :int | value:" + entry.getValue());
        ((ObjectNode) myNode).put(entry.getKey(), (int) entry.getValue());

      } else if (dataType.equalsIgnoreCase("Boolean")) {
        logger.info(
            "Read Json Key :" + entry.getKey() + " | type :boolean | value:" + entry.getValue());
        ((ObjectNode) myNode).put(entry.getKey(), (boolean) entry.getValue());

      } else if (dataType.equalsIgnoreCase("String")) {
        logger.info(
            "Read Json Key :" + entry.getKey() + " | type :string | value:" + entry.getValue());
        ((ObjectNode) myNode).put(entry.getKey(), (String) entry.getValue());
      }
    }
    String jsonString = objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(myNode);
    System.out.println(jsonString);
  }

  /** Gets the spec node entry from Domain CRD JSON tree */
  private JsonNode getSpecNode() {
    return root.path("spec");
  }

  /**
   * Gets the administration server node entry from Domain CRD JSON tree
   *
   * @return
   */
  private JsonNode getAdminServerNode() {
    return root.path("spec").path("adminServer");
  }

  /**
   * Gets the cluster node entry from Domain CRD JSON tree for the given cluster name
   *
   * @param root - Root JSON node of the Domain CRD JSON tree
   * @param clusterName - Name of the cluster
   * @return - cluster node entry from Domain CRD JSON tree
   */
  /**
   * Gets the cluster node entry from Domain CRD JSON tree for the given cluster name
   *
   * @param clusterName - Name of the cluster
   * @return - JsonNode of named cluster
   */
  private JsonNode getClusterNode(String clusterName) {
    ArrayNode clusters = (ArrayNode) root.path("spec").path("clusters");
    JsonNode clusterNode = null;
    for (JsonNode cluster : clusters) {
      if (cluster.get("clusterName").asText().equals(clusterName)) {
        logger.info("Got the cluster");
        clusterNode = cluster;
      }
    }
    return clusterNode;
  }

  /**
   * Gets the managed server node entry from Domain CRD JSON tree
   *
   * @param managedServerName Name of the managed server for which to get the JSON node
   * @return managed server node entry from Domain CRD JSON tree
   */
  private JsonNode getManagedServerNode(String managedServerName) {

    ArrayNode managedservers = null;
    JsonNode managedserverNode = null;
    JsonNode specNode = getSpecNode();
    if (root.path("spec").path("managedServers").isMissingNode()) {
      logger.info("Missing MS Node");
      managedservers = objectMapper.createArrayNode();
      ObjectNode managedserver = objectMapper.createObjectNode();
      managedserver.put("serverName", managedServerName);
      managedservers.add(managedserver);

      ((ObjectNode) specNode).set("managedServers", managedservers);
      managedserverNode = managedserver;
    } else {
      managedservers = (ArrayNode) root.path("spec").path("managedServers");
      if (managedservers.size() != 0) {
        for (JsonNode managedserver : managedservers) {
          if (managedserver.get("serverName").asText().equals(managedServerName)) {
            logger.info("Found managedServer with name " + managedServerName);
            managedserverNode = managedserver;
          }
        }
      } else {
        logger.info("Creating node for managedServer with name " + managedServerName);
        ObjectNode managedserver = objectMapper.createObjectNode();
        managedserver.put("serverName", managedServerName);
        managedservers.add(managedserver);
      }
    }
    return managedserverNode;
  }

  /**
   * Utility method to create a file and write to it.
   *
   * @param dir - Directory in which to create the file
   * @param file - Name of the file to write the content to
   * @param content - String content to write to the file
   * @throws IOException - When file cannot opened or written
   */
  public void writeToFile(String dir, String file, String content) throws IOException {
    Path path = Paths.get(dir, file);
    Charset charset = StandardCharsets.UTF_8;
    Files.write(path, content.getBytes(charset));
  }
}
