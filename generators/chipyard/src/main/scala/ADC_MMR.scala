package chipyard

import chisel3._
import chisel3.util._
import chisel3.experimental.{IntParam, BaseModule}
import freechips.rocketchip.amba.axi4._
import freechips.rocketchip.subsystem.BaseSubsystem
import freechips.rocketchip.config.{Parameters, Field, Config}
import freechips.rocketchip.diplomacy._
import freechips.rocketchip.regmapper.{HasRegMap, RegField}
import freechips.rocketchip.tilelink._
import freechips.rocketchip.util.UIntIsOneOf

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

// DOC include start: ADC params
case class ADCParams(
  address: BigInt = 0x4000,
  width: Int = 8,
  useAXI4: Boolean = false)
// DOC include end: ADC params
 
// DOC include start: ADC key
case object ADCKey extends Field[Option[ADCParams]](None)
// DOC include end: ADC key

class ADCAnalogIO extends Bundle {
  val myadc_data = Input(UInt(8.W))
}

trait ADCTopIO extends Bundle {
  val top = new ADCAnalogIO
}

// DOC include start: ADC instance regmap

trait ADCModule extends HasRegMap {
  val io: ADCTopIO

  implicit val p: Parameters
  def params: ADCParams
  val clock: Clock
  val reset: Reset

  regmap(
    0x00 -> Seq(
      RegField.r(8, io.top.myadc_data) // a plain, read-only register
    )
  )
}
// DOC include end: ADC instance regmap


// DOC include start: ADC router
class ADCTL(params: ADCParams, beatBytes: Int)(implicit p: Parameters)
  extends TLRegisterRouter(
    params.address, "myadc", Seq("ucbbar,myadc"),
    beatBytes = beatBytes)(
      new TLRegBundle(params, _) with ADCTopIO)(
      new TLRegModule(params, _, _) with ADCModule)

class ADCAXI4(params: ADCParams, beatBytes: Int)(implicit p: Parameters)
  extends AXI4RegisterRouter(
    params.address,
    beatBytes=beatBytes)(
      new AXI4RegBundle(params, _) with ADCTopIO)(
      new AXI4RegModule(params, _, _) with ADCModule)
// DOC include end: ADC router


// DOC include start: ADC lazy trait
trait CanHavePeripheryADC { this: BaseSubsystem =>
  private val portName = "myadc"

  // Only build if we are using the TL (nonAXI4) version
  val myadc = p(ADCKey) match {
    case Some(params) => {
      if (params.useAXI4) {
        val myadc = LazyModule(new ADCAXI4(params, pbus.beatBytes)(p))
        pbus.toSlave(Some(portName)) {
          myadc.node :=
          AXI4Buffer () :=
          TLToAXI4 () :=
          // toVariableWidthSlave doesn't use holdFirstDeny, which TLToAXI4() needsx
          TLFragmenter(pbus.beatBytes, pbus.blockBytes, holdFirstDeny = true)
        }
        Some(myadc)
      } else {
        val myadc = LazyModule(new ADCTL(params, pbus.beatBytes)(p))
        pbus.toVariableWidthSlave(Some(portName)) { myadc.node }
        Some(myadc)
      }
    }
    case None => None
  }
}
// DOC include end: ADC lazy trait


// DOC include start: ADC imp trait
trait HasPeripheryADCModuleImp extends LazyModuleImp {
  val outer: CanHavePeripheryADC
  val myadc_data = outer.myadc match {
    case Some(myadc) => {
      val io = IO(new ADCAnalogIO)
      val data = io
      // myadc.module.io.top.myadc_data := data
      myadc.module.io.top := data
      Some(data)
    }
    case None => None
  }
}
// DOC include end: ADC imp trait


// DOC include start: ADC config fragment
class WithADC(useAXI4: Boolean) extends Config((site, here, up) => {
  case ADCKey => Some(ADCParams(useAXI4 = useAXI4))
})
// DOC include end: ADC config fragment


class ADC extends Module
{
  val io = IO(new Bundle {
    val adc = Flipped(new ADCAnalogIO)
  })
  // val (counterValue, counterWrap) = Counter(0 until 222 by 2)
  // counterWrap := false.B
  
  // val countOn = true.B // increment counter every clock cycle
  // val (counterValue, counterWrap) = Counter(countOn, 2)
  io.adc.myadc_data := 222.U
}

