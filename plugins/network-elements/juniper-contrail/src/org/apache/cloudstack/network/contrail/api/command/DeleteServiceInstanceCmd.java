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

package org.apache.cloudstack.network.contrail.api.command;

import javax.inject.Inject;


import org.apache.cloudstack.context.CallContext;
import org.apache.cloudstack.network.contrail.api.response.ServiceInstanceResponse;
import org.apache.cloudstack.network.contrail.management.ServiceManager;
import org.apache.cloudstack.network.contrail.management.ServiceVirtualMachine;
import org.apache.cloudstack.api.APICommand;
import org.apache.cloudstack.api.ApiConstants;
import org.apache.cloudstack.api.ApiErrorCode;
import org.apache.cloudstack.api.BaseAsyncCmd;
import org.apache.cloudstack.api.Parameter;
import org.apache.cloudstack.api.ServerApiException;
import org.apache.cloudstack.api.response.DomainResponse;
import org.apache.cloudstack.api.response.NetworkResponse;
import org.apache.cloudstack.api.response.ProjectResponse;
import org.apache.cloudstack.api.response.ServiceOfferingResponse;
import org.apache.cloudstack.api.response.TemplateResponse;
import org.apache.cloudstack.api.response.ZoneResponse;
import org.apache.cloudstack.api.response.SuccessResponse;
import org.apache.cloudstack.api.response.UserVmResponse;

import com.cloud.dc.DataCenter;
import com.cloud.event.EventTypes;
import com.cloud.exception.ConcurrentOperationException;
import com.cloud.exception.InsufficientCapacityException;
import com.cloud.exception.InvalidParameterValueException;
import com.cloud.exception.NetworkRuleConflictException;
import com.cloud.exception.ResourceAllocationException;
import com.cloud.exception.ResourceUnavailableException;
import com.cloud.network.Network;
import com.cloud.offering.ServiceOffering;
import com.cloud.template.VirtualMachineTemplate;
import com.cloud.user.Account;

@APICommand(name = "deleteServiceInstance",
    description="Deletes a system virtual-machine that implements network services",
    responseObject=ServiceInstanceResponse.class)
public class DeleteServiceInstanceCmd extends BaseAsyncCmd {
    private static final String s_name = "deleteserviceinstanceresponse";
    
    /// API parameters
    @Parameter(name = ApiConstants.UUID, type = CommandType.UUID, entityType=UserVmResponse.class,
            required = true, description = "The service Instance ID that defines the virtual-machine service appliance")
    private Long serviceInstanceId;

    @Inject ServiceManager _vrouterService;

    @Override
    public String getEventType() {
        return EventTypes.EVENT_SERVICE_INSTANCE_VM_DESTROY;
    }

    @Override
    public String getEventDescription() {
        return "Delete service instance";
    }

    @Override
    public String getCommandName() {
        return s_name;
    }

    @Override 
    public long getEntityOwnerId() {
        return CallContext.current().getCallingAccount().getId();
    }

    @Override
    public void execute() throws ResourceUnavailableException,
            InsufficientCapacityException, ServerApiException,
            ConcurrentOperationException, ResourceAllocationException,
            NetworkRuleConflictException {
        // Parameter validation
        try {
            boolean result = _vrouterService.deleteServiceInstance(serviceInstanceId);

            if (result) {
                SuccessResponse response = new SuccessResponse(getCommandName());
                response.setObjectName("serviceinstance");
                this.setResponseObject(response);
            } else {
                throw new ServerApiException(ApiErrorCode.INTERNAL_ERROR, "Failed to delete service instance :" + serviceInstanceId);
            }
        } catch (Exception ex) {
            throw new ServerApiException(ApiErrorCode.INTERNAL_ERROR, ex.getMessage());
        }
    }
}
