// Copyright 2018, 2019, Oracle Corporation and/or its affiliates.  All rights reserved.
// Licensed under the Universal Permissive License v 1.0 as shown at
// http://oss.oracle.com/licenses/upl.

package oracle.kubernetes.json;

import java.util.List;

class TransitiveObject {
  private ReferencingObject referrer;

  private List<SimpleObject> simpleObjects;
}
