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

import chipyard.{BuildSystem, WithMulticlockIncoherentBusTopology}
import chipyard.ee290c._
import baseband._
import aes._

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

class WithOriginal290CConfig extends Config(
  new aes.WithAESAccel ++

  new chipyard.harness.WithADCDummyCounter ++
  // new chipyard.harness.WithADCTiedOff ++
  new chipyard.iobinders.WithADCPunchthrough ++
  new WithADC(useAXI4=false) ++

  new WithBSel ++
  new WithNGPIOs(3) ++                                         // 3 GPIO pins
  new chipyard.config.WithSPIFlash ++
  new chipyard.config.WithTLSerialLocation(
    freechips.rocketchip.subsystem.FBUS,
    freechips.rocketchip.subsystem.PBUS) ++                    // attach TL serial adapter to f/p busses
  // new freechips.rocketchip.subsystem.WithBufferlessBroadcastHub ++
  new freechips.rocketchip.subsystem.WithNoMemPort ++          // remove backing memory
  new EE290Core ++                                             // single tiny rocket-core

  new chipyard.config.WithTileFrequency(20.0) ++
  new chipyard.config.WithPeripheryBusFrequency(20.0)++
  new chipyard.config.WithMemoryBusFrequency(20.0) ++
  new chipyard.config.WithSystemBusFrequency(20.0) ++
  new chipyard.config.WithFrontBusFrequency(20.0) ++
  new chipyard.config.WithControlBusFrequency(20.0) ++

  new chipyard.harness.WithTieOffInterrupts ++                  // tie-off interrupt ports, if present
  new chipyard.harness.WithTieOffL2FBusAXI ++                   // tie-off external AXI4 master, if present
  new chipyard.harness.WithCustomBootPinPlusArg ++
  new chipyard.harness.WithClockAndResetFromHarness ++

  // The IOBinders instantiate ChipTop IOs to match desired digital IOs
  // IOCells are generated for "Chip-like" IOs, while simulation-only IOs are directly punched through
  new chipyard.iobinders.WithAXI4MemPunchthrough ++
  new chipyard.iobinders.WithAXI4MMIOPunchthrough ++
  new chipyard.iobinders.WithL2FBusAXI4Punchthrough ++
  new chipyard.iobinders.WithBlockDeviceIOPunchthrough ++
  new chipyard.iobinders.WithNICIOPunchthrough ++
  new chipyard.iobinders.WithSerialTLIOCells ++
  new chipyard.iobinders.WithDebugIOCells ++
  new chipyard.iobinders.WithUARTIOCells ++
  new chipyard.iobinders.WithTraceIOPunchthrough ++
  new chipyard.iobinders.WithExtInterruptIOCells ++
  new chipyard.iobinders.WithCustomBootPin ++
  new chipyard.iobinders.WithDividerOnlyClockGenerator ++

  new chipyard.iobinders.WithTraceIOPunchthrough ++
  new chipyard.iobinders.WithExtInterruptIOCells ++

  new testchipip.WithSerialTLWidth(1) ++
  new testchipip.WithDefaultSerialTL ++                          // use serialized tilelink port to external serialadapter/harnessRAM0

  new WithEE290CBootROM ++                                       // use our bootrom

  new WithNEntryUART(32, 32) ++                                // add a UART
  new chipyard.WithMulticlockIncoherentBusTopology ++              // hierarchical buses including mbus+l2
  new chipyard.config.WithNoSubsystemDrivenClocks ++             // drive the subsystem diplomatic clocks from ChipTop instead of using implicit clocks
  new chipyard.config.WithInheritBusFrequencyAssignments ++      // Unspecified clocks within a bus will receive the bus frequency if set
  new chipyard.config.WithPeripheryBusFrequencyAsDefault ++      // Unspecified frequencies with match the pbus frequency (which is always set)
  new freechips.rocketchip.subsystem.WithJtagDTM ++              // set the debug module to expose a JTAG port
  new freechips.rocketchip.subsystem.WithNoMMIOPort ++           // no top-level MMIO master port (overrides default set in rocketchip)
  new freechips.rocketchip.subsystem.WithNoSlavePort ++          // no top-level MMIO slave port (overrides default set in rocketchip)
  new freechips.rocketchip.subsystem.WithInclusiveCache ++       // use Sifive L2 cache
  new freechips.rocketchip.subsystem.WithNExtTopInterrupts(0) ++ // no external interrupts
  new freechips.rocketchip.system.BaseConfig                     // "base" rocketchip system
)

class WithArtyTweaks extends Config(
  new WithArtyJTAGHarnessBinder ++
  new WithArtyUARTHarnessBinder ++
  new WithArtyResetHarnessBinder ++
  new WithDebugResetPassthrough ++
  new freechips.rocketchip.subsystem.WithNBreakpoints(2))

class WithArtyOsciTweaks extends Config(
  // new WithArtyOsciI2C ++
  new WithQSPIPassthrough ++ // IOBinder
  new WithGPIOPassthrough ++ // IOBinder
  new WithArtyOsciGPIO ++ // Harness
  new WithArtyOsciQSPI ++ // Harness
  new WithArtyOsciTL // Harness
)

class TinyRocketArtyConfig extends Config(
  new WithDefaultPeripherals ++
  new WithArtyTweaks ++
  new chipyard.TinyRocketConfig)

class OsciArtyConfig extends Config(
  new WithOriginal290CConfig ++
  new WithArtyTweaks ++
  new WithArtyOsciTweaks)
