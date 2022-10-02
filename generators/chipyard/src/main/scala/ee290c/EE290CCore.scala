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
import sifive.blocks.devices.gpio._
import sifive.blocks.devices.uart._

// EE290C
//import dma._
import aes._
import baseband._

case class SBusScratchpadParams(
  base: BigInt,
  mask: BigInt)

case object SBusScratchpadKey extends Field[Option[SBusScratchpadParams]](None)

/**
 * Trait to add a scratchpad on the sbus
 */
trait CanHaveSBusScratchpad { this: BaseSubsystem =>
  private val portName = "Backing-Scratchpad"

  val spadOpt = p(SBusScratchpadKey).map { param =>
    val spad = LazyModule(new TLRAM(address=AddressSet(param.base, param.mask), beatBytes=sbus.beatBytes, cacheable=true, executable=true))
    sbus.toVariableWidthSlave(Some(portName)) { spad.node }
    spad
  }
}

class WithSBusScratchpad(base: BigInt = 0x80000000L, mask: BigInt = ((32 << 10) - 1)) extends Config((site, here, up) => {
  case SBusScratchpadKey => Some(SBusScratchpadParams(base, mask))
})


class WithEE290CBootROM extends Config((site, here, up) => {
  case BootROMLocated(x) => up(BootROMLocated(x), site).map(_.copy(contentFileName = s"./generators/chipyard/src/main/scala/ee290c/bootrom/bootrom.rv${site(XLen)}.img"))
})

class WithNGPIOs(width: Int = 2) extends Config((site, here, up) => {
  case PeripheryGPIOKey => Seq(
    GPIOParams(address = 0x10012000, width = width, includeIOF = false))
})

class WithNEntryUART(nTxEntries: Int = 32, nRxEntries: Int = 32, baudrate: BigInt = 115200) extends Config((site, here, up) => {
  case PeripheryUARTKey => Seq(
    UARTParams(address = 0x54000000L, nTxEntries = nTxEntries, nRxEntries = nRxEntries, initBaudRate = baudrate))
})

class EE290Core extends Config((site, here, up) => {
  case XLen => 32
  case RocketTilesKey => List(RocketTileParams(
      core = RocketCoreParams(
        useVM = false,
        fpu = Some(FPUParams(minFLen = 32, fLen = 32, divSqrt = false, sfmaLatency = 4)),
        mulDiv = Some(MulDivParams(
          mulUnroll = 8,
          mulEarlyOut = true,
          divEarlyOut = true))),
      btb = None,
      dcache = Some(DCacheParams(
        rowBits = site(SystemBusKey).beatBits,
        nSets = ((16 << 10) >> 6), // 16k
        nWays = 1,
        nTLBSets = 1,
        nTLBWays = 4,
        nMSHRs = 0,
        blockBytes = 64,
        scratch = Some(0x80000000L))),
      icache = Some(ICacheParams(
        rowBits = site(SystemBusKey).beatBits,
        nSets = ((4 << 10) >> 6), // 4k
        nWays = 1,
        nTLBSets = 1,
        nTLBWays = 4,
        blockBytes = 64))))
})
