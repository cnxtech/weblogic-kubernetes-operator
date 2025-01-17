// Copyright 2018, 2019, Oracle Corporation and/or its affiliates.  All rights reserved.
// Licensed under the Universal Permissive License v 1.0 as shown at
// http://oss.oracle.com/licenses/upl.

package oracle.kubernetes.operator.helpers;

import static oracle.kubernetes.LogMatcher.containsFine;
import static oracle.kubernetes.operator.ProcessingConstants.SERVERS_TO_ROLL;
import static oracle.kubernetes.operator.WebLogicConstants.ADMIN_STATE;
import static oracle.kubernetes.operator.WebLogicConstants.RUNNING_STATE;
import static oracle.kubernetes.operator.logging.MessageKeys.MANAGED_POD_CREATED;
import static oracle.kubernetes.operator.logging.MessageKeys.MANAGED_POD_EXISTS;
import static oracle.kubernetes.operator.logging.MessageKeys.MANAGED_POD_PATCHED;
import static oracle.kubernetes.operator.logging.MessageKeys.MANAGED_POD_REPLACED;
import static org.hamcrest.Matchers.*;
import static org.hamcrest.junit.MatcherAssert.assertThat;

import io.kubernetes.client.models.V1EnvVar;
import io.kubernetes.client.models.V1Pod;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import oracle.kubernetes.operator.LabelConstants;
import oracle.kubernetes.operator.ProcessingConstants;
import oracle.kubernetes.operator.VersionConstants;
import oracle.kubernetes.operator.work.FiberTestSupport;
import oracle.kubernetes.operator.work.Packet;
import oracle.kubernetes.operator.work.Step.StepAndPacket;
import oracle.kubernetes.weblogic.domain.DomainConfigurator;
import oracle.kubernetes.weblogic.domain.ServerConfigurator;
import oracle.kubernetes.weblogic.domain.model.Domain;
import org.junit.Test;

public class ManagedPodHelperTest extends PodHelperTestBase {

  private static final String SERVER_NAME = "ms1";
  private static final int LISTEN_PORT = 8001;
  private static final String ITEM1 = "item1";
  private static final String ITEM2 = "item2";
  private static final String VALUE1 = "value1";
  private static final String VALUE2 = "value2";
  private static final String RAW_VALUE_1 = "find uid1 at $(DOMAIN_HOME)";
  private static final String END_VALUE_1 = "find uid1 at /u01/oracle/user_projects/domains";
  private static final String RAW_VALUE_2 = "$(SERVER_NAME) is not $(ADMIN_NAME):$(ADMIN_PORT)";
  private static final String END_VALUE_2 = "ms1 is not ADMIN_SERVER:7001";
  private static final String CLUSTER_NAME = "test-cluster";

  public ManagedPodHelperTest() {
    super(SERVER_NAME, LISTEN_PORT);
  }

  @Override
  String getCreatedMessageKey() {
    return MANAGED_POD_CREATED;
  }

  @Override
  String getExistsMessageKey() {
    return MANAGED_POD_EXISTS;
  }

  @Override
  String getPatchedMessageKey() {
    return MANAGED_POD_PATCHED;
  }

  @Override
  String getReplacedMessageKey() {
    return MANAGED_POD_REPLACED;
  }

  @Override
  void expectStepsAfterCreation() {
    expectReplaceDomain();
  }

  @Override
  FiberTestSupport.StepFactory getStepFactory() {
    return PodHelper::createManagedPodStep;
  }

  @Override
  ServerConfigurator configureServer() {
    return configureServer(getConfigurator(), SERVER_NAME);
  }

  private void expectReplaceDomainStatus() {
    testSupport
        .createCannedResponse("replaceDomainStatus")
        .withNamespace(NS)
        .ignoringBody()
        .returning(new Domain());
  }

  private void expectReplaceDomain() {
    testSupport
        .createCannedResponse("replaceDomain")
        .withNamespace(NS)
        .ignoringBody()
        .returning(new Domain());
  }

