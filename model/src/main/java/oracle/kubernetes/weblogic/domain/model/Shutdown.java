// Copyright 2019, Oracle Corporation and/or its affiliates.  All rights reserved.
// Licensed under the Universal Permissive License v 1.0 as shown at
// http://oss.oracle.com/licenses/upl.

package oracle.kubernetes.weblogic.domain.model;

import static oracle.kubernetes.operator.KubernetesConstants.GRACEFUL_SHUTDOWNTYPE;

import java.util.Optional;
import oracle.kubernetes.json.Description;
import oracle.kubernetes.json.EnumClass;
import oracle.kubernetes.operator.ShutdownType;
import org.apache.commons.lang3.builder.EqualsBuilder;
import org.apache.commons.lang3.builder.HashCodeBuilder;
import org.apache.commons.lang3.builder.ToStringBuilder;

@Description("Shutdown describes the configuration for shutting down a server instance.")
public class Shutdown {
  // Default timeout must stay 30 seconds to match Kubernetes default
  public static final Long DEFAULT_TIMEOUT = 30l;
  public static final Boolean DEFAULT_IGNORESESSIONS = Boolean.FALSE;

  @Description(
      "Tells the operator how to shutdown server instances. Not required."
          + " Defaults to graceful shutdown.")
  @EnumClass(ShutdownType.class)
  private String shutdownType;

  @Description(
      "For graceful shutdown only, number of seconds to wait before aborting in-flight work and shutting down the server. Not required. Defaults to 30 seconds.")
  private Long timeoutSeconds;

  @Description(
      "For graceful shutdown only, indicates to ignore pending HTTP sessions during in-flight work handling. Not required. Defaults to false.")
  private Boolean ignoreSessions;

  public Shutdown() {}

  void copyValues(Shutdown fromShutdown) {
    if (shutdownType == null) {
      shutdownType(fromShutdown.shutdownType);
    }
    if (timeoutSeconds == null) {
      timeoutSeconds(fromShutdown.timeoutSeconds);
    }
    if (ignoreSessions == null) {
      ignoreSessions(fromShutdown.ignoreSessions);
    }
  }

  public String getShutdownType() {
    return Optional.ofNullable(shutdownType).orElse(GRACEFUL_SHUTDOWNTYPE);
  }

  public Shutdown shutdownType(String shutdownType) {
    this.shutdownType = shutdownType;
    return this;
  }

  public Long getTimeoutSeconds() {
    return Optional.ofNullable(timeoutSeconds).orElse(DEFAULT_TIMEOUT);
  }

  public Shutdown timeoutSeconds(Long timeoutSeconds) {
    this.timeoutSeconds = timeoutSeconds;
    return this;
  }

  public Boolean getIgnoreSessions() {
    return Optional.ofNullable(ignoreSessions).orElse(DEFAULT_IGNORESESSIONS);
  }

  public Shutdown ignoreSessions(Boolean ignoreSessions) {
    this.ignoreSessions = ignoreSessions;
    return this;
  }

  @Override
  public String toString() {
    return new ToStringBuilder(this)
        .append("shutdownType", shutdownType)
        .append("timeoutSeconds", timeoutSeconds)
        .append("ignoreSessions", ignoreSessions)
        .toString();
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) {
      return true;
    }

    if (o == null || getClass() != o.getClass()) {
      return false;
    }

    Shutdown that = (Shutdown) o;

    return new EqualsBuilder()
        .append(shutdownType, that.shutdownType)
        .append(timeoutSeconds, that.timeoutSeconds)
        .append(ignoreSessions, that.ignoreSessions)
        .isEquals();
  }

  @Override
  public int hashCode() {
    return new HashCodeBuilder(17, 37)
        .append(shutdownType)
        .append(timeoutSeconds)
        .append(ignoreSessions)
        .toHashCode();
  }
}
