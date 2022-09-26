package chipyard

import freechips.rocketchip.config.{Config}
import freechips.rocketchip.config._

import Chisel._
import chisel3._

import freechips.rocketchip.devices.debug._
import freechips.rocketchip.devices.tilelink._
import freechips.rocketchip.diplomacy.{LazyModule, AddressSet}
import freechips.rocketchip.diplomacy._
import freechips.rocketchip.rocket._
import freechips.rocketchip.tile._
import freechips.rocketchip.util._
import freechips.rocketchip.subsystem.BaseSubsystem
import freechips.rocketchip.subsystem._
import freechips.rocketchip.config.{Field, Config}
import freechips.rocketchip.tilelink.{TLRAM}

import freechips.rocketchip.tile.OpcodeSet

import chipyard.harness._
import chipyard.iobinders._
import testchipip._


// EE290C
import baseband._
import aes._

class EE290CBLEConfig extends Config(
  // new baseband.WithBLEBasebandModem ++
  // new chipyard.iobinders.WithBLEBasebandModemPunchthrough() ++
  // new chipyard.harness.WithBLEBasebandModemTiedOff ++

  new aes.WithAESAccel ++

  new chipyard.harness.WithADCDummyCounter ++
  // new chipyard.harness.WithADCTiedOff ++
  new chipyard.iobinders.WithADCPunchthrough ++ 
  new WithADC(useAXI4=false) ++

  new WithBSel ++
  new WithNGPIOs(3) ++                                         // 2 GPIO pins
  new chipyard.config.WithSPIFlash ++
  new chipyard.config.WithTLSerialLocation(
    freechips.rocketchip.subsystem.FBUS,
    freechips.rocketchip.subsystem.PBUS) ++                    // attach TL serial adapter to f/p busses
  // new freechips.rocketchip.subsystem.WithBufferlessBroadcastHub ++
  new chipyard.WithMulticlockIncoherentBusTopology ++          // use incoherent bus topology
  new freechips.rocketchip.subsystem.WithNoMemPort ++          // remove backing memory
  new EE290Core ++                                             // single tiny rocket-core

  new chipyard.config.WithTileFrequency(20.0) ++
  new chipyard.config.WithPeripheryBusFrequency(20.0)++
  new chipyard.config.WithMemoryBusFrequency(20.0) ++
  new chipyard.config.WithSystemBusFrequency(20.0) ++
  new chipyard.config.WithFrontBusFrequency(20.0) ++
  new chipyard.config.WithControlBusFrequency(20.0) ++

  // From the AbstractConfig:
  // The HarnessBinders control generation of hardware in the TestHarness
  new chipyard.harness.WithSimSPIFlashModel(false) ++          // add the SPI flash model in the harness (writeable)
  new chipyard.harness.WithUARTAdapter ++                      // add UART adapter to display UART on stdout, if uart is present
  new chipyard.harness.WithSimSerial ++                        // add external serial-adapter and RAM
  new chipyard.harness.WithSimDebug ++                         // add SimJTAG or SimDTM adapters if debug module is enabled
  new chipyard.harness.WithGPIOTiedOff ++                      // tie-off chiptop GPIOs, if GPIOs are present
  new chipyard.harness.WithSimSPIFlashModel ++                 // add simulated SPI flash memory, if SPI is enabled
  new chipyard.harness.WithTieOffInterrupts ++                 // tie-off interrupt ports, if present
  
  new chipyard.harness.WithPlusArgBSel ++

  // The IOBinders instantiate ChipTop IOs to match desired digital IOs
  // IOCells are generated for "Chip-like" IOs, while simulation-only IOs are directly punched through
  new chipyard.iobinders.WithBlockDeviceIOPunchthrough ++
  new chipyard.iobinders.WithNICIOPunchthrough ++
  new chipyard.iobinders.WithSerialTLIOCells ++
  new chipyard.iobinders.WithDebugIOCells ++
  new chipyard.iobinders.WithGPIOCells ++
  new chipyard.iobinders.WithUARTIOCells ++
  new chipyard.iobinders.WithSPIIOCells ++
  new chipyard.iobinders.WithBSelIOCells ++
  
  new chipyard.iobinders.WithTraceIOPunchthrough ++
  new chipyard.iobinders.WithExtInterruptIOCells ++

  new testchipip.WithSerialTLWidth(1) ++
  new testchipip.WithDefaultSerialTL ++                          // use serialized tilelink port to external serialadapter/harnessRAM0

  new WithEE290CBootROM ++                                       // use our bootrom

  new WithNEntryUART(32, 32) ++                                // add a UART
  new chipyard.config.WithNoSubsystemDrivenClocks ++             // drive the subsystem diplomatic clocks from ChipTop instead of using implicit clocks
  new chipyard.config.WithInheritBusFrequencyAssignments ++      // Unspecified clocks within a bus will receive the bus frequency if set
  new chipyard.config.WithPeripheryBusFrequencyAsDefault ++      // Unspecified frequencies with match the pbus frequency (which is always set)
  new freechips.rocketchip.subsystem.WithJtagDTM ++              // set the debug module to expose a JTAG port
  new freechips.rocketchip.subsystem.WithNoMMIOPort ++           // no top-level MMIO master port (overrides default set in rocketchip)
  new freechips.rocketchip.subsystem.WithNoSlavePort ++          // no top-level MMIO slave port (overrides default set in rocketchip)
  new freechips.rocketchip.subsystem.WithNExtTopInterrupts(0) ++ // no external interrupts
  new freechips.rocketchip.system.BaseConfig)                    // "base" rocketchip system
  