  @Override
  V1Pod createTestPodModel() {
    return new V1Pod().metadata(createPodMetadata()).spec(createPodSpec());
  }

  @Override
  List<String> createStartCommand() {
    return Collections.singletonList("/weblogic-operator/scripts/startServer.sh");
  }

  @Test
  public void whenManagedPodCreated_containerHasStartServerCommand() {
    assertThat(
        getCreatedPodSpecContainer().getCommand(),
        contains("/weblogic-operator/scripts/startServer.sh"));
  }

  @Test
  public void whenPacketHasEnvironmentItems_createManagedPodStartupWithThem() {
    testSupport.addToPacket(
        ProcessingConstants.ENVVARS,
        Arrays.asList(toEnvVar(ITEM1, VALUE1), toEnvVar(ITEM2, VALUE2)));

    assertThat(
        getCreatedPodSpecContainer().getEnv(),
        allOf(hasEnvVar(ITEM1, VALUE1), hasEnvVar(ITEM2, VALUE2)));
  }

  private V1EnvVar toEnvVar(String name, String value) {
    return envItem(name, value);
  }

  @Test
  public void whenPacketHasEnvironmentItemsWithVariables_createManagedPodStartupWithThem() {
    testSupport.addToPacket(
        ProcessingConstants.ENVVARS,
        Arrays.asList(toEnvVar(ITEM1, RAW_VALUE_1), toEnvVar(ITEM2, RAW_VALUE_2)));

    assertThat(
        getCreatedPodSpecContainer().getEnv(),
        allOf(hasEnvVar(ITEM1, END_VALUE_1), hasEnvVar(ITEM2, END_VALUE_2)));
  }

  @Test
  public void createManagedPodStartupWithNullAdminUsernamePasswordEnvVarsValues() {
    testSupport.addToPacket(ProcessingConstants.ENVVARS, Arrays.asList());

    assertThat(
        getCreatedPodSpecContainer().getEnv(),
        allOf(hasEnvVar("ADMIN_USERNAME", null), hasEnvVar("ADMIN_PASSWORD", null)));
  }

  @Test
  public void whenPacketHasEnvironmentItemsWithVariable_createManagedPodShouldNotChangeItsValue() {
    V1EnvVar ENVVAR = toEnvVar(ITEM1, RAW_VALUE_1);
    testSupport.addToPacket(ProcessingConstants.ENVVARS, Arrays.asList(ENVVAR));

    getCreatedPodSpecContainer();

    assertThat(ENVVAR.getValue(), is(RAW_VALUE_1));
  }

  @Test
  public void whenPacketHasClusterConfig_managedPodHasClusterLabel() {
    testSupport.addToPacket(ProcessingConstants.CLUSTER_NAME, CLUSTER_NAME);

    assertThat(
        getCreatedPod().getMetadata().getLabels(),
        hasEntry(LabelConstants.CLUSTERNAME_LABEL, CLUSTER_NAME));
  }

  @Test
  public void whenDomainHasAdditionalVolumes_createManagedPodWithThem() {
    getConfigurator()
        .withAdditionalVolume("volume1", "/source-path1")
        .withAdditionalVolume("volume2", "/source-path2");

    assertThat(
        getCreatedPod().getSpec().getVolumes(),
        allOf(hasVolume("volume1", "/source-path1"), hasVolume("volume2", "/source-path2")));
  }

  @Test
  public void whenDomainHasAdditionalVolumeMounts_createManagedPodWithThem() {
    getConfigurator()
        .withAdditionalVolumeMount("volume1", "/destination-path1")
        .withAdditionalVolumeMount("volume2", "/destination-path2");
    assertThat(
        getCreatedPodSpecContainer().getVolumeMounts(),
        allOf(
            hasVolumeMount("volume1", "/destination-path1"),
            hasVolumeMount("volume2", "/destination-path2")));
  }

