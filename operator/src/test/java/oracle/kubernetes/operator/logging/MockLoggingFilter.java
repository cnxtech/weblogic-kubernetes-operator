// Copyright 2019, Oracle Corporation and/or its affiliates.  All rights reserved.
// Licensed under the Universal Permissive License v 1.0 as shown at
// http://oss.oracle.com/licenses/upl.

package oracle.kubernetes.operator.logging;

public class MockLoggingFilter implements LoggingFilter {

  final boolean returnValue;

  public MockLoggingFilter(boolean returnValue) {
    this.returnValue = returnValue;
  }

  @Override
  public boolean canLog(String msg) {
    return returnValue;
  }

  public static MockLoggingFilter createWithReturnValue(boolean returnValue) {
    return new MockLoggingFilter(returnValue);
  }
}
