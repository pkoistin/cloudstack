/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */
package org.apache.cloudstack.framework.messaging.server;

import javax.annotation.PostConstruct;
import javax.inject.Inject;

import org.apache.cloudstack.framework.messaging.EventBus;
import org.apache.cloudstack.framework.messaging.EventDispatcher;
import org.apache.cloudstack.framework.messaging.EventHandler;
import org.apache.cloudstack.framework.messaging.RpcProvider;
import org.apache.cloudstack.framework.messaging.RpcServerCall;
import org.apache.cloudstack.framework.messaging.RpcServiceDispatcher;
import org.apache.cloudstack.framework.messaging.RpcServiceHandler;
import org.apache.log4j.Logger;
import org.springframework.stereotype.Component;

@Component
public class SampleManagerComponent2 {
    private static final Logger s_logger = Logger.getLogger(SampleManagerComponent2.class);
	
	@Inject
	private EventBus _eventBus;

	@Inject
	private RpcProvider _rpcProvider;

	public SampleManagerComponent2() {
	}
	
	@PostConstruct
	public void init() {
		_rpcProvider.registerRpcServiceEndpoint(
			RpcServiceDispatcher.getDispatcher(this));
			
		// subscribe to all network events (for example)
		_eventBus.subscribe("storage", 
			EventDispatcher.getDispatcher(this));
	}
	
	@RpcServiceHandler(command="StoragePrepare")
	void onStartCommand(RpcServerCall call) {
		s_logger.info("Reevieved StoragePrpare call");
		SampleStoragePrepareCommand cmd = call.getCommandArgument();
		
		s_logger.info("StoragePrepare command arg. pool: " + cmd.getStoragePool() + ", vol: " + cmd.getVolumeId());
		SampleStoragePrepareAnswer answer = new SampleStoragePrepareAnswer();
		
		call.completeCall(answer);
	}
	
	@EventHandler(topic="storage.prepare")
	void onPrepareNetwork(String sender, String topic, Object args) {
	}
}