  @Test
  public void whenClusterHasAdditionalVolumes_createManagedPodWithThem() {
    testSupport.addToPacket(ProcessingConstants.CLUSTER_NAME, CLUSTER_NAME);
    getConfigurator()
        .configureCluster(CLUSTER_NAME)
        .withAdditionalVolume("volume1", "/source-path1")
        .withAdditionalVolume("volume2", "/source-path2");

    assertThat(
        getCreatedPod().getSpec().getVolumes(),
        allOf(hasVolume("volume1", "/source-path1"), hasVolume("volume2", "/source-path2")));
  }

  @Test
  public void whenClusterHasAdditionalVolumeMounts_createManagedPodWithThem() {
    testSupport.addToPacket(ProcessingConstants.CLUSTER_NAME, CLUSTER_NAME);
    getConfigurator()
        .configureCluster(CLUSTER_NAME)
        .withAdditionalVolumeMount("volume1", "/destination-path1")
        .withAdditionalVolumeMount("volume2", "/destination-path2");

    assertThat(
        getCreatedPodSpecContainer().getVolumeMounts(),
        allOf(
            hasVolumeMount("volume1", "/destination-path1"),
            hasVolumeMount("volume2", "/destination-path2")));
  }

  @Test
  public void whenServerHasAdditionalVolumes_createManagedPodWithThem() {
    configureServer(getConfigurator(), SERVER_NAME)
        .withAdditionalVolume("volume1", "/source-path1")
        .withAdditionalVolume("volume2", "/source-path2");

    assertThat(
        getCreatedPod().getSpec().getVolumes(),
        allOf(hasVolume("volume1", "/source-path1"), hasVolume("volume2", "/source-path2")));
  }

  @Test
  public void whenServerHasAdditionalVolumeMounts_createManagedPodWithThem() {
    configureServer(getConfigurator(), SERVER_NAME)
        .withAdditionalVolumeMount("volume1", "/destination-path1")
        .withAdditionalVolumeMount("volume2", "/destination-path2");

    assertThat(
        getCreatedPodSpecContainer().getVolumeMounts(),
        allOf(
            hasVolumeMount("volume1", "/destination-path1"),
            hasVolumeMount("volume2", "/destination-path2")));
  }

  @Test
  public void whenPodHasDuplicateVolumes_createManagedPodWithCombination() {
    getConfigurator()
        .withAdditionalVolume("volume1", "/domain-path1")
        .withAdditionalVolume("volume2", "/domain-path2")
        .withAdditionalVolume("volume3", "/domain-path3")
        .configureServer(SERVER_NAME)
        .withAdditionalVolume("volume3", "/server-path");

    testSupport.addToPacket(ProcessingConstants.CLUSTER_NAME, CLUSTER_NAME);
    getConfigurator()
        .configureCluster(CLUSTER_NAME)
        .withAdditionalVolume("volume2", "/cluster-path");

    assertThat(
        getCreatedPod().getSpec().getVolumes(),
        allOf(
            hasVolume("volume1", "/domain-path1"),
            hasVolume("volume2", "/cluster-path"),
            hasVolume("volume3", "/server-path")));
  }

  @Test
  public void whenPodHasDuplicateVolumeMounts_createManagedPodWithCombination() {
    getConfigurator()
        .withAdditionalVolumeMount("volume1", "/domain-path1")
        .withAdditionalVolumeMount("volume2", "/domain-path2")
        .withAdditionalVolumeMount("volume3", "/domain-path3")
        .configureServer(SERVER_NAME)
        .withAdditionalVolumeMount("volume3", "/server-path");

    testSupport.addToPacket(ProcessingConstants.CLUSTER_NAME, CLUSTER_NAME);
    getConfigurator()
        .configureCluster(CLUSTER_NAME)
        .withAdditionalVolumeMount("volume2", "/cluster-path");

    assertThat(
        getCreatedPodSpecContainer().getVolumeMounts(),
        allOf(
            hasVolumeMount("volume1", "/domain-path1"),
            hasVolumeMount("volume2", "/cluster-path"),
            hasVolumeMount("volume3", "/server-path")));
  }

