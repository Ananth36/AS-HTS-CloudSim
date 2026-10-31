package edu.manipal.ashts.datacenter;

import edu.manipal.ashts.core.SimConfig;
import org.cloudbus.cloudsim.CloudletSchedulerSpaceShared;
import org.cloudbus.cloudsim.Datacenter;
import org.cloudbus.cloudsim.DatacenterCharacteristics;
import org.cloudbus.cloudsim.Host;
import org.cloudbus.cloudsim.Pe;
import org.cloudbus.cloudsim.Storage;
import org.cloudbus.cloudsim.Vm;
import org.cloudbus.cloudsim.VmAllocationPolicySimple;
import org.cloudbus.cloudsim.VmSchedulerTimeShared;
import org.cloudbus.cloudsim.provisioners.BwProvisionerSimple;
import org.cloudbus.cloudsim.provisioners.PeProvisionerSimple;
import org.cloudbus.cloudsim.provisioners.RamProvisionerSimple;

import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;

/**
 * Builds the simulated datacenter: identical physical hosts and a heterogeneous set of
 * single-PE VMs (MIPS ratings from dc.vm.mips). Each VM uses
 * {@link CloudletSchedulerSpaceShared}, so it executes exactly one cloudlet at a time and all
 * queueing happens in the broker, where the scheduling policy controls it.
 */
public final class DatacenterFactory {

    private DatacenterFactory() {}

    public static Datacenter createDatacenter(String name, SimConfig cfg) throws Exception {
        int hosts = cfg.i("dc.hosts");
        int pes = cfg.i("dc.host.pes");
        int peMips = cfg.i("dc.host.pe.mips");
        int ram = cfg.i("dc.host.ram");

        List<Host> hostList = new ArrayList<Host>();
        for (int h = 0; h < hosts; h++) {
            List<Pe> peList = new ArrayList<Pe>();
            for (int p = 0; p < pes; p++) peList.add(new Pe(p, new PeProvisionerSimple(peMips)));
            hostList.add(new Host(h, new RamProvisionerSimple(ram), new BwProvisionerSimple(1_000_000),
                    1_000_000, peList, new VmSchedulerTimeShared(peList)));
        }

        DatacenterCharacteristics ch = new DatacenterCharacteristics(
                "x86", "Linux", "Xen", hostList, 10.0, 3.0, 0.05, 0.001, 0.0);
        return new Datacenter(name, ch, new VmAllocationPolicySimple(hostList),
                new LinkedList<Storage>(), 0);
    }

    public static List<Vm> createVms(int brokerId, SimConfig cfg) {
        double[] mips = cfg.vmMips();
        List<Vm> vms = new ArrayList<Vm>();
        for (int i = 0; i < mips.length; i++) {
            vms.add(new Vm(i, brokerId, mips[i], 1, 2048, 1000, 10000, "Xen",
                    new CloudletSchedulerSpaceShared()));
        }
        return vms;
    }
}
