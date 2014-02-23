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

package org.apache.cloudstack.network.contrail.management;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;

import javax.ejb.Local;
import javax.inject.Inject;

import org.apache.log4j.Logger;

import org.apache.cloudstack.network.element.InternalLoadBalancerElement;

import com.cloud.dc.DataCenter;
import com.cloud.dc.DataCenter.NetworkType;
import com.cloud.network.Network;
import com.cloud.network.Network.Capability;
import com.cloud.network.Network.Provider;
import com.cloud.network.Network.Service;
import com.cloud.network.NetworkModel;
import com.cloud.network.Networks.TrafficType;
import com.cloud.network.PhysicalNetworkServiceProvider;
import com.cloud.network.element.NetworkElement;
import com.cloud.network.element.VirtualRouterElement;
import com.cloud.network.rules.LoadBalancerContainer;
import com.cloud.network.rules.LoadBalancerContainer.Scheme;
import com.cloud.utils.db.EntityManager;

@Local(value = {NetworkElement.class})
public class ContrailLBElementImpl extends InternalLoadBalancerElement {
    private static final Logger s_logger = Logger.getLogger(ContrailLBElementImpl.class);
    protected static final Map<Service, Map<Capability, String>> capabilities = setCapabilities();
    private static ContrailLBElementImpl internalLbElement = null;

    @Inject NetworkModel _ntwkModel;
    @Inject
    EntityManager _entityMgr;

    public static ContrailLBElementImpl getInstance() {
        if ( internalLbElement == null) {
            internalLbElement = new ContrailLBElementImpl();
        }
        return internalLbElement;
     }
    
    
    private boolean canHandle(Network config, Scheme lbScheme) {
        //works in Advance zone only
        DataCenter dc = _entityMgr.findById(DataCenter.class, config.getDataCenterId());
        if (dc.getNetworkType() != NetworkType.Advanced) {
            s_logger.trace("Not hanling zone of network type " + dc.getNetworkType());
            return false;
        }
        if (config.getGuestType() != Network.GuestType.Isolated || config.getTrafficType() != TrafficType.Guest) {
            s_logger.trace("Not handling network with Type  " + config.getGuestType() + " and traffic type " + config.getTrafficType());
            return false;
        }
        
        Map<Capability, String> lbCaps = getCapabilities().get(Service.Lb);
        if (!lbCaps.isEmpty()) {
            String schemeCaps = lbCaps.get(Capability.LbSchemes);
            if (schemeCaps != null && lbScheme != null) {
                if (!schemeCaps.contains(lbScheme.toString())) {
                    s_logger.debug("Scheme " + lbScheme.toString() + " is not supported by the provider " + getProvider().getName());
                    return false;
                }
            }
        }
        
        if (!_ntwkModel.isProviderSupportServiceInNetwork(config.getId(), Service.Lb, getProvider())) {
            s_logger.trace("Element " + getProvider().getName() + " doesn't support service " + Service.Lb
                    + " in the network " + config);
            return false;
        }
        return true;
    }

    
    @Override
    public Map<Service, Map<Capability, String>> getCapabilities() {
        return capabilities;
    }

    
    @Override
    public Provider getProvider() {
        return Provider.JuniperContrailLbRouter;
    }

    @Override
    public boolean isReady(PhysicalNetworkServiceProvider provider) {
        return true;
    }

    @Override
    public boolean canEnableIndividualServices() {
        return true;
    }

    
    @Override
    public boolean verifyServicesCombination(Set<Service> services) {
        return true;
    }

    private static Map<Service, Map<Capability, String>> setCapabilities() {
        Map<Service, Map<Capability, String>> capabilities = new HashMap<Service, Map<Capability, String>>();

        // Set capabilities for LB service
        Map<Capability, String> lbCapabilities = new HashMap<Capability, String>();
        lbCapabilities.put(Capability.SupportedLBAlgorithms, "roundrobin,leastconn,source");
        lbCapabilities.put(Capability.SupportedLBIsolation, "dedicated");
        lbCapabilities.put(Capability.SupportedProtocols, "tcp, udp");
        lbCapabilities.put(Capability.SupportedStickinessMethods, VirtualRouterElement.getHAProxyStickinessCapability());
        lbCapabilities.put(Capability.LbSchemes, LoadBalancerContainer.Scheme.Internal.toString());

        capabilities.put(Service.Lb, lbCapabilities);
        return capabilities;
    }


}