  @Test
  public void whenDesiredStateIsAdmin_createPodWithStartupModeEnvironment() {
    getConfigurator().withServerStartState(ADMIN_STATE);

    assertThat(
        getCreatedPodSpecContainer().getEnv(), allOf(hasEnvVar("STARTUP_MODE", ADMIN_STATE)));
  }

  @Test
  public void whenServerDesiredStateIsAdmin_createPodWithStartupModeEnvironment() {
    getConfigurator().configureServer(SERVER_NAME).withServerStartState(ADMIN_STATE);

    assertThat(
        getCreatedPodSpecContainer().getEnv(), allOf(hasEnvVar("STARTUP_MODE", ADMIN_STATE)));
  }

  @Test
  public void whenDesiredStateIsRunningServerIsAdmin_createPodWithStartupModeEnvironment() {
    getConfigurator()
        .withServerStartState(RUNNING_STATE)
        .configureServer(SERVER_NAME)
        .withServerStartState(ADMIN_STATE);

    assertThat(
        getCreatedPodSpecContainer().getEnv(), allOf(hasEnvVar("STARTUP_MODE", ADMIN_STATE)));
  }

  @Test
  public void whenDesiredStateIsAdminServerIsRunning_createPodWithStartupModeEnvironment() {
    getConfigurator()
        .withServerStartState(ADMIN_STATE)
        .configureServer(SERVER_NAME)
        .withServerStartState(RUNNING_STATE);

    assertThat(
        getCreatedPodSpecContainer().getEnv(), not(allOf(hasEnvVar("STARTUP_MODE", ADMIN_STATE))));
  }

  @Test
  public void whenClusterDesiredStateIsAdmin_createPodWithStartupModeEnvironment() {
    getConfigurator().configureServer(SERVER_NAME);

    testSupport.addToPacket(ProcessingConstants.CLUSTER_NAME, CLUSTER_NAME);
    getConfigurator().configureCluster(CLUSTER_NAME).withServerStartState(ADMIN_STATE);

    assertThat(
        getCreatedPodSpecContainer().getEnv(), allOf(hasEnvVar("STARTUP_MODE", ADMIN_STATE)));
  }

  @Test
  public void whenClusterDesiredStateIsRunningServerIsAdmin_createPodWithStartupModeEnvironment() {
    getConfigurator().configureServer(SERVER_NAME).withServerStartState(ADMIN_STATE);

    testSupport.addToPacket(ProcessingConstants.CLUSTER_NAME, CLUSTER_NAME);
    getConfigurator().configureCluster(CLUSTER_NAME).withServerStartState(RUNNING_STATE);

    assertThat(
        getCreatedPodSpecContainer().getEnv(), allOf(hasEnvVar("STARTUP_MODE", ADMIN_STATE)));
  }

  @Test
  public void whenClusterDesiredStateIsAdminServerIsRunning_createPodWithStartupModeEnvironment() {
    getConfigurator().configureServer(SERVER_NAME).withServerStartState(RUNNING_STATE);

    testSupport.addToPacket(ProcessingConstants.CLUSTER_NAME, CLUSTER_NAME);
    getConfigurator().configureCluster(CLUSTER_NAME).withServerStartState(ADMIN_STATE);

    assertThat(
        getCreatedPodSpecContainer().getEnv(), not(allOf(hasEnvVar("STARTUP_MODE", ADMIN_STATE))));
  }

  @Test
  public void whenDomainHasInitContainers_createPodWithThem() {
    getConfigurator()
        .withInitContainer(
            createContainer(
                "container1", "busybox", "sh", "-c", "echo managed server && sleep 120"))
        .withInitContainer(createContainer("container2", "oraclelinux", "ls /oracle"));

    assertThat(
        getCreatedPodSpecInitContainers(),
        allOf(
            hasContainer("container1", "busybox", "sh", "-c", "echo managed server && sleep 120"),
            hasContainer("container2", "oraclelinux", "ls /oracle")));
  }

