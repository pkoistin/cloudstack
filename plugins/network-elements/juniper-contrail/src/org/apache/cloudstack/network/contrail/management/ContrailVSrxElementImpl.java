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

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import javax.ejb.Local;
import javax.inject.Inject;
import javax.naming.ConfigurationException;

import org.apache.cloudstack.network.contrail.api.command.CreateServiceInstanceCmd;
import org.apache.cloudstack.network.contrail.api.command.DeleteServiceInstanceCmd;
import org.apache.cloudstack.network.contrail.model.InstanceIpModel;
import org.apache.cloudstack.network.contrail.model.VMInterfaceModel;
import org.apache.cloudstack.network.contrail.model.VirtualMachineModel;
import org.apache.cloudstack.network.contrail.model.VirtualNetworkModel;
import org.apache.log4j.Logger;
import org.springframework.stereotype.Component;

import com.cloud.deploy.DeployDestination;
import com.cloud.exception.ConcurrentOperationException;
import com.cloud.exception.InsufficientCapacityException;
import com.cloud.exception.ResourceUnavailableException;
import com.cloud.utils.exception.CloudRuntimeException;
import org.apache.cloudstack.api.ServerApiException;
import com.cloud.network.Network;
import com.cloud.network.Network.Capability;
import com.cloud.network.Network.Provider;
import com.cloud.network.Network;
import com.cloud.network.Network.Service;
import com.cloud.network.Networks.TrafficType;
import com.cloud.network.PhysicalNetworkServiceProvider;
import com.cloud.network.PublicIpAddress;
import com.cloud.network.element.IpDeployer;
import com.cloud.network.element.NetworkElement;
import com.cloud.network.element.SourceNatServiceProvider;
import com.cloud.network.element.StaticNatServiceProvider;
import com.cloud.network.rules.StaticNat;
import com.cloud.offering.NetworkOffering;
import com.cloud.utils.component.AdapterBase;
import com.cloud.vm.NicProfile;
import com.cloud.vm.NicVO;
import com.cloud.vm.ReservationContext;
import com.cloud.vm.VMInstanceVO;
import com.cloud.vm.VirtualMachine;
import com.cloud.vm.VirtualMachineProfile;
import com.cloud.vm.dao.NicDao;
import com.cloud.network.IpAddress;
import com.cloud.server.ConfigurationServer;
import com.cloud.server.ConfigurationServerImpl;
import com.cloud.network.dao.NetworkDao;
import com.cloud.network.dao.NetworkVO;
import com.cloud.utils.db.TransactionLegacy;
import com.cloud.resource.ResourceManager;
import com.cloud.service.ServiceOfferingVO;
import com.cloud.service.dao.ServiceOfferingDao;
import com.cloud.storage.VMTemplateVO;
import com.cloud.storage.dao.VMTemplateDao;
import com.cloud.utils.db.EntityManager;
import com.cloud.user.Account;
import com.cloud.dc.DataCenter;
import com.cloud.template.VirtualMachineTemplate;
import com.cloud.utils.exception.CloudRuntimeException;
import org.apache.cloudstack.api.ApiErrorCode;

