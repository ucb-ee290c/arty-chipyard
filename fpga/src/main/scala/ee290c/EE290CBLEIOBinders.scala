package chipyard.iobinders

import chisel3._
import chisel3.experimental.{Analog, IO, DataMirror}

import chipyard._

import freechips.rocketchip.config._
import freechips.rocketchip.diplomacy._
import freechips.rocketchip.devices.debug._
import freechips.rocketchip.jtag.{JTAGIO}
import freechips.rocketchip.subsystem._
import freechips.rocketchip.system.{SimAXIMem}
import freechips.rocketchip.amba.axi4.{AXI4Bundle, AXI4SlaveNode, AXI4MasterNode, AXI4EdgeParameters}
import freechips.rocketchip.util._
import freechips.rocketchip.prci.{ClockSinkNode, ClockSinkParameters}
import freechips.rocketchip.groundtest.{GroundTestSubsystemModuleImp, GroundTestSubsystem}

import sifive.blocks.devices.gpio._
import sifive.blocks.devices.uart._
import sifive.blocks.devices.spi._
import tracegen.{TraceGenSystemModuleImp}

import barstools.iocell.chisel._

import testchipip._
import icenet.{CanHavePeripheryIceNIC, SimNetwork, NicLoopback, NICKey, NICIOvonly}

import scala.reflect.{ClassTag}


class WithBSelIOCells extends OverrideIOBinder({
  (system: HasPeripheryEE290CBLEModuleImp) => {
    val (port, cells) = IOCell.generateIOFromSignal(system.boot, "bsel", system.p(IOCellKey), abstractResetAsAsync = true)
    (Seq(port), cells)
  }
})


class WithADCPunchthrough(params: ADCParams = ADCParams()) extends OverrideIOBinder({
  (system: HasPeripheryADCModuleImp) => {
    val ports: Seq[ADCAnalogIO] = system.myadc_data.map({ a =>
      val analog = IO(new ADCAnalogIO).suggestName("myadc")
      analog <> a
      analog
    }).toSeq
    (ports, Nil)
  }
})

