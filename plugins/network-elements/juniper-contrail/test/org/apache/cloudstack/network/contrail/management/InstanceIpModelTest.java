package org.apache.cloudstack.network.contrail.model;
import java.util.UUID;

import java.io.IOException;

import org.apache.cloudstack.network.contrail.management.*;
import org.apache.cloudstack.network.contrail.model.*;

import org.apache.log4j.Logger;
import org.junit.runner.RunWith;

import static org.mockito.Mockito.*;

import com.cloud.network.Network;
import com.cloud.network.Networks.TrafficType;
import com.cloud.network.dao.NetworkVO;
import com.cloud.network.dao.NetworkDao;
import com.cloud.vm.NicVO;
import com.cloud.vm.VMInstanceVO;
import com.cloud.vm.VirtualMachine;
import com.cloud.vm.dao.UserVmDao;
import com.cloud.uservm.UserVm;

import net.juniper.contrail.api.ApiConnectorMock;
import net.juniper.contrail.api.ApiConnectorFactory;
import net.juniper.contrail.api.ApiObjectBase;
import net.juniper.contrail.api.types.Domain;
import net.juniper.contrail.api.types.InstanceIp;
import net.juniper.contrail.api.types.NetworkIpam;
import net.juniper.contrail.api.types.SubnetType;
//import net.juniper.contrail.api.types.VirtualMachine;
import net.juniper.contrail.api.types.VirtualMachineInterface;
import net.juniper.contrail.api.types.VirtualNetwork;
import net.juniper.contrail.api.types.VnSubnetsType;
import net.juniper.contrail.api.types.NetworkPolicy;
import net.juniper.contrail.api.types.Project;
import net.juniper.contrail.api.types.FloatingIp;
import net.juniper.contrail.api.types.VnSubnetsType;
import net.juniper.contrail.api.types.ServiceInstance;
import net.juniper.contrail.api.ApiConnector;

import org.junit.runner.RunWith;
import org.junit.After;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.AfterClass;
import org.junit.Ignore;
import org.junit.Test;
import junit.framework.TestCase;

public class InstanceIpModelTest extends TestCase {
    private static final Logger s_logger =
            Logger.getLogger(InstanceIpModelTest.class);

    @Test
    public void testCreateInstanceIp() throws IOException {

        ContrailManagerImpl contrailMgr = mock(ContrailManagerImpl.class);
        ModelController controller      = mock(ModelController.class);
        ApiConnector api = new ApiConnectorMock(null, 0);
        when(controller.getApiAccessor()).thenReturn(api);
        when(controller.getManager()).thenReturn(contrailMgr);

        // Create Virtual-Network (VN)
        NetworkVO network = mock(NetworkVO.class);
        when(network.getName()).thenReturn("testnetwork");
        when(network.getState()).thenReturn(Network.State.Implemented);
        when(network.getGateway()).thenReturn("10.1.1.1");
        when(network.getCidr()).thenReturn("10.1.1.0/24");
        when(network.getPhysicalNetworkId()).thenReturn(42L);
        when(network.getDomainId()).thenReturn(10L);
        when(network.getAccountId()).thenReturn(42L);
        NetworkDao networkDao = mock(NetworkDao.class);
        when(networkDao.findById(anyLong())).thenReturn(network);
        when(controller.getNetworkDao()).thenReturn(networkDao);

        when(contrailMgr.getCanonicalName(network)).thenReturn("testnetwork");
        when(contrailMgr.getProjectId(network.getDomainId(), network.getAccountId())).thenReturn("testProjectId");

        VirtualNetworkModel vnModel = new VirtualNetworkModel(network, UUID.randomUUID().toString(), "test",
        TrafficType.Guest);
        vnModel.build(controller, network);
        try {
            vnModel.update(controller);
        } catch (Exception ex) {
            fail("virtual-network update failed ");
        }

        // Create Virtual-Machine (VM)
        VMInstanceVO vm = mock(VMInstanceVO.class);
        when(vm.getInstanceName()).thenReturn("testVM1");
        when(vm.getState()).thenReturn(VirtualMachine.State.Running);
        when(vm.getDomainId()).thenReturn(10L);
        when(vm.getAccountId()).thenReturn(42L);
        UserVmDao VmDao      = mock(UserVmDao.class);
        when(VmDao.findById(anyLong())).thenReturn(null);
        when(controller.getVmDao()).thenReturn(VmDao);

        VirtualMachineModel vmModel = new VirtualMachineModel(vm, UUID.randomUUID().toString());
        vmModel.build(controller, vm);
        try {
            vmModel.update(controller);
        } catch (Exception ex) {
            fail("virtual-machine update failed ");
        }

        // Create Virtual=Machine-Interface (VMInterface)	
        NicVO nic = mock(NicVO.class);
        when(nic.getIp4Address()).thenReturn("10.1.1.2");
        when(nic.getMacAddress()).thenReturn("00:01:02:03:04:05");
        when(nic.getDeviceId()).thenReturn(100);
        when(nic.getState()).thenReturn(NicVO.State.Allocated);
        when(nic.getNetworkId()).thenReturn(10L);

        when(contrailMgr.getVifNameByVmName(anyString(), anyInt())).thenReturn("testVM1-100");

        VMInterfaceModel vmiModel = new VMInterfaceModel(UUID.randomUUID().toString()); 
        vmiModel.addToVirtualMachine(vmModel);
        vmiModel.addToVirtualNetwork(vnModel);

        try {
            vmiModel.build(controller, vm, nic);
            vmiModel.setActive();
        } catch (Exception ex) {
            fail("vm-interface build failed ");
        }

        try {
            vmiModel.update(controller);
        } catch (Exception ex) {
            fail("vm-interface update failed ");
        }
        InstanceIpModel ipModel = new InstanceIpModel(vm.getInstanceName(), nic.getDeviceId());
        ipModel.addToVMInterface(vmiModel);
        ipModel.setAddress(nic.getIp4Address());

        try {
            ipModel.update(controller);
        } catch (Exception ex) {
            fail("ipInstance update failed ");
        }
    }
}
