package chipyard.fpga.vcu118.osci

import math.min

import freechips.rocketchip.config.{Config, Parameters}
import freechips.rocketchip.diplomacy.{DTSModel, DTSTimebase, RegionType, AddressSet, ResourceBinding, Resource, ResourceAddress}
import freechips.rocketchip.tilelink._
import freechips.rocketchip.diplomacy._
import freechips.rocketchip.subsystem.{MasterPortParams}

import sifive.blocks.devices.gpio.{PeripheryGPIOKey, GPIOParams}
import sifive.blocks.devices.i2c.{PeripheryI2CKey, I2CParams}
import sifive.blocks.devices.uart.{PeripheryUARTKey, UARTParams}

import sifive.fpgashells.shell.{DesignKey}
import sifive.fpgashells.shell.xilinx.{VCU118ShellPMOD, VCU118DDRSize}

import testchipip.{PeripheryTSIHostKey, TSIHostParams, TSIHostSerdesParams}

import chipyard.{BuildSystem}

import chipyard.fpga.vcu118.{WithVCU118Tweaks, WithFPGAFrequency, VCU118DDR2Size}

class WithOsciPeripherals extends Config((site, here, up) => {
  case PeripheryUARTKey => up(PeripheryUARTKey, site) ++ List(UARTParams(address = BigInt(0x64003000L)))
  case PeripheryI2CKey => List(I2CParams(address = BigInt(0x64005000L)))
  case PeripheryGPIOKey => {
    if (OsciGPIOs.width > 0) {
      require(OsciGPIOs.width <= 64) // currently only support 64 GPIOs (change addrs to get more)
      val gpioAddrs = Seq(BigInt(0x64002000), BigInt(0x64007000))
      val maxGPIOSupport = 32 // max gpios supported by SiFive driver (split by 32)
      List.tabulate(((OsciGPIOs.width - 1)/maxGPIOSupport) + 1)(n => {
        GPIOParams(address = gpioAddrs(n), width = min(OsciGPIOs.width - maxGPIOSupport*n, maxGPIOSupport))
      })
    }
    else {
      List.empty[GPIOParams]
    }
  }
  case TSIClockMaxFrequencyKey => 100
  case PeripheryTSIHostKey => List(
    TSIHostParams(
      offchipSerialIfWidth = 1,
      mmioBaseAddress = BigInt(0x64006000),
      mmioSourceId = 1 << 4, // manager source
      serdesParams = TSIHostSerdesParams(
        clientPortParams = TLMasterPortParameters.v1(
          clients = Seq(TLMasterParameters.v1(
            name = "tl-tsi-host-serdes",
            sourceId = IdRange(0, (1 << 4))))),
        managerPortParams = TLSlavePortParameters.v1(
          managers = Seq(TLSlaveParameters.v1(
            address = Seq(AddressSet(0, BigInt("FFFFFFFF", 16))), // access everything on chip
            regionType = RegionType.UNCACHED,
            executable = true,
            supportsGet        = TransferSizes(1, (1 << 15)), // [3:0] io_out_bits_size - need 4 bits (16 values) to represent possible values
            supportsPutFull    = TransferSizes(1, (1 << 15)),
            supportsPutPartial = TransferSizes(1, (1 << 15)),
            supportsAcquireT   = TransferSizes(1, (1 << 15)),
            supportsAcquireB   = TransferSizes(1, (1 << 15)),
            supportsArithmetic = TransferSizes(1, (1 << 15)),
            supportsLogical    = TransferSizes(1, (1 << 15)))),
          endSinkId = 1 << 6, // manager sink
          beatBytes = 8)),
      targetMasterPortParams = MasterPortParams(
        base = BigInt("80000000", 16),
        size = site(VCU118DDR2Size),
        beatBytes = 8, // comes from test chip
        idBits = 4) // comes from VCU118 idBits in XilinxVCU118MIG
      ))
})

class WithOsciVCU118System extends Config((site, here, up) => {
  case BuildSystem => (p: Parameters) => new OsciVCU118DigitalTop()(p) // use the VCU118-extended osci digital top
})

class WithOsciAdditions extends Config(
  new WithOsciUART ++
  new WithOsciI2C ++
  new WithOsciGPIO ++
  new WithOsciTSIHost ++
  new WithTSITLIOPassthrough ++
  new WithI2CIOPassthrough ++
  new WithGPIOIOPassthrough ++
  new WithOsciPeripherals ++
  new WithOsciVCU118System)

class RocketOsciConfig extends Config(
  new WithOsciAdditions ++
  new WithVCU118Tweaks ++
  new chipyard.RocketConfig)

class BoomOsciConfig extends Config(
  new WithFPGAFrequency(50) ++
  new WithOsciAdditions ++
  new WithVCU118Tweaks ++
  new chipyard.MegaBoomConfig)
