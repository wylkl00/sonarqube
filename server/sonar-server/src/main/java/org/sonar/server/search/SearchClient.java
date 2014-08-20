/*
 * SonarQube, open source software quality management tool.
 * Copyright (C) 2008-2014 SonarSource
 * mailto:contact AT sonarsource DOT com
 *
 * SonarQube is free software; you can redistribute it and/or
 * modify it under the terms of the GNU Lesser General Public
 * License as published by the Free Software Foundation; either
 * version 3 of the License, or (at your option) any later version.
 *
 * SonarQube is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
 * Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with this program; if not, write to the Free Software Foundation,
 * Inc., 51 Franklin Street, Fifth Floor, Boston, MA  02110-1301, USA.
 */

package org.sonar.server.search;

import com.google.common.annotations.VisibleForTesting;
import org.apache.commons.lang.StringUtils;
import org.elasticsearch.action.ActionRequestBuilder;
import org.elasticsearch.action.ActionResponse;
import org.elasticsearch.action.admin.cluster.health.ClusterHealthStatus;
import org.elasticsearch.action.admin.cluster.stats.ClusterStatsNodes;
import org.elasticsearch.action.admin.cluster.stats.ClusterStatsResponse;
import org.elasticsearch.client.transport.TransportClient;
import org.elasticsearch.common.logging.ESLoggerFactory;
import org.elasticsearch.common.logging.slf4j.Slf4jESLoggerFactory;
import org.elasticsearch.common.settings.ImmutableSettings;
import org.elasticsearch.common.transport.InetSocketTransportAddress;
import org.elasticsearch.common.xcontent.ToXContent;
import org.elasticsearch.common.xcontent.XContentBuilder;
import org.elasticsearch.common.xcontent.XContentFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.sonar.api.config.Settings;
import org.sonar.core.profiling.Profiling;
import org.sonar.core.profiling.StopWatch;

/**
 * ElasticSearch Node used to connect to index.
 */
public class SearchClient extends TransportClient {

  private static final Logger LOGGER = LoggerFactory.getLogger(SearchClient.class);

  private static final String DEFAULT_HEALTH_TIMEOUT = "30s";

  private final Settings settings;
  private final String healthTimeout;

  protected final Profiling profiling;

  public SearchClient(Settings settings) {
    this(settings, DEFAULT_HEALTH_TIMEOUT);
  }

  @VisibleForTesting
  SearchClient(Settings settings, String healthTimeout) {
    super(ImmutableSettings.settingsBuilder()
        .put("network.bind_host", "localhost")
        .put("node.rack_id", StringUtils.defaultIfEmpty(settings.getString(IndexProperties.NODE_NAME), "unknown"))
        .put("cluster.name", StringUtils.defaultIfBlank(settings.getString(IndexProperties.CLUSTER_NAME), "sonarqube"))
        .build()
    );
    initLogging();
    this.addTransportAddress(new InetSocketTransportAddress("localhost",
      settings.getInt(IndexProperties.NODE_PORT)));
    this.settings = settings;
    this.healthTimeout = healthTimeout;
    this.profiling = new Profiling(settings);
  }

  public NodeHealth getNodeHealth() {
    NodeHealth health = new NodeHealth();
    ClusterStatsResponse clusterStatsResponse = this.admin().cluster().prepareClusterStats().get();

    // Cluster health
    health.setClusterAvailable(clusterStatsResponse.getStatus() != ClusterHealthStatus.RED);

    ClusterStatsNodes nodesStats = clusterStatsResponse.getNodesStats();

    // JVM Heap Usage
    health.setJvmHeapMax(nodesStats.getJvm().getHeapMax().bytes());
    health.setJvmHeapUsed(nodesStats.getJvm().getHeapUsed().bytes());

    // OS Memory Usage ?

    // Disk Usage
    health.setFsTotal(nodesStats.getFs().getTotal().bytes());
    health.setFsAvailable(nodesStats.getFs().getAvailable().bytes());

    // Ping ?

    // Threads
    health.setJvmThreads(nodesStats.getJvm().getThreads());

    // CPU
    health.setProcessCpuPercent(nodesStats.getProcess().getCpuPercent());

    // Open Files
    health.setOpenFiles(nodesStats.getProcess().getAvgOpenFileDescriptors());

    // Uptime
    health.setJvmUptimeMillis(nodesStats.getJvm().getMaxUpTime().getMillis());

    return health;
  }

  private void initLogging() {
    ESLoggerFactory.setDefaultFactory(new Slf4jESLoggerFactory());
  }

  public <K extends ActionResponse> K execute(ActionRequestBuilder request) {
    StopWatch fullProfile = profiling.start("search", Profiling.Level.FULL);
    K response = null;
    try {

      response = (K) request.get();

      if (profiling.isProfilingEnabled(Profiling.Level.BASIC)) {
        if (ToXContent.class.isAssignableFrom(request.getClass())) {
          XContentBuilder debugResponse = XContentFactory.jsonBuilder();
          debugResponse.startObject();
          ((ToXContent) request).toXContent(debugResponse, ToXContent.EMPTY_PARAMS);
          debugResponse.endObject();
          fullProfile.stop("ES Request: %s", debugResponse.string());
        } else {
          fullProfile.stop("ES Request: %s", request.toString().replaceAll("\n", ""));
        }
      }

      if (profiling.isProfilingEnabled(Profiling.Level.FULL)) {
        if (ToXContent.class.isAssignableFrom(response.getClass())) {
          XContentBuilder debugResponse = XContentFactory.jsonBuilder();
          debugResponse.startObject();
          ((ToXContent) response).toXContent(debugResponse, ToXContent.EMPTY_PARAMS);
          debugResponse.endObject();
          fullProfile.stop("ES Response: %s", debugResponse.string());
        } else {
          fullProfile.stop("ES Response: %s", response.toString());
        }
      }
      return response;
    } catch (Exception e) {
      LOGGER.error("could not execute request: "   + response);
      throw new IllegalStateException("ES error: ", e);

    }
  }
}
