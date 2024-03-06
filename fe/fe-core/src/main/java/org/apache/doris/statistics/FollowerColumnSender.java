// Licensed to the Apache Software Foundation (ASF) under one
// or more contributor license agreements.  See the NOTICE file
// distributed with this work for additional information
// regarding copyright ownership.  The ASF licenses this file
// to you under the Apache License, Version 2.0 (the
// "License"); you may not use this file except in compliance
// with the License.  You may obtain a copy of the License at
//
//   http://www.apache.org/licenses/LICENSE-2.0
//
// Unless required by applicable law or agreed to in writing,
// software distributed under the License is distributed on an
// "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
// KIND, either express or implied.  See the License for the
// specific language governing permissions and limitations
// under the License.

package org.apache.doris.statistics;

import org.apache.doris.catalog.Env;
import org.apache.doris.common.ClientPool;
import org.apache.doris.common.util.MasterDaemon;
import org.apache.doris.ha.FrontendNodeType;
import org.apache.doris.statistics.util.StatisticsUtil;
import org.apache.doris.system.Frontend;
import org.apache.doris.thrift.FrontendService;
import org.apache.doris.thrift.TNetworkAddress;
import org.apache.doris.thrift.TQueryColumn;
import org.apache.doris.thrift.TSyncQueryColumns;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.net.InetSocketAddress;
import java.util.List;
import java.util.stream.Collectors;

public class FollowerColumnSender extends MasterDaemon {

    private static final Logger LOG = LogManager.getLogger(FollowerColumnSender.class);

    public static final long INTERVAL = 5000;

    public FollowerColumnSender() {
        super("Follower Column Sender", INTERVAL);
    }

    @Override
    protected void runAfterCatalogReady() {
        if (!StatisticsUtil.enableAutoAnalyze()) {
            return;
        }
        if (Env.getCurrentEnv().isMaster()) {
            return;
        }
        if (Env.isCheckpointThread()) {
            return;
        }
        send();
    }

    protected void send() {
        if (Env.getCurrentEnv().isMaster()) {
            return;
        }
        Env currentEnv = Env.getCurrentEnv();
        AnalysisManager analysisManager = currentEnv.getAnalysisManager();
        if (analysisManager.highPriorityColumns.isEmpty() && analysisManager.midPriorityColumns.isEmpty()) {
            return;
        }
        List<TQueryColumn> highPriorityColumns
                = analysisManager.highPriorityColumns
                .stream()
                .map(HighPriorityColumn::toThrift)
                .collect(Collectors.toList());
        List<TQueryColumn> midPriorityColumns
                = analysisManager.midPriorityColumns
                .stream()
                .map(HighPriorityColumn::toThrift)
                .collect(Collectors.toList());
        analysisManager.highPriorityColumns.clear();
        analysisManager.midPriorityColumns.clear();
        TSyncQueryColumns queryColumns = new TSyncQueryColumns();
        queryColumns.highPriorityColumns = highPriorityColumns;
        queryColumns.midPriorityColumns = midPriorityColumns;
        Frontend master = null;
        try {
            InetSocketAddress masterAddress = currentEnv.getHaProtocol().getLeader();
            for (Frontend fe : currentEnv.getFrontends(FrontendNodeType.FOLLOWER)) {
                InetSocketAddress socketAddress = new InetSocketAddress(fe.getHost(), fe.getEditLogPort());
                if (socketAddress.equals(masterAddress)) {
                    master = fe;
                    break;
                }
            }
        } catch (Exception e) {
            LOG.warn("Failed to find master FE.", e);
            return;
        }

        if (master == null) {
            LOG.warn("No master found in cluster.");
            return;
        }
        TNetworkAddress address = new TNetworkAddress(master.getHost(), master.getRpcPort());
        FrontendService.Client client = null;
        try {
            client = ClientPool.frontendPool.borrowObject(address);
            client.syncQueryColumns(queryColumns);
            LOG.info("Send {} high priority columns and {} mid priority columns to master.",
                    highPriorityColumns.size(), midPriorityColumns.size());
        } catch (Throwable t) {
            LOG.warn("Failed to sync stats to master: {}", address, t);
        } finally {
            if (client != null) {
                ClientPool.frontendPool.returnObject(address, client);
            }
        }
    }
}