  @Test
  public void whenServerHasInitContainers_createPodWithThem() {
    getConfigurator()
        .configureServer(SERVER_NAME)
        .withInitContainer(
            createContainer(
                "container1", "busybox", "sh", "-c", "echo managed server && sleep 120"))
        .withInitContainer(createContainer("container2", "oraclelinux", "ls /oracle"));

    assertThat(
        getCreatedPodSpecInitContainers(),
        allOf(
            hasContainer("container1", "busybox", "sh", "-c", "echo managed server && sleep 120"),
            hasContainer("container2", "oraclelinux", "ls /oracle")));
  }

  @Test
  public void whenServerHasDuplicateInitContainers_createPodWithCombination() {
    getConfigurator()
        .withInitContainer(
            createContainer(
                "container1", "busybox", "sh", "-c", "echo managed server && sleep 120"))
        .withInitContainer(createContainer("container2", "oraclelinux", "ls /top"))
        .configureServer(SERVER_NAME)
        .withInitContainer(createContainer("container2", "oraclelinux", "ls /oracle"));

    assertThat(
        getCreatedPodSpecInitContainers(),
        allOf(
            hasContainer("container1", "busybox", "sh", "-c", "echo managed server && sleep 120"),
            hasContainer("container2", "oraclelinux", "ls /oracle")));
  }

  @Test
  public void whenClusterHasInitContainers_createPodWithThem() {
    getConfigurator()
        .configureCluster(CLUSTER_NAME)
        .withInitContainer(
            createContainer(
                "container1", "busybox", "sh", "-c", "echo managed server && sleep 120"))
        .withInitContainer(createContainer("container2", "oraclelinux", "ls /oracle"));
    testSupport.addToPacket(ProcessingConstants.CLUSTER_NAME, CLUSTER_NAME);

    assertThat(
        getCreatedPodSpecInitContainers(),
        allOf(
            hasContainer("container1", "busybox", "sh", "-c", "echo managed server && sleep 120"),
            hasContainer("container2", "oraclelinux", "ls /oracle")));
  }

  @Test
  public void whenServerAndClusterHasDuplicateInitContainers_createPodWithCombination() {
    getConfigurator()
        .withInitContainer(
            createContainer(
                "container1", "busybox", "sh", "-c", "echo managed server && sleep 120"))
        .withInitContainer(createContainer("container2", "oraclelinux", "ls /top"))
        .configureServer(SERVER_NAME)
        .withInitContainer(createContainer("container2", "oraclelinux", "ls /oracle"));
    getConfigurator()
        .configureCluster(CLUSTER_NAME)
        .withInitContainer(
            createContainer("container1", "busybox", "sh", "-c", "echo cluster && sleep 120"))
        .withInitContainer(createContainer("container3", "oraclelinux", "ls /cluster"));
    testSupport.addToPacket(ProcessingConstants.CLUSTER_NAME, CLUSTER_NAME);

    assertThat(
        getCreatedPodSpecInitContainers(),
        allOf(
            hasContainer("container1", "busybox", "sh", "-c", "echo cluster && sleep 120"),
            hasContainer("container2", "oraclelinux", "ls /oracle"),
            hasContainer("container3", "oraclelinux", "ls /cluster")));
  }

  @Test
  public void whenDomainHasContainers_createPodWithThem() {
    getConfigurator()
        .withContainer(
            createContainer(
                "container1", "busybox", "sh", "-c", "echo managed server && sleep 120"))
        .withContainer(createContainer("container2", "oraclelinux", "ls /oracle"));

    assertThat(
        getCreatedPodSpecContainers(),
        allOf(
            hasContainer("container1", "busybox", "sh", "-c", "echo managed server && sleep 120"),
            hasContainer("container2", "oraclelinux", "ls /oracle")));
  }

  @Test
  public void whenServerHasContainers_createPodWithThem() {
    getConfigurator()
        .configureServer(SERVER_NAME)
        .withContainer(
            createContainer(
                "container1", "busybox", "sh", "-c", "echo managed server && sleep 120"))
        .withContainer(createContainer("container2", "oraclelinux", "ls /oracle"));

    assertThat(
        getCreatedPodSpecContainers(),
        allOf(
            hasContainer("container1", "busybox", "sh", "-c", "echo managed server && sleep 120"),
            hasContainer("container2", "oraclelinux", "ls /oracle")));
  }

