/**
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.apache.hadoop.hdfs;

import java.net.URI;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import org.apache.hadoop.conf.Configuration;
import org.apache.hadoop.fs.FileSystem;
import org.apache.hadoop.io.Text;
import org.apache.hadoop.security.token.DtFetcher;
import org.apache.hadoop.security.token.Token;


/**
 *  DtFetcher is an interface which permits the abstraction and separation of
 *  delegation token fetch implementaions across different packages and
 *  compilation units.  Resolution of fetcher impl will be done at runtime.
 */
public class HdfsDtFetcher implements DtFetcher {
  private static final Log LOG = LogFactory.getLog(HdfsDtFetcher.class);

  private static final String SERVICE_NAME = "hdfs";

  /**
   * Returns the service name, which is also a valid URL prefix.
   */
  public Text getServiceName() {
    return new Text(SERVICE_NAME);
  }

  public Token<?> getDelegationToken(String renewer, String url)
      throws Exception {
    if (!url.startsWith(getServiceName().toString())) {
      url = getServiceName().toString() + "://" + url;
    }
    FileSystem fs = FileSystem.get(URI.create(url), new Configuration());
    return fs.getDelegationToken(renewer);
  }
}
