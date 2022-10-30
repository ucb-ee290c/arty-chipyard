package chipyard.fpga.arty

import chisel3._

import freechips.rocketchip.devices.debug._
import freechips.rocketchip.jtag.{JTAGIO}
import freechips.rocketchip.subsystem._

import sifive.blocks.devices.uart._
import sifive.blocks.devices.jtag._
import sifive.blocks.devices.i2c._
import sifive.blocks.devices.gpio._
import sifive.blocks.devices.spi._

import sifive.blocks.devices.pinctrl._

import sifive.fpgashells.ip.xilinx.{IBUFG, IOBUF, PULLUP, PowerOnResetFPGAOnly}

import chipyard.harness.{ComposeHarnessBinder, OverrideHarnessBinder}
import chipyard.iobinders.JTAGChipIO

import testchipip._

class WithArtyResetHarnessBinder extends ComposeHarnessBinder({
  (system: HasPeripheryDebugModuleImp, th: ArtyFPGATestHarness, ports: Seq[Bool]) => {
    require(ports.size == 2)

    withClockAndReset(th.clock_32MHz, th.hReset) {
      // Debug module reset
      th.dut_ndreset := ports(0)

      // JTAG reset
      ports(1) := PowerOnResetFPGAOnly(th.clock_32MHz)
    }
  }
})

class WithArtyJTAGHarnessBinder extends OverrideHarnessBinder({
  (system: HasPeripheryDebug, th: ArtyFPGATestHarness, ports: Seq[Data]) => {
    ports.map {
      case j: JTAGChipIO =>
        withClockAndReset(th.buildtopClock, th.hReset) {
          val jtag_wire = Wire(new JTAGIO)
          jtag_wire.TDO.data := j.TDO
          jtag_wire.TDO.driven := true.B
          j.TCK := jtag_wire.TCK
          j.TMS := jtag_wire.TMS
          j.TDI := jtag_wire.TDI

          val io_jtag = Wire(new JTAGPins(() => new BasePin(), false)).suggestName("jtag")

          JTAGPinsFromPort(io_jtag, jtag_wire)

          io_jtag.TCK.i.ival := IBUFG(IOBUF(th.jd_2).asClock).asBool

          IOBUF(th.jd_5, io_jtag.TMS)
          PULLUP(th.jd_5)

          IOBUF(th.jd_4, io_jtag.TDI)
          PULLUP(th.jd_4)

          IOBUF(th.jd_0, io_jtag.TDO)

          // mimic putting a pullup on this line (part of reset vote)
          th.SRST_n := IOBUF(th.jd_6)
          PULLUP(th.jd_6)

          // ignore the po input
          io_jtag.TCK.i.po.map(_ := DontCare)
          io_jtag.TDI.i.po.map(_ := DontCare)
          io_jtag.TMS.i.po.map(_ := DontCare)
          io_jtag.TDO.i.po.map(_ := DontCare)
        }
    }
  }
})

class WithArtyUARTHarnessBinder extends OverrideHarnessBinder({
  (system: HasPeripheryUARTModuleImp, th: ArtyFPGATestHarness, ports: Seq[UARTPortIO]) => {
    withClockAndReset(th.clock_32MHz, th.hReset) {
      IOBUF(th.uart_rxd_out,  ports.head.txd)
      ports.head.rxd := IOBUF(th.uart_txd_in)
    }
  }
})

class WithArtyOsciI2C extends OverrideHarnessBinder({
  (system: HasPeripheryI2CModuleImp, th: ArtyFPGATestHarness, ports: Seq[I2CPort]) => {
    withClockAndReset(th.clock_32MHz, th.hReset) {
      // only deals with first set of i2c
      // sda -> ja_0, scl -> ja_1
      val sdaPin = Wire(new BasePin())
      sdaPin.o.oval <> ports.head.sda.out
      sdaPin.o.oe <> ports.head.sda.oe
      sdaPin.o.ie := true.B
      sdaPin.i.po.map(_ := DontCare)
      ports.head.sda.in <> sdaPin.i.ival
      IOBUF(th.ja_0, sdaPin)
      PULLUP(th.ja_0)

      val sclPin = Wire(new BasePin())
      sclPin.o.oval <> ports.head.scl.out
      sclPin.o.oe <> ports.head.scl.oe
      sclPin.o.ie := true.B
      sclPin.i.po.map(_ := DontCare)
      ports.head.scl.in <> sclPin.i.ival
      IOBUF(th.ja_1, sclPin)
      PULLUP(th.ja_1)
    }
  }
})

class WithArtyOsciGPIO extends OverrideHarnessBinder({
  (system: HasPeripheryGPIOModuleImp, th: ArtyFPGATestHarness, ports: Seq[GPIOPortIO]) => {
    withClockAndReset(th.clock_32MHz, th.hReset) {
      // IOBUF(th.ja_2, ports.head.pins(0).toBasePin())
      // IOBUF(th.ja_3, ports.head.pins(1).toBasePin())
      // IOBUF(th.ja_4, ports.head.pins(2).toBasePin())
      IOBUF(th.led_0, ports.head.pins(0).toBasePin())
      IOBUF(th.led_1, ports.head.pins(1).toBasePin())
      IOBUF(th.led_2, ports.head.pins(2).toBasePin())
      // IOBUF(th.ja_5, ports.head.pins(3).toBasePin())
    }
  }
})

class WithArtyOsciQSPI extends OverrideHarnessBinder({
  (system: HasPeripherySPIFlashModuleImp, th: ArtyFPGATestHarness, ports: Seq[SPIPortIO]) => {
    th.connectSPIFlash(ports.head, th.clock_32MHz, th.hReset.asBool)
    // IOBUF(th.qspi_sck, ports.head.sck)
    // IOBUF(th.qspi_cs,  ports.head.cs(0))

    // (th.qspi_dq zip ports.head.dq).foreach { case(a, b) => IOBUF(a, b) }
  }
})

class WithArtyOsciTL extends OverrideHarnessBinder({
  (system: CanHavePeripheryTLSerial, th: ArtyFPGATestHarness, ports: Seq[ClockedIO[SerialIO]]) => {
    implicit val p = chipyard.iobinders.GetSystemParameters(system)
    ports.map({ port =>
      // async queue enq clock = port.clock = system clock
      //             deq clock = clock in constructor = tl clock?
      /* val bits = SerialAdapter.asyncQueue(port, th.clock_8MHz /* IOBUF(th.jc(6)).asClock */, th.buildtopReset)
      withClockAndReset(th.buildtopClock, th.buildtopReset) {
        val ram = SerialAdapter.connectHarnessRAM(system.serdesser.get, bits, th.buildtopReset)
        val serial = ram.module.io.tsi_ser
        serial.out.ready := IOBUF(th.jc(0))
        IOBUF(th.jc(1), serial.out.valid)
        IOBUF(th.jc(2), serial.out.bits(0).asBool()) // TODO: not sure if this works
        IOBUF(th.jc(3), serial.in.ready)
        serial.in.valid := IOBUF(th.jc(4))
        serial.in.bits := IOBUF(th.jc(5)).asUInt()
      } */
      port.bits.out.ready := IOBUF(th.jc(0))
      IOBUF(th.jc(1), port.bits.out.valid)
      IOBUF(th.jc(2), port.bits.out.bits(0).asBool()) // TODO: not sure if this works
      IOBUF(th.jc(3), port.bits.in.ready)
      port.bits.in.valid := IOBUF(th.jc(4))
      port.bits.in.bits := IOBUF(th.jc(5)).asUInt()
      IOBUF(th.jc(6), port.clock.asBool)
    })
  }
})