  @Test
  public void whenServerHasDuplicateContainers_createPodWithCombination() {
    getConfigurator()
        .withContainer(
            createContainer(
                "container1", "busybox", "sh", "-c", "echo managed server && sleep 120"))
        .withContainer(createContainer("container2", "oraclelinux", "ls /top"))
        .configureServer(SERVER_NAME)
        .withContainer(createContainer("container2", "oraclelinux", "ls /oracle"));

    assertThat(
        getCreatedPodSpecContainers(),
        allOf(
            hasContainer("container1", "busybox", "sh", "-c", "echo managed server && sleep 120"),
            hasContainer("container2", "oraclelinux", "ls /oracle")));
  }

  @Test
  public void whenClusterHasContainers_createPodWithThem() {
    getConfigurator()
        .configureCluster(CLUSTER_NAME)
        .withContainer(
            createContainer(
                "container1", "busybox", "sh", "-c", "echo managed server && sleep 120"))
        .withContainer(createContainer("container2", "oraclelinux", "ls /oracle"));
    testSupport.addToPacket(ProcessingConstants.CLUSTER_NAME, CLUSTER_NAME);

    assertThat(
        getCreatedPodSpecContainers(),
        allOf(
            hasContainer("container1", "busybox", "sh", "-c", "echo managed server && sleep 120"),
            hasContainer("container2", "oraclelinux", "ls /oracle")));
  }

  @Test
  public void whenServerAndClusterHasDuplicateContainers_createPodWithCombination() {
    getConfigurator()
        .withContainer(
            createContainer(
                "container1", "busybox", "sh", "-c", "echo managed server && sleep 120"))
        .withContainer(createContainer("container2", "oraclelinux", "ls /top"))
        .configureServer(SERVER_NAME)
        .withContainer(createContainer("container2", "oraclelinux", "ls /oracle"));
    getConfigurator()
        .configureCluster(CLUSTER_NAME)
        .withContainer(
            createContainer("container1", "busybox", "sh", "-c", "echo cluster && sleep 120"))
        .withContainer(createContainer("container3", "oraclelinux", "ls /cluster"));
    testSupport.addToPacket(ProcessingConstants.CLUSTER_NAME, CLUSTER_NAME);

    assertThat(
        getCreatedPodSpecContainers(),
        allOf(
            hasContainer("container1", "busybox", "sh", "-c", "echo cluster && sleep 120"),
            hasContainer("container2", "oraclelinux", "ls /oracle"),
            hasContainer("container3", "oraclelinux", "ls /cluster")));
  }

  @Test
  public void whenDomainHasLabels_createManagedPodWithThem() {
    getConfigurator()
        .withPodLabel("label1", "domain-label-value1")
        .withPodLabel("label2", "domain-label-value2");

    Map<String, String> podLabels = getCreatedPod().getMetadata().getLabels();
    assertThat(podLabels, hasEntry("label1", "domain-label-value1"));
    assertThat(podLabels, hasEntry("label2", "domain-label-value2"));
  }

  @Test
  public void whenDomainHasAnnotations_createManagedPodWithThem() {
    getConfigurator()
        .withPodAnnotation("annotation1", "domain-annotation-value1")
        .withPodAnnotation("annotation2", "domain-annotation-value2");
    Map<String, String> podAnnotations = getCreatedPod().getMetadata().getAnnotations();
    assertThat(podAnnotations, hasEntry("annotation1", "domain-annotation-value1"));
    assertThat(podAnnotations, hasEntry("annotation2", "domain-annotation-value2"));
  }

