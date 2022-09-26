// See LICENSE for license details.
package chipyard.fpga.arty

import freechips.rocketchip.config._
import freechips.rocketchip.subsystem._
import freechips.rocketchip.devices.debug._
import freechips.rocketchip.devices.tilelink._
import freechips.rocketchip.diplomacy.{DTSModel, DTSTimebase}
import freechips.rocketchip.system._
import freechips.rocketchip.tile._

import sifive.blocks.devices.uart._
import sifive.blocks.devices.i2c._
import sifive.blocks.devices.gpio._

import testchipip.{SerialTLKey}

import chipyard.{BuildSystem}

import chipyard.fpga.vcu118.osci.{WithGPIOIOPassthrough, WithI2CIOPassthrough}

class WithDefaultPeripherals extends Config((site, here, up) => {
  case PeripheryUARTKey => List(
    UARTParams(address = 0x10013000))
  case DTSTimebase => BigInt(32768)
  case JtagDTMKey => new JtagDTMConfig (
    idcodeVersion = 2,
    idcodePartNum = 0x000,
    idcodeManufId = 0x489,
    debugIdleCycles = 5)
  case SerialTLKey => None // remove serialized tl port
})

class WithArtyOsciPeripherals extends Config((site, here, up) => {
  case PeripheryUARTKey => /* up(PeripheryUARTKey, site) ++ */ List(UARTParams(address = BigInt(0x64003000L)))
  case PeripheryI2CKey => List(I2CParams(address = BigInt(0x64005000L)))
  case PeripheryGPIOKey => List(GPIOParams(address = BigInt(0x64002000), width = 4))
  case JtagDTMKey => new JtagDTMConfig (
    idcodeVersion = 2,
    idcodePartNum = 0x000,
    idcodeManufId = 0x489,
    debugIdleCycles = 5)
  // case TSIClockMaxFrequencyKey => 100
  /*case PeripheryTSIHostKey => List(
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
      ))*/
})

// DOC include start: AbstractArty and Rocket
class WithArtyTweaks extends Config(
  new WithArtyJTAGHarnessBinder ++
  new WithArtyUARTHarnessBinder ++
  new WithArtyResetHarnessBinder ++
  new WithDebugResetPassthrough ++
  new freechips.rocketchip.subsystem.WithNBreakpoints(2))

class WithArtyOsciTweaks extends Config(
  new WithArtyOsciI2C ++
  new WithArtyOsciGPIO ++
  // new WithArtyOsciQSPI ++ // TODO
  new WithArtyOsciTL ++
  new WithGPIOIOPassthrough ++
  // new WithTSITLIOPassthrough ++
  new WithI2CIOPassthrough)

class TinyRocketArtyConfig extends Config(
  new WithDefaultPeripherals ++ // TODO: QSPI
  new WithArtyTweaks ++
  new chipyard.TinyRocketConfig)

class TinyOsciArtyConfig extends Config(
  new WithArtyOsciPeripherals ++
  new WithArtyTweaks ++
  new WithArtyOsciTweaks ++
  new chipyard.TinyRocketConfig)

class RocketOsciArtyConfig extends Config(
  new WithArtyOsciPeripherals ++
  new WithArtyTweaks ++
  new WithArtyOsciTweaks ++
  new chipyard.RocketConfig)
// DOC include end: AbstractArty and Rocket
