package chipyard.fpga.vcu118.osci

import chisel3._
import chisel3.experimental.{Analog, IO, BaseModule}

import freechips.rocketchip.util.{HeterogeneousBag}
import freechips.rocketchip.tilelink.{TLBundle}

import sifive.blocks.devices.uart.{HasPeripheryUARTModuleImp, UARTPortIO}
import sifive.blocks.devices.spi.{HasPeripherySPI, SPIPortIO}
import sifive.blocks.devices.i2c.{HasPeripheryI2CModuleImp, I2CPort}
import sifive.blocks.devices.gpio.{HasPeripheryGPIOModuleImp, GPIOPortIO}

import testchipip.{HasPeripheryTSIHostWidget, TSIHostWidgetIO}

import chipyard.{HasHarnessSignalReferences}
import chipyard.harness.{ComposeHarnessBinder, OverrideHarnessBinder}

/*** UART ***/
class WithOsciUART extends ComposeHarnessBinder({
  (system: HasPeripheryUARTModuleImp, th: BaseModule with HasHarnessSignalReferences, ports: Seq[UARTPortIO]) => {
    th match { case vcu118th: OsciVCU118FPGATestHarnessImp => {
      require(ports.size == 2)

      vcu118th.osciOuter.io_fmc_uart_bb.bundle <> ports.last
    } }
  }
})

/*** I2C ***/
class WithOsciI2C extends OverrideHarnessBinder({
  (system: HasPeripheryI2CModuleImp, th: BaseModule with HasHarnessSignalReferences, ports: Seq[I2CPort]) => {
    th match { case vcu118th: OsciVCU118FPGATestHarnessImp => {
      require(ports.size == 1)

      vcu118th.osciOuter.io_i2c_bb.bundle <> ports.head
    } }
  }
})

/*** GPIO ***/
class WithOsciGPIO extends OverrideHarnessBinder({
  (system: HasPeripheryGPIOModuleImp, th: BaseModule with HasHarnessSignalReferences, ports: Seq[GPIOPortIO]) => {
    th match { case vcu118th: OsciVCU118FPGATestHarnessImp => {
      (vcu118th.osciOuter.io_gpio_bb zip ports).map { case (bb_io, dut_io) =>
        bb_io.bundle <> dut_io
      }
    } }
  }
})

/*** TSI Host Widget ***/
class WithOsciTSIHost extends OverrideHarnessBinder({
  (system: HasPeripheryTSIHostWidget, th: BaseModule with HasHarnessSignalReferences, ports: Seq[Data]) => {
    th match { case vcu118th: OsciVCU118FPGATestHarnessImp => {
      require(ports.size == 2) // 1st goes to the TL mem, 2nd goes to the serial link

      ports.head match { case tlPort: HeterogeneousBag[TLBundle] =>
        val tsiBundles = vcu118th.osciOuter.tsiDdrClient.out.map(_._1)
        val tsiDdrClientBundle = Wire(new HeterogeneousBag(tsiBundles.map(_.cloneType)))
        tsiBundles.zip(tsiDdrClientBundle).foreach { case (bundle, io) => bundle <> io }
        tsiDdrClientBundle <> tlPort
      }

      ports.last match { case serialPort: TSIHostWidgetIO =>
        vcu118th.osciOuter.io_tsi_serial_bb.bundle <> serialPort
      }
    } }
  }
})