  @Test
  public void whenClusterHasLabels_createManagedPodWithThem() {
    testSupport.addToPacket(ProcessingConstants.CLUSTER_NAME, CLUSTER_NAME);
    getConfigurator()
        .configureCluster(CLUSTER_NAME)
        .withPodLabel("label1", "cluster-label-value1")
        .withPodLabel("label2", "cluster-label-value2");

    Map<String, String> podLabels = getCreatedPod().getMetadata().getLabels();
    assertThat(podLabels, hasEntry("label1", "cluster-label-value1"));
    assertThat(podLabels, hasEntry("label2", "cluster-label-value2"));
  }

  @Test
  public void whenClusterHasRestartVersion_createManagedPodWithRestartVersionLabel() {
    testSupport.addToPacket(ProcessingConstants.CLUSTER_NAME, CLUSTER_NAME);
    getConfigurator().configureCluster(CLUSTER_NAME).withRestartVersion("clusterRestartV1");

    Map<String, String> podLabels = getCreatedPod().getMetadata().getLabels();
    assertThat(podLabels, hasEntry(LabelConstants.CLUSTERRESTARTVERSION_LABEL, "clusterRestartV1"));
    assertThat(podLabels, hasKey(not(LabelConstants.DOMAINRESTARTVERSION_LABEL)));
    assertThat(podLabels, hasKey(not(LabelConstants.SERVERRESTARTVERSION_LABEL)));
  }

  @Test
  public void whenDomainHasRestartVersion_createManagedPodWithRestartVersionLabel() {
    testSupport.addToPacket(ProcessingConstants.CLUSTER_NAME, CLUSTER_NAME);
    getConfigurator().withRestartVersion("domainRestartV1");

    Map<String, String> podLabels = getCreatedPod().getMetadata().getLabels();
    assertThat(podLabels, hasEntry(LabelConstants.DOMAINRESTARTVERSION_LABEL, "domainRestartV1"));
    assertThat(podLabels, hasKey(not(LabelConstants.CLUSTERRESTARTVERSION_LABEL)));
    assertThat(podLabels, hasKey(not(LabelConstants.SERVERRESTARTVERSION_LABEL)));
  }

  @Test
  public void whenClusterHasAnnotations_createManagedPodWithThem() {
    testSupport.addToPacket(ProcessingConstants.CLUSTER_NAME, CLUSTER_NAME);
    getConfigurator()
        .configureCluster(CLUSTER_NAME)
        .withPodAnnotation("annotation1", "cluster-annotation-value1")
        .withPodAnnotation("annotation2", "cluster-annotation-value2");

    Map<String, String> podAnnotations = getCreatedPod().getMetadata().getAnnotations();
    assertThat(podAnnotations, hasEntry("annotation1", "cluster-annotation-value1"));
    assertThat(podAnnotations, hasEntry("annotation2", "cluster-annotation-value2"));
  }

  @Test
  public void whenServerHasLabels_createManagedPodWithThem() {
    configureServer(getConfigurator(), SERVER_NAME)
        .withPodLabel("label1", "server-label-value1")
        .withPodLabel("label2", "server-label-value2");

    Map<String, String> podLabels = getCreatedPod().getMetadata().getLabels();
    assertThat(podLabels, hasEntry("label1", "server-label-value1"));
    assertThat(podLabels, hasEntry("label2", "server-label-value2"));
  }

  @Test
  public void whenServerHasAnnotations_createManagedPodWithThem() {
    configureServer(getConfigurator(), SERVER_NAME)
        .withPodAnnotation("annotation1", "server-annotation-value1")
        .withPodAnnotation("annotation2", "server-annotation-value2");

    Map<String, String> podAnnotations = getCreatedPod().getMetadata().getAnnotations();
    assertThat(podAnnotations, hasEntry("annotation1", "server-annotation-value1"));
    assertThat(podAnnotations, hasEntry("annotation2", "server-annotation-value2"));
  }

