package chipyard

import chisel3._
// import chisel3.core.{withReset, dontTouch}
// import chisel3.experimental.MultiIOModule
import chisel3.util._

import freechips.rocketchip.config.{Config}
import freechips.rocketchip.config._

import freechips.rocketchip.config.{Field, Parameters}
import freechips.rocketchip.diplomacy._
import freechips.rocketchip.tilelink._
import freechips.rocketchip.devices.tilelink._
import freechips.rocketchip.regmapper._
import freechips.rocketchip.subsystem._
import freechips.rocketchip.util._

import testchipip._

case object PeripheryEE290CBLEKey extends Field[EE290CBLEParams]
case object EE290CBLEPipelineResetDepth extends Field[Int]
case object CacheBlockStriping extends Field[Int]
case object LbwifBitWidth extends Field[Int]

case class EE290CBLEParams(
  scrAddress: Int = 0x2000
)

/**
 * IO out from the MMIO components
 */
trait HasEE290CBLETopBundleContents extends Bundle
{
  //implicit val p: Parameters

  val boot          = Input(Bool())
}

/**
 * MMIO controlled components (system control registers (SCR))
 */
trait HasEE290CBLETopModuleContents extends MultiIOModule with HasRegMap
{
  val io: HasEE290CBLETopBundleContents
  //implicit val p: Parameters
  def params: EE290CBLEParams
  def c = params

  // this corresponds to byte addressable memory (0x0 -> 0x1 == 8 bits)
  regmap(
    0x00 -> Seq(RegField.r(1, RegReadFn(io.boot)))
  )
}

/**
 * TL Module used to connect MMIO registers
 */
class TLEE290CBLE(w: Int, c: EE290CBLEParams)(implicit p: Parameters)
  extends TLRegisterRouter(c.scrAddress, "EE290CBLE-scr", Seq("ucb-bar,EE290CBLE-scr"), interrupts = 0, beatBytes = w)(
    new TLRegBundle(c, _) with HasEE290CBLETopBundleContents)(
    new TLRegModule(c, _, _) with HasEE290CBLETopModuleContents)

// ------------------------------------------------------------------------------------------------------------------------------------

trait HasPeripheryEE290CBLE
{
  this: BaseSubsystem =>

  val scrParams = EE290CBLEParams() // p(PeripheryEE290CBLEKey)

  val scrName = Some("EE290CBLE_scr")
  val scr = LazyModule(new TLEE290CBLE(pbus.beatBytes, scrParams)).suggestName(scrName)
  pbus.toVariableWidthSlave(scrName) { scr.node := TLBuffer() }

}

trait HasPeripheryEE290CBLEBundle
{
  val boot: Bool

  def tieoffBoot() {
    boot := true.B
  }
}

trait HasPeripheryEE290CBLEModuleImp extends LazyModuleImp with HasPeripheryEE290CBLEBundle
{
  val outer: HasPeripheryEE290CBLE

  // instantiate IOs
  val boot = IO(Input(Bool()))


  // ------------------------------------------------

  val scr_mod = outer.scr.module

  // other signals
  scr_mod.io.boot := boot

  // ------------------------------------------------
}

class WithBSel extends Config((site, here, up) => {
  case PeripheryEE290CBLEKey => Some(EE290CBLEParams(scrAddress = 0x2000))
})