@Component
@Local(value = {NetworkElement.class, SourceNatServiceProvider.class})
public class ContrailVSrxElementImpl extends AdapterBase
        implements SourceNatServiceProvider, IpDeployer, NetworkElement {

	private static final Map<Service, Map<Capability, String>> _capabilities = InitCapabilities();

        @Inject ResourceManager _resourceMgr;
        @Inject ConfigurationServer _configServer;
        @Inject NetworkDao _networksDao;
	@Inject ContrailManager _manager;
	@Inject NicDao _nicDao;
	@Inject ServerDBSync  _dbSync;
	@Inject ServiceManager _vrouterService;
	@Inject EntityManager _entityMgr;  // ???
	@Inject VMTemplateDao _tmpltDao;
	@Inject ServiceOfferingDao _serviceOfferingDao;

	private static final Logger s_logger =
			Logger.getLogger(ContrailVSrxElementImpl.class);
	
    // NetworkElement API
    @Override
    public Provider getProvider() {
        return Provider.JuniperContrailvSRX;
    }

    private static Map<Service, Map<Capability, String>> InitCapabilities() {
    	Map<Service, Map<Capability, String>> capabilities = new HashMap<Service, Map<Capability, String>>();
		Map<Capability, String> sourceNatCapabilities = new HashMap<Capability, String>();
		sourceNatCapabilities.put(Capability.SupportedSourceNatTypes, "peraccount");
		sourceNatCapabilities.put(Capability.RedundantRouter, "false");
		capabilities.put(Service.SourceNat, sourceNatCapabilities);

    	return capabilities;
    }

	@Override
	public Map<Service, Map<Capability, String>> getCapabilities() {
		return _capabilities;
	}

	/**
	 * Network add/update.
	 */
	@Override
	public boolean implement(Network network, NetworkOffering offering,
			DeployDestination dest, ReservationContext context)
			throws ConcurrentOperationException, ResourceUnavailableException,
			InsufficientCapacityException {
	    s_logger.debug("ContrailVSrxElement implement: " + network.getName() + ", traffic type: " + network.getTrafficType());

		Account owner = _entityMgr.findById(Account.class, network.getAccountId());
		DataCenter zone = dest.getDataCenter();
		List<ServiceOfferingVO> service_offering_list = _serviceOfferingDao.findPublicServiceOfferings();
		//List<ServiceOfferingVO> service_offering_list = _serviceOfferingDao.findSystemOffering(owner.getDomainId(), true, "domainrouter");
		if (service_offering_list.isEmpty()) {
			throw new CloudRuntimeException("public service_offering list is empty");
		}
		
        ServiceOfferingVO vsrx_compute_offering = null;
        for (ServiceOfferingVO service_offering:service_offering_list) {
            if (service_offering.getRamSize() < 2048 || service_offering.getCpu() < 2)
                continue;
            else {
	            vsrx_compute_offering = service_offering;
                break;
            }
        }

		if (vsrx_compute_offering == null ) {
			throw new CloudRuntimeException("No suitable service/compute offering for vSRX found");
		}
		
		VMTemplateVO tmplt = _tmpltDao.findByTemplateName("Juniper vSRX");
		VirtualMachineTemplate template = _entityMgr.findById(VirtualMachineTemplate.class, tmplt.getId());
		String name = network.getName();
		List<? extends Network> networks = _networksDao.listByZoneAndTrafficType(zone.getId(), TrafficType.Public);
		if (networks.isEmpty() || networks.size() > 1) {
				throw new CloudRuntimeException("Can't find public network in the zone specified");
		}
		Network public_network = networks.get(0);
		if (template == null) {
				throw new CloudRuntimeException("template is null");
		}

        ServiceVirtualMachine svm = _vrouterService.createServiceInstance(zone, 
						                                                  owner, template, vsrx_compute_offering,
                                                                              name, network, public_network);
        if (svm == null) {
            throw new ServerApiException(ApiErrorCode.INTERNAL_ERROR, "Unable to create service instance");
        }
        _vrouterService.startServiceInstance(svm.getId());

	    return true;
	}

	@Override
	public boolean prepare(Network network, NicProfile nicProfile,
			VirtualMachineProfile vm,
			DeployDestination dest, ReservationContext context)
			throws ConcurrentOperationException, ResourceUnavailableException,
			InsufficientCapacityException {

	    s_logger.debug("ContrailVSrxElement prepare: " + network.getName() + ", traffic type: " + network.getTrafficType());

	    return true;
	}

	@Override
	public boolean release(Network network, NicProfile nicProfile,
			VirtualMachineProfile vm,
			ReservationContext context) throws ConcurrentOperationException,
			ResourceUnavailableException {
	    s_logger.debug("ContrailVSrxElement release: " + network.getName() + ", traffic type: " + network.getTrafficType());

	    return true;
	}

	/**
	 * Network disable
	 */
	@Override
	public boolean shutdown(Network network, ReservationContext context,
			boolean cleanup) throws ConcurrentOperationException,
			ResourceUnavailableException {
		s_logger.debug("ContrailVSrxElement shutdown");
		return true;
	}

	/**
	 * Network delete
	 */
	@Override
	public boolean destroy(Network network, ReservationContext context)
			throws ConcurrentOperationException, ResourceUnavailableException {
		s_logger.debug("ContrailVSrxElement destroy");
		return true;
	}

        @Override
        public boolean isReady(PhysicalNetworkServiceProvider provider) {
                return true;
        }

	@Override
	public boolean shutdownProviderInstances(
			PhysicalNetworkServiceProvider provider, ReservationContext context)
			throws ConcurrentOperationException, ResourceUnavailableException {
		s_logger.debug("ContrailVSrxElement shutdown ProviderInstances");
		return true;
	}

	@Override
	public boolean canEnableIndividualServices() {
		return true;
	}

	@Override
	public boolean verifyServicesCombination(Set<Service> services) {
		s_logger.debug("ContrailVSrxElement verifyServicesCombination()");
		s_logger.debug("Services: " + services);
		return true;
	}

	@Override
	public IpDeployer getIpDeployer(Network network) {
		return this;
	}

	@Override
	public boolean applyIps(Network network, List<? extends PublicIpAddress> ipAddress, Set<Service> service) throws ResourceUnavailableException {
		s_logger.debug("ContrailVSrxElement  applyIps(");
		return false;
	}
}