  @Test
  public void whenPodHasDuplicateLabels_createManagedPodWithCombination() {
    getConfigurator()
        .withPodLabel("label1", "domain-label-value1")
        .withPodLabel("label2", "domain-label-value2")
        .withPodLabel("label3", "domain-label-value3")
        .configureServer(SERVER_NAME)
        .withPodLabel("label3", "server-label-value1");

    testSupport.addToPacket(ProcessingConstants.CLUSTER_NAME, CLUSTER_NAME);
    getConfigurator()
        .configureCluster(CLUSTER_NAME)
        .withPodLabel("label2", "cluster-label-value1")
        .withPodLabel("label3", "cluster-label-value2");

    Map<String, String> podLabels = getCreatedPod().getMetadata().getLabels();
    assertThat(podLabels, hasEntry("label1", "domain-label-value1"));
    assertThat(podLabels, hasEntry("label2", "cluster-label-value1"));
    assertThat(podLabels, hasEntry("label3", "server-label-value1"));
  }

  @Test
  public void whenPodHasDuplicateAnnotations_createManagedPodWithCombination() {
    getConfigurator()
        .withPodAnnotation("annotation1", "domain-annotation-value1")
        .withPodAnnotation("annotation2", "domain-annotation-value2")
        .withPodAnnotation("annotation3", "domain-annotation-value3")
        .configureServer(SERVER_NAME)
        .withPodAnnotation("annotation3", "server-annotation-value1");

    testSupport.addToPacket(ProcessingConstants.CLUSTER_NAME, CLUSTER_NAME);
    getConfigurator()
        .configureCluster(CLUSTER_NAME)
        .withPodAnnotation("annotation2", "cluster-annotation-value1")
        .withPodAnnotation("annotation3", "cluster-annotation-value2");

    Map<String, String> podAnnotations = getCreatedPod().getMetadata().getAnnotations();
    assertThat(podAnnotations, hasEntry("annotation1", "domain-annotation-value1"));
    assertThat(podAnnotations, hasEntry("annotation2", "cluster-annotation-value1"));
    assertThat(podAnnotations, hasEntry("annotation3", "server-annotation-value1"));
  }

  @Test
  public void whenPodHasCustomLabelConflictWithInternal_createManagedPodWithInternal() {
    getConfigurator()
        .withPodLabel(LabelConstants.RESOURCE_VERSION_LABEL, "domain-label-value1")
        .configureServer((SERVER_NAME))
        .withPodLabel(LabelConstants.CREATEDBYOPERATOR_LABEL, "server-label-value1");

    Map<String, String> podLabels = getCreatedPod().getMetadata().getLabels();
    assertThat(
        podLabels,
        hasEntry(LabelConstants.RESOURCE_VERSION_LABEL, VersionConstants.DEFAULT_DOMAIN_VERSION));
    assertThat(podLabels, hasEntry(LabelConstants.CREATEDBYOPERATOR_LABEL, "true"));
  }

  @Override
  void setServerPort(int port) {
    getServerTopology().setListenPort(port);
  }

  @Override
  protected void verifyPodReplaced() {
    assertThat(computePodsToRoll(), not(anEmptyMap()));
  }

  private Map<String, StepAndPacket> computePodsToRoll() {
    Map<String, StepAndPacket> rolling = new HashMap<>();
    testSupport.addToPacket(SERVERS_TO_ROLL, rolling);

    testSupport.runSteps(getStepFactory(), terminalStep);
    return rolling;
  }

  @Override
  protected void verifyPodNotReplacedWhen(PodMutator mutator) {
    /**/
    V1Pod existingPod = createPod(testSupport.getPacket());
    /*/
    V1Pod existingPod = createPodModel();
    /**/
    mutator.mutate(existingPod);
    initializeExistingPod(existingPod);

    Map<String, StepAndPacket> rolling = computePodsToRoll();

    assertThat(rolling, is(anEmptyMap()));
    assertThat(logRecords, containsFine(getExistsMessageKey()));
  }

  @Override
  V1Pod createPod(Packet packet) {
    return new PodHelper.ManagedPodStepContext(null, packet).getPodModel();
  }

  @Override
  protected ServerConfigurator configureServer(DomainConfigurator configurator, String serverName) {
    return configurator.configureServer(serverName);
  }
}
